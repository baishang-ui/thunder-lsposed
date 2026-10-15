package pui.c17.shade;

import android.app.Application;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.ArrayList;
import java.lang.ref.WeakReference;
import android.os.Process;
import android.view.View;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import android.content.SharedPreferences;
import android.graphics.Canvas;

/** SystemUI effect handlers installed via libxposed API 102; Compose stays in the app process. */
public final class ShadeHook {
    private static final String BLUR = "com.oplusos.systemui.common.blurability.";
    private static final String ROLE = "pui.c17.role";
    private static final Uri URI = Uri.parse("content://pui.c17.shade.settings");
    private static volatile HookConfig config = new HookConfig(null);
    private static volatile Object qsManager;
    private static volatile Context context;
    private static Handler worker, main;
    private static volatile boolean qsVisible;
    private static final Map<Object, Boolean> proxies = Collections.synchronizedMap(new WeakHashMap<>());
    private static boolean installed;
    private static final Set<String> hooks = ConcurrentHashMap.newKeySet();
    private static final Set<String> hits = ConcurrentHashMap.newKeySet();
    private static final Set<String> failures = ConcurrentHashMap.newKeySet();
    private static final Map<String,String> failureDetails = new ConcurrentHashMap<>();
    private static final Map<String,String> effective = new ConcurrentHashMap<>();
    private static final ThreadLocal<Integer> livePanelDepth = ThreadLocal.withInitial(() -> 0);
    private static String settingsSource = "unavailable";
    private static Class<?> shaderClass, shaderConfigClass, mixClass, motionConfigClass;

    public static void install(String packageName, String processName, ClassLoader loader) {
        if (!"com.android.systemui".equals(packageName)
                || !"com.android.systemui".equals(processName) || installed) return;
        if (android.os.Build.VERSION.SDK_INT != 37) return;
        installed = true;
        HandlerThread thread = new HandlerThread("PUI-C17-settings");
        thread.start(); worker = new Handler(thread.getLooper()); main = new Handler(Looper.getMainLooper());
        // Framework-owned storage is available before the target Application exists.
        // This avoids the old provider startup race and stale dimension caches.
        install("remote-settings", () -> {
            SharedPreferences prefs=Api102Runtime.remotePreferences();
            Bundle data=new Bundle();
            for(Map.Entry<String,?> entry:prefs.getAll().entrySet()) {
                Object value=entry.getValue(); String key=entry.getKey();
                if(value instanceof Boolean)data.putBoolean(key,(Boolean)value);
                else if(value instanceof Integer)data.putInt(key,(Integer)value);
                else if(value instanceof Long)data.putLong(key,(Long)value);
            }
            if(!data.containsKey("revision"))throw new IllegalStateException("Open module app to sync API 102 settings");
            config=new HookConfig(data); settingsSource="API102 remote";
        });
        install("settings", () -> Api102Runtime.findAndHookMethod(Application.class, "attach", Context.class,
            new HookCallback() {
                @Override protected void afterHookedMethod(HookCallback.Param p) {
                    context = ((Context) p.args[0]).getApplicationContext();
                    if (context == null) context = (Context) p.args[0];
                    // API 102 config is already loaded. Provider is only a fallback
                    // and a diagnostic return channel; never block SystemUI startup.
                    if(config.revision<0) worker.post(() -> readSettings(0));
                    else worker.post(ShadeHook::report);
                }
            }));
        install("resource-overrides", ResourceOverrides::install);
        install("qs", () -> Api102Runtime.hookAllConstructors(Reflect.findClass(
            "com.oplus.systemui.separate.OplusSeparateQSManager", loader), new HookCallback() {
                @Override protected void afterHookedMethod(HookCallback.Param p) { qsManager = p.thisObject; }
            }));
        install("qs-transition", () -> Api102Runtime.findAndHookMethod(
            "com.oplus.systemui.separate.OplusSeparateQSManager", loader, "getBlurFraction", new HookCallback() {
                @Override protected void afterHookedMethod(HookCallback.Param p) {
                    if (p.hasThrowable() || !(p.getResult() instanceof Number)) return;
                    boolean visible = ((Number) p.getResult()).floatValue() > 0.001f;
                    if (visible != qsVisible) {
                        qsVisible = visible;
                        main.post(ShadeHook::refreshProxies);
                    }
                }
            }));
        install("mix-api", () -> {
            shaderClass = Reflect.findClass(BLUR+"MixColorWithShader", loader);
            shaderConfigClass = Reflect.findClass(BLUR+"BlurMixConfig$BlurMixSingleWithShader", loader);
            mixClass = Reflect.findClass(BLUR+"MixColor", loader);
            motionConfigClass = Reflect.findClass(BLUR+"BlurMixConfig$BlurMixSingle", loader);
        });
        // Same producer used by the original working dim-removal module.
        // Runs before ViewBlurProxy creates/caches its native backend and configs.
        install("panel-source", () -> Api102Runtime.findAndHookMethod(
            "com.oplusos.systemui.common.util.NotifiAndQsPlatformBlurExKt", loader,
            "panelPlatformMixConfig", Context.class, boolean.class, boolean.class, new HookCallback() {
                @Override protected void afterHookedMethod(HookCallback.Param p) {
                    if(p.hasThrowable()||p.getResult()==null||!config.enabled||!config.shade.tint)return;
                    p.setResult(panelMix(p.getResult(),config.shade));
                    observed("panel-source", "ARGB="+Integer.toHexString(config.shade.color));
                }
            }));
        install("panel-radius-source", () -> Api102Runtime.findAndHookMethod(
            "com.oplusos.systemui.common.util.NotifiAndQsPlatformBlurExKt", loader,
            "panelBlurRadius", Context.class, new HookCallback() {
                @Override protected void afterHookedMethod(HookCallback.Param p) {
                    if(!p.hasThrowable()&&config.enabled&&config.shade.blur) {
                        p.setResult(config.shade.radius);observed("panel-radius-source",config.shade.radius+"px");
                    }
                }
            }));
        install("notification-layout", () -> Api102Runtime.findAndHookMethod(
            "com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayout",loader,
            "onMeasure",int.class,int.class,new HookCallback() {
                @Override protected void beforeHookedMethod(HookCallback.Param p) {
                    HookConfig c=config;if(!c.enabled||!c.margins)return;
                    View view=(View)p.thisObject;
                    int px=Math.round(c.margin*view.getResources().getDisplayMetrics().density);
                    Reflect.setIntField(view,"mSidePaddings",px);
                    observed("notification-layout",px+"px ("+c.margin+"dp)");
                }
            }));
        install("deopt-panel", () -> Api102Runtime.deoptimizeCallers(Reflect.findClass(
            "com.oplus.systemui.statusbar.phone.ScrimControllerExImp",loader),
            "getPanelPlatformMixConfig","refreshBehindDrawable","updateBehindMixConfig","adjudgeScrimBehindBlurType"));
        install("deopt-proxy", () -> Api102Runtime.deoptimizeCallers(Reflect.findClass(BLUR+"ViewBlurProxy",loader),
            "applyBlurConfig","setBlurAmount","setBlurType","applyConfigToMotionBlur","applyConfigToPlatformBlur",
            "applyConfigToStaticBlur","applyConfigToBlendWallpaper"));
        install("deopt-layout", () -> Api102Runtime.deoptimizeCallers(Reflect.findClass(
            "com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayout",loader),"initView"));
        // Low-Gaussian uses a pre-blurred wallpaper bitmap; radius=0 cannot undo it.
        // Scope the native selection override to the unlocked background only.
        for(String name:new String[]{"refreshBehindDrawable","adjudgeScrimBehindBlurType"})
            install("live-scope-"+name, () -> Api102Runtime.findAndHookMethod(
                "com.oplus.systemui.statusbar.phone.ScrimControllerExImp",loader,name,livePanelScope(true)));
        install("live-scope-color", () -> Api102Runtime.findAndHookMethod(
            "com.oplus.systemui.scrim.ScrimViewExImp",loader,"updateColorWithTint",boolean.class,livePanelScope(false)));
        install("live-scope-amount", () -> Api102Runtime.findAndHookMethod(
            "com.oplus.systemui.scrim.ScrimViewExImp",loader,"setBlurAmount",float.class,String.class,livePanelScope(false)));
        install("live-scope-alpha", () -> Api102Runtime.findAndHookMethod(
            "com.oplus.systemui.scrim.ScrimViewExImp",loader,"setViewAlpha",float.class,livePanelScope(false)));
        install("live-panel-selection", () -> Api102Runtime.findAndHookMethod(
            "com.oplusos.systemui.common.util.ScrimUtil",loader,"isLowGaussianLevel",Context.class,new HookCallback() {
                @Override protected void beforeHookedMethod(HookCallback.Param p) {
                    if(livePanelDepth.get()>0) { p.setResult(false);hit("live-panel-selection"); }
                }
            }));
        install("deopt-scrim-ext", () -> Api102Runtime.deoptimizeCallers(Reflect.findClass(
            "com.oplus.systemui.scrim.ScrimViewExImp",loader),"updateColorWithTint","setBlurAmount","setViewAlpha","setColor"));
        install("deopt-mask", () -> Api102Runtime.deoptimizeCallers(Reflect.findClass(
            BLUR+"drawable.MaskDrawable",loader),"draw"));
        install("panel", () -> Api102Runtime.hookAllConstructors(Reflect.findClass(BLUR+"ViewBlurProxy", loader),
            new HookCallback() {
                @Override protected void afterHookedMethod(HookCallback.Param p) {
                    try {
                        View view = (View) p.args[0];
                        if (view.getId() != View.NO_ID && "scrim_behind".equals(view.getResources().getResourceEntryName(view.getId()))) {
                            Reflect.setAdditionalInstanceField(p.args[1], ROLE, "panel");
                            proxies.put(p.thisObject, true);
                        }
                    } catch (Throwable ignored) { /* Other views and missing IDs are unrelated. */ }
                }
            }));
        install("banner", () -> Api102Runtime.findAndHookMethod(
            "com.oplus.systemui.notification.blur.ViewBlurManager", loader, "requireBlurProxyForView", View.class,
            "com.oplus.systemui.notification.blur.ViewBlurManager$CardType", float.class,
            "com.oplus.systemui.notification.row.material.NotificationBlurMode", new HookCallback() {
                @Override protected void afterHookedMethod(HookCallback.Param p) {
                    if (p.hasThrowable() || p.getResult() == null) return;
                    String type = ((Enum<?>) p.args[1]).name();
                    if (!"FULLSCREENBANNER".equals(type)) return;
                    try {
                        Object proxy = p.getResult();
                        Object blur = Reflect.callMethod(proxy, "getBlurConfig");
                        if (Reflect.getAdditionalInstanceField(blur, ROLE) == null) {
                            Reflect.setAdditionalInstanceField(blur, ROLE, "banner");
                            proxies.put(proxy, true);
                            Reflect.callMethod(proxy, "applyBlurConfig");
                        }
                    } catch (Throwable error) { failed("banner", error); }
                }
            }));
        for (String name : new String[]{"blurForHeadsUp", "cancelBlurForHeadsUp"}) {
            install(name, () -> Api102Runtime.findAndHookMethod(
                "com.oplus.systemui.notification.blur.ViewBlurManager", loader, name, View.class, boolean.class,
                new HookCallback() {
                    @Override protected void beforeHookedMethod(HookCallback.Param p) {
                        try {
                            Object proxy = Reflect.callMethod(p.thisObject, "getBlurProxyForView", p.args[0]);
                            if (proxy == null) return;
                            Object blurConfig = Reflect.callMethod(proxy, "getBlurConfig");
                            if ("blurForHeadsUp".equals(name)) {
                                Reflect.setAdditionalInstanceField(blurConfig, ROLE, "banner");
                                proxies.put(proxy, true);
                                hit("banner");
                            } else {
                                Reflect.removeAdditionalInstanceField(blurConfig, ROLE);
                            }
                        } catch (Throwable error) { failed(name, error); }
                    }
                }));
        }
        install("panel-owner", () -> Api102Runtime.findAndHookMethod(
            "com.oplus.systemui.statusbar.phone.ScrimControllerExImp", loader, "updateBehindMixConfig", new HookCallback() {
                @Override protected void beforeHookedMethod(HookCallback.Param p) { capturePanel(p.thisObject); }
                @Override protected void afterHookedMethod(HookCallback.Param p) { capturePanel(p.thisObject); }
            }));
        install("panel-drawable", () -> Api102Runtime.findAndHookMethod(
            "com.oplus.systemui.statusbar.phone.ScrimControllerExImp", loader, "refreshBehindDrawable", new HookCallback() {
                @Override protected void afterHookedMethod(HookCallback.Param p) { capturePanel(p.thisObject); }
            }));
        // Backends are also called directly from setBlurAmount / setBlurType,
        // without applyBlurConfig. Patch at each native consumer, not just getters.
        for(String method:new String[]{"applyBlurConfig","applyConfigToMotionBlur","applyConfigToPlatformBlur",
                "applyConfigToStaticBlur","applyConfigToBlendWallpaper"}) {
            install("native-"+method, () -> Api102Runtime.findAndHookMethod(BLUR+"ViewBlurProxy",loader,method,new HookCallback() {
                @Override protected void beforeHookedMethod(HookCallback.Param p) {
                    Object blur=Reflect.callMethod(p.thisObject,"getBlurConfig");
                    HookConfig.Area area=area(blur);if(area==null)return;
                    String role=String.valueOf(Reflect.getAdditionalInstanceField(blur,ROLE));
                    Object type=Reflect.callMethod(p.thisObject,"getBlurType");
                    String backend=type==null?"unknown":type.getClass().getSimpleName();
                    Reflect.setAdditionalInstanceField(blur,"pui-backend",backend);
                    p.setObjectExtra("config",blur);
                    if(area.blur) {
                        temporary(p,blur,"blurRadius",area.radius);
                        temporary(p,blur,"motionBlurRadius",area.radius);
                    }
                    if(area.tint) {
                        Object platform=Reflect.getObjectField(blur,"platformMixConfig");
                        temporary(p,blur,"platformMixConfig","panel".equals(role)?panelMix(platform,area):bannerMix(platform,area));
                        if("banner".equals(role))temporary(p,blur,"motionBlurMixConfig",motionMix(Reflect.getObjectField(blur,"motionBlurMixConfig"),area));
                    }
                    observed(role+"-native",backend+" radius="+(area.blur?area.radius:"system")+" ARGB="+(area.tint?Integer.toHexString(area.color):"system"));
                }
                @Override protected void afterHookedMethod(HookCallback.Param p) {
                    Object blur=p.getObjectExtra("config");if(blur==null)return;
                    for(String field:new String[]{"blurRadius","motionBlurRadius","platformMixConfig","motionBlurMixConfig"}) {
                        Object saved=p.getObjectExtra(field);
                        if(saved instanceof Object[])Reflect.setObjectField(blur,field,((Object[])saved)[0]);
                    }
                }
            }));
        }
        // Low-Gaussian wallpaper drawables bypass BlurConfig's platform shader entirely.
        install("wallpaper-owner", () -> Api102Runtime.findAndHookMethod(
            "com.oplus.systemui.wallpaperblur.WallpaperBlurManager", loader, "requireWallpaperBlurDrawableForView", View.class, boolean.class, new HookCallback() {
                @Override protected void afterHookedMethod(HookCallback.Param p) {
                    if(p.hasThrowable()||p.getResult()==null)return;
                    try {
                        View view=(View)p.args[0];
                        if("scrim_behind".equals(view.getResources().getResourceEntryName(view.getId())))
                            Reflect.setAdditionalInstanceField(p.getResult(),ROLE,new WeakReference<>(view));
                    } catch(Throwable ignored) { }
                }
            }));
        install("wallpaper-dim", () -> Api102Runtime.findAndHookMethod(
            "com.oplus.systemui.wallpaperblur.WallpaperBlurDrawable", loader,"draw",android.graphics.Canvas.class,new HookCallback() {
                @Override protected void beforeHookedMethod(HookCallback.Param p) {
                    if(!config.enabled||!config.shade.tint)return;
                    try {
                        Object owner=Reflect.getAdditionalInstanceField(p.thisObject,ROLE);
                        Object view=owner instanceof WeakReference ? ((WeakReference<?>)owner).get() : null;
                        if(view==null)return;
                        Object ext=Reflect.callMethod(view,"getExt");
                        Object state=Reflect.callMethod(ext,"getScrimState");
                        if(!"UNLOCKED".equals(String.valueOf(state)))return;
                        // .104 ignores drawableColor alpha and always draws currentAlpha * 0.3.
                        // Let the native bitmap draw, then draw its overlay with the chosen alpha.
                        p.setObjectExtra("pui-overlay-enabled",Reflect.getBooleanField(p.thisObject,"isOverlayEnabled"));
                        p.setObjectExtra("pui-overlay-color",config.shade.color);
                        Reflect.setBooleanField(p.thisObject,"isOverlayEnabled",false);
                        hit("wallpaper-dim");
                    } catch(Throwable error) { failed("wallpaper-dim",error); }
                }
                @Override protected void afterHookedMethod(HookCallback.Param p) {
                    Object enabled=p.getObjectExtra("pui-overlay-enabled");
                    if(enabled==null)return;
                    Reflect.setBooleanField(p.thisObject,"isOverlayEnabled",(Boolean)enabled);
                    if(p.hasThrowable() || !(Boolean)enabled)return;
                    int color=(Integer)p.getObjectExtra("pui-overlay-color");
                    int amount=Math.max(0,Math.min(255,Reflect.getIntField(p.thisObject,"currentAlpha")));
                    int alpha=Math.round((color>>>24)*amount/255f);
                    ((android.graphics.Canvas)p.args[0]).drawColor((color&0xffffff)|(alpha<<24));

                }
            }));
        Class<?> blur;
        try { blur = Reflect.findClass(BLUR+"BlurConfig", loader); }
        catch (Throwable error) { failed("config-api", error); return; }
        install("radius", () -> Api102Runtime.findAndHookMethod(blur, "platformBlurRadiusForAmount", float.class,
            new HookCallback() {
                @Override protected void afterHookedMethod(HookCallback.Param p) {
                    if (p.hasThrowable()) return;
                    try {
                        HookConfig.Area area = area(p.thisObject);
                        if (area == null || !area.blur) return;
                        boolean scales = Reflect.getBooleanField(p.thisObject, "scaleBlurRadiusWithAmount");
                        float amount = scales ? Math.max(0f, Math.min(1f, (Float) p.args[0])) : 1f;
                        p.setResult((int) (area.radius * amount));
                        hit("radius");
                    } catch (Throwable error) { failed("radius", error); }
                }
            }));
        for (String getter : new String[]{"getBlurRadius", "getMotionBlurRadius"}) {
            install(getter, () -> Api102Runtime.findAndHookMethod(blur, getter, new HookCallback() {
                @Override protected void afterHookedMethod(HookCallback.Param p) {
                    if (p.hasThrowable()) return;
                    try {
                        HookConfig.Area area = area(p.thisObject);
                        if (area != null && area.blur) { p.setResult(area.radius); hit("radius"); }
                    } catch (Throwable error) { failed("radius", error); }
                }
            }));
        }
        install("panel-mask", () -> Api102Runtime.findAndHookMethod(blur,"getBlurColor",new HookCallback() {
            @Override protected void afterHookedMethod(HookCallback.Param p) {
                if(p.hasThrowable())return;
                try {
                    HookConfig.Area area=area(p.thisObject);
                    if(area!=null && area.tint && "panel".equals(Reflect.getAdditionalInstanceField(p.thisObject,ROLE))) {
                        // The chosen native mix is the dim layer; avoid an independent opaque mask on top.
                        Object backend=Reflect.getAdditionalInstanceField(p.thisObject,"pui-backend");
                        boolean mask="BlurTypeMotion".equals(backend)||"BlurTypeStatic".equals(backend);
                        p.setResult(mask?area.color:0);hit("panel-dim");
                    }
                } catch(Throwable error) { failed("panel-mask",error); }
            }
        }));
        install("color", () -> Api102Runtime.findAndHookMethod(blur, "getPlatformMixConfig", new HookCallback() {
            @Override protected void afterHookedMethod(HookCallback.Param p) {
                if (p.hasThrowable() || p.getResult() == null || shaderClass == null) return;
                try {
                    HookConfig.Area area = area(p.thisObject);
                    if (area == null || !area.tint) return;
                    Object replacement;
                    if("banner".equals(Reflect.getAdditionalInstanceField(p.thisObject,ROLE))) {
                        replacement=bannerMix(p.getResult(),area);
                    } else replacement=panelMix(p.getResult(),area);
                    p.setResult(replacement); hit("color");
                } catch (Throwable error) { failed("color", error); }
            }
        }));
        install("banner-motion", () -> Api102Runtime.findAndHookMethod(blur, "getMotionBlurMixConfig", new HookCallback() {
            @Override protected void afterHookedMethod(HookCallback.Param p) {
                if (p.hasThrowable() || p.getResult() == null || mixClass == null) return;
                try {
                    if (!"banner".equals(Reflect.getAdditionalInstanceField(p.thisObject, ROLE))) return;
                    HookConfig.Area area = area(p.thisObject);
                    if (area == null || !area.tint) return;
                    Object replacement=motionMix(p.getResult(),area); p.setResult(replacement); hit("banner-motion");
                } catch (Throwable error) { failed("banner-motion", error); }
            }
        }));
    }
    private static HookConfig.Area area(Object blur) {
        HookConfig snapshot = config;
        if (!snapshot.enabled) return null;
        Object role = Reflect.getAdditionalInstanceField(blur, ROLE);
        if ("banner".equals(role)) return snapshot.banner;
        if (!"panel".equals(role)) return null;
        Object nativeMix = Reflect.getObjectField(blur, "platformMixConfig");
        // Bouncer and global themes use other config types; leave those paths to the system.
        if (nativeMix == null || shaderConfigClass == null || !shaderConfigClass.isInstance(nativeMix)) return null;
        return snapshot.shade;
    }
    static HookConfig snapshot() { return config; }
    static void resourceHit(String key) { hit(key); }
    private static void capturePanel(Object owner) {
        try {
            Object controller=Reflect.callMethod(owner,"getScrimController");
            Object view=Reflect.callMethod(controller,"getScrimBehind");
            if(view==null)return;
            Object ext=Reflect.callMethod(view,"getExt");
            if(ext==null)return;
            Object proxy=Reflect.callMethod(ext,"getViewBlurProxy");
            if(proxy==null)return;
            Reflect.setAdditionalInstanceField(Reflect.callMethod(proxy,"getBlurConfig"),ROLE,"panel");
            proxies.put(proxy,true);hit("panel-owner");
        } catch(Throwable error) { failed("panel-owner",error); }
    }
    private static Object panelMix(Object original,HookConfig.Area area) {
        if(shaderConfigClass==null||!shaderConfigClass.isInstance(original))return original;
        Object nativeMix=Reflect.getAdditionalInstanceField(original,"pui-native-mix");
        if(nativeMix==null)nativeMix=original;
        Object ref=Reflect.getAdditionalInstanceField(nativeMix,"pui-cached-mix");
        Object cached=ref instanceof WeakReference ? ((WeakReference<?>)ref).get() : null;
        if(cached!=null && Reflect.getAdditionalInstanceField(nativeMix,"pui-cached-area")==area) {
            copyFlags(original,cached);return cached;
        }
        Object shader=Reflect.callMethod(nativeMix,"getBackgroundShaderParam");
        int nativeTop=(Integer)Reflect.callMethod(shader,"getTopLayerColor");
        int nativeBottom=(Integer)Reflect.callMethod(shader,"getBottomLayerColor");
        int alpha=area.color>>>24;
        int bottomAlpha=Math.min(255,Math.round((nativeBottom>>>24)*alpha/(float)Math.max(1,nativeTop>>>24)));
        Object replacementShader=alpha==0?Reflect.newInstance(shaderClass,0,0,0,0):Reflect.newInstance(shaderClass,
            Reflect.callMethod(shader,"getTopMode"),area.color,
            Reflect.callMethod(shader,"getBottomMode"),(nativeBottom&0xffffff)|(bottomAlpha<<24));
        Object replacement=Reflect.newInstance(shaderConfigClass,replacementShader);
        copyFlags(original,replacement);
        Reflect.setAdditionalInstanceField(replacement,"pui-native-mix",nativeMix);
        Reflect.setAdditionalInstanceField(nativeMix,"pui-cached-area",area);
        Reflect.setAdditionalInstanceField(nativeMix,"pui-cached-mix",new WeakReference<>(replacement));
        return replacement;
    }
    private static HookCallback livePanelScope(boolean controller) {
        return new HookCallback() {
            @Override protected void beforeHookedMethod(HookCallback.Param p) {
                if(!config.enabled||!config.shade.blur)return;
                Object state;
                if(controller) {
                    Object owner=Reflect.callMethod(p.thisObject,"getScrimController");
                    state=Reflect.callMethod(owner,"getState");
                } else {
                    Object view=Reflect.callMethod(p.thisObject,"getScrimView");
                    if(!(view instanceof View)||!isPanel((View)view))return;
                    state=Reflect.callMethod(p.thisObject,"getScrimState");
                }
                if(!"UNLOCKED".equals(String.valueOf(state)))return;
                int previous=livePanelDepth.get();p.setObjectExtra("live-depth",previous);livePanelDepth.set(previous+1);
            }
            @Override protected void afterHookedMethod(HookCallback.Param p) {
                Object previous=p.getObjectExtra("live-depth");
                if(previous!=null) { if((Integer)previous==0)livePanelDepth.remove();else livePanelDepth.set((Integer)previous); }
            }
        };
    }
    private static boolean isPanel(View view) {
        try { return view.getId()!=View.NO_ID&&"scrim_behind".equals(view.getResources().getResourceEntryName(view.getId())); }
        catch(android.content.res.Resources.NotFoundException ignored) { return false; }
    }
    private static void temporary(HookCallback.Param p,Object object,String field,Object value) {
        p.setObjectExtra(field,new Object[]{Reflect.getObjectField(object,field)});
        Reflect.setObjectField(object,field,value);
    }
    private static Object bannerMix(Object original,HookConfig.Area area) {
        if(original==null)return null;
        // Match the native shader's blend mode; mode=0 is EMPTY, not a colored overlay.
        if(!shaderConfigClass.isInstance(original))return original;
        Object nativeShader=Reflect.callMethod(original,"getBackgroundShaderParam");
        int mode=area.color>>>24==0?0:(Integer)Reflect.callMethod(nativeShader,"getTopMode");
        Object shader=Reflect.newInstance(shaderClass,mode,area.color>>>24==0?0:area.color,0,0);
        Object result=Reflect.newInstance(shaderConfigClass,shader);copyFlags(original,result);return result;
    }
    private static Object motionMix(Object original,HookConfig.Area area) {
        if(original==null)return null;
        Object mix=Reflect.callMethod(original,"getMixColor");
        Object replacement=Reflect.newInstance(motionConfigClass,Reflect.newInstance(mixClass,
            area.color>>>24==0?0:Reflect.callMethod(mix,"getMode"),area.color>>>24==0?0:area.color,0));
        copyFlags(original,replacement);return replacement;
    }
    private static void observed(String key,String value) {
        String previous=effective.put(key,value);hit(key);
        if(!value.equals(previous)) worker.postDelayed(ShadeHook::report,500);
    }
    private static void copyFlags(Object from, Object to) {
        Reflect.callMethod(to, "setAlphaWithBlurAmount", Reflect.callMethod(from, "getAlphaWithBlurAmount"));
        Reflect.callMethod(to, "setMirrorScale", Reflect.callMethod(from, "getMirrorScale"));
    }
    private static void readSettings(int attempt) {
        try {
            Bundle data = context.getContentResolver().call(URI, "settings", null, null);
            if (data == null) throw new IllegalStateException("No settings response");
            config = new HookConfig(data); settingsSource="provider fallback";
            main.post(ShadeHook::refreshProxies); report();
        } catch (Throwable error) {
            if (attempt < 4) worker.postDelayed(() -> readSettings(attempt+1), 2500);
            else failed("settings-read", error);
        }
    }
    private static void refreshProxies() {
        // Rebuild native cached render parameters after settings load or shared-panel scene changes.
        ArrayList<Object> current;
        synchronized (proxies) { current = new ArrayList<>(proxies.keySet()); }
        for (Object proxy : current) {
            try { Reflect.callMethod(proxy, "applyBlurConfig"); }
            catch (Throwable error) { failed("refresh", error); }
        }
    }
    private interface Install { void run() throws Throwable; }
    private static void install(String key, Install action) {
        try { action.run(); hooks.add(key); }
        catch (Throwable error) { failed(key, error); }
    }
    private static void hit(String key) {
        if (hits.add(key)) worker.postDelayed(ShadeHook::report, 500);
    }
    public static void frameworkFailure(String key, Throwable error) { failed("api102-"+key, error); }
    private static void failed(String key, Throwable error) {
        if (failures.add(key)) {
            failureDetails.put(key,String.valueOf(error));
            Api102Runtime.log("PUI-C17: "+key+": "+error);
            if (worker != null) worker.postDelayed(ShadeHook::report, 500);
        }
    }
    private static void report() {
        if (context == null) return;
        try {
            Bundle data = new Bundle();
            data.putString("hooks", String.join(", ", hooks));
            data.putString("hits", String.join(", ", hits));
            data.putString("error", failureDetails.toString().equals("{}")?"":failureDetails.toString());
            data.putString("effective","framework="+Api102Runtime.framework()+"; source="+settingsSource+"; enabled="+config.enabled+"; "+effective);
            data.putLong("revision", config.revision); data.putInt("pid", Process.myPid());
            data.putInt("hook_version", BuildConfig.VERSION_CODE);
            context.getContentResolver().call(URI, "report", null, data);
        } catch (Throwable error) { Api102Runtime.log("PUI-C17: status unavailable: "+error); }
    }
}
