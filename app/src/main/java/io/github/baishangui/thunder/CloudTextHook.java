package io.github.baishangui.thunder;

import android.view.View;
import android.widget.TextView;
import android.animation.Animator;
import android.animation.ValueAnimator;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Predicate;

/** Uses the host's direct text commit while an independent cloud owns its pixels. */
final class CloudTextHook {
    private static final String BASE="com.oplus.systemui.plugins.shared.template.component.";
    private static final String MODULE_FLAG="thunder.cloud-text-module-flag";
    private static final String MEDIA_ARGUMENT="thunder.cloud-text-media-argument";
    private static final Map<ClassLoader,Boolean> installed=new WeakHashMap<>();

    private record Pending(WeakReference<Object> owner,WeakReference<Object> value,boolean media) { }
    private static final Map<View,Pending> pending=new WeakHashMap<>();

    /** Finish an inner slide started before the source was acquired by the independent cloud. */
    static void adopt(View root) {
        if(root==null)return;
        for(var item:new ArrayList<>(pending.entrySet())) {
            View source=item.getKey();boolean inside=false;
            for(Object node=source;node instanceof View view;node=view.getParent())if(view==root){inside=true;break;}
            if(!inside)continue;
            Pending update=item.getValue();Object owner=update.owner.get(),value=update.value.get();
            pending.remove(source);if(owner==null||value==null)continue;
            try {
                if(update.media)Reflect.callMethod(owner,"c",value,true);
                else {
                    boolean previous=Reflect.getBooleanField(owner,"a");
                    try{Reflect.setBooleanField(owner,"a",false);Reflect.callMethod(owner,"p",value);}
                    finally{Reflect.setBooleanField(owner,"a",previous);}
                }
            }catch(Throwable error){ShadeHook.frameworkFailure("cloud-text-adopt",error);}
        }
    }
    static synchronized void install(ClassLoader loader) {
        if(installed.put(loader,true)!=null)return;
        bind("content",()->Api102Runtime.findAndHookMethod(BASE+"module.i",loader,"p",BASE+"model.r",
            moduleCallback(CloudTextHook::owned)));
        bind("media",()->Api102Runtime.findAndHookMethod(BASE+"media.view.r",loader,"c",String.class,boolean.class,
            mediaCallback(CloudTextHook::owned)));
    }
    private static boolean owned(View source){return CloudTextRenderer.owns(source)||CloudMorphHook.ownsContentSource(source);}
    static HookCallback moduleCallback(Predicate<View> owned) {
        return new HookCallback() {
            @Override protected void beforeHookedMethod(Param p) {
                try {
                    if(!(Reflect.getObjectField(p.thisObject,"d") instanceof View source))return;
                    if(!owned.test(source)) {
                        if(Reflect.getBooleanField(p.thisObject,"a"))pending.put(source,new Pending(new WeakReference<>(p.thisObject),new WeakReference<>(p.args[0]),false));
                        return;
                    }
                    pending.remove(source);
                    // Native i.p checks module.a.a before creating its inner
                    // TextView-pair slide. False selects its own cancellation,
                    // direct commit and layout path; the method still runs.
                    boolean previous=Reflect.getBooleanField(p.thisObject,"a");
                    p.setObjectExtra(MODULE_FLAG,previous);
                    Reflect.setBooleanField(p.thisObject,"a",false);
                }catch(Throwable error){ShadeHook.frameworkFailure("cloud-text-content-before",error);}
            }
            @Override protected void afterHookedMethod(Param p) {
                if(p.hasThrowable())try{pending.remove(Reflect.getObjectField(p.thisObject,"d"));}catch(Throwable ignored){}
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
                    if(!(first instanceof View a&&owned.test(a))&&!(second instanceof View b&&owned.test(b))) {
                        if(first instanceof View source&&!Boolean.TRUE.equals(p.args[1]))
                            pending.put(source,new Pending(new WeakReference<>(p.thisObject),new WeakReference<>(p.args[0]),true));
                        return;
                    }
                    pending.remove(first);
                    // Both ordinary progress delivery and native force/rebind
                    // commits repeat the current lyric. Even c(text,true) calls
                    // setText again, restarting marquee and producing a flash.
                    if(Reflect.getObjectField(p.thisObject,"f") instanceof TextView current&&
                        current.getVisibility()==View.VISIBLE&&java.util.Objects.equals(current.getText().toString(),p.args[0])) {
                        cancelMediaFade(p.thisObject);
                        View other=current==first?(View)second:(View)first;
                        current.setAlpha(1f);other.setAlpha(0f);other.setVisibility(View.INVISIBLE);
                        p.setResult(null);return;
                    }
                    // MediaTextSwitcher.c(text,true) cancels its old 300ms fade,
                    // commits text to the current view and clears the other one.
                    // Explicit native direct commits keep their cleanup semantics.
                    p.setObjectExtra(MEDIA_ARGUMENT,p.args[1]);p.args[1]=true;
                }catch(Throwable error){ShadeHook.frameworkFailure("cloud-text-media-before",error);}
            }
            @Override protected void afterHookedMethod(Param p) {
                if(p.hasThrowable())try{pending.remove(Reflect.getObjectField(p.thisObject,"b"));}catch(Throwable ignored){}
                Object previous=p.getObjectExtra(MEDIA_ARGUMENT);
                if(previous instanceof Boolean) {
                    p.args[1]=previous;
                    // The direct branch omits the fade listener that normally
                    // restarts native marquee for the newly committed line.
                    if(!p.hasThrowable())try{Reflect.callMethod(p.thisObject,"a");}
                    catch(Throwable error){ShadeHook.frameworkFailure("cloud-text-media-marquee",error);}
                }
            }
        };
    }
    private static void cancelMediaFade(Object owner) {
        Object animator=Reflect.getObjectField(owner,"d");
        if(animator instanceof Animator fade) {
            if(fade instanceof ValueAnimator value)value.removeAllUpdateListeners();
            fade.removeAllListeners();fade.cancel();
            Reflect.setObjectField(owner,"d",null);
        }
        Reflect.setObjectField(owner,"e",null);
    }
    private static void bind(String name,Runnable action) {
        try{action.run();ShadeHook.hookRegistered("cloud-text-"+name);}
        catch(Throwable error){ShadeHook.frameworkFailure("cloud-text-"+name,error);}
    }
}
