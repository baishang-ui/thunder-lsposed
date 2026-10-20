# 通知圆角的直接更新路径

ColorOS 17 的 `ActivatableNotificationView.applyRoundnessAndInvalidate` 把上下圆角传给 `NotificationBackgroundView.setRadius(float, float, boolean)`。后者不仅更新填充，还通过扩展更新焦点层、直接描边、阴影和堆叠背景。这些写入可能绕过 `ViewBlurProxy.applyBlurConfig`，上游圆角 getter 也可能被 ART 内联。

原有直接接收端只选择 `CloudLockHook.areaForView`，普通通知和横幅没有对应流体云材质时提前退出，仍把系统半径写进这些层。现在接收端统一调用 `NotificationCardHook.area`，复用现有的普通通知、横幅和流体云优先级，再按原生上下圆角动画比例计算像素值。关闭模块或对应圆角开关时保留原生传入值；尺寸、混色与模糊设置不参与圆角启用判断。

回归检查 `NotificationExpansionCheck` 直接传入原生半径，不手动调用圆角 getter Hook，覆盖普通通知与横幅的 0/13/64 dp、0/0.5/1 动画比例、描边/阴影/焦点层、无模糊与混色时的独立圆角以及关闭恢复。修复前新增的 18 个接收端场景全部失败，修复后通过。`CloudNotificationGeometryCheck` 同时验证流体云圆角继续独立于普通通知。

这些主机回归检查验证真实 Java Hook 的参数选择与状态行为，不等于真机渲染验收。运行报告新增 `notification-edge-radius` 命中项，用于连接设备后确认实际接收端已生效。
