package io.github.baishangui.thunder;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.Outline;
import android.graphics.drawable.DrawableWrapper;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** ColorOS 17 sidebar styling and an anchored expansion using the native volume controller. */
final class VolumeHook {
    private static final String BASE="com.oplus.systemui.volume.";
    private static final String MATERIAL=BASE+"utils.material.";
    private static final Map<Object,Boolean> dialogs=Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View,int[]> layouts=Collections.synchronizedMap(new WeakHashMap<>());
    // Cleared on outside/back dismissal, native hide, detach, rotation, destroy and configuration refresh.
    @SuppressLint("StaticFieldLeak")
    private static Session session;

    static void install(ClassLoader loader) {
        bind("view",() -> Api102Runtime.hookAllConstructors(Reflect.findClass(BASE+"view.OplusVolumeDialogView",loader),new HookCallback() {
            @Override protected void afterHookedMethod(Param p) { if(!p.hasThrowable())dialogs.put(p.thisObject,true); }
        }));
        hook(loader,BASE+"view.OplusVolumeDialogView",new String[]{"updateRowsH","initDialog","showSliderVolume","updateState","adaptThemeDrawable"},new HookCallback() {
            @Override protected void afterHookedMethod(Param p) { if(!p.hasThrowable())updateDialog(p.thisObject); }
        });
        hook(loader,BASE+"OplusVolumeSeekBar",new String[]{"onAttachedToWindow"},new HookCallback() {
            @Override protected void afterHookedMethod(Param p) { styleBar((View)p.thisObject); }
        });
        hook(loader,BASE+"OplusVolumeSeekBar",new String[]{"onDraw"},new HookCallback() {
            @Override protected void beforeHookedMethod(Param p) {
                HookConfig c=ShadeHook.snapshot();if(!c.enabled)return;
                if(c.volumeBar.tint)temporary(p,"mBackgroundColor",c.volumeBar.color);
                if(c.volumeBar.cornerEnabled) {
                    View v=(View)p.thisObject;
                    float radius=Math.min(dp(v,c.volumeBar.corner),Math.min(v.getWidth(),v.getHeight())/2f);
                    for(String key:new String[]{"mCurBackgroundRadius","mCurProgressRadius"})temporary(p,key,radius);
                }
            }
            @Override protected void afterHookedMethod(Param p) {
                for(String key:new String[]{"mBackgroundColor","mCurBackgroundRadius","mCurProgressRadius"}) {
                    Object value=p.getObjectExtra(key);if(value!=null)Reflect.setObjectField(p.thisObject,key,value);
                }
            }
        });
        hook(loader,MATERIAL+"OplusVolumeBarMaterialHost",new String[]{"resolveCornerRadius"},new HookCallback() {
            @Override protected void afterHookedMethod(Param p) {
                HookConfig c=ShadeHook.snapshot();if(!c.enabled||!c.volumeBar.cornerEnabled)return;
                Object context=Reflect.getObjectField(p.thisObject,"context");
                View host=(View)Reflect.callMethod(context,"getMaterialHostView");
                p.setResult(Math.min((float)dp(host,c.volumeBar.corner),Math.min(host.getWidth(),host.getHeight())/2f));
            }
        });
        for(String factory:new String[]{"getVolumeBarBackground","getVolumeSettingsButtonBackground","getVolumeSideAccessoryBackground","getVolumePanelBackground"}) {
            String role=factory.equals("getVolumeBarBackground")?"volume-bar":factory.equals("getVolumePanelBackground")?"volume-panel":"volume-buttons";
            hook(loader,MATERIAL+"VolumeBlurManager",new String[]{factory},new HookCallback() {
                @Override protected void afterHookedMethod(Param p) throws Throwable {
                    if(p.hasThrowable()||!(p.getResult() instanceof Drawable))return;
                    Class<?> finder=Reflect.findClass(MATERIAL+"OplusVolumeBlurDrawableFinder",loader);
                    Method find=finder.getDeclaredMethod("findAutoBlurDrawable",Drawable.class);find.setAccessible(true);
                    Object auto=find.invoke(null,p.getResult());
                    if(auto!=null)ShadeHook.trackVolume(Reflect.callMethod(auto,"getViewBlurProxy"),role);
                    else p.setResult(new FlatSurface((Drawable)p.getResult(),(Context)p.args[0],role));
                }
            });
        }
        hook(loader,BASE+"OplusVolumeDialogImpl",new String[]{"expandPanel"},new HookCallback() {
            @Override protected void beforeHookedMethod(Param p) {
                HookConfig c=ShadeHook.snapshot();if(!c.enabled||!c.volumeCompact)return;
                // Intercept before native mExpanded and fullscreen flags change.
                try { if(open(p.thisObject))p.setResult(null); }
                catch(Throwable error) { close();ShadeHook.frameworkFailure("volume-expand",error); }
            }
        });
        hook(loader,BASE+"OplusVolumeDialogImpl",new String[]{"dismissH$1","destroy","onConfigChanged","onThemeChanged","onUiModeChanged"},new HookCallback() {
            @Override protected void beforeHookedMethod(Param p) { close(); }
        });
        hook(loader,BASE+"OplusVolumeDialogImpl",new String[]{"onStateChangedH"},new HookCallback() {
            @Override protected void afterHookedMethod(Param p) { if(session!=null&&session.owner==p.thisObject)session.update(); }
        });
        // Keep the native timeout while sliders are idle; touch keeps it alive while interacting.
        hook(loader,BASE+"OplusVolumeDialogImpl",new String[]{"computeTimeoutH$1"},new HookCallback() {
            @Override protected void afterHookedMethod(Param p) { if(session!=null&&session.owner==p.thisObject&&!p.hasThrowable())p.setResult(Math.max(((Number)p.getResult()).intValue(),session.tracking?16000:6000)); }
        });
    }
    private static void temporary(HookCallback.Param p,String key,Object value) {
        p.setObjectExtra(key,Reflect.getObjectField(p.thisObject,key));Reflect.setObjectField(p.thisObject,key,value);
    }
    private static int dp(View v,int value) { return Math.round(value*v.getResources().getDisplayMetrics().density); }
    private static void size(View v,int width,int height,boolean custom) {
        ViewGroup.LayoutParams lp=v.getLayoutParams();if(lp==null)return;
        if(custom)layouts.putIfAbsent(v,new int[]{lp.width,lp.height});
        int[] original=layouts.get(v);if(!custom&&original==null)return;
        int w=custom?width:original[0],h=custom?height:original[1];
        if(lp.width!=w||lp.height!=h){lp.width=w;lp.height=h;v.setLayoutParams(lp);}
        if(!custom)layouts.remove(v);
    }
    private static void styleBar(View v) {
        HookConfig c=ShadeHook.snapshot();boolean custom=c.enabled&&c.volumeSize;
        size(v,dp(v,c.volumeWidth),dp(v,c.volumeHeight),custom);
        // Native geometry and touch mapping must match the measured dimensions.
        for(String key:new String[]{"mBackgroundWidth","mProgressWidth","mSeekbarMinWidth","mMaxHeight"}) {
            String saved="thunder.volume."+key;
            Object original=Reflect.getAdditionalInstanceField(v,saved);
            if(custom) {
                if(original==null)Reflect.setAdditionalInstanceField(v,saved,Reflect.getObjectField(v,key));
                Reflect.setObjectField(v,key,key.equals("mMaxHeight")?(Object)dp(v,c.volumeHeight):key.equals("mSeekbarMinWidth")?(Object)dp(v,c.volumeWidth):(Object)(float)dp(v,c.volumeWidth));
            } else if(original!=null) {Reflect.setObjectField(v,key,original);Reflect.removeAdditionalInstanceField(v,saved);}
        }
        Reflect.callMethod(v,"ensureSize");v.invalidate();
    }
    private static void updateDialog(Object dialog) {
        dialogs.put(dialog,true);HookConfig c=ShadeHook.snapshot();
        Object owner=Reflect.getObjectField(dialog,"volumeInteractor");
        boolean side=!Reflect.getBooleanField(owner,"mExpanded");
        if(!side)return;
        Object rows=Reflect.getObjectField(owner,"mRows");
        for(Object row:(Iterable<?>)rows) {
            View bar=(View)Reflect.getObjectField(row,"slider");if(bar==null)continue;
            styleBar(bar);
            View frame=(View)Reflect.getObjectField(row,"sliderFrame");
            View root=(View)Reflect.getObjectField(row,"view");
            boolean custom=c.enabled&&c.volumeSize;
            if(frame!=null)size(frame,dp(bar,c.volumeWidth)+frame.getPaddingLeft()+frame.getPaddingRight(),dp(bar,c.volumeHeight)+frame.getPaddingTop()+frame.getPaddingBottom(),custom);
            if(root!=null)size(root,dp(bar,c.volumeWidth)+dp(bar,10),ViewGroup.LayoutParams.WRAP_CONTENT,custom);
        }
        View recycler=(View)Reflect.getObjectField(dialog,"mVolumeRecyclerview");
        if(recycler!=null)size(recycler,ViewGroup.LayoutParams.MATCH_PARENT,dp(recycler,c.volumeHeight+24),c.enabled&&c.volumeSize);
        for(String key:new String[]{"mMoreRowStreamLl","mAppVolumeAdjustFl","mODICaptionsView","mSettingsButton"}) {
            View button=(View)Reflect.getObjectField(dialog,key);if(button==null)continue;
            size(button,dp(button,c.volumeButton),dp(button,c.volumeButton),c.enabled&&c.volumeButtonSize);
            button.invalidate();button.invalidateOutline();
        }
    }
    static void refresh() {
        close();
        for(Object dialog:new ArrayList<>(dialogs.keySet()))try { updateDialog(dialog); }
        catch(Throwable error) { ShadeHook.frameworkFailure("volume-refresh",error); }
    }
    private static boolean open(Object owner) {
        if(session!=null){close();return true;}
        Object row=Reflect.callMethod(owner,"getActiveRow");if(row==null)return false;
        View anchor=(View)Reflect.getObjectField(row,"slider");
        if(anchor==null||!anchor.isAttachedToWindow()||!anchor.isShown())return false;
        if(Boolean.TRUE.equals(Reflect.callMethod(Reflect.getObjectField(owner,"mOplusVolumeDialogView"),"isDismissing")))return false;
        Session next=new Session(owner,anchor);
        session=next;
        try {next.show();} catch(Throwable error) {close();throw error;}
        ShadeHook.observed("volume-compact","anchored; streams="+next.controls.size());return true;
    }
    private static void close() { Session old=session;session=null;if(old!=null)old.dismiss(); }
    private static final class Control {
        final Object row;final SeekBar slider;final TextView label;final String name;
        Control(Object r,SeekBar s,TextView l,String n){row=r;slider=s;label=l;name=n;}
    }
    private static final class Session {
        final Object owner,controller;final View anchor;final LinearLayout content;final ScrollView scroll;
        final PopupWindow popup;final ArrayList<Control> controls=new ArrayList<>();
        final ArrayList<View> actionButtons=new ArrayList<>();
        final View.OnLayoutChangeListener layoutListener=(v,l,t,r,b,ol,ot,or,ob)->position();
        final View.OnAttachStateChangeListener attachListener=new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) { }
            @Override public void onViewDetachedFromWindow(View v) { close(); }
        };
        boolean tracking;
        Session(Object o,View a) {
            owner=o;anchor=a;controller=Reflect.getObjectField(o,"mController");Context ctx=a.getContext();
            content=new LinearLayout(ctx);content.setOrientation(LinearLayout.VERTICAL);int padding=dp(a,16);content.setPadding(padding,padding,padding,padding);
            scroll=new ScrollView(ctx) {
                @Override public boolean dispatchTouchEvent(MotionEvent event) {touch();return super.dispatchTouchEvent(event);}
            };
            scroll.setFillViewport(false);scroll.addView(content);
            popup=new PopupWindow(scroll,dp(a,300),ViewGroup.LayoutParams.WRAP_CONTENT,true);
            popup.setBackgroundDrawable(new ColorDrawable(0));popup.setOutsideTouchable(true);popup.setClippingEnabled(true);
            popup.setIsLaidOutInScreen(true);popup.setTouchModal(false);
            popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);popup.setElevation(dp(a,12));
            TextView title=text(ctx,"音量",18);content.addView(title);
            for(Object row:(Iterable<?>)Reflect.getObjectField(o,"mRows")) {
                Object ss=Reflect.getObjectField(row,"ss");
                if(ss==null||Reflect.getBooleanField(row,"isMultiMediaVolume")||(!Reflect.getBooleanField(row,"isShowInPanel")&&!Reflect.getBooleanField(row,"important")))continue;
                int stream=Reflect.getIntField(row,"stream");
                String name=switch(stream){case 0->"通话";case 1->"系统";case 2->"铃声";case 3->"媒体";case 4->"闹钟";case 5->"通知";case 6->"蓝牙通话";case 10->"无障碍";default->"音量";};
                TextView label=text(ctx,name,14);content.addView(label);
                SeekBar slider=new SeekBar(ctx);slider.setContentDescription(name+"音量");
                content.addView(slider,new LinearLayout.LayoutParams(-1,dp(a,48)));
                controls.add(new Control(row,slider,label,name));
                slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    @Override public void onProgressChanged(SeekBar s,int value,boolean user) {
                        if(!user)return;
                        Reflect.callMethod(controller,"setActiveStream",stream);
                        Reflect.callMethod(controller,"setStreamVolume",stream,value);touch();
                    }
                    @Override public void onStartTrackingTouch(SeekBar s) {tracking=true;touch();}
                    @Override public void onStopTrackingTouch(SeekBar s) {tracking=false;touch();}
                });
            }
            Object dialog=Reflect.getObjectField(owner,"mOplusVolumeDialogView");
            View settings=(View)Reflect.getObjectField(dialog,"mSettingsButton");
            if(settings!=null) {
                TextView soundSettings=text(ctx,"声音设置",15);soundSettings.setGravity(Gravity.CENTER);soundSettings.setMinHeight(dp(a,48));
                soundSettings.setBackground(surface(ctx,ShadeHook.snapshot().volumeButtons,20));
                soundSettings.setOnClickListener(v->{close();settings.performClick();});content.addView(soundSettings);actionButtons.add(soundSettings);
            }
            TextView collapse=text(ctx,"收起",15);collapse.setGravity(Gravity.CENTER);collapse.setMinHeight(dp(a,48));
            collapse.setOnClickListener(v->close());collapse.setBackground(surface(ctx,ShadeHook.snapshot().volumeButtons,20));content.addView(collapse);actionButtons.add(collapse);
            popup.setOnDismissListener(()->{tracking=false;anchor.removeOnLayoutChangeListener(layoutListener);anchor.removeOnAttachStateChangeListener(attachListener);if(session==this)session=null;});
            update();
        }
        void touch(){Reflect.callMethod(owner,"rescheduleTimeoutH");}
        void update() {
            for(Control control:controls) {
                Object ss=Reflect.getObjectField(control.row,"ss");if(ss==null){control.slider.setEnabled(false);continue;}
                int min=Reflect.getIntField(ss,"levelMin"),max=Reflect.getIntField(ss,"levelMax"),level=Reflect.getIntField(ss,"level");
                control.slider.setMax(Math.max(min,max));control.slider.setMin(min);
                if(!tracking)control.slider.setProgress(level);
                Object nativeSlider=Reflect.getObjectField(control.row,"slider");
                control.slider.setEnabled(Reflect.getBooleanField(control.row,"enable")&&Reflect.getBooleanField(control.row,"touchable")&&!Boolean.TRUE.equals(Reflect.callMethod(nativeSlider,"getDisabled")));
                control.label.setText(String.format(java.util.Locale.getDefault(),"%s  %d / %d",control.name,level,max));
            }
        }
        // Position uses absolute physical screen coordinates, including in RTL layouts.
        @SuppressLint("RtlHardcoded")
        void show() {
            if(controls.isEmpty())throw new IllegalStateException("No native volume streams available");
            HookConfig.Area area=ShadeHook.snapshot().volumePanel;
            scroll.setBackground(surface(anchor.getContext(),area,28));
            scroll.setClipToOutline(true);
            popup.showAtLocation(anchor,Gravity.TOP|Gravity.LEFT,0,0);
            anchor.addOnLayoutChangeListener(layoutListener);anchor.addOnAttachStateChangeListener(attachListener);
            position();touch();
            // Popup children receive their ViewRootImpl during the first attachment traversal.
            scroll.post(() -> {
                if(!popup.isShowing())return;
                HookConfig c=ShadeHook.snapshot();
                applyPopupSurface(scroll,c.volumePanel,28,"panel");
                for(View button:actionButtons)applyPopupSurface(button,c.volumeButtons,20,"buttons");
            });
        }
        void position() {
            if(!popup.isShowing())return;
            Rect frame=new Rect();anchor.getWindowVisibleDisplayFrame(frame);
            int[] location=new int[2];anchor.getLocationOnScreen(location);
            content.measure(View.MeasureSpec.makeMeasureSpec(Math.min(dp(anchor,300),frame.width()),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
            VolumePanelGeometry.Bounds b=VolumePanelGeometry.place(frame.left+dp(anchor,8),frame.top+dp(anchor,8),frame.right-dp(anchor,8),frame.bottom-dp(anchor,8),location[0],location[1],location[0]+anchor.getWidth(),location[1]+anchor.getHeight(),dp(anchor,300),content.getMeasuredHeight(),dp(anchor,8));
            popup.update(b.x(),b.y(),b.width(),b.height());

        }
        void dismiss(){popup.dismiss();}
    }
    private static void applyPopupSurface(View view,HookConfig.Area area,int corner,String role) {
        if(!area.blur)return;
        try {
            Object root=Reflect.callMethod(view,"getViewRootImpl");
            Object blur=Reflect.callMethod(root,"createBackgroundBlurDrawable");
            Reflect.callMethod(blur,"setBlurRadius",area.radius);
            Reflect.callMethod(blur,"setCornerRadius",(float)dp(view,area.cornerEnabled?area.corner:corner));
            view.setBackground(new android.graphics.drawable.LayerDrawable(new Drawable[]{(Drawable)blur,surface(view.getContext(),area,corner)}));
            ShadeHook.observed("volume-popup-"+role+"-blur",area.radius+"px");
        } catch(Throwable error) {ShadeHook.frameworkFailure("volume-popup-"+role+"-blur",error);}
    }
    /** Dynamic fallback retains the native drawable so disabling a setting restores it immediately. */
    private static final class FlatSurface extends DrawableWrapper {
        final Context context;final String role;final Path clip=new Path();final GradientDrawable fill=new GradientDrawable();
        FlatSurface(Drawable nativeDrawable,Context ctx,String r){super(nativeDrawable);context=ctx;role=r;}
        HookConfig.Area area() {
            HookConfig c=ShadeHook.snapshot();return !c.enabled?null:role.equals("volume-bar")?c.volumeBar:role.equals("volume-panel")?c.volumePanel:c.volumeButtons;
        }
        float radius(HookConfig.Area a) {
            float nativeRadius=getDrawable() instanceof GradientDrawable?((GradientDrawable)getDrawable()).getCornerRadius():28*context.getResources().getDisplayMetrics().density;
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
    private static TextView text(Context ctx,String value,int size) {
        TextView v=new TextView(ctx);v.setText(value);v.setTextSize(size);
        boolean dark=(ctx.getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;
        HookConfig.Area area=ShadeHook.snapshot().volumePanel;
        int color=area.tint&&(area.color>>>24)>127?AdaptiveTextColor.foreground(area.color):(dark?0xfff5f5f5:0xff202124);
        v.setTextColor(color);return v;
    }
    private static Drawable surface(Context ctx,HookConfig.Area a,int corner) {
        GradientDrawable bg=new GradientDrawable();
        boolean dark=(ctx.getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;
        bg.setColor(a.tint?a.color:(dark?0xee252525:0xeef4f4f4));
        bg.setCornerRadius((a.cornerEnabled?a.corner:corner)*ctx.getResources().getDisplayMetrics().density);return bg;
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
