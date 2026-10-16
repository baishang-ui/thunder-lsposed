package io.github.baishangui.thunder;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Native resource styling, with scoped C17 label and status-window sizing hooks. */
final class IosStatusBarHook {
    private static final String MODULE = "io.github.baishangui.thunder";
    private static final Set<Integer> failed = ConcurrentHashMap.newKeySet();
    private static volatile Context moduleContext;
    private static boolean enabled;
    static final float ICON_SCALE = 1.12f;
    static final float ICON_SIDE_GAP_DP = 1f;
    static final float BATTERY_WIDTH_SCALE = 27f / 29f;
    static final float BATTERY_HEIGHT_SCALE = 1f;
    // The original C17 vector body ends at x=1979 in its 2200-wide canvas.
    static final float BATTERY_BODY_FRACTION = 1979f / 2200f;
    static final float EXTRA_HEIGHT_DP = 4f;
    private static final ThreadLocal<Integer> heightDepth = ThreadLocal.withInitial(() -> 0);

    static void install(ClassLoader loader) {
        // Resource consumers cache drawables and Compose vectors. Freeze the style until restart.
        enabled = IosStatusIcons.active(ShadeHook.snapshot());
        installLabels(loader);
        installHeight(loader);
        installBatteryTextLayout(loader);
        Api102Runtime.findAndHookMethod(Resources.class, "getDrawableForDensity", int.class, int.class,
            Resources.Theme.class, new HookCallback() {
                @Override protected void afterHookedMethod(Param p) {
                    if (!p.hasThrowable() && p.getResult() instanceof Drawable drawable) {
                        Drawable replacement = drawable((Resources) p.thisObject, (Integer) p.args[0], (Integer) p.args[1], drawable);
                        if (replacement != drawable) p.setResult(replacement);
                    }
                }
            });
        // XML src/compound drawables resolve through TypedArray, bypassing Resources' public getter.
        Api102Runtime.findAndHookMethod(TypedArray.class, "getDrawableForDensity", int.class, int.class, new HookCallback() {
            @Override protected void afterHookedMethod(Param p) {
                if (p.hasThrowable() || !(p.getResult() instanceof Drawable drawable)) return;
                TypedArray array = (TypedArray) p.thisObject;
                int id = array.getResourceId((Integer) p.args[0], 0);
                if (id == 0) return;
                Drawable replacement = drawable(array.getResources(), id, (Integer) p.args[1], drawable);
                if (replacement != drawable) p.setResult(replacement);
            }
        });
        // Compose vectorResource parses XML instead of calling getDrawable. Its painter stays native.
        Api102Runtime.findAndHookMethod(Resources.class, "getXml", int.class, new HookCallback() {
            @Override protected void beforeHookedMethod(Param p) {
                Resources resources = (Resources) p.thisObject;
                int source = (Integer) p.args[0];
                int id = replacement(resources, source);
                if (id == 0) return;
                try {
                    // Only literal vectors can be parsed against host Resources (no module IDs).
                    if (IosStatusResources.xmlReplacement(resources.getResourceName(source)) == 0) return;
                    p.setResult(moduleContext.getResources().getXml(id));
                    ShadeHook.resourceHit("status-bar-ios-resource-xml");
                } catch (Exception error) { failure(id, error); }
            }
        });
    }

    private static void installBatteryTextLayout(ClassLoader loader) {
        String type = "com.oplus.systemui.statusbar.pipeline.battery.ui.drawable.HorizontalBatteryContentDrawable";
        try {
            Api102Runtime.findAndHookMethod(type, loader, "getBodyRectForLayout", RectF.class, new HookCallback() {
                @Override protected void afterHookedMethod(Param p) {
                    if (!enabled || p.hasThrowable() || !(p.getResult() instanceof RectF body)) return;
                    RectF content = (RectF) p.args[0];
                    float cap = (content.right - content.left) * (1f - BATTERY_BODY_FRACTION);
                    // Keep native glyph measurement and per-level centering; exclude only the cap.
                    boolean rtl = Boolean.TRUE.equals(Reflect.callMethod(p.thisObject, "isRTLLayout"));
                    body.left = content.left + (rtl ? cap : 0f);
                    body.right = content.right - (rtl ? 0f : cap);
                }
            });
            Api102Runtime.findAndHookMethod(type, loader, "getOffsetX", new HookCallback() {
                @Override protected void afterHookedMethod(Param p) {
                    if (!enabled || p.hasThrowable()) return;
                    // Native fallback also centers on the body if a font is too large for it.
                    RectF content = (RectF) Reflect.getObjectField(p.thisObject, "rect");
                    p.setResult((content.right - content.left) * (1f - BATTERY_BODY_FRACTION) / 2f);
                }
            });
        } catch (RuntimeException error) { ShadeHook.frameworkFailure("status-bar-ios-battery-text-layout", error); }
    }

    private static void installLabels(ClassLoader loader) {
        String binding = "com.oplus.systemui.statusbar.pipeline.mobile.ui.view.OplusStatusBarMobileViewBinder$Os17Binding";
        try {
            Api102Runtime.findAndHookMethod(binding, loader, "bindNetworkTypeText",
                "com.android.systemui.statusbar.pipeline.mobile.domain.model.OplusNetworkTypeTextModel", new HookCallback() {
                    @Override protected void beforeHookedMethod(Param p) {
                        // Native null branch clears both the text and Tigo image on every update.
                        if (enabled) p.args[0] = null;
                    }
                });
            Api102Runtime.findAndHookMethod(binding, loader, "bindRoamingText", boolean.class, new HookCallback() {
                @Override protected void beforeHookedMethod(Param p) {
                    if (enabled) p.args[0] = false;
                }
            });
        } catch (RuntimeException error) { ShadeHook.frameworkFailure("status-bar-ios-c17-labels", error); }
    }

    private static void installHeight(ClassLoader loader) {
        // C17 uses these results for the root layout, all four window rotations,
        // and providedInsets (statusBars + tappableElement). Keep them consistent.
        HookCallback height = new HookCallback() {
            @Override protected void beforeHookedMethod(Param p) {
                p.setObjectExtra("ios-height-depth", heightDepth.get());
                heightDepth.set(heightDepth.get() + 1);
            }
            @Override protected void afterHookedMethod(Param p) {
                int depth = (Integer) p.getObjectExtra("ios-height-depth");
                if (depth == 0) heightDepth.remove(); else heightDepth.set(depth);
                // Some firmware delegates one height overload to the other.
                if (!enabled || depth != 0 || p.hasThrowable() || !(p.getResult() instanceof Integer original) || original <= 0) return;
                Context context = (Context) p.args[0];
                p.setResult(original + Math.round(EXTRA_HEIGHT_DP * context.getResources().getDisplayMetrics().density));
            }
        };
        for (String method : new String[]{"getStatusBarHeight", "getStatusBarHeightForRotation"}) {
            try {
                if (method.equals("getStatusBarHeight"))
                    Api102Runtime.findAndHookMethod("com.android.internal.policy.SystemBarUtils", loader, method, Context.class, height);
                else Api102Runtime.findAndHookMethod("com.android.internal.policy.SystemBarUtils", loader, method, Context.class, int.class, height);
            } catch (RuntimeException error) { ShadeHook.frameworkFailure("status-bar-ios-height-" + method, error); }
        }
    }

    static boolean scalesDimension(String name) {
        return switch (name) {
            case "com.android.systemui:dimen/status_bar_icon_container_height",
                 "com.android.systemui:dimen/status_bar_icon_drawing_size",
                 "com.android.systemui:dimen/status_bar_icon_drawing_size_dark",
                 "com.android.systemui:dimen/status_bar_icon_size",
                 "com.android.systemui:dimen/status_bar_icon_size_sp",
                 "com.android.systemui:dimen/status_bar_mobile_container_height",
                 "com.android.systemui:dimen/status_bar_mobile_signal_size",
                 "com.android.systemui:dimen/status_bar_wifi_signal_size" -> true;
            default -> false;
        };
    }

    private static Float dimension(Resources resources, int id) {
        if (!enabled || id == 0) return null;
        try {
            String name = ResourceOverrides.name(resources, id);
            if (name.equals("com.android.systemui:dimen/signal_cluster_battery_padding_new")) return ICON_SIDE_GAP_DP;
            Float dp = IosStatusResources.dimension(name);
            if (dp == null) return null;
            float scale = switch (name) {
                case "com.android.systemui:dimen/status_bar_battery_horizontal_width",
                     "com.android.systemui:dimen/status_bar_battery_horizontal_progress_start",
                     "com.android.systemui:dimen/status_bar_battery_horizontal_progress_end",
                     "com.android.systemui:dimen/battery_percent_in_text_offset_x" -> BATTERY_WIDTH_SCALE;
                case "com.android.systemui:dimen/status_bar_battery_horizontal_height",
                     "com.android.systemui:dimen/status_bar_battery_horizontal_progress_top",
                     "com.android.systemui:dimen/status_bar_battery_horizontal_progress_bottom",
                     "com.android.systemui:dimen/battery_progress_corner_radius",
                     "com.android.systemui:dimen/battery_percent_in_text_size",
                     "com.android.systemui:dimen/battery_percent_in_text_margin_top",
                     "com.android.systemui:dimen/battery_percent_in_text_offset_y" -> BATTERY_HEIGHT_SCALE;
                default -> ICON_SCALE;
            };
            return dp * scale + (name.equals("com.android.systemui:dimen/signal_cluster_battery_padding") ? ICON_SIDE_GAP_DP : 0);
        }
        catch (Resources.NotFoundException ignored) { return null; }
    }

    static boolean dimensionResult(HookCallback.Param p, String getter, Resources resources, int id) {
        if (p.hasThrowable()) return false;
        Float dp = dimension(resources, id);
        float px;
        if (dp != null) px = dp * resources.getDisplayMetrics().density;
        else {
            if (!enabled || id == 0 || !scalesDimension(ResourceOverrides.name(resources, id)) || !(p.getResult() instanceof Number original)) return false;
            px = original.floatValue() * ICON_SCALE;
        }
        // PixelSize rounds a nonzero subpixel dimension up to one pixel, like Resources.
        if (getter.equals("getDimension")) p.setResult(px);
        else if (getter.equals("getDimensionPixelOffset")) p.setResult((int) px);
        else {
            int rounded = (int) (px + .5f);
            p.setResult(rounded != 0 ? rounded : px == 0 ? 0 : px > 0 ? 1 : -1);
        }
        ShadeHook.resourceHit("status-bar-ios-resource-dimension");
        return true;
    }

    static boolean dimensionValue(HookCallback.Param p) {
        if (p.hasThrowable()) return false;
        Float dp = dimension((Resources) p.thisObject, (Integer) p.args[0]);
        TypedValue value = (TypedValue) p.args[1];
        if (dp == null) {
            if (!enabled || value.type != TypedValue.TYPE_DIMENSION || !scalesDimension(ResourceOverrides.name((Resources) p.thisObject, (Integer) p.args[0]))) return false;
            int unit = value.data & 15;
            float[] radix = {1f, 1f/128f, 1f/32768f, 1f/8388608f};
            float original = (value.data >> 8) * radix[(value.data >> 4) & 3];
            value.data = (complexDimension(original * ICON_SCALE) & ~15) | unit;
            return true;
        }
        value.type = TypedValue.TYPE_DIMENSION;
        value.data = complexDimension(dp);
        value.string = null;
        ShadeHook.resourceHit("status-bar-ios-resource-dimension");
        return true;
    }

    static int complexDimension(float dp) {
        // Status dimensions fit within 0..255 units. Android's 8p15 radix preserves
        // their fractional values without calling hidden TypedValue APIs.
        return (Math.round(dp * 32768f) << 8) | (2 << 4) | TypedValue.COMPLEX_UNIT_DIP;
    }

    static void prepare(Context context) throws android.content.pm.PackageManager.NameNotFoundException {
        // This context loads resources only; module code is already supplied by libxposed.
        moduleContext = context.createPackageContext(MODULE, 0);
    }

    static int replacement(Resources resources, int id) {
        if (!enabled || moduleContext == null || id == 0) return 0;
        try {
            int target = IosStatusResources.replacement(resources.getResourceName(id));
            return failed.contains(target) ? 0 : target;
        } catch (Resources.NotFoundException ignored) { return 0; }
    }

    static Drawable drawable(Resources resources, int id, int density, Drawable original) {
        int target = replacement(resources, id);
        if (target == 0) return original;
        try {
            Drawable icon = moduleContext.getResources().getDrawableForDensity(target, density, null);
            // Preserve callers' concrete type contracts (including AnimationDrawable).
            if (icon == null || icon.getClass() != original.getClass()) return original;
            ShadeHook.resourceHit("status-bar-ios-resource-drawable");
            return icon;
        } catch (Exception error) {
            failure(target, error);
            return original;
        }
    }

    private static void failure(int id, Exception error) {
        if (failed.add(id)) ShadeHook.frameworkFailure("status-bar-ios-resource-fallback", error);
    }

    static String state() {
        return (enabled ? "style=ios;renderer=resources;signals=pui-c17;battery=pui-c17;state=system;icons=112%;height=+4dp;gap=+2dp;battery-width=27dp;battery-height=17dp;battery-text=body-centered;network-text=hidden" : "style=system")
            + (enabled != IosStatusIcons.active(ShadeHook.snapshot()) ? ";restart-required=true" : "");
    }
    private IosStatusBarHook() { }
}
