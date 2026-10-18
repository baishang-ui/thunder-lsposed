package io.github.baishangui.thunder;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.DrawableWrapper;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.LayerDrawable;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.content.res.ColorStateList;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** ColorOS 17: change native geometry/material inputs, retain the complete native transition. */
final class VolumeHook {
    private static final String BASE="com.oplus.systemui.volume.", MATERIAL=BASE+"utils.material.";
    private static final Map<Object,Boolean> dialogs=Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object,Compact> panels=Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View,int[]> layouts=Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View,Map<String,Object>> barDefaults=Collections.synchronizedMap(new WeakHashMap<>());
    private static final String[] MORPH_FIELDS={"mCurBackgroundWidth","mCurProgressWidth","mCurBackgroundRadius","mCurProgressRadius","mCurPaddingVertical"};
    private static final String[] ACCESSORIES={"mVolumePanelTitleIcon","mVolumePanelTitleText","mVolumePanelTipsText","mSettingsButton","mVolumeSettingText"};
    private static final ThreadLocal<Boolean> delayedEntrance=new ThreadLocal<>();
    private static final float PANEL_SMOOTH_WEIGHT=3f;
    private static Method panelClipBuilder;
    private static Class<?> panelFillType;

    static void install(ClassLoader loader) {
        bind("smooth-panel",()->{
            try {panelClipBuilder=Reflect.findClass(MATERIAL+"OplusVolumeShapePathHelper",loader).getMethod(
                    "buildClipPath",Path.class,Rect.class,float.class,float.class,boolean.class);}
            catch(NoSuchMethodException error) {throw new IllegalStateException("Native panel shape API",error);}
            panelFillType=Reflect.findClass("com.oplusos.systemui.common.view.SmoothCornersGradientDrawable",loader);
        });
        bind("view",()->Api102Runtime.hookAllConstructors(Reflect.findClass(BASE+"view.OplusVolumeDialogView",loader),new HookCallback() {
            @Override protected void afterHookedMethod(Param p) { if(!p.hasThrowable())dialogs.put(p.thisObject,true); }
        }));
        hook(loader,BASE+"view.OplusVolumeDialogView",new String[]{"initDialog"},new HookCallback() {
            @Override protected void beforeHookedMethod(Param p) { VolumeLiquidEntrance.finishDialog(p.thisObject);restorePanel(p.thisObject); }
            @Override protected void afterHookedMethod(Param p) { if(!p.hasThrowable())updateDialog(p.thisObject); }
        });
        hook(loader,BASE+"view.OplusVolumeDialogView",new String[]{"updateRowsH","showSliderVolume","updateState","adaptThemeDrawable"},new HookCallback() {
            @Override protected void afterHookedMethod(Param p) { if(!p.hasThrowable())updateDialog(p.thisObject); }
        });
        hook(loader,BASE+"view.OplusVolumeDialogView",new String[]{"getIconHalfDelta"},new HookCallback() {
            @Override protected void afterHookedMethod(Param p) {
                Compact panel=panels.get(p.thisObject);HookConfig c=panel==null?ShadeHook.snapshot():panel.config;
                View button=view(p.thisObject,"mMoreRowStreamLl");
                if(button!=null&&(panel!=null||(c.enabled&&c.volumeSize)))
                    p.setResult((panel==null?dp(button,c.volumeWidth):panel.barWidth())/2f);
            }
        });
        // Never cancel either expandPanel method. mExpanded, controller flags, row adapter,
        // PanelTransitionSession, spring interpolators and interruption handoff remain native.
        hook(loader,BASE+"view.OplusVolumeDialogView",new String[]{"expandPanel"},new HookCallback() {
            @Override protected void beforeHookedMethod(Param p) {
                VolumeLiquidEntrance.finishDialog(p.thisObject);
                if(panels.containsKey(p.thisObject))return;
                HookConfig c=ShadeHook.snapshot();if(!c.enabled||!c.volumeCompact)return;
                try { preparePanel(p.thisObject); }
                catch(Throwable error) {
                    ShadeHook.frameworkFailure("volume-native-panel",error);
                    java.io.StringWriter trace=new java.io.StringWriter();
                    error.printStackTrace(new java.io.PrintWriter(trace));
                    Api102Runtime.log("Thunder: volume-native-panel initialization\n"+trace);
                    restorePanel(p.thisObject);
                }
            }
            @Override protected void afterHookedMethod(Param p) {
                Compact panel=panels.get(p.thisObject);if(panel!=null)panel.hideAccessories();
            }
        });
        hook(loader,BASE+"anim.PanelExpandPreludeAnimator",new String[]{"start","collapseForPanelDismiss"},new HookCallback() {
            @Override protected void beforeHookedMethod(Param p) {
                for(Compact panel:panels.values()) {
                    Object d=panel.dialog.get();
                    if(d!=null&&Reflect.getObjectField(d,"panelExpandPrelude")==p.thisObject) {
                        // Compact expansion hands the existing buttons to the moving row.
                        // The fullscreen prelude would shrink them upwards before that handoff.
                        Reflect.callMethod(p.thisObject,"cancelRunningAnimationOnly");p.setResult(null);return;
                    }
                }
            }
        });
        hook(loader,BASE+"view.OplusVolumeDialogView",new String[]{"startBtnThickAnim"},new HookCallback() {
            @Override protected void beforeHookedMethod(Param p) {if(panels.containsKey(p.thisObject))p.setResult(null);}
        });
        hook(loader,BASE+"view.OplusVolumeDialogView",new String[]{"showOrHideAnimation"},new HookCallback() {
            @Override protected void beforeHookedMethod(Param p) {
                VolumeLiquidEntrance.finishDialog(p.thisObject);
                Compact panel=panels.get(p.thisObject);
                if(panel!=null) {panel.dismissing=!Boolean.TRUE.equals(p.args[1]);panel.dismissOpacity=1f;}
            }
        });
        hook(loader,"com.coui.appcompat.pressfeedback.COUIPressFeedbackHelper",new String[]{"executeFeedbackAnimator"},new HookCallback() {
            @Override protected void beforeHookedMethod(Param p) {
                if(Boolean.TRUE.equals(Reflect.getAdditionalInstanceField(p.thisObject,"volume.shortcut-press")))p.setResult(null);
            }
        });
        hook(loader,"com.oplus.systemui.plugins.qs.customize.view.tile.OplusQSThreeStageLayout",new String[]{"getActiveIconColor"},new HookCallback() {
            @Override protected void afterHookedMethod(Param p) {
                if(p.hasThrowable()||!Boolean.TRUE.equals(p.args[0]))return;
                for(Compact panel:panels.values())if(panel.soundSegments==p.thisObject) {
                    p.setResult(panel.selectedIconColor(((Number)p.getResult()).intValue()));return;
                }
            }
        });
        hook(loader,BASE+"anim.VolumeItemAnimator",new String[]{"animateAddImpl","animateMoveImpl"},new HookCallback() {
            @Override protected void beforeHookedMethod(Param p) {
                delayedEntrance.remove();
                if(p.args.length==0)return;
                View item=(View)Reflect.getObjectField(p.args[0],"itemView");Compact panel=forItem(item);
                if(panel!=null&&((Number)Reflect.callMethod(Reflect.getObjectField(p.thisObject,"adapter"),"getCurrentDataSource")).intValue()==1
                        &&!panel.isSource(item))delayedEntrance.set(true);
            }
            @Override protected void afterHookedMethod(Param p) {delayedEntrance.remove();}
        });
        hook(loader,"android.view.ViewPropertyAnimator",new String[]{"setStartDelay"},new HookCallback() {
            @Override protected void beforeHookedMethod(Param p) {
                // Keep vendor springs, staggering and completion listeners; only stage new rows
                // after the original bar/button pair has begun its horizontal movement.
                if(Boolean.TRUE.equals(delayedEntrance.get()))p.args[0]=((Number)p.args[0]).longValue()+99L;
            }
        });
        hook(loader,BASE+"VolumeListAdapter",new String[]{"switchToDataSourcePanel"},new HookCallback() {
            @Override protected void afterHookedMethod(Param p) {
                Compact panel=forAdapter(p.thisObject);if(panel!=null)panel.arrange(p.thisObject);
            }
        });
        hook(loader,BASE+"VolumeListAdapter",new String[]{"onBindViewHolder"},new HookCallback() {
            @Override protected void afterHookedMethod(Param p) {
                if(p.hasThrowable())return;
                View item=(View)Reflect.getObjectField(p.args[0],"itemView");
                HookConfig c=ShadeHook.snapshot();Compact panel=forItem(item);
                size(item,panel==null?dp(item,c.volumeWidth+10):panel.rowWidth,0,panel!=null||(c.enabled&&c.volumeSize),true,false);
                Object row=Reflect.callMethod(p.thisObject,"getItem",((Number)p.args[1]).intValue());
                styleRow(row,panel==null?c:panel.config);
                int labelId=item.getResources().getIdentifier("volume_text","id","com.android.systemui");
                View label=labelId==0?null:item.findViewById(labelId);
                if(panel!=null)panel.hide(label);
            }
        });
        for(String method:new String[]{"getEndX","getTranslationY","resolvePanelStartTranslationX"}) {
            hook(loader,BASE+"anim.VolumeItemAnimator",new String[]{method},new HookCallback() {
                @Override protected void afterHookedMethod(Param p) {
                    if(p.hasThrowable())return;
                    View item=(View)Reflect.getObjectField(p.args[0],"itemView");
                    Compact panel=forItem(item);if(panel==null)return;
                    if(!method.equals("resolvePanelStartTranslationX"))panel.arrange(Reflect.getObjectField(p.thisObject,"adapter"));
                    int[] xy=location(item);
                    if(method.equals("resolvePanelStartTranslationX")) {
                        p.setResult(VolumePanelGeometry.translation(panel.anchorX,xy[0],item.getTranslationX()));
                    } else if(method.equals("getTranslationY")) {
                        float y=panel.rowY(item,VolumePanelGeometry.translation(panel.rowTop,xy[1],item.getTranslationY()));
                        // Start and end at the same vertical pose; retain the native X spring.
                        item.setTranslationY(y);
                        Reflect.setObjectField(p.thisObject,"cachedPanelBiasTranslationY",y);p.setResult(y);
                    } else {
                        Object adapter=Reflect.getObjectField(p.thisObject,"adapter");panel.arrange(adapter);
                        // Native LayoutManager keeps physical LTR order on both key sides.
                        float x=panel.rowX(p.args[0],item);if(Float.isNaN(x))return;
                        panel.follow(item,x);
                        p.setResult(VolumePanelGeometry.translation(x,xy[0],item.getTranslationX()));
                    }
                }
            });
        }
        hook(loader,MATERIAL+"VolumePlatformBlurHostHelper",new String[]{"mountPanelBackground","createPanelBackgroundIfNeeded"},new HookCallback() {
            @Override protected void afterHookedMethod(Param p) {
                Object dialog=Reflect.getObjectField(Reflect.getObjectField(p.thisObject,"deps"),"this$0");
                Compact panel=panels.get(dialog);if(panel!=null)panel.mount();
            }
        });
        // Fallback for detached/replaced holders and vendor animation-disabled paths.
        hook(loader,MATERIAL+"VolumePlatformBlurHostHelper",new String[]{"applyBarPanelBottomProgress"},new HookCallback() {
            @Override protected void afterHookedMethod(Param p) {
                Object dialog=Reflect.getObjectField(Reflect.getObjectField(p.thisObject,"deps"),"this$0");
                Compact panel=panels.get(dialog);
                if(panel!=null) {
                    panel.backgroundProgress=((Number)p.args[0]).floatValue();
                    panel.applyShortcutMaterial(panel.backgroundProgress,false);
                }
            }
        });
        hook(loader,BASE+"view.OplusVolumeDialogView$CustomDialog",new String[]{"dispatchTouchEvent"},new HookCallback() {
            @Override protected void beforeHookedMethod(Param p) {
                Object dialog=Reflect.getObjectField(p.thisObject,"this$0");Compact panel=panels.get(dialog);
                if(panel==null)return;
                MotionEvent event=(MotionEvent)p.args[0];
                if(event.getActionMasked()==MotionEvent.ACTION_DOWN)panel.outsideGesture=!panel.contains(event.getRawX(),event.getRawY());
                if(panel.outsideGesture) {
                    if(event.getActionMasked()==MotionEvent.ACTION_DOWN)Reflect.callMethod(Reflect.getObjectField(dialog,"volumeInteractor"),"dismissH$1",1);
                    p.setResult(true);
                    if(event.getActionMasked()==MotionEvent.ACTION_UP||event.getActionMasked()==MotionEvent.ACTION_CANCEL)panel.outsideGesture=false;
                }
            }
        });
        // Keep compact geometry until native terminal hide, including interrupted collapse.
        hook(loader,BASE+"view.OplusVolumeDialogView",new String[]{"resetSliderState"},new HookCallback() {
            @Override protected void beforeHookedMethod(Param p) {VolumeLiquidEntrance.finishDialog(p.thisObject);}
            @Override protected void afterHookedMethod(Param p) { restorePanel(p.thisObject);updateDialog(p.thisObject); }
        });
        hook(loader,BASE+"OplusVolumeDialogImpl",new String[]{"destroy","onConfigChanged","onThemeChanged","onUiModeChanged"},new HookCallback() {
            @Override protected void beforeHookedMethod(Param p) { Object dialog=Reflect.getObjectField(p.thisObject,"mOplusVolumeDialogView");VolumeLiquidEntrance.finishDialog(dialog);restorePanel(dialog); }
        });
        hook(loader,BASE+"OplusVolumeSeekBar",new String[]{"onAttachedToWindow","onMeasure"},new HookCallback() {
            @Override protected void beforeHookedMethod(Param p) { styleBar((View)p.thisObject); }
        });
        // Native setters/ensureSize can recalculate the rest pose. Reapply only the requested
        // base inputs, preserving the current/base deformation ratios rather than ending animation.
        hook(loader,"com.coui.appcompat.seekbar.COUIVerticalSeekBar",new String[]{"ensureSize"},new HookCallback() {
            @Override protected void beforeHookedMethod(Param p) {
                if(p.thisObject.getClass().getName().equals(BASE+"OplusVolumeSeekBar"))styleBar((View)p.thisObject);
            }
        });
        hook(loader,BASE+"OplusVolumeSeekBar",new String[]{"onDraw"},new HookCallback() {
            @Override protected void beforeHookedMethod(Param p) {
                HookConfig c=ShadeHook.snapshot();if(!c.enabled||!c.volumeBar.tint)return;
                p.setObjectExtra("backgroundColor",Reflect.getObjectField(p.thisObject,"mBackgroundColor"));
                // A material background already owns the tint. Never paint the same tint twice.
                Reflect.setIntField(p.thisObject,"mBackgroundColor",((View)p.thisObject).getBackground()==null?c.volumeBar.color:0);
            }
            @Override protected void afterHookedMethod(Param p) {
                Object original=p.getObjectExtra("backgroundColor");if(original!=null)Reflect.setObjectField(p.thisObject,"mBackgroundColor",original);
            }
        });
        // Include View.draw's background pass, not only onDraw's progress paint. Geometry is
        // temporary: native press/release animators keep their fields, listeners and clocks.
        hook(loader,BASE+"OplusVolumeSeekBar",new String[]{"draw"},new HookCallback() {
            @Override protected void beforeHookedMethod(Param p) {
                View bar=(View)p.thisObject;Compact panel=containing(bar);if(panel==null)return;
                float progress=panel.progress();if(progress>=1f)return;
                float base=((Number)Reflect.getObjectField(bar,"mBackgroundWidth")).floatValue();
                float baseRadius=((Number)Reflect.getObjectField(bar,"mBackgroundRadius")).floatValue();
                if(Math.abs(base-panel.startBarWidth)<.01f&&Math.abs(baseRadius-panel.startBarRadius)<.01f)return;
                float[] pose=new float[MORPH_FIELDS.length];
                for(int i=0;i<pose.length;i++)pose[i]=((Number)Reflect.getObjectField(bar,MORPH_FIELDS[i])).floatValue();
                p.setObjectExtra("volume.widthPose",pose);
                float width=VolumePanelGeometry.mix(panel.startBarWidth,base,progress);
                float ratio=base<=0?1:width/base;
                Reflect.setObjectField(bar,MORPH_FIELDS[0],pose[0]*ratio);
                Reflect.setObjectField(bar,MORPH_FIELDS[1],pose[1]*ratio);
                float radius=VolumePanelGeometry.mix(panel.startBarRadius,baseRadius,progress);
                float cornerRatio=baseRadius<=0?1:radius/baseRadius;
                float backgroundRadius=Math.min(pose[2]*cornerRatio,pose[0]*ratio/2f);
                Reflect.setObjectField(bar,MORPH_FIELDS[2],backgroundRadius);
                Reflect.setObjectField(bar,MORPH_FIELDS[3],Math.min(pose[3]*cornerRatio,pose[1]*ratio/2f));
                Reflect.setObjectField(bar,MORPH_FIELDS[4],Math.max(0,pose[4]+backgroundRadius-pose[2]));
                Reflect.callMethod(bar,"preCalcMaterialClipPath");
            }
            @Override protected void afterHookedMethod(Param p) {
                float[] pose=(float[])p.getObjectExtra("volume.widthPose");if(pose==null)return;
                for(int i=0;i<pose.length;i++)Reflect.setObjectField(p.thisObject,MORPH_FIELDS[i],pose[i]);
            }
        });
        for(String factory:new String[]{"getVolumeBarBackground","getVolumeSettingsButtonBackground","getVolumeSideAccessoryBackground","getVolumePanelBackground"}) {
            String role=factory.equals("getVolumeBarBackground")?"volume-bar":factory.equals("getVolumePanelBackground")?"volume-panel":"volume-buttons";
            hook(loader,MATERIAL+"VolumeBlurManager",new String[]{factory},new HookCallback() {
                @Override protected void beforeHookedMethod(Param p) {
                    HookConfig c=ShadeHook.snapshot();
                    if(factory.equals("getVolumePanelBackground")&&c.enabled&&c.volumeCompact&&!c.volumePanel.cornerEnabled)
                        p.args[3]=28f*((Context)p.args[0]).getResources().getDisplayMetrics().density;
                }
                @Override protected void afterHookedMethod(Param p) throws Throwable {
                    if(p.hasThrowable()||!(p.getResult() instanceof Drawable))return;
                    View host=(View)p.args[2];
                    String materialRole=role.equals("volume-bar")&&Boolean.TRUE.equals(Reflect.getAdditionalInstanceField(host,"volume.shortcut"))?"volume-buttons":role;
                    p.setResult(material((Drawable)p.getResult(),(Context)p.args[0],materialRole,host,loader));
                }
            });
        }
        // Expansion also hot-updates the cached static shader without calling any
        // ViewBlurProxy backend. Apply the same scoped override at that consumer.
        for(String consumer:new String[]{"applyPlatformBlurMixAfterConfigWrite","hotUpdateStaticPlatformLightAndStroke"}) {
        hook(loader,MATERIAL+"VolumeBlurManager",new String[]{consumer},new HookCallback() {
            @Override protected void beforeHookedMethod(Param p) {
                Object blur=consumer.equals("hotUpdateStaticPlatformLightAndStroke")?p.args[1]:Reflect.callMethod(p.args[0],"getBlurConfig");
                String role=String.valueOf(Reflect.getAdditionalInstanceField(blur,"thunder.role"));
                if(role.startsWith("volume-"))ShadeHook.overrideNativeConfig(p,blur,"BlurTypePlatformStatic");
            }
            @Override protected void afterHookedMethod(Param p) {ShadeHook.restoreNativeConfig(p);}
        });
        }
        hook(loader,MATERIAL+"VolumeBlurConfigWriter",new String[]{"writeSideAccessoryPlatform"},new HookCallback() {
            @Override protected void beforeHookedMethod(Param p) {
                if(p.args.length==5&&Boolean.TRUE.equals(Reflect.getAdditionalInstanceField(p.args[4],"volume.shortcut"))) {
                    // The native button supplies touch spotlight, but this panel shares the
                    // tracks' material. Do not let its size/theme refresh select SIDE_ACCESSORY.
                    Reflect.callMethod(p.thisObject,"writeBarPlatform",p.args);p.setResult(null);
                }
            }
        });
        bind("material-animation-deopt",()->Api102Runtime.deoptimizeCallers(Reflect.findClass(MATERIAL+"VolumeBlurManager",loader),
                "applyVolumeBarPanelBottomAmount","applyVolumeBarProgressMixColors","applySettingsButtonMixColors",
                "applyPlatformBlurMixAfterConfigWrite","syncVolumeBarPlatformLightBounds","syncVolumeBarProgressCorner"));
        // These small native getters are called by ART-compiled animation code.
        bind("button-animation-deopt",()->Api102Runtime.deoptimizeCallers(Reflect.findClass(BASE+"view.OplusVolumeDialogView",loader),"startBtnThickAnim","expandPanel","showOrHideAnimation"));
        bind("animation-deopt",()->Api102Runtime.deoptimizeCallers(Reflect.findClass(BASE+"anim.VolumeItemAnimator",loader),"animateAddImpl","animateMoveImpl","runPendingAnimations"));
        bind("shortcut-palette-deopt",()->Api102Runtime.deoptimizeCallers(Reflect.findClass("com.oplus.systemui.plugins.qs.customize.view.tile.OplusQSThreeStageLayout",loader),"tintIconColor","selectSegmentAt","setTranslationXAndScale","executeScaleAnimatorAt"));
        bind("liquid-entrance",()->VolumeLiquidEntrance.install(loader));
    }

    private static Drawable material(Drawable original,Context context,String role,View host,ClassLoader loader) throws Throwable {
        if(original instanceof FlatSurface)return original;
        Class<?> finder=Reflect.findClass(MATERIAL+"OplusVolumeBlurDrawableFinder",loader);
        Method find=finder.getDeclaredMethod("findAutoBlurDrawable",Drawable.class);find.setAccessible(true);
        Object auto=find.invoke(null,original);
        if(auto==null)return new FlatSurface(original,context,role,host);
        Object proxy=Reflect.callMethod(auto,"getViewBlurProxy"),blur=Reflect.callMethod(proxy,"getBlurConfig");
        Reflect.setAdditionalInstanceField(blur,"volume.host",new WeakReference<>(host));
        Reflect.setAdditionalInstanceField(host,"volume.proxy",new WeakReference<>(proxy));
        if(!role.equals(Reflect.getAdditionalInstanceField(blur,"thunder.role")))ShadeHook.trackVolume(proxy,role);
        return original;
    }
    private static void refreshMaterial(View host) {
        Object ref=Reflect.getAdditionalInstanceField(host,"volume.proxy");
        Object proxy=ref instanceof WeakReference<?>?((WeakReference<?>)ref).get():null;
        if(proxy!=null) {
            Reflect.callMethod(proxy,"invalidatePath");Reflect.callMethod(proxy,"applyBlurConfig");
        }
        host.invalidate();host.invalidateOutline();
    }

    static Float cornerRadius(Object blur,HookConfig.Area area) {
        String role=String.valueOf(Reflect.getAdditionalInstanceField(blur,"thunder.role"));
        if(!role.startsWith("volume-"))return null;
        Object ref=Reflect.getAdditionalInstanceField(blur,"volume.host");
        View host=ref instanceof WeakReference<?>?(View)((WeakReference<?>)ref).get():null;
        if(host==null)return null;
        Compact panel=null;for(Compact candidate:new ArrayList<>(panels.values()))if(candidate.root.get()==host){panel=candidate;break;}
        if(!area.cornerEnabled&&!(role.equals("volume-panel")&&panel!=null))return null;
        float radius=dp(host,area.cornerEnabled?area.corner:28);
        if(role.equals("volume-bar")) {
            radius=((Number)Reflect.getObjectField(host,"mCurBackgroundRadius")).floatValue();
            float width=((Number)Reflect.getObjectField(host,"mCurBackgroundWidth")).floatValue();
            return Math.min(radius,width/2f);
        }
        VolumePanelGeometry.Bounds visible=panel==null?null:panel.visualBounds();
        int width=visible==null?host.getWidth():visible.width(),height=visible==null?host.getHeight():visible.height();
        // New shortcut hosts have layout parameters before their first layout. Submitting
        // zero here caches square native blur corners until another config is applied.
        ViewGroup.LayoutParams lp=host.getLayoutParams();
        if(width<=0&&lp!=null)width=lp.width;
        if(height<=0&&lp!=null)height=lp.height;
        return width>0&&height>0?Math.min(radius,Math.min(width,height)/2f):radius;
    }

    static boolean smoothPanel(Object blur) {
        if(!"volume-panel".equals(Reflect.getAdditionalInstanceField(blur,"thunder.role")))return false;
        Object ref=Reflect.getAdditionalInstanceField(blur,"volume.host");
        Object host=ref instanceof WeakReference<?>?((WeakReference<?>)ref).get():null;
        for(Compact panel:new ArrayList<>(panels.values()))if(host!=null&&panel.root.get()==host)return true;
        return false;
    }
    private static void panelClip(Path path,Rect bounds,float radius) {
        try {panelClipBuilder.invoke(null,path,bounds,radius,PANEL_SMOOTH_WEIGHT,true);}
        catch(ReflectiveOperationException error) {throw new IllegalStateException("Native panel smooth path",error);}
    }

    private static int dp(View v,int value) { return Math.round(value*v.getResources().getDisplayMetrics().density); }
    private static int height(View v) {
        if(v==null)return 0;ViewGroup.LayoutParams lp=v.getLayoutParams();
        if(lp!=null&&lp.height>0)return lp.height;
        return v.getMeasuredHeight()>0?v.getMeasuredHeight():v.getHeight();
    }
    private static int[] location(View v) {int[] xy=new int[2];v.getLocationOnScreen(xy);return xy;}
    private static View view(Object dialog,String key) { return (View)Reflect.getObjectField(dialog,key); }
    private static void size(View v,int width,int height,boolean custom,boolean x,boolean y) {
        ViewGroup.LayoutParams lp=v.getLayoutParams();if(lp==null)return;
        if(custom)layouts.putIfAbsent(v,new int[]{lp.width,lp.height});
        int[] original=layouts.get(v);if(original==null)return;
        int w=custom?(x?width:lp.width):original[0],h=custom?(y?height:lp.height):original[1];
        if(lp.width!=w||lp.height!=h){lp.width=w;lp.height=h;v.setLayoutParams(lp);}
        if(!custom)layouts.remove(v);
    }
    private static void scaled(View v,Map<String,Object> defaults,String base,String current,Float target) {
        float value=((Number)Reflect.getObjectField(v,base)).floatValue();
        if(target!=null)defaults.putIfAbsent(base,value);
        Object saved=defaults.get(base);if(saved==null)return;
        float next=target==null?((Number)saved).floatValue():target;
        if(Float.compare(value,next)!=0) {
            float pose=((Number)Reflect.getObjectField(v,current)).floatValue();
            Reflect.setObjectField(v,base,next);Reflect.setObjectField(v,current,value==0?next:pose*next/value);
        }
        if(target==null)defaults.remove(base);
    }
    private static void field(View v,Map<String,Object> defaults,String name,Object target) {
        if(target!=null)defaults.putIfAbsent(name,Reflect.getObjectField(v,name));
        Object saved=defaults.get(name);if(saved==null)return;
        Reflect.setObjectField(v,name,target==null?saved:target);
        if(target==null)defaults.remove(name);
    }
    private static void styleBar(View v) {
        Compact panel=containing(v);HookConfig c=panel==null?ShadeHook.snapshot():panel.config;
        if(panel!=null)panel.bars.put(v,true);
        boolean custom=panel!=null||(c.enabled&&c.volumeSize),corner=c.enabled&&c.volumeBar.cornerEnabled;
        int widthPx=panel==null?dp(v,c.volumeWidth):Math.max(1,panel.rowWidth-dp(v,10));
        if(Boolean.TRUE.equals(Reflect.getAdditionalInstanceField(v,"volume.recalculating")))return;
        float oldWidth=((Number)Reflect.getObjectField(v,"mBackgroundWidth")).floatValue();
        float oldRadius=((Number)Reflect.getObjectField(v,"mBackgroundRadius")).floatValue();
        Map<String,Object> defaults=barDefaults.computeIfAbsent(v,k->new LinkedHashMap<>());
        Float width=custom?(float)widthPx:null;
        scaled(v,defaults,"mBackgroundWidth","mCurBackgroundWidth",width);
        // Native ProgressFull shares track width. Keep that relationship without touching colors.
        scaled(v,defaults,"mProgressWidth","mCurProgressWidth",width);
        field(v,defaults,"mSeekbarMinWidth",custom?widthPx:null);
        float track=((Number)Reflect.getObjectField(v,"mBackgroundWidth")).floatValue();
        Object savedRadius=defaults.get("mBackgroundRadius");
        float restRadius=savedRadius==null?((Number)Reflect.getObjectField(v,"mBackgroundRadius")).floatValue():((Number)savedRadius).floatValue();
        Float radius=null;
        if(corner)radius=Math.min((float)dp(v,c.volumeBar.corner),track/2f);
        else if(custom)radius=Math.min(restRadius,track/2f);
        scaled(v,defaults,"mBackgroundRadius","mCurBackgroundRadius",radius);
        scaled(v,defaults,"mProgressRadius","mCurProgressRadius",radius);
        // Background rect extends by radius; padding must move with it to keep its ends fixed.
        Object nativeRadius=defaults.get("mBackgroundRadius");
        if(radius!=null) {
            defaults.putIfAbsent("mPaddingVertical",Reflect.getObjectField(v,"mPaddingVertical"));
            float padding=((Number)defaults.get("mPaddingVertical")).floatValue()+radius-((Number)nativeRadius).floatValue();
            scaled(v,defaults,"mPaddingVertical","mCurPaddingVertical",Math.max(radius,padding));
        } else scaled(v,defaults,"mPaddingVertical","mCurPaddingVertical",null);
        size(v,custom?widthPx:0,0,custom,true,false);
        if(oldWidth!=((Number)Reflect.getObjectField(v,"mBackgroundWidth")).floatValue()||oldRadius!=((Number)Reflect.getObjectField(v,"mBackgroundRadius")).floatValue()) {
            String[] current={"mCurBackgroundWidth","mCurProgressWidth","mCurBackgroundRadius","mCurProgressRadius","mCurPaddingVertical"};
            Object[] pose=new Object[current.length];for(int i=0;i<current.length;i++)pose[i]=Reflect.getObjectField(v,current[i]);
            Reflect.setAdditionalInstanceField(v,"volume.recalculating",true);
            try {Reflect.callMethod(v,"ensureSize");}
            finally {for(int i=0;i<current.length;i++)Reflect.setObjectField(v,current[i],pose[i]);Reflect.removeAdditionalInstanceField(v,"volume.recalculating");}
            Reflect.callMethod(v,"preCalcMaterialClipPath");Reflect.callMethod(v,"updatePixPerProgress");v.invalidate();v.invalidateOutline();
        }
    }
    private static void styleRow(Object row,HookConfig c) {
        if(row==null)return;
        View bar=(View)Reflect.getObjectField(row,"slider");if(bar==null)return;styleBar(bar);
        View frame=(View)Reflect.getObjectField(row,"sliderFrame");
        Compact active=containing(bar);int width=active==null?dp(bar,c.volumeWidth):Math.max(1,active.rowWidth-dp(bar,10));
        if(frame!=null)size(frame,width+frame.getPaddingLeft()+frame.getPaddingRight(),0,active!=null||(c.enabled&&c.volumeSize),true,false);
        // Row root height/topMargin belong to native overflow/handoff logic.
        View root=(View)Reflect.getObjectField(row,"view");if(root!=null)size(root,active==null?dp(bar,c.volumeWidth+10):active.rowWidth,0,active!=null||(c.enabled&&c.volumeSize),true,false);
        bar.invalidate();bar.invalidateOutline();
    }
    private static void updateDialog(Object dialog) {
        dialogs.put(dialog,true);Compact panel=panels.get(dialog);HookConfig c=panel==null?ShadeHook.snapshot():panel.config;Object owner=Reflect.getObjectField(dialog,"volumeInteractor");
        for(Object row:(Iterable<?>)Reflect.getObjectField(owner,"mRows"))styleRow(row,c);
        View recycler=view(dialog,"mVolumeRecyclerview");
        if(recycler!=null&&panel==null) {
            boolean custom=c.enabled&&c.volumeSize;
            int[] saved=layouts.get(recycler);int original=saved==null?recycler.getLayoutParams().height:saved[1];
            // Preserve the native label, inset and reveal overhead instead of guessing +24 dp.
            int nativeBarHeight=0;
            for(Object row:(Iterable<?>)Reflect.getObjectField(owner,"mRows")) {
                View bar=(View)Reflect.getObjectField(row,"slider");if(bar!=null&&bar.getHeight()>0){nativeBarHeight=bar.getHeight();break;}
            }
            Object overhead=Reflect.getAdditionalInstanceField(recycler,"volume.heightOverhead");
            if(custom&&overhead==null&&nativeBarHeight>0&&original>0) {
                overhead=Math.max(0,original-nativeBarHeight);Reflect.setAdditionalInstanceField(recycler,"volume.heightOverhead",overhead);
            }
            if(overhead!=null)size(recycler,0,dp(recycler,c.volumeHeight)+(Integer)overhead,custom,false,true);
        }
        styleButtons(dialog,c,panel);
        if(panel!=null) {panel.arrange(Reflect.getObjectField(dialog,"volumeListAdapter"));panel.mount();}
    }

    private static void styleButtons(Object dialog,HookConfig c,Compact panel) {
        for(String key:new String[]{"mMoreRowStreamLl","mAppVolumeAdjustFl","mODICaptionsView","mSettingsButton"}) {
            View button=view(dialog,key);if(button==null)continue;
            int side=panel==null?dp(button,c.volumeWidth):panel.barWidth();
            size(button,side,side,panel!=null||(c.enabled&&c.volumeSize),true,true);
            button.invalidate();button.invalidateOutline();
        }
    }

    private static void preparePanel(Object dialog) {
        View root=view(dialog,"mDialogView"),recycler=view(dialog,"mVolumeRecyclerview");
        Object owner=Reflect.getObjectField(dialog,"volumeInteractor"),row=Reflect.callMethod(owner,"getActiveRow");
        if(root==null||recycler==null||row==null)return;
        View bar=(View)Reflect.getObjectField(row,"slider"),rowView=(View)Reflect.getObjectField(row,"view");
        if(bar==null||!bar.isAttachedToWindow()||!bar.isShown()||root.getWidth()<=0)return;
        // Holder, not the slider: their left/top differ by native frame padding and reveal margin.
        View item=rowView;
        while(item!=null&&item.getParent() instanceof View&&item.getParent()!=recycler)item=(View)item.getParent();
        if(item==null||item.getParent()!=recycler)return;
        Compact panel=new Compact(dialog,root,recycler,item,bar);panels.put(dialog,panel);
        panel.arrange(Reflect.getObjectField(dialog,"volumeListAdapter"));panel.watch();
        ShadeHook.observed("volume-compact","native item/accessory/background animators; anchored geometry");
    }
    private static Compact forAdapter(Object adapter) {
        for(Compact panel:new ArrayList<>(panels.values())) {
            Object dialog=panel.dialog.get();if(dialog!=null&&Reflect.getObjectField(dialog,"volumeListAdapter")==adapter)return panel;
        }
        return null;
    }
    private static Compact containing(View v) {
        ArrayList<Compact> active=new ArrayList<>(panels.values());
        for(View item=v;item!=null;item=item.getParent() instanceof View?(View)item.getParent():null) {
            for(Compact panel:active)if(item.getParent()==panel.recycler.get())return panel;
        }
        return null;
    }
    private static Compact forItem(View item) {
        for(Compact panel:new ArrayList<>(panels.values()))if(item.getParent()==panel.recycler.get())return panel;
        return null;
    }
    private static void restorePanel(Object dialog) {
        Compact panel=panels.remove(dialog);if(panel!=null)panel.restore();
    }
    static void refresh() {
        VolumeLiquidEntrance.refresh();
        for(Object dialog:new ArrayList<>(dialogs.keySet()))try {
            // Changing geometry midway through an existing native gesture would jump. Let the
            // native dismiss path finish with its captured geometry, then apply on next show.
            if(panels.containsKey(dialog)) {
                Reflect.callMethod(Reflect.getObjectField(dialog,"volumeInteractor"),"dismissH$1",1);
            } else updateDialog(dialog);
        } catch(Throwable error) {ShadeHook.frameworkFailure("volume-refresh",error);}
    }

    private static final class Compact {
        final HookConfig config;
        final WeakReference<Object> dialog;
        final WeakReference<View> root,recycler,anchorItem;
        final WeakReference<View> sourceBar;
        final View sourceGroup;
        final Map<View,float[]> sourceAccessories=new LinkedHashMap<>();
        WeakReference<View> driver;
        final float startBarWidth,startBarRadius;
        float targetX,targetY,backgroundProgress=Float.NaN,lastProgress=-1;
        ViewTreeObserver observer;
        final ViewTreeObserver.OnPreDrawListener frame=()->{tick();return true;};
        final Map<View,Integer> hidden=new WeakHashMap<>();
        final Map<View,Boolean> bars=new WeakHashMap<>();
        final Rect usable=new Rect();
        // Only used synchronously on the view thread; each read returns a scalar.
        final int[] screenLocation=new int[2];
        final int anchorX,anchorY,anchorRight,inset,sourceHeight,trackTop,trackBottom,sourceRowHeight,anchorTrackY,sourceBarX,sourceBarY;
        int rowHeight;
        VolumePanelGeometry.Bounds bounds;
        int rowTop,count,arrangedCount=-1,panelShiftX;
        final int labelId;
        NativePanelSurface surface;
        boolean outsideGesture,dismissing;
        float dismissOpacity=1f;
        int rowWidth;
        float rowOffset;
        FrameLayout shortcuts,soundSegments,zenControl;
        Method shortcutMixMethod;
        Object shortcutManager;
        float shortcutMaterialProgress=Float.NaN;
        final View.OnLayoutChangeListener materialLayout=(v,l,t,r,b,ol,ot,or,ob)->{
            if(r-l!=or-ol||b-t!=ob-ot) {
                if(v==soundSegments)Reflect.callMethod(v,"setTranslationXAndScale");
                refreshMaterial(v);
            }
        };
        final View.OnLayoutChangeListener selectionLayout=(v,l,t,r,b,ol,ot,or,ob)->{
            refreshProgressSurface(v);
        };
        final View.OnAttachStateChangeListener materialAttach=new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) {refreshMaterial(v);}
            @Override public void onViewDetachedFromWindow(View v) { }
        };
        final View[] soundButtons=new View[3];
        View zenIcon;
        View zenSelection;
        float zenCorner;
        boolean zenProgressBlur;
        Object zenSpring,morphSpring;
        VolumeShortcutFeedback soundFeedback,zenFeedback;
        boolean morphExpanded,morphReturning;
        int shortcutStep;
        int shownRinger=-1,shownZen=-1,lastZen=1;
        boolean hasVibrator,alertSlider;
        Compact(Object d,View r,View list,View item,View bar) {
            anchorItem=new WeakReference<>(item);
            sourceBar=new WeakReference<>(bar);sourceBarX=location(bar)[0];sourceBarY=location(bar)[1];
            startBarWidth=((Number)Reflect.getObjectField(bar,"mBackgroundWidth")).floatValue();
            startBarRadius=((Number)Reflect.getObjectField(bar,"mBackgroundRadius")).floatValue();
            labelId=list.getResources().getIdentifier("volume_text","id","com.android.systemui");
            config=ShadeHook.snapshot();dialog=new WeakReference<>(d);root=new WeakReference<>(r);recycler=new WeakReference<>(list);
            int[] xy=location(item);anchorX=xy[0];anchorY=xy[1];anchorRight=anchorX+item.getWidth();
            r.getWindowVisibleDisplayFrame(usable);int[] origin=location(r);
            usable.left=Math.max(usable.left,origin[0]);usable.top=Math.max(usable.top,origin[1]);
            usable.right=Math.min(usable.right,origin[0]+r.getWidth());usable.bottom=Math.min(usable.bottom,origin[1]+r.getHeight());
            inset=dp(r,8);sourceHeight=height(list);
            View frame=(View)bar.getParent();
            float padding=((Number)Reflect.getObjectField(bar,"mPaddingVertical")).floatValue();
            int endPadding=Math.max(0,Math.round(padding-startBarRadius));
            // Panel binding clears the sidebar's negative reveal margin. Only the native
            // frame/seekbar padding then separates the holder edge from the visible track.
            trackTop=(frame==null?0:frame.getPaddingTop())+bar.getPaddingTop()+endPadding;
            trackBottom=(frame==null?0:frame.getPaddingBottom())+bar.getPaddingBottom()+endPadding;
            sourceRowHeight=bar.getHeight()+(frame==null?0:frame.getPaddingTop()+frame.getPaddingBottom());
            anchorTrackY=location(bar)[1]+bar.getPaddingTop()+endPadding;
            View group=view(d,"mVerticalRowIconParent");sourceGroup=group;
            if(group!=null) {
                Reflect.callMethod(Reflect.callMethod(group,"animate"),"cancel");
                int[] groupLocation=location(group);
                sourceAccessories.put(group,new float[]{group.getTranslationX(),group.getTranslationY(),group.getScaleX(),group.getScaleY(),group.getAlpha(),group.getVisibility(),groupLocation[0],groupLocation[1]});
                for(String key:new String[]{"mMoreRowStreamLl","mAppVolumeAdjustFl","mODICaptionsView"}) {
                    View button=view(d,key);if(button==null)continue;
                    Reflect.callMethod(Reflect.callMethod(button,"animate"),"cancel");
                    sourceAccessories.put(button,new float[]{button.getTranslationX(),button.getTranslationY(),button.getScaleX(),button.getScaleY(),button.getAlpha(),button.getVisibility()});
                }
            }
        }
        void arrange(Object adapter) {
            View r=root.get(),list=recycler.get();Object d=dialog.get();if(r==null||list==null||d==null)return;
            hideAccessories();
            int n=((java.util.List<?>)Reflect.callMethod(adapter,"getPanelShowList")).size();
            if(n==0)n=((Number)Reflect.callMethod(adapter,"getItemCount")).intValue();
            n=Math.max(1,n);if(arrangedCount==n)return;arrangedCount=n;count=n;
            int margin=dp(r,8),usableWidth=Math.max(1,usable.width()-2*margin);
            int columns=Math.max(4,count);
            int panelTop=anchorTrackY-inset;
            int available=Math.max(1,usable.bottom-margin-panelTop);
            int desiredWidth=Math.min(Math.round(startBarWidth),Math.max(1,available-3*inset-1));
            int trackWidth=VolumePanelGeometry.fitTrackWidth(usableWidth,columns,desiredWidth,inset);
            rowWidth=trackWidth+dp(r,10);
            int cardWidth=VolumePanelGeometry.panelWidth(columns,trackWidth,inset);
            rowOffset=(cardWidth-VolumePanelGeometry.panelWidth(count,trackWidth,inset))/2f;
            styleButtons(d,config,this);
            // Keep the visible top anchored. Only a real screen edge can shorten the row.
            rowHeight=Math.max(trackTop+trackBottom+1,Math.min(sourceRowHeight,available-3*inset-trackWidth+trackTop+trackBottom));
            int contentHeight=Math.max(1,rowHeight-trackTop-trackBottom);
            // Size to the visible track and its native bottom icon, excluding frame elevation
            // padding and the adapter's text-label row. There is no header/footer minimum.
            int holderInset=Math.round((anchorRight-anchorX-startBarWidth)/2f);
            bounds=VolumePanelGeometry.place(usable.left+margin,usable.top+margin,usable.right-margin,usable.bottom-margin,
                    anchorX+holderInset,anchorY+trackTop,anchorRight-holderInset,anchorY+sourceHeight,
                    cardWidth,contentHeight+trackWidth+3*inset,inset);
            // Leave a short inward travel for the outermost row too. All rows still
            // start at the captured sidebar X; keep equal final column spacing.
            int inward=dp(r,16)*(bounds.opensLeft()?-1:1);
            int panelX=Math.max(usable.left+margin,Math.min(usable.right-margin-bounds.width(),bounds.x()+inward));
            panelShiftX=panelX-bounds.x();
            bounds=new VolumePanelGeometry.Bounds(panelX,panelTop,bounds.width(),contentHeight+trackWidth+3*inset,bounds.opensLeft());
            rowTop=anchorTrackY-trackTop;
            size(list,0,rowHeight,true,false,true);
            arrangeShortcuts();
            if(surface!=null)surface.invalidateSelf();
        }
        void arrangeShortcuts() {
            View r=root.get();Object d=dialog.get();if(!(r instanceof ViewGroup parent)||d==null)return;
            if(shortcuts==null) {
                // Reuse the firmware control, including its indicator drawable, COUI springs,
                // drag gestures, press feedback and Lottie icons. Do not reproduce its visuals.
                soundSegments=(FrameLayout)Reflect.newInstance(Reflect.findClass(
                        "com.oplus.systemui.plugins.qs.customize.view.tile.OplusQSThreeStageLayout",
                        d.getClass().getClassLoader()),r.getContext());
                // This host uses the normal native highlight; use its matching native icon
                // palette too, rather than the QS theme's potentially white-on-white colors.
                Reflect.setObjectField(soundSegments,"disableTheme",true);
                shortcuts=new FrameLayout(r.getContext());
                zenControl=new FrameLayout(r.getContext());
                for(View group:new View[]{soundSegments,zenControl}) {
                    group.addOnLayoutChangeListener(materialLayout);group.addOnAttachStateChangeListener(materialAttach);
                }
                java.util.List<?> frames=(java.util.List<?>)Reflect.getObjectField(soundSegments,"frameLayoutViews");
                for(int i=0;i<3;i++) {
                    soundButtons[i]=(View)frames.get(i);
                    // Keep accessibility click listeners, but send physical gestures to the
                    // vendor parent's onTouchEvent: it owns drag, press and spring release.
                    soundButtons[i].setClickable(false);soundButtons[i].setLongClickable(false);
                }
                soundSegments.setOnClickListener(v->{
                    Object mode=v.getTag();
                    if(mode instanceof Integer selected)shortcut(selected);
                    // The native control previews a tap/drag before the controller confirms it.
                    // Reconcile even a rejected mode or an asynchronous controller response.
                    syncSoundSelection();
                });
                zenSelection=new View(r.getContext());zenControl.addView(zenSelection);
                Object span=Reflect.newInstance(Reflect.findClass("com.oplusos.systemui.common.model.SpanSize",d.getClass().getClassLoader()),1,1);
                zenIcon=(View)Reflect.newInstance(Reflect.findClass("com.oplus.systemui.plugins.qs.customize.view.tile.OplusQSIconView",d.getClass().getClassLoader()),r.getContext(),span,false);
                Reflect.callMethod(zenIcon,"disableTheme",true);
                // Volume is itself a dialog; use the native detail-icon allowance for Lottie.
                Reflect.callMethod(Reflect.getObjectField(zenIcon,"iconViewProxy"),"setDetailTile",true);
                zenControl.addView(zenIcon);
                try {
                    soundFeedback=VolumeShortcutFeedback.attach(soundSegments,d.getClass().getClassLoader());
                    zenFeedback=VolumeShortcutFeedback.attach(zenControl,d.getClass().getClassLoader());
                } catch(ReflectiveOperationException error){throw new IllegalStateException("Native volume feedback",error);}
                for(Object helper:(java.util.List<?>)Reflect.getObjectField(soundSegments,"pressScaleHelper"))
                    Reflect.setAdditionalInstanceField(helper,"volume.shortcut-press",true);
                try {
                    Object alpha=Reflect.findClass("com.coui.appcompat.animation.dynamicanimation.COUIDynamicAnimation",d.getClass().getClassLoader()).getField("ALPHA").get(null);
                    zenSpring=Reflect.newInstance(Reflect.findClass("com.coui.appcompat.animation.dynamicanimation.COUISpringAnimation",d.getClass().getClassLoader()),zenSelection,alpha,0f);
                    Object spring=Reflect.newInstance(Reflect.findClass("com.coui.appcompat.animation.dynamicanimation.COUISpringForce",d.getClass().getClassLoader()));
                    Reflect.callMethod(spring,"setBounce",0f);Reflect.callMethod(spring,"setResponse",.35f);
                    Reflect.callMethod(zenSpring,"setSpring",spring);
                    shortcuts.setAlpha(0f);
                    morphSpring=Reflect.newInstance(Reflect.findClass("com.coui.appcompat.animation.dynamicanimation.COUISpringAnimation",d.getClass().getClassLoader()),shortcuts,alpha,0f);
                    Object morphForce=Reflect.newInstance(Reflect.findClass("com.coui.appcompat.animation.dynamicanimation.COUISpringForce",d.getClass().getClassLoader()));
                    Reflect.callMethod(morphForce,"setBounce",0f);Reflect.callMethod(morphForce,"setResponse",.48f);
                    Reflect.callMethod(morphSpring,"setSpring",morphForce);
                } catch(ReflectiveOperationException error){throw new IllegalStateException("Native DND spring",error);}
                shortcuts.addView(soundSegments);shortcuts.addView(zenControl);
                Object owner=Reflect.getObjectField(d,"volumeInteractor"),controller=Reflect.getObjectField(owner,"mController");
                hasVibrator=(Boolean)Reflect.callMethod(controller,"hasVibrator");
                try {
                    Class<?> feature=Reflect.findClass("com.oplusos.systemui.common.feature.FeatureOption",d.getClass().getClassLoader());
                    alertSlider=(Boolean)feature.getDeclaredMethod("isOplusAlertSlider").invoke(null);
                } catch(ReflectiveOperationException error) {throw new IllegalStateException(error);}
                zenControl.setOnClickListener(v->shortcut(-1));
                parent.addView(shortcuts);
            }
            cancelShortcutMotion();
            int side=barWidth(),innerWidth=bounds.width()-2*inset;
            // Four equal icon slots fill the row, even when native app-volume adds more columns.
            shortcutStep=(innerWidth-side)/3;
            Object lp=Reflect.newInstance(recycler.get().getLayoutParams().getClass(),innerWidth,side);
            Reflect.setIntField(lp,"leftToLeft",0);Reflect.setIntField(lp,"topToTop",0);
            int[] origin=location(r);
            Reflect.setIntField(lp,"leftMargin",bounds.x()+inset-origin[0]);
            Reflect.setIntField(lp,"topMargin",bounds.y()+bounds.height()-inset-side-origin[1]);
            shortcuts.setLayoutParams((ViewGroup.LayoutParams)lp);
            Object manager;
            try {manager=Reflect.findClass(MATERIAL+"VolumeBlurManager",d.getClass().getClassLoader()).getDeclaredField("INSTANCE").get(null);}
            catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
            shortcutManager=manager;
            try {shortcutMixMethod=manager.getClass().getDeclaredMethod("applyVolumeBarPanelBottomAmount",View.class,float.class,boolean.class);}
            catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
            soundSegments.setLayoutParams(new FrameLayout.LayoutParams(2*shortcutStep+side,side));
            FrameLayout.LayoutParams zenLp=new FrameLayout.LayoutParams(side,side);
            zenLp.leftMargin=innerWidth-side;zenControl.setLayoutParams(zenLp);
            float corner=Math.min(side/2f,config.volumeBar.cornerEnabled?dp(r,config.volumeBar.corner):startBarRadius);
            Reflect.callMethod(soundSegments,"updateContainerLayoutParams",0,0);
            Reflect.callMethod(soundSegments,"updateIconBgDrawable");
            Reflect.callMethod(soundSegments,"onIconDrawableUpdate",false);
            Reflect.callMethod(soundSegments,"onIconDrawableUpdate",true);
            Reflect.callMethod(soundSegments,"tintColor",(Object)null);
            View indicator=(View)Reflect.getObjectField(soundSegments,"indicatorView");
            int indicatorSize=side;
            ViewGroup.LayoutParams indicatorLp=indicator.getLayoutParams();
            indicatorLp.width=indicatorLp.height=indicatorSize;indicator.setLayoutParams(indicatorLp);
            int iconSize=Math.min(dp(r,20),Math.round(side/1.2f));
            for(String key:new String[]{"iconViews","lottieViews"})
                for(Object child:(java.util.List<?>)Reflect.getObjectField(soundSegments,key)) {
                    View icon=(View)child;ViewGroup.LayoutParams iconLp=icon.getLayoutParams();
                    iconLp.width=iconLp.height=iconSize;icon.setLayoutParams(iconLp);
                }
            // Both selected surfaces use the track progress material and shared corner.
            zenSelection.setLayoutParams(new FrameLayout.LayoutParams(side,side));
            zenCorner=corner;
            Drawable progressMaterial=progressSurface(zenSelection,corner);
            zenProgressBlur=!(progressMaterial instanceof GradientDrawable);
            zenSelection.setBackground(progressMaterial);
            indicator.setBackground(progressSurface(indicator,corner));
            indicator.setDuplicateParentStateEnabled(false);
            indicator.addOnLayoutChangeListener(selectionLayout);
            zenSelection.addOnLayoutChangeListener(selectionLayout);
            for(View group:new View[]{soundSegments,zenControl}) {
                // SIDE_ACCESSORY has a brighter native mix and different light defaults,
                // even when custom tint is off. Use the same surface kind as the tracks.
                Reflect.setAdditionalInstanceField(group,"volume.shortcut",true);
                Drawable material=(Drawable)Reflect.callMethod(manager,"getVolumeBarBackground",r.getContext(),r,group,corner,
                        Reflect.callMethod(d,"getMIsDefaultVolumeTheme"));
                try {material=material(material,r.getContext(),"volume-buttons",group,d.getClass().getClassLoader());}
                catch(Throwable error) {throw new IllegalStateException("Shortcut material",error);}
                group.setBackground(material);
                refreshMaterial(group);
            }
            soundFeedback.update(corner,(Boolean)Reflect.callMethod(d,"getMIsDefaultVolumeTheme"));
            zenFeedback.update(corner,(Boolean)Reflect.callMethod(d,"getMIsDefaultVolumeTheme"));
            refreshZenProgress();
            applyShortcutMaterial(Float.isNaN(backgroundProgress)?0f:backgroundProgress,true);
            zenIcon.setLayoutParams(new FrameLayout.LayoutParams(side,side));
            int nativeIconSize=r.getResources().getIdentifier("std_1x1_switch_icon_size","dimen","com.android.systemui");
            if(nativeIconSize!=0)Reflect.callMethod(zenIcon,"setIconScaleRatio",iconSize/(float)r.getResources().getDimensionPixelSize(nativeIconSize));
            zenIcon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            zenControl.setFocusable(true);
            shownRinger=shownZen=-1;lastProgress=-1;updateShortcutState();
            shortcuts.setAlpha(0f);
        }
        // -1 is DND; 2/1/0 directly select ringing/vibration/silent. Native state is authoritative.
        void shortcut(int mode) {
            Object d=dialog.get();if(d==null||progress()<.95f)return;
            try {
                Object owner=Reflect.getObjectField(d,"volumeInteractor"),state=Reflect.getObjectField(owner,"mState");
                if(state==null)return;
                Object controller=Reflect.getObjectField(owner,"mController");
                if(mode==-1) {
                    int current=Reflect.getIntField(state,"zenMode");
                    if(current!=0)lastZen=current;
                    Reflect.callMethod(controller,"setZenMode",current==0?lastZen:0);
                } else {
                    if(alertSlider||Reflect.getBooleanField(state,"disallowRinger")||(mode==1&&!hasVibrator))return;
                    int current=Reflect.getIntField(state,"ringerModeInternal");
                    if(current==mode) {Reflect.callMethod(owner,"rescheduleTimeoutH");return;}
                    Reflect.callMethod(controller,"setRingerMode",mode,false);
                }
                Reflect.callMethod(owner,"rescheduleTimeoutH");
                updateShortcutState();
            } catch(Throwable error) {ShadeHook.frameworkFailure("volume-shortcut",error);}
        }
        void updateShortcutState() {
            updateShortcutState(progress());
        }
        void updateShortcutState(float progress) {
            if(shortcuts==null)return;Object d=dialog.get();if(d==null)return;
            Object state=Reflect.getObjectField(Reflect.getObjectField(d,"volumeInteractor"),"mState");
            boolean ready=state!=null&&progress>=.95f;
            boolean soundReady=ready&&!alertSlider&&!Reflect.getBooleanField(state,"disallowRinger");
            for(int i=0;i<3;i++)soundButtons[i].setEnabled(soundReady&&(i!=1||hasVibrator));
            Reflect.callMethod(soundSegments,"setEditMode",!soundReady);
            soundSegments.setEnabled(soundReady);
            soundSegments.setAlpha(state!=null&&!alertSlider&&!Reflect.getBooleanField(state,"disallowRinger")?1f:.38f);
            zenControl.setEnabled(ready);zenControl.setAlpha(1f);
            boolean theme=(Boolean)Reflect.callMethod(d,"getMIsDefaultVolumeTheme");
            soundFeedback.update(zenCorner,theme);zenFeedback.update(zenCorner,theme);
            if(state==null)return;
            int ringer=Reflect.getIntField(state,"ringerModeInternal"),zen=Reflect.getIntField(state,"zenMode");
            if(zen!=0)lastZen=zen;
            if(ringer==shownRinger&&zen==shownZen)return;
            boolean animate=ready&&shownRinger!=-1&&shownZen!=-1;
            shownRinger=ringer;shownZen=zen;
            String[] labels={"响铃","振动","静音"};
            for(int i=0;i<3;i++) {
                boolean selected=ringer==2-i;soundButtons[i].setSelected(selected);
                soundButtons[i].setContentDescription(labels[i]+(selected?"，已选中":"，点击切换")+
                        (alertSlider?"，由实体拨杆控制":i==1&&!hasVibrator?"，设备不支持振动":""));
            }
            zenControl.setSelected(zen!=0);zenControl.setContentDescription(zen==0?"开启免打扰":"关闭免打扰");
            syncSoundSelection();
            animateShortcutPose(animate);
        }
        void cancelShortcutMotion() {
            if(zenSpring!=null)Reflect.callMethod(zenSpring,"cancel");
        }
        int selectedIconColor(int nativeColor) {
            if(zenSelection==null||zenSelection.getBackground()==null)return nativeColor;
            Drawable background=zenSelection.getBackground();Integer fill=null;
            if(background instanceof ColorDrawable color)fill=color.getColor();
            else {
                if(background.getClass().getName().endsWith("GradientTileDrawable"))
                    fill=((ColorDrawable)Reflect.getObjectField(background,"colorDrawable")).getColor();
                else if(background.getClass().getName().endsWith("MixColorTileDrawable")) {
                    fill=Reflect.getIntField(background,"maskColor");
                    if((fill>>>24)<192) {
                        Object mix=Reflect.callMethod(Reflect.getObjectField(background,"currentConfig"),"getFirst");
                        if(mix!=null&&mix.getClass().getName().endsWith("BlurMixMultiWithShader"))
                            fill=Reflect.getIntField(Reflect.callMethod(mix,"getForegroundShaderParam"),"topLayerColor");
                    }
                }
            }
            // Some firmware palettes return white even for an opaque white normal highlight.
            // Bind its contrasting foreground to the native state API, retaining native springs.
            if(fill!=null&&(fill>>>24)>=192)return AdaptiveTextColor.foreground(fill);
            // The captured firmware's normal highlight is white even when its opaque fill
            // comes from a state-list resource rather than an exposed mask/shader color.
            return (nativeColor&0xffffff)==0xffffff?AdaptiveTextColor.BLACK:nativeColor;
        }
        Drawable progressSurface(View host,float corner) {
            Drawable material=(Drawable)Reflect.callMethod(shortcutManager,"getVolumeBarProgressDrawable",host.getContext(),host,corner);
            if(material!=null)return material;
            GradientDrawable fill=(GradientDrawable)Reflect.newInstance(panelFillType);
            fill.setColor(Reflect.getIntField(sourceBar.get(),"mProgressColor"));
            Reflect.callMethod(fill,"setSmoothRound",corner,PANEL_SMOOTH_WEIGHT);
            return fill;
        }
        void refreshProgressSurface(View view) {
            if(view==null||view.getBackground()==null)return;
            ViewGroup.LayoutParams lp=view.getLayoutParams();
            int width=view.getWidth()>0?view.getWidth():lp.width;
            int height=view.getHeight()>0?view.getHeight():lp.height;
            Drawable background=view.getBackground();background.setBounds(0,0,width,height);
            float corner=zenCorner;
            if(background instanceof GradientDrawable)Reflect.callMethod(background,"setSmoothRound",corner,PANEL_SMOOTH_WEIGHT);
            else {
                Reflect.callMethod(shortcutManager,"syncVolumeBarProgressCorner",background,corner,null);
                Reflect.callMethod(shortcutManager,"applyVolumeBarProgressMixColors",background,view.getContext(),true);
            }
        }
        void refreshZenProgress() {
            refreshProgressSurface(zenSelection);
            if(soundSegments!=null)refreshProgressSurface((View)Reflect.getObjectField(soundSegments,"indicatorView"));
        }
        void syncSoundSelection() {
            Object d=dialog.get();if(d==null)return;
            Object state=Reflect.getObjectField(Reflect.getObjectField(d,"volumeInteractor"),"mState");
            if(state==null)return;
            int mode=Reflect.getIntField(state,"ringerModeInternal");
            // The vendor API accepts ringer modes (2/1/0), not segment indices.
            if(mode>=0&&mode<=2)Reflect.callMethod(soundSegments,"setSelectedIndex",mode,
                    soundButtons[2-mode].getContentDescription());
        }
        void animateShortcutPose(boolean animate) {
            Object d=dialog.get();if(d==null)return;ClassLoader loader=d.getClass().getClassLoader();
            boolean active=shownZen!=0;
            float target=active?1f:0f;
            if(animate)Reflect.callMethod(zenSpring,"animateToFinalPosition",target);
            else {cancelShortcutMotion();zenSelection.setAlpha(target);}
            try {
                Object state=Reflect.newInstance(Reflect.findClass("com.android.systemui.plugins.qs.QSTile$BooleanState",loader));
                Reflect.setObjectField(state,"spec","dnd");Reflect.setObjectField(state,"lottiePrefix","qs_dnd_lottie");
                Reflect.setIntField(state,"lottieSupport",17);Reflect.setIntField(state,"state",active?2:1);
                Reflect.setObjectField(state,"value",active);Reflect.setObjectField(state,"toggleable",true);
                int id=zenIcon.getResources().getIdentifier(active?"status_bar_qs_dnd_active":"status_bar_qs_dnd_inactive","drawable","com.android.systemui");
                Method resource=Reflect.findClass("com.android.systemui.qs.tileimpl.QSTileImpl$ResourceIcon",loader).getMethod("get",int.class);
                Reflect.setObjectField(state,"icon",resource.invoke(null,id));
                int foreground=((Number)Reflect.callMethod(soundSegments,"getActiveIconColor",active)).intValue();
                if(active)foreground=selectedIconColor(foreground);
                Object proxy=Reflect.getObjectField(zenIcon,"iconViewProxy");
                ColorStateList tint=ColorStateList.valueOf(foreground);
                // Native Lottie captures tintList inside setIcon, before static icon tint updates.
                Reflect.callMethod(proxy,"setTintList",tint);
                Reflect.callMethod(zenIcon,"setIcon",state,animate);
                Reflect.callMethod(proxy,"setTintList",tint);
            } catch(ReflectiveOperationException error) {throw new IllegalStateException("Native DND state",error);}
        }
        void stopNativeSoundMotion() {
            Reflect.callMethod(soundSegments,"clearRunnable");
            // Lottie end callbacks can start icon-alpha springs; stop Lottie first.
            for(Object lottie:(java.util.List<?>)Reflect.getObjectField(soundSegments,"lottieViews"))
                Reflect.callMethod(lottie,"cancelAnimation");
            for(String key:new String[]{"indicatorSpringTranslationX","springAnimationBounce"})
                Reflect.callMethod(Reflect.getObjectField(soundSegments,key),"cancel");
            for(Object animation:(java.util.List<?>)Reflect.getObjectField(soundSegments,"iconSpringAnimatorSets"))
                Reflect.callMethod(animation,"cancel");
            for(Object helper:(java.util.List<?>)Reflect.getObjectField(soundSegments,"scaleSyncAnimatorHelper"))
                Reflect.callMethod(Reflect.callMethod(helper,"getSpringAnim"),"cancel");
            for(Object helper:(java.util.List<?>)Reflect.getObjectField(soundSegments,"iconAlphaSpringAnimatorSets"))
                for(String key:new String[]{"alphaSpringAnim","colorSpringAnim"})
                    Reflect.callMethod(Reflect.getObjectField(helper,key),"cancel");
            for(Object helper:(java.util.List<?>)Reflect.getObjectField(soundSegments,"pressScaleHelper")) {
                Object spring=Reflect.callMethod(helper,"getSpringAnimation");
                if(spring!=null)Reflect.callMethod(spring,"cancel");
            }
        }
        boolean isSource(View item) {
            for(View v=sourceBar.get();v!=null;v=v.getParent() instanceof View?(View)v.getParent():null)
                if(v==item)return true;
            return item==anchorItem.get();
        }
        void follow(View item,float x) {
            // Prefer the original active holder, but allow the native adapter to replace it.
            if(driver!=null&&driver.get()==item)return;
            if(driver!=null&&driver.get()!=null&&driver.get().getParent()==recycler.get()&&!isSource(item))return;
            driver=new WeakReference<>(item);targetX=x;targetY=rowTop;
        }
        float rowX(Object holder,View item) {
            int index=((Number)Reflect.callMethod(holder,"getAbsoluteAdapterPosition")).intValue();
            if(index<0)index=((Number)Reflect.callMethod(holder,"getLayoutPosition")).intValue();
            if(index<0)index=((Number)Reflect.callMethod(holder,"getOldPosition")).intValue();
            return index<0?Float.NaN:VolumePanelGeometry.trackRowX(bounds,index,barWidth(),item.getWidth(),inset)+rowOffset;
        }
        View holder(View bar) {
            View item=bar;
            while(item!=null&&item.getParent() instanceof View&&item.getParent()!=recycler.get())item=(View)item.getParent();
            return item!=null&&item.getParent()==recycler.get()?item:null;
        }
        float rowY(View item,float fallback) {
            View source=sourceBar.get();
            if(source!=null&&holder(source)==item)return item.getTranslationY()+sourceBarY-screenY(source);
            for(View bar:bars.keySet())if(holder(bar)==item)return item.getTranslationY()+sourceBarY-screenY(bar);
            return fallback;
        }
        int screenX(View view){view.getLocationOnScreen(screenLocation);return screenLocation[0];}
        int screenY(View view){view.getLocationOnScreen(screenLocation);return screenLocation[1];}
        void alignRows() {
            View source=sourceBar.get(),sourceItem=source==null?null:holder(source);
            if(sourceItem!=null)sourceItem.setTranslationY(rowY(sourceItem,sourceItem.getTranslationY()));
            for(View bar:bars.keySet()) {
                View item=holder(bar);if(item!=null&&item!=sourceItem)item.setTranslationY(rowY(item,item.getTranslationY()));
            }
        }
        float progress() {
            View item=driver==null?null:driver.get();
            float fallback=Float.isNaN(backgroundProgress)?(surface==null?0:surface.getDrawable().getAlpha()/255f):backgroundProgress;
            if(item==null||!item.isAttachedToWindow()||item.getParent()!=recycler.get())return VolumePanelGeometry.clamp(fallback);
            return VolumePanelGeometry.progress(screenX(item),0,anchorX,0,targetX,0,fallback);
        }
        VolumePanelGeometry.Bounds visualBounds() {
            return visualBounds(progress());
        }
        VolumePanelGeometry.Bounds visualBounds(float progress) {
            VolumePanelGeometry.Bounds b=VolumePanelGeometry.widthAt(bounds,Math.round(startBarWidth)+2*inset,progress);
            return new VolumePanelGeometry.Bounds(b.x()-Math.round(panelShiftX*(1f-progress)),b.y(),b.width(),b.height(),b.opensLeft());
        }
        void applyShortcutMaterial(float progress,boolean force) {
            float phase=VolumePanelGeometry.clamp(progress);
            if(shortcutMixMethod==null||(!force&&phase==shortcutMaterialProgress))return;
            try {
                // Custom hosts are outside the native row/accessory collections. Forward the
                // same material phase, including reverse transitions and the terminal zero.
                shortcutMixMethod.invoke(null,soundSegments,phase,force||phase==0f);
                shortcutMixMethod.invoke(null,zenControl,phase,force||phase==0f);
                shortcutMaterialProgress=phase;
                if(zenProgressBlur)refreshZenProgress();
            } catch(ReflectiveOperationException error) {throw new IllegalStateException("Shortcut material phase",error);}
        }
        void watch() {View r=root.get();if(r!=null){observer=r.getViewTreeObserver();observer.addOnPreDrawListener(frame);}}
        void tick() {
            hideAccessories();
            alignRows();
            float progress=progress();updateShortcutState(progress);
            if(shortcuts!=null) {
                // Trigger a native spring once movement is under way, rather than
                // compressing the entire shape change into the eased row's last frames.
                if(lastProgress>=0&&progress<lastProgress-.01f)morphReturning=true;
                else if(lastProgress>=0&&progress>lastProgress+.01f)morphReturning=false;
                boolean expanded=!dismissing&&!morphReturning&&(morphExpanded||progress>=.35f);
                if(expanded!=morphExpanded) {
                    morphExpanded=expanded;
                    Reflect.callMethod(morphSpring,"animateToFinalPosition",expanded?1f:0f);
                }
                float reveal=VolumePanelGeometry.clamp(shortcuts.getAlpha());
                shortcuts.setClipBounds(null);
                int side=barWidth(),fullWidth=2*shortcutStep+side;
                int width=Math.round(VolumePanelGeometry.mix(side,fullWidth,reveal));
                ViewGroup.LayoutParams soundLp=soundSegments.getLayoutParams();
                if(soundLp.width!=width){soundLp.width=width;soundSegments.setLayoutParams(soundLp);}
                // Native icons keep their real size. Fade their frames in only after
                // the host has room, rather than overlapping three icons in one circle.
                float content=VolumePanelGeometry.clamp((reveal-.25f)/.5f);
                for(View button:soundButtons)button.setAlpha(content);
                ((View)Reflect.getObjectField(soundSegments,"indicatorView")).setAlpha(content);
                // Resize the actual native material host: a circle opens into a capsule,
                // without exposing a pre-sized three-stage control through a clip window.
                View source=sourceBar.get();
                float sourceX=source==null?bounds.x()+inset:screenX(source);
                soundSegments.setTranslationX((sourceX-bounds.x()-inset)*(1f-reveal));
                float opacity=1f;
                if(dismissing) {
                    // The native remove animator fades the row, then resets its alpha to 1
                    // before the dialog's terminal hide. Accessories must never flash back.
                    View list=recycler.get();
                    for(View v=sourceBar.get();v!=null&&v!=root.get();v=v.getParent() instanceof View?(View)v.getParent():null) {
                        opacity*=v.getAlpha();if(v==list)break;
                    }
                    dismissOpacity=Math.min(dismissOpacity,VolumePanelGeometry.clamp(opacity));
                    opacity=dismissOpacity;
                }
                soundSegments.setAlpha(soundSegments.getAlpha()*opacity);
                zenControl.setAlpha(zenControl.getAlpha()*opacity);
                View bar=sourceBar.get();
                float dx=bar!=null&&bar.isAttachedToWindow()?screenX(bar)-sourceBarX:(targetX-anchorX)*progress;
                for(Map.Entry<View,float[]> entry:sourceAccessories.entrySet()) {
                    View v=entry.getKey();float[] pose=entry.getValue();
                    if(v==sourceGroup) {
                        // Native expansion also rebases this ConstraintLayout child. Follow
                        // the painted source bar in screen space, compensating that re-layout.
                        int[] xy=location(v);
                        v.setTranslationX(v.getTranslationX()+pose[6]+dx-xy[0]);
                        v.setTranslationY(v.getTranslationY()+pose[7]-xy[1]);
                    } else {v.setTranslationX(pose[0]);v.setTranslationY(pose[1]);}
                    v.setScaleX(pose[2]);v.setScaleY(pose[3]);
                    v.setAlpha(pose[4]*(v==sourceGroup?(1f-reveal)*opacity:1f));
                    v.setVisibility((int)pose[5]);
                }
            }
            if(Math.abs(progress-lastProgress)<.0001f)return;lastProgress=progress;
            if(surface!=null)surface.invalidateSelf();
            // Native row translations already invalidate their display lists. Only a fitted
            // track width needs an extra draw for our temporary geometry interpolation.
            if(Math.abs(barWidth()-startBarWidth)>.01f)
                for(View bar:bars.keySet())bar.invalidate();
        }
        int barWidth() {View r=root.get();return r==null?1:Math.max(1,rowWidth-dp(r,10));}
        void hide(View v) {
            if(v==null)return;
            hidden.putIfAbsent(v,v.getVisibility());
            if(v.getVisibility()!=View.GONE)v.setVisibility(View.GONE);
        }
        void hideAccessories() {
            Object d=dialog.get();if(d!=null)for(String key:ACCESSORIES)hide(view(d,key));
            // Native bindPanelAccessibility can reveal a label again during a row refresh.
            View list=recycler.get();
            if(labelId!=0&&list instanceof ViewGroup group)
                for(int i=0;i<group.getChildCount();i++)hide(group.getChildAt(i).findViewById(labelId));
        }
        void mount() {
            View r=root.get();if(r==null||bounds==null)return;
            Drawable background=r.getBackground();if(background==null||background==surface)return;
            surface=new NativePanelSurface(background,this);r.setBackground(surface);
        }
        boolean contains(float x,float y) {
            VolumePanelGeometry.Bounds visible=bounds==null?null:visualBounds();
            if(visible!=null&&x>=visible.x()&&x<visible.x()+visible.width()&&y>=visible.y()&&y<visible.y()+visible.height())return true;
            View list=recycler.get();
            if(list instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++)if(hit(group.getChildAt(i),x,y))return true;
            Object d=dialog.get();return d!=null&&hit(view(d,"mSnackBar"),x,y);
        }
        boolean hit(View v,float x,float y) {
            if(v==null||!v.isShown())return false;int[] xy=location(v);
            return x>=xy[0]&&x<xy[0]+v.getWidth()&&y>=xy[1]&&y<xy[1]+v.getHeight();
        }
        void restore() {
            View r=root.get(),list=recycler.get();
            if(shortcuts!=null) {
                if(morphSpring!=null)Reflect.callMethod(morphSpring,"cancel");
                cancelShortcutMotion();
                ((View)Reflect.getObjectField(soundSegments,"indicatorView")).removeOnLayoutChangeListener(selectionLayout);
                zenSelection.removeOnLayoutChangeListener(selectionLayout);
                stopNativeSoundMotion();soundSegments.setOnClickListener(null);
                if(zenIcon!=null)Reflect.callMethod(zenIcon,"onRecycle");
                if(soundFeedback!=null)soundFeedback.release();
                if(zenFeedback!=null)zenFeedback.release();
                for(View button:soundButtons)if(button!=null)button.setOnClickListener(null);
                zenControl.setOnClickListener(null);zenControl.setOnTouchListener(null);zenControl.setBackground(null);soundSegments.setBackground(null);
                for(View group:new View[]{soundSegments,zenControl}) {
                    group.removeOnLayoutChangeListener(materialLayout);group.removeOnAttachStateChangeListener(materialAttach);
                    Reflect.removeAdditionalInstanceField(group,"volume.proxy");
                    Reflect.removeAdditionalInstanceField(group,"volume.shortcut");
                }
                if(shortcuts.getParent() instanceof ViewGroup parent)parent.removeView(shortcuts);
            }
            if(observer!=null&&observer.isAlive())observer.removeOnPreDrawListener(frame);
            else if(r!=null)r.getViewTreeObserver().removeOnPreDrawListener(frame);
            if(surface!=null&&r!=null&&r.getBackground()==surface)r.setBackground(surface.getDrawable());
            for(Map.Entry<View,float[]> entry:sourceAccessories.entrySet()) {
                View v=entry.getKey();float[] pose=entry.getValue();
                v.setTranslationX(pose[0]);v.setTranslationY(pose[1]);v.setScaleX(pose[2]);v.setScaleY(pose[3]);v.setAlpha(pose[4]);
                // Native terminal hide owns visibility; only restore geometry for the next show.
            }
            for(Map.Entry<View,Integer> entry:hidden.entrySet())entry.getKey().setVisibility(entry.getValue());
            if(list!=null)size(list,0,0,false,false,true);
        }
    }
    /** Child drawable identity stays native: the native blur/alpha animator still drives it. */
    private static final class NativePanelSurface extends DrawableWrapper {
        final Compact panel;final Path clip=new Path();
        NativePanelSurface(Drawable original,Compact p){super(original);panel=p;}
        @Override public void draw(Canvas canvas) {
            View root=panel.root.get();if(root==null||panel.bounds==null)return;
            int[] origin=location(root);VolumePanelGeometry.Bounds b=panel.visualBounds();
            int x=b.x()-origin[0],y=b.y()-origin[1];
            HookConfig c=panel.config;float corner=dp(root,c.enabled&&c.volumePanel.cornerEnabled?c.volumePanel.corner:28);
            corner=Math.min(corner,Math.min(b.width(),b.height())/2f);
            // Set actual backend bounds; canvas clipping alone leaves hardware blur fullscreen.
            getDrawable().setBounds(x,y,x+b.width(),y+b.height());
            panelClip(clip,getDrawable().getBounds(),corner);
            int save=canvas.save();try {canvas.clipPath(clip);getDrawable().draw(canvas);}finally {canvas.restoreToCount(save);}
        }
        @Override public void getOutline(Outline outline) {outline.setEmpty();}
    }
    /** Dynamic fallback retains the native drawable so disabling a setting restores it immediately. */
    private static final class FlatSurface extends DrawableWrapper {
        final Context context;final String role;final WeakReference<View> host;final Path clip=new Path();final GradientDrawable fill;
        FlatSurface(Drawable nativeDrawable,Context ctx,String r,View v){super(nativeDrawable);context=ctx;role=r;host=new WeakReference<>(v);
            fill=r.equals("volume-panel")?(GradientDrawable)Reflect.newInstance(panelFillType):new GradientDrawable();}
        HookConfig.Area area() {
            HookConfig c=ShadeHook.snapshot();return !c.enabled?null:role.equals("volume-panel")?c.volumePanel:c.volumeBar;
        }
        Rect materialBounds() {
            View v=host.get();
            if(v==null||!role.equals("volume-bar"))return getBounds();
            Reflect.callMethod(v,"preCalcMaterialClipPath");return (Rect)Reflect.getObjectField(v,"mBackgroundRect");
        }
        float radius(HookConfig.Area a,Rect bounds) {
            View v=host.get();
            if(v!=null&&role.equals("volume-bar"))return Math.min(((Number)Reflect.getObjectField(v,"mCurBackgroundRadius")).floatValue(),Math.min(bounds.width(),bounds.height())/2f);
            Outline nativeOutline=new Outline();getDrawable().getOutline(nativeOutline);
            float nativeRadius=getDrawable() instanceof GradientDrawable?((GradientDrawable)getDrawable()).getCornerRadius():Math.max(0f,nativeOutline.getRadius());
            return Math.min(a.cornerEnabled?Math.round(a.corner*context.getResources().getDisplayMetrics().density):nativeRadius,Math.min(bounds.width(),bounds.height())/2f);
        }
        void roundNative(Drawable drawable,float radius,Map<GradientDrawable,Object> saved) {
            if(drawable instanceof GradientDrawable gradient) {
                if(saved.containsKey(gradient))return;
                float[] corners=gradient.getCornerRadii();
                saved.put(gradient,corners==null?gradient.getCornerRadius():corners);
                gradient.setCornerRadius(radius);
            } else if(drawable instanceof LayerDrawable layers) {
                for(int i=0;i<layers.getNumberOfLayers();i++)roundNative(layers.getDrawable(i),radius,saved);
            } else if(drawable instanceof DrawableWrapper wrapper)roundNative(wrapper.getDrawable(),radius,saved);
            else if(drawable.getClass().getName().equals(MATERIAL+"OplusVolumeBackgroundWrapper"))
                roundNative((Drawable)Reflect.callMethod(drawable,"getContent"),radius,saved);
        }
        @Override public void draw(Canvas canvas) {
            HookConfig.Area a=area();
            boolean smoothPanel=role.equals("volume-panel")&&ShadeHook.snapshot().volumeCompact;
            if(a==null||(!a.tint&&!a.cornerEnabled&&!smoothPanel)){super.draw(canvas);return;}
            Rect b=materialBounds();float radius=radius(a,b);
            if(a.tint) {
                fill.setBounds(b);fill.setColor(a.color);
                if(role.equals("volume-panel"))Reflect.callMethod(fill,"setSmoothRound",radius,PANEL_SMOOTH_WEIGHT);
                else fill.setCornerRadius(radius);
                fill.setAlpha(getAlpha());fill.draw(canvas);
            } else {
                if(role.equals("volume-panel"))panelClip(clip,b,radius);
                else {clip.rewind();clip.addRoundRect(b.left,b.top,b.right,b.bottom,radius,radius,Path.Direction.CW);}
                Drawable original=getDrawable();Rect old=original.getBounds();int l=old.left,t=old.top,r=old.right,bottom=old.bottom;
                Drawable.Callback callback=original.getCallback();int save=canvas.save();
                Map<GradientDrawable,Object> corners=new LinkedHashMap<>();
                try {
                    original.setCallback(null);original.setBounds(b);
                    // Clipping can cut a larger corner but cannot fill a smaller one.
                    // Update solid native shapes during this draw, then restore them.
                    if(a.cornerEnabled||smoothPanel)roundNative(original,role.equals("volume-panel")?0f:radius,corners);
                    canvas.clipPath(clip);super.draw(canvas);
                } finally {
                    for(Map.Entry<GradientDrawable,Object> entry:corners.entrySet()) {
                        if(entry.getValue() instanceof float[] values)entry.getKey().setCornerRadii(values);
                        else entry.getKey().setCornerRadius(((Number)entry.getValue()).floatValue());
                    }
                    original.setBounds(l,t,r,bottom);original.setCallback(callback);canvas.restoreToCount(save);
                }
            }
        }
        @Override public void getOutline(Outline outline) {
            HookConfig.Area a=area();if(a==null||!a.cornerEnabled){super.getOutline(outline);return;}
            Rect b=materialBounds();outline.setRoundRect(b.left,b.top,b.right,b.bottom,radius(a,b));
        }
    }
    private static void hook(ClassLoader loader,String type,String[] names,HookCallback callback) {
        for(String name:names)bind(name,()->{
            Class<?> cls=Reflect.findClass(type,loader);boolean found=false;
            for(Method method:cls.getDeclaredMethods())if(method.getName().equals(name)) {
                Class<?>[] types=method.getParameterTypes();Object[] signature=new Object[types.length+1];
                System.arraycopy(types,0,signature,0,types.length);signature[types.length]=callback;
                Api102Runtime.findAndHookMethod(cls,name,signature);found=true;
            }
            if(!found)throw new IllegalStateException(type+"."+name);
        });
    }
    private static void bind(String key,Runnable action) {
        try {action.run();ShadeHook.hookRegistered("volume-"+key);}
        catch(Throwable error) {ShadeHook.frameworkFailure("volume-"+key,error);}
    }
}
