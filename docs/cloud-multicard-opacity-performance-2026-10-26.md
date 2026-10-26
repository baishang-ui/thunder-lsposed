# 胶囊展开多卡片的透明合成范围修复（2026-10-26）

用户反馈胶囊展开多张卡片仍然卡顿。本次检查沿用系统化排错和先失败后修复的回归流程，修改范围为 SystemUI 宿主 Hook，没有修改独立 App UI。

## 定位

此前的 `cloud-multicard-performance-2026-10-23.md` 已处理同一时钟进度重复提交、输入批处理和共享原生树监听。本次检查发现另一个多卡片专有的合成范围问题：

- 主 Body 是独立窗口，包络为整个显示屏；跟随卡片的 Body 也以 MATCH_PARENT 添加到主 Body，方便共享窗口坐标和输入区域。
- `applyOpacity()` 把每张跟随卡片的错峰淡入透明度写在其全屏 FrameLayout Body 上。Body 保留重叠绘制语义，因此中间透明度引入的组合范围随全屏包络增长；多张跟随卡片同时淡入会放大这部分负担。
- 跟随 Body 实际仅绘制自己的 Shape。Shape 的边界已经是当前卡片大小，其内部包含原生背景、材质、文字和按压效果。因此可以将整张跟随卡片的透明度放到 Shape，保留原来的分组视觉语义。

## 修改

- 主 Body 继续控制整个卡片组的透明度。
- 跟随 Body 保持 alpha=1，将自身透明度设置到有实际卡片边界的 Shape。
- `presentationOpacity()` 对跟随卡片读取 Shape alpha，保证删除、动画接管及中途反转拿到正确的可见透明度。
- Body 的可见性仍由原来的透明度与生命周期控制，保持零透明度时的输入规则。没有调整动画时长、错峰、曲线、内容绘制或布局包络。

实际修改集中在 `CloudMorphSurface` 的透明度写入与快照读取。排查时发现的其他重复同步调用没有混入本次修改。

## 验证

- 新增 `CloudMultiCardOpacityDeviceCheck`，运行真实生产 Surface 的透明度方法，使用真实 Android Body/Shape，隔离无关的插件材质构造和窗口挂载。
- 使用修改前的 Surface 源码运行同一检查，在错峰淡入中点失败：跟随 Body alpha 实际为 0.5，期望包络 alpha 为 1。证据：`.local/cloud-multi-opacity-red.log`。
- 修改后 16,787 项原生 Android 断言通过：透明度端点与中途反转、主卡片组透明度、host gate、删除接管；三张重叠卡片的新旧渲染树逐像素通道对比；1/2/3/5/8 张卡片的透明度作用范围。
- 测试中每张跟随卡片的透明度节点由 1272×2800 的 Body 转到 400×240 的 Shape。这是该测试布局的节点面积对比，不能换算为实际业务帧率或固定性能提升比例。
- 真机命令：`python3 scripts/check_cloud_motion_device.py --root-probe-install --render-only`。同时通过胶囊像素/透明层 42 项、卡片祖先变换 20 项、歌词 TextView 33 项、ART 轨迹 42,971 项；记录 `.local/cloud-multi-opacity-device.log`。
- `--render-only` 是显式选择的无窗口渲染验证模式，不创建窗口或注入触摸；默认模式继续要求执行已有删除窗口检查。本轮没有复跑真实窗口的触摸注入，前一轮结果见卡片关闭修复文档。
- 全量 `scripts/check_effects.py` 与文字渲染 46 项检查通过；多卡片错峰、位置、收起、反转、目标重定向，以及 1,013 项删除/接管检查、3,019 项生命周期检查通过。
- Debug/Release 构建、128 项 JUnit 通过（0 失败/错误/跳过）；Release lint 0 Error、44 Warning。

## 交付与边界

- 签名包：`dist/thunder-cloud-multicard-fix-20261026.apk`，包含此前歌词和卡片按钮关闭修复。
- SHA-256：`cdb9a8e04b8e2a9212fee68c46a33e9f6b7bf326b785b4d806357b05e9cdb11b`，附 `.apk.sha256`。
- 16 KB 对齐与 APK v3 签名验证通过，证书与之前安装包一致。
- 构建/回归/签名日志：`.local/cloud-multi-opacity-build.log`、`.local/cloud-multi-opacity-effects.log`、`.local/cloud-multi-opacity-text.log`、`.local/cloud-multi-opacity-signature.log`。

本轮临时真机测试包已卸载，没有替换手机上的模块、重载 SystemUI 或操作真实业务卡片。真实多卡片场景的帧耗时及视觉效果仍需在安装新包并重载后复测；本次不宣称达到固定 FPS。
