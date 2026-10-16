# LSPosed 运行报告通信修复

依据用户提供的 `LSPosed_20261016_093529.zip` 排查。压缩包内容仅作为诊断数据。

## 日志结论

两份模块日志中，thunder 分别有 140 条和 243 条错误，内容均为：

```text
Thunder: status unavailable: java.lang.IllegalArgumentException:
Unknown authority io.github.baishangui.thunder.settings
```

调用位置是 `ShadeHook.report()` 的运行报告回传。现有日志能确认接收端无法解析，
无法进一步区分当时 ROM 的包可见性过滤、应用未启动或其他临时不可用原因。
配置主要通过 API 102 的远程配置通道读取，不能把这些报告错误直接判断为效果 Hook 全部失效。

原代码每次 Hook 注册、首次命中及参数变化都单独排队发送报告，同一启动阶段会密集执行；
发送失败后不重试，接收端恢复时可能一直等不到下一次状态事件；返回 `null` 也没有校验。

## 修改

- Provider 初始化时向 `com.android.systemui` 和 `com.oplus.systemui.plugins` 分别授予自身
  authority 的读取 URI 权限，建立调用方对模块的反向包可见性。原有 `<queries>` 只控制相反方向。
  单个包不存在或拒绝授权时，保留另一个包的初始化。原有 Binder UID 校验继续限制所有 IPC 方法。
- 多个事件合并为一个待发报告，后续事件不延后首次发送，也不提前失败重试。
- 失败或没有确认返回时，按 2.5 秒起步、最长 60 秒的退避间隔重试。
  每次重新生成最新快照，接收端恢复后自动补发；IPC 期间发生的新事件也会再次上报。
- 同一段不可用期间只记录一次错误，收到有效确认后重置退避和日志去重状态。

## 验证及边界

`scripts/check_effects.py` 的 `RuntimeReportCheck` 使用虚拟时钟执行实际 `ShadeHook`，
覆盖 140 个启动事件合并、首个发送时限、未知 authority、持续故障退避及日志去重、
恢复后发送最新配置、空确认与 IPC 期间新事件。`ProviderVisibilityCheck` 执行实际
Provider 初始化，覆盖精确授权对象、authority、读取权限及单个包缺失；原有
`ProviderCheck` 继续覆盖无关 UID 拒绝访问和宿主／插件报告隔离。

这些是合成 Android 环境回归。最终包可见性与开机行为仍需安装到日志对应手机复测。
压缩包中的 LSPosed 管理器 `Resources$NotFoundException` 和历史原生崩溃没有足够证据
归因到这条报告通信路径，本次没有据此改动框架或其他模块。
