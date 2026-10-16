package io.github.baishangui.thunder;

import android.view.View;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;

/** C17 home-status-bar padding. Native cutout layout and its spacer remain intact. */
final class StatusBarLayoutHook {
    private static final Map<Object, int[]> owners = new WeakHashMap<>();

    static void install(ClassLoader loader) {
        Api102Runtime.findAndHookMethod("com.oplus.systemui.statusbar.phone.PhoneStatusBarViewExImpl",
            loader, "updateContentsPadding", new HookCallback() {
                @Override protected void beforeHookedMethod(Param p) {
                    int[] previous = owners.get(p.thisObject);
                    if (previous == null || previous[1] < 0) return;
                    Object contents = Reflect.getObjectField(p.thisObject, "statusBarContents");
                    if (contents instanceof View root) {
                        View end = child(root, "status_bar_end_side_container");
                        if (end != null && (end.getPaddingStart() == previous[1] || root.getLayoutDirection() != previous[2]))
                            end.setPaddingRelative(previous[0], end.getPaddingTop(), end.getPaddingEnd(), end.getPaddingBottom());
                    }
                    owners.remove(p.thisObject);
                }
                @Override protected void afterHookedMethod(Param p) {
                    if (p.hasThrowable()) return;
                    owners.putIfAbsent(p.thisObject, new int[]{0, -1, 0});
                    apply(p.thisObject);
                }
            });
    }

    static void apply(Object owner) {
        HookConfig c = ShadeHook.snapshot();
        if (!c.enabled || (!c.statusBarMargins && !c.statusBarCenterEnabled)) return;
        Object contents = Reflect.getObjectField(owner, "statusBarContents");
        if (!(contents instanceof View root)) return;
        View start = child(root, "status_bar_start_side_container");
        View end = child(root, "status_bar_end_side_container");
        if (start == null || end == null) return;
        float density = root.getResources().getDisplayMetrics().density;
        boolean rtl = root.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        int startOuter = start.getPaddingStart(), endOuter = end.getPaddingEnd();
        if (c.statusBarMargins) {
            int left = Math.max(Math.round(c.statusBarLeft * density), Reflect.getIntField(owner, "cutoutLeftPadding"));
            int right = Math.max(Math.round(c.statusBarRight * density), Reflect.getIntField(owner, "cutoutRightPadding"));
            int clockPaddingId = root.getResources().getIdentifier("status_bar_left_clock_starting_padding", "dimen", "com.android.systemui");
            int clockPadding = clockPaddingId == 0 ? 0 : root.getResources().getDimensionPixelSize(clockPaddingId);
            startOuter = Math.max(0, (rtl ? right : left) - clockPadding);
            endOuter = rtl ? left : right;
        }
        int middle = c.statusBarCenterEnabled ? Math.round(c.statusBarCenter * density) : 0;
        owners.put(owner, new int[]{end.getPaddingStart(), end.getPaddingStart() + middle - middle / 2, root.getLayoutDirection()});
        // Add to native inward padding, rather than shrinking the camera spacer.
        start.setPaddingRelative(startOuter, start.getPaddingTop(), start.getPaddingEnd() + middle / 2, start.getPaddingBottom());
        end.setPaddingRelative(end.getPaddingStart() + middle - middle / 2, end.getPaddingTop(), endOuter, end.getPaddingBottom());
        ShadeHook.resourceHit("status-bar-layout");
    }

    private static View child(View root, String name) {
        int id = root.getResources().getIdentifier(name, "id", "com.android.systemui");
        return id == 0 ? null : root.findViewById(id);
    }

    static void refresh() {
        for (Object owner : new ArrayList<>(owners.keySet())) {
            try { Reflect.callMethod(owner, "updateContentsPadding"); }
            catch (Throwable error) { ShadeHook.frameworkFailure("status-bar-layout-refresh", error); }
        }
    }
}
