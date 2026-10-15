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
with accessible labels/value actions and current callback state. Layout and the
original press/optical implementations are retained. The slider keeps a continuous
floating-point drag accumulator, reads current callbacks and geometry, and adds a
finished callback so the app commits a drag once instead of recomposing the whole
screen for every pointer event.

Other UI components (theme, text, cards, choice tabs, dialog, color picker, and
text input) use `top.yukonga.miuix.kmp:miuix-ui:0.9.0`.
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
- [LuckyTool: ControlCenterBackgroundTransParency](https://github.com/luckyzyx/LuckyTool/blob/master/app/src/main/java/com/luckyzyx/luckytool/hook/scopes/systemui/ControlCenterBackgroundTransParency.kt): modify the actual scrim path. Its older `updateScrimColor` argument positions differ from collected C17, so this app uses C17's native mix producer instead.
- [LuckyTool: EnableNotificationSideSpacing](https://github.com/luckyzyx/LuckyTool/blob/master/app/src/main/java/com/luckyzyx/luckytool/hook/scopes/systemui/EnableNotificationSideSpacing.kt): enforce notification side padding during measurement, adapted to this app's density and snapshot controls.
- [LuckyTool: NotificationBackgroundBlurAlpha](https://github.com/luckyzyx/LuckyTool/blob/master/app/src/main/java/com/luckyzyx/luckytool/hook/scopes/systemui/NotificationBackgroundBlurAlpha.kt) and `CustomNotificationBackgroundTransparency`: distinguish the notification drawable from its host view. The shipped app targets C17's `ViewBlurManager` and native backend configs verified in the collected APK.

These references alone do not establish compatibility. Method descriptors and fields
were checked against the user's ColorOS 17.0.0.104 SystemUI.
