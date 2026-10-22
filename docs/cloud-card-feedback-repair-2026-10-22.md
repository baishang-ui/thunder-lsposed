# 流体云卡片原生反馈恢复（2026-10-22）

本次修改属于宿主 Hook / 显示桥接，不涉及独立 App 界面组件。

## 原因

统一显示层仍向原生根节点转发完整手势，但只绘制卡片内容 View，遗漏了普通卡片 `CardView.dispatchDraw()` 和即时卡片 `InstantContainer.dispatchDraw()` 中调用的原生按压高光 `card.ui.view.k.b(Canvas, int, int)`。

绘制前只同步了胶囊按压缩放，未同步展开卡片 `O3` 在按压、拖动、松手回弹期间对原生 View 的缩放、支点和位移修改。这些属性变化不会触发布局监听，所以显示层看起来没有反馈、无法拖动。

即时卡片还有一处相关依赖：原生容器在 `dispatchDraw()` 中通过 `k.g(width, height)` 设置高光边界。隐藏原生容器后，该调用也会被跳过，导致首次 DOWN 的高光处理提前返回。

上述原生调用链已与本地留存的设备 SystemUIPlugin APK 核对；这不等同于运行时真机验证。

## 修复

- `CloudCardFeedback` 直接调用原生高光对象，保留原生状态、着色与现有圆角 Hook；绑定、首次 DOWN 和绘制时更新其原生尺寸，不重做按压效果。
- 展开态在每次原生绘制前同步卡片的 X/Y 缩放、支点、拖动位移及回弹。形变过程中继续由模块动画控制几何，避免叠加宿主过渡缩放。
- 输入轮廓使用显示控件的完整矩阵，跟随非中心支点、不同轴的缩放和拖动位移。
- 保持原有原生 DOWN/MOVE/UP/CANCEL 转发与长按计时；没有新增手势识别器。

## 校验与限制

新增主机回归覆盖原生缩放、支点映射、拖动位移、无新触摸事件时的松手回弹、形变隔离、高光对象选择、首次按下前的边界初始化和高光绘制坐标。测试使用生产 `CloudCardFeedback`，Android View / Canvas 为平台边界替身，不代表 GPU 外观验收。

执行完整 `scripts/check_effects.py`、Debug / Release 离线构建、JUnit 和 Release lint。原始日志位于 `.local/cloud-card-feedback-effects.log`、`.local/cloud-card-feedback-build-final.log`。

本次 ADB 无已连接设备，未安装或重启 SystemUI，尚未验证实际长按、按压高光及拖动手感。后续真机需分别覆盖普通卡片与即时卡片，检查长按、按住移动、松手回弹、多卡片选择、外部点击收起与中途反向。
