# SystemUI Hook 性能优化

本次优化只处理 CPU 侧重复查找和临时对象，不调整模糊半径、混色算法、动画时序、刷新频率或功能开关。

## 改动

- `Reflect` 按接收对象的 Class 缓存成员。字段直接使用名称查找；无参方法使用独立缓存和共用空参数数组，避免每次构造包含 Class 的键、参数类型数组及列表。继承查找、重载匹配、歧义检查和异常传播维持原规则。`ClassValue` 使缓存跟随 Class 生命周期，避免全局成员表永久持有已卸载的插件类型。
- `AdaptiveTextHook` 按 Class 缓存通知、磁贴、面板及排除范围的固定类型标记，避免逐帧反复扫描父类。资源名称按 Resources 实例及 ID 缓存，Resources 使用弱引用键。
- 父视图链、当前 ID、Resources 实例、背景内容、磁贴状态、横幅角色和设置仍在绘制时读取。不会缓存最终背景颜色或父容器，因此视图复用和实时配置不会沿用过期结果。颜色叠加顺序及 160 毫秒文字过渡保持原实现。

这些公共反射调用由下拉面板、通知、流体云、音量面板等处理器共用。图形模糊本身的 GPU 成本没有变化。

## 验证和测量

运行：

```sh
python3 scripts/check_effects.py
python3 scripts/check_effects.py --only ReflectCheck AdaptiveTextPerformanceCheck --benchmark
./gradlew :app:testReleaseUnitTest :app:lintRelease :app:assembleRelease
```

性能脚本使用实际 Java 工具和处理器、合成 Android 替身，在主机 JDK 17 上预热 20 万次后测量三轮，每轮 20 万次。分配量使用 `ThreadMXBean.getThreadAllocatedBytes`；下面耗时为三轮中位值，存在 JIT 和主机调度误差，不作为真机性能门槛。

2026-10-16 本地对照记录：

| 热路径 | 修改前 | 修改后 |
| --- | ---: | ---: |
| 字段读取＋无参方法调用的临时分配 | 120 B/次 | 16 B/次 |
| 字段读取＋无参方法调用耗时 | 29.3 ns/次 | 15.9 ns/次 |
| 三层视图的面板文字背景判断耗时 | 117.4 ns/次 | 50.2 ns/次 |
| 稳定面板 ID 连续 100 次判断的名称解析次数 | 100 次 | 1 次 |

反射用例保留了 int 字段通过 Object 返回时的装箱，因此修改后仍有 16 B 分配。背景判断用例在主机 JIT 优化后两版均未测得临时分配，不能据此推断 Android ART 的分配量。此处的 ID 与 Resources 也是替身，耗时不代表 Android 资源系统的真实开销。

回归覆盖反射继承与字段遮蔽、带参与无参重载、null 参数、原生异常，以及资源隔离、ID 变化、重新挂载和多层半透明背景的原顺序。原有效果回归继续检查通知与横幅切换、磁贴语义状态、文字过渡及关闭配置后的恢复。

尚未连接 ColorOS 17 真机；没有测量实际 FPS、CPU 占用、温度或续航变化。需在同一设备、同一配置下对比展开下拉、通知横幅、流体云更新及音量面板动画，并确认视觉一致。
