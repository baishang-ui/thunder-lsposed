# SystemUI 重启与 LSPosed 安全模式

## 核对依据

2026-10-16 从 [LSPosed 官方分发站](https://lsposed.zip/) 下载的文件为 `LSPosed-v2.2.1-7912-release.zip`，其 `module.prop` 声明版本 `v2.2.1 (7912)`，与历史手机日志一致。

- ZIP SHA-256：`ed37de2d2097aa6ba871377e7bf4ac719b708484f9468928e6abae10ab311e74`
- `daemon.apk` SHA-256：`a43bb3554721d650356fa23783a4f3757b85e7c703b836a28eeba6e3cc7c960a`
- [官方更新记录](https://t.me/LSPosed/324) 提到修复“安全模式计数未在正常启动后重置”的问题。

核对该分发包的 DEX 后，确认保护逻辑监听 SystemUI 的 Binder 死亡：

1. 用 `elapsedRealtime()` 记录 SystemUI 注册心跳的时间。
2. Binder 死亡时，若本次存活不足 60,000 ms，就增加计数；默认第 4 次触发安全模式。
3. 存活达到 60,000 ms 后退出，该次退出不增加计数。
4. 该判断不检查退出信号或 ActivityManager 的退出原因。因此单独将 KILL 换成 TERM、force-stop 或其他进程退出接口，都不能解决短时间退出的计数问题。

历史诊断来自 `LSPosed_20261015_153239.zip`。`log.old/verbose_2026-10-15T13:26:51.867762.log` 的 43466、43671、44577、44783 行依次记录 13:50:26 第 1 次、13:50:58 第 2 次、14:18:29 第 3 次、14:18:51 进入安全模式，时间沿用手机日志中的北京时间。这个记录不能推出“任何时候退出都会计数”；它表明按按钮间隔限频不足以证明被重启的当前进程已经稳定。

## LuckyTool 的参照依据

2026-10-16 查阅 [LuckyTool 官方源代码](https://github.com/luckyzyx/LuckyTool/tree/2de5cf1f5bbbe07d8a072244bb14296486856c04)，固定到提交 `2de5cf1f5bbbe07d8a072244bb14296486856c04`：

- [`RestartMenuUtils.restartScope`](https://github.com/luckyzyx/LuckyTool/blob/2de5cf1f5bbbe07d8a072244bb14296486856c04/app/src/main/java/com/luckyzyx/luckytool/utils/RestartMenuUtils.kt) 对普通作用域调用 `forceStopPackage`，单独为 SystemUI 执行 `CommandUtils.killSysui`。
- [`CommandUtils.killSysui`](https://github.com/luckyzyx/LuckyTool/blob/2de5cf1f5bbbe07d8a072244bb14296486856c04/app/src/main/java/com/luckyzyx/luckytool/utils/CommandUtils.kt) 是 `kill -9` 配合 `pgrep systemui`，退出 SystemUI 后由系统重新创建进程。
- 同一工具明确分开了作用域重启、`hotReloadModule` 热重载和结束 Zygote 的快速重启。

本实现参考其进程退出方式，使用已经核对身份的精确 PID，避免模糊匹配其他进程。没有引入 LuckyTool 的其他功能，也不复制其源码。这个方式本身不会避开 LSPosed 的短生命周期计数，因此仍保留稳定期。

## 作用域重启流程

“重启作用域”只覆盖模块声明的 `com.android.systemui` 和 `com.oplus.systemui.plugins`。先检查实际作用域、保存同步配置、核对报告和当前进程身份，然后真正结束旧进程。

SystemUI 使用一次精确 PID 的 `SIGKILL`：

```sh
/system/bin/kill -9 "$confirmed_systemui_pid"
```

不再对 SystemUI 使用 `am force-stop`。系统通常会自动重建这个持久进程，因此先等待新 PID；3 秒内未恢复时显式启动 `com.android.systemui/com.android.systemui.SystemUIService`，仍无新进程时再补启动一次。服务查询不再是 SystemUI 主路径的前置条件。所有恢复尝试只请求启动，不再次终止进程，也不会结束 Zygote 或重启手机。

独立插件按 LuckyTool 普通作用域的方式强制停止，再显式恢复其服务：

```sh
am force-stop --user 0 com.oplus.systemui.plugins
am startservice --user 0 -n com.oplus.systemui.plugins/com.oplus.systemui.plugins.seedling.plugin.SeedlingPluginService
```

已检查采集的 ColorOS 17 `SystemUIPlugin.apk`：该服务继承 `android.app.Service`，清单声明该组件；插件与 SystemUI 不共享 UID。插件恢复的启动请求最多两次，先确认插件恢复后再处理宿主。没有独立插件进程时，只结束 SystemUI，让其中加载的插件代码一同重建。

每个进程结果必须同时具备旧 PID、已退出标记及不同的新 PID。已退出判定检查 `/proc/PID/stat`：原进程消失、启动时间变化或进入退出状态；命令返回零、新进程名出现都不能单独证明旧进程退出。旧进程仍存活时不报告成功、不尝试重复终止。

插件恢复可能导致 Android 自动重建 SystemUI。如果此时已确认原 SystemUI 退出，则接受自然恢复的新进程，或补启动服务，避免再次终止刚启动的 SystemUI。如果旧进程仍存活而进程名结果变化，则取消操作。

执行前的 LSPosed 检查仍然保留：

- 要求两个推荐作用域均已选中，其他作用域不会成为命令目标。
- 保存并同步配置后，从 `runtime` / `runtime_cloud` 分别取得本次请求之后的完整报告，要求有效 PID、非零 Hook 版本及本次或更新配置版本。旧版 Hook 可以提供身份，支持模块升级；首次安装没有报告时提示正常重启手机。
- 从各进程报告起等待 65 秒，取剩余时间的最大值，再重新检查实际作用域。报告证明注入已经完成，是已核对 LSPosed 心跳注册之后的保守计时基准。
- Root 脚本用精确进程名、PID、`/proc/PID/stat` 启动时间与 `getconf CLK_TCK` 排除替换和 PID 复用，再通过 `/proc/uptime` 复核稳定期。
- 独立插件服务在第一次停止前查询确认；Root 授权等待和服务查询后再次核对两进程身份及请求有效期。过期请求不开始终止；已经开始的事务继续完成恢复，避免在插件恢复后因授权期限取消宿主重启。

命令结果通过后，再等待最多 30 秒分别确认新 PID、本版 Hook、当前或更新配置版本、报告时间和插件的 `cloud-package-entry` 标记。两个作用域都确认时显示完整完成；已确认进程重建但缺少 Hook 回报时，明确显示“进程已重启，Hook / 配置尚未确认”，不把两种结果混淆。不会为了补等 Hook 回报再次终止新进程。

脚本为 `app/src/main/assets/restart-scope.sh`。自动调参只使用实时配置通道。没有调用热重载接口，也不修改 LSPosed 安全模式开关或计数。

## 界面与验证

确认、进度、倒计时和结果在同一个对话框中切换；“后台继续”可收起，右上角可重新查看。应用级协程和互斥锁避免旋转或重复点击产生并行重启。

- `scripts/check_systemui_restart.py` 用模拟 Android 命令验证精确 PID 的单次 SIGKILL、系统自动重建、服务补启动与启动重试、插件强制停止与恢复、宿主已被自动重建、Root 拒绝、过期、PID 复用、稳定期及部分失败；命令返回零但旧进程仍存活时必须失败。测试替换绝对路径的 Android kill 工具，绝不对宿主机 PID 发信号。
- `ScopeRestartWindowTest` 检查从当前进程报告起等待 65 秒，替换进程不能复用旧进程的等待时间。
- `ScopeRestartReportTest` 检查各自的新 PID、当前 Hook / 配置 / 报告时间、插件加载标记及命令结果完整性，特别是缺少旧进程退出标记时不能接受新 PID。
- `scripts/check_live_settings.py` 检查自动配置监听、重复通知合并、失败隔离及恢复。

验证依据针对已核对的 LSPosed 2.2.1 (7912)，其他分支的保护策略可能不同。目前未连接真机，服务启动行为、实际界面显示及长期稳定性仍需目标 ColorOS 17 验证。真实 Hook 崩溃、固件或其他模块故障仍可能触发框架保护；本次不关闭该保护。
