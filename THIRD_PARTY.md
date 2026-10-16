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

The progressive header follows Kyant0's [progressive blur tutorial](https://kyant.gitbook.io/backdrop/tutorials/progressive-blur):
Backdrop `blur` plus the `effect` API applies an alpha-mask shader to the blurred
backdrop and tint. The mask uses an equivalent ascending smoothstep expression;
intensity and tint follow the app theme. It does not use the previous custom
variable-radius convolution shader.

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

Status bar vectors use symmetric curves and consistent strokes at proportions studied from
[Apple's iPhone User Guide status-icon illustrations](https://support.apple.com/zh-cn/guide/iphone/iphef7bb57dc/ios).
Reference image URLs, measurements and provenance are recorded in
`docs/status-icons-sources.md` and `docs/status-icons-reference.json`.
The original reference illustrations are Apple material; they are not described
as MIT assets or an Apple-supplied Android icon pack. No Apple fonts are bundled.
`app/src/main/assets/icons/ios-reference` contains editable vector studies;
`scripts/refine_status_icons.py` constructs the regular geometry;
`scripts/render_status_icons.py` generates app preview paths;
`scripts/generate_status_drawables.py` generates native Android XML resources.
Bluetooth, silent, vibrate, NFC and performance indicators are project companion
artwork, including Android-specific states.
The solid battery study follows iOS 16 percentage-mode proportions. The current
SystemUI integration retains native battery rendering and percentage calculations.

Static status-resource label outlines are derived from Work Sans Bold by Wei Huang,
licensed under the SIL Open Font License 1.1. The glyph subset is stored in
`scripts/fixtures/iconstyle/label-glyphs.json`; license and copyright notice are
bundled as `app/src/main/assets/licenses/status-labels.txt`. No runtime font is added.


PUI artwork is copied from the user-supplied
`PUI Theme For ColorOS 17_v17.0.0.137.zip`: status icons, Wi-Fi/cellular,
horizontal/vertical battery, Bluetooth battery and ADB status resources.
The original XML paths, transforms, sizes and opacity are retained. Adaptations
are limited to native tint masks, reference names, inset type compatibility,
progress wrappers and aliases. No raster tracing or redrawn fallback is used.
Source entries and SHA-256 hashes are recorded in
`app/src/main/assets/icons/pui-reference/manifest.json`. The first `pui.zip`
is no longer an implementation input. These resources remain attributed to PUI;
the project's license does not relicense the artwork. Archive installers and
mount scripts are not run or bundled. Apple research files are historical only.
