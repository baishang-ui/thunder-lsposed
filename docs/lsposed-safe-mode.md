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

本实现参考其进程退出方式，使用已经核对身份的精确 PID，避免模糊匹配其他进程。没有引入 LuckyTool 的其他功能，也不复制其源码。这个方式本身不会避开 LSPosed 的短生命周期计数。当前交互按用户点击立即重启，已移除应用额外施加的稳定期；框架自己的保护逻辑继续生效。

## 当前系统界面重启流程

右上角“重启系统界面”点击后立即开始，不再二次确认。应用保存当前设置并尝试同步配置，然后请求 Root。重启本身不依赖框架作用域读取、Hook 运行报告或独立插件状态；配置尚未同步时，结果会另外说明。

Root 授权后才读取 `pidof com.android.systemui`，核对 `/proc/PID/cmdline` 的精确进程名与 `/proc/PID/stat` 的启动时间，再复核 PID 和启动时间未变化，执行一次：

```sh
/system/bin/kill -9 "$thunder_host_old"
```

不再强制等待 65 秒，不以进程年龄或报告时间限制本次点击。SystemUI 通常由 Android 自动拉起，轮询每 200 ms 检查实际结果，检测到新进程即可结束；3 秒仍未恢复时补启动 `com.android.systemui/com.android.systemui.SystemUIService`，必要时再请求启动一次。不会再次终止旧进程或新进程。

只操作 `com.android.systemui`。不查询、停止或启动独立的 `com.oplus.systemui.plugins`。在 SystemUI 内加载的插件代码会随宿主重建；模块的两个推荐作用域仍用于模块启用与功能加载，不决定这个按钮的重启目标。

命令结果必须包含旧 PID、旧进程已退出标记和不同的新 PID。已退出判断检查原进程消失、启动时间变化或退出状态。命令返回零但旧进程仍在时，不会报告成功。结果仅表示进程重建，不宣称 Hook 或配置已加载；运行报告仍可在主页单独查看。

脚本为 `app/src/main/assets/restart-scope.sh`。请求有效期只限制 Root 授权后的首次终止，已经开始的恢复流程会继续。自动调参只使用实时配置通道，不会触发重启，也不修改 LSPosed 的安全模式开关或计数。

此前的等待来自“本次配置报告之后再等待 65 秒”，即使进程早已运行很久，新的配置报告仍会让点击重新等满。这是旧流程额外引入的延迟，已连同独立插件重启、前置报告窗口和重启后的 Hook 确认窗口一起移除。

## 界面与验证

按钮点击直接执行，弹窗只展示进度与结果，可收起。应用级协程和互斥锁避免旋转或重复点击产生并行重启。

- `scripts/check_systemui_restart.py` 用模拟 Android 命令验证立即终止新启动的 SystemUI、独立插件保持运行、精确 PID 的单次 SIGKILL、旧进程确实退出、系统自动重建、补启动及其失败、Root 拒绝、过期、进程名不符、PID 变化和复用。测试替换绝对路径的 Android kill 工具，绝不对宿主机 PID 发信号。
- `ScopeRestartTargetResultTest` 验证结果必须包含唯一的旧 PID、退出标记及不同的新 PID，插件结果不能代替 SystemUI 结果。
- `scripts/check_live_settings.py` 检查自动配置监听、重复通知合并、失败隔离及恢复。

保护逻辑的依据针对已核对的 LSPosed 2.2.1 (7912)，其他分支策略可能不同。模拟测试不验证手机实际恢复耗时、界面渲染或目标 ColorOS 17 的长期稳定性。
