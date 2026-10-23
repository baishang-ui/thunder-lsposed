# 展开卡片拖动与输入修复（2026-10-23）

用户确认的问题是拖动展开后的卡片。此次修改全部属于 SystemUI 宿主 Hook，不涉及独立 App 的界面组件。

## 审查范围与依据

检查了 Motion 原生动画监听、共享 CloudMorphSurface、卡片层绑定、父容器滚动与位置变换、画面同步、坐标反变换、原生触摸转发、窗口输入租约、OUTSIDE 收起及打开/关闭反转。

从连接手机提取当前 SystemUIPlugin APK，结合 Androguard 反编译输出检查 CardContainer、InstantContainer、O3 手势反馈与根窗口收起事件。普通卡片的父 RecyclerView 会改变子项位置或滚动值；InstantContainer/O3 还会直接修改原生 View 的缩放、位移。根窗口接收 ACTION_OUTSIDE 后会投递状态机事件，返回 handled 不代表状态机已经接受关闭。

## 根因与改动

1. 原来的反馈只复制卡片自身 scale/pivot/translation，没有追踪父容器位置和滚动。新增可复用矩阵及浮点坐标缓存，记录端点时的父级全局原点，将后续父级移动、scroll、子项布局偏移作为增量同步到独立呈现层。端点重算时重新锚定，避免配置/布局变化重复累计位移。
2. 原生窗口 preDraw 与独立呈现窗口的绘制并非同一遍历。触摸转发完成后立即同步反馈；独立 Body 绘制前再采样一次，绘制与输入区域使用同一 View 变换。监听随 Body 挂载/卸载注册和移除，不引入定时轮询或持续主动重绘。
3. 卡片展开期间，后台胶囊宽度、数据、出现/消失场景会竞争同一个共享 surface，覆盖卡片的画面和输入状态。卡片打开中和展开时保留其呈现所有权；胶囊事件继续执行原生生命周期，只更新共享内容。真实卡片关闭开始后允许所有权交接；原生卡片窗口卸载或隐藏后也不保留过期所有权。
4. 原实现把 OUTSIDE 的 dispatch 返回 true 当作关闭成功，立刻锁住整棵输入树。若状态机延迟或拒绝关闭，卡片仍显示但再也不能操作。现在只有真实 beginTransition(close) 才释放卡片输入；删除可提前锁死输入的 dismiss 入口，保留关闭反转与原生窗口延迟清理语义。

## 回归与验证

- 新增回归在修复前分别复现父容器移动未跟随、胶囊场景覆盖展开卡片，保留本地 RED 日志。
- 全量 scripts/check_effects.py：通过；其中统一对象生命周期 3015 项，原生卡片反馈 35 项，另包含输入所有权、关闭反转和后台胶囊场景回归。
- Android 真机 instrumentation：胶囊像素及透明层检查 42 项、卡片祖先/输入矩阵检查 20 项通过；真实 MotionEvent 输入桥检查通过。覆盖祖先小数位移、scroll、子项 offset、缩放/pivot、矩阵逆变换、端点重锚及卡片重绑。
- Debug/Release 构建、125 项 JUnit 单元测试通过（0 失败、0 跳过）；Release lint 无 error，有 37 条 Warning。
- 连接的 PLQ110 / Android API 37 上安装签名 Release，重载 SystemUI，检查真实计时器、秒表和音乐卡片。执行卡片区域拖动、空白收起和再次展开；收起后输入区域回到胶囊，再展开的 native dump 为 CARD_STATE_MANUAL，isBlockedUpdate/isBlockedAnimation 为 false。

这些检查验证了此次修复涉及的状态与坐标链路，不构成全部业务场景的长时间稳定性或实测帧率结论。InstantContainer 的手势实现已反编译审查，但此次没有单独构造可持续的原生 instant 业务卡片做完整交互压测。ART ns/tick 探针只测轨迹计算 CPU 开销，不代表屏幕 FPS；不使用前后不同业务负载的 gfxinfo 汇总宣称性能提升幅度。

## 交付与本地证据

- 安装包：dist/thunder-cloud-drag-fix-20261023.apk（附 SHA-256 文件）。
- 全量回归：.local/cloud-drag-final-effects.log。
- 最终构建：.local/cloud-drag-final-verification.log。
- 真机检查：.local/cloud-drag-device-checks.log。
- 修复前失败：.local/cloud-drag-parent-red.log、.local/cloud-drag-scene-red.log。
- 原生状态与窗口：.local/cloud-drag-fixed-reopen-dump.txt、.local/cloud-drag-fixed-outside-windows.txt。

保留原有 docs/cloud-cleanup-2026-10-22.md 的工作区改动，没有覆盖或回滚。
