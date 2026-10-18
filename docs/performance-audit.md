# 性能检查与等价优化（2026-10-18）

本次对 `app/src/main` 的 33 个 Java、33 个 Kotlin 文件做了全量静态扫描，并重点审查高频 Hook、绘制、动画、配置同步、后台任务和 Compose 状态路径。包含随项目引入的 8 个 Kyant 官方组件/工具文件；这些组件未修改。生成的图标资源及构建配置也纳入资源加载方式检查。

本次以“不改变功能”为约束：不降低动画帧率、不缩短或取消动画、不更改混色/饱和度曲线、不关闭功能、不修改默认值、保存时机、超时策略或宿主触摸处理。性能修改集中在 7 个 Java 文件。工作区已有的其他修改单独保留，不属于本报告的优化成果。

## 覆盖范围与处理

| 范围 | 检查内容 | 处理 |
| --- | --- | --- |
| `Reflect`、`HookCallback`、`Api102Runtime`、`HookEntry` | 每次调用的分配、方法缓存、实例身份、并发访问、异常恢复 | 优化反射热路径和调用附加状态，保留 API 102 注册及调用链语义 |
| `ShadeHook`、`PanelSaturationHook` | 下拉时的整组刷新、捕获首帧、混色缓存、滤镜及诊断字符串分配 | 移除无消费者的场景观察链；饱和度状态、矩阵、Paint 复用 |
| `VolumeHook`、`VolumeShortcutFeedback`、`VolumeLiquidEntrance`、`VolumePanelGeometry` | 每帧坐标数组、裁剪路径、原生状态/材质更新、取消与释放 | 复用坐标缓冲和不变的裁剪路径；原生状态轮询、动画及反馈开关继续执行 |
| `QsCardHook`、`QsCornerHook`、`NotificationCardHook`、`AdaptiveText*` | 绘制失效循环、临时颜色恢复、视图归属、文字背景查询 | 保留原有异常恢复及 drawable callback 隔离；通过公共反射优化受益 |
| `CloudHook`、`CloudLockHook`、`CloudSdkHook`、`CloudTemplateHook` | 各进程/加载器注册、颜色覆盖、动态 drawable、缓存清理 | 保留动态状态读取，避免缓存过期；通过公共反射和减少全局刷新受益 |
| `StatusBar*`、`Ios*`、`GestureBarHook`、`ResourceOverrides` | 时间刷新、布局重测、资源映射、blur 释放、弱引用 | 已有 formatter/资源映射缓存和失效保护保持原样；未增加定时器 |
| `Settings*`、`LiveSettings`、`RemoteSettings`、`SettingsProvider` | revision、合并写入、Binder、Provider 报告和退避 | 保留原有保存、同步、作用域刷新和重试语义，避免优化改变激活/错误恢复行为 |
| `MainActivity`、`UiComponents`、`AppTheme`、预览和导航 | 重组、位图创建、状态恢复、动画阶段读状态 | 图标位图已有 remember；手势循环是挂起等待。保持现有 MIUIx/Liquid Glass 实现 |
| `RootAccess`、`ScopeRestart`、`TestNotification` | 主线程阻塞、超时、子进程和一次性延迟 | 原有 IO 调度、去重及 finally 清理保留；未改 Root 请求或重启行为 |
| 构建和资源 | R8、资源加载、生成脚本是否进入运行时 | 保留 Release 的 R8 优化及现有 keep 规则；离线脚本不进入应用运行时 |

“保留”表示本次没有足够依据做行为等价且收益明确的修改，并不表示所有路径已经经过真机功耗证明。

## 实际修改

1. **反射查询**：带参数方法按类、方法名缓存不可变签名链。缓存命中时直接比较运行时参数类型，不再构造参数类型数组、List、Key 和捕获 lambda。继承查找、null、重载歧义和原生异常传播保持不变。`int` 字段使用 `Field.getInt/setInt`，同时保留 `Integer` 字段兼容路径。
2. **附加实例状态**：用锁内复用的查询对象访问弱身份键。查询对象从不放入 Map，并在 finally 中清除强引用；实际存储仍是 WeakReference + ReferenceQueue，仍按对象身份而非 equals 区分。没有改为强引用对象缓存。
3. **Hook 调用局部状态**：只有第一次 `setObjectExtra` 才创建 HashMap。没有附加状态的 Hook、关闭功能的快速返回和只读取不存在状态的 after 回调不再先分配一个空 Map。不同调用和嵌套调用仍各自隔离。
4. **控制中心刷新**：旧 `qsManager` 没有读者；`qsVisible` 只供自身比较后调用全局 `refreshProxies`，没有参与材质选择。控制中心和通知中心已共用配置。因此移除该构造器/getBlurFraction 观察链，避免每次开合都重建通知、云控件、音量等全套效果。配置变更刷新、原生 blur 提交和首帧处理仍在。
5. **饱和度**：每个 drawable 使用一个 Tracking，保存弱来源引用和原始 float 进度；相同来源不再重复创建 WeakReference，不再按帧装箱进度。复用 ColorMatrix 和 layer Paint，只有饱和度改变时创建新的不可变 ColorMatrixColorFilter。保留原有 smoothstep、精确浮点进度、shader/bitmap 两条路径、原生滤镜叠加及异常恢复。诊断文本按配置快照变化生成。
6. **音量坐标**：Compact 在视图线程复用一个 `int[2]` 作为坐标读取缓冲，立即返回单个坐标值；每帧仍查询实际位置，未缓存可能过期的视图位置或父链。
7. **按压反馈裁剪**：宽、高、圆角都不变时复用原生裁剪路径。任一参数改变立即调用同一个原生 shape helper。已核对本地 C17 原生实现，此 helper 的路径只依赖传入参数。触摸、主题/启用策略、native drawable 绘制和 detach/release 继续执行。

## 分配量对照

基线为本次修改前的源码快照 `.local/tmp/performance-baseline/app/src/main/java`，两边使用同一份 `HookPerformanceCheck`、D:\java17 和合成 Android/Oplus 环境。每项先预热 200,000 次，再测 3 轮、每轮 200,000 次；使用 ThreadMXBean 统计当前线程分配字节。

默认 HotSpot 优化下，各轮结果：

| 热路径 | 优化前 bytes/op | 优化后 bytes/op |
| --- | ---: | ---: |
| 附加实例状态读取 | 32 / 32 / 32 | 0 / 0 / 0 |
| 一个参数的方法调用 | 96 / 96 / 96 | 7.7 / 0 / 0 |
| 静止面板饱和度 track | 64 / 64 / 64 | 0 / 0 / 0 |
| `int` 字段读取 | 0 / 0 / 0 | 0 / 0 / 0 |
| Object 字段读取 + 无参调用（未改路径） | 16 / 16 / 16 | 16 / 16 / 16 |
| 文字背景查询（未改路径） | 0 / 0 / 0 | 0 / 0 / 0 |

为区分源码优化与 HotSpot 逃逸分析，另用 `-XX:-DoEscapeAnalysis` 对照。一个参数调用仍保留反射所需的 Object[]，不能把主机 JIT 消除它的结果理解为手机上必定零分配。

| 关闭逃逸分析的路径 | 优化前 bytes/op | 优化后 bytes/op |
| --- | ---: | ---: |
| `int` 字段读取 | 16 | 0 |
| 附加实例状态读取 | 32 | 0 |
| 一个参数的方法调用 | 152 | 24 |
| 静止面板饱和度 track | 64 | 0 |

上述四项各自 3 轮分配量一致。Object 字段读取和文字背景查询在这组对照中分别仍为 16 和 24 bytes/op，前后不变。

这些数据是主机微基准，不代表 ART、GPU、整帧分配量或耗电比例。没有改动的路径也列出，避免把已有优化算作本次收益。纳秒耗时受 JIT、构建并发和主机调度影响，不据此宣称手机性能提升百分比。

原生 clip 调用计数检查：静止的 120 次 draw 从每次调用 helper 变为仅首次调用；改变宽度或圆角都会重新构建。旧源码运行新检查会失败，新源码通过。

## 回归验证

- `python scripts/check_effects.py --benchmark`：32 个检查程序及 6 种模块入口场景；覆盖颜色连续性、通知展开、音量展开/反向/取消/重绑定、材质、按压、云控件、状态栏和手势条。
- `ReflectCheck` 新增 boxed int、null 与已缓存重载、身份相等但不同实例、6 线程共 30,000 轮附加状态读写、ReferenceQueue 清理检查。
- `ShortcutPerformanceCheck` 覆盖静止缓存、尺寸/圆角变化、触摸转发、主题禁用、detach/attach、release。
- `HookCallbackTest` 直接测试生产 Callback 的惰性分配、嵌套调用隔离及传入 Map 兼容性。
- `HookCheck` 验证开合控制中心时配置结果不变、不新增全局刷新任务，同时保留设置刷新时原生 proxy 重新应用的断言。
- JVM 单元测试共 86 项，失败、错误、跳过均为 0。Release 构建包含 R8 和 lintVital；首次增量编译因历史 `.class` 产物缺失失败，使用 `--rerun-tasks` 完整重建后通过。

测试中的 Android/Oplus stand-in 不能代替真实屏幕效果验证。代码检查和主机分配测量已经覆盖本次修改；实际耗电、ART GC 和 GPU 帧耗时尚无安装后 A/B 结论。本次未安装 APK 或重启用户设备。

## 复现

```powershell
$env:JAVA_HOME='D:\java17'
python scripts/check_effects.py --benchmark
python scripts/check_effects.py --only ReflectCheck --benchmark --java-source-root .local/tmp/performance-baseline/app/src/main/java

# 可选：关闭主机逃逸分析，观察仍然存在的 varargs 数组等分配。
$env:JAVA_TOOL_OPTIONS='-XX:-DoEscapeAnalysis'
python scripts/check_effects.py --only ReflectCheck --benchmark
```

构建使用已安装 SDK 的 `.local/android-sdk.init.gradle`，离线运行。普通构建配置没有因本次优化改变。
