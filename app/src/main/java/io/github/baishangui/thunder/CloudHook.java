package io.github.baishangui.thunder;

import android.view.View;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** C17 plugin hooks are registered in the plugin's actual ClassLoader before it creates views. */
final class CloudHook {
    private static final String BASE="com.oplus.systemui.plugins.seedling.";
    private static final Map<ClassLoader,Boolean> installed=Collections.synchronizedMap(new WeakHashMap<>());
    static void watch(ClassLoader host) {
        bind(host,"loader-create", () -> Api102Runtime.findAndHookMethod("com.android.systemui.shared.plugins.PluginInstance$PluginFactory",host,
            "createClassLoader",new HookCallback() {
                @Override protected void afterHookedMethod(Param p) {
                    if(p.hasThrowable()||!(p.getResult() instanceof ClassLoader))return;
                    if(isCloudFactory(p.thisObject))install((ClassLoader)p.getResult());
                }
            }));
        // Firmware can reuse getCacheClassLoader(), bypassing createClassLoader().
        // PluginContextWrapper exposes that exact loader before plugin views inflate.
        bind(host,"loader-context", () -> Api102Runtime.findAndHookMethod("com.android.systemui.shared.plugins.PluginInstance$PluginFactory",host,
            "createPluginContext",new HookCallback() {
                @Override protected void afterHookedMethod(Param p) {
                    if(p.hasThrowable()||!(p.getResult() instanceof Context))return;
                    if(isCloudFactory(p.thisObject))install(((Context)p.getResult()).getClassLoader());
                }
            }));
        Api102Runtime.deoptimizeCallers(Reflect.findClass("com.android.systemui.shared.plugins.PluginInstance$PluginFactory",host),"createPlugin","createPluginContext");
    }
    private static boolean isCloudFactory(Object factory) {
        Object app=Reflect.getObjectField(factory,"pluginAppInfo");
        return app!=null&&"com.oplus.systemui.plugins".equals(Reflect.getObjectField(app,"packageName"));
    }
    static synchronized void install(ClassLoader loader) {
        if(installed.containsKey(loader))return;
        // Do not mark an unrelated/host loader as installed after failed resolution.
        Reflect.findClass(BASE+"capsule.ui.view.CapsuleContainer",loader);
        installed.put(loader,true);
        bind(loader,"capsule-height",() -> Api102Runtime.findAndHookMethod(BASE+"capsule.ui.view.CapsuleContainer",loader,"getDefaultMaxHeight",new HookCallback() {
            @Override protected void afterHookedMethod(Param p) {
                HookConfig c=ShadeHook.snapshot();if(p.hasThrowable()||!c.enabled||!c.cloud||!c.cloudHeightEnabled)return;
                p.setResult(Math.round(c.cloudHeight*((View)p.thisObject).getResources().getDisplayMetrics().density));
                ShadeHook.observed("cloud-height",c.cloudHeight+"dp");
            }
        }));
        for(String method:new String[]{"onFinishInflate","onAttachedToWindow"})bind(loader,"capsule-"+method,() ->
            Api102Runtime.findAndHookMethod(BASE+"capsule.ui.view.CapsuleView",loader,method,new HookCallback() {
                @Override protected void afterHookedMethod(Param p) {
                    if(p.hasThrowable())return;
                    HookConfig c=ShadeHook.snapshot();if(!c.enabled||!c.cloud)return;
                    View view=(View)p.thisObject;
                    if(c.cloudCornerEnabled) {
                        float radius=c.cloudCorner*view.getResources().getDisplayMetrics().density;
                        round(view.getBackground(),radius);round(view.getForeground(),radius);
                    }
                    if("onAttachedToWindow".equals(method)&&c.cloudArea.blur) {
                        Object manager=Reflect.newInstance(Reflect.findClass("com.oplus.view.ViewRootManager",loader),view);
                        applyMaterial(view,manager,c);
                        Reflect.callMethod(manager,"setBlurRadius",c.cloudArea.radius);
                        Object background=Reflect.callMethod(manager,"getBackgroundBlurDrawable");
                        if(!(background instanceof Drawable))throw new IllegalStateException("Native capsule blur drawable unavailable");
                        view.setBackground((Drawable)background);
                        ShadeHook.resourceHit("cloud-capsule-blur");
                    }
                }
            }));
        for(String method:new String[]{"onAttachedToWindow","onConfigurationChanged"}) {
            HookCallback callback=new HookCallback() {
                @Override protected void afterHookedMethod(Param p) {
                    if(p.hasThrowable())return;
                    HookConfig c=ShadeHook.snapshot();if(!c.enabled||!c.cloud)return;
                    View view=(View)p.thisObject;
                    Object manager=Reflect.getObjectField(view,"c");
                    if(manager!=null) {
                        applyMaterial(view,manager,c);
                        if(c.cloudArea.blur)Reflect.callMethod(manager,"setBlurRadius",c.cloudArea.radius);
                    } else if(c.cloudCornerEnabled)round(view.getBackground(),c.cloudCorner*view.getResources().getDisplayMetrics().density);
                    ShadeHook.observed("cloud-card","corner="+(c.cloudCornerEnabled?c.cloudCorner:"system")+"dp radius="+(c.cloudArea.blur?c.cloudArea.radius:"system")+" ARGB="+(c.cloudArea.tint?Integer.toHexString(c.cloudArea.color):"system"));
                }
            };
            bind(loader,"card-"+method,() -> {
                if("onConfigurationChanged".equals(method))Api102Runtime.findAndHookMethod(BASE+"card.ui.view.CardBackgroundView",loader,method,android.content.res.Configuration.class,callback);
                else Api102Runtime.findAndHookMethod(BASE+"card.ui.view.CardBackgroundView",loader,method,callback);
            });
        }
        bind(loader,"card-alpha",() -> Api102Runtime.findAndHookMethod(BASE+"card.ui.view.CardBackgroundView",loader,"setBlurRadiusFollowAlpha",float.class,new HookCallback() {
            @Override protected void afterHookedMethod(Param p) {
                HookConfig c=ShadeHook.snapshot();if(p.hasThrowable()||!c.enabled||!c.cloud||!c.cloudArea.blur)return;
                Object manager=Reflect.getObjectField(p.thisObject,"c");if(manager==null)return;
                float alpha=Math.max(0f,Math.min(1f,((Number)p.args[0]).floatValue()));
                Reflect.callMethod(manager,"setBlurRadius",(int)(c.cloudArea.radius*alpha));
                ShadeHook.resourceHit("cloud-card-blur");
            }
        }));
    }
    private static void applyMaterial(View view,Object manager,HookConfig c) {
        if(c.cloudCornerEnabled || view.getClass().getName().endsWith("CapsuleView")) {
            int dp=c.cloudCornerEnabled?c.cloudCorner:20;
            Reflect.callMethod(manager,"setCornerRadius",dp*view.getResources().getDisplayMetrics().density);
        }
        if(c.cloudArea.tint) {
            int color=c.cloudArea.color;
            Object param=Reflect.newInstance(Reflect.findClass("com.oplus.graphics.OplusBlurParam",view.getClass().getClassLoader()));
            Reflect.callMethod(param,"setBlurType",2);
            float[] top={(color>>16&255)/255f,(color>>8&255)/255f,(color&255)/255f,(color>>>24)/255f};
            Reflect.callMethod(param,"setMaterialParams",color>>>24==0?0:4,new float[4],top);
            Reflect.callMethod(manager,"setBlurParams",param);
            ShadeHook.resourceHit("cloud-native-tint");
        }
    }
    private static void round(Drawable drawable,float radius) {
        if(drawable instanceof GradientDrawable)((GradientDrawable)drawable.mutate()).setCornerRadius(radius);
    }
    private static void bind(ClassLoader loader,String key,Runnable action) {
        try {action.run();ShadeHook.hookRegistered("cloud-"+key);}catch(Throwable error){ShadeHook.frameworkFailure("cloud-"+key,error);}
    }
}
