package io.github.baishangui.thunder;

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
import android.graphics.Canvas;

/** SystemUI effect handlers installed via libxposed API 102; Compose stays in the app process. */
public final class ShadeHook {
    private static final String BLUR = "com.oplusos.systemui.common.blurability.";
    private static final String ROLE = "thunder.role";
    private static final Uri URI = Uri.parse("content://io.github.baishangui.thunder.settings");
    private static volatile HookConfig config = new HookConfig(null);
    private static volatile Object qsManager;
    private static volatile Context context;
    private static Handler worker, main;
    private static final Runnable reportTask = ShadeHook::report;
    private static boolean reportScheduled, reportUnavailable;
    private static long reportGeneration, reportRetryDelay = 2500;
    private static LiveSettings liveSettings;
    private static volatile boolean qsVisible;
    private static final Map<Object, Boolean> proxies = Collections.synchronizedMap(new WeakHashMap<>());
    private static boolean installed, initialized;
    private static String reportProcess = "com.android.systemui";
    private static final Set<String> hooks = ConcurrentHashMap.newKeySet();
    private static final Set<String> hits = ConcurrentHashMap.newKeySet();
    private static final Set<String> failures = ConcurrentHashMap.newKeySet();
    private static final Map<String,String> failureDetails = new ConcurrentHashMap<>();
    private static final Map<String,String> effective = new ConcurrentHashMap<>();
    private static final ThreadLocal<Integer> livePanelDepth = ThreadLocal.withInitial(() -> 0);
    private static String settingsSource = "unavailable";
    private static Class<?> shaderClass, shaderConfigClass, multiShaderConfigClass, mixClass, motionConfigClass;

    public static void install(String packageName, String processName, ClassLoader loader) {
        if (!"com.android.systemui".equals(packageName)
                || !"com.android.systemui".equals(processName) || installed) return;
        if (android.os.Build.VERSION.SDK_INT != 37) return;
        installed = true;
        initialize(processName);
        install("gesture-bar", () -> GestureBarHook.install(loader));
        install("status-bar-icons", () -> StatusBarIconHook.install(loader));
        install("status-bar-layout", () -> StatusBarLayoutHook.install(loader));
        install("status-bar-clock", () -> StatusBarClockHook.install(loader));
        install("status-bar-clock-transition", () -> StatusBarClockTransitionHook.install(loader));
        install("status-bar-ios-resources", () -> IosStatusBarHook.install(loader));
        install("qs-card-backgrounds", () -> QsCardHook.install(loader));
        install("volume-panel", () -> VolumeHook.install(loader));
        install("notification-backgrounds", () -> NotificationCardHook.install(loader));
        install("adaptive-panel-text", AdaptiveTextHook::install);
        install("cloud-plugin-loader", () -> CloudHook.watch(loader));
        install("cloud-lock-screen", () -> CloudLockHook.install(loader));
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
            multiShaderConfigClass = Reflect.findClass(BLUR+"BlurMixConfig$BlurMixMultiWithShader", loader);
            mixClass = Reflect.findClass(BLUR+"MixColor", loader);
            motionConfigClass = Reflect.findClass(BLUR+"BlurMixConfig$BlurMixSingle", loader);
        });
        install("panel-mirror-scale", () -> Api102Runtime.findAndHookMethod(
            BLUR+"BlurMixConfig",loader,"setMirrorScale",float.class,new HookCallback() {
                @Override protected void afterHookedMethod(HookCallback.Param p) {
                    if(p.hasThrowable())return;
                    Object original=nativeMix(p.thisObject);
                    // applyPanelMirrorScale writes through our getter replacement.
                    // Keep the source current before a later getter/cache refresh
                    // copies its flags back, including after tint is disabled.
                    if(original!=p.thisObject)Reflect.callMethod(original,"setMirrorScale",p.args[0]);
                }
            }));
        install("deopt-panel-mirror-scale", () -> Api102Runtime.deoptimizeCallers(Reflect.findClass(
            "com.oplusos.systemui.common.util.NotifiAndQsPlatformBlurExKt",loader),"applyPanelMirrorScale"));
        install("single-tint-alpha", () -> Api102Runtime.findAndHookMethod(
            "com.oplusos.systemui.common.util.ShaderBlendParamHelper",loader,
            "updateMixSingleShaderParams",shaderConfigClass,float.class,new HookCallback() {
                @Override protected void beforeHookedMethod(HookCallback.Param p) {
                    // Skip tint fading only when the whole capture already fades.
                    // Scaled panels keep native capture opacity and fade just tint;
                    // fading that capture would expose the unscaled background below.
                    if(Boolean.TRUE.equals(Reflect.getAdditionalInstanceField(p.args[0],"thunder-custom-tint"))
                            && Boolean.TRUE.equals(Reflect.callMethod(p.args[0],"getAlphaWithBlurAmount")))p.args[1]=1f;
                }
            }));
        // Replace before ViewBlurProxy caches producer configs; retain their native source for restore.
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
            "com.oplus.systemui.scrim.ScrimViewExImp",loader),"updateColorWithTint","setBlurAmount","setBlurAndScaleAmount","setViewAlpha","setColor"));
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
                    if (!"FULLSCREENBANNER".equals(type)&&!"NOTIFICATION".equals(type)&&!"CUSTOM".equals(type)&&!"CAPSULE".equals(type)) return;
                    try {
                        Object proxy = p.getResult();
                        Object blur = Reflect.callMethod(proxy, "getBlurConfig");
                        CloudLockHook.track((View)p.args[0],proxy,type,p.thisObject);
                        NotificationCardHook.trackBlur((View)p.args[0],blur);
                        String role="CAPSULE".equals(type)?"cloud-lock":"FULLSCREENBANNER".equals(type)?"banner":"notification-card";
                        Object base=Reflect.getAdditionalInstanceField(blur,"thunder.base-role");
                        // A repeated request while heads-up is active must not revert it to list styling.
                        if(!role.equals(base)||Reflect.getAdditionalInstanceField(blur,ROLE)==null) {
                            Reflect.setAdditionalInstanceField(blur,"thunder.base-role",role);
                            Reflect.setAdditionalInstanceField(blur,ROLE,role);
                            if(!"CAPSULE".equals(type))NotificationCardHook.track((View)p.args[0],role);
                            proxies.put(proxy,true);
                            Reflect.callMethod(proxy,"applyBlurConfig");
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
                                NotificationCardHook.track((View)p.args[0],"banner");
                                proxies.put(proxy, true);
                                hit("banner");
                            } else {
                                Object base=Reflect.getAdditionalInstanceField(blurConfig,"thunder.base-role");
                                if(base==null)Reflect.removeAdditionalInstanceField(blurConfig, ROLE);
                                else Reflect.setAdditionalInstanceField(blurConfig,ROLE,base);
                                NotificationCardHook.track((View)p.args[0],base==null?null:String.valueOf(base));
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
                    Object type=Reflect.callMethod(p.thisObject,"getBlurType");
                    String backend=type==null?"unknown":type.getClass().getSimpleName();
                    overrideNativeConfig(p,blur,backend);
                }
                @Override protected void afterHookedMethod(HookCallback.Param p) {
                    restoreNativeConfig(p);
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
                        p.setObjectExtra("thunder-overlay-enabled",Reflect.getBooleanField(p.thisObject,"isOverlayEnabled"));
                        p.setObjectExtra("thunder-overlay-color",config.shade.color);
                        Reflect.setBooleanField(p.thisObject,"isOverlayEnabled",false);
                        hit("wallpaper-dim");
                    } catch(Throwable error) { failed("wallpaper-dim",error); }
                }
                @Override protected void afterHookedMethod(HookCallback.Param p) {
                    Object enabled=p.getObjectExtra("thunder-overlay-enabled");
                    if(enabled==null)return;
                    Reflect.setBooleanField(p.thisObject,"isOverlayEnabled",(Boolean)enabled);
                    if(p.hasThrowable())return;
                    int color=(Integer)p.getObjectExtra("thunder-overlay-color");
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
                        boolean scales = "panel".equals(Reflect.getAdditionalInstanceField(p.thisObject, ROLE))
                            || Reflect.getBooleanField(p.thisObject, "scaleBlurRadiusWithAmount");
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
        install("panel-radius-scaling", () -> Api102Runtime.findAndHookMethod(blur,"getScaleBlurRadiusWithAmount",new HookCallback() {
            @Override protected void afterHookedMethod(HookCallback.Param p) {
                HookConfig.Area a=area(p.thisObject);
                if(!p.hasThrowable() && a!=null && a.blur && "panel".equals(Reflect.getAdditionalInstanceField(p.thisObject,ROLE)))p.setResult(true);
            }
        }));
        install("deopt-platform-radius", () -> Api102Runtime.deoptimizeCallers(Reflect.findClass(BLUR+"platformblur.PlatformBlurDrawable",loader),"applyBlurConfig"));
        install("panel-mask", () -> Api102Runtime.findAndHookMethod(blur,"getBlurColor",new HookCallback() {
            @Override protected void afterHookedMethod(HookCallback.Param p) {
                if(p.hasThrowable())return;
                try {
                    HookConfig.Area area=area(p.thisObject);
                    Object role=Reflect.getAdditionalInstanceField(p.thisObject,ROLE);
                    if(area!=null && area.tint && QsCardHook.useCustomTint(p.thisObject) && ("panel".equals(role)||"qs-card".equals(role)||"notification-card".equals(role)||"banner".equals(role)||"cloud-lock".equals(role)||String.valueOf(role).startsWith("volume-"))) {
                        // The chosen native mix is the dim layer; avoid an independent opaque mask on top.
                        Object backend=Reflect.getAdditionalInstanceField(p.thisObject,"thunder-backend");
                        boolean mask="BlurTypeStatic".equals(backend);
                        p.setResult(mask?area.color:0);hit("panel".equals(role)?"panel-dim":"qs-card-dim");
                    }
                } catch(Throwable error) { failed("panel-mask",error); }
            }
        }));
        install("color", () -> Api102Runtime.findAndHookMethod(blur, "getPlatformMixConfig", new HookCallback() {
            @Override protected void afterHookedMethod(HookCallback.Param p) {
                if (p.hasThrowable() || p.getResult() == null || shaderClass == null) return;
                try {
                    Object original=nativeMix(p.getResult());
                    p.setResult(original);
                    HookConfig.Area area = area(p.thisObject);
                    if (area == null || !area.tint || !QsCardHook.useCustomTint(p.thisObject)) return;
                    Object replacement;
                    if("banner".equals(Reflect.getAdditionalInstanceField(p.thisObject,ROLE)) ||
                            String.valueOf(Reflect.getAdditionalInstanceField(p.thisObject,ROLE)).startsWith("volume-")) {
                        replacement=backgroundMix(p.getResult(),area);
                    } else if("qs-card".equals(Reflect.getAdditionalInstanceField(p.thisObject,ROLE))||
                            "notification-card".equals(Reflect.getAdditionalInstanceField(p.thisObject,ROLE))||
                            "cloud-lock".equals(Reflect.getAdditionalInstanceField(p.thisObject,ROLE))) {
                        replacement=backgroundMix(p.getResult(),area);
                    } else replacement=panelMix(p.getResult(),area);
                    p.setResult(replacement); hit("color");
                } catch (Throwable error) { failed("color", error); }
            }
        }));
        install("banner-motion", () -> Api102Runtime.findAndHookMethod(blur, "getMotionBlurMixConfig", new HookCallback() {
            @Override protected void afterHookedMethod(HookCallback.Param p) {
                if (p.hasThrowable() || mixClass == null) return;
                try {
                    HookConfig.Area area = area(p.thisObject);
                    if (area == null || !area.tint || !QsCardHook.useCustomTint(p.thisObject)) return;
                    Object replacement=motionMix(p.getResult(),area); p.setResult(replacement); hit("banner-motion");
                } catch (Throwable error) { failed("banner-motion", error); }
            }
        }));
    }
    static void overrideNativeConfig(HookCallback.Param p,Object blur,String backend) {
        // Producer replacements may remain cached even after customization is disabled.
        p.setObjectExtra("config",blur);
        Object stored=Reflect.getObjectField(blur,"platformMixConfig");
        Object original=nativeMix(stored);
        if(original!=stored)temporary(p,blur,"platformMixConfig",original);
        HookConfig.Area area=area(blur);if(area==null)return;
        String role=String.valueOf(Reflect.getAdditionalInstanceField(blur,ROLE));
        Reflect.setAdditionalInstanceField(blur,"thunder-backend",backend);
        NotificationCardHook.overrideCorners(p,blur,area);
        if(area.blur) {
            temporary(p,blur,"blurRadius",area.radius);
            temporary(p,blur,"motionBlurRadius",area.radius);
            if("panel".equals(role))temporary(p,blur,"scaleBlurRadiusWithAmount",true);
        }
        if(area.tint && QsCardHook.useCustomTint(blur)) {
            Object platform=Reflect.getObjectField(blur,"platformMixConfig");
            temporary(p,blur,"platformMixConfig","panel".equals(role)?panelMix(platform,area):backgroundMix(platform,area));
            temporary(p,blur,"motionBlurMixConfig",motionMix(Reflect.getObjectField(blur,"motionBlurMixConfig"),area));
        }
        observed(role+"-native",backend+" radius="+(area.blur?area.radius:"system")+" ARGB="+(area.tint?Integer.toHexString(area.color):"system"));
    }
    static void restoreNativeConfig(HookCallback.Param p) {
        Object blur=p.getObjectExtra("config");if(blur==null)return;
        for(String field:new String[]{"blurRadius","motionBlurRadius","platformMixConfig","motionBlurMixConfig","scaleBlurRadiusWithAmount","cornerRadius","leftTopCornerRadius","rightTopCornerRadius",
                "leftBottomCornerRadius","rightBottomCornerRadius","pathProvider","gradientStrokeCornerParam","radiusWeight","enableMotionSmoothCorner"}) {
            Object saved=p.getObjectExtra(field);
            if(saved instanceof Object[])Reflect.setObjectField(blur,field,((Object[])saved)[0]);
        }
    }
    private static HookConfig.Area area(Object blur) {
        HookConfig snapshot = config;
        if (!snapshot.enabled) return null;
        HookConfig.Area cloudLock=CloudLockHook.area(blur,snapshot);
        if(cloudLock!=null)return cloudLock;
        Object role = Reflect.getAdditionalInstanceField(blur, ROLE);
        if ("volume-bar".equals(role)) return snapshot.volumeBar;
        if ("volume-buttons".equals(role)) return snapshot.volumeBar;
        if ("volume-panel".equals(role)) return snapshot.volumePanel;
        if ("banner".equals(role)) return snapshot.banner;
        if ("qs-card".equals(role)) return snapshot.qsCards;
        if ("qs-progress".equals(role)) return snapshot.qsProgress;
        if ("notification-card".equals(role)) return snapshot.notificationCard;
        if (!"panel".equals(role)) return null;
        Object nativeMix = Reflect.getObjectField(blur, "platformMixConfig");
        // Bouncer and global themes use other config types; leave those paths to the system.
        if (nativeMix == null || shaderConfigClass == null || !shaderConfigClass.isInstance(nativeMix)) return null;
        return snapshot.shade;
    }
    private static synchronized void initialize(String processName) {
        if (initialized) return;
        initialized = true;
        reportProcess = processName;
        HandlerThread thread = new HandlerThread("Thunder-settings");
        thread.start(); worker = new Handler(thread.getLooper()); main = new Handler(Looper.getMainLooper());
        // Framework-owned storage is available before the target Application exists.
        // This avoids the old provider startup race and stale dimension caches.
        install("remote-settings", () -> {
            liveSettings = new LiveSettings(Api102Runtime.remotePreferences(), worker, values -> {
                Bundle data = new Bundle();
                for (Map.Entry<String, ?> entry : values.entrySet()) {
                    Object value = entry.getValue(); String key = entry.getKey();
                    if (value instanceof Boolean) data.putBoolean(key, (Boolean) value);
                    else if (value instanceof Integer) data.putInt(key, (Integer) value);
                    else if (value instanceof Long) data.putLong(key, (Long) value);
                }
                config = new HookConfig(data); settingsSource = "API102 live";
                failures.remove("remote-settings"); failureDetails.remove("remote-settings");
                main.post(() -> {
                    refreshProxies();
                    StatusBarIconHook.refresh();
                    StatusBarLayoutHook.refresh();
                    StatusBarClockHook.refresh();
                    GestureBarHook.refresh();
                    requestReport();
                });
            }, error -> failed("remote-settings", error));
            liveSettings.start();
        });
        install("settings", () -> Api102Runtime.findAndHookMethod(Application.class, "attach", Context.class,
            new HookCallback() {
                @Override protected void afterHookedMethod(HookCallback.Param p) {
                    context = ((Context) p.args[0]).getApplicationContext();
                    if (context == null) context = (Context) p.args[0];
                    if ("com.android.systemui".equals(reportProcess)) {
                        try { IosStatusBarHook.prepare(context); }
                        catch (Exception error) { failed("status-bar-ios-resources", error); }
                    }
                    if ("com.android.systemui".equals(reportProcess)) AdaptiveTextHook.startWallpaper(context, worker, main);
                    // API 102 config is already loaded. Provider is only a fallback
                    // and a diagnostic return channel; never block SystemUI startup.
                    if(config.revision<0) worker.post(() -> readSettings(0));
                    else requestReport();
                }
            }));
        install("resource-overrides", ResourceOverrides::install);
    }
    static void installCloud(String packageName, String processName, ClassLoader loader) {
        if (!"com.oplus.systemui.plugins".equals(packageName)
                || !("com.android.systemui".equals(processName) || "com.oplus.systemui.plugins".equals(processName))
                || android.os.Build.VERSION.SDK_INT != 37) return;
        initialize(processName);
        install("cloud-package-entry", () -> CloudHook.install(loader));
    }
    static void hookRegistered(String key) {
        if (hooks.add(key)) requestReport();
    }
    static HookConfig snapshot() { return config; }
    static void trackVolume(Object proxy,String role) {
        Reflect.setAdditionalInstanceField(Reflect.callMethod(proxy,"getBlurConfig"),ROLE,role);
        proxies.put(proxy,true);Reflect.callMethod(proxy,"applyBlurConfig");
    }
    static void trackQsCard(Object proxy) {
        Reflect.setAdditionalInstanceField(Reflect.callMethod(proxy,"getBlurConfig"),ROLE,"qs-card");
        proxies.put(proxy,true);
        Reflect.callMethod(proxy,"applyBlurConfig");
    }
    static void trackQsProgress(Object proxy) {
        Reflect.setAdditionalInstanceField(Reflect.callMethod(proxy,"getBlurConfig"),ROLE,"qs-progress");
        proxies.put(proxy,true);
        Reflect.callMethod(proxy,"applyBlurConfig");
    }
    static void resourceHit(String key) { hit(key); }
    static void statusIconsObserved(String slots) {
        observed("status-bar-icons", IosStatusBarHook.state() + (slots.isEmpty() ? "" : ";hidden="+slots));
    }
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
        original=nativeMix(original);
        if(shaderConfigClass==null||!shaderConfigClass.isInstance(original))return original;
        Object replacement=backgroundMix(original,area);
        // C17 disables capture fading while mirror scaling is active. Retain
        // that policy on every cache read, independently of the selected tint.
        Reflect.callMethod(replacement,"setAlphaWithBlurAmount",Reflect.callMethod(original,"getAlphaWithBlurAmount"));
        return replacement;
    }
    private static Object nativeMix(Object mix) {
        if(mix==null)return null;
        Object original=Reflect.getAdditionalInstanceField(mix,"thunder-native-mix");
        return original==null?mix:original;
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
        if(p.getObjectExtra(field)==null)p.setObjectExtra(field,new Object[]{Reflect.getObjectField(object,field)});
        Reflect.setObjectField(object,field,value);
    }
    private static Object backgroundMix(Object original,HookConfig.Area area) {
        original=nativeMix(original);
        if(original==null)return null;
        boolean multi=multiShaderConfigClass!=null&&multiShaderConfigClass.isInstance(original);
        // CUSTOM cards can use compositor-only Single/Multi configurations. They
        // still need the selected material; leaving these unchanged silently
        // discards the lock-screen tint on those firmware paths.
        boolean plain=motionConfigClass!=null&&motionConfigClass.isInstance(original)
            || original.getClass().getName().equals(BLUR+"BlurMixConfig$BlurMixMulti");
        if(!multi&&!shaderConfigClass.isInstance(original)&&!plain)return original;
        Object ref=Reflect.getAdditionalInstanceField(original,"thunder-background-mix");
        Object cached=ref instanceof WeakReference ? ((WeakReference<?>)ref).get() : null;
        if(cached!=null&&Reflect.getAdditionalInstanceField(original,"thunder-background-area")==area) {
            customFlags(original,cached);return cached;
        }
        int color=area.color>>>24==0?0:area.color;
        // C17 AGSL MIX interpolates straight RGB by the selected alpha.
        int mode=color==0?0:1;
        Object tint=Reflect.newInstance(shaderClass,mode,color,0,0);
        Object result;
        if(multi) {
            Object empty=Reflect.newInstance(shaderClass,0,0,0,0);
            result=Reflect.newInstance(multiShaderConfigClass,tint,empty);
        } else result=Reflect.newInstance(shaderConfigClass,tint);
        Reflect.callMethod(result,"setPlaceHolderColor",color);
        customFlags(original,result);
        Reflect.setAdditionalInstanceField(result,"thunder-native-mix",original);
        Reflect.setAdditionalInstanceField(result,"thunder-custom-tint",true);
        Reflect.setAdditionalInstanceField(original,"thunder-background-area",area);
        Reflect.setAdditionalInstanceField(original,"thunder-background-mix",new WeakReference<>(result));
        return result;
    }
    private static Object motionMix(Object original,HookConfig.Area area) {
        // OplusBlurParam has its own enum: 1=COLORMIX, 4=GLOW_OVERLAY.
        // Keep the tint in the compositor; BackgroundBlurDrawable.setColor is a
        // separate paint drawn before the RenderNode and does not follow setAlpha.
        int color=area.color>>>24==0?0:area.color;
        Object replacement=Reflect.newInstance(motionConfigClass,
            Reflect.newInstance(mixClass,color==0?0:1,color,0));
        customFlags(original,replacement);return replacement;
    }
    private static void customFlags(Object original,Object replacement) {
        if(original!=null)copyFlags(original,replacement);
        Reflect.callMethod(replacement,"setAlphaWithBlurAmount",true);
    }
    static void observed(String key,String value) {
        String previous=effective.put(key,value);hit(key);
        if(!value.equals(previous)) requestReport();
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
            main.post(() -> { refreshProxies(); StatusBarIconHook.refresh(); StatusBarLayoutHook.refresh(); StatusBarClockHook.refresh(); GestureBarHook.refresh(); }); requestReport();
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
        QsCardHook.refresh();
        NotificationCardHook.refresh();
        AdaptiveTextHook.refresh();
        CloudHook.refresh();
        CloudLockHook.refresh();
        CloudSdkHook.refresh();
        CloudTemplateHook.refresh();
        VolumeHook.refresh();
    }
    private interface Install { void run() throws Throwable; }
    private static void install(String key, Install action) {
        try { action.run(); hooks.add(key); }
        catch (Throwable error) { failed(key, error); }
    }
    private static void hit(String key) {
        if (hits.add(key)) requestReport();
    }
    public static void frameworkFailure(String key, Throwable error) { failed("api102-"+key, error); }
    private static void failed(String key, Throwable error) {
        if (failures.add(key)) {
            failureDetails.put(key,String.valueOf(error));
            Api102Runtime.log("Thunder: "+key+": "+error);
            requestReport();
        }
    }
    private static synchronized void requestReport() {
        if (worker == null || context == null) return;
        reportGeneration++;
        // Keep the first deadline, including a retry deadline during an outage.
        if (reportScheduled) return;
        reportScheduled = true;
        worker.postDelayed(reportTask, 500);
    }
    private static void report() {
        long generation;
        synchronized (ShadeHook.class) { generation = reportGeneration; }
        try {
            Bundle data = new Bundle();
            data.putString("process", reportProcess);
            data.putString("hooks", String.join(", ", hooks));
            data.putString("hits", String.join(", ", hits));
            data.putString("error", failureDetails.toString().equals("{}")?"":failureDetails.toString());
            data.putString("effective","framework="+Api102Runtime.framework()+"; source="+settingsSource+"; enabled="+config.enabled+"; "+effective);
            data.putLong("revision", config.revision); data.putInt("pid", Process.myPid());
            data.putInt("hook_version", BuildConfig.VERSION_CODE);
            Bundle ack = context.getContentResolver().call(URI, "report", null, data);
            if (ack == null) throw new IllegalStateException("No runtime report acknowledgement");
            synchronized (ShadeHook.class) {
                reportUnavailable = false;
                reportRetryDelay = 2500;
                // State may change on the main thread while Binder IPC is in flight.
                if (generation != reportGeneration) worker.postDelayed(reportTask, 500);
                else reportScheduled = false;
            }
        } catch (Throwable error) {
            synchronized (ShadeHook.class) {
                if (!reportUnavailable) {
                    Api102Runtime.log("Thunder: status unavailable (retrying): " + error);
                    reportUnavailable = true;
                }
                // Read a fresh snapshot on each retry. Keep trying quietly across
                // boot/unlock and module startup, without requiring another hook hit.
                worker.postDelayed(reportTask, reportRetryDelay);
                reportRetryDelay = Math.min(60000, reportRetryDelay * 2);
            }
        }
    }
}
