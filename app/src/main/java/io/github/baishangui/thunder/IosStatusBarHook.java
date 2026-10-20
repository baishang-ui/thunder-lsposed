package io.github.baishangui.thunder;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/** Native resource styling, with scoped C17 label and status-window sizing hooks. */
final class IosStatusBarHook {
    private static final String MODULE = "io.github.baishangui.thunder";
    private static final Set<Integer> failed = ConcurrentHashMap.newKeySet();
    private static volatile Context moduleContext;
    private static final String HORIZONTAL_BATTERY="com.oplus.systemui.statusbar.pipeline.battery.ui.drawable.HorizontalBatteryContentDrawable";
    private static final String[] BADGE_NAMES={"mobile_type_text","mobile_type_image","mobile_type","mobile_type_alone","wifi_left","wifi_left_tv"};
    private static volatile int[] badgeIds=new int[0];
    private static final Map<View,Float> wifiTranslations=new WeakHashMap<>();
    private static boolean enabled;
    static final float ICON_SCALE = 1.12f;
    static final float ICON_SIDE_GAP_DP = 1f;
    // Enlarge the complete original 93x55 canvas without changing its proportions.
    static final float BATTERY_WIDTH_DP = 34f;
    static final float BATTERY_HEIGHT_DP = BATTERY_WIDTH_DP * 55f / 93f;
    static final float BATTERY_WIDTH_SCALE = 1f;
    static final float BATTERY_HEIGHT_SCALE = 1f;
    // C16's body ends at x=85 in its 93-wide canvas; exclude its cap from text.
    static final float BATTERY_BODY_FRACTION = 85f / 93f;
    static final float EXTRA_HEIGHT_DP = 4f;
    private static final ThreadLocal<Integer> heightDepth = ThreadLocal.withInitial(() -> 0);

    static void install(ClassLoader loader) {
        // Resource consumers cache drawables and Compose vectors. Freeze the style until restart.
        enabled = IosStatusIcons.active(ShadeHook.snapshot());
        installLabels(loader);
        installBadgeGuards();
        installHeight(loader);
        installBatteryTextLayout(loader);
        installBatteryPadding(loader);
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
        String type = HORIZONTAL_BATTERY;
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

    private static void installBatteryPadding(ClassLoader loader) {
        try {
            Api102Runtime.findAndHookMethod("com.oplus.systemui.statusbar.pipeline.battery.ui.drawable.BatteryBarDrawableBase",loader,"updatePadding",new HookCallback() {
                @Override protected void afterHookedMethod(Param p) {
                    if (enabled && !p.hasThrowable() && p.thisObject.getClass().getName().equals(HORIZONTAL_BATTERY)) clearBatteryPadding(p.thisObject,false);
                }
            });
        } catch (RuntimeException error) { ShadeHook.frameworkFailure("status-bar-ios-battery-padding-base",error); }
        try {
            Api102Runtime.findAndHookMethod(HORIZONTAL_BATTERY,loader,"updatePadding",new HookCallback() {
                @Override protected void afterHookedMethod(Param p) {
                    if (enabled && !p.hasThrowable()) clearBatteryPadding(p.thisObject,true);
                }
            });
        } catch (RuntimeException error) { ShadeHook.frameworkFailure("status-bar-ios-battery-padding",error); }
    }

    private static void clearBatteryPadding(Object drawable,boolean outside) {
        // Source vectors already contain their own padding. Native 2dp insets,
        // including theme-derived insets, would shrink the actual visible icon.
        for (String edge : new String[]{"Left","Right","Top","Bottom"}) {
            Reflect.callMethod(drawable,"setPadding"+edge,0f);
            if (outside) Reflect.setObjectField(drawable,"outPadding"+edge,0f);
        }
        ShadeHook.resourceHit("status-bar-ios-battery-padding");
    }

    private static boolean isBadge(View view) {
        if (!enabled) return false;
        int id=view.getId();
        if (id<=0) return false;
        boolean candidate=false;
        for (int target : badgeIds) if (target!=0 && target==id) { candidate=true;break; }
        if (!candidate) return false;
        return switch (ResourceOverrides.name(view.getResources(),id)) {
            case "com.android.systemui:id/mobile_type_text", "com.android.systemui:id/mobile_type_image",
                 "com.android.systemui:id/mobile_type", "com.android.systemui:id/mobile_type_alone",
                 "com.android.systemui:id/wifi_left", "com.android.systemui:id/wifi_left_tv" -> true;
            default -> false;
        };
    }

    private static void installBadgeGuards() {
        // Fast integer-ID rejection keeps unrelated views on their native path.
        // The package-qualified check also rejects resource-ID collisions.
        try {
            Api102Runtime.findAndHookMethod(View.class,"setVisibility",int.class,new HookCallback() {
                @Override protected void beforeHookedMethod(Param p) {
                    if (p.thisObject instanceof View view && isBadge(view)) {
                        if (!p.args[0].equals(View.GONE)) ShadeHook.resourceHit("status-bar-ios-badge-guard");
                        p.args[0]=View.GONE;
                    }
                }
            });
        } catch (RuntimeException error) { ShadeHook.frameworkFailure("status-bar-ios-badge-visibility",error); }
        try {
            // This central framework method also catches optimized callers that
            // bypass setVisibility. Other view flags are left unchanged.
            Api102Runtime.findAndHookMethod(View.class,"setFlags",int.class,int.class,new HookCallback() {
                @Override protected void beforeHookedMethod(Param p) {
                    if ((((Integer)p.args[1])&12)!=0 && p.thisObject instanceof View view && isBadge(view)) {
                        if ((((Integer)p.args[0])&12)!=View.GONE) ShadeHook.resourceHit("status-bar-ios-badge-guard");
                        p.args[0]=(((Integer)p.args[0])&~12)|View.GONE;
                        p.args[1]=((Integer)p.args[1])|12;
                    }
                }
            });
        } catch (RuntimeException error) { ShadeHook.frameworkFailure("status-bar-ios-badge-flags",error); }
        HookCallback text=new HookCallback() {
            @Override protected void beforeHookedMethod(Param p) {
                if (p.thisObject instanceof TextView view && isBadge(view)) p.args[0]=null;
            }
            @Override protected void afterHookedMethod(Param p) {
                if (!p.hasThrowable() && p.thisObject instanceof TextView view && isBadge(view)) {
                    view.setContentDescription(null);
                    view.setVisibility(View.GONE);
                    ShadeHook.resourceHit("status-bar-ios-badge-guard");
                }
            }
        };
        try {
            Api102Runtime.findAndHookMethod(TextView.class,"setText",CharSequence.class,"android.widget.TextView$BufferType",text);
        } catch (RuntimeException error) { ShadeHook.frameworkFailure("status-bar-ios-badge-text",error); }
        try {
            Api102Runtime.findAndHookMethod(TextView.class,"setText",CharSequence.class,"android.widget.TextView$BufferType",boolean.class,int.class,text);
        } catch (RuntimeException error) { ShadeHook.frameworkFailure("status-bar-ios-badge-text-internal",error); }
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
                    @Override protected void afterHookedMethod(Param p) {
                        if (enabled && !p.hasThrowable()) hideMobileBadges(p.thisObject);
                    }
                });
            Api102Runtime.findAndHookMethod(binding, loader, "bindRoamingText", boolean.class, new HookCallback() {
                @Override protected void beforeHookedMethod(Param p) {
                    if (enabled) p.args[0] = false;
                }
            });
        } catch (RuntimeException error) { ShadeHook.frameworkFailure("status-bar-ios-c17-labels", error); }
        // The collector itself must clear the views after every update: ART can
        // inline bindNetworkTypeText into this vendor collector on device.
        try {
            Api102Runtime.findAndHookMethod(binding+"$bindSubclassFlows$1$1", loader, "emit",
                Object.class, "kotlin.coroutines.Continuation", new HookCallback() {
                    @Override protected void afterHookedMethod(Param p) {
                        if (enabled && !p.hasThrowable())
                            hideMobileBadges(Reflect.getObjectField(p.thisObject, "this$0"));
                    }
                });
        } catch (RuntimeException error) { ShadeHook.frameworkFailure("status-bar-ios-c17-label-collector", error); }
        installWifiLabels(loader);
    }

    private static void installWifiLabels(ClassLoader loader) {
        String binding="com.oplus.systemui.statusbar.pipeline.OplusWifiSignalExImpl";
        try {
            Api102Runtime.findAndHookMethod(binding, loader, "bindEx", ViewGroup.class,
                "kotlinx.coroutines.flow.MutableStateFlow",
                "com.android.systemui.statusbar.pipeline.wifi.ui.viewmodel.LocationBasedWifiViewModel", new HookCallback() {
                    @Override protected void afterHookedMethod(Param p) {
                        if (!enabled || p.hasThrowable() || !(p.args[0] instanceof ViewGroup root)) return;
                        for (String name : new String[]{"wifi_left", "wifi_left_tv"}) {
                            int id=root.getResources().getIdentifier(name,"id","com.android.systemui");
                            if (id!=0) hideBadge(root.findViewById(id));
                        }
                        int signalId=root.getResources().getIdentifier("wifi_signal","id","com.android.systemui");
                        View signal=signalId==0?null:root.findViewById(signalId);
                        if (signal!=null) {
                            float original=wifiTranslations.computeIfAbsent(signal,View::getTranslationY);
                            signal.setTranslationY(original+1f); // One physical pixel, independent of density.
                            ShadeHook.resourceHit("status-bar-ios-wifi-baseline");
                        }
                    }
                });
        } catch (RuntimeException error) { ShadeHook.frameworkFailure("status-bar-ios-wifi-label-init", error); }
        try {
            // C17 renders Wi-Fi 4/5/6/6E/7 as either text or an image. This
            // audited binding owns both alternatives, separate from signal bars.
            Api102Runtime.findAndHookMethod(binding+"$bindEx$2$1$2$1", loader, "invokeSuspend", Object.class, new HookCallback() {
                @Override protected void afterHookedMethod(Param p) {
                    if (!enabled || p.hasThrowable()) return;
                    hideBadge(Reflect.getObjectField(p.thisObject,"$wifiLeftTv"));
                    hideBadge(Reflect.getObjectField(p.thisObject,"$wifiLeft"));
                }
            });
        } catch (RuntimeException error) { ShadeHook.frameworkFailure("status-bar-ios-wifi-label-update", error); }
    }

    private static void hideMobileBadges(Object binding) {
        if (binding==null) return;
        for (String field : new String[]{"mobileTypeText","mobileTypeImage","mobileRoamingText"})
            hideBadge(Reflect.getObjectField(binding,field));
    }

    private static void hideBadge(Object target) {
        if (!(target instanceof View view)) return;
        if (view instanceof TextView text) text.setText(null);
        view.setContentDescription(null);
        view.setVisibility(View.GONE);
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
        int[] ids=new int[BADGE_NAMES.length];
        for (int i=0;i<ids.length;i++) ids[i]=context.getResources().getIdentifier(BADGE_NAMES[i],"id","com.android.systemui");
        badgeIds=ids;
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
        return (enabled ? "style=ios;renderer=resources;signals=status-c16-c17-stacked;battery=status-c16;state=system;icons=112%;height=+4dp;gap=+2dp;battery-proportions=93:55;battery-width="+BATTERY_WIDTH_DP+"dp;battery-height="+BATTERY_HEIGHT_DP+"dp;battery-text=body-centered;battery-padding=0;network-text=hidden;wifi-offset=1px" : "style=system")
            + (enabled != IosStatusIcons.active(ShadeHook.snapshot()) ? ";restart-required=true" : "");
    }
    private IosStatusBarHook() { }
}
