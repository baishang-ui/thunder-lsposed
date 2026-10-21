# 流体云统一动画与交互 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development. Steps use checkbox syntax for tracking.

**Goal:** 保留同一独立对象，补齐胶囊生命周期、连续形变、前台进入、样式与原生交互。

**Architecture:** 原生状态机负责业务，CloudMorphHook 观察事件并维护会话和运动。CloudMorphSurface 持有窗口/原生材质/绘制与输入适配；运动与坐标映射抽取可测试纯逻辑。

**Tech Stack:** Java 17、Android View/WindowManager、Oplus 原生材质、API102 Hook、现有 Kotlin 设置。

**Spec:** docs/superpowers/specs/2026-10-21-cloud-unified-motion-design.md

## Global Constraints

- 现有未提交改动是本次基线，不回滚、不批量提交、不更改无关文件。
- 同一会话同一 Body；不恢复状态栏视觉依赖；原生业务 Animator/事件继续执行。
- 胶囊和卡片参数分别生效，保存的 552ms / 曲线 2 / 回弹 0 不重置。
- Hook 使用原生宿主组件；独立设置若改动则遵守 MIUIx/Liquid Glass。
- 真机调试已授权，测试不改变秒表等业务数据；记录实际验证和未覆盖范围。

## Review Focus

- 重复点击后根容器状态与视觉目标错位：Task 2 验证根路径和逆变换。
- 初次出现与服务内容替换不经过卡片：Task 1 验证 capsule-only 初始化及更新。
- 取消后反向从同一位置继续、旧卸载不误伤：Task 1 覆盖代次与连续速度。
- 固定窗口透明区域吞触摸/blur 越界：Task 2 检查 region 和设备实际材质。
- 关闭/锁屏/退出应用路径留下隐形窗口：Task 1 与 Task 3 均验证窗口清理。

## Task 1: 场景会话和连续运动（主执行者）

**Files:** CloudMorphHook.java、CloudMorphTiming.java、新 CloudMotion.java、CloudMorphCheck.java、CloudMotionCheck.java、平台 fixture、scripts/check_effects.py。

**Interfaces:** 保留 Frame(x,y,width,height,radius,progress)。Surface 提供 current()/disposed()/show()/frame(Frame)/settle(boolean)/dispose()，bind(View capsule, View nullableCard, View nullableOwner, boolean instant)；新增 syncCapsule(View) 刷新内容；胶囊会话可 card=null。

- [ ] 记录当前专项检查基线，新增首次 CAPSULE_APPEAR/APP_TO_CAPSULE 无卡片就建立同一 Body 的失败断言；新增目标切换、宽度更新、退出及取消测试。
- [ ] 新增 CloudMotion 的反向位置/速度连续、回弹 0 和 100 边界、不同刷新间隔的失败测试；先运行观察失败。
- [ ] 接入原生 C0 场景/胶囊生命周期；以独立入口解析胶囊，不误读卡片窗口字段；无动画与退出路径按本会话收敛/释放。
- [ ] 运动逐帧读取实际时间，继承呈现状态与速度；内容 progress 限幅，圆角使用胶囊/卡片各自配置。
- [ ] 更新 fixture 的平台接口，运行专项实际处理器检查，记录红绿结果；不改 Task 2 文件。

## Task 2: 持久窗口、样式和原生输入（独立 implementer）

**Files:** CloudMorphSurface.java、新 CloudContentTransform.java/CloudContentTransformCheck.java、必要的输入/材质小型适配；只提交本任务负责的文件，主执行者统一合并脚本清单。

**Interfaces:** 遵循 Task 1 Surface 接口；nullable card/owner 不抛错；syncCapsule(View) 更新服务内容源；不更改 CloudMorphHook/Timing/配置。

- [ ] 为生产绘制逆变换写纯逻辑检查：不同宽高、居中、缩放、平移、边缘点击、多段手势。先运行失败，再实现并绿测。
- [ ] 窗口在一段运动中固定承载范围，内部 Body 原生材质随视觉 Frame 改变；透明区域 touch region 限定到实际形状。避免每帧 WindowManager resize 和稳定后自激重绘。
- [ ] 胶囊端读取胶囊配置，卡片端读卡片配置；热更新、mini/真实内容替换及 native alpha 所有权正确恢复。不能用卡片材质固定替代胶囊。
- [ ] 输入通过真实根容器分发；整段手势固定目标，使用绘制逆变换，替换/销毁发 CANCEL；保留状态栏纵向回调、外部点击和返回职责，辅助功能转发动作和正确节点边界。
- [ ] Android 编译并自审，在报告中写清平台行为必须真机验证的部分。

## Task 3: 集成、真机验证和文档（主执行者）

**Files:** 上述集成修正、docs/cloud-morph.md、docs/cloud-animation-chain-audit.md、本计划进度；.local 保存证据。

- [ ] 运行专项和完整 scripts/check_effects.py、assembleDebug、testDebugUnitTest；针对新增失败定位并修复，不忽略基线错误。
- [ ] 安装 APK、重启 SystemUI，核对新 PID、版本、报告错误和悬浮窗口身份。
- [ ] 实际验证前台进入、重复展开/收起、反向、外部点击/返回、长按/滑动/按钮、服务更新、设置样式和退出清理；可用模板逐项记录，未触发项明确标记。
- [ ] 对测试区间录屏和采样，核实无连续 buffer 重连，检查可见轨迹和终态。回弹测试档恢复原值。
- [ ] 独立 reviewer 检查规格、实现和回归证据，修复实质问题并复核；更新链路文档及最终限制。

## 执行裁定与自审

用户已明确批准完整执行，实施方法采用当前会话的 subagent-driven-development，无需重复审批。现有结构在工作区未提交文件中；为保留它们，当前目录切到 codex 分支并保存初始相关文件快照，不另起一个缺失本次基线的新 worktree。只有一名实现子代理与主执行者在明确隔离的文件上工作；所有集成由主执行者完成。

所有任务覆盖规格中的动画、样式、前台进入、交互和验证；共享接口只在 Surface 方法处相交。Task 1 不编辑真实 Surface，Task 2 不编辑 Hook/运动/脚本清单，Task 3 统一集成。纯逻辑不模拟掉实际代码；窗口与 GPU 行为由 Android 编译和真机证据验证。
