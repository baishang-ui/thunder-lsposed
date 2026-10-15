package io.github.baishangui.thunder;

import android.graphics.Canvas;
import android.graphics.Insets;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowMetrics;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** C17: change the insets supplied by the navigation window, retaining its input region. */
final class GestureBarHook {
    private static final Map<Object, Boolean> bars = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View, Boolean> handles = Collections.synchronizedMap(new WeakHashMap<>());
    private static boolean lastHidden, lastRemoved;

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
            Api102Runtime.findAndHookMethod(name, loader, "onDraw", Canvas.class, new HookCallback() {
                @Override protected void beforeHookedMethod(Param p) {
                    handles.put((View) p.thisObject, true);
                    HookConfig c = ShadeHook.snapshot();
                    if (c.enabled && c.hideGestureBar) {
                        p.setResult(null);
                        ShadeHook.observed("gesture-bar-drawing", "hidden; gesture input retained");
                    }
                }
            });
        }
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
        if (lastHidden == hidden && lastRemoved == removed) return;
        boolean relayout = lastRemoved != removed;
        lastHidden = hidden; lastRemoved = removed;
        ArrayList<View> views;
        synchronized (handles) { views = new ArrayList<>(handles.keySet()); }
        for (View view : views) view.invalidate();
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
