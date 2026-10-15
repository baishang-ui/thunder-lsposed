# thunder

适用于 ColorOS 17 的独立 LSPosed 模块，调整 SystemUI 的下拉背景、通知横幅、通知边距与流体云外观。设置自动保存，改动的组件旁可回退。

当前版本 **4.3 / versionCode 8**，应用包名及源码命名空间统一为 `io.github.baishangui.thunder`，配置格式为 `thunder.settings`。

## 环境要求

- Android 17 / SDK 37，目标适配 ColorOS 17.0.0.104（CN01）的 SystemUI。
- 支持 **libxposed API 102** 的 LSPosed。管理器版本号不能替代实际连接检查，以应用主页的连接状态和运行报告为准。
- “重启系统界面”按钮需要 Root 授权；效果通过 SystemUI 进程中的 Hook 实现。

目前没有连接真机验证注入、视觉效果或性能；其他 ColorOS 版本的内部接口可能不同。流体云的原生背景模糊依赖设备固件提供的 ViewRootManager 后端。

## 功能

- 控制中心与通知中心共用一组背景模糊、压暗颜色和强度设置。压暗强度为 0% 时清除混色，关闭自定义后恢复系统参数。
- 通知横幅独立调整模糊、混色颜色与强度。
- 通知列表左右边距调节，适配单栏、双栏和分屏资源。
- 调整 C17 的流体云参数，可独立调整胶囊高度、圆角、背景混色与模糊；未启用自定义圆角时保留 C17。
- 主页、调节、设置三页悬浮导航，顶部使用 Kyant0 教程中的透明遮罩渐进模糊，左上角返回、右上角重启均使用 LiquidButton。
- 滑块在组件内保留连续浮点手势值，松手后提交；开关和颜色确认后自动保存。改过的滑块、开关、颜色和外观选项提供“回退”，返回本次编辑前的值。
- JSON 配置导入导出、界面配色和玻璃参数调节。使用 `thunder.settings` 配置格式。
- 通知横幅页提供真实测试通知，首次发送请求通知权限，通知在 15 秒后自动移除。横幅是否显示仍受系统通知渠道、勿扰模式和提醒频率限制。
- 框架连接、配置同步与 SystemUI 运行报告，以及重启后的新进程和配置版本确认。

这是独立 APK，运行不依赖 KSU 主题包或挂载脚本。当前源码不包含原主题包的图标资源替换。

## 安装使用

1. 从 GitHub Releases 安装签名后的 thunder APK。新包名会作为独立应用安装；首次安装需重新启用模块并设置作用域。卸载旧包前先在 LSPosed 中停用，避免两个模块同时修改 SystemUI。后续版本可使用同一包名和签名覆盖升级。
2. 在 LSPosed 中启用模块，作用域选择 **系统界面 / com.android.systemui**。
3. 首次安装或从旧式入口升级至 API 102 入口时，先打开应用，再重启手机。
4. 在“调节”页开启需要的功能。交互会自动保存并同步，不再需要手动点击保存。
5. 点击右上角“重启”并确认，授予 Root 权限。状态栏和通知界面会短暂消失。
6. 执行下拉或触发通知横幅后，在主页刷新状态、查看或复制运行报告。

设置页的界面配色只影响 thunder 自身。修改系统下拉背景需要在“调节”页启用对应选项。报告命中说明相关代码路径已执行，并不等于视觉效果已经通过真机验证。

## 构建

使用 JDK 17、Android SDK Platform 37（本地 SDK 目录通常为 `platforms/android-37.0`）和 Build Tools 35.0.0。项目提供 Gradle 8.13 Wrapper，并锁定依赖版本。

在 `local.properties` 中设置本机 SDK 路径，或配置 `ANDROID_HOME`，然后执行：

```sh
./gradlew :app:testReleaseUnitTest :app:lintRelease :app:assembleRelease
python3 scripts/check_systemui_restart.py
python3 scripts/check_effects.py
```

Windows 使用 `gradlew.bat`。本地构建输出为 `app/build/outputs/apk/release/app-release-unsigned.apk`，需签名后安装。仓库不包含发布私钥，正式安装包由 GitHub Actions 签名并发布。

重启脚本检查使用独立的临时进程，不会重启开发电脑的系统界面。效果检查用合成的 Android / Oplus 替身执行实际 Java 处理器，覆盖展开进度、状态恢复与流体云原生接口；不包含采集固件，也不验证手机渲染。

主要依赖：AGP 8.13.2、Kotlin 2.3.20、Compose 1.10.3、Kyant0 Backdrop 1.0.6、Shapes 1.2.0、Miuix 0.9.0，以及 libxposed API / Service 102.0.0。

## GitHub Actions 与发布

[构建工作流](../../actions/workflows/android.yml) 在推送 `main`、提交 Pull Request 或手动运行时执行处理器检查、重启脚本检查、单元测试、Lint 和 Release 构建，保留未签名 APK 及检查报告。普通分支构建不使用发布密钥。

发布步骤：

1. 更新 `app/build.gradle.kts` 中的 `versionName` 与递增的 `versionCode`，提交并推送。
2. 创建与 `versionName` 一致的版本标签，例如 `git tag v4.3`，再执行 `git push origin v4.3`。
3. 工作流核对包名、APK 版本与标签，全部检查通过后使用正式密钥签名、验证签名及 16 KB 对齐，并发布 [GitHub Release](../../releases)，包含 APK 和 SHA-256 校验文件。

也可以在已有标签上手动运行工作流。带 `-` 的版本标记为预发布；已发布的版本不覆盖，应递增版本后发布。发布失败留下的草稿可以通过重新运行工作流继续上传并发布。

仓库 Actions Secrets 需要配置：

| 名称 | 内容 |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | thunder 发布 keystore 的 Base64 内容 |
| `ANDROID_KEYSTORE_PASSWORD` | keystore 密码 |
| `ANDROID_KEY_ALIAS` | 发布密钥别名 |
| `ANDROID_KEY_PASSWORD` | 发布密钥密码 |

仓库变量 `ANDROID_SIGNING_CERT_SHA256` 保存签名证书的 SHA-256 指纹（小写、无冒号），工作流用它核对签名身份。已为本仓库配置 thunder 专用发布密钥；请保管密钥备份，后续版本需要使用同一密钥才能覆盖升级。

## 目录

```text
app/                 Android 应用、Hook、配置和单元测试
gradle/              Gradle Wrapper
scripts/             可独立执行的检查脚本
.github/workflows/   自动构建、签名与 Release 发布
THIRD_PARTY.md       第三方组件来源与许可
```

本地 `archive/` 保存原始模块包、采集文件及历史产物，`.local/` 保存工具链、签名和分析资料，`dist/` 保存签名构建结果。这些目录均被 Git 忽略。

Kyant0 的四类 Liquid 组件使用官方 Catalog 源码，其余界面组件使用 Miuix。详细来源与改动说明见 [THIRD_PARTY.md](THIRD_PARTY.md)，许可证随 APK 打包在 `app/src/main/assets/licenses/` 中。
