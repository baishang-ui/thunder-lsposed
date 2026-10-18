package io.github.baishangui.thunder;

import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.view.View;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Native SystemUI template color planes, separate from MetaBall and Seedling SDK fills. */
final class CloudTemplateHook {
    private static final String BASE="com.oplus.systemui.plugins.shared.template.";
    private static final Map<ClassLoader,Boolean> installed=Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Drawable,Integer> backgrounds=Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View,Boolean> geometryViews=Collections.synchronizedMap(new WeakHashMap<>());
    private record Alpha(float[] values,int index,float original) {
        void restore(){values[index]=original;}
    }
    static synchronized void install(ClassLoader loader) {
        if(installed.containsKey(loader))return;
        installed.put(loader,true);
        // H also serves notification templates. Keep its resource-derived baseline
        // native; select cloud geometry at the owning view, after reparenting.
        bind("native-geometry",() -> Api102Runtime.findAndHookMethod("com.heytap.log.strategy.f",loader,"H",
            android.content.Context.class,int.class,boolean.class,new HookCallback() {
                @Override protected void beforeHookedMethod(Param p){CloudHook.beginNativeCorners();}
                @Override protected void afterHookedMethod(Param p){CloudHook.endNativeCorners();}
            }));
        bind("media-stroke",() -> Api102Runtime.findAndHookMethod(BASE+"component.view.CustomLottieView",loader,"i",Canvas.class,mediaGeometry(false)));
        bind("media-outline",() -> Api102Runtime.findAndHookMethod(BASE+"component.view.c",loader,"getOutline",
            View.class,android.graphics.Outline.class,mediaGeometry(true)));
        bind("light",() -> Api102Runtime.findAndHookMethod(BASE+"component.light.f",loader,"draw",Canvas.class,new HookCallback(){
            @Override protected void beforeHookedMethod(Param p) {
                if(!background((Drawable)p.thisObject))return;
                Object params=Reflect.getObjectField(p.thisObject,"e");
                View owner=owner((Drawable)p.thisObject);
                Float radius=radius(owner);
                if(radius!=null) {
                    float[] values=(float[])Reflect.getObjectField(params,"d");
                    Alpha[] saved=new Alpha[4];
                    for(int i=0;i<4;i++)saved[i]=new Alpha(values,6+i,values[6+i]);
                    p.setObjectExtra("geometry",saved);p.setObjectExtra("geometry-params",params);
                    for(int i=6;i<10;i++)values[i]=radius;
                    Reflect.setObjectField(params,"e",true);
                    ShadeHook.resourceHit("cloud-template-corners");
                }
                int strength=track((Drawable)p.thisObject);if(strength==100)return;
                float[] shadow=(float[])Reflect.getObjectField(params,"i");
                float[] edge=(float[])Reflect.getObjectField(params,"n");
                float[] circle=(float[])Reflect.getObjectField(params,"t");
                // Firmware shader: shadow FadeAlpha[0:2], edge LineAlpha[10:12],
                // circle Alpha[4]. RGB/model ARGB alpha does not control these lights.
                Alpha[] saved={new Alpha(shadow,0,shadow[0]),new Alpha(shadow,1,shadow[1]),
                    new Alpha(edge,10,edge[10]),new Alpha(edge,11,edge[11]),new Alpha(circle,4,circle[4])};
                p.setObjectExtra("alpha",saved);p.setObjectExtra("params",params);
                for(Alpha alpha:saved)alpha.values[alpha.index]=scale(alpha.original,strength);
                dirty(params);
            }
            @Override protected void afterHookedMethod(Param p) {
                Object geometry=p.getObjectExtra("geometry");
                if(geometry instanceof Alpha[]) {
                    for(Alpha value:(Alpha[])geometry)value.restore();
                    Reflect.setObjectField(p.getObjectExtra("geometry-params"),"e",true);
                }
                Object saved=p.getObjectExtra("alpha");if(!(saved instanceof Alpha[]))return;
                for(Alpha alpha:(Alpha[])saved)alpha.restore();
                // The native draw cleared these flags after uploading adjusted uniforms.
                // Restore CPU values and force next frame to upload again, including at 100%.
                dirty(p.getObjectExtra("params"));
            }
        }));
        bind("pulse",() -> Api102Runtime.findAndHookMethod(BASE+"animator.drawable.b",loader,"draw",Canvas.class,new HookCallback(){
            @Override protected void beforeHookedMethod(Param p) {
                if(!background((Drawable)p.thisObject))return;
                int strength=track((Drawable)p.thisObject);if(strength==100)return;
                for(String field:new String[]{"c","d"}) {
                    float value=((Number)Reflect.getObjectField(p.thisObject,field)).floatValue();
                    p.setObjectExtra(field,value);Reflect.setObjectField(p.thisObject,field,scale(value,strength));
                }
            }
            @Override protected void afterHookedMethod(Param p) {
                for(String field:new String[]{"c","d"}) {
                    Object value=p.getObjectExtra(field);if(value!=null)Reflect.setObjectField(p.thisObject,field,value);
                }
            }
        }));
        bind("page-deopt",() -> Api102Runtime.deoptimizeCallers(Reflect.findClass(BASE+"page.view.a",loader),"draw"));
        bind("media-geometry-deopt",() -> Api102Runtime.deoptimizeCallers(Reflect.findClass(BASE+"component.view.CustomLottieView",loader),"dispatchDraw"));
    }
    private static View owner(Drawable drawable) {
        Object callback=drawable.getCallback();
        for(int depth=0;callback instanceof Drawable&&depth<16;depth++)callback=((Drawable)callback).getCallback();
        return callback instanceof View?(View)callback:null;
    }
    private static Float radius(View view) {
        if(view==null)return null;
        geometryViews.put(view,true);
        HookConfig.Area notification=NotificationCardHook.containingArea(view);
        if(notification!=null)return notification.cornerEnabled?
            notification.corner*view.getResources().getDisplayMetrics().density:null;
        HookConfig c=ShadeHook.snapshot();
        return c.enabled&&c.cloud&&c.cloudCardArea.cornerEnabled&&CloudHook.inCard(view)?
            c.cloudCardArea.corner*view.getResources().getDisplayMetrics().density:null;
    }
    private static HookCallback mediaGeometry(boolean outline) {
        return new HookCallback() {
            @Override protected void beforeHookedMethod(Param p) {
                Object candidate=outline?p.args[0]:p.thisObject;
                if(!(candidate instanceof View)||!candidate.getClass().getName().equals(BASE+"component.media.view.MediaBackGroundView"))return;
                Float radius=radius((View)candidate);if(radius==null)return;
                Object saved=Reflect.getObjectField(candidate,"t");
                p.setObjectExtra("media",candidate);p.setObjectExtra("radius",saved);
                Reflect.setObjectField(candidate,"t",radius);
            }
            @Override protected void afterHookedMethod(Param p) {
                Object media=p.getObjectExtra("media"),saved=p.getObjectExtra("radius");
                if(media!=null&&saved!=null)Reflect.setObjectField(media,"t",saved);
            }
        };
    }
    private static float scale(float alpha,int strength){return Math.max(0f,Math.min(1f,alpha*strength/100f));}
    private static void dirty(Object params) {
        for(String field:new String[]{"j","o","u"})Reflect.setObjectField(params,field,true);
    }
    private static boolean background(Drawable drawable) {
        Object owner=drawable.getCallback();
        for(int depth=0;owner!=null&&depth<8;depth++) {
            for(Class<?> cls=owner.getClass();cls!=null;cls=cls.getSuperclass()) {
                String name=cls.getName();
                if(name.equals(BASE+"component.media.view.MediaBackGroundView")||name.equals(BASE+"page.view.PageBackgroundView"))return true;
            }
            if(!(owner instanceof Drawable))return false;
            owner=((Drawable)owner).getCallback();
        }
        return false;
    }
    private static int track(Drawable drawable) {
        HookConfig c=ShadeHook.snapshot();
        int strength=!c.enabled||!c.cloud?100:!c.cloudAppColor?0:c.cloudAppColorStrengthEnabled?c.cloudAppColorStrength:100;
        Integer previous=backgrounds.put(drawable,strength);
        if(previous==null||previous!=strength)ShadeHook.observed("cloud-template-app-color",drawable.getClass().getSimpleName()+" strength="+strength+"%");
        return strength;
    }
    static void refresh() {
        ArrayList<Drawable> current;synchronized(backgrounds){current=new ArrayList<>(backgrounds.keySet());}
        for(Drawable drawable:current)drawable.invalidateSelf();
        ArrayList<View> views;synchronized(geometryViews){views=new ArrayList<>(geometryViews.keySet());}
        for(View view:views){view.invalidateOutline();view.invalidate();}
    }
    private static void bind(String key,Runnable action) {
        try{action.run();ShadeHook.hookRegistered("cloud-template-"+key);}
        catch(Throwable error){ShadeHook.frameworkFailure("cloud-template-"+key,error);}
    }
}
