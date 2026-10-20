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
    private static final ThreadLocal<Integer> geometryRead=ThreadLocal.withInitial(() -> 0);
    private static final Map<View,Boolean> outlines=Collections.synchronizedMap(new WeakHashMap<>());
    static boolean readingNativeCorners(){return geometryRead.get()>0;}
    static void beginNativeCorners(){geometryRead.set(geometryRead.get()+1);}
    static void endNativeCorners(){int depth=geometryRead.get()-1;if(depth<=0)geometryRead.remove();else geometryRead.set(depth);}
    private record NativeOutline(ViewOutlineProvider provider,boolean clip) { }
    private static final Map<View,NativeOutline> fallbackOutlines=Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View,SharedBackground> shared=Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View,CapsuleState> capsules=Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Drawable,java.lang.ref.WeakReference<View>> gradients=Collections.synchronizedMap(new WeakHashMap<>());
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
        CloudMorphHook.install(loader);
        CloudSdkHook.watch(loader);
        CloudTemplateHook.install(loader);
        bind(loader,"corner-config",() -> {
            final java.lang.reflect.Field companionField;
            try {companionField=Reflect.findClass(BASE+"plugin.ContextHandler",loader).getField("Companion");}
            catch(ReflectiveOperationException error){throw new IllegalStateException("Plugin context unavailable",error);}
            // Parent outlines, animated bounds and the gradient all consume this Triple.
            // Smooth2 reads a different system dimension, so resource overrides alone miss it.
            Api102Runtime.findAndHookMethod(BASE+"util.c",loader,"f",new HookCallback() {
                @Override protected void beforeHookedMethod(Param p) {beginNativeCorners();}
                @Override protected void afterHookedMethod(Param p) throws Throwable {
                    endNativeCorners();
                    if(p.hasThrowable()||p.getResult()==null||readingNativeCorners())return;
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
                if(CloudMorphHook.ownsOutline(view.getOutlineProvider()))return;
                boolean page=CloudTemplateHook.pageSurface(view);
                if(CloudLockHook.inNativeCapsule(view)) {
                    if(page)CloudTemplateHook.pageRadius(view); // Restore a reused expanded foreground on its first mini-card outline.
                    return;
                }
                if(!cardSurface(view)&&!page)return;
                outlines.put(view,true);
                Object saved=Reflect.getObjectField(p.thisObject,"b");
                Object nativeCorners=nativeCorners(saved);
                HookConfig c=ShadeHook.snapshot();HookConfig.Area area=c.cloudCardArea;
                Object scaleValue=Reflect.getAdditionalInstanceField(p.thisObject,"thunder.cloud-corner-scale");
                float scale=scaleValue instanceof Number?((Number)scaleValue).floatValue():1f;
                Float radius=page?CloudTemplateHook.pageRadius(view):
                    c.enabled&&c.cloud&&area.cornerEnabled?area.corner*view.getResources().getDisplayMetrics().density:null;
                Object corners=radius!=null?cornerConfig(nativeCorners,radius*scale):nativeCorners;
                p.setObjectExtra("corners",saved);Reflect.setObjectField(p.thisObject,"b",corners);
            }
            @Override protected void afterHookedMethod(Param p) {
                Object corners=p.getObjectExtra("corners");if(corners!=null)Reflect.setObjectField(p.thisObject,"b",corners);
            }
        }));
        // k.c creates a NEW Triple and divides radius by animation scale. Preserve
        // both its native baseline and scale; overwriting getOutline with raw dp
        // breaks animated clipping and loses the baseline when custom corners stop.
        bind(loader,"outline-scale",() -> Api102Runtime.findAndHookMethod("com.oplus.systemui.plugins.common.util.k",loader,"c",
            View.class,android.graphics.Rect.class,float.class,"kotlin.Triple",int.class,new HookCallback() {
                @Override protected void afterHookedMethod(Param p) {
                    if(p.hasThrowable()||!(p.args[0] instanceof View))return;
                    View view=(View)p.args[0];Object provider=view.getOutlineProvider();
                    if(provider==null||!provider.getClass().getName().equals("com.oplus.systemui.plugins.common.util.j"))return;
                    float scale=((Number)p.args[2]).floatValue();
                    if((((Number)p.args[4]).intValue()&8)!=0||scale<=0f)scale=1f;
                    Object nativeConfig=nativeCorners(p.args[3]);
                    Object baseline=Reflect.newInstance(nativeConfig.getClass(),Reflect.callMethod(nativeConfig,"d"),
                        ((Number)Reflect.callMethod(nativeConfig,"e")).floatValue()/scale,Reflect.callMethod(nativeConfig,"f"));
                    Reflect.setObjectField(provider,"b",baseline);
                    Reflect.setAdditionalInstanceField(provider,"thunder.cloud-corner-scale",1f/scale);
                }
            }));
        bind(loader,"press-corners",() -> Api102Runtime.findAndHookMethod(BASE+"card.ui.view.k",loader,"b",
            android.graphics.Canvas.class,int.class,int.class,new HookCallback() {
                @Override protected void beforeHookedMethod(Param p) {
                    Object saved=Reflect.getObjectField(p.thisObject,"b");
                    if(saved==null)return;
                    p.setObjectExtra("corners",saved);
                    HookConfig c=ShadeHook.snapshot();Object value=nativeCorners(saved);
                    if(c.enabled&&c.cloud&&c.cloudCardArea.cornerEnabled)value=cornerConfig(value,
                        c.cloudCardArea.corner*android.content.res.Resources.getSystem().getDisplayMetrics().density);
                    Reflect.setObjectField(p.thisObject,"b",value);
                    ShadeHook.resourceHit("cloud-press-corners");
                }
                @Override protected void afterHookedMethod(Param p) {
                    Object saved=p.getObjectExtra("corners");if(saved!=null)Reflect.setObjectField(p.thisObject,"b",saved);
                }
            }));
        // C17 BackgroundUtils is also called by InstantContainer for lock-screen content,
        // including its deferred layout path. Apply after every background recreation.
        bind(loader,"shared-background",() -> Api102Runtime.findAndHookMethod("com.heytap.log.nx.obus.a",loader,"b",
            View.class,"com.oplus.view.ViewRootManager",new HookCallback() {
                @Override protected void beforeHookedMethod(Param p) {
                    if(p.args[0] instanceof View) {
                        View view=(View)p.args[0];restoreFallbackOutline(view);
                        if(CloudLockHook.inNativeCapsule(view)){beginNativeCorners();p.setObjectExtra("native-capsule",true);}
                    }
                }
                @Override protected void afterHookedMethod(Param p) {
                    if(Boolean.TRUE.equals(p.getObjectExtra("native-capsule")))endNativeCorners();
                    if(p.hasThrowable()||!(p.args[0] instanceof View))return;
                    View view=(View)p.args[0];Object manager=p.args[1];
                    shared.put(view,new SharedBackground(p.thisObject,manager));
                    HookConfig c=ShadeHook.snapshot();if(!c.enabled||!c.cloud)return;
                    applyCard(view,manager,cardArea(view,c));
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
                    applyCard(view,manager,cardArea(view,c));
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
                java.lang.ref.WeakReference<View> ref=gradients.get(p.thisObject);
                View owner=ref==null?null:ref.get();
                HookConfig.Area area=owner==null?c.cloudCardArea:cardArea(owner,c);
                if(c.enabled&&c.cloud&&area.tint)p.setResult(null);
            }
        }));
        bind(loader,"card-alpha",() -> Api102Runtime.findAndHookMethod(BASE+"card.ui.view.CardBackgroundView",loader,"setBlurRadiusFollowAlpha",float.class,new HookCallback() {
            @Override protected void afterHookedMethod(Param p) {
                HookConfig c=ShadeHook.snapshot();if(p.hasThrowable()||!c.enabled||!c.cloud)return;
                HookConfig.Area area=cardArea((View)p.thisObject,c);if(!area.blur)return;
                Object manager=Reflect.getObjectField(p.thisObject,"c");if(manager==null)return;
                float alpha=Math.max(0f,Math.min(1f,((Number)p.args[0]).floatValue()));
                Reflect.callMethod(manager,"setBlurRadius",(int)(area.radius*alpha));
                ShadeHook.resourceHit("cloud-card-blur");
            }
        }));
        for(String type:new String[]{"com.oplus.systemui.plugins.common.util.k",BASE+"card.ui.view.k"})
            bind(loader,"geometry-deopt-"+type,() -> Api102Runtime.deoptimizeCallers(Reflect.findClass(type,loader),type.endsWith("view.k")?"b":"c"));
    }
    private static Object nativeCorners(Object corners) {
        Object original=Reflect.getAdditionalInstanceField(corners,"thunder.native-corners");
        return original==null?corners:original;
    }
    static boolean inCard(View view) {
        for(int depth=0;view!=null&&depth<32;depth++) {
            if(shared.containsKey(view)||view.getClass().getName().startsWith(BASE+"card.ui.view."))return true;
            Object parent=view.getParent();view=parent instanceof View?(View)parent:null;
        }
        return false;
    }
    private static boolean cardSurface(View view) {
        String name=view.getClass().getName();
        return shared.containsKey(view)||name.startsWith(BASE+"card.")||
            (name.equals("com.oplus.systemui.plugins.shared.template.component.media.view.MediaBackGroundView")&&inCard(view));
    }
    private static void restoreFallbackOutline(View view) {
        NativeOutline saved=fallbackOutlines.remove(view);
        if(saved!=null){view.setOutlineProvider(saved.provider);view.setClipToOutline(saved.clip);view.invalidateOutline();}
    }
    private static void applyCard(View view,Object manager,HookConfig.Area area) {
        trackGradients(view.getBackground(),view);
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
    private static HookConfig.Area cardArea(View view,HookConfig c) {
        HookConfig.Area card=c.cloudCardArea;
        boolean capsule=CloudLockHook.inNativeCapsule(view);
        if(!CloudLockHook.pluginLock(view,c))return capsule?
            new HookConfig.Area(card.blur,card.radius,card.tint,card.color,false,card.corner):card;
        HookConfig.Area lock=c.cloudLockArea;
        ShadeHook.resourceHit("cloud-plugin-lock-material");
        return new HookConfig.Area(lock.blur,lock.radius,lock.tint,lock.color,!capsule&&card.cornerEnabled,card.corner);
    }
    private static void trackGradients(Drawable drawable,View view) {
        if(drawable==null)return;
        if(drawable.getClass().getName().equals(BASE+"util.d"))gradients.put(drawable,new java.lang.ref.WeakReference<>(view));
        else if(drawable instanceof LayerDrawable) {
            LayerDrawable layers=(LayerDrawable)drawable;
            for(int i=0;i<layers.getNumberOfLayers();i++)trackGradients(layers.getDrawable(i),view);
        } else if(drawable instanceof DrawableWrapper)trackGradients(((DrawableWrapper)drawable).getDrawable(),view);
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
    static void remember(Drawable drawable,java.util.ArrayList<Runnable> restore) {
        if(drawable!=null) {
            android.graphics.ColorFilter filter=drawable.getColorFilter();
            restore.add(() -> drawable.setColorFilter(filter));
        }
        if(drawable instanceof ColorDrawable) {
            int color=((ColorDrawable)drawable).getColor();
            restore.add(() -> ((ColorDrawable)drawable).setColor(color));
        } else if(drawable instanceof GradientDrawable) {
            GradientDrawable gradient=(GradientDrawable)drawable;
            CloudCornerGeometry.Gradient nativeSmooth=new CloudCornerGeometry.Gradient(gradient);
            restore.add(nativeSmooth::restore);
            float radius=gradient.getCornerRadius();float[] radii=gradient.getCornerRadii();
            if(radii!=null)radii=radii.clone();final float[] corners=radii;
            android.content.res.ColorStateList color=gradient.getColor();int[] colors=gradient.getColors();
            restore.add(() -> {if(corners==null)gradient.setCornerRadius(radius);else gradient.setCornerRadii(corners);
                if(colors!=null)gradient.setColors(colors);else if(color!=null)gradient.setColor(color);});
        } else if(drawable instanceof LayerDrawable) {
            LayerDrawable layers=(LayerDrawable)drawable;
            for(int i=0;i<layers.getNumberOfLayers();i++)remember(layers.getDrawable(i),restore);
        } else if(drawable instanceof DrawableWrapper)remember(((DrawableWrapper)drawable).getDrawable(),restore);
        else if(drawable!=null&&drawable.getClass().getName().equals(BASE+"util.d")) {
            Object corners=Reflect.getObjectField(drawable,"d");
            restore.add(() -> Reflect.setObjectField(drawable,"d",corners));
        }
    }
    static void refresh() {
        CloudMorphHook.refresh();
        java.util.ArrayList<View> parents;
        synchronized(outlines){parents=new java.util.ArrayList<>(outlines.keySet());}
        for(View view:parents){view.invalidateOutline();view.invalidate();}
        java.util.ArrayList<Map.Entry<View,SharedBackground>> backgrounds;
        synchronized(shared){backgrounds=new java.util.ArrayList<>(shared.entrySet());}
        for(Map.Entry<View,SharedBackground> entry:backgrounds)try {
            restoreFallbackOutline(entry.getKey());
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
            fallbackOutlines.putIfAbsent(view,new NativeOutline(view.getOutlineProvider(),view.getClipToOutline()));
            // Use the plugin's own smooth outline. A plain setRoundRect clips a
            // different curve from its G2 gradient, template and press layers.
            ViewOutlineProvider original=view.getOutlineProvider();
            boolean nativeProvider=original!=null&&original.getClass().getName().equals("com.oplus.systemui.plugins.common.util.j");
            ClassLoader loader=nativeProvider?original.getClass().getClassLoader():view.getContext().getClassLoader();
            Object corners;
            Object rect=null;float scale=1f;
            if(nativeProvider) {
                rect=Reflect.getObjectField(original,"a");
                corners=nativeCorners(Reflect.getObjectField(original,"b"));
                Object savedScale=Reflect.getAdditionalInstanceField(original,"thunder.cloud-corner-scale");
                if(savedScale instanceof Number)scale=((Number)savedScale).floatValue();
            } else try {
                corners=nativeCorners(Reflect.findClass(BASE+"util.c",loader).getDeclaredMethod("f").invoke(null));
            } catch(ReflectiveOperationException error){throw new IllegalStateException("Cloud outline geometry unavailable",error);}
            ViewOutlineProvider outline=(ViewOutlineProvider)Reflect.newInstance(
                Reflect.findClass("com.oplus.systemui.plugins.common.util.j",loader),rect,cornerConfig(corners,radius*scale));
            Reflect.setAdditionalInstanceField(outline,"thunder.cloud-corner-scale",scale);
            view.setOutlineProvider(outline);
            view.setClipToOutline(true);
            view.invalidateOutline();
        }
        view.invalidate();
    }
    static void round(Drawable drawable,float radius) {
        if(drawable==null)return;
        drawable=drawable.mutate();
        if(drawable instanceof GradientDrawable) {
            new CloudCornerGeometry.Gradient((GradientDrawable)drawable).apply();
            ((GradientDrawable)drawable).setCornerRadius(radius);
        }
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
        Object replacement=Reflect.newInstance(corners.getClass(),2,radius,CloudCornerGeometry.WEIGHT);
        Object nativeCorners=Reflect.getAdditionalInstanceField(corners,"thunder.native-corners");
        Reflect.setAdditionalInstanceField(replacement,"thunder.native-corners",nativeCorners==null?corners:nativeCorners);
        return replacement;
    }
    private static void bind(ClassLoader loader,String key,Runnable action) {
        try {action.run();ShadeHook.hookRegistered("cloud-"+key);}catch(Throwable error){ShadeHook.frameworkFailure("cloud-"+key,error);}
    }
}
