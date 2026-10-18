# 锁屏流体云与应用取色遮罩

锁屏样式只提供自定义模糊与混色，使用与其他背景一致的单次 alpha 混色算法。关闭对应开关即恢复系统参数，不再提供主题预设。

## 菜单与配置

“调节 → 流体云”提供“锁屏样式”和“应用取色遮罩”。锁屏样式直接显示自定义模糊、混色开关与参数；关闭对应开关恢复系统值。已有胶囊、卡片的设置保留。锁屏样式只匹配流体云胶囊数据和处于锁屏的自定义流体云卡片，普通通知胶囊不采用此样式。旧配置中的预设值 1 按自定义值 2 读取，采用已保存的自定义参数，不再生成固定材质。流体云外观总开关和模块总开关仍控制该功能。

应用取色遮罩只保留一个“自定义遮罩强度”滑块，范围 0–200%，默认 100%。0% 关闭遮罩：保留原始 RGB、把 alpha 设为 0；其他强度按 `alpha = min(255, round(原始 alpha × 强度 / 100))` 计算。100% 为系统强度。渐变中每个色标独立计算，保留透明色标和渐变方向。流体云总开关或模块总开关关闭后恢复原始 alpha。旧配置的遮罩关闭状态显示为 0%，未启用强度自定义时显示为 100%；调整滑块后直接应用选择的强度，无需额外开关。

配置格式升级至 v19，继续接受 v3–v18。旧配置不启用新的锁屏材质覆盖，保留原生取色遮罩，不自动开启强度修改。配置导入对新增字段的完整性、类型和数值范围做校验。

## C17 接口与恢复

- `ViewBlurManager.requireBlurProxyForView` 的 `CAPSULE`、`CUSTOM` 类型用于标记背景所属视图；CUSTOM 通过 `isKeyguardMaterialColorState()` 选择锁屏或原有通知条材质。CAPSULE 检查 `CapsuleItemView.getEntry()` 是否为 `CapsuleSeedingData`，共用背景通过 `NotificationCapsuleContainer.getBaseCard()` 判断。
- 模糊后端沿用 `ShadeHook` 的参数临时覆盖与恢复。`CapsuleBackgroundHelper.drawCapsuleBg/getBackgroundColor` 覆盖无模糊回退；保留原生裁剪、描边、形状动画和 AOD scrim。
- `smoothSharedViewBlurProxy` 来自复用池，宿主是容器中的临时视图。绘制前将本地、共享代理绑定到当前胶囊，以当前胶囊的数据选择材质；配置与目标类型缓存放在代理上，避免另一胶囊复用代理后，原胶囊误以为配置仍已应用。目标不变的后续帧不重复重建材质。
- 应用色背景由 `NotificationBackgroundCustomRowHandler.updateDrawableMetaColor` 从应用数据中读取原生颜色，再调用 `MetaBallBlurDrawable.setMetaColor` 或 `setGradientColors`。只在这个生产器的调用范围内调整 alpha，不把其他调用者的 AOD 和动画颜色识别为应用遮罩。
- `setMetaColor` 内部调用 `setGradientColors`，进入最外层 setter 后暂时清除生产器标记，避免重复缩放及错误保存已调整的颜色。退出和异常路径均恢复调用范围。
- 用弱引用保存已成功写入的原始单色/渐变命令。实时设置刷新从原始命令重新计算，避免拖动滑条时累积透明度；随后让现有背景失效重绘。后续 AOD 或动画调用颜色 setter 时移交所有权，停止重放旧应用色，直到应用色生产器再次写入。

- 插件模板音乐卡使用 `MediaBackGroundView` 的 `component.light.f`，页面底层使用 `PageBackgroundView → page.view.a → component.light.f / animator.drawable.b`。这些取色平面独立于 MetaBall 和 Seedling SDK，不能仅修改 MetaBall 的颜色。
- 原生 MultiLight Shader 丢弃输入颜色的 ARGB alpha；在其 `draw(Canvas)` 内临时缩放阴影数组 `i[0,1]`、边缘数组 `n[10,11]`、扩散数组 `t[4]`。按每帧原值计算并钳制至 0–1，绘制完成或异常后只恢复这些透明度，保留原生动画进度，并标记 uniform 待更新。
- Page 的圆形脉冲会在 draw 内重写 Paint alpha，因此在原生 draw 前缩放动画字段 `c/d`，之后恢复。仅通过 Drawable callback 链匹配上述背景宿主，文字、封面、按钮及其他模板光效不受影响。实时设置使已发现的背景失效重绘。

遮罩控制覆盖上述 C17 原生 MetaBall、独立 Seedling SDK 和插件模板背景路径。应用自行画入内容视图的其他背景需按实际固件接口单独确认。

## 验证范围

`CloudLockSettingsTest` 检查配置往返、旧预设迁移到已保存的自定义参数、旧版迁移、严格字段与范围校验。`scripts/check_effects.py --only CloudLockCheck CloudSdkCheck CloudTemplateCheck` 用合成 Android/Oplus 替身执行实际 Java 处理器，覆盖单色和渐变、嵌套 setter、强度上限、实时刷新、关闭恢复、失败恢复、非目标颜色隔离、锁屏与下拉材质切换、普通通知排除、原生 Shader、无模糊回退，以及共享代理在流体云与普通通知间反复复用时的材质刷新和逐帧缓存。

这些检查不代表已在手机上验证视觉效果、GPU 渲染、锁屏切换或 AOD 动画。需在 ColorOS 17 真机上确认流体云胶囊、展开卡片、应用更新及进出动画。

2026-10-18 设备审查：已从连接手机的 SystemUIPlugin 提取并确认上述模板字段与调用链；安装新版并重启 SystemUI 后，运行报告命中 `cloud-template-app-color=f strength=4%`，error 为空。32 项模板专项检查及现有全部合成运行检查通过，79 项单元测试通过。此记录仅确认当前音乐模板取色路径的加载与命中，0%/100% 同场景视觉对比仍需解锁手机完成。
