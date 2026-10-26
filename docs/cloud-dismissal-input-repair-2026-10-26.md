# 流体云删除动画与残留触摸区域修复（2026-10-26）

本次修改限于 SystemUI 流体云 Hook，没有改动独立 App UI。

## 根因与修复

1. 卡片收起期间，原生窗口的 `j(false)` / `e()` 会等待独立动画完成。如果胶囊更新、出现或消失场景接管同一个 surface，旧 Motion 以 superseded 结束，原实现会直接丢弃暂存的隐藏/移除操作。随后画面回到胶囊，透明原生卡片窗口却可能仍保留展开区域。现在胶囊接管时执行旧窗口已暂存的清理，并释放对应的旧窗口目标记录；重新展开时仍保留原有防止过期关闭回调隐藏新卡片的处理。
2. `CAPSULE_DISAPPEAR` 原来始终固定起始高度、纵坐标和内容形态进度，只收缩宽度。如果它打断尚未完成的卡片收起，就会把卡片挤成高而窄的条状。现在根据起始形态选择轨迹：已是胶囊时保留原来的横向收缩；仍处于卡片形态时继承当前画面及速度，继续改变高度、位置和内容形态，直到隐藏的胶囊端点。

## 回归与验证

- 新增 `CloudDismissalCheck`，在修复前分别复现暂存清理丢失和删除时卡片高度被冻结；修复后 192 项断言通过。
- 覆盖胶囊宽度更新、出现、删除接管收起；原生清理提前/延迟完成；四种曲线下删除打断收起；删除后再次展开和输入所有权。
- 全量 `scripts/check_effects.py` 通过，保留已有展开/收起、反转、多卡片、胶囊横向删除和输入窗口规则回归。
- 文字渲染检查 46 项通过。
- Debug / Release 构建通过；128 项 JUnit 通过，0 失败、0 跳过。
- Release lint 无 Error，有 37 项 Warning。

当前无已连接的 Android 设备，未安装 APK、未重载 SystemUI，也未做真机动画与触摸区域复测。JVM 回归使用真实 Hook/轨迹代码和模拟平台边界，不能替代设备效果验证。

## 本地产物与证据

- 签名安装包：`dist/thunder-cloud-dismissal-fix-20261026.apk`，附 SHA-256 文件。
- 修复前失败：`.local/cloud-dismissal-red.log`、`.local/cloud-dismissal-geometry-red.log`。
- 全量回归：`.local/cloud-dismissal-effects.log`。
- 文字渲染：`.local/cloud-dismissal-text.log`。
- 构建与检查：`.local/cloud-dismissal-build.log`。
