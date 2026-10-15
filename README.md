# thunder

适用于 ColorOS 17 的独立 LSPosed 模块，调整 SystemUI 的下拉背景、通知横幅、通知边距与流体云外观。

当前版本 **4.1 / versionCode 6**。应用名称由 PUI · C17 更改为 thunder；保留包名 `pui.c17.shade`、配置格式和签名身份，方便覆盖升级并保留已有设置。

## 环境要求

- Android 17 / SDK 37，目标适配 ColorOS 17.0.0.104（CN01）的 SystemUI。
- 支持 **libxposed API 102** 的 LSPosed。管理器版本号不能替代实际连接检查，以应用主页的连接状态和运行报告为准。
- “重启系统界面”按钮需要 Root 授权；效果通过 SystemUI 进程中的 Hook 实现。

目前没有连接真机验证注入、视觉效果或性能；其他 ColorOS 版本的内部接口可能不同。

## 功能

- 控制中心与通知中心共用一组背景模糊、压暗颜色和强度设置。压暗强度为 0% 时清除混色，关闭自定义后恢复系统参数。
- 通知横幅独立调整模糊、混色颜色与强度。
- 通知列表左右边距调节，适配单栏、双栏和分屏资源。
- 融合原 PUI 与 C17 的流体云参数，重叠颜色使用 C17，系统圆角沿用 C17。
- 主页、调节、设置三页悬浮导航，顶部渐进模糊；JSON 配置导入导出、界面配色和玻璃参数调节。
- 框架连接、配置同步与 SystemUI 运行报告，以及重启后的新进程和配置版本确认。

这是独立 APK，运行不依赖 KSU 主题包或挂载脚本。当前源码不包含原主题包的图标资源替换。

## 安装使用

1. 安装签名后的 thunder APK；已有版本可使用同一签名覆盖升级。
2. 在 LSPosed 中启用模块，作用域选择 **系统界面 / com.android.systemui**。
3. 首次安装或从旧式入口升级至 API 102 入口时，先打开应用，再重启手机。
4. 在“调节”页开启需要的功能，修改数值并保存，确认配置已同步。
5. 点击“重启系统界面”，授予 Root 权限。状态栏和通知界面会短暂消失。
6. 执行下拉或触发通知横幅后，在主页刷新状态、查看或复制运行报告。

设置页的界面配色只影响 thunder 自身。修改系统下拉背景需要在“调节”页启用对应选项。报告命中说明相关代码路径已执行，并不等于视觉效果已经通过真机验证。

## 构建

使用 JDK 17、Android SDK Platform 37（本地 SDK 目录通常为 `platforms/android-37.0`）和 Build Tools 35.0.0。项目提供 Gradle 8.13 Wrapper，并锁定依赖版本。

在 `local.properties` 中设置本机 SDK 路径，或配置 `ANDROID_HOME`，然后执行：

```sh
./gradlew :app:testReleaseUnitTest :app:lintRelease :app:assembleRelease
python3 scripts/check_systemui_restart.py
```

Windows 使用 `gradlew.bat`。Release 构建输出为 `app/build/outputs/apk/release/app-release-unsigned.apk`，需使用自己的密钥通过 Android Build Tools 的 `apksigner` 签名后安装。已有安装只能使用原签名直接覆盖升级，仓库不包含发布私钥。

重启脚本检查使用独立的临时进程，不会重启开发电脑的系统界面。

主要依赖：AGP 8.13.2、Kotlin 2.3.20、Compose 1.10.3、Kyant0 Backdrop 1.0.6、Shapes 1.2.0、Miuix 0.9.0，以及 libxposed API / Service 102.0.0。

## 目录

```text
app/                 Android 应用、Hook、配置和单元测试
gradle/              Gradle Wrapper
scripts/             可独立执行的检查脚本
THIRD_PARTY.md       第三方组件来源与许可
```

本地 `archive/` 保存原始模块包、采集文件及历史产物，`.local/` 保存工具链、签名和分析资料，`dist/` 保存签名构建结果。这些目录均被 Git 忽略。

Kyant0 的四类 Liquid 组件使用官方 Catalog 源码，其余界面组件使用 Miuix。详细来源与改动说明见 [THIRD_PARTY.md](THIRD_PARTY.md)，许可证随 APK 打包在 `app/src/main/assets/licenses/` 中。
