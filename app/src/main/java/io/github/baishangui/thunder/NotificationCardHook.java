package io.github.baishangui.thunder;

import android.content.res.Resources;
import android.view.View;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Notification list and heads-up share views; always select the current role before drawing. */
final class NotificationCardHook {
    private static final String ROW="com.android.systemui.statusbar.notification.row.";
    private static final Map<View,Boolean> views=Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object,Boolean> stacks=Collections.synchronizedMap(new WeakHashMap<>());
    private static Class<?> rowClass;
    static void trackBlur(View view,Object blur) {
        Reflect.setAdditionalInstanceField(blur,"thunder.notification-view",new WeakReference<>(view));
        if(!Boolean.TRUE.equals(Reflect.getAdditionalInstanceField(blur,"thunder.notification-stack")))
            Reflect.setAdditionalInstanceField(view,"thunder.notification-config",new WeakReference<>(blur));
    }
    private static Float rowRoundness(Object blur,boolean top) {
        Object owner=Reflect.getAdditionalInstanceField(blur,"thunder.notification-view");
        Object view=owner instanceof WeakReference<?>?((WeakReference<?>)owner).get():null;
        View row=view instanceof View?rowForView((View)view):null;
        if(row==null)return null;
        return Math.max(0f,Math.min(1f,((Number)Reflect.callMethod(row,top?"getTopRoundness":"getBottomRoundness")).floatValue()));
    }
    private static View rowForView(View view) {
        for(int depth=0;view!=null&&depth<32;depth++) {
            if(rowClass!=null&&rowClass.isInstance(view))return view;
            Object parent=view.getParent();view=parent instanceof View?(View)parent:null;
        }
        return null;
    }
    static void track(View view,String role) {
        Object previousRole=Reflect.getAdditionalInstanceField(view,"thunder.role");
        if(role==null)Reflect.removeAdditionalInstanceField(view,"thunder.role");
        else Reflect.setAdditionalInstanceField(view,"thunder.role",role);
        views.putIfAbsent(view,false);
        Object tint=Reflect.getAdditionalInstanceField(view,"thunder.native-tint");
        if(tint!=null)Reflect.callMethod(view,"setTint",tint);
        view.invalidate();view.invalidateOutline();
        if(!java.util.Objects.equals(previousRole,role))AdaptiveTextHook.refresh();
    }
    static HookConfig.Area area(View view) {
        HookConfig c=ShadeHook.snapshot();if(!c.enabled)return null;
        HookConfig.Area cloudLock=CloudLockHook.areaForView(view,c);
        if(cloudLock!=null)return cloudLock;
        return "banner".equals(Reflect.getAdditionalInstanceField(view,"thunder.role"))?c.banner:c.notificationCard;
    }
    static HookConfig.Area containingArea(View view) {
        for(int depth=0;view!=null&&depth<32;depth++) {
            // Match by class name as templates may come from the plugin loader.
            // On this firmware mBackgroundNormal lives on ActivatableNotificationView
            // (the direct parent), so match any class in the ExpandableNotificationRow
            // hierarchy rather than only the exact leaf class.
            for(Class<?> type=view.getClass();type!=null;type=type.getSuperclass()) {
                String n=type.getName();
                if(n.equals(ROW+"ExpandableNotificationRow")||n.equals(ROW+"ActivatableNotificationView")) {
                    Object background=Reflect.getObjectField(view,"mBackgroundNormal");
                    return background instanceof View?area((View)background):null;
                }
            }
            Object parent=view.getParent();view=parent instanceof View?(View)parent:null;
        }
        return null;
    }
    static HookConfig.Area stackArea(Object blur) {
        if(!Boolean.TRUE.equals(Reflect.getAdditionalInstanceField(blur,"thunder.notification-stack")))return null;
        Object owner=Reflect.getAdditionalInstanceField(blur,"thunder.notification-view");
        Object view=owner instanceof WeakReference<?>?((WeakReference<?>)owner).get():null;
        return view instanceof View?area((View)view):null;
    }
    private static boolean cloudGeometry(Object blur) {
        HookConfig c=ShadeHook.snapshot();
        if(CloudLockHook.area(blur,c)!=null)return true;
        if(!Boolean.TRUE.equals(Reflect.getAdditionalInstanceField(blur,"thunder.notification-stack")))return false;
        Object ref=Reflect.getAdditionalInstanceField(blur,"thunder.notification-view");
        Object view=ref instanceof WeakReference<?>?((WeakReference<?>)ref).get():null;
        return view instanceof View&&CloudLockHook.areaForView((View)view,c)!=null;
    }
    static void overrideCorners(HookCallback.Param p,Object blur,HookConfig.Area area) {
        Float volumeRadius=VolumeHook.cornerRadius(blur,area);
        if(!area.cornerEnabled&&volumeRadius==null)return;
        float radius=volumeRadius!=null?volumeRadius:area.corner*Resources.getSystem().getDisplayMetrics().density;
        radius=QsCornerHook.radiusForBlur(blur,radius);
        Object role=Reflect.getAdditionalInstanceField(blur,"thunder.role");
        Object nativePath=Reflect.getObjectField(blur,"pathProvider");
        boolean qs="qs-card".equals(role)||"qs-progress".equals(role);
        Object shape=qs?QsCornerHook.blurProvider(blur,nativePath,radius):null;
        boolean smoothPanel=VolumeHook.smoothPanel(blur);
        boolean cloud=cloudGeometry(blur)&&CloudCornerGeometry.enabled();
        if(qs)radius=QsCornerHook.blurRadius(shape,radius);
        // All backends consume these fields, sometimes bypassing the public getters.
        float nativeRadius=((Number)Reflect.getObjectField(blur,"cornerRadius")).floatValue();
        // updateRadius feeds the already-customized row radius into the native edge fields.
        // Read the animation fraction at its source instead of scaling those pixels again.
        Float top=rowRoundness(blur,true),bottom=rowRoundness(blur,false);
        for(String field:new String[]{"leftTopCornerRadius","rightTopCornerRadius","leftBottomCornerRadius","rightBottomCornerRadius"}) {
            float edge=((Number)Reflect.getObjectField(blur,field)).floatValue();
            Float rowFraction=field.contains("Top")?top:bottom;
            float roundness=qs?1f:rowFraction!=null?rowFraction:nativeRadius>0?Math.max(0f,Math.min(1f,edge/nativeRadius)):1f;
            temporary(p,blur,field,radius*roundness);
        }
        temporary(p,blur,"cornerRadius",radius);
        float weight=cloud?CloudCornerGeometry.WEIGHT:smoothPanel?3f:qs?QsCornerHook.blurWeight(shape):0f;
        // Native volume geometry owns its deforming smooth shape and press outline.
        if(volumeRadius==null)temporary(p,blur,"pathProvider",shape);
        // Offset panel bounds require the backend's local smooth geometry. A view-space
        // path provider would apply the panel origin twice inside its render node.
        if(smoothPanel)temporary(p,blur,"pathProvider",null);
        if(qs||smoothPanel||cloud) {
            temporary(p,blur,"radiusWeight",weight);
            temporary(p,blur,"enableMotionSmoothCorner",true);
        }
        Object corners=Reflect.getObjectField(blur,"gradientStrokeCornerParam");
        if(corners!=null)temporary(p,blur,"gradientStrokeCornerParam",Reflect.newInstance(corners.getClass(),
            qs||smoothPanel||cloud?QsCornerHook.smoothCornerType(Reflect.callMethod(corners,"getType"),smoothPanel?blur:shape):Reflect.callMethod(corners,"getType"),
            radius,qs||smoothPanel||cloud?weight:Reflect.callMethod(corners,"getWeight")));
    }
    private static void temporary(HookCallback.Param p,Object target,String field,Object value) {
        p.setObjectExtra(field,new Object[]{Reflect.getObjectField(target,field)});
        Reflect.setObjectField(target,field,value);
    }
    static void install(ClassLoader loader) {
        bind("row-class",() -> rowClass=Reflect.findClass(ROW+"ExpandableNotificationRow",loader));
        // setRadius also updates the focus overlay, direct BaseDrawable stroke and
        // shadow edge. Those writes bypass ViewBlurProxy.applyBlurConfig, and the
        // upstream row getters may have been inlined by ART. Select the effective
        // notification, heads-up or cloud geometry here before native code fans out
        // to every edge layer; changing only BlurConfig misses these direct writes.
        bind("edge-radius",() -> Api102Runtime.findAndHookMethod(ROW+"NotificationBackgroundView",loader,"setRadius",
            float.class,float.class,boolean.class,new HookCallback() {
                @Override protected void beforeHookedMethod(Param p) {
                    View background=(View)p.thisObject;
                    View row=rowForView(background);
                    if(row==null)return;
                    views.put(row,true);
                    HookConfig.Area area=area(background);
                    if(area==null||!area.cornerEnabled)return;
                    float radius=area.corner*background.getResources().getDisplayMetrics().density;
                    for(int i=0;i<2;i++) {
                        float roundness=((Number)Reflect.callMethod(row,i==0?"getTopRoundness":"getBottomRoundness")).floatValue();
                        p.args[i]=radius*Math.max(0f,Math.min(1f,roundness));
                    }
                    if(CloudLockHook.areaForView(background,ShadeHook.snapshot())!=null) {
                        Object ref=Reflect.getAdditionalInstanceField(background,"thunder.notification-config");
                        Object blur=ref instanceof WeakReference<?>?((WeakReference<?>)ref).get():null;
                        if(blur!=null) {
                            Object nativeCorners=Reflect.getObjectField(blur,"gradientStrokeCornerParam");
                            p.setObjectExtra("cloud-direct-stroke",new Object[]{blur,nativeCorners});
                            if(nativeCorners!=null)Reflect.setObjectField(blur,"gradientStrokeCornerParam",CloudCornerGeometry.stroke(nativeCorners,(Float)p.args[0]));
                        }
                    }
                    ShadeHook.resourceHit("notification-edge-radius");
                    if(CloudLockHook.areaForView(background,ShadeHook.snapshot())!=null)
                        ShadeHook.resourceHit("cloud-notification-edge-radius");
                }
                @Override protected void afterHookedMethod(Param p) {
                    Object state=p.getObjectExtra("cloud-direct-stroke");
                    if(state instanceof Object[])Reflect.setObjectField(((Object[])state)[0],"gradientStrokeCornerParam",((Object[])state)[1]);
                }
            }));
        bind("cloud-clip",() -> Api102Runtime.findAndHookMethod(ROW+"ExpandableOutlineView",loader,"getRoundedRectPath",
            int.class,int.class,int.class,int.class,float.class,float.class,android.graphics.Path.class,new HookCallback() {
                @Override protected void beforeHookedMethod(Param p) {
                    if(!CloudCornerGeometry.enabled()||rowClass==null||!rowClass.isInstance(p.thisObject))return;
                    Object background=Reflect.getObjectField(p.thisObject,"mBackgroundNormal");
                    if(!(background instanceof View)||CloudLockHook.areaForView((View)background,ShadeHook.snapshot())==null)return;
                    android.graphics.RectF bounds=new android.graphics.RectF((Integer)p.args[0],(Integer)p.args[1],(Integer)p.args[2],(Integer)p.args[3]);
                    CloudCornerGeometry.path((android.graphics.Path)p.args[6],bounds,(Float)p.args[4],(Float)p.args[5],loader);
                    p.setResult(null);
                }
            }));
        bind("cloud-clip-deopt",() -> Api102Runtime.deoptimizeCallers(Reflect.findClass(ROW+"ExpandableOutlineView",loader),"getClipPath"));
        bind("edge-deopt",() -> Api102Runtime.deoptimizeCallers(Reflect.findClass(ROW+"ActivatableNotificationView",loader),
            "applyRoundnessAndInvalidate"));
        // Stacked notifications have separate configs and call PlatformBlurDrawable directly,
        // bypassing ViewBlurProxy's main config and all of its backend hooks.
        bind("stack-backgrounds",() -> Api102Runtime.hookAllConstructors(Reflect.findClass(
            "com.oplusos.systemui.common.blurability.platformblur.MultiPlatformBlurHelper",loader),new HookCallback() {
                @Override protected void afterHookedMethod(Param p) {
                    if(p.hasThrowable())return;
                    Object configs=Reflect.getObjectField(p.thisObject,"configMap");
                    if(!(configs instanceof Map<?,?>))return;
                    View view=(View)p.args[0];
                    for(Object blur:((Map<?,?>)configs).values()) {
                        Reflect.setAdditionalInstanceField(blur,"thunder.role","notification-card");
                        Reflect.setAdditionalInstanceField(blur,"thunder.notification-stack",true);
                        trackBlur(view,blur);
                    }
                    stacks.put(p.thisObject,true);
                }
            }));
        bind("stack-material",() -> Api102Runtime.findAndHookMethod(
            "com.oplusos.systemui.common.blurability.platformblur.PlatformBlurDrawable",loader,"applyBlurConfig",
            "com.oplusos.systemui.common.blurability.BlurConfig",float.class,new HookCallback() {
                @Override protected void beforeHookedMethod(Param p) {
                    if(Boolean.TRUE.equals(Reflect.getAdditionalInstanceField(p.args[0],"thunder.notification-stack")))
                        ShadeHook.overrideNativeConfig(p,p.args[0],"BlurTypePlatformStatic");
                }
                @Override protected void afterHookedMethod(Param p) { ShadeHook.restoreNativeConfig(p); }
            }));
        // Native row roundness remains a fraction: grouped edges and swipe animations retain their shape.
        for(String method:new String[]{"getTopCornerRadius","getBottomCornerRadius"}) bind(method,() ->
            Api102Runtime.findAndHookMethod(ROW+"ExpandableNotificationRow",loader,method,new HookCallback() {
                @Override protected void afterHookedMethod(Param p) {
                    if(p.hasThrowable())return;
                    views.put((View)p.thisObject,true);
                    View background=(View)Reflect.getObjectField(p.thisObject,"mBackgroundNormal");
                    HookConfig.Area a=background==null?null:area(background);
                    if(a==null||!a.cornerEnabled)return;
                    String fraction=method.startsWith("getTop")?"getTopRoundness":"getBottomRoundness";
                    float roundness=((Number)Reflect.callMethod(p.thisObject,fraction)).floatValue();
                    p.setResult(a.corner*background.getResources().getDisplayMetrics().density*Math.max(0f,Math.min(1f,roundness)));
                }
            }));
        bind("flat-tint",() -> Api102Runtime.findAndHookMethod(ROW+"NotificationBackgroundView",loader,"setTint",int.class,new HookCallback() {
            @Override protected void beforeHookedMethod(Param p) {
                View view=(View)p.thisObject;views.putIfAbsent(view,false);
                Reflect.setAdditionalInstanceField(view,"thunder.native-tint",p.args[0]);
                HookConfig.Area a=area(view);if(a!=null&&a.tint)p.args[0]=a.color;
            }
        }));
        bind("flat-background",() -> Api102Runtime.findAndHookMethod(ROW+"NotificationBackgroundView",loader,"setCustomBackground",
            android.graphics.drawable.Drawable.class,new HookCallback() {
                @Override protected void afterHookedMethod(Param p) {
                    if(p.hasThrowable())return;
                    View view=(View)p.thisObject;views.putIfAbsent(view,false);
                    HookConfig.Area a=area(view);
                    if(a!=null&&a.tint) {
                        Object nativeTint=Reflect.getAdditionalInstanceField(view,"thunder.native-tint");
                        if(nativeTint==null)nativeTint=Reflect.getIntField(view,"mTintColor");
                        Reflect.callMethod(view,"setTint",nativeTint);
                    }
                }
            }));
    }
    static void refresh() {
        ArrayList<View> current; synchronized(views){current=new ArrayList<>(views.keySet());}
        for(View view:current)try {
            Object tint=Reflect.getAdditionalInstanceField(view,"thunder.native-tint");
            if(tint!=null)Reflect.callMethod(view,"setTint",tint);
            if(Boolean.TRUE.equals(views.get(view)))Reflect.callMethod(view,"applyRoundnessAndInvalidate");
            view.invalidate();view.invalidateOutline();
        } catch(Throwable error){ShadeHook.frameworkFailure("notification-refresh",error);}
        ArrayList<Object> helpers;synchronized(stacks){helpers=new ArrayList<>(stacks.keySet());}
        for(Object helper:helpers)try { Reflect.callMethod(helper,"applyBlurConfig"); }
        catch(Throwable error){ShadeHook.frameworkFailure("notification-stack-refresh",error);}
    }
    private static void bind(String key,Runnable action) {
        try{action.run();ShadeHook.hookRegistered("notification-card-"+key);}
        catch(Throwable error){ShadeHook.frameworkFailure("notification-card-"+key,error);}
    }
}
