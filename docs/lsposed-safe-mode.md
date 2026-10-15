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

## 重启流程

保留右上角的“重启系统界面”，改用 Android 管理的应用生命周期：

```sh
am force-stop --user 0 com.android.systemui
am startservice --user 0 -n com.android.systemui/com.android.systemui.SystemUIService
```

这两个命令只用于显式点击重启，执行前必须完成以下检查：

- 保存并同步配置，收到本次操作之后、当前或更新配置版本的 Hook 报告。允许旧版 Hook 报告提供进程身份，以便已安装的模块升级；首次安装、没有有效报告时提示正常重启手机。
- 从报告时间起等待 65 秒，超过已核对框架的 60 秒观察期。新报告证明该进程已经完成注入，因此是心跳注册时间之后的保守计时基准，不使用上一次点击的时间。界面显示倒计时，并支持后台继续。
- Root 脚本用精确进程名核对报告 PID，用 `/proc/PID/stat` 的启动时间及 `getconf CLK_TCK` 排除 PID 已被复用的情况，再用 `/proc/uptime` 验证观察期已经结束。
- 在停止前用 `cmd package query-services --components --user 0 -n ...` 确认目标服务存在；Root 等待或服务查询结束后，重新检查请求有效期及 PID。目标变化、无法读取身份或服务不可用时不执行停止。

脚本只请求停止一次，并立即显式启动服务，随后等待新 PID。没有 TERM / KILL、崩溃命令或 Zygote / 手机重启兜底，不修改 LSPosed 的安全模式开关、计数或模块状态。自动调参始终只走配置监听通道。

新进程启动后，仍要求新 PID、本版 Hook、当前或更新配置版本、本次操作后的报告全部匹配才显示确认成功，所有字段从同一报告快照读取。各阶段失败单独提示，不自动重试停止。

## 界面与验证

确认、进度、倒计时和结果在同一个对话框中切换；“后台继续”可收起，右上角可重新查看。应用级协程和互斥锁避免旋转或重复点击产生并行重启。

- `scripts/check_systemui_restart.py` 用模拟 Android 命令验证正常顺序、Root 拒绝、过期、无进程、非法 PID、进程变化、PID 复用、缺少报告、未过观察期、服务缺失、停止失败、启动失败及新进程超时；禁止信号强杀和崩溃兜底。
- `SystemUiRestartWindowTest` 检查从当前进程报告起等待 65 秒，替换进程不能复用旧进程的等待时间。
- `SystemUiRestartReceiptTest` 检查新 PID 和当前 Hook / 配置 / 报告时间的确认。
- `scripts/check_live_settings.py` 检查自动配置监听、重复通知合并、失败隔离及恢复。

验证依据针对已核对的 LSPosed 2.2.1 (7912)，其他分支的保护策略可能不同。目前未连接真机，服务启动行为、实际界面显示及长期稳定性仍需目标 ColorOS 17 验证。真实 Hook 崩溃、固件或其他模块故障仍可能触发框架保护；本次不关闭该保护。
