# 状态栏图标来源

当前 iOS 风格全部使用用户提供的 `PUI Theme For ColorOS 17_v17.0.0.137.zip` 原始图标。包内状态、Wi-Fi、蜂窝信号、横向 / 竖向电池、蓝牙电池和 ADB 状态资源直接导入，不再使用 `pui.zip`，也不使用项目重绘图形补足包内缺少的状态。

来源路径、原始 XML SHA-256 和适配说明记录于 `app/src/main/assets/icons/pui-reference/manifest.json`。`scripts/import_pui_icons.py` 从已解码的 C17 目录导入原图，`scripts/generate_status_drawables.py` 生成编译输入。所有实际图形路径、比例、分组和透明度保持原包设计，只处理原生 tint、引用、inset 类型兼容和系统进度接口。包内安装与挂载脚本没有执行，也不随应用打包。

状态栏增加 4dp，其他图标仍放大 12%，左右各增加 1dp 留白，相邻间距增加约 2dp；横向电池宽度收窄到 29dp，高度调整到 14dp，参考 iOS 横向电池采用接近 2:1 的长宽比例。原始路径不变，留白通过扩展画布并整体平移图形实现，主副卡相对位置保持一致。Wi-Fi / 信号角标、上下行箭头及文字型 5G 按用户要求隐藏。包内缺少的图标保留系统资源。实现与检查见 [状态栏资源方案](status-icons-rendering.md)，原始资源离线预览见 [status-icons-pui.png](status-icons-pui.png)。

仓库中旧 Apple 参考、SVG、字形与早期画布是历史设计研究，当前样式不从这些文件绘制替代图标。原包素材仍归 PUI 作者所有，项目许可证不重新授权这些素材；来源声明见仓库根目录的 `THIRD_PARTY.md`。
