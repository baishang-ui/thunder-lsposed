package io.github.baishangui.thunder;

import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Native SystemUI template color planes, separate from MetaBall and Seedling SDK fills. */
final class CloudTemplateHook {
    private static final String BASE="com.oplus.systemui.plugins.shared.template.";
    private static final Map<ClassLoader,Boolean> installed=Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Drawable,Integer> backgrounds=Collections.synchronizedMap(new WeakHashMap<>());
    private record Alpha(float[] values,int index,float original) {
        void restore(){values[index]=original;}
    }
    static synchronized void install(ClassLoader loader) {
        if(installed.containsKey(loader))return;
        installed.put(loader,true);
        bind("light",() -> Api102Runtime.findAndHookMethod(BASE+"component.light.f",loader,"draw",Canvas.class,new HookCallback(){
            @Override protected void beforeHookedMethod(Param p) {
                if(!background((Drawable)p.thisObject))return;
                int strength=track((Drawable)p.thisObject);if(strength==100)return;
                Object params=Reflect.getObjectField(p.thisObject,"e");
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
    }
    private static void bind(String key,Runnable action) {
        try{action.run();ShadeHook.hookRegistered("cloud-template-"+key);}
        catch(Throwable error){ShadeHook.frameworkFailure("cloud-template-"+key,error);}
    }
}
