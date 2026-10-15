package io.github.baishangui.thunder;

import android.view.View;
import android.view.ViewOutlineProvider;
import android.content.Context;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.DrawableWrapper;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
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
        bind(loader,"corner-config",() -> {
            final java.lang.reflect.Field companionField;
            try {companionField=Reflect.findClass(BASE+"plugin.ContextHandler",loader).getField("Companion");}
            catch(ReflectiveOperationException error){throw new IllegalStateException("Plugin context unavailable",error);}
            // Parent outlines, animated bounds and the gradient all consume this Triple.
            // Smooth2 reads a different system dimension, so resource overrides alone miss it.
            Api102Runtime.findAndHookMethod(BASE+"util.c",loader,"f",new HookCallback() {
                @Override protected void afterHookedMethod(Param p) throws Throwable {
                    if(p.hasThrowable()||p.getResult()==null)return;
                    HookConfig c=ShadeHook.snapshot();if(!c.enabled||!c.cloud||!c.cloudCornerEnabled)return;
                    Object context=Reflect.callMethod(Reflect.callMethod(companionField.get(null),"getInstance"),"getPluginCtx");
                    if(!(context instanceof Context))return;
                    float radius=c.cloudCorner*((Context)context).getResources().getDisplayMetrics().density;
                    p.setResult(cornerConfig(p.getResult(),radius));
                    ShadeHook.resourceHit("cloud-corner-config");
                }
            });
        });
        // C17 BackgroundUtils is also called by InstantContainer for lock-screen content,
        // including its deferred layout path. Apply after every background recreation.
        bind(loader,"shared-background",() -> Api102Runtime.findAndHookMethod("com.heytap.log.nx.obus.a",loader,"b",
            View.class,"com.oplus.view.ViewRootManager",new HookCallback() {
                @Override protected void afterHookedMethod(Param p) {
                    if(p.hasThrowable()||!(p.args[0] instanceof View))return;
                    HookConfig c=ShadeHook.snapshot();if(!c.enabled||!c.cloud)return;
                    View view=(View)p.args[0];Object manager=p.args[1];
                    if(manager!=null) {
                        applyMaterial(view,manager,c);
                        if(c.cloudArea.blur)Reflect.callMethod(manager,"setBlurRadius",c.cloudArea.radius);
                    }
                    applyCorners(view,c,manager==null);
                    ShadeHook.resourceHit("cloud-shared-background");
                }
            }));
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
                    applyCorners(view,c,false);
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
                    }
                    applyCorners(view,c,manager==null);
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
    private static void applyCorners(View view,HookConfig c,boolean fallbackOutline) {
        if(!c.cloudCornerEnabled)return;
        float radius=c.cloudCorner*view.getResources().getDisplayMetrics().density;
        round(view.getBackground(),radius);round(view.getForeground(),radius);
        if(fallbackOutline) {
            // The non-blur path installs an outline with its own cached system radius.
            view.setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View target,Outline outline) {
                    outline.setRoundRect(0,0,target.getWidth(),target.getHeight(),radius);
                }
            });
            view.setClipToOutline(true);
            view.invalidateOutline();
        }
        view.invalidate();
    }
    private static void round(Drawable drawable,float radius) {
        if(drawable==null)return;
        drawable=drawable.mutate();
        if(drawable instanceof GradientDrawable)((GradientDrawable)drawable).setCornerRadius(radius);
        else if(drawable instanceof LayerDrawable) {
            LayerDrawable layers=(LayerDrawable)drawable;
            for(int i=0;i<layers.getNumberOfLayers();i++)round(layers.getDrawable(i),radius);
        } else if(drawable instanceof DrawableWrapper)round(((DrawableWrapper)drawable).getDrawable(),radius);
        else if(drawable.getClass().getName().equals(BASE+"util.d")) {
            // C17's translucent gradient draws its own Path from a cached Kotlin Triple:
            // (corner type, radius in px, smooth weight). Preserve type and weight.
            Object corners=Reflect.getObjectField(drawable,"d");
            Reflect.setObjectField(drawable,"d",cornerConfig(corners,radius));
            drawable.invalidateSelf();
        }
    }
    private static Object cornerConfig(Object corners,float radius) {
        return Reflect.newInstance(corners.getClass(),Reflect.callMethod(corners,"d"),radius,Reflect.callMethod(corners,"f"));
    }
    private static void bind(ClassLoader loader,String key,Runnable action) {
        try {action.run();ShadeHook.hookRegistered("cloud-"+key);}catch(Throwable error){ShadeHook.frameworkFailure("cloud-"+key,error);}
    }
}
