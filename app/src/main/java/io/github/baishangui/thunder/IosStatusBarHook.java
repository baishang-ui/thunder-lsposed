package io.github.baishangui.thunder;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Resource substitution only. SystemUI owns every view, state update and draw call. */
final class IosStatusBarHook {
    private static final String MODULE = "io.github.baishangui.thunder";
    private static final Set<Integer> failed = ConcurrentHashMap.newKeySet();
    private static volatile Context moduleContext;
    private static boolean enabled;

    static void install(ClassLoader loader) {
        // Resource consumers cache drawables and Compose vectors. Freeze the style until restart.
        enabled = IosStatusIcons.active(ShadeHook.snapshot());
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
        return (enabled ? "style=ios;renderer=resources;signals=system;battery=system" : "style=system")
            + (enabled != IosStatusIcons.active(ShadeHook.snapshot()) ? ";restart-required=true" : "");
    }
    private IosStatusBarHook() { }
}
