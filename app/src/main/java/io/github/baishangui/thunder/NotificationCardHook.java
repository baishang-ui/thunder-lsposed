package io.github.baishangui.thunder;

import android.content.res.Resources;
import android.view.View;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Notification list and heads-up share views; always select the current role before drawing. */
final class NotificationCardHook {
    private static final String ROW="com.android.systemui.statusbar.notification.row.";
    private static final Map<View,Boolean> views=Collections.synchronizedMap(new WeakHashMap<>());
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
        return "banner".equals(Reflect.getAdditionalInstanceField(view,"thunder.role"))?c.banner:c.notificationCard;
    }
    static void overrideCorners(HookCallback.Param p,Object blur,HookConfig.Area area) {
        if(!area.cornerEnabled)return;
        float radius=area.corner*Resources.getSystem().getDisplayMetrics().density;
        radius=QsCornerHook.radiusForBlur(blur,radius);
        Object role=Reflect.getAdditionalInstanceField(blur,"thunder.role");
        Object nativePath=Reflect.getObjectField(blur,"pathProvider");
        boolean qs="qs-card".equals(role)||"qs-progress".equals(role);
        Object shape=qs?QsCornerHook.blurProvider(blur,nativePath,radius):null;
        if(qs)radius=QsCornerHook.blurRadius(shape,radius);
        // All backends consume these fields, sometimes bypassing the public getters.
        float nativeRadius=((Number)Reflect.getObjectField(blur,"cornerRadius")).floatValue();
        for(String field:new String[]{"leftTopCornerRadius","rightTopCornerRadius","leftBottomCornerRadius","rightBottomCornerRadius"}) {
            float edge=((Number)Reflect.getObjectField(blur,field)).floatValue();
            float roundness=qs?1f:nativeRadius>0?Math.max(0f,Math.min(1f,edge/nativeRadius)):1f;
            temporary(p,blur,field,radius*roundness);
        }
        temporary(p,blur,"cornerRadius",radius);
        float weight=qs?QsCornerHook.blurWeight(shape):0f;
        temporary(p,blur,"pathProvider",shape);
        if(qs) {
            temporary(p,blur,"radiusWeight",weight);
            temporary(p,blur,"enableMotionSmoothCorner",true);
        }
        Object corners=Reflect.getObjectField(blur,"gradientStrokeCornerParam");
        if(corners!=null)temporary(p,blur,"gradientStrokeCornerParam",Reflect.newInstance(corners.getClass(),
            qs?QsCornerHook.smoothCornerType(Reflect.callMethod(corners,"getType")):Reflect.callMethod(corners,"getType"),
            radius,qs?weight:Reflect.callMethod(corners,"getWeight")));
    }
    private static void temporary(HookCallback.Param p,Object target,String field,Object value) {
        p.setObjectExtra(field,new Object[]{Reflect.getObjectField(target,field)});
        Reflect.setObjectField(target,field,value);
    }
    static void install(ClassLoader loader) {
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
    }
    private static void bind(String key,Runnable action) {
        try{action.run();ShadeHook.hookRegistered("notification-card-"+key);}
        catch(Throwable error){ShadeHook.frameworkFailure("notification-card-"+key,error);}
    }
}
