# 通知条展开时的材质稳定性

通知中心有多条通知时，展开、收起以及堆叠背景切换应持续使用通知条设置的颜色、模糊和圆角。分组边角仍跟随系统的上下圆角比例变化。

## 原因与修复

ColorOS 17 的 `NotificationBackgroundView.setRadius()` 会把通知行的上下圆角传给 `NotificationBackgroundViewExtImp.updateRadius()`，再写回 `BlurConfig` 的四个边角字段。通知行的圆角已经由模块替换，旧实现又用“边角像素值 ÷ 系统基准圆角”计算比例，随后乘一次自定义圆角。例如系统基准为 47 px、自定义为 26 px 时，全圆角被再次缩放成约 14.4 px；展开过程中重新写回边角，会触发外观跳变。

现在通过弱引用关联背景与通知行，直接读取该行的 `getTopRoundness()` / `getBottomRoundness()` 比例，只应用一次自定义半径。其他没有通知行归属的材质继续使用原来的边角比例计算。

固件还通过 `KeyguardStackingBlurConfig.createBlurConfigs()` 为两层堆叠背景创建独立配置，保存在 `MultiPlatformBlurHelper.configMap`。这些背景直接调用 `PlatformBlurDrawable.applyBlurConfig()`，绕过通知主背景的 `ViewBlurProxy`。现在为这些独立配置关联通知材质和所属通知行，在实际消费配置时应用同一套临时参数，并在调用结束或异常后还原。配置更新会同时刷新已有堆叠背景。

## 验证

`NotificationExpansionCheck` 运行真实 Java Hook，使用合成 Android/Oplus 替身重放通知行圆角回写、嵌套背景消费、多条通知交错更新、横幅切换、两层堆叠背景、实时调参、零混色、零圆角、异常还原及关闭模块。新增用例在修复前复现了重复缩放、堆叠配置未覆盖和堆叠缓存未刷新。

```sh
python3 scripts/check_effects.py --only NotificationExpansionCheck NotificationStyleCheck
python3 scripts/check_effects.py
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

固件调用链依据来自本地采集的 ColorOS 17 SystemUI.apk。合成回归不执行设备 GPU 或真实展开动画；多条普通通知、分组通知及锁屏堆叠背景的最终观感仍需目标真机验证。
