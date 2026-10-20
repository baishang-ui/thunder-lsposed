package io.github.baishangui.thunder;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Host-side C17 lock-screen material and native application-color overlays. */
final class CloudLockHook {
    private static final String LOCK="com.oplus.systemui.notification.lockscreen.capsule.";
    private static final String META="com.oplus.posteffect.drawable.MetaBallBlurDrawable";
    private static final ThreadLocal<Object> colorTarget=new ThreadLocal<>();
    private static final ThreadLocal<Object> writingTarget=new ThreadLocal<>();
    private static final ThreadLocal<View> drawing=new ThreadLocal<>();
    private static final Map<View,Boolean> views=Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View,Boolean> capsuleGeometry=Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View,Boolean> strokeViews=Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object,Mask> masks=Collections.synchronizedMap(new WeakHashMap<>());
    private record Mask(Object orientation,int[] colors) { }
    private record StrokeRadius(Object layer,float radius) {
        void restore(){Reflect.callMethod(layer,"setCornerRadius",radius);}
    }

    static void track(View view,Object proxy,String type,Object manager) {
        Object blur=Reflect.callMethod(proxy,"getBlurConfig");
        if(!"CAPSULE".equals(type)&&!"CUSTOM".equals(type)) {
            Object previous=referent(blur,"thunder.cloud-view");
            if(previous instanceof View&&referent(previous,"thunder.cloud-config")==blur)
                Reflect.removeAdditionalInstanceField(previous,"thunder.cloud-config");
            for(String key:new String[]{"thunder.cloud-type","thunder.cloud-view","thunder.cloud-manager"})
                Reflect.removeAdditionalInstanceField(blur,key);
            return;
        }
        Reflect.setAdditionalInstanceField(blur,"thunder.cloud-type",type);
        Reflect.setAdditionalInstanceField(blur,"thunder.cloud-view",new WeakReference<>(view));
        Reflect.setAdditionalInstanceField(blur,"thunder.cloud-manager",new WeakReference<>(manager));
        if("CAPSULE".equals(type))Reflect.setAdditionalInstanceField(blur,"thunder.role","cloud-lock");
        Reflect.setAdditionalInstanceField(view,"thunder.cloud-config",new WeakReference<>(blur));
        views.put(view,true);
    }
    private static Object referent(Object owner,String key) {
        Object ref=Reflect.getAdditionalInstanceField(owner,key);
        return ref instanceof WeakReference<?>?((WeakReference<?>)ref).get():null;
    }
    static boolean flowCapsule(View view) {
        for(int i=0;view!=null&&i<8;i++) {
            String name=view.getClass().getName();
            if(name.equals(LOCK+"CapsuleItemView")) {
                Object entry=Reflect.callMethod(view,"getEntry");
                return entry!=null&&entry.getClass().getName().equals("com.oplus.systemui.notification.lockscreen.seedling.CapsuleSeedingData");
            }
            if(name.equals(LOCK+"NotificationCapsuleContainer")) {
                Object base=Reflect.callMethod(view,"getBaseCard");
                return base instanceof View&&flowCapsule((View)base);
            }
            Object parent=view.getParent();view=parent instanceof View?(View)parent:null;
        }
        return false;
    }
    static HookConfig.Area area(Object blur,HookConfig c) {
        if(!c.enabled)return null;
        Object type=Reflect.getAdditionalInstanceField(blur,"thunder.cloud-type");
        if("CUSTOM".equals(type)) {
            // A notification-hosted flow card keeps notification material, but
            // its shape always belongs to the flow card, including when disabled.
            HookConfig.Area material="banner".equals(Reflect.getAdditionalInstanceField(blur,"thunder.role"))?c.banner:c.notificationCard;
            Object manager=referent(blur,"thunder.cloud-manager");
            if(c.cloud&&c.cloudLockStyle!=0&&manager!=null&&Boolean.TRUE.equals(Reflect.callMethod(manager,"isKeyguardMaterialColorState")))
                material=c.cloudLockArea;
            return cardCorners(material,c);
        } else if("CAPSULE".equals(type)) {
            Object view=referent(blur,"thunder.cloud-view");
            if(c.cloud&&view instanceof View&&flowCapsule((View)view))
                return cardCorners(c.cloudLockStyle!=0?c.cloudLockArea:new HookConfig.Area(false,0,false,0,false,0),c);
        }
        return null;
    }
    private static HookConfig.Area cardCorners(HookConfig.Area material,HookConfig c) {
        HookConfig.Area corners=c.cloudCardArea;
        return new HookConfig.Area(material.blur,material.radius,material.tint,material.color,c.cloud&&corners.cornerEnabled,corners.corner);
    }
    static HookConfig.Area areaForView(View view,HookConfig c) {
        Object blur=referent(view,"thunder.cloud-config");
        return blur==null?null:area(blur,c);
    }
    static boolean pluginLock(View view,HookConfig c) {
        if(!c.enabled||!c.cloud||c.cloudLockStyle==0)return false;
        Object service=Reflect.callMethod(view.getContext(),"getSystemService","keyguard");
        return service!=null&&Boolean.TRUE.equals(Reflect.callMethod(service,"isKeyguardLocked"));
    }
    private static void prepareCapsule(Object proxy,View view,HookConfig c,boolean flow) {
        if(proxy==null)return;
        // The smooth proxy is pooled and its host is a transient container view.
        // Bind it to the capsule actually drawing it, rather than the pool host.
        Object blur=Reflect.callMethod(proxy,"getBlurConfig");
        if(referent(blur,"thunder.cloud-view")!=view)track(view,proxy,"CAPSULE",null);
        if(Reflect.getAdditionalInstanceField(proxy,"thunder.cloud-snapshot")==c&&
                Boolean.valueOf(flow).equals(Reflect.getAdditionalInstanceField(proxy,"thunder.cloud-flow")))return;
        Reflect.callMethod(proxy,"applyBlurConfig");
        // Cache on the pooled resource, so another helper's write invalidates it.
        Reflect.setAdditionalInstanceField(proxy,"thunder.cloud-snapshot",c);
        Reflect.setAdditionalInstanceField(proxy,"thunder.cloud-flow",flow);
    }
    static void install(ClassLoader loader) {
        // This animated highlight is independent of the capsule background. C17's
        // updateViewSize resets its layers to height / 2, even after roundness changed.
        bind("capsule-animated-edge",() -> Api102Runtime.findAndHookMethod(LOCK+"stroke.StrokeView",loader,"onDraw",Canvas.class,new HookCallback() {
            @Override protected void beforeHookedMethod(Param p) {
                View view=(View)p.thisObject;strokeViews.put(view,true);
                HookConfig c=ShadeHook.snapshot();
                if(!c.enabled||!c.cloud||!c.cloudCardArea.cornerEnabled||!flowCapsule(view))return;
                float height=((Number)Reflect.getObjectField(view,"desiredDrawHeight")).floatValue();
                float radius=Math.min(c.cloudCardArea.corner*view.getResources().getDisplayMetrics().density,Math.max(0f,height/2f));
                ArrayList<StrokeRadius> saved=new ArrayList<>();p.setObjectExtra("cloud-edge-radii",saved);
                for(Object layer:(Iterable<?>)Reflect.getObjectField(view,"layers")) {
                    saved.add(new StrokeRadius(layer,((Number)Reflect.callMethod(layer,"getCornerRadius")).floatValue()));
                    Reflect.callMethod(layer,"setCornerRadius",radius);
                }
                ShadeHook.resourceHit("cloud-lock-animated-edge");
            }
            @Override protected void afterHookedMethod(Param p) {
                Object saved=p.getObjectExtra("cloud-edge-radii");
                if(saved instanceof Iterable<?>)for(Object radius:(Iterable<?>)saved)((StrokeRadius)radius).restore();
            }
        }));
        // The lock capsule clips content and draws its stroke from getActualRadius,
        // independently of BlurConfig. Update that native source as well.
        for(String method:new String[]{"getActualRadius","getMaxRadius"})bind("capsule-"+method,() ->
            Api102Runtime.findAndHookMethod(LOCK+"CapsuleItemView",loader,method,new HookCallback() {
                @Override protected void afterHookedMethod(Param p) {
                    if(p.hasThrowable())return;
                    View view=(View)p.thisObject;capsuleGeometry.put(view,true);
                    HookConfig c=ShadeHook.snapshot();
                    if(!c.enabled||!c.cloud||!c.cloudCardArea.cornerEnabled||!flowCapsule(view))return;
                    Object location=Reflect.getObjectField(view,"location");
                    android.graphics.Rect clip=(android.graphics.Rect)Reflect.callMethod(location,"getClipRect");
                    float radius=c.cloudCardArea.corner*view.getResources().getDisplayMetrics().density;
                    p.setResult(Math.min(radius,Math.max(0f,clip.height()/2f)));
                    ShadeHook.resourceHit("cloud-lock-corners");
                }
            }));
        // onClipInfoChange bypasses getActualRadius and writes CapsuleConfig's
        // default directly. Repair only the radius after native bounds, scaling
        // and smoothing have been applied, using the provider's own cache updater.
        bind("capsule-clip-corners",() -> Api102Runtime.findAndHookMethod(LOCK+"CapsuleItemView",loader,"onClipInfoChange",
            LOCK+"CapsuleViewState$ScrollClipInfo",new HookCallback() {
                @Override protected void afterHookedMethod(Param p) {
                    if(p.hasThrowable())return;
                    View view=(View)p.thisObject;capsuleGeometry.put(view,true);
                    HookConfig c=ShadeHook.snapshot();
                    if(!c.enabled||!c.cloud||!c.cloudCardArea.cornerEnabled||!flowCapsule(view))return;
                    Object location=Reflect.getObjectField(view,"location");
                    android.graphics.Rect clip=(android.graphics.Rect)Reflect.callMethod(location,"getClipRect");
                    float radius=Math.min(c.cloudCardArea.corner*view.getResources().getDisplayMetrics().density,Math.max(0f,clip.height()/2f));
                    Reflect.callMethod(Reflect.getObjectField(view,"pathProvider"),"updateMetaData",radius,null,null);
                    ShadeHook.resourceHit("cloud-lock-clip-corners");
                }
            }));
        bind("capsule-corners-deopt",() -> Api102Runtime.deoptimizeCallers(Reflect.findClass(LOCK+"CapsuleItemView",loader),
            "applyRoundnessAndInvalidate","getActualRadius","getTopCornerRadius","getBottomCornerRadius"));
        bind("fallback-color",() -> Api102Runtime.findAndHookMethod(LOCK+"CapsuleBackgroundHelper",loader,"getBackgroundColor",new HookCallback() {
            @Override protected void afterHookedMethod(Param p) {
                HookConfig c=ShadeHook.snapshot();View view=drawing.get();
                if(!p.hasThrowable()&&view!=null&&c.enabled&&c.cloud&&c.cloudLockStyle!=0&&c.cloudLockArea.tint&&flowCapsule(view))
                    p.setResult(c.cloudLockArea.color);
            }
        }));
        bind("capsule-draw",() -> Api102Runtime.findAndHookMethod(LOCK+"CapsuleBackgroundHelper",loader,"drawCapsuleBg",View.class,Canvas.class,new HookCallback() {
            @Override protected void beforeHookedMethod(Param p) {
                p.setObjectExtra("cloud-drawing",drawing.get());drawing.set((View)p.args[0]);
                View view=(View)p.args[0];views.put(view,true);
                HookConfig c=ShadeHook.snapshot();boolean flow=flowCapsule(view);
                Object proxy=Reflect.getObjectField(p.thisObject,"viewBlurProxy");
                Object shared=Reflect.getObjectField(p.thisObject,"smoothSharedViewBlurProxy");
                prepareCapsule(proxy,view,c,flow);
                prepareCapsule(shared,view,c,flow);
            }
            @Override protected void afterHookedMethod(Param p) {
                Object previous=p.getObjectExtra("cloud-drawing");
                if(previous instanceof View)drawing.set((View)previous);else drawing.remove();
            }
        }));
        // Scope the setters to the app-color producer. AOD and metaball animation
        // also use setMetaColor, and must never be mistaken for application tint.
        bind("app-color-producer",() -> Api102Runtime.findAndHookMethod(
            "com.oplus.systemui.notification.row.NotificationBackgroundCustomRowHandler",loader,"updateDrawableMetaColor",
            Context.class,"com.android.systemui.plugins.custom.CustomRowEntry",META,new HookCallback() {
                @Override protected void beforeHookedMethod(Param p) {
                    p.setObjectExtra("cloud-color-target",colorTarget.get());colorTarget.set(p.args[2]);
                }
                @Override protected void afterHookedMethod(Param p) {
                    Object previous=p.getObjectExtra("cloud-color-target");
                    if(previous==null)colorTarget.remove();else colorTarget.set(previous);
                }
            }));
        bind("app-solid-color",() -> Api102Runtime.findAndHookMethod(META,loader,"setMetaColor",int.class,colorSetter(false)));
        bind("app-gradient-color",() -> Api102Runtime.findAndHookMethod(META,loader,"setGradientColors",
            GradientDrawable.Orientation.class,int[].class,colorSetter(true)));
        bind("deopt-app-color",() -> Api102Runtime.deoptimizeCallers(Reflect.findClass(
            "com.oplus.systemui.notification.row.NotificationBackgroundCustomRowHandler",loader),"updateDrawableMetaColor"));
        bind("deopt-capsule-background",() -> Api102Runtime.deoptimizeCallers(Reflect.findClass(
            LOCK+"CapsuleBackgroundHelper",loader),"drawCapsuleBg"));
        bind("deopt-capsule-view",() -> Api102Runtime.deoptimizeCallers(Reflect.findClass(
            LOCK+"CapsuleBackgroundView",loader),"onDraw"));
    }
    private static HookCallback colorSetter(boolean gradient) {
        return new HookCallback() {
            @Override protected void beforeHookedMethod(Param p) {
                if(colorTarget.get()!=p.thisObject) {
                    // An external animation/AOD write takes ownership until the
                    // application-color producer writes again. Do not replay over it.
                    if(writingTarget.get()!=p.thisObject)masks.remove(p.thisObject);
                    return;
                }
                int[] colors=gradient?((int[])p.args[1]).clone():new int[]{(Integer)p.args[0]};
                if(colors.length==0||colors.length>4)return; // Native setter ignores these commands.
                Mask mask=new Mask(gradient?p.args[0]:null,colors);
                p.setObjectExtra("cloud-mask",mask);
                // C17 setMetaColor delegates to setGradientColors. Let its nested
                // setter consume the adjusted value without scaling or saving it again.
                p.setObjectExtra("cloud-setter-target",colorTarget.get());colorTarget.remove();
                p.setObjectExtra("cloud-writing-target",writingTarget.get());writingTarget.set(p.thisObject);
                int[] adjusted=adjust(colors,ShadeHook.snapshot());
                if(gradient)p.args[1]=adjusted;else p.args[0]=adjusted[0];
            }
            @Override protected void afterHookedMethod(Param p) {
                Object target=p.getObjectExtra("cloud-setter-target");
                if(target!=null) {
                    colorTarget.set(target);
                    Object previous=p.getObjectExtra("cloud-writing-target");
                    if(previous==null)writingTarget.remove();else writingTarget.set(previous);
                }
                Object mask=p.getObjectExtra("cloud-mask");
                if(!p.hasThrowable()&&mask instanceof Mask) {
                    masks.put(p.thisObject,(Mask)mask);ShadeHook.resourceHit("cloud-app-color");
                }
            }
        };
    }
    static int[] adjust(int[] colors,HookConfig c) {
        int[] result=colors.clone();
        if(!c.enabled||!c.cloud)return result;
        int strength=!c.cloudAppColor?0:c.cloudAppColorStrengthEnabled?c.cloudAppColorStrength:100;
        for(int i=0;i<result.length;i++) {
            int alpha=Math.min(255,((result[i]>>>24)*strength+50)/100);
            result[i]=(result[i]&0xffffff)|(alpha<<24);
        }
        return result;
    }
    static void refresh() {
        ArrayList<Map.Entry<Object,Mask>> current;
        synchronized(masks){current=new ArrayList<>(masks.entrySet());}
        for(Map.Entry<Object,Mask> entry:current) {
            Object previous=writingTarget.get();writingTarget.set(entry.getKey());
            try {
                Mask mask=entry.getValue();int[] colors=adjust(mask.colors,ShadeHook.snapshot());
                if(mask.orientation==null)Reflect.callMethod(entry.getKey(),"setMetaColor",colors[0]);
                else Reflect.callMethod(entry.getKey(),"setGradientColors",mask.orientation,colors);
            } catch(Throwable error){ShadeHook.frameworkFailure("cloud-lock-mask-refresh",error);}
            finally {if(previous==null)writingTarget.remove();else writingTarget.set(previous);}
        }
        ArrayList<View> backgrounds;synchronized(views){backgrounds=new ArrayList<>(views.keySet());}
        for(View view:backgrounds){view.invalidate();view.invalidateOutline();}
        ArrayList<View> edges;synchronized(strokeViews){edges=new ArrayList<>(strokeViews.keySet());}
        for(View view:edges)view.invalidate();
        ArrayList<View> capsules;synchronized(capsuleGeometry){capsules=new ArrayList<>(capsuleGeometry.keySet());}
        for(View view:capsules)try {Reflect.callMethod(view,"applyRoundnessAndInvalidate");}
        catch(Throwable error){ShadeHook.frameworkFailure("cloud-lock-corners-refresh",error);}
    }
    private static void bind(String key,Runnable action) {
        try{action.run();ShadeHook.hookRegistered("cloud-lock-"+key);}
        catch(Throwable error){ShadeHook.frameworkFailure("cloud-lock-"+key,error);}
    }
}
