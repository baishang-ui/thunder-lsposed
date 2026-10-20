package io.github.baishangui.thunder;

import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.view.View;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Feed the same G2 geometry to the firmware's path, outline, blur and press renderers. */
final class QsCornerHook {
    private static final String TILE="com.oplus.systemui.qs.base.res.drawable.";
    private static final String MEDIA="com.oplus.systemui.qs.media.";
    private static final String PLUGIN="com.oplus.systemui.plugins.drawable.PluginDrawable";
    private static final String NATIVE="thunder.qs-native-corner-provider";
    private static final String COPY="thunder.qs-custom-corner-provider";
    private static final String SLIDER="thunder.qs-corner-slider";
    private static final String DRAWING="thunder.qs-corner-drawing";
    private static final String NATIVE_RADIUS="thunder.qs-corner-native-radius";
    private static final String NATIVE_WEIGHT="thunder.qs-corner-native-weight";
    private static final String PRESS_ADAPTER="thunder.qs-corner-press-adapter";
    private static final String PRESS_BOUNDS="thunder.qs-corner-press-bounds";
    private static final String PRESS_SHAPE="thunder.qs-corner-press-shape";
    private static final String CARD="thunder.qs-corner-card";
    private static final String GEOMETRY_OWNER="thunder.qs-corner-owner";
    private static final String OUTLINE_PATH="thunder.qs-corner-outline-path";
    private static final String BLUR_PROVIDER="thunder.qs-corner-blur-provider";
    // C17 OplusQsSmoothRoundUtil's design-radius table uses G2 weight 3.
    // A single radius and weight must describe every surface, regardless of its native shape/size.
    static final float SMOOTH_WEIGHT=3f;
    private static final Map<Drawable,Boolean> surfaces=Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View,Boolean> pressViews=Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View,Boolean> mediaViews=Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View,Boolean> outlines=Collections.synchronizedMap(new WeakHashMap<>());
    private static final ArrayList<WeakReference<Object>> copies=new ArrayList<>();
    private static Class<?> roundRect;
    private static Class<?> pathAdapter;
    private static Class<?> spotLight;

    static void install(ClassLoader loader) {
        bind("native-provider",() -> roundRect=Reflect.findClass("com.oplusos.systemui.common.outline.RoundRectOutlineProvider",loader));
        bind("native-path-adapter",() -> pathAdapter=Reflect.findClass("com.oplus.graphics.OplusPathAdapter",loader));
        bind("native-spotlight",() -> spotLight=Reflect.findClass("com.oplus.systemui.qs.base.spotlight.SharedSpotLightEffect",loader));
        for(String name:new String[]{TILE+"TileDrawableWrapper",TILE+"TileLayerDrawable",PLUGIN}) {
            bind("owner-"+name,() -> Api102Runtime.hookAllConstructors(Reflect.findClass(name,loader),new HookCallback() {
                @Override protected void afterHookedMethod(Param p){
                    if(p.hasThrowable())return;
                    surfaces.put((Drawable)p.thisObject,true);
                    if(PLUGIN.equals(name))Reflect.setAdditionalInstanceField(p.thisObject,CARD,true);
                }
            }));
            bind("provider-"+name,() -> Api102Runtime.findAndHookMethod(name,loader,"getPathProvider",new HookCallback() {
                @Override protected void afterHookedMethod(Param p) {
                    if(p.hasThrowable())return;
                    surfaces.put((Drawable)p.thisObject,true);
                    if(PLUGIN.equals(name))rememberPluginOwner(p.getResult(),(Drawable)p.thisObject);
                    p.setResult(provider(p.getResult(),PLUGIN.equals(name)||Boolean.TRUE.equals(Reflect.getAdditionalInstanceField(p.thisObject,CARD)),(Drawable)p.thisObject));
                }
            }));
        }
        bind("cached-outline",() -> Api102Runtime.findAndHookMethod(roundRect,"getOutline",View.class,android.graphics.Outline.class,new HookCallback() {
            @Override protected void beforeHookedMethod(Param p) {
                View view=(View)p.args[0];
                Object nativeProvider=Reflect.getAdditionalInstanceField(p.thisObject,NATIVE);
                if(nativeProvider==null) {
                    // Device cards can retain a native provider obtained while customization was off.
                    // Invalidate and render that existing clip too, without waiting for reattachment.
                    Drawable owner=geometryOwner(p.thisObject);
                    if(owner==null)owner=cardBackground(view.getBackground());
                    if(owner==null)return;
                    outlines.put(view,true);
                    if(!enabled())return;
                    Object shape=provider(p.thisObject,true,owner);
                    if(owner.getBounds().isEmpty())Reflect.callMethod(shape,"getOutline",p.args);
                    else pathOutline(shape,owner,(android.graphics.Outline)p.args[1]);
                    p.setResult(null);
                    return;
                }
                outlines.put(view,true);
                if(!enabled()) {
                    Reflect.callMethod(nativeProvider,"getOutline",p.args);
                    p.setResult(null);
                    return;
                }
                update(p.thisObject,nativeProvider);
                Drawable owner=geometryOwner(p.thisObject);
                if(owner!=null&&!owner.getBounds().isEmpty()) {
                    pathOutline(p.thisObject,owner,(android.graphics.Outline)p.args[1]);
                    p.setResult(null);
                }
            }
        }));
        bind("plugin-host",() -> Api102Runtime.findAndHookMethod(
            "com.oplus.systemui.plugins.drawable.MixColorPluginDrawable$BuilderConfig",loader,
            "build",View.class,boolean.class,new HookCallback() {
                @Override protected void afterHookedMethod(Param p) {
                    if(p.hasThrowable()||!(p.getResult() instanceof Drawable))return;
                    Drawable drawable=(Drawable)p.getResult();
                    trackCard(drawable);
                    rememberPluginOwner(Reflect.callMethod(drawable,"getPathProvider"),drawable);
                    outlines.put((View)p.args[0],true);
                }
            }));
        // Mark the card's own layers, not the separate oval icon/three-stage indicator layers.
        String tileView="com.oplus.systemui.plugins.qs.customize.view.tile.OplusQSResizeableTileView";
        HookCallback cardOwner=new HookCallback() {
            @Override protected void afterHookedMethod(Param p) {if(!p.hasThrowable())trackTile(p.thisObject);}
        };
        bind("tile-card-owner",() -> Api102Runtime.hookAllConstructors(Reflect.findClass(tileView,loader),cardOwner));
        for(String size:new String[]{"OneXOne","TwoXTwo"})
            bind("tile-card-rebuild-"+size,() -> Api102Runtime.findAndHookMethod(tileView+size,loader,
                "onDrawableUpdate",java.util.List.class,boolean.class,cardOwner));
        HookCallback mediaOwner=new HookCallback() {
            @Override protected void afterHookedMethod(Param p) {if(!p.hasThrowable())trackMedia((View)p.thisObject);}
        };
        for(String method:new String[]{"onFinishInflate","onAttachedToWindow","onOutlineUpdate"})
            bind("media-"+method,() -> Api102Runtime.findAndHookMethod(MEDIA+"OplusQsBaseMediaPanelView",loader,method,mediaOwner));
        bind("media-rebuild",() -> Api102Runtime.findAndHookMethod(MEDIA+"OplusQsBaseMediaPanelView",loader,
            "onDrawableUpdate",boolean.class,mediaOwner));
        // The media light is an independent shader, outside TileLayerDrawable's path.
        // Adjust its native uniforms only for this draw and restore even on failure.
        bind("media-light",() -> Api102Runtime.findAndHookMethod(MEDIA+"multilight.OplusQsMediaBackgroundDrawable",loader,
            "draw",android.graphics.Canvas.class,new HookCallback() {
                @Override protected void beforeHookedMethod(Param p) {
                    if(!enabled())return;
                    Drawable background=(Drawable)p.thisObject;
                    Object light=Reflect.getObjectField(background,"mediaLightDrawable");if(light==null)return;
                    Object params=Reflect.getObjectField(light,"params");
                    float[] values=(float[])Reflect.getObjectField(params,"uCommonArray");
                    Object shape=provider(Reflect.callMethod(background,"getPathProvider"),true,background);
                    if(shape==null||!roundRect.isInstance(shape))return;
                    float radius=((Number)Reflect.callMethod(shape,"getCornerRadius")).floatValue();
                    boolean full=fullCorners(shape);
                    float[] saved=new float[5];System.arraycopy(values,6,saved,0,5);
                    p.setObjectExtra("media-light",new Object[]{params,values,saved,Reflect.getIntField(params,"uCornerType")});
                    for(int i=6;i<10;i++)values[i]=radius;
                    values[10]=full?1f:SMOOTH_WEIGHT;
                    Reflect.setIntField(params,"uCornerType",full?1:2);
                    dirtyMedia(params);
                    ShadeHook.resourceHit("qs-media-corners");
                }
                @Override protected void afterHookedMethod(Param p) {
                    Object state=p.getObjectExtra("media-light");if(!(state instanceof Object[]))return;
                    Object[] saved=(Object[])state;
                    System.arraycopy((float[])saved[2],0,(float[])saved[1],6,5);
                    Reflect.setIntField(saved[0],"uCornerType",(Integer)saved[3]);dirtyMedia(saved[0]);
                }
            }));
        bind("media-deopt",() -> Api102Runtime.deoptimizeCallers(Reflect.findClass(MEDIA+"OplusQsBaseMediaPanelView",loader),"onOutlineUpdate"));
        // Theme/state pools replace the child without replacing the already marked wrapper.
        bind("card-child-replaced",() -> Api102Runtime.findAndHookMethod(
            TILE+"TileDrawableWrapper",loader,"setDrawable",Drawable.class,new HookCallback() {
                @Override protected void afterHookedMethod(Param p) {
                    if(!p.hasThrowable()&&Boolean.TRUE.equals(Reflect.getAdditionalInstanceField(p.thisObject,CARD))) {
                        trackCard((Drawable)p.args[0]);
                        invalidateSurface((Drawable)p.thisObject);
                    }
                }
            }));
        bind("slider-press-radius",() -> Api102Runtime.findAndHookMethod("com.oplus.systemui.qs.base.seek.OplusQsVerticalSeekBar",loader,"getBackgroundRadius",new HookCallback() {
            @Override protected void afterHookedMethod(Param p){if(!p.hasThrowable()&&enabled())p.setResult(sliderBackgroundRadius(p.thisObject));}
        }));
        bind("slider-press-path",() -> Api102Runtime.findAndHookMethod(
            "com.oplus.systemui.qs.base.seek.OplusQsVerticalSeekBar",loader,"updateSpotLightPath",
            android.graphics.Path.class,Object.class,new HookCallback() {
                @Override protected void beforeHookedMethod(Param p) {
                    if(!enabled())return;
                    pressViews.put((View)p.thisObject,true);
                    // The stock overload uses the adapter's default weight; use the track's G2 weight explicitly.
                    Drawable bounds=(Drawable)Reflect.getAdditionalInstanceField(p.thisObject,PRESS_BOUNDS);
                    if(bounds==null) {
                        bounds=new android.graphics.drawable.ColorDrawable(0);
                        Reflect.setAdditionalInstanceField(p.thisObject,PRESS_BOUNDS,bounds);
                    }
                    bounds.setBounds((android.graphics.Rect)Reflect.callMethod(p.thisObject,"getBackgroundRect"));
                    float selected=sliderBackgroundRadius(p.thisObject);
                    float weight=SMOOTH_WEIGHT;
                    Object shape=Reflect.getAdditionalInstanceField(p.thisObject,PRESS_SHAPE);
                    if(shape==null) {
                        shape=Reflect.newInstance(roundRect,selected,weight);
                        Reflect.setAdditionalInstanceField(p.thisObject,PRESS_SHAPE,shape);
                    } else Reflect.callMethod(shape,"update",selected,weight);
                    Reflect.callMethod(shape,"updatePath",bounds,p.args[0],null,pressAdapter(p));
                    p.setResult(null);
                }
            }));
        // The view clip is cached separately from its drawable's path (notably 1x1 circles).
        bind("view-clip",() -> Api102Runtime.findAndHookMethod(
            "com.oplus.systemui.qs.base.widget.QsViewOutlineProvider$QsViewRoundRectOutlineProvider",loader,
            "getOutline",View.class,android.graphics.Outline.class,new HookCallback() {
                @Override protected void beforeHookedMethod(Param p) {
                    View view=(View)p.args[0];
                    Drawable background=cardBackground(view.getBackground());
                    if(!enabled()||background==null)return;
                    trackCard(background);
                    Object shape=provider(Reflect.callMethod(background,"getPathProvider"),
                        Boolean.TRUE.equals(Reflect.getAdditionalInstanceField(background,CARD)),background);
                    if(shape==null||roundRect==null||!roundRect.isInstance(shape))return;
                    outlines.put(view,true);
                    if(background.getBounds().isEmpty())Reflect.callMethod(shape,"getOutline",view,p.args[1]);
                    else pathOutline(shape,background,(android.graphics.Outline)p.args[1]);
                    p.setResult(null);
                }
            }));
        // Device/media card hosts can use a generic QS spotlight with a hard-coded capsule.
        for(String name:new String[]{"SpotLightFrameLayout","SpotLightLinearLayout","SpotLightConstraintLayout"})
            bind("press-"+name,() -> Api102Runtime.findAndHookMethod("com.oplus.systemui.qs.base.spotlight."+name,
                loader,"updateSpotLightPath",android.graphics.Path.class,Object.class,new HookCallback() {
                    @Override protected void beforeHookedMethod(Param p) {
                        if(!enabled())return;
                        View view=(View)p.thisObject;
                        Drawable background=cardBackground(view.getBackground());
                        if(background==null)return;
                        trackCard(background);
                        Object shape=provider(Reflect.callMethod(background,"getPathProvider"),
                            Boolean.TRUE.equals(Reflect.getAdditionalInstanceField(background,CARD)),background);
                        if(shape==null||roundRect==null||!roundRect.isInstance(shape))return;
                        pressViews.put(view,true);
                        ((android.graphics.Path)p.args[0]).rewind();
                        Reflect.callMethod(shape,"updatePath",background,p.args[0],null,pressAdapter(p));
                        p.setResult(null);
                    }
                }));
        for(String name:new String[]{"com.oplus.systemui.plugins.qs.customize.view.tile.OplusQSResizeableTileView",MEDIA+"OplusQsNormalMediaPanelView"}) {
            bind("tile-press-"+name,() -> Api102Runtime.findAndHookMethod(name,loader,
                "updateSpotLightPath",android.graphics.Path.class,Object.class,new HookCallback() {
                    @Override protected void beforeHookedMethod(Param p) {
                        if(!enabled())return;
                        Drawable background=(Drawable)Reflect.getObjectField(p.thisObject,"transitionDrawable");
                        Object shape=provider(Reflect.callMethod(background,"getPathProvider"),true,background);
                        if(shape==null||!roundRect.isInstance(shape))return;
                        pressViews.put((View)p.thisObject,true);
                        Reflect.callMethod(shape,"updatePath",background,p.args[0],null,pressAdapter(p));
                        p.setResult(null);
                    }
                }));
        }
        // These callers otherwise inline the small getPathProvider/getBackgroundRadius getters.
        for(String name:new String[]{TILE+"MixColorTileDrawable",TILE+"GradientTileDrawable",TILE+"TileLayerDrawable",
                "com.oplus.systemui.plugins.drawable.MixColorPluginDrawable"})
            bind("deopt-"+name,() -> Api102Runtime.deoptimizeCallers(Reflect.findClass(name,loader),"invalidatePath"));
        bind("deopt-smooth-adapter",() -> Api102Runtime.deoptimizeCallers(
            Reflect.findClass(TILE+"TileDrawableWrapper",loader),"ensureOplusPathAdapter"));
        for(String name:new String[]{"com.oplus.systemui.plugins.qs.customize.view.tile.OplusQSResizeableTileView",
                "com.oplus.systemui.qs.base.seek.OplusQsVerticalSeekBar",MEDIA+"OplusQsNormalMediaPanelView"})
            bind("deopt-press-"+name,() -> Api102Runtime.deoptimizeCallers(Reflect.findClass(name,loader),"updateSpotLightPath"));
    }
    private static void dirtyMedia(Object params) {
        Reflect.setBooleanField(params,"dirtyCommonArray",true);
        Reflect.setBooleanField(params,"dirtyConerType",true);
    }
    private static void trackMedia(View view) {
        mediaViews.put(view,true);
        for(String field:new String[]{"bgDrawable","themeBgDrawable","transitionDrawable"})
            trackCard((Drawable)Reflect.getObjectField(view,field));
        View background=(View)Reflect.callMethod(view,"getBg");
        if(background!=null){outlines.put(background,true);background.invalidateOutline();}
        view.invalidate();view.invalidateOutline();
    }
    private static Object pressAdapter(HookCallback.Param p) {
        if(p.args[1]!=null)return p.args[1];
        Object ref=Reflect.getAdditionalInstanceField(p.args[0],PRESS_ADAPTER);
        Object adapter=ref instanceof WeakReference?((WeakReference<?>)ref).get():null;
        if(adapter==null) {
            adapter=Reflect.newInstance(pathAdapter,p.args[0],1);
            Reflect.setAdditionalInstanceField(p.args[0],PRESS_ADAPTER,new WeakReference<>(adapter));
        }
        return adapter;
    }
    static Object smoothCornerType(Object original,Object shape) {
        String selected=fullCorners(shape)?"FULL":"G2";
        if(original instanceof Enum<?>)for(Object value:((Enum<?>)original).getDeclaringClass().getEnumConstants())
            if(selected.equals(((Enum<?>)value).name()))return value;
        return original;
    }
    static void trackTile(Object tile) {
        for(String field:new String[]{"bgDrawable","themeDrawable","transitionDrawable"})
            trackCard((Drawable)Reflect.getObjectField(tile,field));
    }
    static void trackCard(Drawable drawable) {
        if(drawable==null)return;
        boolean fresh=!Boolean.TRUE.equals(Reflect.getAdditionalInstanceField(drawable,CARD));
        Reflect.setAdditionalInstanceField(drawable,CARD,true);
        if(isCardBackground(drawable))surfaces.put(drawable,true);
        if(drawable instanceof android.graphics.drawable.DrawableWrapper)
            trackCard(((android.graphics.drawable.DrawableWrapper)drawable).getDrawable());
        else if(drawable instanceof android.graphics.drawable.LayerDrawable) {
            android.graphics.drawable.LayerDrawable layers=(android.graphics.drawable.LayerDrawable)drawable;
            for(int i=0;i<layers.getNumberOfLayers();i++)trackCard(layers.getDrawable(i));
        }
        if(fresh) {
            invalidateSurface(drawable);
            if(drawable.getClass().getName().equals(TILE+"MixColorTileDrawable")||
                    drawable.getClass().getName().equals("com.oplus.systemui.plugins.drawable.MixColorPluginDrawable")) {
                Object proxy=Reflect.callMethod(Reflect.getObjectField(drawable,"autoBlurDrawable"),"getViewBlurProxy");
                trackBlur(proxy,drawable);
                ShadeHook.trackQsCard(proxy);
            }
        }
    }
    private static void invalidateSurface(Drawable drawable) {
        if(isCardBackground(drawable))Reflect.callMethod(drawable,"invalidatePath");
        drawable.invalidateSelf();
    }
    static boolean enabled(){HookConfig c=ShadeHook.snapshot();return c.enabled&&c.qsCards.cornerEnabled;}
    // Use the same dp unit as notification cards; only small containers clamp the radius.
    static float radius(){return ShadeHook.snapshot().qsCards.corner*Resources.getSystem().getDisplayMetrics().density;}
    private static void rememberPluginOwner(Object shape,Drawable owner) {
        if(shape==null)return;
        Object nativeProvider=Reflect.getAdditionalInstanceField(shape,NATIVE);
        Reflect.setAdditionalInstanceField(nativeProvider==null?shape:nativeProvider,GEOMETRY_OWNER,new WeakReference<>(owner));
    }
    private static float sliderBackgroundRadius(Object view) {
        if(Boolean.TRUE.equals(Reflect.getAdditionalInstanceField(view,DRAWING)))return number(view,"mCurBackgroundRadius");
        return sliderRadius(view)*scale(number(view,"mCurBackgroundRadius"),number(view,"mBackgroundRadius"));
    }
    private static boolean isCardBackground(Drawable drawable) {
        if(drawable==null)return false;
        for(Class<?> type=drawable.getClass();type!=null;type=type.getSuperclass())
            if(type.getName().equals(TILE+"TileDrawableWrapper")||type.getName().equals(TILE+"TileLayerDrawable")||type.getName().equals(PLUGIN))return true;
        return false;
    }
    private static Drawable cardBackground(Drawable drawable) {
        for(int depth=0;drawable!=null&&depth<8;depth++) {
            if(isCardBackground(drawable))return drawable;
            drawable=drawable instanceof android.graphics.drawable.DrawableWrapper?
                ((android.graphics.drawable.DrawableWrapper)drawable).getDrawable():null;
        }
        return null;
    }

    private static void pathOutline(Object shape,Drawable background,android.graphics.Outline outline) {
        android.graphics.Path path=(android.graphics.Path)Reflect.getAdditionalInstanceField(background,OUTLINE_PATH);
        if(path==null) {
            path=new android.graphics.Path();
            Reflect.setAdditionalInstanceField(background,OUTLINE_PATH,path);
        }
        Object ref=Reflect.getAdditionalInstanceField(path,PRESS_ADAPTER);
        Object adapter=ref instanceof WeakReference?((WeakReference<?>)ref).get():null;
        if(adapter==null) {
            adapter=Reflect.newInstance(pathAdapter,path,1);
            Reflect.setAdditionalInstanceField(path,PRESS_ADAPTER,new WeakReference<>(adapter));
        }
        Reflect.callMethod(shape,"updatePath",background,path,null,adapter);
        outline.setPath(path);
    }
    private static Drawable geometryOwner(Object shape) {
        Object ref=Reflect.getAdditionalInstanceField(shape,GEOMETRY_OWNER);
        Object owner=ref instanceof WeakReference?((WeakReference<?>)ref).get():null;
        return owner instanceof Drawable?(Drawable)owner:null;
    }
    private static float selectedRadius(android.graphics.Rect bounds) {
        if(bounds==null||bounds.isEmpty())return radius(); // Native construction can precede layout.
        return Math.min(radius(),Math.min(bounds.width(),bounds.height())*.5f);
    }
    private static float sliderRadius(Object view) {
        return selectedRadius((android.graphics.Rect)Reflect.callMethod(view,"getBackgroundRect"));
    }
    private static Object provider(Object original,boolean card,Drawable owner) {
        if(original==null||roundRect==null)return original;
        Object source=Reflect.getAdditionalInstanceField(original,NATIVE);
        if(source!=null) {
            if(!enabled())return source;
            if(owner==null||owner==geometryOwner(original)) {
                update(original,source);
                return original;
            }
            original=source;
        }
        boolean oval=card&&original.getClass().getName().equals("com.oplusos.systemui.common.outline.OvalOutlineProvider");
        if(!roundRect.isInstance(original)&&!oval)return original;
        if(!enabled())return original;
        // Firmware providers (including OvalOutlineProvider.INSTANCE) are shared across sizes.
        // Cache the selected geometry on the drawable so one card cannot resize another's path.
        Object cache=owner==null?original:owner;
        Object ref=Reflect.getAdditionalInstanceField(cache,COPY);
        Object copy=ref instanceof WeakReference?((WeakReference<?>)ref).get():null;
        if(copy==null||Reflect.getAdditionalInstanceField(copy,NATIVE)!=original) {
            copy=Reflect.newInstance(roundRect,radius(),SMOOTH_WEIGHT);
            Reflect.setAdditionalInstanceField(copy,NATIVE,original);
            if(owner!=null)Reflect.setAdditionalInstanceField(copy,GEOMETRY_OWNER,new WeakReference<>(owner));
            Reflect.setAdditionalInstanceField(cache,COPY,new WeakReference<>(copy));
            synchronized(copies){copies.add(new WeakReference<>(copy));}
        }
        update(copy,original);
        return copy;
    }
    private static void update(Object copy,Object original) {
        if(!enabled()&&!roundRect.isInstance(original))return;
        Drawable owner=geometryOwner(copy);
        float selected=enabled()?selectedRadius(owner==null?null:owner.getBounds()):
            ((Number)Reflect.callMethod(original,"getCornerRadius")).floatValue();
        Object weight=enabled()?SMOOTH_WEIGHT:Reflect.callMethod(original,"getCornerWeight");
        if(((Number)Reflect.callMethod(copy,"getCornerRadius")).floatValue()!=selected||
                !java.util.Objects.equals(Reflect.callMethod(copy,"getCornerWeight"),weight))
            Reflect.callMethod(copy,"update",selected,weight);
    }
    static Object blurProvider(Object blur,Object original,float selected) {
        Drawable owner=geometryOwner(blur);
        Object shape=provider(original,owner==null||Boolean.TRUE.equals(Reflect.getAdditionalInstanceField(owner,CARD)),owner);
        if(shape!=null&&Reflect.getAdditionalInstanceField(blur,SLIDER)==null)return shape;
        if(owner!=null)selected=selectedRadius(owner.getBounds());
        // Seekbar configs normally have no provider and otherwise fall back to ordinary rounding.
        shape=Reflect.getAdditionalInstanceField(blur,BLUR_PROVIDER);
        if(shape==null) {
            shape=Reflect.newInstance(roundRect,selected,SMOOTH_WEIGHT);
            Reflect.setAdditionalInstanceField(blur,BLUR_PROVIDER,shape);
        } else Reflect.callMethod(shape,"update",selected,SMOOTH_WEIGHT);
        return shape;
    }
    static void trackBlur(Object proxy,Drawable owner) {
        Reflect.setAdditionalInstanceField(Reflect.callMethod(proxy,"getBlurConfig"),GEOMETRY_OWNER,new WeakReference<>(owner));
    }
    static float blurRadius(Object shape,float fallback) {
        return shape!=null&&roundRect.isInstance(shape)?((Number)Reflect.callMethod(shape,"getCornerRadius")).floatValue():fallback;
    }
    private static boolean fullCorners(Object shape) {
        Drawable owner=geometryOwner(shape);
        if(owner==null||owner.getBounds().isEmpty())return false;
        float half=Math.min(owner.getBounds().width(),owner.getBounds().height())*.5f;
        return blurRadius(shape,0f)>=half;
    }
    // The native G2 path becomes a circle/capsule at half the short edge. Its shader
    // must switch with it: G2 stroke corners at that limit cut away the outer rim.
    static float blurWeight(Object shape) {return fullCorners(shape)?1f:SMOOTH_WEIGHT;}
    static void beforeSlider(HookCallback.Param p) {
        if(!enabled()||Boolean.TRUE.equals(Reflect.getAdditionalInstanceField(p.thisObject,DRAWING)))return;
        p.setObjectExtra(DRAWING,new Object[]{Reflect.getAdditionalInstanceField(p.thisObject,DRAWING)});
        Reflect.setAdditionalInstanceField(p.thisObject,DRAWING,true);
        Reflect.setAdditionalInstanceField(p.thisObject,NATIVE_RADIUS,number(p.thisObject,"mCurProgressRadius"));
        Reflect.setAdditionalInstanceField(p.thisObject,NATIVE_WEIGHT,number(p.thisObject,"mBackgroundRoundCornerWeight"));
        float base=number(p.thisObject,"mBackgroundRadius"),progress=number(p.thisObject,"mProgressRadius");
        float selected=sliderRadius(p.thisObject),inner=Math.max(0f,selected-Math.max(0f,base-progress));
        temporary(p,"mCurBackgroundRadius",selected*scale(number(p.thisObject,"mCurBackgroundRadius"),base));
        temporary(p,"mCurProgressRadius",inner*scale(number(p.thisObject,"mCurProgressRadius"),progress));
        temporary(p,"mBackgroundRadius",selected);
        temporary(p,"mProgressRadius",inner);
        temporary(p,"mIsSupportSmoothRoundCorner",true);
        temporary(p,"mSmoothStyleType",1);
        temporary(p,"mBackgroundRoundCornerWeight",SMOOTH_WEIGHT);
        temporary(p,"mProgressRoundCornerWeight",SMOOTH_WEIGHT);
        for(String field:new String[]{"baseMixColorDrawable","activeMixColorDrawable"}) {
            Object drawable=Reflect.getObjectField(p.thisObject,field);
            if(drawable==null)continue;
            Object proxy=Reflect.callMethod(drawable,"getViewBlurProxy");
            trackSlider(proxy,(View)p.thisObject);
            Object blur=Reflect.callMethod(proxy,"getBlurConfig");
            float current=radiusForBlur(blur,selected);
            Object previous=Reflect.getAdditionalInstanceField(blur,"thunder.qs-applied-corner");
            if(!Float.valueOf(current).equals(previous)) {
                Reflect.callMethod(proxy,"applyBlurConfig");
                Reflect.setAdditionalInstanceField(blur,"thunder.qs-applied-corner",current);
            }
        }
    }
    static void trackSlider(Object proxy,View view) {
        Object blur=Reflect.callMethod(proxy,"getBlurConfig");
        Reflect.setAdditionalInstanceField(blur,SLIDER,new WeakReference<>(view));
    }
    static float nativeSliderRadius(View view,float fallback) {
        Object nativeRadius=Reflect.getAdditionalInstanceField(view,NATIVE_RADIUS);
        return nativeRadius instanceof Float?(Float)nativeRadius:fallback;
    }
    static float nativeSliderWeight(View view,float fallback) {
        Object nativeWeight=Reflect.getAdditionalInstanceField(view,NATIVE_WEIGHT);
        return nativeWeight instanceof Float?(Float)nativeWeight:fallback;
    }
    static float radiusForBlur(Object blur,float fallback) {
        Object role=Reflect.getAdditionalInstanceField(blur,"thunder.role");
        if(!"qs-card".equals(role)&&!"qs-progress".equals(role))return fallback;
        Object ref=Reflect.getAdditionalInstanceField(blur,SLIDER);
        Object view=ref instanceof WeakReference?((WeakReference<?>)ref).get():null;
        if(!enabled())return fallback;
        if(view==null)return selectedRadius(geometryOwner(blur)==null?null:geometryOwner(blur).getBounds());
        if(Boolean.TRUE.equals(Reflect.getAdditionalInstanceField(view,DRAWING)))return number(view,"mCurProgressRadius");
        float base=number(view,"mBackgroundRadius"),progress=number(view,"mProgressRadius");
        return Math.max(0f,sliderRadius(view)-Math.max(0f,base-progress))*scale(number(view,"mCurProgressRadius"),progress);
    }
    private static float scale(float current,float base){return base>0f?Math.max(0f,current/base):1f;}
    private static float number(Object owner,String field){return ((Number)Reflect.getObjectField(owner,field)).floatValue();}
    private static void temporary(HookCallback.Param p,String field,Object value) {
        p.setObjectExtra("qs-corner-"+field,Reflect.getObjectField(p.thisObject,field));
        Reflect.setObjectField(p.thisObject,field,value);
    }
    static void afterSlider(HookCallback.Param p) {
        for(String field:new String[]{"mBackgroundRadius","mCurBackgroundRadius","mProgressRadius","mCurProgressRadius",
                "mIsSupportSmoothRoundCorner","mSmoothStyleType","mBackgroundRoundCornerWeight","mProgressRoundCornerWeight"}) {
            Object saved=p.getObjectExtra("qs-corner-"+field);
            if(saved!=null)Reflect.setObjectField(p.thisObject,field,saved);
        }
        Object state=p.getObjectExtra(DRAWING);
        if(state instanceof Object[]) {
            Reflect.removeAdditionalInstanceField(p.thisObject,NATIVE_RADIUS);
            Reflect.removeAdditionalInstanceField(p.thisObject,NATIVE_WEIGHT);
            Object previous=((Object[])state)[0];
            if(previous==null)Reflect.removeAdditionalInstanceField(p.thisObject,DRAWING);
            else Reflect.setAdditionalInstanceField(p.thisObject,DRAWING,previous);
        }
    }
    static void refresh() {
        ArrayList<Object> currentCopies=new ArrayList<>();
        synchronized(copies) {
            copies.removeIf(ref -> ref.get()==null);
            for(WeakReference<Object> ref:copies){Object copy=ref.get();if(copy!=null)currentCopies.add(copy);}
        }
        for(Object copy:currentCopies)try{update(copy,Reflect.getAdditionalInstanceField(copy,NATIVE));}
        catch(Throwable error){ShadeHook.frameworkFailure("qs-corner-refresh-provider",error);}
        ArrayList<Drawable> current; synchronized(surfaces){current=new ArrayList<>(surfaces.keySet());}
        for(Drawable surface:current)try{Reflect.callMethod(surface,"invalidatePath");surface.invalidateSelf();}
        catch(Throwable error){ShadeHook.frameworkFailure("qs-corner-refresh-path",error);}
        ArrayList<View> media; synchronized(mediaViews){media=new ArrayList<>(mediaViews.keySet());}
        for(View view:media)try {
            Reflect.callMethod(view,"onOutlineUpdate");trackMedia(view);
        } catch(Throwable error){ShadeHook.frameworkFailure("qs-media-refresh",error);}
        ArrayList<View> currentViews; synchronized(outlines){currentViews=new ArrayList<>(outlines.keySet());}
        for(View view:currentViews){view.invalidateOutline();view.invalidate();}
        ArrayList<View> currentPressViews; synchronized(pressViews){currentPressViews=new ArrayList<>(pressViews.keySet());}
        for(View view:currentPressViews)try {
            if(spotLight!=null)spotLight.getDeclaredMethod("updateBounds",View.class,int.class,int.class)
                .invoke(null,view,view.getWidth(),view.getHeight());
            view.invalidate();
        } catch(Throwable error){ShadeHook.frameworkFailure("qs-corner-refresh-spotlight",error);}
    }
    private static void bind(String key,Runnable action) {
        try{action.run();ShadeHook.hookRegistered("qs-corner-"+key);}
        catch(Throwable error){ShadeHook.frameworkFailure("qs-corner-"+key,error);}
    }
}
