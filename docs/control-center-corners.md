# 控制中心平滑圆角

自定义圆角同时覆盖背景路径、视图裁剪、模糊后端、描边和按压高光。所有尺寸磁贴、媒体 / 设备卡片和滑条共用实际半径与 G2 权重 3。圆角设置使用与通知条相同的 0–64 dp 半径，实际像素半径 = 设置值 × 屏幕密度，范围为此前 0–32 dp 的两倍；只在容器短边不足两倍半径时限制到短边一半。0 为直角，增大 dp 数值时各类型同步变圆，大卡片不会再因为尺寸大而使用更大的圆角。通知条、横幅与流体云仍使用各自的 dp 半径。

按每张背景短边缩放会使大卡片、小磁贴和滑条的实际半径不一致；将程度折半映射到 dp 又会与通知条相同数值的半径不一致。本次所有类型均使用 dp，并统一此前随原生类型变化的平滑权重。小磁贴在达到短边一半时自然成为圆形或胶囊，之后更大的设置值继续作用于容得下该半径的大卡片。固件的轮廓对象可能由多个尺寸共用，自定义轮廓继续按 Drawable 独立缓存；尺寸变化只检查边界限制，不放大圆角。

滑条保留系统按压放大比例、进度颜色和弹性动画；外轨与内轨保留原生内缩差。固件的 `baseMixColorDrawable` 和 `activeMixColorDrawable` 都绘制在 `mClipProgressRect`，两者都必须使用动画中的内轨半径；按压高光使用外轨边界和半径。绘制结束后恢复所有原生动画字段与权重。

## C17 固件依据

核对对象为本地导出的 `SystemUI.apk`，固件不随仓库发布。

- `SepQSResPool` 默认的磁贴轮廓是 `OvalOutlineProvider`。仅处理 `RoundRectOutlineProvider` 会漏掉真实的 1×1 圆形背景。模块标记可调整尺寸磁贴的主背景及过渡层，允许它们切换为平滑圆角；独立的小图标与三段指示器保留原生圆形。
- `RoundRectOutlineProvider.updatePath` 在有平滑权重且有路径适配器时调用 `addSmoothRoundRect`，否则调用普通 `Path.addRoundRect`。类名和 `isSmoothCorner()` 返回值不足以证明最终路径平滑。
- `OplusQsSmoothRoundUtil` 的设计半径表采用 G2 权重 3。自定义时统一使用该权重，包括原生空值、0、1 和其他平滑权重，避免相同半径仍呈现不同曲线。关闭后返回原生 provider 与权重。
- `OplusQSResizeableTileView` 的构造器创建 `bgDrawable`、`themeDrawable` 和 `TileTransitionDrawable`。后者实际继承 `TileLayerDrawable` / `LayerDrawable`，与普通背景包装器不是同一继承结构。递归标记它们的背景层，配置刷新时全部重建路径。
- `onDrawableUpdate` 分别声明在 `OneXOne`、`TwoXOne`、`TwoXTwo` 和 `OplusQSResizeableThreeStageView`，不声明在基类。所有四种实现都接入背景重建；已标记包装器替换子背景时继承标记并刷新模糊归属，覆盖主题和状态切换。独立图标与三段指示器保持原生。
- 设备中心 `SquareDeviceCardView` / `SquareEntranceCardView` 的背景由 `MixColorPluginDrawable.BuilderConfig.build(View, boolean)` 创建，父视图和内容视图可能各自缓存同一个原生轮廓。创建完成时跟踪 host，并在原生轮廓记录背景归属；即使第一次获取轮廓时自定义关闭，配置刷新也会使已有裁剪失效，首次启用就使用新半径，不再依赖收起重开。保留原生轮廓数据，关闭时恢复。
- `PluginDrawable` 基类构造结束时仅记录归属，不能调用子类的 `invalidatePath`，因为 `MixColorPluginDrawable.autoBlurDrawable` 此时尚未初始化。路径刷新与模糊绑定在完整背景创建后执行。
- `TileDrawableWrapper.ensureOplusPathAdapter` 会根据轮廓类型决定是否建立适配器。该调用点也要取消内联，避免从圆形切换后仍返回空适配器。
- 视图裁剪直接使用背景路径和背景边界，避免背景带内缩边距时按整个 View 重建轮廓而裁掉边缘高光。
- 模糊背景显式记录所属 Drawable；原生圆形 provider 同样转换为该背景的自定义轮廓，后端半径、四角和 G2 描边使用转换后的实际半径。
- `BlurConfig` 的 `radiusWeight`、`enableMotionSmoothCorner`、`pathProvider`，以及描边的 `CornerParams` 是独立输入。模块将这些输入与背景几何对齐，描边类型改为 G2；调用结束后恢复原配置。
- 1×1 的圆角接近短边一半时，原生路径会连续过渡为圆形，宽卡片则成为胶囊。此时描边必须从 G2 / 权重 3 切换为 FULL / 权重 1，避免圆形裁剪切掉仍按 G2 绘制的外轮廓。模块按实际背景边界判断是否达到几何上限，不使用原生提前 10 px 的“接近圆形”判定；未达到上限仍保持 G2。24、28、30、31、32、40、48、64 dp 的回归同时验证描边类型、半径、权重及配置恢复。
- 滑条和磁贴的 `updateSpotLightPath(Path, Object)` 可能收到空适配器。模块补建原生 `OplusPathAdapter(path, 1)`；滑条高光使用当前动画半径和轨道权重。
- `SharedSpotLightEffect` 只在按下和尺寸变更时复制 session 的裁剪路径；配置刷新时额外调用其 `updateBounds` 重建活跃高光，关闭自定义时同样刷新。
- 滑条本体通过 `mSmoothStyleType` 选择旧平滑算法或 G2。自定义绘制时同时启用平滑支持、G2 类型和平滑权重，结束后恢复，避免只改半径而继续走另一种裁剪曲线。
- 滑条绘制时的临时半径与权重不得写入原生工厂缓存。工厂接收原始参数，自定义几何应用于绘制及后端配置阶段。

## 验证范围

`scripts/check_effects.py` 中的 `QsGeometryCheck` 执行实际模块处理器，覆盖 0、16、30、31、40、48、63、64 的跨尺寸实际半径一致性、与通知条同值 dp 半径一致性、设备中心在原生轮廓缓存下首次启用和连续修改无须重新挂载、多尺寸共享原生轮廓隔离、四种磁贴与媒体卡片同步、子背景替换与缓存重建、带内缩边距的裁剪路径、原生圆形模糊后端与描边一致性、原生 null / 0 / 1 / 1.4 / 5 权重统一、主背景与内部圆形元素隔离、缺失按压适配器、G2 描边、滑条动画半径、内外平滑参数一致、工厂缓存与关闭恢复。

这些回归使用 Android/Oplus 替身，核对参数分支和恢复逻辑。2026-10-16 额外连接 PLQ110（Android 17 / ColorOS 17，560 dpi），从该机读取 SystemUI 固件并核对上述声明。早期 0–32 dp 方案的设备探针执行当时的 `QsCornerHook` 几何选择逻辑，再调用固件 `RoundRectOutlineProvider`、`OplusPathAdapter` 与 Android Path / Region，完成 36 组原生路径和栅格检查（四种尺寸，0、16、30、31、40、48、58、63、64 档位）；容器容得下所选半径时，角部像素一致。该探针在独立进程运行，不等同于新 APK 已注入 SystemUI；系统界面中的最终动画、背景重建和稳定性仍需安装本次包后验收。

最新 0–64 dp 与圆形描边修正通过 222 项 QS 几何检查（含 64 项圆形 / 胶囊描边切换与恢复检查）、完整效果回归、69 项单元测试和 release lint（0 错误）。原生设备探针另确认 216 px 方形的 G2 路径在半径达到 108 px 后成为圆形；大半径下继续使用 G2 描边与圆形背景不匹配，修正后按实际上限选择 FULL / 权重 1。最终签名包已生成，但 USB 设备在最终安装前断开，因此最新描边修正的实机界面验收待连接恢复后完成。
