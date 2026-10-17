# 锁屏流体云与应用取色遮罩

参考用户提供的 `PUI Theme For ColorOS 17_v17.0.0.137.zip`，只读取资源，不执行压缩包内的安装脚本。`PuiThemeSystemUIPluginColor.apk` 面向 SystemUI，将 `custom_card_mixed_color_top_layer` 设为 `#000c0c0c`、`custom_card_mixed_color_bottom_layer` 设为 `#3cd8d8d8`。本项目的“PUI 材质”参考这组浅灰半透明混色，通过现有的单次 alpha 混色算法应用 `#3cd8d8d8`，不复制主题包其他系统组件的修改，也不声称逐像素复现整个主题。

## 菜单与配置

“调节 → 流体云”新增“锁屏样式”和“应用取色遮罩”。锁屏样式提供跟随系统、PUI 材质、自定义；自定义可独立调节模糊与混色。已有胶囊、卡片的设置保留。锁屏样式只匹配流体云胶囊数据和处于锁屏的自定义流体云卡片，普通通知胶囊不采用此样式。

应用取色遮罩默认开启，强度默认跟随系统。关闭遮罩后保留原始 RGB、把 alpha 设为 0；开启自定义强度后，`alpha = min(255, round(原始 alpha × 强度 / 100))`。范围为 0–200%，100% 为系统强度。渐变中每个色标独立计算，保留透明色标和渐变方向。关闭强度自定义、流体云总开关或模块总开关后恢复原始 alpha。

配置格式升级至 v19，继续接受 v3–v18。旧配置不启用新的锁屏材质覆盖，保留原生取色遮罩，不自动开启强度修改。配置导入对新增字段的完整性、类型和数值范围做校验。

## C17 接口与恢复

- `ViewBlurManager.requireBlurProxyForView` 的 `CAPSULE`、`CUSTOM` 类型用于标记背景所属视图；CUSTOM 通过 `isKeyguardMaterialColorState()` 选择锁屏或原有通知条材质。CAPSULE 检查 `CapsuleItemView.getEntry()` 是否为 `CapsuleSeedingData`，共用背景通过 `NotificationCapsuleContainer.getBaseCard()` 判断。
- 模糊后端沿用 `ShadeHook` 的参数临时覆盖与恢复。`CapsuleBackgroundHelper.drawCapsuleBg/getBackgroundColor` 覆盖无模糊回退；保留原生裁剪、描边、形状动画和 AOD scrim。
- 应用色背景由 `NotificationBackgroundCustomRowHandler.updateDrawableMetaColor` 从应用数据中读取原生颜色，再调用 `MetaBallBlurDrawable.setMetaColor` 或 `setGradientColors`。只在这个生产器的调用范围内调整 alpha，不把其他调用者的 AOD 和动画颜色识别为应用遮罩。
- `setMetaColor` 内部调用 `setGradientColors`，进入最外层 setter 后暂时清除生产器标记，避免重复缩放及错误保存已调整的颜色。退出和异常路径均恢复调用范围。
- 用弱引用保存已成功写入的原始单色/渐变命令。实时设置刷新从原始命令重新计算，避免拖动滑条时累积透明度；随后让现有背景失效重绘。后续 AOD 或动画调用颜色 setter 时移交所有权，停止重放旧应用色，直到应用色生产器再次写入。

遮罩控制针对上述 C17 原生 MetaBall 应用色路径。固件或设备不提供此路径、应用自身画入内容视图的背景不在此接口覆盖范围。

## 验证范围

`CloudLockSettingsTest` 检查配置往返、PUI 与自定义参数独立保留、旧版迁移、严格字段与范围校验。`scripts/check_effects.py --only CloudLockCheck` 用合成 Android/Oplus 替身执行实际 Java 处理器，覆盖单色和渐变、嵌套 setter、强度上限、实时刷新、关闭恢复、失败恢复、非目标颜色隔离、锁屏与下拉材质切换、普通通知排除、原生 Shader 与无模糊回退。

这些检查不代表已在手机上验证视觉效果、GPU 渲染、锁屏切换或 AOD 动画。需在 ColorOS 17 真机上确认流体云胶囊、展开卡片、应用更新及进出动画。
