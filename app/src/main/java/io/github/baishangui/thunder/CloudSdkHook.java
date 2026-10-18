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
    private static final Map<Object,Boolean> geometry=Collections.synchronizedMap(new WeakHashMap<>());
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
        // ModularCardChrome.s0 feeds three separate consumers: content outline,
        // sj stroke and COE shader e1. Its radius is dp (the plugin Triple is px).
        bind("corner-style",() -> Api102Runtime.findAndHookMethod("g7.f2",loader,"s0",boolean.class,new HookCallback() {
            @Override protected void afterHookedMethod(Param p) {
                if(p.hasThrowable()||p.getResult()==null||!expandedCloud(p.thisObject))return;
                geometry.putIfAbsent(p.thisObject,false);
                if(customCorners(p.thisObject))geometry.put(p.thisObject,true);
                p.setResult(style(p.thisObject,p.getResult()));
            }
        }));
        bind("outline-mode",() -> Api102Runtime.findAndHookMethod("g7.f2",loader,"c0",boolean.class,new HookCallback() {
            @Override protected void beforeHookedMethod(Param p) {
                Reflect.setAdditionalInstanceField(p.thisObject,"thunder.sdk-outline-mode",p.args[0]);
            }
        }));
        bind("expanded-geometry",() -> Api102Runtime.findAndHookMethod("g7.f2",loader,"w0",
            "com.oplus.ule.lite.components.o1",boolean.class,new HookCallback() {
                @Override protected void beforeHookedMethod(Param p) {
                    Object light=Reflect.getObjectField(p.thisObject,"l");
                    if(light!=null)Reflect.setAdditionalInstanceField(light,"thunder.sdk-chrome",new WeakReference<>(p.thisObject));
                }
                @Override protected void afterHookedMethod(Param p) {
                    if(!p.hasThrowable()&&expandedCloud(p.thisObject)) {
                        geometry.putIfAbsent(p.thisObject,false);refreshGeometryIfNeeded(p.thisObject);
                    }
                }
            }));
        // The newer light-effect backend bypasses s0 and caches its own j5.
        bind("light-corner-style",() -> Api102Runtime.findAndHookMethod("t7.e0",loader,"K0",new HookCallback() {
            @Override protected void afterHookedMethod(Param p) {
                Object ref=Reflect.getAdditionalInstanceField(p.thisObject,"thunder.sdk-chrome");
                Object card=ref instanceof WeakReference<?>?((WeakReference<?>)ref).get():null;
                if(!p.hasThrowable()&&p.getResult()!=null&&card!=null&&expandedCloud(card))p.setResult(style(card,p.getResult()));
            }
        }));
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
        bind("geometry-deopt",() -> Api102Runtime.deoptimizeCallers(Reflect.findClass("g7.f2",loader),"c0","e1","h1","t0","w0"));
        bind("light-geometry-deopt",() -> Api102Runtime.deoptimizeCallers(Reflect.findClass("t7.e0",loader),"c0","g0"));
    }
    private static boolean expandedCloud(Object card) {
        if(Reflect.getObjectField(card,"n")==null)return false;
        String entrance=String.valueOf(Reflect.getObjectField(card,"d"));
        return "Notification".equals(entrance)||"StatusBar".equals(entrance)||"LockScreen".equals(entrance)||"SecondaryLockScreen".equals(entrance);
    }
    private static HookConfig.Area cornerArea(Object card) {
        HookConfig c=ShadeHook.snapshot();
        if(!c.enabled)return null;
        // Notification content is also used on the lock screen. Its outer row
        // uses notification geometry, independently of cloud appearance settings.
        if("Notification".equals(String.valueOf(Reflect.getObjectField(card,"d"))))return c.notificationCard;
        return c.cloud?c.cloudCardArea:null;
    }
    private static Object style(Object card,Object nativeStyle) {
        HookConfig.Area area=cornerArea(card);
        if(area==null||!area.cornerEnabled)return nativeStyle;
        ShadeHook.resourceHit("cloud-sdk-corners");
        return Reflect.newInstance(nativeStyle.getClass(),Reflect.callMethod(nativeStyle,"b"),
            (float)area.corner,Reflect.callMethod(nativeStyle,"c"));
    }
    private static boolean customCorners(Object card) {
        HookConfig.Area area=cornerArea(card);return area!=null&&area.cornerEnabled;
    }
    private static void refreshGeometryIfNeeded(Object card) {
        boolean active=customCorners(card);
        if(!active&&!Boolean.TRUE.equals(geometry.get(card)))return;
        refreshGeometry(card);geometry.put(card,active);
    }
    private static void refreshGeometry(Object card) {
        Object mode=Reflect.getAdditionalInstanceField(card,"thunder.sdk-outline-mode");
        Reflect.callMethod(card,"c0",mode instanceof Boolean?mode:true);
        if(Reflect.getObjectField(card,"o")!=null)Reflect.callMethod(card,"e1");
        Object corners=Reflect.callMethod(card,"s0",true);
        View stroke=(View)Reflect.getObjectField(card,"k");
        Drawable drawable=stroke==null?null:stroke.getBackground();
        if(drawable!=null&&drawable.getClass().getName().equals("e7.sj"))
            Reflect.callMethod(drawable,"e",Reflect.callMethod(corners,"a"),Reflect.callMethod(corners,"c"),Reflect.callMethod(corners,"b"));
        Object light=Reflect.getObjectField(card,"l");
        if(light!=null) {
            Object lightCorners=Reflect.callMethod(light,"K0");
            if(lightCorners!=null) {
                Class<?> util=Reflect.findClass("g7.j4",card.getClass().getClassLoader());
                for(String field:new String[]{"a","b"}) {
                    View view=(View)Reflect.getObjectField(light,field);
                    // Use the native outline installer so smoothing/type stays intact.
                    try {
                        util.getDeclaredMethod("f",android.content.res.Resources.class,View.class,float.class,boolean.class,int.class,Float.class)
                            .invoke(null,view.getResources(),view,Reflect.callMethod(lightCorners,"a"),false,
                                Reflect.callMethod(lightCorners,"b"),Reflect.callMethod(lightCorners,"c"));
                    } catch(ReflectiveOperationException error){throw new IllegalStateException("SDK outline installer",error);}
                    view.invalidateOutline();
                }
            }
            if(Reflect.getObjectField(light,"f")!=null)Reflect.callMethod(light,"c0");
        }
        View host=(View)Reflect.getObjectField(card,"b");host.invalidateOutline();host.invalidate();
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
        ArrayList<Object> shapes;synchronized(geometry){shapes=new ArrayList<>(geometry.keySet());}
        for(Object card:shapes)try {if(expandedCloud(card))refreshGeometryIfNeeded(card);}
        catch(Throwable error){ShadeHook.frameworkFailure("cloud-sdk-refresh-geometry",error);}
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
