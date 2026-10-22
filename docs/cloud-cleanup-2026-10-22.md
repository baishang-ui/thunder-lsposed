# 流体云后续审查与代码清理

日期：2026-10-22。清理起点为 `0e16740`，本轮聚焦运行时重复状态、内容通知契约、测试夹具覆盖和本地生成文件。遵循用户停止真机复测的要求，未操作手机、安装 APK 或重载 SystemUI。

## 运行时代码

- 删除 `CloudMorphSurface.selectedContent`。该字段只用于判断是否有内容层，与 `contentSwitch.layers` 重复；绘制现在直接检查真实图层集合，避免维护第二份状态。
- 删除只返回字段的 `ownsContentMotion()`，直接读取当前所有权。
- 删除 `contentChanged` / `contentChangedForSource` 未消费的 revision 参数。模型版本判断仍由 `CloudMorphHook.queueContentRevision` 负责；场景 reveal 继续保留实际使用的 revision。
- 稳定内容源先比较 View 身份，只有胶囊/卡片来源变化时才创建 `ContentSources`。保留首次绑定及单侧变化时的独立重绑。
- 将不访问实例状态的 `nativeCardContent` 改为静态方法，使用不捕获 Surface 的方法引用。
- 删除输入变换改用 `inputShapeMatrix` 后残留的未使用 `inputMatrix`。

以上精简不调整动画曲线、时长或回弹参数。减少绑定快照与冗余引用不等于测得 Android 帧率提升，本轮不作新增帧率结论。

## 测试可信度与无用文件

`check_effects.py` 原来按目录顺序复制同名 Java 桩文件，后写入者静默覆盖先写入者。被覆盖的文件看似有效，实际上从未参与编译。

本轮删除 16 个被覆盖的 Java 文件，以及 `effect-runtime.json` 中 25 条被覆盖的类定义。按清理时的加载顺序逐类比较，去重前后最终 302 个夹具类的源码字节一致；随后单独精简了内容通知的边界夹具。删除明细及 SHA-256 位于 `.local/cloud-cleanup-20261022/fixture-cleanup.json`。

加载器现在拒绝同名夹具。隔离临时目录中的验证确认：正常集合可加载；人为加入重复的 `android/os/Build.java` 会在编译前明确失败。

内容通知的边界夹具不再自行按 revision 去重，而是记录每次调用，与生产 Surface 的职责一致。这样不会用测试桩中的额外逻辑掩盖 Hook 缺陷。临时源码中移除生产版本的去重判断后，`CloudMorphCheck` 在重复引擎完成通知断言处失败；正式源码通过相同回归。

## 本地生成文件

清理 7 份旧验证工程中的 `app/build`、根 `build`、`.gradle`、`.kotlin`，以及明确的 Python 字节码缓存，共 30 个生成/缓存目录、19195 个文件，逻辑大小 491484245 bytes（468.72 MiB）。清单位于 `.local/cloud-cleanup-20261022/generated-cleanup.json`。

历史验证源码、原始日志和结果、测试探针、当前工作区构建目录、SDK/JDK、签名文件、`dist` 安装包及 `archive` 均保留。不同测试后端独立使用的 `cloudtext` 桩不属于同名覆盖，本轮没有将它们作为重复文件删除。

## 验证状态

验证日志统一写入 `.local/cloud-cleanup-20261022/`。工作期间另外出现文字渲染范围和卡片按压反馈的并行修改；它们保留在工作区，不能将全部差异归为本轮清理。

- 清理相关的动画、生命周期、内容切换与多卡片检查通过。
- 首次 Debug / Release 构建与 125 项 JUnit 全部通过。
- 文字渲染器当前 46 项检查通过。
- 去重故障注入与夹具重复检测检查通过。
- 最终构建、lint 与完整回归结果待收尾记录。
