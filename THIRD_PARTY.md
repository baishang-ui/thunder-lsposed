# UI component sources

The four Liquid Glass controls reuse the original implementations by Kyant0:

- `LiquidButton.kt`
- `LiquidToggle.kt`
- `LiquidSlider.kt`
- `LiquidBottomTabs.kt` (with its `LiquidBottomTab.kt` helper)

Source: [Kyant0/AndroidLiquidGlass tag 1.0.6](https://github.com/Kyant0/AndroidLiquidGlass/tree/1.0.6/catalog/src/main/java/com/kyant/backdrop/catalog).
This release matches the app's `io.github.kyant0:backdrop:1.0.6` dependency. The
same four components are linked from the repository's current `kmp` README.
The required `DampedDragAnimation`, `InteractiveHighlight` and
`DragGestureInspector` helpers are included from that tag as well.

Copyright Kyant (Kyant0), Apache License 2.0. License text is distributed under
`app/src/main/assets/licenses/backdrop.txt`.

Local adaptations: replace hardcoded theme/accent with `LocalLiquidAppearance`,
scale blur/lens dimensions using the app's UI preferences, and wrap the components
with accessible labels/value actions and current callback state. The original
press/optical algorithms are retained; app-specific layout adaptations are listed
below. The slider keeps a continuous
floating-point drag accumulator, reads current callbacks and geometry, and adds a
finished callback so the app commits a drag once instead of recomposing the whole
screen for every pointer event.

The button accepts configurable content padding for header icons inside 48 dp
touch targets. Text buttons use horizontal and vertical content padding, a 48 dp
minimum height and a configurable shape with a 24 dp corner radius so wrapped or
enlarged text can increase the height without entering capsule corners. Button
shapes are restricted to Kyant's `RoundedRectangularShape`: icons use `Capsule`
and text buttons use `RoundedRectangle`. Miuix's cached smooth shapes are not
accepted by Backdrop 1.0.6's lens effect and cause an exception on first draw.
The compiled button signature is checked by `scripts/check_liquid_button.py`.
Button press content is clipped to the same shape as its backdrop; disabled buttons have
no click or press handlers. Selected buttons draw an opaque accent surface so the
theme's contrasting foreground matches the displayed background. Bottom tabs
accept a configurable height shared by the visible bar, sampled tab layer and
moving highlight, with a font-aware height supplied by the app.

Other UI components (theme, text, cards, menu rows, choice tabs, dialog, color picker, and
text input) use `top.yukonga.miuix.kmp:miuix-ui:0.9.0`.
All in-app navigation, header and menu icons use the Regular set from
`top.yukonga.miuix.kmp:miuix-icons:0.9.0`. The previous custom back and restart
vectors have been removed.
Copyright compose-miuix-ui contributors, Apache License 2.0; license under
`app/src/main/assets/licenses/miuix.txt`.

The application header uses Miuix `SmallTopAppBar` and a transparent `Surface`,
with reserved navigation/action slots. The existing Liquid buttons sit in a fixed
root overlay above the header and outside the predictive-back transform, so their
position is independent of title/subtitle measurement. A shared,
font-aware height is used for the header and page spacing, avoiding a one-frame
padding change after navigation. The backdrop follows Kyant0's official
[Progressive Blur tutorial](https://kyant.gitbook.io/backdrop/tutorials/progressive-blur):
`drawPlainBackdrop`, a 4 dp blur and the tutorial's `AlphaMask` effect, retaining
the app's original light/dark tint strength. The mask shader follows the official
tutorial verbatim; only its size, tint and tint-intensity uniforms are supplied by
the app.

[Kyant0 Shapes](https://github.com/Kyant0/Shapes) is already included as
`io.github.kyant0:shapes:1.2.0`. Its `RoundedRectangle` supplies text-button
corners, and `Capsule` supplies icon-button and Liquid control shapes, including
the bottom navigation. These remain library shapes rather than app-drawn paths.

Preview selection uses Miuix `TabRowWithContour` / `RadioButton`, preview cards
use Miuix `Card` / `Surface`, and enlarged previews use Miuix `WindowDialog`.
Read-only progress illustrations use Miuix `LinearProgressIndicator`; the close
and music glyphs use Miuix Icons. Installed system and theme assets remain preview
content; they do not implement application interaction controls. Material samples
use Miuix `Surface` with Kyant Backdrop's blur API, without a custom glass shader.

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
