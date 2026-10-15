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

## 作用域重启流程

右上角入口为“重启作用域”，只覆盖模块声明的 `com.android.systemui` 和 `com.oplus.systemui.plugins`。先检查 LSPosed 实际作用域同时包含这两个包；其他作用域不会成为命令目标。

目标 ColorOS 17 的插件存在两种加载方式：作为次要包加载进 SystemUI，或独立运行。已检查采集固件的 `SystemUIPlugin.apk`：`com.oplus.systemui.plugins.seedling.plugin.SeedlingPluginService` 继承 `android.app.Service`，清单声明该服务；插件未与 SystemUI 共享 UID。独立运行时按以下顺序恢复：

```sh
am force-stop --user 0 com.oplus.systemui.plugins
am startservice --user 0 -n com.oplus.systemui.plugins/com.oplus.systemui.plugins.seedling.plugin.SeedlingPluginService
# 确认插件新 PID 后，再核对原 SystemUI 的身份和请求有效期。
am force-stop --user 0 com.android.systemui
am startservice --user 0 -n com.android.systemui/com.android.systemui.SystemUIService
```

插件没有独立进程时，仅重启 SystemUI，由宿主重新加载插件代码。不会为了一个未运行的插件额外启动进程。独立插件如果没有本次配置的有效报告，取消整个请求；不会把“未收到报告”当成“未运行”。

执行前必须完成以下检查：

- 保存并同步配置，读取 `runtime` / `runtime_cloud` 的完整报告快照，要求 PID 有效、Hook 版本大于零、报告在本次请求之后、配置版本不低于本次提交。允许旧版 Hook 提供身份，以便升级；首次安装没有有效报告时提示正常重启手机。
- 从每个已确认进程的报告时间起等待 65 秒，取剩余等待时间的最大值，超过已核对框架的 60 秒观察期。报告证明注入已完成，因此计时基准晚于心跳注册，不使用上次点击的时间。
- 等待后重新检查实际作用域。Root 脚本用精确进程名、报告 PID、`/proc/PID/stat` 启动时间和 `getconf CLK_TCK` 排除进程变化及 PID 复用，并用 `/proc/uptime` 复核观察期。
- 在第一次停止前，用 `cmd package query-services --components --user 0 -n ...` 确认全部所需服务存在，再重新检查请求有效期和两进程身份。插件在等待期间出现也会取消。
- 独立插件恢复失败时不再停止 SystemUI；插件恢复后再次核对原 SystemUI，避免宿主已被 Android 自动替换时二次停止新进程。每个目标最多停止一次，步骤失败即结束，已经完成的步骤会在结果中说明。

新脚本为 `app/src/main/assets/restart-scope.sh`。没有信号强杀、崩溃命令或手机重启兜底，不修改 LSPosed 保护状态。自动调参始终只走配置监听通道。

命令执行后分别检查两个作用域的新 PID、本版 Hook、本次或更新配置版本及报告时间；随宿主加载时，还要求 SystemUI 新报告包含精确的 `cloud-package-entry` 标记，独立插件则从其新进程报告核对该标记。只收到 SystemUI 报告不会显示全部成功；超时会指出未确认的作用域。

## 界面与验证

确认、进度、倒计时和结果在同一个对话框中切换；“后台继续”可收起，右上角可重新查看。应用级协程和互斥锁避免旋转或重复点击产生并行重启。

- `scripts/check_systemui_restart.py` 用 30 个模拟场景验证宿主内加载、独立插件的停止与恢复顺序、全部服务预检查、Root 拒绝、过期、PID 复用、缺少报告、稳定期、插件延迟出现、进程替换和部分失败；检查插件失败或宿主变化后不再停止宿主。
- `ScopeRestartWindowTest` 检查从当前进程报告起等待 65 秒，替换进程不能复用旧进程的等待时间。
- `ScopeRestartReportTest` 检查各自的新 PID、当前 Hook / 配置 / 报告时间、插件加载标记及命令结果完整性。
- `scripts/check_live_settings.py` 检查自动配置监听、重复通知合并、失败隔离及恢复。

验证依据针对已核对的 LSPosed 2.2.1 (7912)，其他分支的保护策略可能不同。目前未连接真机，服务启动行为、实际界面显示及长期稳定性仍需目标 ColorOS 17 验证。真实 Hook 崩溃、固件或其他模块故障仍可能触发框架保护；本次不关闭该保护。
