package io.github.baishangui.thunder;

import android.graphics.Canvas;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** C17 control-center tiles, media/plugin cards and their non-blur fallback backgrounds. */
final class QsCardHook {
    private static final String TILE="com.oplus.systemui.qs.base.res.drawable.";
    private static final Map<Drawable,Boolean> drawables=Collections.synchronizedMap(new WeakHashMap<>());

    static void install(ClassLoader loader) {
        for(String name:new String[]{TILE+"MixColorTileDrawable","com.oplus.systemui.plugins.drawable.MixColorPluginDrawable"}) {
            bind("owner-"+name,() -> Api102Runtime.hookAllConstructors(Reflect.findClass(name,loader),new HookCallback() {
                @Override protected void afterHookedMethod(Param p) {
                    if(p.hasThrowable())return;
                    Drawable drawable=(Drawable)p.thisObject;
                    drawables.put(drawable,true);
                    Object auto=Reflect.getObjectField(drawable,"autoBlurDrawable");
                    ShadeHook.trackQsCard(Reflect.callMethod(auto,"getViewBlurProxy"));
                }
            }));
        }
        bind("mask",() -> Api102Runtime.findAndHookMethod(TILE+"MixColorTileDrawable",loader,"draw",Canvas.class,new HookCallback() {
            @Override protected void beforeHookedMethod(Param p) {
                HookConfig c=ShadeHook.snapshot();if(!c.enabled||!c.qsCards.tint)return;
                p.setObjectExtra("mask",Reflect.getIntField(p.thisObject,"maskColor"));
                Reflect.setIntField(p.thisObject,"maskColor",0);
            }
            @Override protected void afterHookedMethod(Param p) {
                Object mask=p.getObjectExtra("mask");
                if(mask!=null)Reflect.setIntField(p.thisObject,"maskColor",(Integer)mask);
            }
        }));
        bind("flat-owner",() -> Api102Runtime.hookAllConstructors(Reflect.findClass(TILE+"GradientTileDrawable",loader),new HookCallback() {
            @Override protected void afterHookedMethod(Param p) {
                if(!p.hasThrowable())drawables.put((Drawable)p.thisObject,true);
            }
        }));
        bind("flat-color",() -> Api102Runtime.findAndHookMethod(TILE+"GradientTileDrawable",loader,"draw",Canvas.class,new HookCallback() {
            @Override protected void beforeHookedMethod(Param p) {
                HookConfig c=ShadeHook.snapshot();if(!c.enabled||!c.qsCards.tint)return;
                ColorDrawable fill=(ColorDrawable)Reflect.getObjectField(p.thisObject,"colorDrawable");
                p.setObjectExtra("fill",fill);p.setObjectExtra("color",fill.getColor());
                p.setObjectExtra("callback",fill.getCallback());
                // setColor invalidates its wrapper. A temporary draw override must not schedule another frame.
                fill.setCallback(null);
                int opacity=Math.max(0,Math.min(255,(Integer)Reflect.callMethod(p.thisObject,"getAlpha")));
                int alpha=Math.round((c.qsCards.color>>>24)*opacity/255f);
                fill.setColor((c.qsCards.color&0xffffff)|(alpha<<24));
                ShadeHook.resourceHit("qs-card-flat");
            }
            @Override protected void afterHookedMethod(Param p) {
                Object fill=p.getObjectExtra("fill");
                if(fill!=null) {
                    ColorDrawable drawable=(ColorDrawable)fill;
                    try {drawable.setColor((Integer)p.getObjectExtra("color"));}
                    finally {drawable.setCallback((Drawable.Callback)p.getObjectExtra("callback"));}
                }
            }
        }));
    }
    static void refresh() {
        ArrayList<Drawable> current;
        synchronized(drawables){current=new ArrayList<>(drawables.keySet());}
        for(Drawable drawable:current)drawable.invalidateSelf();
    }
    private static void bind(String key,Runnable action) {
        try {action.run();ShadeHook.hookRegistered("qs-card-"+key);}
        catch(Throwable error){ShadeHook.frameworkFailure("qs-card-"+key,error);}
    }
}
