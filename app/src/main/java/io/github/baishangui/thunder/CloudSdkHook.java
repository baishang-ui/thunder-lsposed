package io.github.baishangui.thunder;

import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Background colors rendered inside the separately loaded C17 Seedling SDK. */
final class CloudSdkHook {
    private static final Map<ClassLoader,Boolean> installed=Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object,Boolean> cards=Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View,Boolean> flowing=Collections.synchronizedMap(new WeakHashMap<>());
    private record Fill(Drawable drawable,int[] colors) { }

    static void watch(ClassLoader loader) {
        bind("loader",() -> Api102Runtime.findAndHookMethod("com.oplus.seedling.sdk.plugin.PluginManager",loader,
            "initPluginClassLoader","com.oplus.seedling.sdk.SeedlingInitConfig",new HookCallback() {
                @Override protected void afterHookedMethod(Param p) {
                    if(!p.hasThrowable()&&p.getResult() instanceof ClassLoader)install((ClassLoader)p.getResult());
                }
            }));
        bind("loader-deopt",() -> Api102Runtime.deoptimizeCallers(Reflect.findClass(
            "com.oplus.seedling.sdk.plugin.PluginManager",loader),"doInit","initPluginClassLoader"));
    }

    static synchronized void install(ClassLoader loader) {
        if(installed.containsKey(loader))return;
        Reflect.findClass("g7.f2",loader);
        installed.put(loader,true);
        // L rebuilds only the SDK's cardBackground plane. Text, artwork and
        // controls belong to other planes and must retain their native opacity.
        bind("card-background",() -> Api102Runtime.findAndHookMethod("g7.f2",loader,"L",
            "com.oplus.ule.lite.components.s1",boolean.class,new HookCallback() {
                @Override protected void beforeHookedMethod(Param p) { restore(p.thisObject); }
                @Override protected void afterHookedMethod(Param p) {
                    if(p.hasThrowable())return;
                    cards.put(p.thisObject,true);
                    View background=(View)Reflect.getObjectField(p.thisObject,"i");
                    Drawable drawable=background.getBackground();
                    Fill fill=read(drawable);
                    Reflect.setAdditionalInstanceField(p.thisObject,"thunder.sdk-fill",fill);
                    apply(fill,ShadeHook.snapshot());
                    ShadeHook.resourceHit("cloud-sdk-app-color");
                }
            }));
        // FlowingBackgroundView replaces Paint alpha twice in onDraw. Scale
        // those original animation values, not the alpha of the whole card.
        bind("flowing-color",() -> Api102Runtime.findAndHookMethod("e7.a4",loader,"onDraw",
            android.graphics.Canvas.class,new HookCallback() {
                @Override protected void beforeHookedMethod(Param p) {
                    flowing.put((View)p.thisObject,true);
                    int strength=strength(ShadeHook.snapshot());
                    if(strength==100)return;
                    for(String field:new String[]{"d","e"}) {
                        float value=((Number)Reflect.getObjectField(p.thisObject,field)).floatValue();
                        p.setObjectExtra(field,value);
                        Reflect.setObjectField(p.thisObject,field,Math.min(1f,value*strength/100f));
                    }
                }
                @Override protected void afterHookedMethod(Param p) {
                    for(String field:new String[]{"d","e"}) {
                        Object value=p.getObjectExtra(field);if(value!=null)Reflect.setObjectField(p.thisObject,field,value);
                    }
                }
            }));
        bind("background-deopt",() -> Api102Runtime.deoptimizeCallers(Reflect.findClass("g7.f2",loader),"L","f1","l0"));
    }
    private static Fill read(Drawable drawable) {
        if(drawable instanceof ColorDrawable)return new Fill(drawable,new int[]{((ColorDrawable)drawable).getColor()});
        if(drawable instanceof GradientDrawable) {
            GradientDrawable gradient=(GradientDrawable)drawable;
            int[] colors=gradient.getColors();
            if(colors!=null)return new Fill(drawable,colors.clone());
            if(gradient.getColor()!=null)return new Fill(drawable,new int[]{gradient.getColor().getDefaultColor()});
        }
        return null;
    }
    private static void write(Fill fill,int[] colors) {
        if(fill==null)return;
        if(fill.drawable instanceof ColorDrawable)((ColorDrawable)fill.drawable).setColor(colors[0]);
        else if(colors.length==1)((GradientDrawable)fill.drawable).setColor(colors[0]);
        else ((GradientDrawable)fill.drawable).setColors(colors);
    }
    private static void restore(Object card) {
        Object saved=Reflect.getAdditionalInstanceField(card,"thunder.sdk-fill");
        if(saved instanceof Fill)write((Fill)saved,((Fill)saved).colors);
    }
    private static void apply(Fill fill,HookConfig config) {
        if(fill!=null)write(fill,CloudLockHook.adjust(fill.colors,config));
    }
    private static int strength(HookConfig c) {
        if(!c.enabled||!c.cloud)return 100;
        return !c.cloudAppColor?0:c.cloudAppColorStrengthEnabled?c.cloudAppColorStrength:100;
    }
    static void refresh() {
        ArrayList<Object> current;synchronized(cards){current=new ArrayList<>(cards.keySet());}
        for(Object card:current) {
            Object saved=Reflect.getAdditionalInstanceField(card,"thunder.sdk-fill");
            if(saved instanceof Fill)apply((Fill)saved,ShadeHook.snapshot());
        }
        ArrayList<View> animations;synchronized(flowing){animations=new ArrayList<>(flowing.keySet());}
        for(View view:animations)view.invalidate();
    }
    private static void bind(String key,Runnable action) {
        try{action.run();ShadeHook.hookRegistered("cloud-sdk-"+key);}
        catch(Throwable error){ShadeHook.frameworkFailure("cloud-sdk-"+key,error);}
    }
}
