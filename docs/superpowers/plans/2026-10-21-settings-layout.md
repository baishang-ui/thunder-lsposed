# 功能设置排版实施计划

**Goal:** 两套界面按功能和样式分组，以同级开关控制生效与展开。
**Architecture:** 增加共享的样式配置，持久化原参数，在 HookConfig 创建运行时快照时屏蔽关闭组的修改。磁贴依据所属视图/背景选择独立材质。两端复用同一分组数据。
**Tech Stack:** Kotlin Compose / Miuix / Kyant0 Liquid Glass / Java Hook / 宿主 COUI。
**Spec:** docs/superpowers/specs/2026-10-21-settings-layout-design.md

## 约束
遵循 AGENTS.md 两端组件要求；保留仓库已有未提交修改；不新增自绘控件。

## Review Focus
关闭但保存的参数、旧版本迁移、锁屏与通知中心独立性、复用磁贴背景的类型识别、跨入口开关一致性。

- [x] 配置与测试：StyleGroupsTest 验证关闭恢复系统、保留参数、两端显隐和独立磁贴；先运行失败，再实现配置模型与 v23 迁移。
- [x] Hook：运行时按分组屏蔽修改，区分磁贴材质、流体云宿主上下文，保持原生恢复路径。
- [x] App：功能入口归并，Miuix 卡片标题行使用 LiquidToggle，关闭收起参数。
- [x] 系统设置：复用同一组开关与显隐条件，使用 COUI 原生分组。
- [x] 验证：单元测试、lint、release 构建、相关 fixture 检查；检查 diff 并记录设备验证限制。

验证结果：118 项单元测试通过；lintRelease、assembleRelease 通过；完整 check_effects.py 通过，StyleScopeCheck 另覆盖四类磁贴和按宿主恢复应用色。当前 adb 无连接设备，未进行真机视觉验收。
