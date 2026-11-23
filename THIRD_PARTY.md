# UI component sources

The standalone app uses [COUI Compose 2.0.0](https://github.com/suqi8/coui/tree/v2.0.0)
(`coui-ui`, `coui-preference`, `coui-icons`, `coui-blur`, `coui-nav`) for its ColorOS 17 UI.
System Settings integration uses native COUI views from the host, not Compose.
License: Apache License 2.0 (`app/src/main/assets/licenses/coui.txt`).

Hook framework: libxposed [API](https://github.com/libxposed/api) and
[Service](https://github.com/libxposed/service) **102.0.0**, Copyright libxposed
contributors, Apache License 2.0. The API is compile-only; the framework supplies
it in SystemUI. The Service library supplies the app's remote-preference channel.
License text: `app/src/main/assets/licenses/libxposed.txt`.

Implementation references inspected (ideas only, no source copied):
- [HyperCeiler: AppsTool](https://github.com/ReChronoRain/HyperCeiler/blob/main/library/libhook/src/main/java/com/sevtinge/hyperceiler/libhook/utils/hookapi/tool/AppsTool.java) and [PixelXpert: SystemUtils](https://github.com/siavash79/PixelXpert/blob/canary/app/src/main/java/sh/siava/pixelxpert/xposed/utils/SystemUtils.java): inspected explicit target-process restart and default TERM behavior. Thunder independently adds save-before-restart, exact original PID checks, one bounded fallback, request expiry, app-level serialization and versioned replacement-process receipts. No source was copied and no framework crash counter is modified.
- [LuckyTool: StatusBarIcon](https://github.com/luckyzyx/LuckyTool/blob/master/app/src/main/java/com/luckyzyx/luckytool/hook/statusbar/StatusBarIcon.kt), [BluetoothIconRelated](https://github.com/luckyzyx/LuckyTool/blob/master/app/src/main/java/com/luckyzyx/luckytool/hook/scopes/systemui/BluetoothIconRelated.kt), and [Oxygen Customizer: StatusbarIcons](https://github.com/DHD2280/Oxygen-Customizer/blob/fc647e6754574ab067fd78f14f6be5bde531fd69/app/src/main/java/it/dhd/oxygencustomizer/xposed/hooks/systemui/statusbar/StatusbarIcons.java): inspected slot visibility and vendor Bluetooth policy interception. The collected C17 implementation uses `updateBluetooth$1`, so thunder independently extends the current `com.android.systemui.statusbar.phone.ui.StatusBarIconControllerImpl` native hide list instead of copying older policy hooks. Slot names were verified in the exported framework's `config_statusBarIcons`; constructor, `onTuningChanged`, `mIconHideList`, and `refreshStatusBarIconList` were checked in the exported SystemUI DEX. No additional library or legacy hook API is used.
- [LuckyTool: ControlCenterBackgroundTransParency](https://github.com/luckyzyx/LuckyTool/blob/master/app/src/main/java/com/luckyzyx/luckytool/hook/scopes/systemui/ControlCenterBackgroundTransParency.kt): modify the actual scrim path. Its older `updateScrimColor` argument positions differ from collected C17, so this app uses C17's native mix producer instead.
- [LuckyTool: EnableNotificationSideSpacing](https://github.com/luckyzyx/LuckyTool/blob/master/app/src/main/java/com/luckyzyx/luckytool/hook/scopes/systemui/EnableNotificationSideSpacing.kt): enforce notification side padding during measurement, adapted to this app's density and snapshot controls.
- [LuckyTool: NotificationBackgroundBlurAlpha](https://github.com/luckyzyx/LuckyTool/blob/master/app/src/main/java/com/luckyzyx/luckytool/hook/scopes/systemui/NotificationBackgroundBlurAlpha.kt) and `CustomNotificationBackgroundTransparency`: distinguish the notification drawable from its host view. The shipped app targets C17's `ViewBlurManager` and native backend configs verified in the collected APK.

These references alone do not establish compatibility. Method descriptors and fields
were checked against the user's ColorOS 17.0.0.104 SystemUI.

Historical vector studies (not used by current status-bar replacements) use
symmetric curves and consistent strokes at proportions studied from
[Apple's iPhone User Guide status-icon illustrations](https://support.apple.com/zh-cn/guide/iphone/iphef7bb57dc/ios).
Reference image URLs, measurements and provenance are recorded in
`docs/status-icons-sources.md` and `docs/status-icons-reference.json`.
The original reference illustrations are Apple material; they are not described
as MIT assets or an Apple-supplied Android icon pack. No Apple fonts are bundled.
`app/src/main/assets/icons/ios-reference` contains editable vector studies;
`scripts/refine_status_icons.py` constructs the regular geometry;
`scripts/render_status_icons.py` generates the historical preview paths.
The current Android resource generator uses the C16 sources described below.
Bluetooth, silent, vibrate, NFC and performance indicators are project companion
artwork, including Android-specific states.
The solid battery study follows iOS 16 percentage-mode proportions. The current
SystemUI integration retains native battery rendering and percentage calculations.

Static status-resource label outlines are derived from Work Sans Bold by Wei Huang,
licensed under the SIL Open Font License 1.1. The glyph subset is stored in
`scripts/fixtures/iconstyle/label-glyphs.json`; license and copyright notice are
bundled as `app/src/main/assets/licenses/status-labels.txt`. No runtime font is added.


Status-bar artwork is copied from the user-supplied
`双排信号状态栏模块C16.zip`: status icons, Wi-Fi/cellular, horizontal battery
and Bluetooth battery resources. The second user-supplied pack,
the ColorOS 17 reference archive, supplies only the C17 shared signal
frame and resource organization reference. C16 top bars and bottom short bars
are separated into independent native C17 SIM resources; lower-row opacity is
selected for each signal level. Single-SIM resources use the original top row.
Legacy external insets are normalized to the project's spacing. Battery fill
uses the original C16 body path with native C17 progress wrappers and dimensions.
Icons absent from C16 are removed from the replacement set; transparent network
badge suppressors remain enabled. Every visible path is retained from C16.
Source entries, binary XML SHA-256 hashes, path hashes and adaptations are recorded
in `app/src/main/assets/icons/theme-reference/manifest.json`. These resources remain
owned by the original theme authors; the project's license does not relicense the artwork. Archive
installers and mount scripts are not run or bundled. Apple research files and
legacy vector studies are historical only and do not supply current replacements.
