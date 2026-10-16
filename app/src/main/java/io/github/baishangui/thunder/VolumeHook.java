package io.github.baishangui.thunder;

import android.content.Context;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.DrawableWrapper;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.drawable.RippleDrawable;
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

    static void install(ClassLoader loader) {
        bind("view",()->Api102Runtime.hookAllConstructors(Reflect.findClass(BASE+"view.OplusVolumeDialogView",loader),new HookCallback() {
            @Override protected void afterHookedMethod(Param p) { if(!p.hasThrowable())dialogs.put(p.thisObject,true); }
        }));
        hook(loader,BASE+"view.OplusVolumeDialogView",new String[]{"initDialog"},new HookCallback() {
            @Override protected void beforeHookedMethod(Param p) { restorePanel(p.thisObject); }
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
                if(panels.containsKey(p.thisObject))return;
                HookConfig c=ShadeHook.snapshot();if(!c.enabled||!c.volumeCompact)return;
                try { preparePanel(p.thisObject); }
                catch(Throwable error) { restorePanel(p.thisObject);ShadeHook.frameworkFailure("volume-native-panel",error); }
            }
            @Override protected void afterHookedMethod(Param p) {
                Compact panel=panels.get(p.thisObject);if(panel!=null)panel.hideAccessories();
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
                        float y=VolumePanelGeometry.translation(panel.rowTop,xy[1],item.getTranslationY());
                        // Start and end at the same vertical pose; retain the native X spring.
                        item.setTranslationY(y);
                        Reflect.setObjectField(p.thisObject,"cachedPanelBiasTranslationY",y);p.setResult(y);
                    } else {
                        Object adapter=Reflect.getObjectField(p.thisObject,"adapter");panel.arrange(adapter);
                        int index=((Number)Reflect.callMethod(p.args[0],"getAbsoluteAdapterPosition")).intValue();
                        if(index<0)index=((Number)Reflect.callMethod(p.args[0],"getLayoutPosition")).intValue();
                        if(index<0)index=((Number)Reflect.callMethod(p.args[0],"getOldPosition")).intValue();
                        if(index<0)return;
                        // Native LayoutManager keeps physical LTR order on both key sides.
                        float x=VolumePanelGeometry.trackRowX(panel.bounds,index,panel.barWidth(),item.getWidth(),panel.inset)+panel.rowOffset;
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
                Compact panel=panels.get(dialog);if(panel!=null)panel.backgroundProgress=((Number)p.args[0]).floatValue();
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
            @Override protected void afterHookedMethod(Param p) { restorePanel(p.thisObject);updateDialog(p.thisObject); }
        });
        hook(loader,BASE+"OplusVolumeDialogImpl",new String[]{"destroy","onConfigChanged","onThemeChanged","onUiModeChanged"},new HookCallback() {
            @Override protected void beforeHookedMethod(Param p) { restorePanel(Reflect.getObjectField(p.thisObject,"mOplusVolumeDialogView")); }
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
                float[] pose=new float[MORPH_FIELDS.length];
                for(int i=0;i<pose.length;i++)pose[i]=((Number)Reflect.getObjectField(bar,MORPH_FIELDS[i])).floatValue();
                p.setObjectExtra("volume.widthPose",pose);
                float base=((Number)Reflect.getObjectField(bar,"mBackgroundWidth")).floatValue();
                float width=VolumePanelGeometry.mix(panel.startBarWidth,base,progress);
                float ratio=base<=0?1:width/base;
                Reflect.setObjectField(bar,MORPH_FIELDS[0],pose[0]*ratio);
                Reflect.setObjectField(bar,MORPH_FIELDS[1],pose[1]*ratio);
                float baseRadius=((Number)Reflect.getObjectField(bar,"mBackgroundRadius")).floatValue();
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
        // These small native getters are called by ART-compiled animation code.
        bind("button-animation-deopt",()->Api102Runtime.deoptimizeCallers(Reflect.findClass(BASE+"view.OplusVolumeDialogView",loader),"startBtnThickAnim"));
        bind("animation-deopt",()->Api102Runtime.deoptimizeCallers(Reflect.findClass(BASE+"anim.VolumeItemAnimator",loader),"animateAddImpl","animateMoveImpl","runPendingAnimations"));
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
        for(View item=v;item!=null;item=item.getParent() instanceof View?(View)item.getParent():null) {
            Compact panel=forItem(item);if(panel!=null)return panel;
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
        WeakReference<View> driver;
        final float startBarWidth,startBarRadius;
        float targetX,targetY,backgroundProgress=Float.NaN,lastProgress=-1;
        ViewTreeObserver observer;
        final ViewTreeObserver.OnPreDrawListener frame=()->{tick();return true;};
        final Map<View,Integer> hidden=new WeakHashMap<>();
        final Rect usable=new Rect();
        final int anchorX,anchorY,anchorRight,inset,sourceHeight,trackTop,trackBottom,sourceRowHeight,anchorTrackY;
        int rowHeight;
        VolumePanelGeometry.Bounds bounds;
        int rowTop,count,arrangedCount=-1;
        final int labelId;
        NativePanelSurface surface;
        boolean outsideGesture;
        int rowWidth;
        float rowOffset;
        FrameLayout shortcuts,soundSegments,zenControl;
        final View.OnLayoutChangeListener materialLayout=(v,l,t,r,b,ol,ot,or,ob)->{
            if(r-l!=or-ol||b-t!=ob-ot)refreshMaterial(v);
        };
        final View.OnAttachStateChangeListener materialAttach=new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) {refreshMaterial(v);}
            @Override public void onViewDetachedFromWindow(View v) { }
        };
        final ImageView[] soundButtons=new ImageView[3];
        ImageView zenOff,zenOn;
        View soundSelection,zenSelection;
        ValueAnimator shortcutMotion;
        final float[] soundWeights=new float[3];
        float selectionX,selectionAlpha,zenWeight;
        int shortcutStep;
        int shownRinger=-1,shownZen=-1,lastZen=1;
        boolean hasVibrator,alertSlider;
        Compact(Object d,View r,View list,View item,View bar) {
            anchorItem=new WeakReference<>(item);
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
            bounds=new VolumePanelGeometry.Bounds(bounds.x(),panelTop,bounds.width(),contentHeight+trackWidth+3*inset,bounds.opensLeft());
            rowTop=anchorTrackY-trackTop;
            size(list,0,rowHeight,true,false,true);
            arrangeShortcuts();
            if(surface!=null)surface.invalidateSelf();
        }
        void arrangeShortcuts() {
            View r=root.get();Object d=dialog.get();if(!(r instanceof ViewGroup parent)||d==null)return;
            if(shortcuts==null) {
                shortcuts=new FrameLayout(r.getContext());
                soundSegments=new FrameLayout(r.getContext());zenControl=new FrameLayout(r.getContext());
                for(View group:new View[]{soundSegments,zenControl}) {
                    group.addOnLayoutChangeListener(materialLayout);group.addOnAttachStateChangeListener(materialAttach);
                }
                soundSelection=new View(r.getContext());zenSelection=new View(r.getContext());
                soundSegments.addView(soundSelection);zenControl.addView(zenSelection);
                for(int i=0;i<soundButtons.length;i++) {
                    final int mode=2-i;
                    soundButtons[i]=new ImageView(r.getContext());
                    soundButtons[i].setOnClickListener(v->shortcut(mode));
                    soundSegments.addView(soundButtons[i]);
                }
                zenOff=new ImageView(r.getContext());zenOn=new ImageView(r.getContext());
                zenControl.addView(zenOff);zenControl.addView(zenOn);
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
            boolean dark=(r.getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;
            int foreground=dark?0xffffffff:0xff000000;
            if(config.volumeBar.tint)foreground=AdaptiveTextColor.foreground(AdaptiveTextColor.composite(config.volumeBar.color,dark?0xff202124:0xfffafafa));
            soundSegments.setLayoutParams(new FrameLayout.LayoutParams(2*shortcutStep+side,side));
            FrameLayout.LayoutParams zenLp=new FrameLayout.LayoutParams(side,side);
            zenLp.leftMargin=innerWidth-side;zenControl.setLayoutParams(zenLp);
            float corner=Math.min(side/2f,config.volumeBar.cornerEnabled?dp(r,config.volumeBar.corner):startBarRadius);
            for(View selection:new View[]{soundSelection,zenSelection}) {
                selection.setLayoutParams(new FrameLayout.LayoutParams(side,side));
                // Keep the chosen material ARGB visible in selected slots too. A filled
                // black/white overlay changes its apparent tint by another 15 percent.
                GradientDrawable fill=new GradientDrawable();fill.setColor(0);fill.setCornerRadius(corner);
                fill.setStroke(Math.max(1,dp(r,1)),(foreground&0xffffff)|0x66000000);
                selection.setBackground(fill);
            }
            for(View group:new View[]{soundSegments,zenControl}) {
                // SIDE_ACCESSORY has a brighter native mix and different light defaults,
                // even when custom tint is off. Use the same surface kind as the tracks.
                Reflect.setAdditionalInstanceField(group,"volume.shortcut",true);
                Drawable material=(Drawable)Reflect.callMethod(manager,"getVolumeBarBackground",r.getContext(),r,group,corner,
                        Reflect.callMethod(d,"getMIsDefaultVolumeTheme"));
                try {material=material(material,r.getContext(),"volume-buttons",group,d.getClass().getClassLoader());}
                catch(Throwable error) {throw new IllegalStateException("Shortcut material",error);}
                GradientDrawable mask=new GradientDrawable();mask.setColor(0xffffffff);mask.setCornerRadius(corner);
                group.setBackground(group==zenControl?new RippleDrawable(ColorStateList.valueOf((foreground&0xffffff)|0x24000000),material,mask):material);
                refreshMaterial(group);
            }
            for(int i=0;i<5;i++) {
                ImageView button=i<3?soundButtons[i]:i==3?zenOff:zenOn;
                FrameLayout.LayoutParams child=new FrameLayout.LayoutParams(side,side);
                child.leftMargin=i<3?i*shortcutStep:0;button.setLayoutParams(child);
                int padding=Math.max(0,(side-Math.min(side,dp(r,24)))/2);
                button.setPadding(padding,padding,padding,padding);button.setScaleType(ImageView.ScaleType.FIT_CENTER);
                button.setImageTintList(ColorStateList.valueOf(foreground));
                if(i<3) {
                    GradientDrawable mask=new GradientDrawable();mask.setColor(0xffffffff);mask.setCornerRadius(corner);
                    button.setBackground(new RippleDrawable(ColorStateList.valueOf((foreground&0xffffff)|0x24000000),null,mask));
                    button.setFocusable(true);
                } else button.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            }
            zenControl.setFocusable(true);
            icon(soundButtons[0],"status_bar_qs_mute_inactive");
            icon(soundButtons[1],"status_bar_qs_vibrate_on");
            icon(soundButtons[2],"status_bar_qs_mute_active");
            icon(zenOff,"status_bar_qs_dnd_inactive");icon(zenOn,"status_bar_qs_dnd_active");
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
            if(shortcuts==null)return;Object d=dialog.get();if(d==null)return;
            Object state=Reflect.getObjectField(Reflect.getObjectField(d,"volumeInteractor"),"mState");
            boolean ready=state!=null&&progress()>=.95f;
            boolean soundReady=ready&&!alertSlider&&!Reflect.getBooleanField(state,"disallowRinger");
            for(int i=0;i<3;i++)soundButtons[i].setEnabled(soundReady&&(i!=1||hasVibrator));
            soundSegments.setAlpha(soundReady?1f:.38f);zenControl.setEnabled(ready);zenControl.setAlpha(ready?1f:.38f);
            renderShortcutPose();
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
            animateShortcutPose(animate);
        }
        void cancelShortcutMotion() {
            if(shortcutMotion==null)return;
            shortcutMotion.removeAllUpdateListeners();shortcutMotion.cancel();shortcutMotion=null;
        }
        void animateShortcutPose(boolean animate) {
            cancelShortcutMotion();
            float[] from=soundWeights.clone();float fromX=selectionX,fromAlpha=selectionAlpha,fromZen=zenWeight;
            boolean valid=shownRinger>=0&&shownRinger<=2;
            float toX=valid?(2-shownRinger)*shortcutStep:fromX,toAlpha=valid?1f:0f,toZen=shownZen==0?0f:1f;
            if(!animate) {
                selectionX=toX;selectionAlpha=toAlpha;zenWeight=toZen;
                for(int i=0;i<3;i++)soundWeights[i]=shownRinger==2-i?1f:0f;
                renderShortcutPose();return;
            }
            shortcutMotion=ValueAnimator.ofFloat(0f,1f);shortcutMotion.setDuration(200);
            shortcutMotion.setInterpolator(new PathInterpolator(.2f,0f,0f,1f));
            shortcutMotion.addUpdateListener(animation->{
                float t=(Float)animation.getAnimatedValue();
                selectionX=fromX+(toX-fromX)*t;selectionAlpha=fromAlpha+(toAlpha-fromAlpha)*t;zenWeight=fromZen+(toZen-fromZen)*t;
                for(int i=0;i<3;i++)soundWeights[i]=from[i]+((shownRinger==2-i?1f:0f)-from[i])*t;
                renderShortcutPose();
            });
            shortcutMotion.start();
        }
        void renderShortcutPose() {
            soundSelection.setTranslationX(selectionX);soundSelection.setAlpha(selectionAlpha);
            for(int i=0;i<3;i++) {
                soundButtons[i].setAlpha((.6f+.4f*soundWeights[i])*(i==1&&!hasVibrator?.38f:1f));
                float scale=.92f+.08f*soundWeights[i];soundButtons[i].setScaleX(scale);soundButtons[i].setScaleY(scale);
            }
            zenSelection.setAlpha(zenWeight);zenOff.setAlpha(1f-zenWeight);zenOn.setAlpha(zenWeight);
            float offScale=1f-.08f*zenWeight,onScale=.92f+.08f*zenWeight;
            zenOff.setScaleX(offScale);zenOff.setScaleY(offScale);zenOn.setScaleX(onScale);zenOn.setScaleY(onScale);
        }
        void icon(ImageView button,String name) {
            View r=root.get();if(r==null)return;
            int id=r.getResources().getIdentifier(name,"drawable","com.android.systemui");
            if(id!=0)button.setImageResource(id);
        }
        void follow(View item,float x) {
            // Prefer the original active holder, but allow the native adapter to replace it.
            if(driver!=null&&driver.get()==item)return;
            if(driver!=null&&driver.get()!=null&&driver.get().getParent()==recycler.get()&&item!=anchorItem.get())return;
            driver=new WeakReference<>(item);targetX=x;targetY=rowTop;
        }
        float progress() {
            View item=driver==null?null:driver.get();
            float fallback=Float.isNaN(backgroundProgress)?(surface==null?0:surface.getDrawable().getAlpha()/255f):backgroundProgress;
            if(item==null||!item.isAttachedToWindow()||item.getParent()!=recycler.get())return VolumePanelGeometry.clamp(fallback);
            int[] xy=location(item);
            return VolumePanelGeometry.progress(xy[0],0,anchorX,0,targetX,0,fallback);
        }
        VolumePanelGeometry.Bounds visualBounds() {
            return VolumePanelGeometry.widthAt(bounds,Math.round(startBarWidth)+2*inset,progress());
        }
        void watch() {View r=root.get();if(r!=null){observer=r.getViewTreeObserver();observer.addOnPreDrawListener(frame);}}
        void tick() {
            hideAccessories();
            updateShortcutState();
            float progress=progress();if(Math.abs(progress-lastProgress)<.0001f)return;lastProgress=progress;
            if(surface!=null)surface.invalidateSelf();
            if(shortcuts!=null) {
                VolumePanelGeometry.Bounds visible=visualBounds();
                int left=visible.x()-bounds.x(),right=left+visible.width()-2*inset;
                shortcuts.setClipBounds(new Rect(Math.max(0,left),0,Math.max(0,right),barWidth()));
                shortcuts.setAlpha(VolumePanelGeometry.clamp((progress-.25f)/.75f));
            }
            View list=recycler.get();if(list!=null)invalidateTree(list);
        }
        void invalidateTree(View v) {
            v.invalidate();
            if(v instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++)invalidateTree(group.getChildAt(i));
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
                cancelShortcutMotion();
                for(ImageView button:soundButtons) {button.setOnClickListener(null);button.setBackground(null);}
                zenControl.setOnClickListener(null);zenControl.setBackground(null);soundSegments.setBackground(null);
                for(View group:new View[]{soundSegments,zenControl}) {
                    group.removeOnLayoutChangeListener(materialLayout);group.removeOnAttachStateChangeListener(materialAttach);
                    Reflect.removeAdditionalInstanceField(group,"volume.proxy");
                    Reflect.removeAdditionalInstanceField(group,"volume.shortcut");
                }
                if(shortcuts.getParent() instanceof ViewGroup parent)parent.removeView(shortcuts);
            }
            if(observer!=null&&observer.isAlive())observer.removeOnPreDrawListener(frame);
            else if(r!=null)r.getViewTreeObserver().removeOnPreDrawListener(frame);
            if(r!=null&&r.getBackground()==surface)r.setBackground(surface.getDrawable());
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
            clip.rewind();clip.addRoundRect(x,y,x+b.width(),y+b.height(),corner,corner,Path.Direction.CW);
            int save=canvas.save();try {canvas.clipPath(clip);getDrawable().draw(canvas);}finally {canvas.restoreToCount(save);}
        }
        @Override public void getOutline(Outline outline) {outline.setEmpty();}
    }
    /** Dynamic fallback retains the native drawable so disabling a setting restores it immediately. */
    private static final class FlatSurface extends DrawableWrapper {
        final Context context;final String role;final WeakReference<View> host;final Path clip=new Path();final GradientDrawable fill=new GradientDrawable();
        FlatSurface(Drawable nativeDrawable,Context ctx,String r,View v){super(nativeDrawable);context=ctx;role=r;host=new WeakReference<>(v);}
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
            HookConfig.Area a=area();if(a==null||(!a.tint&&!a.cornerEnabled)){super.draw(canvas);return;}
            Rect b=materialBounds();float radius=radius(a,b);
            if(a.tint) {
                fill.setBounds(b);fill.setColor(a.color);fill.setCornerRadius(radius);fill.setAlpha(getAlpha());fill.draw(canvas);
            } else {
                clip.rewind();clip.addRoundRect(b.left,b.top,b.right,b.bottom,radius,radius,Path.Direction.CW);
                Drawable original=getDrawable();Rect old=original.getBounds();int l=old.left,t=old.top,r=old.right,bottom=old.bottom;
                Drawable.Callback callback=original.getCallback();int save=canvas.save();
                Map<GradientDrawable,Object> corners=new LinkedHashMap<>();
                try {
                    original.setCallback(null);original.setBounds(b);
                    // Clipping can cut a larger corner but cannot fill a smaller one.
                    // Update solid native shapes during this draw, then restore them.
                    if(a.cornerEnabled)roundNative(original,radius,corners);
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
