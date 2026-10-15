package io.github.baishangui.thunder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Extend C17's native hide list without writing Settings.Secure or changing icon state. */
final class StatusBarIconHook {
    // Accessed on SystemUI's main thread; weak keys do not retain recreated controllers.
    private static final Map<Object, Set<String>> additions = new WeakHashMap<>();

    static void install(ClassLoader loader) {
        Class<?> controller = Reflect.findClass(
            "com.android.systemui.statusbar.phone.ui.StatusBarIconControllerImpl", loader);
        Api102Runtime.findAndHookMethod(controller, "onTuningChanged", String.class, String.class,
            new HookCallback() {
                @Override protected void beforeHookedMethod(HookCallback.Param p) {
                    // The original method clears and rebuilds its list from system preferences.
                    if ("icon_blacklist".equals(p.args[0])) additions.remove(p.thisObject);
                }
                @Override protected void afterHookedMethod(HookCallback.Param p) {
                    if (!p.hasThrowable() && "icon_blacklist".equals(p.args[0])) apply(p.thisObject);
                }
            });
        Api102Runtime.hookAllConstructors(controller, new HookCallback() {
            @Override protected void afterHookedMethod(HookCallback.Param p) {
                if (!p.hasThrowable()) apply(p.thisObject);
            }
        });
    }

    @SuppressWarnings("unchecked")
    static void apply(Object controller) {
        Set<String> nativeList = (Set<String>) Reflect.getObjectField(controller, "mIconHideList");
        Set<String> before = new HashSet<>(nativeList);
        Set<String> owned = additions.getOrDefault(controller, Collections.emptySet());
        nativeList.removeAll(owned);
        HookConfig config = ShadeHook.snapshot();
        Set<String> selected = StatusBarIcons.hidden(config.enabled ? config.hiddenStatusIcons : 0);
        Set<String> added = new HashSet<>(selected);
        added.removeAll(nativeList); // Preserve icons already hidden by the user's system settings.
        nativeList.addAll(selected);
        additions.put(controller, added);
        if (!before.equals(nativeList)) Reflect.callMethod(controller, "refreshStatusBarIconList");
        ShadeHook.statusIconsObserved(String.join(",", selected));
    }

    static void refresh() {
        IosStatusBarHook.refresh();
        for (Object controller : new ArrayList<>(additions.keySet())) {
            try { apply(controller); }
            catch (Throwable error) { ShadeHook.frameworkFailure("status-icons-refresh", error); }
        }
    }
}
