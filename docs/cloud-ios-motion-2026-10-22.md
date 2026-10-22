# 流体云动效与性能优化（2026-10-22，首轮记录）

> 本文保留首轮实现与测量记录；分轴生长和相关实现已由后续重构替代。当前实现与真机验证见 [流体云重构与交互验证](cloud-motion-overhaul-2026-10-22.md)。

## 参考依据与范围

本轮查阅 Apple 的 [Design dynamic Live Activities（WWDC23）](https://developer.apple.com/videos/play/wwdc2023/10194/)、[ActivityKit 的展示形态说明](https://developer.apple.com/documentation/activitykit/displaying-live-data-with-live-activities) 和 [数据更新动画说明](https://developer.apple.com/documentation/widgetkit/animating-data-updates-in-widgets-and-live-activities)。资料明确了 compact、minimal、expanded 形态、展开时轮廓生长、形态之间内容位置的连贯性，以及数据更新的文字和数字过渡。

Apple 未在这些资料中公布系统轮廓动画的完整运动参数。本项目继续使用宿主的真实内容、窗口、事件与目标几何，做对应动效的近似实现；下面的轴向响应系数是本项目的调参，不是 Apple 公布或测得的参数。没有据此宣称已逐项复刻 iOS 全部系统动画。

| 场景 | 当前处理及本轮变化 |
| --- | --- |
| 应用进入胶囊 | 保留现有真实 APP_TO_CAPSULE 行程、中心扩宽、速度反馈及剩余事件时钟；本轮共用位置、速度与加速度采样 |
| 胶囊展开卡片 | 新增横向稍快、纵向稍慢的生长层次；x/width 共用响应，y/height 共用响应，保持轴向锚点连贯 |
| 卡片收回胶囊 | 保持已有整副矩形的临界阻尼回收和胶囊终点限制；不引入分轴回弹 |
| 中途反向、迟到布局 | 保留当前几何和速度交接；本轮新增分轴展开的导数及乱序采样检查 |
| mini、多服务和宽度变化 | 继续走宿主原有事件与目标解析，不凭空新增宿主没有的手势 |
| 多张卡片 | 保留已有跟随首卡当前轮廓的展开、汇拢和小幅交错 |
| 内容交接 | 由直接线性交叉淡入改为基于形态进度的五次平滑交叉淡入，两个内容权重互补；文字仍使用上一轮独立的场景与字符动画 |
| 胶囊隐藏及按压 | 保持已有横向收拢及原生按压；不改变用户保存的时长、曲线和回弹 |

修改仅涉及 Hook 对宿主内容的动画和绘制，不涉及独立 App 控件构建。

## 实现与性能

`CloudMotion` 对同一进度只计算一次六个通道的位置、速度与加速度，共享包络和原生入口行程，再生成两个不可变 Frame。多个窗口在同一 tick 请求同一进度时复用这两个快照；进度改变时重新解析计算，不累积积分误差，支持退回旧进度采样。采样数组在轨迹创建时分配，生命周期随轨迹结束。

展开时，横向响应使用原响应的 1.06 倍，纵向使用 0.86 倍，圆角使用 0.92 倍。仅在宽高同时增加且形态进度向展开方向增长时使用；应用入口、收起及用户选择的恒速路径保持原行为。初速度仍直接使用继承值，不通过改时间参数破坏反向交接。

`CloudMorphSurface` 的内容、装饰与前景，以及 `CloudCapsuleContent` 的胶囊内容，在量化 alpha 为 255 时直接保存 Canvas 状态，省去仅用于组透明度的离屏图层；alpha 为 0 时跳过绘制。真实半透明过渡继续保留组透明度图层，子内容的原生 alpha、矩阵、父子关系及两侧附件保持原语义。此优化不移除文字自身需要的 RenderNode，也不改变原生材质模糊。

## 测量

使用相同源码快照、同一 PLQ110（API 37）、相同 128 MiB 测试进程堆上限。每条轨迹预热 120,000 次，随后各测三轮 120,000 次，取中位数。每个 tick 请求两遍位置与速度，模拟原生和独立窗口重复观察同一帧。基线取修改前保存的工作区源码，并未安装或重载手机上的模块来做此比较。

| ART 轨迹 CPU 基准 | 修改前 ns/tick | 修改后 ns/tick | 耗时减少 |
| --- | ---: | ---: | ---: |
| 展开 | 681.2 | 313.7 | 54% |
| 收起 | 395.1 | 201.1 | 49% |
| 应用入场 | 3104.7 | 452.3 | 85% |

主机 JVM 在相同重复采样负载下，分配量由 160 B/tick 降至 80 B/tick。这里的数字只衡量轨迹函数和快照采样，不代表 SystemUI 整帧耗时、GPU 时间、耗电或实际显示帧率；CPU 调频和其他手机负载也会影响时间读数。

## 验证与产物

- 主机完整 `scripts/check_effects.py --benchmark` 通过；新增检查先在旧实现上复现失败，再验证通过。
- 主机及真实 ART 的 42,349 项轨迹检查通过，覆盖四种曲线、0/18/100 回弹、反向初速度、入口形变、端点、分轴锚点、有限差分速度与乱序/重复采样。
- 独立文字渲染 40 项检查通过；125 项 JUnit 检查通过，0 失败、0 错误。
- Debug、Release 离线构建通过。
- 临时自目标 Instrumentation 在正常 Android 应用进程执行 22 项原生像素与 alpha 图层检查，包括完整不透明内容无需组透明度图层、半透明内容保留一层、不可见内容跳过，以及两侧附件和原生矩阵。Android 17 的 ApplicationSharedMemory 要求普通应用进程，不能直接使用裸 app_process 创建 View。临时包测试后已卸载。
- 本轮未覆盖最终 APK 在全部应用模板下的展开/收起录屏、屏幕帧率与 GPU 耗时验收。没有用轨迹 CPU 基准代替这些证据。

复现命令（JAVA_HOME 指向项目可用 JDK 17）：

```sh
python3 scripts/check_effects.py --benchmark
python3 scripts/check_cloud_text_renderer.py
./gradlew --offline :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest
python3 scripts/check_cloud_motion_device.py
```

最后一条会安装并卸载临时测试包，不启动前台 Activity，不替换模块、不重启 SystemUI。已授权的 root 测试设备可使用 `--root-probe-install`；只测轨迹 CPU 使用 `--benchmark-only`。传入 `--java-source-root` 可比较保存的运动源码快照。

最终签名包：`dist/thunder-cloud-ios-motion-20261022.apk`。构建包含完整当前工作区（含上一轮文字修改）；源码校验、APK 校验、测量和检查日志记录在 `.local/cloud-ios-motion-20261022/`。
