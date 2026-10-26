# 卡片自带按钮关闭流体云修复（2026-10-26）

用户补充：异常由卡片自带的按钮触发。此前 `cloud-dismissal-input-repair-2026-10-26.md` 的回归仅覆盖卡片收起后胶囊场景接管，遗漏了展开卡片直接消失的路径。本次修改限于宿主 Hook。

## 根因与实现

- 原生 `CARD_DISAPPEAR`、无动画/DOO 变体、大小即时卡片消失和三种卡片/胶囊同时消失场景，没有接入独立动画的生命周期。展开完成后 `windowTargets=true` 仍存在，真实删除的 `j(false)` / `e()` 会被错误识别为过期关闭并拦截。
- 将这些场景接入终止删除模式，使用已绑定的容器与原生窗口，不依赖删除时可能已经清空的 CardViews。动画从当前 presentation 与速度连续收缩高度、位置、宽度、内容形态并淡出，完成原生清理后移除独立窗口。已经是胶囊的起点仍使用横向退出。
- 终止删除立即取消手势并清空输入资格，禁止展开区域捕获以及胶囊反转点击。背景胶囊宽度更新/消失不能打断它、重新激活输入；新的真实展开仍可以接管，并保留对迟到旧关闭回调的保护。
- 原生窗口的 NOT_TOUCHABLE 租约根据原生窗口的挂载与可见生命周期保留。独立 Body 的透明度或可见性结束，不再提前把透明原生卡片窗口恢复成可触摸。
- 接管未结束的旧关闭时，转移其已暂存的清理操作，保留原生业务回调、动画对象和状态机时序。

## 验证

- 修复前 `CloudDismissalCheck` 在 `CARD_DISAPPEAR` 立即释放输入的断言失败，记录于 `.local/cloud-card-button-red.log`。
- 最终全量 `scripts/check_effects.py` 通过；其中删除/接管 1,013 项断言、原有展开/收起生命周期 3,019 项检查通过。覆盖八类删除场景、原生清理提前/延迟、CardViews 已清空、四种曲线、背景更新、删除中重新展开。
- 真机反向比较：使用旧版 `ownsNativeInput` 条件，在 Body 不可见且原生卡片仍挂载时丢失租约，`.local/cloud-card-button-device-red.log` 记录对应失败。
- 最终 `scripts/check_cloud_motion_device.py --root-probe-install` 通过：删除窗口检查 20 项，胶囊像素/透明层 42 项，卡片祖先变换 20 项，歌词 TextView 检查 33 项；ART 轨迹 42,971 项。真机记录为 `.local/cloud-card-button-device.log`，结束后临时包已卸载。
- 真机删除检查实际创建两个 Android WindowManager 窗口并注入点击：先验证上层窗口收到点击，再验证 Body 不可见时原生窗口仍保持 NOT_TOUCHABLE，点击到达底层；真实隐藏/移除后再次核对。测试直接调用本次生产 `CloudMorphSurface.ownsNativeInput` 和输入桥接代码，绕过与输入无关的插件材质构造。临时应用没有 SystemUI 签名权限，使用 2038 悬浮窗口并仅在窗口类型边界适配 2017 检查；不代表已完成真实宿主卡片按钮的视觉验收。测试要求设备解锁且通知面板收起。
- 文字渲染检查 46 项；Debug/Release 构建、128 项 JUnit 全部通过（0 失败/错误/跳过）；Release lint 0 Error、44 Warning；`git diff --check` 通过。

## 交付

- 安装包：`dist/thunder-cloud-card-close-fix-20261026.apk`，包含此前歌词修复。
- SHA-256：`91bbfdcd66d093c3989ccd2e56d86cd1a26add52854359b5b1af823b6652a77a`，附 `.apk.sha256` 文件。
- 16 KB zipalign 与 APK v3 签名验证通过，证书与此前发布包一致。
- 构建记录：`.local/cloud-card-button-build.log`；签名记录：`.local/cloud-card-button-signature.log`；全量回归：`.local/cloud-card-button-effects.log`。

本次没有安装/替换手机上的模块，没有重载 SystemUI，也没有触发用户实际业务卡片的关闭按钮。真实宿主的整段视觉与业务操作仍需安装此包并重载后确认；窗口交接与点击穿透已由临时测试真机验证。
