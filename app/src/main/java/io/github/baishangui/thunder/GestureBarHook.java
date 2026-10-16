package io.github.baishangui.thunder;

import android.graphics.Canvas;
import android.graphics.Insets;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.content.res.Configuration;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowMetrics;
import java.lang.reflect.Array;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** C17: change the insets supplied by the navigation window, retaining its input region. */
final class GestureBarHook {
    private static final Map<Object, Boolean> bars = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View, HandleState> handles = Collections.synchronizedMap(new WeakHashMap<>());
    private static boolean lastHidden, lastRemoved;
    private static HookConfig lastConfig;

    private static final class HandleState {
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final RectF bounds = new RectF();
        Paint nativePaint;
        float intensity = Float.NaN;
        int originalWidth, appliedWidth = Integer.MIN_VALUE;
        WeakReference<Drawable> blur = new WeakReference<>(null);
        WeakReference<Object> root = new WeakReference<>(null);
        boolean blurAttempted;
        HookConfig reported;
        String pulseField;
        float additionalHeight, additionalWidth, shrinkWidth;
    }

    private static HandleState state(View view) {
        HandleState state = handles.get(view);
        if (state != null) return state;
        state = new HandleState();
        state.nativePaint = (Paint) Reflect.getObjectField(view, "mPaint");
        try {
            state.additionalHeight = ((Number) Reflect.getObjectField(view, "mAdditionalHeightForAnimation")).floatValue();
            if (view.getClass().getName().startsWith("com.oplus.")) state.pulseField = "longPressAnimProgress";
            else {
                state.additionalWidth = ((Number) Reflect.getObjectField(view, "mAdditionalWidthForAnimation")).floatValue();
                state.shrinkWidth = ((Number) Reflect.getObjectField(view, "mShrinkWidthForAnimation")).floatValue();
                state.pulseField = "mPulseAnimationProgress";
            }
        } catch (IllegalStateException absent) { state.pulseField = null; }
        handles.put(view, state);
        HandleState saved = state;
        view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) { v.invalidate(); }
            @Override public void onViewDetachedFromWindow(View v) { releaseBlur(saved); }
        });
        return state;
    }

    static void install(ClassLoader loader) {
        Api102Runtime.findAndHookMethod("com.android.systemui.navigationbar.views.NavigationBar", loader,
            "getBarLayoutParamsForRotation", int.class, WindowMetrics.class, new HookCallback() {
                @Override protected void afterHookedMethod(Param p) {
                    if (p.hasThrowable() || p.getResult() == null) return;
                    bars.put(p.thisObject, true);
                    HookConfig c = ShadeHook.snapshot();
                    if (!c.enabled || !c.removeGestureBarInset || Reflect.getIntField(p.thisObject, "mNavBarMode") != 2) return;
                    Object handler = Reflect.getObjectField(p.thisObject, "mEdgeBackGestureHandler");
                    // Accessibility may force actual navigation buttons while gesture mode is selected.
                    if (handler == null || Reflect.getBooleanField(handler, "mIsButtonForcedVisible")) return;
                    clearContentInsets(p.getResult());
                    ShadeHook.observed("gesture-bar-insets", "navigationBars=0; gesture input retained");
                }
            });
        for (String name : new String[]{
                "com.android.systemui.navigationbar.gestural.NavigationHandle",
                "com.android.systemui.navigationbar.gestural.QuickswitchOrientedNavHandle",
                "com.oplus.systemui.navigationbar.gesture.sidegesture.OplusNavigationHandle"}) {
            try { Api102Runtime.findAndHookMethod(name, loader, "onDraw", Canvas.class, new HookCallback() {
                @Override protected void beforeHookedMethod(Param p) {
                    View view = (View) p.thisObject;
                    HandleState state = state(view);
                    HookConfig c = ShadeHook.snapshot();
                    updateWidth(view, state, c);
                    if (c.enabled && c.hideGestureBar) {
                        releaseBlur(state);
                        p.setResult(null);
                        ShadeHook.observed("gesture-bar-drawing", "hidden; gesture input retained");
                    } else if (c.enabled && c.gestureStyle) {
                        draw(view, (Canvas) p.args[0], state, c);
                        p.setResult(null);
                    } else releaseBlur(state);
                }
            }); } catch (Throwable error) { ShadeHook.frameworkFailure("gesture-draw:" + name, error); }
            // Quickswitch inherits this method from NavigationHandle.
            if (name.endsWith("QuickswitchOrientedNavHandle")) continue;
            try { Api102Runtime.findAndHookMethod(name, loader, "setDarkIntensity", float.class, new HookCallback() {
                @Override protected void afterHookedMethod(Param p) {
                    if (!p.hasThrowable()) {
                        View view = (View) p.thisObject;
                        state(view).intensity = Math.max(0, Math.min(1, (Float) p.args[0]));
                        HookConfig c = ShadeHook.snapshot();
                        if (c.enabled && c.gestureStyle) view.invalidate();
                    }
                }
            }); } catch (Throwable error) { ShadeHook.frameworkFailure("gesture-color:" + name, error); }
        }
    }

    private static boolean oriented(View view) {
        return view.getClass().getName().endsWith("QuickswitchOrientedNavHandle");
    }

    private static void updateWidth(View view, HandleState state, HookConfig c) {
        if (oriented(view)) return; // This view occupies the rotation overlay, not the indicator width.
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params == null) return;
        boolean custom = c.enabled && c.gestureStyle && !c.hideGestureBar;
        if (custom) {
            if (state.appliedWidth == Integer.MIN_VALUE || params.width != state.appliedWidth)
                state.originalWidth = params.width; // Preserve native orientation/setting changes.
            int width = Math.round(c.gestureLength * view.getResources().getDisplayMetrics().density);
            state.appliedWidth = width;
            if (params.width != width) { params.width = width; view.setLayoutParams(params); }
        } else if (state.appliedWidth != Integer.MIN_VALUE) {
            if (params.width == state.appliedWidth) { params.width = state.originalWidth; view.setLayoutParams(params); }
            state.appliedWidth = Integer.MIN_VALUE;
        }
    }

    private static void draw(View view, Canvas canvas, HandleState state, HookConfig c) {
        float density = view.getResources().getDisplayMetrics().density;
        float width = view.getWidth(), height = view.getHeight();
        if (width <= 0 || height <= 0) { releaseBlur(state); return; }
        float pulse = state.pulseField == null ? 0 : ((Number) Reflect.getObjectField(view, state.pulseField)).floatValue();
        boolean shrink = "mPulseAnimationProgress".equals(state.pulseField) && Reflect.getBooleanField(view, "mShrink");
        float thickness = Math.min(c.gestureThickness * density + (shrink ? 0 : state.additionalHeight * pulse), Math.min(width, height));
        float length = Math.min(c.gestureLength * density + 2 * (shrink ? -state.shrinkWidth : state.additionalWidth) * pulse, width);
        length = Math.max(thickness, length);
        float bottom = Math.min(c.gestureBottom * density, height - thickness);
        RectF b = state.bounds;
        b.set((width - length) / 2, height - bottom - thickness, (width + length) / 2, height - bottom);
        if (oriented(view)) {
            RectF nativeBounds = (RectF) Reflect.callMethod(view, "computeHomeHandleBounds");
            int rotation = Reflect.getIntField(view, "mDeltaRotation");
            if (rotation == 1 || rotation == 3) {
                length = Math.min(c.gestureLength * density, height);
                bottom = Math.min(c.gestureBottom * density, width - thickness);
                float center = Math.max(length / 2, Math.min(height - length / 2, nativeBounds.centerY()));
                float left = rotation == 1 ? bottom : width - bottom - thickness;
                b.set(left, center - length / 2, left + thickness, center + length / 2);
            }
        }
        HookConfig.Area area = c.gestureArea;
        float corner = Math.min(Math.min(b.width(), b.height()) / 2, area.cornerEnabled ? area.corner * density : thickness / 2);
        int color = color(view, state, c, area);
        boolean blurred = area.blur && area.radius > 0 && drawBlur(view, canvas, state, area.radius, corner);
        if (!blurred && state.blur.get() != null) releaseBlur(state);
        state.paint.setColor(color);
        canvas.drawRoundRect(b.left, b.top, b.right, b.bottom, corner, corner, state.paint);
        if (state.reported != c) {
            state.reported = c;
            ShadeHook.observed("gesture-bar-style", "length=" + c.gestureLength + "dp thickness=" + c.gestureThickness
                + "dp bottom=" + c.gestureBottom + "dp blur=" + blurred
                + " colors=" + c.gestureColors + " auto=" + c.gestureAutoColor);
        }
    }

    private static int color(View view, HandleState state, HookConfig c, HookConfig.Area area) {
        int nativeColor = state.nativePaint.getColor();
        if (!c.gestureColors) return area.tint ? area.color : area.blur ? (nativeColor & 0x00ffffff) | 0x55000000 : nativeColor;
        float intensity;
        if (c.gestureAutoColor) {
            intensity = state.intensity;
            if (Float.isNaN(intensity)) {
                // Oplus retains the latest sampling result even before our first callback.
                try { intensity = ((Number) Reflect.getObjectField(view, "mLastIntensity")).floatValue(); }
                catch (IllegalStateException absent) {
                    intensity = ((nativeColor >> 16 & 255) + (nativeColor >> 8 & 255) + (nativeColor & 255)) < 384 ? 1 : 0;
                }
                state.intensity = intensity;
            }
        } else intensity = (view.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
            == Configuration.UI_MODE_NIGHT_YES ? 0 : 1;
        int color = blend(c.gestureLightColor, c.gestureDarkColor, intensity);
        int alpha = color >>> 24;
        if (area.tint) alpha = alpha * (area.color >>> 24) / 255;
        else if (area.blur) alpha = alpha / 3;
        return (color & 0x00ffffff) | (alpha << 24);
    }

    static int blend(int light, int dark, float intensity) {
        float t = Math.max(0, Math.min(1, intensity));
        int result = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            int a = light >>> shift & 255, b = dark >>> shift & 255;
            result |= Math.round(a + (b - a) * t) << shift;
        }
        return result;
    }

    private static boolean drawBlur(View view, Canvas canvas, HandleState state, int radius, float corner) {
        if (!canvas.isHardwareAccelerated()) return false;
        Object root = Reflect.callMethod(view, "getViewRootImpl");
        if (root == null) return false;
        if (state.root.get() != root) { releaseBlur(state); state.root = new WeakReference<>(root); state.blurAttempted = false; }
        Drawable blur = state.blur.get();
        if (blur == null && !state.blurAttempted) {
            state.blurAttempted = true;
            try {
                blur = (Drawable) Reflect.callMethod(root, "createBackgroundBlurDrawable");
                state.blur = new WeakReference<>(blur);
            }
            catch (Throwable error) { ShadeHook.frameworkFailure("gesture-blur", error); }
        }
        if (blur == null) return false;
        try {
            Reflect.callMethod(blur, "setBlurRadius", radius);
            Reflect.callMethod(blur, "setCornerRadius", corner);
            RectF b = state.bounds;
            blur.setBounds(Math.round(b.left), Math.round(b.top), Math.round(b.right), Math.round(b.bottom));
            blur.draw(canvas);
            return true;
        } catch (Throwable error) {
            releaseBlur(state);
            state.blurAttempted = true;
            ShadeHook.frameworkFailure("gesture-blur-draw", error);
            return false;
        }
    }

    private static void releaseBlur(HandleState state) {
        state.blurAttempted = false;
        Drawable blur = state.blur.get();
        if (blur == null) return;
        state.blur.clear();
        try { Reflect.callMethod(blur, "setBlurRadius", 0); blur.setBounds(0, 0, 0, 0); }
        catch (Throwable error) { ShadeHook.frameworkFailure("gesture-blur-release", error); }
    }

    private static void clearContentInsets(Object params) {
        Object providers = Reflect.getObjectField(params, "providedInsets");
        if (providers == null) return;
        for (int i = 0; i < Array.getLength(providers); i++) {
            Object provider = Array.get(providers, i);
            if (provider == null) continue;
            int type = (Integer) Reflect.callMethod(provider, "getType");
            if (type != WindowInsets.Type.navigationBars() && type != WindowInsets.Type.tappableElement()) continue;
            Reflect.callMethod(provider, "setInsetsSize", Insets.NONE);
            Reflect.callMethod(provider, "setMinimalInsetsSizeInDisplayCutoutSafe", Insets.NONE);
        }
        // Window height, flags, systemGestures, mandatorySystemGestures and IME stay native.
        // getBarLayoutParamsForRotation creates fresh providers, so turning off restores native values.
    }

    static void refresh() {
        HookConfig c = ShadeHook.snapshot();
        boolean hidden = c.enabled && c.hideGestureBar;
        boolean removed = c.enabled && c.removeGestureBarInset;
        if (lastConfig == c && lastHidden == hidden && lastRemoved == removed) return;
        lastConfig = c;
        boolean relayout = lastRemoved != removed;
        lastHidden = hidden; lastRemoved = removed;
        ArrayList<View> views;
        synchronized (handles) { views = new ArrayList<>(handles.keySet()); }
        for (View view : views) {
            HandleState state = handles.get(view);
            if (state == null) continue;
            updateWidth(view, state, c);
            // Drop the old compositor region before enabling, hiding or changing material.
            releaseBlur(state); state.blurAttempted = false;
            view.invalidate();
        }
        if (!relayout) return;
        ArrayList<Object> owners;
        synchronized (bars) { owners = new ArrayList<>(bars.keySet()); }
        for (Object bar : owners) {
            try {
                // Native relayout regenerates all four rotation variants and sends them to WMS.
                Reflect.callMethod(bar, "repositionNavigationBar", Reflect.getIntField(bar, "mCurrentRotation"));
            } catch (Throwable error) { ShadeHook.frameworkFailure("gesture-bar-refresh", error); }
        }
    }
}
