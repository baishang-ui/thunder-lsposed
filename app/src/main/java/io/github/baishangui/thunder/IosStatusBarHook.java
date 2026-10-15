package io.github.baishangui.thunder;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.graphics.drawable.VectorDrawable;
import android.view.View;
import android.widget.ImageView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Draw in the native view's existing cell. Never replace host objects or mutate its state/layout. */
final class IosStatusBarHook {
    private static final String LEGACY = "com.android.systemui.statusbar.StatusBarIconView";
    private static final String METER = "com.oplus.systemui.statusbar.pipeline.battery.ui.view.StatBatteryMeterView";
    private static final String SYMBOL = "thunder.ios.source";
    private record Renderer(IosStatusIcons.Symbol symbol, IosIconDrawable drawable) { }
    private static final Map<View,Boolean> views = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Drawable,Boolean> vectors = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object,Boolean> failed = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object,Renderer> renderers = Collections.synchronizedMap(new WeakHashMap<>());

    static void install(ClassLoader loader) {
        // Some SystemUI icons bypass ImageView.onDraw; intercept their outer boundary as well.
        Api102Runtime.findAndHookMethod(ImageView.class, "onDraw", Canvas.class, imageDrawing());
        Api102Runtime.findAndHookMethod(Reflect.findClass(LEGACY, loader), "onDraw", Canvas.class, imageDrawing());
    }

    static void installSymbols(ClassLoader loader) {
        Api102Runtime.findAndHookMethod(Reflect.findClass(LEGACY, loader), "getIcon",
            "com.android.internal.statusbar.StatusBarIcon", new HookCallback() {
                @Override protected void afterHookedMethod(Param p) {
                    if (p.hasThrowable() || !(p.getResult() instanceof Drawable drawable)) return;
                    // Remember provenance only. The native return value, type and callback are untouched.
                    Reflect.removeAdditionalInstanceField(drawable, SYMBOL);
                    if (Reflect.getObjectField(p.thisObject, "mNotification") != null || p.args[0] == null) return;
                    Icon source = (Icon) Reflect.getObjectField(p.args[0], "icon");
                    if (source == null || source.getType() != Icon.TYPE_RESOURCE
                            || !"com.android.systemui".equals(source.getResPackage())) return;
                    String slot = (String) Reflect.getObjectField(p.thisObject, "mSlot");
                    var symbol = IosStatusIcons.slot(slot, resourceName(((View) p.thisObject).getResources(), source.getResId()));
                    if (symbol != null) Reflect.setAdditionalInstanceField(drawable, SYMBOL, symbol);
                }
            });
    }

    static void installResources(ClassLoader loader) {
        // Modern binders may load a drawable, then call setImageDrawable (which clears mResource).
        Api102Runtime.findAndHookMethod(Reflect.findClass("androidx.appcompat.content.res.AppCompatResources", loader),
            "getDrawable", Context.class, int.class, new HookCallback() {
                @Override protected void afterHookedMethod(Param p) {
                    if (p.hasThrowable() || !(p.getResult() instanceof Drawable drawable)) return;
                    var symbol = IosStatusIcons.system(resourceName(((Context) p.args[0]).getResources(), (Integer) p.args[1]));
                    if (symbol != null) {
                        Reflect.setAdditionalInstanceField(drawable, SYMBOL, symbol);
                        if (symbol.row() > 0 && drawable instanceof VectorDrawable) vectors.put(drawable, Boolean.TRUE);
                    } else Reflect.removeAdditionalInstanceField(drawable, SYMBOL);
                }
            });
    }

    static void installStackedDrawing() {
        // C17's native Compose painter draws these vectors directly, without an ImageView.
        // Keep the vector/painter, intrinsic size, tint, animation and native SIM selection intact.
        Api102Runtime.findAndHookMethod(VectorDrawable.class, "draw", Canvas.class, new HookCallback() {
            @Override protected void beforeHookedMethod(Param p) {
                Drawable nativeIcon = (Drawable) p.thisObject;
                if (!vectors.containsKey(nativeIcon) || !active(nativeIcon)) return;
                guarded(nativeIcon, p, () -> {
                    var symbol = (IosStatusIcons.Symbol) Reflect.getAdditionalInstanceField(nativeIcon, SYMBOL);
                    if (symbol == null || symbol.row() == 0 || nativeIcon.getBounds().isEmpty()) return false;
                    IosIconDrawable icon = renderer(nativeIcon, symbol);
                    ColorStateList tint = vectorTint(nativeIcon);
                    icon.color(tint != null ? tint.getColorForState(nativeIcon.getState(), tint.getDefaultColor()) : 0xffffffff);
                    icon.setAlpha(nativeIcon.getAlpha());
                    icon.setColorFilter(nativeIcon.getColorFilter());
                    icon.setBounds(nativeIcon.getBounds());
                    icon.draw((Canvas) p.args[0]);
                    ShadeHook.resourceHit("status-bar-ios-stacked-native-draw");
                    return true;
                });
            }
        });
    }

    static void installBattery(ClassLoader loader) {
        // Repaint the meter's contents from its read-only model, including the internal percentage.
        // No binder re-entry, model.copy(), forced battery style or visibility changes.
        Api102Runtime.findAndHookMethod(Reflect.findClass(METER, loader), "dispatchDraw", Canvas.class, new HookCallback() {
            @Override protected void beforeHookedMethod(Param p) {
                View meter = (View) p.thisObject;
                views.put(meter, Boolean.TRUE);
                if (!active(meter)) return;
                guarded(meter, p, () -> drawBattery(meter, (Canvas) p.args[0]));
            }
        });
    }

    private static HookCallback imageDrawing() {
        return new HookCallback() {
            @Override protected void beforeHookedMethod(Param p) {
                ImageView image = (ImageView) p.thisObject;
                // Reject non-status ImageViews before reading host internals; notification checks are guarded.
                if (!statusImage(image)) return;
                views.put(image, Boolean.TRUE);
                if (!active(image)) return;
                guarded(image, p, () -> drawImage(image, (Canvas) p.args[0]));
            }
        };
    }

    private interface Drawing { boolean draw(); }
    private static void guarded(Object view, HookCallback.Param p, Drawing drawing) {
        Canvas canvas = (Canvas) p.args[0];
        int save = canvas.save();
        try {
            // Suppress the original only after the entire custom draw succeeds.
            if (drawing.draw()) p.setResult(null);
        } catch (Throwable error) {
            failed.put(view, Boolean.TRUE);
            renderers.remove(view);
            ShadeHook.frameworkFailure("status-bar-ios-draw-fallback", error);
        } finally {
            canvas.restoreToCount(save);
        }
    }

    private static boolean active(Object view) {
        return IosStatusIcons.active(ShadeHook.snapshot()) && !failed.containsKey(view);
    }

    private static boolean drawImage(ImageView image, Canvas canvas) {
        Drawable nativeIcon = image.getDrawable();
        if (nativeIcon == null) return false;
        boolean legacy = instanceOf(image, LEGACY);
        if (legacy && Reflect.getObjectField(image, "mNotification") != null) return false;
        // Native StatusBarIconView also paints overflow dots and animates icon appearance.
        if (legacy && Reflect.getIntField(image, "mVisibleState") != 0) return false;
        var symbol = (IosStatusIcons.Symbol) Reflect.getAdditionalInstanceField(nativeIcon, SYMBOL);
        if (!legacy && symbol == null) {
            int id = Reflect.getIntField(image, "mResource");
            if (id != 0) symbol = IosStatusIcons.system(resourceName(image.getResources(), id));
        }
        Paint signalPaint = null;
        if (Boolean.FALSE.equals(signalOwner(image)) && nativeIcon.getClass().getName().equals("com.android.settingslib.graph.SignalDrawable")) {
            int encoded = nativeIcon.getLevel(), level = encoded & 255, maximum = ((encoded >> 8) & 255) - 1, state = encoded >>> 16;
            if (state > 3 || maximum < 1 || maximum > 5 || level > maximum) return false;
            symbol = new IosStatusIcons.Symbol(state == 1 ? IosStatusIcons.Kind.NO_SERVICE : IosStatusIcons.Kind.CELLULAR,
                state == 1 ? 0 : level, maximum).marked(state == 2 ? IosStatusIcons.WARNING : state == 3 ? IosStatusIcons.CONNECTING : 0);
            signalPaint = (Paint) Reflect.getObjectField(nativeIcon, "mForegroundPaint");
        }
        if (symbol == null) return false;
        IosIconDrawable icon = renderer(image, symbol);
        ColorStateList tint = image.getImageTintList();
        if (tint == null) tint = vectorTint(nativeIcon);
        icon.color(signalPaint != null ? signalPaint.getColor() | 0xff000000
            : tint != null ? tint.getColorForState(image.getDrawableState(), tint.getDefaultColor()) : 0xffffffff);
        icon.setColorFilter(signalPaint != null ? signalPaint.getColorFilter() : nativeIcon.getColorFilter());
        int alpha = signalPaint != null ? signalPaint.getAlpha() : nativeIcon.getAlpha();
        float appear = legacy ? (Float) Reflect.getObjectField(image, "mIconAppearAmount") : 1f;
        if (appear <= 0 || !Float.isFinite(appear)) return false;
        icon.setAlpha(alpha);
        if (!bounds(icon, image, appear)) return false;
        icon.draw(canvas);
        ShadeHook.resourceHit("status-bar-ios-view-draw");
        return true;
    }

    private static ColorStateList vectorTint(Drawable drawable) {
        if (!(drawable instanceof VectorDrawable)) return null;
        // VectorDrawable's public getColorFilter excludes its internal tint filter on Android.
        // Read the original state; a changed platform contract is contained by guarded().
        return (ColorStateList) Reflect.getObjectField(Reflect.getObjectField(drawable, "mVectorState"), "mTint");
    }

    private static boolean drawBattery(View meter, Canvas canvas) {
        if (Reflect.getBooleanField(meter, "hidden") || Reflect.getBooleanField(meter, "disable")) return false;
        Object style = Reflect.getObjectField(meter, "batteryIconStyle");
        Object model = Reflect.getObjectField(meter, "batteryIcon");
        if (style == null || model == null) return false;
        int mode = (Integer) Reflect.callMethod(style, "getIconStyle");
        // Hidden and percentage-only modes keep the native renderer.
        if (mode != 1 && mode != 3 && mode != 4 && mode != 5) return false;
        int level = (Integer) Reflect.callMethod(model, "getPowerLevel");
        if (level < 0 || level > 100) return false;
        IosIconDrawable icon = renderer(meter, new IosStatusIcons.Symbol(IosStatusIcons.Kind.BATTERY, level, 100));
        int color = (Integer) Reflect.callMethod(model, "getTintColor");
        int fill = (Integer) Reflect.callMethod(model, "getProgressColor");
        boolean charging = (Integer) Reflect.callMethod(model, "getChargeIconId") > 0;
        Object charge = Reflect.getObjectField(meter, "batteryCharge");
        if (charge != null) charging |= Reflect.getBooleanField(charge, "isVisible");
        Object reverse = Reflect.getObjectField(meter, "batteryReverse");
        // Preserve the separate native reverse-charge badge and its layout.
        if (reverse != null && Reflect.getBooleanField(reverse, "isVisible")) return false;
        icon.color(color);
        icon.battery(charging, fill);
        icon.setAlpha(255);
        if (!bounds(icon, meter, 1f)) return false;
        icon.draw(canvas);
        ShadeHook.resourceHit("status-bar-ios-battery-view-draw");
        return true;
    }

    private static IosIconDrawable renderer(Object view, IosStatusIcons.Symbol symbol) {
        Renderer current = renderers.get(view);
        if (current == null || !current.symbol.equals(symbol)) {
            current = new Renderer(symbol, new IosIconDrawable(symbol));
            renderers.put(view, current);
        }
        return current.drawable;
    }

    private static boolean bounds(IosIconDrawable icon, View view, float appear) {
        int left = view.getPaddingLeft(), top = view.getPaddingTop();
        int right = view.getWidth() - view.getPaddingRight(), bottom = view.getHeight() - view.getPaddingBottom();
        if (right <= left || bottom <= top) return false;
        float density = view.getResources().getDisplayMetrics().density;
        float scale = Math.min(1f, Math.min((right-left) / (IosIconDrawable.cellWidth(icon.symbol())*density),
            (bottom-top) / (IosIconDrawable.cellHeight()*density))) * Math.min(1f, appear);
        int width = Math.round(IosIconDrawable.cellWidth(icon.symbol())*density*scale);
        int height = Math.round(IosIconDrawable.cellHeight()*density*scale);
        if (width <= 0 || height <= 0) return false;
        int x = left + (right-left-width)/2, y = top + (bottom-top-height)/2;
        icon.setBounds(x, y, x+width, y+height);
        return true;
    }

    private static boolean statusImage(ImageView image) {
        View view = image;
        for (int depth = 0; depth < 6 && view != null; depth++) {
            if (instanceOf(view, LEGACY)
                    || instanceOf(view, "com.android.systemui.statusbar.pipeline.shared.ui.view.ModernStatusBarView")
                    || instanceOf(view, "com.android.systemui.statusbar.pipeline.wifi.ui.view.ModernStatusBarWifiView")
                    || instanceOf(view, "com.android.systemui.statusbar.pipeline.mobile.ui.view.ModernStatusBarMobileView")
                    || instanceOf(view, "com.oplus.systemui.statusbar.phone.dynamic.icon.OplusStatusBarIconView")) return true;
            view = view.getParent() instanceof View parent ? parent : null;
        }
        return false;
    }
    private static boolean instanceOf(Object value, String name) {
        for (Class<?> type = value.getClass(); type != null; type = type.getSuperclass())
            if (type.getName().equals(name)) return true;
        return false;
    }
    static Boolean signalOwner(View image) {
        View view = image;
        for (int depth = 0; depth < 6 && view != null; depth++) {
            if (instanceOf(view, "com.android.systemui.statusbar.pipeline.wifi.ui.view.ModernStatusBarWifiView")) return true;
            if (instanceOf(view, "com.android.systemui.statusbar.pipeline.mobile.ui.view.ModernStatusBarMobileView")) return false;
            view = view.getParent() instanceof View parent ? parent : null;
        }
        return null;
    }
    static String resourceName(Resources resources, int id) {
        try { return resources.getResourceName(id); }
        catch (Resources.NotFoundException ignored) { return ""; }
    }
    static void refresh() {
        ArrayList<View> current;
        synchronized (views) { current = new ArrayList<>(views.keySet()); }
        // invalidate posts no binder calls and does not re-enter setters, measurement or native binders.
        for (View view : current) view.postInvalidateOnAnimation();
        ArrayList<Drawable> stacked;
        synchronized (vectors) { stacked = new ArrayList<>(vectors.keySet()); }
        for (Drawable drawable : stacked) drawable.invalidateSelf();
    }
    private IosStatusBarHook() { }
}
