# 状态栏流体云动画链路审查（2026-10-21）

本轮以当前手机提取的组件和进程日志为依据，录屏仅用于末端验证。设备上的 `SystemUIPlugin.apk` 与此前采集件 SHA-256 相同：`43fc34f7d4aa9ce1882e454e449d84d610a9454cf36fe8c7c5fc97954b678372`，排除了混用不同插件版本的假设。

## 实际链路与职责

| 阶段 | C17 消费者/入口 | 本项目处理 |
| --- | --- | --- |
| 按下与松手反馈 | CapsuleContainerRoot.onTouchEvent → CapsuleContainer.m / n → plugins.r8.O3.a / b | 保留原生按压与松手动画。触摸处仅保存形变锚点，不消费点击 |
| 点击状态 | CapsuleContainer 的 capsule.ui.model.a → capsule.ui.viewmodel.u → 状态机 | 保留原生事件；不调用替代点击或重放事件 |
| 场景选择 | animatorscenes.o → C0 的具体子类 | 按 `C0.f()` 的语义枚举限定状态栏展开/收起，包含 y / J0 的留白分支 |
| 挂载与布局 | C0.p；card.window.d.j / f.k 排队至布局后调用继续函数 | 不暂停原生动画，不阻断挂载，不替换系统事件时钟 |
| 图构建 | animatorscenes.B0.invoke → h.t / 组合片段与 Z 条件时钟 | 标记外层原生图；不假设所有入口都经过 t / Q |
| 原生播放 | animatorengine.h.s → h.g → Animator.start | 返回同一个原生 Animator，只追加可清理的视觉观察器 |
| 胶囊几何 | CapsuleContainer.f(key) 的 CapsuleView；mini 形态在 getMiniView | 同时支持两种承载。目标来自场景的服务 key、测量尺寸或宽度动画末段；Drawable 的瞬时覆盖宽度不作为最终尺寸 |
| 卡片材质 | CardView 内 CardBackgroundView；InstantContainer.getInstantView 上的 ViewRootManager | 对实际材质面统一改尺寸、位置与原生平滑圆角 |
| 每帧视觉 | 窗口 ViewTreeObserver.OnPreDrawListener | 在原生属性更新后覆盖同帧的缩放、位移、alpha、轮廓、遮罩和过渡 blur，避免两套画面接力 |
| 收起隐藏/卸载 | card.window.i.j(false) / i.e() | 仅在本次收起仍未完成时延迟；视觉达到胶囊终点后执行一次。被新展开打断时丢弃旧隐藏，避免移除新卡片 |
| 末段内容交接 | 原胶囊与已缩小卡片 | 收起最后 20% 的几何进度逐步恢复真实胶囊内容，淡出相近几何的卡片，不留空白胶囊等待 end |

原生卡片窗口 `card.window.i.f(boolean)` 创建全屏 `-1 × -1` 的 `SeedlingCard` 窗口，并启用 cutout、清空 fitInsets；因此此固件不需要通过新建窗口绕过状态栏边界。父级裁剪在本次视觉动画中引用计数释放，结束时恢复。

## 已由手机日志确认的问题

1. 报告一度仍属于旧 PID；重新打开模块使 Provider 可见性与报告通道恢复后，以新 SystemUI PID 的记录分析。旧报告不能用来认定新代码是否命中。
2. 当前手机曾记录 `cards=1`、真实胶囊尺寸 `465×118`，但动画停在 `capsule anchor missing`。反编译显示 `getCapsuleDrawableWidth()` 直接返回字段 `i`，这个动画覆盖值可以合法为零。前几版把零当作胶囊不可用而不断放弃。现在覆盖宽度无效时回退真实测量尺寸，点击前的锚点按容器保存，跨形态切换仍可用于收起。
3. mini 形态还可能保留一个 `45×45` 的空占位 CapsuleView，真实内容在 getMiniView。不能只通过类名选起点。
4. 修正锚点后，手机首次出现 `cloud-morph-started` 与 `cloud-continuous-morph`，并记录 `1159×294 → 438×119` 的收起尺寸；随后暴露 `IllegalArgumentException: not implemented`。旧清理器保存了从未修改过的 colorFilter，结束时调用 SDK Drawable 不支持的 setter。已改为只保存/恢复真正修改的几何属性；新进程报告 error 为空。
5. 可调时长接通后，手机报告实际使用 `1000ms`，同时命中 `cloud-morph-window-handoff`，验证收起窗口隐藏确实经过延迟交接。用户保存的时长、曲线与回弹值原样保留。

## 配置与 UI

独立 App 的“流体云样式 → 展开与收起动画”提供优化开关、120–1000 ms 时长、四种曲线和 0–100% 回弹。控件复用现有 MIUIx 选择行和官方 Liquid Glass 开关、滑块。系统设置入口由相同 schema 生成原生 COUI Preference。参数经 SettingsWriter / RemoteSettings、SettingsProvider 到 HookConfig，下一次动画使用不可变快照，关闭开关即时释放视觉工作。

配置导出升级为 v24，严格检查新增字段类型与范围；v3–v23 导入保留旧字段并为新增动画参数补默认值。默认 300ms、iOS 式缓出、回弹 0；关闭动画优化时完全采用原生路径，按压反馈始终保留。

## 回归依据

- 测试从原生 start 才创建卡片，验证原生对象身份和生命周期不变。
- 重放 `45×45` 占位 mini 与 `465×118 / override=0` 的实际失败条件，观察修改前失败、修改后通过。
- 在视觉时钟之后写入原生缩放、隐藏、blur、压缩轮廓与遮罩，再触发 pre-draw，验证旧分段无法覆盖画面。
- 收起中途让原生 end 请求隐藏窗口，验证卡片还会继续缩小，终点后只隐藏一次。
- SDK Drawable 的 colorFilter setter 抛出与设备一致的 not implemented，验证几何清理不再调用它。
- 设置往返、v23 迁移、未知字段/缺失字段/越界值拒绝、原生设置 schema 与曲线端点/回弹边界单独测试。

日志与短录屏证明当前测试场景命中，不等于所有应用模板、横屏、锁屏与 GPU 性能都已完成验收。锁屏和通知中心不在本次动画接管范围内。

## 2026-10-21 入口与隐藏的补充核对

当前固件的 `APP_TO_CAPSULE`（`animatorscenes.e`）由旧/新 `CapsuleView` 的 `capsule.animation.s/F` 和状态栏协同片段 `N` 组成；`APP_TO_CAPSULE_WITH_NONE`（`f`）使用胶囊 `j`、状态栏 `N` 和条件片段 `Z`；`CAPSULE_APPEAR`（`h`）使用胶囊 `i/Wj`。这些图不包含独立应用窗口或 SurfaceControl 的形变。`N` 调用 `notifyAnimationPrepare` 并发送状态栏动画数据。首次入口的 600ms 状态栏协同与 950ms 条件等待不等同于胶囊形变时长，因此保留用户设置的视觉时长，不把条件等待拉长为视觉动画。

入口预留改为先认领持久 `CapsuleContainer`，不再要求 `p()` 尚未绑定的 `CapsuleView` 已存在。首个内容布局时即隐藏原生实体并建立透明的统一表面，直到真实原生入口图开始再推进视觉时钟。初始轮廓从中心的小圆点形变为胶囊，取代此前 95% 胶囊大小的弱缩放；已有服务替换仍继承当前表面、位置和速度。host/business 隐藏与 `CAPSULE_DISAPPEAR` 使用中心收缩几何，透明度只在末段退出。

`CloudMorphCheck` 重放“预处理时无 CapsuleView → 原生绑定与布局 → 原生图启动”，确认不会提前展示入口终点，并覆盖小圆点入口、业务隐藏和保留当前服务替换轮廓。`CloudVisibilityStateCheck` 单独检查几何压缩、固定中心、反向中断的一致性与末段透明度。这些检查验证 Hook 状态和时序，不代替所有应用的真机视觉验收。

### 滑动与已有服务替换的目标追踪

当前 APK 的 `animatorscenes.e.c` 与 `m.c` 分别读取 `container.f(container.l)` 旧服务和 `container.f(event.e.z().b)` 新服务。`p()` 绑定时可以保留旧 `l`，所以持续读取 `l` 会让新文本已显示时，统一胶囊仍停在旧服务尺寸。`m.c` 还明确比较新服务的 measured/layout 大小，必要时按父容器测量中心布置新视图，证明动画期间 `getWidth()` 不是可靠终点。

宽度变化场景 `A.c` 将 `fluid.i.e.c` 交给 `r8.t6` 和 `capsule.animation.P`。`P.g` 顺序播放 `animationData.d` 的段，每段 `d()` 为起始宽度、`c()` 为结束宽度；原生每帧改 `CapsuleView.setCapsuleDrawableWidth` 的背景边界，无需同步改布局宽。Hook 现在随原生 Request 保存明确的新服务 key 和末段宽度，接入 `m/A/j.c` 的直接图路径，并保持同一个 Body 从当前轮廓连续走向新目标。原生文本横滑、月牙和内容事件仍由原生图推进。

`syncCapsule` 在图运行中追踪同一个目标，避免旧 key 的布局回调拉回旧尺寸；原生结束后保留尚未确认的目标，直到测量赶上或新的真实选择/测量取代它。预留中的 `y(false,*)` 不再先藏掉已有胶囊。若旧业务隐藏已运行到中段，入口从实际绘制矩形和 alpha 接手，取消旧业务门的第二条恢复形变；控制中心的真实 host(false) 门继续生效。退场同样从实际绘制状态接手，只运行一条中心收缩。

新增回归覆盖旧 key/旧布局与新测量并存、首测量早于布局、原生宽度段与瞬时 Drawable 宽度不同、原生结束后迟到旧布局、真正新选择/新测量解除目标、业务隐藏中断入口和 host/business 隐藏重叠。首次失败保存在 `.local/cloud-native-target-red.log`，随后 `CloudMorphCheck` 的 696 项与多卡片、可见性及触摸所有权回归通过；平台绘制和不同应用模板仍需要真机复核。

可见性与场景交接同时传递实际展示矩形、透明度和几何导数。退场已经接手显示后，若控制中心关闭并紧接新服务进入，重新建立宿主动画起点但保留关闭目标；新场景扣除宿主动画初速度，避免二次压缩或速度叠加。线性曲线的方形迷你胶囊可显式保留补偿后的零速度，普通线性场景仍按原规则运行。宿主恢复时透明度限制连续解除。四种曲线的组合微步、隐藏期间不重现及中途恢复回归通过；关闭速度补偿或零速度保留的源快照均能重新触发失败。
