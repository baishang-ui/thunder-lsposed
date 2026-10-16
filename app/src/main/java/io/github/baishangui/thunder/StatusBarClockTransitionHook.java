package io.github.baishangui.thunder;

import android.text.Layout;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;

/** Align the clocks used by C17's notification-center seamless transfer. */
final class StatusBarClockTransitionHook {
    private static final Map<Object, Boolean> controllers = new WeakHashMap<>();
    private record Clocks(TextView status, TextView shade, ViewGroup fake) { }

    static void install(ClassLoader loader) {
        Class<?> type = Reflect.findClass("com.oplus.systemui.separate.OplusSimpleQSFakeController", loader);
        Api102Runtime.hookAllConstructors(type, new HookCallback() {
            @Override protected void afterHookedMethod(Param p) {
                if (p.hasThrowable()) return;
                controllers.put(p.thisObject, Boolean.FALSE);
                // ART may have inlined the tiny scale/pivot methods into these
                // Kotlin listeners. Invalidate their compiled callers as well.
                for (String field : new String[]{"fractionChangeListener", "panelTranslationYChangeListener"}) {
                    Class<?> listener = Reflect.getObjectField(p.thisObject, field).getClass();
                    for (var method : listener.getDeclaredMethods()) Api102Runtime.deoptimizeCallers(listener, method.getName());
                }
            }
        });
        Api102Runtime.findAndHookMethod(type, "updateClockPivot$1", new HookCallback() {
            @Override protected void afterHookedMethod(Param p) {
                if (p.hasThrowable()) return;
                Clocks clocks = clocks(p.thisObject);
                if (clocks == null) return;
                boolean rtl = clocks.status.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
                clocks.shade.setPivotX(timeAnchor(clocks.shade, rtl));
                clocks.shade.setPivotY(clocks.shade.getBaseline());
                clocks.fake.setPivotX(timeAnchor(clocks.status, rtl));
                clocks.fake.setPivotY((clocks.fake.getHeight() - clocks.status.getHeight()) / 2f + clocks.status.getBaseline());
            }
        });
        Api102Runtime.findAndHookMethod(type, "access$updateFakeToStatusBarTranslation", type, new HookCallback() {
            @Override protected void afterHookedMethod(Param p) {
                if (p.hasThrowable()) return;
                Object controller = p.args[0];
                Clocks clocks = clocks(controller);
                if (clocks == null) return;
                boolean rtl = clocks.status.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
                float padding = number(controller, "statusbarClockPadding");
                float source = timeAnchor(clocks.status, rtl) - (rtl ? clocks.status.getWidth() - padding : padding);
                float target = timeAnchor(clocks.shade, rtl) - (rtl ? clocks.shade.getWidth() : 0f);
                Reflect.setObjectField(controller, "fakeToClockTranslationX", number(controller, "fakeToClockTranslationX") + source - target);
            }
        });
        for (boolean fake : new boolean[]{true, false}) {
            Api102Runtime.findAndHookMethod(type, fake ? "access$updateFakeSimpleQSTranslation" : "access$updateSimpleQSTranslation", type, float.class, new HookCallback() {
                @Override protected void afterHookedMethod(Param p) {
                    if (p.hasThrowable()) return;
                    Clocks clocks = clocks(p.args[0]);
                    if (clocks == null) return;
                    float sourceBaseline = clocks.status.getBaseline() - clocks.status.getHeight() / 2f;
                    float targetBaseline = clocks.shade.getBaseline() - clocks.shade.getHeight() / 2f;
                    float fraction = (Float) p.args[1];
                    View view = fake ? clocks.fake : clocks.shade;
                    // Native fakeToClockTranslationY also moves notification icons.
                    // Apply the baseline correction only to the two clock views.
                    view.setTranslationY(view.getTranslationY() + (targetBaseline - sourceBaseline) * (fake ? fraction : fraction - 1f));
                }
            });
        }
        for (boolean fake : new boolean[]{true, false}) {
            Api102Runtime.findAndHookMethod(type, fake ? "updateFakeClockScale" : "updateQSClockScale", float.class, new HookCallback() {
                @Override protected void afterHookedMethod(Param p) {
                    if (p.hasThrowable()) return;
                    Clocks clocks = clocks(p.thisObject);
                    if (clocks == null) return;
                    float ratioY = clocks.status.getTextSize() / clocks.shade.getTextSize();
                    float ratioX = ratioY * clocks.status.getTextScaleX() / clocks.shade.getTextScaleX();
                    if (ratioX <= 0f || ratioY <= 0f || !Float.isFinite(ratioX) || !Float.isFinite(ratioY)) return;
                    // Keep the native system font/display scale and its cap,
                    // but interpolate between the actual two font dimensions.
                    View view = fake ? clocks.fake : clocks.shade;
                    float nativeScale = view.getScaleY();
                    float fraction = (Float) p.args[0];
                    view.setScaleX(fake ? nativeScale / ratioX + (1f - 1f / ratioX) * (1f - fraction)
                        : nativeScale + (ratioX - 1f) * (1f - fraction));
                    view.setScaleY(fake ? nativeScale / ratioY + (1f - 1f / ratioY) * (1f - fraction)
                        : nativeScale + (ratioY - 1f) * (1f - fraction));
                }
            });
        }
        Api102Runtime.deoptimizeCallers(type, "access$updateFakeToStatusBarTranslation", "access$resetfakeViewsStartLocation", "access$resetQsViewsExpandLocation");
    }

    private static float number(Object owner, String name) { return ((Number) Reflect.getObjectField(owner, name)).floatValue(); }

    private static Clocks clocks(Object controller) {
        controllers.putIfAbsent(controller, Boolean.FALSE);
        HookConfig c = ShadeHook.snapshot();
        if (!c.enabled || (!c.statusBarClock && !c.statusBarCompressClock)) return null;
        Object header = Reflect.getObjectField(controller, "oplusQSSimpleHeader");
        Object container = Reflect.getObjectField(controller, "fakeClockViewContainer");
        if (header == null || !(container instanceof ViewGroup fake) || fake.getChildCount() == 0) return null;
        Object real = Reflect.callMethod(fake.getChildAt(0), "getMView");
        Object target = Reflect.callMethod(header, "getClockView");
        if (!(real instanceof TextView status) || !(target instanceof TextView shade)
                || !StatusBarClockHook.isStatusClock(status) || status.getLayout() == null || shade.getLayout() == null
                || status.getBaseline() < 0 || shade.getBaseline() < 0 || status.getTextSize() <= 0 || shade.getTextSize() <= 0) return null;
        controllers.put(controller, Boolean.TRUE);
        return new Clocks(status, shade, fake);
    }

    // Anchor HH:mm itself: dates/weekdays can precede it, seconds/AM-PM can
    // follow it, and Layout supplies the actual compressed/bidi coordinates.
    static float timeAnchor(TextView clock, boolean rtl) {
        String text = clock.getText().toString();
        int separator = text.indexOf(':');
        if (separator < 0) separator = text.indexOf('.');
        int start = separator, end = separator + 1;
        if (separator >= 0) {
            while (start > 0 && Character.isDigit(text.charAt(start - 1))) start--;
            while (end < text.length() && Character.isDigit(text.charAt(end))) end++;
        }
        Layout layout = clock.getLayout();
        if (start < 0 || start == separator || end == separator + 1 || layout == null)
            return rtl ? clock.getWidth() - clock.getPaddingStart() : clock.getPaddingStart();
        float first = layout.getPrimaryHorizontal(start), last = layout.getPrimaryHorizontal(end);
        return clock.getCompoundPaddingLeft() - clock.getScrollX() + (rtl ? Math.max(first, last) : Math.min(first, last));
    }

    static void invalidate() {
        HookConfig c = ShadeHook.snapshot();
        boolean active = c.enabled && (c.statusBarClock || c.statusBarCompressClock);
        for (Object controller : new ArrayList<>(controllers.keySet())) {
            if (active || Boolean.TRUE.equals(controllers.get(controller)))
                Reflect.setBooleanField(controller, "isUpdateFakeViewLocation", true);
            if (!active) controllers.put(controller, Boolean.FALSE);
        }
    }
}
