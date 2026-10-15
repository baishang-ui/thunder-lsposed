package io.github.baishangui.thunder;

import android.view.View;
import android.view.ViewOutlineProvider;
import android.content.Context;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ColorDrawable;
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
    private static final ThreadLocal<Boolean> geometryRead=ThreadLocal.withInitial(() -> false);
    private static final Map<View,Boolean> outlines=Collections.synchronizedMap(new WeakHashMap<>());
    static boolean readingNativeCorners(){return geometryRead.get();}
    private static final Map<View,SharedBackground> shared=Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View,CapsuleState> capsules=Collections.synchronizedMap(new WeakHashMap<>());
    private static final class SharedBackground {
        final Object factory;
        final java.lang.ref.WeakReference<Object> manager;
        SharedBackground(Object f,Object m){factory=f;manager=new java.lang.ref.WeakReference<>(m);}
    }
    private static final class CapsuleState {
        final Drawable background,foreground;
        final java.util.ArrayList<Runnable> restore=new java.util.ArrayList<>();
        final ViewOutlineProvider outline;
        final boolean clip;
        Object manager;
        HookConfig applied;
        CapsuleState(View view) {
            background=view.getBackground();foreground=view.getForeground();
            outline=view.getOutlineProvider();clip=view.getClipToOutline();
            remember(background,restore);remember(foreground,restore);
        }
    }
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
                @Override protected void beforeHookedMethod(Param p) {geometryRead.set(true);}
                @Override protected void afterHookedMethod(Param p) throws Throwable {
                    geometryRead.remove();
                    if(p.hasThrowable()||p.getResult()==null)return;
                    HookConfig c=ShadeHook.snapshot();if(!c.enabled||!c.cloud||!c.cloudCardArea.cornerEnabled)return;
                    Object context=Reflect.callMethod(Reflect.callMethod(companionField.get(null),"getInstance"),"getPluginCtx");
                    if(!(context instanceof Context))return;
                    float radius=c.cloudCardArea.corner*((Context)context).getResources().getDisplayMetrics().density;
                    p.setResult(cornerConfig(p.getResult(),radius));
                    ShadeHook.resourceHit("cloud-corner-config");
                }
            });
        });
        bind(loader,"cached-outline",() -> Api102Runtime.findAndHookMethod("com.oplus.systemui.plugins.common.util.j",loader,"getOutline",View.class,Outline.class,new HookCallback() {
            @Override protected void beforeHookedMethod(Param p) {
                if(!(p.args[0] instanceof View))return;
                View view=(View)p.args[0];
                if(!view.getClass().getName().startsWith(BASE+"card.")&&!shared.containsKey(view))return;
                outlines.put(view,true);
                Object saved=Reflect.getObjectField(p.thisObject,"b");
                Object nativeCorners=Reflect.getAdditionalInstanceField(saved,"thunder.native-corners");
                if(nativeCorners==null)nativeCorners=saved;
                HookConfig c=ShadeHook.snapshot();HookConfig.Area area=c.cloudCardArea;
                Object corners=c.enabled&&c.cloud&&area.cornerEnabled?
                    cornerConfig(nativeCorners,area.corner*view.getResources().getDisplayMetrics().density):nativeCorners;
                p.setObjectExtra("corners",saved);Reflect.setObjectField(p.thisObject,"b",corners);
            }
            @Override protected void afterHookedMethod(Param p) {
                Object corners=p.getObjectExtra("corners");if(corners!=null)Reflect.setObjectField(p.thisObject,"b",corners);
            }
        }));
        // C17 BackgroundUtils is also called by InstantContainer for lock-screen content,
        // including its deferred layout path. Apply after every background recreation.
        bind(loader,"shared-background",() -> Api102Runtime.findAndHookMethod("com.heytap.log.nx.obus.a",loader,"b",
            View.class,"com.oplus.view.ViewRootManager",new HookCallback() {
                @Override protected void afterHookedMethod(Param p) {
                    if(p.hasThrowable()||!(p.args[0] instanceof View))return;
                    View view=(View)p.args[0];Object manager=p.args[1];
                    shared.put(view,new SharedBackground(p.thisObject,manager));
                    HookConfig c=ShadeHook.snapshot();if(!c.enabled||!c.cloud)return;
                    applyCard(view,manager,c.cloudCardArea);
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
                    View view=(View)p.thisObject;
                    CapsuleState state=capsules.get(view);
                    if(state==null){state=new CapsuleState(view);capsules.put(view,state);}
                    if("onFinishInflate".equals(method)) {
                        HookConfig c=ShadeHook.snapshot();if(c.enabled&&c.cloud)applyCorners(view,c.cloudArea,false);
                    } else applyCapsule(view,state,ShadeHook.snapshot());
                }
            }));
        bind(loader,"capsule-detach",() -> Api102Runtime.findAndHookMethod(BASE+"capsule.ui.view.CapsuleView",loader,"onDetachedFromWindow",new HookCallback() {
            @Override protected void afterHookedMethod(Param p) {
                View view=(View)p.thisObject;CapsuleState state=capsules.remove(view);
                if(state!=null){view.setBackground(state.background);for(Runnable reset:state.restore)reset.run();
                    view.setOutlineProvider(state.outline);view.setClipToOutline(state.clip);}
            }
        }));
        // Firmware reapplies drawable bounds while the capsule changes width.
        bind(loader,"capsule-bounds",() -> Api102Runtime.findAndHookMethod(BASE+"capsule.ui.view.CapsuleView",loader,"setCapsuleDrawableWidth",int.class,new HookCallback() {
            @Override protected void afterHookedMethod(Param p) {
                HookConfig c=ShadeHook.snapshot();
                if(!p.hasThrowable()&&c.enabled&&c.cloud)applyCorners((View)p.thisObject,c.cloudArea,false);
            }
        }));
        for(String method:new String[]{"onAttachedToWindow","onConfigurationChanged"}) {
            HookCallback callback=new HookCallback() {
                @Override protected void afterHookedMethod(Param p) {
                    if(p.hasThrowable())return;
                    HookConfig c=ShadeHook.snapshot();if(!c.enabled||!c.cloud)return;
                    View view=(View)p.thisObject;
                    Object manager=Reflect.getObjectField(view,"c");
                    applyCard(view,manager,c.cloudCardArea);
                    ShadeHook.observed("cloud-card","corner="+(c.cloudCardArea.cornerEnabled?c.cloudCardArea.corner:"system")+"dp radius="+(c.cloudCardArea.blur?c.cloudCardArea.radius:"system")+" ARGB="+(c.cloudCardArea.tint?Integer.toHexString(c.cloudCardArea.color):"system"));
                }
            };
            bind(loader,"card-"+method,() -> {
                if("onConfigurationChanged".equals(method))Api102Runtime.findAndHookMethod(BASE+"card.ui.view.CardBackgroundView",loader,method,android.content.res.Configuration.class,callback);
                else Api102Runtime.findAndHookMethod(BASE+"card.ui.view.CardBackgroundView",loader,method,callback);
            });
        }
        bind(loader,"card-gradient",() -> Api102Runtime.findAndHookMethod(BASE+"util.d",loader,"draw",android.graphics.Canvas.class,new HookCallback() {
            @Override protected void beforeHookedMethod(Param p) {
                HookConfig c=ShadeHook.snapshot();
                if(c.enabled&&c.cloud&&c.cloudCardArea.tint)p.setResult(null);
            }
        }));
        bind(loader,"card-alpha",() -> Api102Runtime.findAndHookMethod(BASE+"card.ui.view.CardBackgroundView",loader,"setBlurRadiusFollowAlpha",float.class,new HookCallback() {
            @Override protected void afterHookedMethod(Param p) {
                HookConfig c=ShadeHook.snapshot();if(p.hasThrowable()||!c.enabled||!c.cloud||!c.cloudCardArea.blur)return;
                Object manager=Reflect.getObjectField(p.thisObject,"c");if(manager==null)return;
                float alpha=Math.max(0f,Math.min(1f,((Number)p.args[0]).floatValue()));
                Reflect.callMethod(manager,"setBlurRadius",(int)(c.cloudCardArea.radius*alpha));
                ShadeHook.resourceHit("cloud-card-blur");
            }
        }));
    }
    private static void applyCard(View view,Object manager,HookConfig.Area area) {
        boolean nativeBlur=manager!=null && contains(view.getBackground(),
            Reflect.callMethod(manager,"getBackgroundBlurDrawable"));
        if(nativeBlur) {
            applyMaterial(view,manager,area);
            if(area.blur)Reflect.callMethod(manager,"setBlurRadius",area.radius);
        }
        // The translucent gradient shader is an independent dark/color layer.
        // Custom native mixing supplies that color once; hide the extra shader entirely.
        tint(view.getBackground(),area,nativeBlur);
        applyCorners(view,area,!nativeBlur);
    }
    private static void applyCapsule(View view,CapsuleState state,HookConfig c) {
        if(state.applied==c)return;
        view.setBackground(state.background);
        for(Runnable reset:state.restore)reset.run();
        view.setOutlineProvider(state.outline);view.setClipToOutline(state.clip);
        if(!c.enabled||!c.cloud){state.manager=null;state.applied=c;view.invalidate();return;}
        HookConfig.Area area=c.cloudArea;
        if(area.blur) {
            state.manager=Reflect.newInstance(Reflect.findClass("com.oplus.view.ViewRootManager",view.getClass().getClassLoader()),view);
            applyMaterial(view,state.manager,area);
            Reflect.callMethod(state.manager,"setBlurRadius",area.radius);
            Object drawable=Reflect.callMethod(state.manager,"getBackgroundBlurDrawable");
            if(!(drawable instanceof Drawable))throw new IllegalStateException("Native capsule blur drawable unavailable");
            view.setBackground((Drawable)drawable);
            ShadeHook.resourceHit("cloud-capsule-blur");
        } else {state.manager=null;tint(view.getBackground(),area,false);}
        applyCorners(view,area,!area.blur);
        state.applied=c;view.invalidate();view.invalidateOutline();
    }
    private static boolean contains(Drawable drawable,Object target) {
        if(drawable==null||target==null)return false;
        if(drawable==target)return true;
        if(drawable instanceof LayerDrawable) {
            LayerDrawable layers=(LayerDrawable)drawable;
            for(int i=0;i<layers.getNumberOfLayers();i++)if(contains(layers.getDrawable(i),target))return true;
        } else if(drawable instanceof DrawableWrapper)return contains(((DrawableWrapper)drawable).getDrawable(),target);
        return false;
    }
    private static void tint(Drawable drawable,HookConfig.Area area,boolean nativeBlur) {
        if(drawable==null||!area.tint)return;
        java.util.ArrayList<Drawable> fills=new java.util.ArrayList<>();
        collectFills(drawable,fills);
        // LayerDrawable draws in index order. Tint only its last fill; clear every
        // other fill so nested backgrounds cannot multiply the chosen opacity.
        for(int i=0;i<fills.size();i++) {
            Drawable fill=fills.get(i).mutate();
            int color=!nativeBlur&&i==fills.size()-1?area.color:0;
            fill.clearColorFilter();
            if(fill instanceof GradientDrawable)((GradientDrawable)fill).setColor(color);
            else ((ColorDrawable)fill).setColor(color);
        }
    }
    private static void collectFills(Drawable drawable,java.util.ArrayList<Drawable> fills) {
        if(drawable==null)return;
        if(drawable.getClass().getName().equals(BASE+"util.d")) {
            // setAlpha alone leaves the native cached LinearGradient in place.
            Reflect.setObjectField(drawable,"g",null);drawable.invalidateSelf();
        } else if(drawable instanceof LayerDrawable) {
            LayerDrawable layers=(LayerDrawable)drawable;
            for(int i=0;i<layers.getNumberOfLayers();i++)collectFills(layers.getDrawable(i),fills);
        } else if(drawable instanceof DrawableWrapper)collectFills(((DrawableWrapper)drawable).getDrawable(),fills);
        else if(drawable instanceof ColorDrawable)fills.add(drawable);
        else if(drawable instanceof GradientDrawable) {
            GradientDrawable gradient=(GradientDrawable)drawable;
            // Stroke-only drawables must not acquire a solid rectangular fill.
            if(gradient.getColor()!=null||gradient.getColors()!=null)fills.add(drawable);
        }
    }
    private static void remember(Drawable drawable,java.util.ArrayList<Runnable> restore) {
        if(drawable!=null) {
            android.graphics.ColorFilter filter=drawable.getColorFilter();
            restore.add(() -> drawable.setColorFilter(filter));
        }
        if(drawable instanceof ColorDrawable) {
            int color=((ColorDrawable)drawable).getColor();
            restore.add(() -> ((ColorDrawable)drawable).setColor(color));
        } else if(drawable instanceof GradientDrawable) {
            GradientDrawable gradient=(GradientDrawable)drawable;
            float radius=gradient.getCornerRadius();float[] radii=gradient.getCornerRadii();
            if(radii!=null)radii=radii.clone();final float[] corners=radii;
            android.content.res.ColorStateList color=gradient.getColor();int[] colors=gradient.getColors();
            restore.add(() -> {if(corners==null)gradient.setCornerRadius(radius);else gradient.setCornerRadii(corners);
                if(colors!=null)gradient.setColors(colors);else if(color!=null)gradient.setColor(color);});
        } else if(drawable instanceof LayerDrawable) {
            LayerDrawable layers=(LayerDrawable)drawable;
            for(int i=0;i<layers.getNumberOfLayers();i++)remember(layers.getDrawable(i),restore);
        } else if(drawable instanceof DrawableWrapper)remember(((DrawableWrapper)drawable).getDrawable(),restore);
    }
    static void refresh() {
        java.util.ArrayList<View> parents;
        synchronized(outlines){parents=new java.util.ArrayList<>(outlines.keySet());}
        for(View view:parents){view.invalidateOutline();view.invalidate();}
        java.util.ArrayList<Map.Entry<View,SharedBackground>> backgrounds;
        synchronized(shared){backgrounds=new java.util.ArrayList<>(shared.entrySet());}
        for(Map.Entry<View,SharedBackground> entry:backgrounds)try {
            Object factory=entry.getValue().factory;
            if(factory!=null)Reflect.callMethod(factory,"b",entry.getKey(),entry.getValue().manager.get());
        } catch(Throwable error){ShadeHook.frameworkFailure("cloud-refresh-background",error);}
        java.util.ArrayList<Map.Entry<View,CapsuleState>> current;
        synchronized(capsules){current=new java.util.ArrayList<>(capsules.entrySet());}
        for(Map.Entry<View,CapsuleState> entry:current)try {applyCapsule(entry.getKey(),entry.getValue(),ShadeHook.snapshot());}
        catch(Throwable error){ShadeHook.frameworkFailure("cloud-refresh-capsule",error);}
    }
    private static void applyMaterial(View view,Object manager,HookConfig.Area area) {
        if(area.cornerEnabled || view.getClass().getName().endsWith("CapsuleView")) {
            int dp=area.cornerEnabled?area.corner:20;
            Reflect.callMethod(manager,"setCornerRadius",dp*view.getResources().getDisplayMetrics().density);
        }
        if(area.tint) {
            int color=area.color;
            Object param=Reflect.newInstance(Reflect.findClass("com.oplus.graphics.OplusBlurParam",view.getClass().getClassLoader()));
            Reflect.callMethod(param,"setBlurType",2);
            try {
                Object geometry=Reflect.findClass(BASE+"util.c",view.getClass().getClassLoader()).getDeclaredMethod("f").invoke(null);
                int type=((Number)Reflect.callMethod(geometry,"d")).intValue();
                Reflect.callMethod(param,"setSmoothCornerType",type==2?1:0);
                Reflect.callMethod(param,"setSmoothCornerWeight",((Number)Reflect.callMethod(geometry,"f")).floatValue());
            } catch(ReflectiveOperationException error){throw new IllegalStateException("Cloud smooth corners unavailable",error);}
            float[] top={(color>>16&255)/255f,(color>>8&255)/255f,(color&255)/255f,(color>>>24)/255f};
            Reflect.callMethod(param,"setMaterialParams",color>>>24==0?0:1,new float[4],top);
            Reflect.callMethod(manager,"setBlurParams",param);
            ShadeHook.resourceHit("cloud-native-tint");
        }
    }
    private static void applyCorners(View view,HookConfig.Area area,boolean fallbackOutline) {
        if(!area.cornerEnabled)return;
        float radius=area.corner*view.getResources().getDisplayMetrics().density;
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
        Object replacement=Reflect.newInstance(corners.getClass(),Reflect.callMethod(corners,"d"),radius,Reflect.callMethod(corners,"f"));
        Object nativeCorners=Reflect.getAdditionalInstanceField(corners,"thunder.native-corners");
        Reflect.setAdditionalInstanceField(replacement,"thunder.native-corners",nativeCorners==null?corners:nativeCorners);
        return replacement;
    }
    private static void bind(ClassLoader loader,String key,Runnable action) {
        try {action.run();ShadeHook.hookRegistered("cloud-"+key);}catch(Throwable error){ShadeHook.frameworkFailure("cloud-"+key,error);}
    }
}
