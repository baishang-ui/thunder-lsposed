package io.github.baishangui.thunder;

import android.view.View;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Predicate;

/** Uses the host's direct text commit while an independent cloud owns its pixels. */
final class CloudTextHook {
    private static final String BASE="com.oplus.systemui.plugins.shared.template.component.";
    private static final String MODULE_FLAG="thunder.cloud-text-module-flag";
    private static final String MEDIA_ARGUMENT="thunder.cloud-text-media-argument";
    private static final Map<ClassLoader,Boolean> installed=new WeakHashMap<>();

    static synchronized void install(ClassLoader loader) {
        if(installed.put(loader,true)!=null)return;
        bind("content",()->Api102Runtime.findAndHookMethod(BASE+"module.i",loader,"p",BASE+"model.r",
            moduleCallback(CloudMorphHook::ownsContentSource)));
        bind("media",()->Api102Runtime.findAndHookMethod(BASE+"media.view.r",loader,"c",String.class,boolean.class,
            mediaCallback(CloudMorphHook::ownsContentSource)));
    }
    static HookCallback moduleCallback(Predicate<View> owned) {
        return new HookCallback() {
            @Override protected void beforeHookedMethod(Param p) {
                try {
                    if(!(Reflect.getObjectField(p.thisObject,"d") instanceof View source)||!owned.test(source))return;
                    // Native i.p checks module.a.a before creating its inner
                    // TextView-pair slide. False selects its own cancellation,
                    // direct commit and layout path; the method still runs.
                    boolean previous=Reflect.getBooleanField(p.thisObject,"a");
                    p.setObjectExtra(MODULE_FLAG,previous);
                    Reflect.setBooleanField(p.thisObject,"a",false);
                }catch(Throwable error){ShadeHook.frameworkFailure("cloud-text-content-before",error);}
            }
            @Override protected void afterHookedMethod(Param p) {
                // Restore even after a native failure or ownership change. Each
                // invocation has its own saved flag, so nested commits unwind.
                if(!(p.getObjectExtra(MODULE_FLAG) instanceof Boolean previous))return;
                try{Reflect.setBooleanField(p.thisObject,"a",previous);}
                catch(Throwable error){ShadeHook.frameworkFailure("cloud-text-content-after",error);}
            }
        };
    }
    static HookCallback mediaCallback(Predicate<View> owned) {
        return new HookCallback() {
            @Override protected void beforeHookedMethod(Param p) {
                try {
                    Object first=Reflect.getObjectField(p.thisObject,"b"),second=Reflect.getObjectField(p.thisObject,"c");
                    if(!(first instanceof View a&&owned.test(a))&&!(second instanceof View b&&owned.test(b)))return;
                    // MediaTextSwitcher.c(text,true) cancels its old 300ms fade,
                    // commits text to the current view and clears the other one.
                    // A same-value setText is left to the local text observer.
                    p.setObjectExtra(MEDIA_ARGUMENT,p.args[1]);p.args[1]=true;
                }catch(Throwable error){ShadeHook.frameworkFailure("cloud-text-media-before",error);}
            }
            @Override protected void afterHookedMethod(Param p) {
                Object previous=p.getObjectExtra(MEDIA_ARGUMENT);
                if(previous instanceof Boolean)p.args[1]=previous;
            }
        };
    }
    private static void bind(String name,Runnable action) {
        try{action.run();ShadeHook.hookRegistered("cloud-text-"+name);}
        catch(Throwable error){ShadeHook.frameworkFailure("cloud-text-"+name,error);}
    }
}
