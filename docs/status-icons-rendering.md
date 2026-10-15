# 状态栏图标：保留系统对象的视图绘制方案

## 故障依据与边界

用户反馈开启图标替换会使 LSPosed 进入安全模式。仓库历史日志及已核对 LSPosed 2.2.1 (7912) 的保护策略表明，SystemUI 短时间连续退出会累计保护次数，详见 [安全模式说明](lsposed-safe-mode.md)。目前没有本次故障对应的崩溃堆栈，也没有连接目标手机，不能把某一处 Hook 断言为此次崩溃的唯一原因。

旧方案在 `getIcon`、`setImageResource` 和 `setImageDrawable` 中用 `DrawableWrapper` 改变系统持有的 Drawable 类型与回调链，并修改测量参数、电池模型、重新调用电池 binder，以及覆盖 `StateFlowImpl` 的双卡排列状态。框架的 Hook 异常保护只覆盖拦截器内部；调用者后续对返回 Drawable 的类型假设、绘制和异步重新绑定不在同一个保护边界内。新增 `IosDrawSafetyCheck` 在旧代码上因“原生图标对象被替换”失败，确认这个行为存在；这不是已取得手机崩溃堆栈的证明。

## 新实现

- 资源边界只把已识别的符号记录为原生 Drawable 的附加元数据，返回对象、类型、Callback、动画及内部状态都不改。
- 在 `ImageView.onDraw` 和 `StatusBarIconView.onDraw` 中限定状态栏视图，读取图形来源、信号编码、原生 tint / ColorFilter / alpha，在原来的图标区域内居中绘制。通知应用标识及非 SystemUI 资源排除；原生溢出圆点与完全隐藏状态沿用系统绘制。
- C17 Compose 堆叠信号保持原生 VectorDrawable 与 painter，只在已标记的堆叠资源的 `VectorDrawable.draw` 边界绘制对应行的图形；不 Hook Compose 函数或反优化代码，也不修改资源返回值和尺寸。切换样式通过原 Drawable 回调重绘，其他矢量图形不受影响。
- 电池在 `StatBatteryMeterView.dispatchDraw` 读取现有 `StatBatteryIcon`、电池样式与充电模型，输出实心百分比图形。模型、原生 Drawable、子视图可见性及布局不改，也不重新调用 binder。
- 绘制在 Canvas 的 save / restore 边界内执行，全部成功才跳过原绘制。任何渲染或接口异常会记录 `status-bar-ios-draw-fallback` 并隔离当前视图，恢复系统绘制，不循环重试。其他视图继续工作。
- 配置变化只发起 `postInvalidateOnAnimation`。视图在系统样式下也会被弱引用跟踪，因此开启、关闭均可实时重绘，不依赖图标状态变化或重启。

撤掉旧的 Drawable 包装、ImageView setter Hook、状态栏测量与时钟字体修改、电池 binder Hook、Compose scope / 反优化和 StateFlow Hook。没有调整 LSPosed 保护阈值，也没有添加进程终止动作。

## 功能取舍

尺寸、间距、时间字体、双卡分开或堆叠排列均由系统控制。原强制双卡排列设置保留配置兼容性，但不再提供修改入口或强制应用。已识别的 C17 Compose 堆叠矢量图形可换样式，但不改变系统排列；其他绘制后端保留系统图形。隐藏 / 仅百分比的电池模式、反向充电指示、未初始化或无法识别的模型沿用系统绘制，避免覆盖系统提示。已识别状态图标、独立与堆叠信号和正常电池继续使用 iOS 图形。

由于保留系统原有容器，窄电池样式下百分比图形会按可用空间缩小；预览只展示图形，不代表目标手机的最终尺寸。

## 验证

```sh
python3 scripts/check_effects.py --only IosDrawSafetyCheck IosStatusBarCheck IosRenderPathCheck IosCoverageCheck
python3 scripts/check_effects.py
python3 scripts/check_live_settings.py
python3 scripts/check_systemui_restart.py
./gradlew :app:testReleaseUnitTest :app:lintRelease :app:assembleRelease
```

检查使用合成 Android / Oplus 环境，验证真实 Java 处理器的对象保持、状态读取、关闭恢复、绘制异常隔离及 Canvas 恢复。图形资源检查覆盖 298 个固件资源映射，不能推出每一种系统渲染路径均已在手机命中。异常测试刻意注入渲染失败，允许报告一次回退日志。完整安装后的开机注入、深浅色切换、双卡 / 充电 / 反向充电、样式反复切换与长期稳定性仍需 ColorOS 17 真机验证。

安装更新后需要正常重启手机以卸载旧 Hook、加载新实现。若框架已进入安全模式，先按框架管理器要求恢复模块状态，再正常重启手机；应用中的样式开关不能卸载已经注入到旧进程的 Hook。
