package io.github.baishangui.thunder;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.DrawableWrapper;
import android.graphics.drawable.GradientDrawable;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
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
                if(c.enabled&&c.volumeButtonSize&&button!=null)p.setResult(dp(button,c.volumeButton)/2f);
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
                if(label!=null)size(label,panel==null?0:panel.rowWidth+panel.gap-dp(item,4),0,panel!=null,true,false);
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
                        Reflect.setObjectField(p.thisObject,"cachedPanelBiasTranslationY",y);p.setResult(y);
                    } else {
                        Object adapter=Reflect.getObjectField(p.thisObject,"adapter");panel.arrange(adapter);
                        int index=((Number)Reflect.callMethod(p.args[0],"getAbsoluteAdapterPosition")).intValue();
                        if(index<0)index=((Number)Reflect.callMethod(p.args[0],"getLayoutPosition")).intValue();
                        if(index<0)index=((Number)Reflect.callMethod(p.args[0],"getOldPosition")).intValue();
                        if(index<0)return;
                        // Native LayoutManager keeps physical LTR order on both key sides.
                        float x=VolumePanelGeometry.rowX(panel.bounds,index,panel.count,item.getWidth(),panel.inset,panel.gap);
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
            @Override protected void afterHookedMethod(Param p) { restorePanel(p.thisObject); }
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
                    Class<?> finder=Reflect.findClass(MATERIAL+"OplusVolumeBlurDrawableFinder",loader);
                    Method find=finder.getDeclaredMethod("findAutoBlurDrawable",Drawable.class);find.setAccessible(true);
                    Object auto=find.invoke(null,p.getResult());
                    if(auto!=null) {
                        Object proxy=Reflect.callMethod(auto,"getViewBlurProxy"),blur=Reflect.callMethod(proxy,"getBlurConfig");
                        Reflect.setAdditionalInstanceField(blur,"volume.host",new WeakReference<>((View)p.args[2]));
                        ShadeHook.trackVolume(proxy,role);
                    }
                    else p.setResult(new FlatSurface((Drawable)p.getResult(),(Context)p.args[0],role));
                }
            });
        }
        // These small native getters are called by ART-compiled animation code.
        bind("button-animation-deopt",()->Api102Runtime.deoptimizeCallers(Reflect.findClass(BASE+"view.OplusVolumeDialogView",loader),"startBtnThickAnim"));
        bind("animation-deopt",()->Api102Runtime.deoptimizeCallers(Reflect.findClass(BASE+"anim.VolumeItemAnimator",loader),"animateAddImpl","animateMoveImpl","runPendingAnimations"));
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
        int width=panel==null?host.getWidth():panel.bounds.width(),height=panel==null?host.getHeight():panel.bounds.height();
        return Math.min(radius,Math.min(width,height)/2f);
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
        for(String key:new String[]{"mMoreRowStreamLl","mAppVolumeAdjustFl","mODICaptionsView","mSettingsButton"}) {
            View button=view(dialog,key);if(button==null)continue;
            int h=dp(button,c.volumeButton),w=h;
            int[] original=layouts.get(button);int ow=original==null?button.getLayoutParams().width:original[0],oh=original==null?button.getLayoutParams().height:original[1];
            if(key.equals("mSettingsButton")&&ow>0&&oh>0)w=Math.round(h*(float)ow/oh);
            size(button,w,h,c.enabled&&c.volumeButtonSize,true,true);
            button.invalidate();button.invalidateOutline();
        }
        if(panel!=null) {panel.arrange(Reflect.getObjectField(dialog,"volumeListAdapter"));panel.mount();}
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
        Compact panel=new Compact(dialog,root,recycler,item);panels.put(dialog,panel);
        panel.arrange(Reflect.getObjectField(dialog,"volumeListAdapter"));
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

    private static final String[] CONSTRAINTS={"leftToLeft","leftToRight","rightToLeft","rightToRight","startToStart","startToEnd","endToStart","endToEnd","topToTop","topToBottom","bottomToTop","bottomToBottom","leftMargin","rightMargin","topMargin","bottomMargin","width","height","horizontalBias","verticalBias"};
    private static final class Compact {
        final HookConfig config;
        final WeakReference<Object> dialog;
        final WeakReference<View> root,recycler;
        final Map<View,Map<String,Object>> accessories=new WeakHashMap<>();
        final Rect usable=new Rect();
        final int anchorX,anchorY,anchorRight,inset,gap,header,footer,rowHeight;
        VolumePanelGeometry.Bounds bounds;
        int rowTop,count,arrangedCount=-1;
        NativePanelSurface surface;
        boolean outsideGesture;
        int rowWidth;
        Compact(Object d,View r,View list,View item) {
            config=ShadeHook.snapshot();dialog=new WeakReference<>(d);root=new WeakReference<>(r);recycler=new WeakReference<>(list);
            int[] xy=location(item);anchorX=Math.round(xy[0]-item.getTranslationX());anchorY=Math.round(xy[1]-item.getTranslationY());anchorRight=anchorX+item.getWidth();
            r.getWindowVisibleDisplayFrame(usable);int[] origin=location(r);
            usable.left=Math.max(usable.left,origin[0]);usable.top=Math.max(usable.top,origin[1]);
            usable.right=Math.min(usable.right,origin[0]+r.getWidth());usable.bottom=Math.min(usable.bottom,origin[1]+r.getHeight());
            inset=dp(r,20);gap=dp(r,12);header=dp(r,116);
            View button=view(d,"mSettingsButton"),label=view(d,"mVolumeSettingText");
            footer=Math.max(dp(r,88),height(button)+height(label)+dp(r,28));
            rowHeight=list.getHeight();
        }
        void arrange(Object adapter) {
            View r=root.get(),list=recycler.get();Object d=dialog.get();if(r==null||list==null||d==null)return;
            int n=((java.util.List<?>)Reflect.callMethod(adapter,"getPanelShowList")).size();
            if(n==0)n=((Number)Reflect.callMethod(adapter,"getItemCount")).intValue();
            n=Math.max(1,n);if(arrangedCount==n)return;arrangedCount=n;count=n;
            HookConfig c=config;rowWidth=c.enabled&&c.volumeSize?dp(r,c.volumeWidth+10):dp(r,56);
            int usableWidth=Math.max(1,usable.width()-dp(r,16));
            rowWidth=Math.min(rowWidth,Math.max(1,(usableWidth-2*inset-(count-1)*gap)/count));
            int margin=dp(r,8),available=Math.max(1,usable.height()-2*margin);
            bounds=VolumePanelGeometry.place(usable.left+margin,usable.top+margin,usable.right-margin,usable.bottom-margin,anchorX,anchorY-header+inset+dp(r,16),anchorRight,anchorY+rowHeight,
                    count*rowWidth+(count-1)*gap+2*inset,Math.min(available,header+rowHeight+footer),inset);
            rowTop=Math.min(Math.max(bounds.y()+header,anchorY+dp(r,16)),bounds.y()+bounds.height()-footer-rowHeight);
            // Very short landscape windows need a shorter native row viewport.
            int fit=Math.max(dp(r,64),bounds.height()-header-footer);
            if(fit<rowHeight){size(list,0,fit,true,false,true);rowTop=bounds.y()+header;}
            int[] origin=location(r);
            int titleTop=bounds.y()+dp(r,16),settingsTop=bounds.y()+bounds.height()-footer;
            placeAccessory(d,ACCESSORIES[0],titleTop,origin);
            View titleIcon=view(d,ACCESSORIES[0]);titleTop+=(titleIcon==null?dp(r,24):height(titleIcon))+dp(r,4);
            placeAccessory(d,ACCESSORIES[1],titleTop,origin);
            View title=view(d,ACCESSORIES[1]);placeAccessory(d,ACCESSORIES[2],titleTop+height(title)+dp(r,2),origin);
            placeAccessory(d,ACCESSORIES[3],settingsTop,origin);
            View button=view(d,ACCESSORIES[3]);placeAccessory(d,ACCESSORIES[4],settingsTop+height(button)+dp(r,6),origin);
            if(surface!=null)surface.invalidateSelf();
        }
        void placeAccessory(Object d,String key,int top,int[] origin) {
            View v=view(d,key);if(v==null)return;Object lp=v.getLayoutParams();if(lp==null)return;
            Map<String,Object> saved=accessories.computeIfAbsent(v,k->{
                Map<String,Object> values=new LinkedHashMap<>();for(String field:CONSTRAINTS)values.put(field,Reflect.getObjectField(lp,field));
                values.put("relativeMargins",Reflect.callMethod(lp,"isMarginRelative"));values.put("marginStart",Reflect.callMethod(lp,"getMarginStart"));values.put("marginEnd",Reflect.callMethod(lp,"getMarginEnd"));return values;
            });
            for(String keyField:CONSTRAINTS)if(keyField.contains("To"))Reflect.setIntField(lp,keyField,-1);
            Reflect.setIntField(lp,"leftToLeft",0);Reflect.setIntField(lp,"topToTop",0);
            int width=((Number)saved.get("width")).intValue();
            if(width<=0) {
                v.measure(View.MeasureSpec.makeMeasureSpec(Math.max(1,bounds.width()-2*inset),View.MeasureSpec.AT_MOST),View.MeasureSpec.makeMeasureSpec(header,View.MeasureSpec.AT_MOST));
                width=v.getMeasuredWidth();
            }
            width=Math.min(Math.max(1,width),bounds.width()-2*inset);
            Reflect.setIntField(lp,"width",width);
            Reflect.setIntField(lp,"leftMargin",bounds.x()-origin[0]+(bounds.width()-width)/2);
            Reflect.setIntField(lp,"topMargin",top-origin[1]);
            Reflect.setIntField(lp,"rightMargin",0);Reflect.setIntField(lp,"bottomMargin",0);
            Reflect.callMethod(lp,"setMarginStart",Integer.MIN_VALUE);Reflect.callMethod(lp,"setMarginEnd",Integer.MIN_VALUE);
            v.setLayoutParams((ViewGroup.LayoutParams)lp);
        }
        void mount() {
            View r=root.get();if(r==null||bounds==null)return;
            Drawable background=r.getBackground();if(background==null||background==surface)return;
            surface=new NativePanelSurface(background,this);r.setBackground(surface);
        }
        boolean contains(float x,float y) {
            if(bounds!=null&&x>=bounds.x()&&x<bounds.x()+bounds.width()&&y>=bounds.y()&&y<bounds.y()+bounds.height())return true;
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
            if(r!=null&&r.getBackground()==surface)r.setBackground(surface.getDrawable());
            for(Map.Entry<View,Map<String,Object>> entry:accessories.entrySet()) {
                View v=entry.getKey();Object lp=v.getLayoutParams();
                for(String field:CONSTRAINTS)Reflect.setObjectField(lp,field,entry.getValue().get(field));
                Reflect.callMethod(lp,"setMarginStart",entry.getValue().get("marginStart"));Reflect.callMethod(lp,"setMarginEnd",entry.getValue().get("marginEnd"));
                v.setLayoutParams((ViewGroup.LayoutParams)lp);
            }
            if(list!=null)size(list,0,0,false,false,true);
        }
    }
    /** Child drawable identity stays native: the native blur/alpha animator still drives it. */
    private static final class NativePanelSurface extends DrawableWrapper {
        final Compact panel;final Path clip=new Path();
        NativePanelSurface(Drawable original,Compact p){super(original);panel=p;}
        @Override public void draw(Canvas canvas) {
            View root=panel.root.get();if(root==null||panel.bounds==null)return;
            int[] origin=location(root);VolumePanelGeometry.Bounds b=panel.bounds;
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
        final Context context;final String role;final Path clip=new Path();final GradientDrawable fill=new GradientDrawable();
        FlatSurface(Drawable nativeDrawable,Context ctx,String r){super(nativeDrawable);context=ctx;role=r;}
        HookConfig.Area area() {
            HookConfig c=ShadeHook.snapshot();return !c.enabled?null:role.equals("volume-bar")?c.volumeBar:role.equals("volume-panel")?c.volumePanel:c.volumeButtons;
        }
        float radius(HookConfig.Area a) {
            Outline nativeOutline=new Outline();getDrawable().getOutline(nativeOutline);
            float nativeRadius=getDrawable() instanceof GradientDrawable?((GradientDrawable)getDrawable()).getCornerRadius():Math.max(0f,nativeOutline.getRadius());
            return Math.min(a.cornerEnabled?a.corner*context.getResources().getDisplayMetrics().density:nativeRadius,Math.min(getBounds().width(),getBounds().height())/2f);
        }
        @Override public void draw(Canvas canvas) {
            HookConfig.Area a=area();if(a==null||(!a.tint&&!a.cornerEnabled)){super.draw(canvas);return;}
            if(a.tint) {
                fill.setBounds(getBounds());fill.setColor(a.color);fill.setCornerRadius(radius(a));fill.setAlpha(getAlpha());fill.draw(canvas);
            } else {
                Rect b=getBounds();float radius=radius(a);clip.rewind();clip.addRoundRect(b.left,b.top,b.right,b.bottom,radius,radius,Path.Direction.CW);
                int save=canvas.save();try {canvas.clipPath(clip);super.draw(canvas);}finally {canvas.restoreToCount(save);}
            }
        }
        @Override public void getOutline(Outline outline) {
            HookConfig.Area a=area();if(a==null||!a.cornerEnabled){super.getOutline(outline);return;}
            Rect b=getBounds();outline.setRoundRect(b.left,b.top,b.right,b.bottom,radius(a));
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
