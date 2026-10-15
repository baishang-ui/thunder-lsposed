# 状态栏跟随应用过渡（实验性）

## 用户确认的目标与当前差距

用户在 r3 真机反馈状态栏仍完全不动，并明确目标：状态栏沿应用打开时的实际运动路径移动、缩放，显示在应用遮罩上方；应用展开到全屏的同一帧，状态栏回到屏幕顶部的正确位置。应用遮罩不能把状态栏裁掉。r1～r3 均不能作为已完成交付。r4 已将独立图层与桌面逐帧事务接入写入源码，本地回归通过，尚未真机验收。

此前实现将整个状态栏 WindowToken 直接挂到 TransitionInfo 的应用 leash 下面。这样会继承该层及其祖先的裁剪、透明度和几何变换，也未核实它是否就是桌面逐帧变换的最终目标层，因此不能保证上述遮罩关系与运动路径。不能仅继续放宽场景过滤来认定问题已经解决。

r4 改为独立的状态栏运动层：使用实际应用动画的坐标变换，保持在应用遮罩上方，同一帧提交位置和缩放，不继承应用遮罩的 crop、圆角和 alpha。当前仅接管源区域等于完整主屏、原点为零、无旋转与透视的缩放平移；其他坐标体系恢复系统状态栏，避免引入终点偏移。动画终点必须精确为原始位置与单位缩放。中断、取消与接管者死亡仍须恢复原层级。

已有固件静态证据：桌面 RectTransformHelper.a 在每帧计算 Matrix（字段 d），包含缩放、平移及旋转，再通过 xi.q 包装的 SurfaceControl.Transaction 提交。gi.c.d 是其参与变换的表面，gi.c 另持有 RunningTaskInfo 与多个其他表面句柄。只有核对这些层之间的映射，才能决定独立运动层应放置在哪一层、复制哪一组变换。静态证据不能证明 r3 真机回调是否命中，仍需结合本次运行报告或采集日志区分未命中、被过滤与挂接被覆盖。

入口：**调节 → 状态栏 → 应用过渡 → 状态栏跟随应用**，默认关闭。

首次安装或升级此实现后，需要在 LSPosed 的 thunder 作用域中额外勾选 **系统框架（system / system_server）** 和 **系统桌面（com.android.launcher）**，打开 thunder 同步配置，然后**重启手机**。系统界面与系统界面插件继续用于其他外观功能。只重启 SystemUI 无法重新加载 system_server 中的代码。首次加载后，开关通过 API 102 远程配置实时更新；关闭会立即恢复状态栏。

## r4：独立图层与桌面同帧变换

- 已核对采集固件：`ma.p0.run` 对 `gi.c.d` 写入应用矩阵，使用 `xi.q$b.i(Matrix)`，之后才提交 `xi.q.a` 中的原始事务。Hook 在这个矩阵写入之后、事务提交之前追加状态栏的矩阵。
- system_server 在原状态栏父层下创建独立 container，层级使用状态栏原值；WindowToken 移入此层，正常 Insets 子 leash 保留。该层不在应用的遮罩或任务裁剪层内，不复制应用 alpha、crop 和圆角。
- 模块 Provider 只保存 system_server 发布的 Binder 入口。桌面按实际任务 ID 获取当前会话的图层句柄，每个目标最多获取一次；后续帧直接使用桌面的同一事务，无逐帧 Binder 传输。
- Binder 校验桌面 UID、客户端与任务，提交确认/拒绝还校验会话代次。原生 finish 与 cleanup 都归还原始父层并断开 container；原生终止后才 release 服务端句柄。不能在构建 finish 时调用 `Transaction.remove`，固件此方法立即 release 句柄，会让尚未开始的动画失效。
- 独立层初始隐藏，桌面首个实际矩阵写入与 show 同帧提交，避免先在顶部闪现。500ms 内未确认首帧会恢复；另保留 6 秒动画超时、取消、锁屏、配置关闭与桌面死亡恢复。
- 系统框架通过 AMS 的 `getContentProviderExternal` 发布入口和报告，并在 finally 释放外部 Provider 引用。普通 ContentResolver 路径不再用于 system_server。Provider 重启后入口会重新发布。
- 运行报告新增独立的“桌面动画”：连接状态、动画数、同帧写入帧数与最后矩阵。系统框架报告区分 acquired 与 committed；后者来自实际事务提交回调。日志包含 `launcher-overlay`、`overlay-scene` 与首帧超时原因。

这些合成接口检查验证真实生产代码的事务与恢复行为，不能证明真机 SurfaceFlinger、Insets 或其他厂商代码不会覆盖图层。必须以用户手机实际动画为最终验收。

## r2：更正系统进程作用域

上一版错误地将 `android` 当作 API 102 的系统进程作用域，导致作用域检查可能误报完整。API 102 使用独立虚拟包名 `system`；`android` 仅覆盖 Android 包的其他进程。安装 r2 后实际作用域需含 `system`，原先勾选 `android` 并重启不能替代此项。更改系统进程作用域后需重启手机。

用户反馈 r2 在作用域完整且重启后仍没有系统框架回报。后续采集已确认 system_server 成功加载（见下方 r3 依据），因此不能再将该设备上的无回报解释为未注入。

回归检查直接核对 APK 的推荐作用域，并测试：只有 `android` 时必须显示缺少 `system`，不支持系统进程 Hook 时不能显示完整。

LSPosed 日志中的 `window-bootstrap statusbar-follow/r2` 依次记录 `system-entry`、`installing`、`hooks-ready`、`wms-captured`、`settings-loaded`。系统框架运行报告包含同一实现标记和阶段；如果没有报告，应先查启动阶段，不能据此假定动画场景过滤失败。

## r3：修正 Insets 控制层冲突判断及 ColorOS 手势类型

依据用户的 `thunder-statusbar-bootstrap-20261015-195322-7417.tar.gz`：19:41:54.881 进入 system_server（PID 3623），19:41:54.888 安装 Hook 完成，19:41:57.875 捕获 WMS，19:41:58.580 读取配置；后续配置同步也到达该进程。包内 APK 的 SHA-256 与已交付 r2 完全一致。应用自己的配置显示 enabled 与 status_bar_follow_app 均为 true，但没有 runtime_system 文件，日志也没有系统报告发送异常。这证明入口与配置链路已运行，尚不足以定位报告未落盘的具体原因。

此前固件图层采集中，StatusBar WindowState 位于正常 Insets animation leash 内。固件 `WindowContainer.onAnimationLeashCreated` 会设置 mAnimationLeash，WindowState.getAnimationLeash 返回该字段；旧版以此非空直接跳过，错误地将正常控制层当作冲突。r3 允许 WindowState 的 ANIMATION_TYPE_INSETS_CONTROL（固件值 32），挂接其祖先 WindowToken 并保留子层；其他 WindowState 动画、WindowToken 动画和相对图层冲突继续跳过。回归测试先以真实层级特征复现失败，再验证修正。

本次 19:52:48.794 的返回手势为 FIRST_CUSTOM+21（1021）、flags=0x80，包含移到前台的全屏桌面、退到后台的唯一全屏应用与壁纸。SystemUI 固件 RecentsTransitionHandler.startRealRecentsTransition 确实使用 1021。r3 仅对这个类型/标记组合及明确的桌面进入、应用退出方向开放；其他类型、其他标记与多个普通应用继续跳过。该手势可能进入最近任务，不能在开始时断言最终一定返回桌面。

回报另有独立的可靠性修正：固定首次发送期限，连续更新只替换快照；检查非 null 的接收确认，失败后每 5 秒重试，最多自动尝试 8 次。首次发送、首次确认及有限的失败信息会记录为 window-report；启动阶段另附配置版本、开关、计数与原因。报告失败不关闭动画接管。这些变更用于消除可复现的报告盲区，尚未证明它们就是该设备未落盘的唯一原因。

真机效果仍需安装 r3 后验证，不能由模拟事务检查推断已成功移动状态栏。

## 当前范围

针对用户提供的 PLQ110_17.0.0.104(CN01)、Android 17 / SDK 37 固件接口实现。主屏竖屏、一个普通全屏应用与桌面之间的打开、返回，以及符合上述条件的手势最近任务过渡可接管。过渡必须含桌面与唯一的普通应用任务，开始和结束都占满显示区域，且过渡根坐标为原点。

以下情况交给系统：两个普通应用间直接切换、不符合上述条件的最近任务过渡、应用内页面跳转、旋转、横屏、分屏、小窗、画中画、外接屏、锁屏、下拉面板、活动流体云、已有非 Insets 状态栏动画，以及无法确定唯一窗口归属的场景。除上述 1021/0x80 组合外，非零过渡标记仍跳过。这些限制用于避免将共用状态栏交给错误的窗口。

尚未连接手机验证视觉效果。合成接口测试不能证明设备上 Launcher 与 SurfaceFlinger 的实际动画表现。

## 接入与恢复

- 使用 API 102 `onSystemServerStarting` 接入 WMS，配置在独立工作线程读取。
- 在 `Transition.buildFinishTransaction` 之后准备独立状态栏层，原生 start transaction 挂接 WindowToken；桌面在应用的真实事务中逐帧写入独立层矩阵。
- 原生 finish transaction 先写恢复，再写 start 挂接；cleanup transaction 同时补恢复。保留原状态栏变换；跟随期间通过独立父层保持在应用遮罩上方，结束时恢复保存的原图层值。已有相对图层依赖时跳过。
- ColorOS 的 `LiteOSAnimController.startAsyncFadeAnimIfNeed` 比挂接更早。仅在同步调用期间，为已通过相同场景检查的状态栏保留淡出豁免；正在跟随的状态栏也豁免独立淡出。导航栏和其他窗口保留原逻辑。
- 正常结束、abort、失败清理、关闭开关、界面状态变化和 6 秒超时均有恢复路径。取消后仍保留过渡所有权屏障，直到原过渡终止，防止后来的旧 finish 事务拆掉新的跟随。原 start 事务延迟提交时会再次恢复。
- 重叠过渡恢复旧状态栏并跳过新过渡。内部接口异常会停止本进程中的接管，等待重新启动系统进程；不会持续尝试失败的挂接。
- system_server 的层级与所有权操作由 WMS 全局锁串行化；桌面只可写入独立 container 的矩阵与 show。配置与运行报告 IPC 使用独立工作线程。两端报告分别保存于 `runtime_system`、`runtime_launcher`，不参与 SystemUI 重启成功判定。

## 验证

```sh
./gradlew :app:testReleaseUnitTest :app:lintRelease :app:assembleRelease
python3 scripts/check_transitions.py
python3 scripts/check_effects.py
python3 scripts/check_live_settings.py
python3 scripts/check_systemui_restart.py
```

Python 检查要求 `JAVA_HOME` 指向 JDK 17 或更新版本。`check_transitions.py` 执行真实适配器，合成类只提供已核对的固件接口及可观察的事务；覆盖默认关闭、原生开始/结束/清理事务、实时关闭、场景过滤、淡出隔离、重叠、锁屏打断及超时。纯 Java 所有权测试另外覆盖延迟提交、旧提交回调与恢复失败重试。设置测试覆盖默认值、严格类型、配置导入迁移与独立设置页。

## 真机验收

1. 打开功能，确认系统框架报告为 `impl=statusbar-overlay/r4`、已读到当前配置，桌面报告显示 `connected=true`。
2. 从桌面打开设置等普通竖屏应用，返回桌面，检查状态栏是否和应用同帧缩放、移动，结束后恢复正常位置。
3. 连续快速打开、返回，动画中下拉、锁屏或旋转；状态栏应及时恢复，不能停在应用卡片里。
4. 动画中关闭开关，检查立即恢复；重新打开后再触发一次完整过渡。
5. 检查进入最近任务再取消手势，状态栏能恢复；横屏、分屏、小窗和活动流体云仍由系统处理。
6. 在运行报告查看桌面同帧写入帧数、系统 acquired/committed、跟随/跳过次数、当前原因和两端异常；如出现异常，先关闭开关恢复，再保留报告定位。
