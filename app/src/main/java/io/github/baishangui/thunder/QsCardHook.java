package io.github.baishangui.thunder;

import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.DrawableWrapper;
import android.content.Context;
import android.view.View;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** C17 control-center tiles, media/plugin cards and their non-blur fallback backgrounds. */
final class QsCardHook {
    private static final String TILE="com.oplus.systemui.qs.base.res.drawable.";
    private static final String SEEK="com.oplus.systemui.qs.base.util.QsSeekBarBlurManager";
    private static final String BAR="com.oplus.systemui.qs.base.seek.OplusQsVerticalSeekBar";
    private static final String TOP_CARD="com.oplus.systemui.qs.base.tile.OplusQSHighlightTileView";
    private static final String RESIZEABLE_CARD="com.oplus.systemui.plugins.qs.customize.view.tile.OplusQSResizeableTileView";
    private static final String CARD_SURFACE="thunder.qs-card-surface";
    private static final String OWNER="thunder.qs-state-owner";
    private static final Map<Drawable,Boolean> drawables=Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View,Boolean> sliders=Collections.synchronizedMap(new WeakHashMap<>());
    private static int[] nativeStateAttrs;

    static void install(ClassLoader loader) {
        QsCornerHook.install(loader);
        installSliders(loader);
        installNotificationTopCards(loader);
        installControlCenterTwoXOne(loader);
        bind("states",() -> {
            ArrayList<Integer> attrs=new ArrayList<>();
            for(Object flag:Reflect.findClass("com.oplus.systemui.qs.base.res.model.TileViewFlag",loader).getEnumConstants()) {
                String name=((Enum<?>)flag).name();
                if("Active".equals(name)||"Unavailable".equals(name)||"Inoperable".equals(name))
                    attrs.add((Integer)Reflect.callMethod(flag,"getAttr"));
            }
            if(attrs.size()!=3)throw new IllegalStateException("Missing QS semantic states");
            nativeStateAttrs=attrs.stream().mapToInt(Integer::intValue).toArray();
        });
        for(String name:new String[]{TILE+"MixColorTileDrawable","com.oplus.systemui.plugins.drawable.MixColorPluginDrawable"}) {
            bind("owner-"+name,() -> Api102Runtime.hookAllConstructors(Reflect.findClass(name,loader),new HookCallback() {
                @Override protected void afterHookedMethod(Param p) {
                    if(p.hasThrowable())return;
                    Drawable drawable=(Drawable)p.thisObject;
                    drawables.put(drawable,true);
                    Object auto=Reflect.getObjectField(drawable,"autoBlurDrawable");
                    Object proxy=Reflect.callMethod(auto,"getViewBlurProxy");
                    if(name.equals(TILE+"MixColorTileDrawable"))
                        Reflect.setAdditionalInstanceField(Reflect.callMethod(proxy,"getBlurConfig"),OWNER,new WeakReference<>(drawable));
                    QsCornerHook.trackBlur(proxy,drawable);
                    ShadeHook.trackQsCard(proxy);
                }
            }));
        }
        bind("mask",() -> Api102Runtime.findAndHookMethod(TILE+"MixColorTileDrawable",loader,"draw",Canvas.class,new HookCallback() {
            @Override protected void beforeHookedMethod(Param p) {
                HookConfig c=ShadeHook.snapshot();if(!c.enabled||!c.qsCards.tint||preserveNativeState((Drawable)p.thisObject))return;
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
                HookConfig c=ShadeHook.snapshot();if(!c.enabled||!c.qsCards.tint||preserveNativeState((Drawable)p.thisObject))return;
                ColorDrawable fill=(ColorDrawable)Reflect.getObjectField(p.thisObject,"colorDrawable");
                p.setObjectExtra("fill",fill);p.setObjectExtra("color",fill.getColor());
                p.setObjectExtra("filter",fill.getColorFilter());
                p.setObjectExtra("callback",fill.getCallback());
                // setColor invalidates its wrapper. A temporary draw override must not schedule another frame.
                fill.setCallback(null);
                fill.clearColorFilter();
                int opacity=Math.max(0,Math.min(255,(Integer)Reflect.callMethod(p.thisObject,"getAlpha")));
                int alpha=Math.round((c.qsCards.color>>>24)*opacity/255f);
                fill.setColor((c.qsCards.color&0xffffff)|(alpha<<24));
                ShadeHook.resourceHit("qs-card-flat");
            }
            @Override protected void afterHookedMethod(Param p) {
                Object fill=p.getObjectExtra("fill");
                if(fill!=null) {
                    ColorDrawable drawable=(ColorDrawable)fill;
                    try {drawable.setColor((Integer)p.getObjectExtra("color"));
                        drawable.setColorFilter((android.graphics.ColorFilter)p.getObjectExtra("filter"));}
                    finally {drawable.setCallback((Drawable.Callback)p.getObjectExtra("callback"));}
                }
            }
        }));
    }
    private static void installNotificationTopCards(ClassLoader loader) {
        // StdQSResPool builds the notification header's 2x1 cards separately from the icon.
        HookCallback owner = new HookCallback() {
            @Override protected void afterHookedMethod(Param p) {
                if(!p.hasThrowable())trackNotificationTopCard(p.thisObject);
            }
        };
        bind("notification-top-owner",() -> Api102Runtime.hookAllConstructors(Reflect.findClass(TOP_CARD,loader),owner));
        bind("notification-top-attach",() -> Api102Runtime.findAndHookMethod(TOP_CARD,loader,"onAttachedToWindow",owner));
        bind("notification-top-rebuild",() -> Api102Runtime.findAndHookMethod(TOP_CARD,loader,"onDrawableUpdate",java.util.List.class,boolean.class,owner));
    }
    private static void trackNotificationTopCard(Object tile) {
        QsCornerHook.trackCard((Drawable)Reflect.getObjectField(tile,"mBgDrawable"));
        trackCardSurface((Drawable)Reflect.getObjectField(tile,"mBgDrawable"));
    }
    private static void installControlCenterTwoXOne(ClassLoader loader) {
        // Separate control center uses SepQSResPool and bgDrawable/themeDrawable, not mBgDrawable.
        HookCallback owner=new HookCallback() {
            @Override protected void afterHookedMethod(Param p) {
                if(p.hasThrowable())return;
                QsCornerHook.trackTile(p.thisObject);
                if(!isWideCard(p.thisObject))return;
                trackCardSurface((Drawable)Reflect.getObjectField(p.thisObject,"bgDrawable"));
                trackCardSurface((Drawable)Reflect.getObjectField(p.thisObject,"themeDrawable"));
            }
        };
        for(String name:new String[]{RESIZEABLE_CARD+"TwoXOne","com.oplus.systemui.plugins.qs.customize.view.tile.OplusQSResizeableThreeStageView"}) {
            bind("control-wide-owner-"+name,() -> Api102Runtime.hookAllConstructors(Reflect.findClass(name,loader),owner));
            bind("control-wide-rebuild-"+name,() -> Api102Runtime.findAndHookMethod(name,loader,"onDrawableUpdate",java.util.List.class,boolean.class,owner));
        }
        // Attachment is inherited by both wide-card implementations and other tile sizes.
        bind("control-wide-attach",() -> Api102Runtime.findAndHookMethod(RESIZEABLE_CARD,loader,"onAttachedToWindow",owner));
    }
    private static boolean isWideCard(Object tile) {
        for(Class<?> type=tile.getClass();type!=null;type=type.getSuperclass())
            if(type.getName().equals(RESIZEABLE_CARD+"TwoXOne")||type.getName().equals("com.oplus.systemui.plugins.qs.customize.view.tile.OplusQSResizeableThreeStageView"))return true;
        return false;
    }
    private static void trackCardSurface(Drawable drawable) {
        for(int depth=0;drawable!=null&&depth<8;depth++) {
            // Only the background chain is a card surface; icon/label state drawables stay native.
            Reflect.setAdditionalInstanceField(drawable,CARD_SURFACE,true);
            drawables.put(drawable,true);
            if(drawable.getClass().getName().equals(TILE+"MixColorTileDrawable")) {
                Object auto=Reflect.getObjectField(drawable,"autoBlurDrawable");
                ShadeHook.trackQsCard(Reflect.callMethod(auto,"getViewBlurProxy"));
            }
            drawable.invalidateSelf();
            drawable=drawable instanceof DrawableWrapper?((DrawableWrapper)drawable).getDrawable():null;
        }
    }
    private static void installSliders(ClassLoader loader) {
        bind("slider-progress",() -> Api102Runtime.findAndHookMethod(SEEK,loader,"getSeekBarActiveDrawable",
            View.class,Rect.class,float.class,float.class,boolean.class,boolean.class,float.class,new HookCallback() {
                @Override protected void beforeHookedMethod(Param p){
                    p.args[2]=QsCornerHook.nativeSliderRadius((View)p.args[0],(Float)p.args[2]);
                    p.args[3]=QsCornerHook.nativeSliderWeight((View)p.args[0],(Float)p.args[3]);
                }
                @Override protected void afterHookedMethod(Param p) {
                    if(p.hasThrowable()||p.getResult()==null)return;
                    sliders.put((View)p.args[0],true);
                    Object proxy=Reflect.callMethod(p.getResult(),"getViewBlurProxy");
                    QsCornerHook.trackSlider(proxy,(View)p.args[0]);
                    ShadeHook.trackQsProgress(proxy);
                }
            }));
        bind("slider-progress-update",() -> Api102Runtime.findAndHookMethod(SEEK,loader,"applySeekBarActiveBlurConfig",
            View.class,Rect.class,float.class,float.class,"com.oplusos.systemui.common.blurability.drawable.AutoBlurDrawable",
            boolean.class,float.class,boolean.class,new HookCallback() {
                @Override protected void beforeHookedMethod(Param p){
                    p.args[2]=QsCornerHook.nativeSliderRadius((View)p.args[0],(Float)p.args[2]);
                    p.args[3]=QsCornerHook.nativeSliderWeight((View)p.args[0],(Float)p.args[3]);
                    Object proxy=Reflect.callMethod(p.args[4],"getViewBlurProxy");
                    QsCornerHook.trackSlider(proxy,(View)p.args[0]);
                    ShadeHook.trackQsProgress(proxy);
                }
            }));
        // Brightness/volume tracks use AutoBlurDrawable directly, bypassing tile wrappers.
        bind("slider-background",() -> Api102Runtime.findAndHookMethod(SEEK,loader,"getSeekBarBackground",
            View.class,Rect.class,float.class,float.class,boolean.class,boolean.class,float.class,new HookCallback() {
                @Override protected void beforeHookedMethod(Param p){
                    p.args[2]=QsCornerHook.nativeSliderRadius((View)p.args[0],(Float)p.args[2]);
                    p.args[3]=QsCornerHook.nativeSliderWeight((View)p.args[0],(Float)p.args[3]);
                }
                @Override protected void afterHookedMethod(Param p) {
                    if(p.hasThrowable()||p.getResult()==null)return;
                    sliders.put((View)p.args[0],true);
                    QsCornerHook.trackSlider(Reflect.callMethod(p.getResult(),"getViewBlurProxy"),(View)p.args[0]);
                    trackSliderBackground(p.getResult());
                }
            }));
        bind("slider-update",() -> Api102Runtime.findAndHookMethod(SEEK,loader,"applySeekBarBgBlurConfig",
            Context.class,Rect.class,float.class,float.class,
            "com.oplusos.systemui.common.blurability.drawable.AutoBlurDrawable",boolean.class,boolean.class,float.class,new HookCallback() {
                @Override protected void beforeHookedMethod(Param p) {trackSliderBackground(p.args[4]);}
            }));
        bind("slider-flat",() -> Api102Runtime.findAndHookMethod(BAR,loader,"onDraw",Canvas.class,new HookCallback() {
            @Override protected void beforeHookedMethod(Param p) {
                sliders.put((View)p.thisObject,true);
                QsCornerHook.beforeSlider(p);
                HookConfig c=ShadeHook.snapshot();if(!c.enabled||!c.qsCards.tint||Reflect.getBooleanField(p.thisObject,"isDrawingWithMixColor"))return;
                p.setObjectExtra("background",Reflect.getIntField(p.thisObject,"mBackgroundColor"));
                p.setObjectExtra("track",new Object[]{Reflect.getObjectField(p.thisObject,"mInactiveTrackDrawable")});
                // Preserve native geometry, progress fill and icon; replace only the base track color.
                Reflect.setIntField(p.thisObject,"mBackgroundColor",c.qsCards.color);
                Reflect.setObjectField(p.thisObject,"mInactiveTrackDrawable",null);
                ShadeHook.resourceHit("qs-slider-flat");
            }
            @Override protected void afterHookedMethod(Param p) {
                QsCornerHook.afterSlider(p);
                Object saved=p.getObjectExtra("background");if(saved==null)return;
                Reflect.setIntField(p.thisObject,"mBackgroundColor",(Integer)saved);
                Reflect.setObjectField(p.thisObject,"mInactiveTrackDrawable",((Object[])p.getObjectExtra("track"))[0]);
            }
        }));
        bind("deopt-slider",() -> Api102Runtime.deoptimizeCallers(Reflect.findClass(BAR,loader),"onDraw","updateColor"));
    }
    private static void trackSliderBackground(Object drawable) {
        drawables.put((Drawable)drawable,true);
        ShadeHook.trackQsCard(Reflect.callMethod(drawable,"getViewBlurProxy"));
    }
    private static boolean preserveNativeState(Drawable drawable) {
        if(Boolean.TRUE.equals(Reflect.getAdditionalInstanceField(drawable,CARD_SURFACE)))return false;
        // Keep semantic colors if state discovery fails on a different firmware.
        if(nativeStateAttrs==null)return true;
        for(int state:drawable.getState())for(int attr:nativeStateAttrs)if(state==attr)return true;
        return false;
    }
    static boolean useCustomTint(Object blur) {
        Object ref=Reflect.getAdditionalInstanceField(blur,OWNER);
        Object owner=ref instanceof WeakReference?((WeakReference<?>)ref).get():null;
        return !(owner instanceof Drawable)||!preserveNativeState((Drawable)owner);
    }
    /** 0: unrelated; 1: shared card material; 2: native semantic state. */
    static int textSurface(Drawable drawable) {
        if(drawable==null||!drawables.containsKey(drawable))return 0;
        boolean tile=drawable.getClass().getName().startsWith(TILE);
        return tile&&preserveNativeState(drawable)?2:1;
    }
    static void refresh() {
        QsCornerHook.refresh();
        ArrayList<Drawable> current;
        synchronized(drawables){current=new ArrayList<>(drawables.keySet());}
        for(Drawable drawable:current)drawable.invalidateSelf();
        ArrayList<View> currentSliders;
        synchronized(sliders){currentSliders=new ArrayList<>(sliders.keySet());}
        for(View slider:currentSliders)slider.invalidate();
    }
    private static void bind(String key,Runnable action) {
        try {action.run();ShadeHook.hookRegistered("qs-card-"+key);}
        catch(Throwable error){ShadeHook.frameworkFailure("qs-card-"+key,error);}
    }
}
