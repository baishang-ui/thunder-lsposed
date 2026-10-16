package io.github.baishangui.thunder;

import android.os.Bundle;
import android.animation.ValueAnimator;
import android.view.*;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.*;
import androidx.recyclerview.widget.RecyclerView;
import androidx.constraintlayout.widget.ConstraintLayout.LayoutParams;
import com.oplus.systemui.volume.*;
import com.oplus.systemui.volume.view.*;
import com.oplus.systemui.volume.anim.VolumeItemAnimator;
import com.oplus.systemui.volume.utils.material.VolumePlatformBlurHostHelper;
import com.oplusos.systemui.common.blurability.*;
import com.oplusos.systemui.common.blurability.drawable.AutoBlurDrawable;

/** Real callbacks with native-shaped stand-ins. Does not claim device animation/render fidelity. */
public final class VolumePanelCheck {
    static final String BASE="com.oplus.systemui.volume.",OWNER=BASE+"OplusVolumeDialogImpl",VIEW=BASE+"view.OplusVolumeDialogView",BAR=BASE+"OplusVolumeSeekBar",ANIM=BASE+"anim.VolumeItemAnimator",HELPER=BASE+"utils.material.VolumePlatformBlurHostHelper";
    static int checks;
    static final int LABEL=6045;
    static android.content.res.Resources volumeResources(){return new android.content.res.Resources(){@Override public int getIdentifier(String name,String type,String pkg){if(type.equals("drawable"))return Math.abs(name.hashCode());return name.equals("volume_text")?LABEL:0;}};}
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;System.out.println("PASS: "+message);}
    static HookCallback.Param before(String key,Object owner,Object... args)throws Throwable{HookCallback.Param p=new HookCallback.Param();p.thisObject=owner;p.args=args;Api102Runtime.callbacks.get(key).before(p);return p;}
    static Bundle config() {
        Bundle b=new Bundle();b.putBoolean("enabled",true);b.putBoolean("volume_compact",true);b.putBoolean("volume_size",true);b.putInt("volume_width",76);b.putInt("volume_height",320);
        b.putBoolean("volume_button_size",true);b.putInt("volume_button",60);
        b.putBoolean("volume_buttons_tint",true);b.putInt("volume_buttons_color",0xff010203);b.putInt("volume_buttons_radius",7);b.putInt("volume_buttons_corner",40);
        b.putBoolean("volume_bar_tint",true);b.putInt("volume_bar_color",0x4188aacc);b.putBoolean("volume_bar_blur",true);b.putInt("volume_bar_radius",123);
        b.putBoolean("volume_bar_corner_enabled",true);b.putInt("volume_bar_corner",18);b.putBoolean("volume_panel_blur",true);b.putInt("volume_panel_radius",80);return b;
    }
    static RecyclerView.ViewHolder holder(OplusVolumeDialogView d,int position,int x,int y) {
        ViewGroup item=new ViewGroup();item.actualWidth=154;item.actualHeight=300;item.screenX=x;item.screenY=y;item.layout=new ViewGroup.LayoutParams(154,-1);d.mVolumeRecyclerview.addView(item);
        View label=new View();label.id=LABEL;label.setVisibility(4);item.res=volumeResources();item.addView(label);
        return new RecyclerView.ViewHolder(item,position);
    }
    static float endpoint(String method,VolumeItemAnimator a,RecyclerView.ViewHolder h)throws Throwable {
        return ((Number)CloudCheck.after(ANIM+"#"+method,a,999f,h)).floatValue();
    }
    static void sizeScenario(int mainHeight,int initialMode,int count,boolean noVibrator,boolean hardwareSlider)throws Throwable {
        Bundle settings=new Bundle();settings.putBoolean("enabled",true);settings.putBoolean("volume_compact",true);CloudCheck.config(settings);
        com.oplusos.systemui.common.feature.FeatureOption.alertSlider=hardwareSlider;
        OplusVolumeDialogImpl owner=new OplusVolumeDialogImpl();owner.mController.vibrator=!noVibrator;owner.mState.ringerModeInternal=initialMode;
        OplusVolumeDialogView d=owner.mOplusVolumeDialogView;d.mDialogView.res=volumeResources();d.volumeListAdapter.count=count;
        OplusVolumeRow row=new OplusVolumeRow();owner.mRows.add(row);d.volumeListAdapter.rows.add(row);
        RecyclerView.ViewHolder h=holder(d,0,900,300);((ViewGroup)h.itemView).addView(row.view);row.sliderFrame.parent=row.view;row.slider.parent=row.sliderFrame;
        row.slider.actualHeight=mainHeight;row.slider.screenY=311;row.sliderFrame.setPadding(14,11,14,22);
        row.slider.mBackgroundWidth=row.slider.mCurBackgroundWidth=209;
        d.mVolumeRecyclerview.layout.height=mainHeight+90;
        GradientDrawable original=new GradientDrawable();d.mDialogView.setBackground(original);
        before(VIEW+"#expandPanel",d);d.expandPanel();CloudCheck.after(HELPER+"#mountPanelBackground",new VolumePlatformBlurHostHelper(d),null);
        d.mDialogView.observer.dispatchOnPreDraw();d.mDialogView.getBackground().draw(new Canvas());
        CloudCheck.after(ANIM+"#getEndX",new VolumeItemAnimator(d.volumeListAdapter),999f,h);
        before(BAR+"#onMeasure",row.slider,0,0);
        check(d.mVolumeRecyclerview.layout.height==mainHeight+33,"measured main height "+mainHeight+" is inherited without a fixed expanded length");
        check(original.getBounds().top==311-22&&original.getBounds().height()==mainHeight+row.slider.layout.width+66,"panel remains anchored at the same visible Y and wraps track plus shortcuts for height "+mainHeight);
        check(original.getBounds().width()<=1036&&row.slider.layout.width<=(count>4?208:209),"only the real usable screen width limits the track group");
        ViewGroup quick=(ViewGroup)d.mDialogView.getChildAt(d.mDialogView.getChildCount()-1);
        // Detach the driver to exercise the native background-progress fallback at the endpoint.
        h.itemView.attached=false;d.mDialogView.observer.dispatchOnPreDraw();
        ViewGroup segments=(ViewGroup)quick.getChildAt(0);
        segments.getChildAt(2).performClick();
        check(owner.mState.ringerModeInternal==((hardwareSlider||noVibrator)?2:1),"direct vibration segment respects vibrator capability and physical alert slider");
        segments.getChildAt(3).performClick();
        check(owner.mState.ringerModeInternal==(hardwareSlider?2:0),"silent segment remains directly selectable without a vibrator");
        CloudCheck.after(VIEW+"#resetSliderState",d,null);
        com.oplusos.systemui.common.feature.FeatureOption.alertSlider=false;
    }
    static void shortcutMaterialScenario(boolean nativeBlur,int corner)throws Throwable {
        Bundle settings=config();settings.putInt("volume_bar_corner",corner);CloudCheck.config(settings);
        com.oplus.systemui.volume.utils.material.VolumeBlurManager.INSTANCE.shortcutBlur=nativeBlur;
        OplusVolumeDialogImpl owner=new OplusVolumeDialogImpl();OplusVolumeDialogView d=owner.mOplusVolumeDialogView;
        d.mDialogView.res=volumeResources();OplusVolumeRow row=new OplusVolumeRow();owner.mRows.add(row);d.volumeListAdapter.rows.add(row);
        RecyclerView.ViewHolder h=holder(d,0,900,300);((ViewGroup)h.itemView).addView(row.view);row.sliderFrame.parent=row.view;row.slider.parent=row.sliderFrame;
        row.slider.actualHeight=600;before(BAR+"#onMeasure",row.slider,0,0);
        before(VIEW+"#expandPanel",d);d.expandPanel();
        ViewGroup quick=(ViewGroup)d.mDialogView.getChildAt(d.mDialogView.getChildCount()-1);
        float expected=Math.min(Math.round(corner*2.75f),row.slider.mBackgroundWidth/2);
        for(int i=0;i<2;i++) {
            ViewGroup group=(ViewGroup)quick.getChildAt(i);Drawable background=group.getBackground();
            if(i==1)background=((DrawableWrapper)background).getDrawable();
            GradientDrawable selection=(GradientDrawable)group.getChildAt(0).getBackground();
            check(selection.getColor().getDefaultColor()==0&&selection.strokeWidth>0&&selection.radius==expected,"selected shortcut keeps shared tint and follows corner "+corner);
            RippleDrawable ripple=(RippleDrawable)(i==1?group.getBackground():group.getChildAt(1).getBackground());
            check(((GradientDrawable)ripple.mask).getCornerRadius()==expected,"shortcut ripple mask follows the shared corner "+corner);
            if(nativeBlur) {
                AutoBlurDrawable auto=(AutoBlurDrawable)background;BlurConfig blur=auto.proxy.config;
                check(group.getWidth()==0&&"volume-buttons".equals(Reflect.getAdditionalInstanceField(blur,"thunder.role")),"actual unlaid shortcut background is registered with shared material role");
                HookCallback.Param submission=before(QsTintCheck.B+"ViewBlurProxy#applyConfigToPlatformBlur",auto.proxy);
                BlurMixConfig.BlurMixSingleWithShader mix=(BlurMixConfig.BlurMixSingleWithShader)blur.platformMixConfig;
                check(blur.cornerRadius==expected&&blur.blurRadius==123&&mix.backgroundShaderParam.topLayerColor==0x4188aacc,"unlaid shortcut submits shared corner, blur and exact ARGB for "+corner);
                QsTintCheck.after(QsTintCheck.B+"ViewBlurProxy#applyConfigToPlatformBlur",submission);
                int applied=auto.proxy.applied;
                group.actualWidth=group.layout.width;group.actualHeight=group.layout.height;
                for(View.OnLayoutChangeListener listener:group.layoutListeners)listener.onLayoutChange(group,0,0,group.actualWidth,group.actualHeight,0,0,0,0);
                for(View.OnAttachStateChangeListener listener:group.attachListeners)listener.onViewAttachedToWindow(group);
                check(auto.proxy.applied==applied+2&&auto.proxy.invalidatedPaths>=2,"layout and attachment reapply shortcut native material after real bounds exist");
            } else {
                background.setBounds(0,0,group.layout.width,group.layout.height);background.draw(new Canvas());
                GradientDrawable fill=(GradientDrawable)Reflect.getObjectField(background,"fill");
                check(fill.getColor().getDefaultColor()==0x4188aacc&&fill.getCornerRadius()==expected,"actual flat shortcut background paints the exact shared ARGB and corner "+corner);
            }
        }
        CloudCheck.after(VIEW+"#resetSliderState",d,null);
        for(int i=0;i<2;i++) {
            View group=quick.getChildAt(i);
            check(group.layoutListeners.isEmpty()&&group.attachListeners.isEmpty()&&Reflect.getAdditionalInstanceField(group,"volume.proxy")==null,"shortcut cleanup removes material callbacks and proxy reference");
        }
        com.oplus.systemui.volume.utils.material.VolumeBlurManager.INSTANCE.shortcutBlur=false;
    }
    static final class RecordedShape extends GradientDrawable {
        float drawnCorner;boolean fail;
        @Override public void draw(Canvas canvas) {drawnCorner=getCornerRadius();if(fail)throw new IllegalStateException("native draw failure");super.draw(canvas);}
    }
    static void cornerOnlyScenario(int corner)throws Throwable {
        Bundle settings=config();settings.putBoolean("volume_bar_tint",false);settings.putInt("volume_bar_corner",corner);CloudCheck.config(settings);
        View host=new View();host.actualWidth=host.actualHeight=209;
        RecordedShape shape=new RecordedShape();shape.setCornerRadius(90);shape.setColor(0xff123456);
        DrawableWrapper nativeWrapper=new DrawableWrapper(shape);
        HookCallback.Param result=CloudCheck.param(null,nativeWrapper,new android.content.Context(),new View(),host,true);
        Api102Runtime.callbacks.get(BASE+"utils.material.VolumeBlurManager#getVolumeSettingsButtonBackground").after(result);
        Drawable background=(Drawable)result.getResult();background.setBounds(0,0,209,209);
        float expected=Math.round(corner*2.75f);
        background.draw(new Canvas());
        check(shape.drawnCorner==expected&&shape.getCornerRadius()==90&&shape.getColor().getDefaultColor()==0xff123456,"corner-only button draw changes the native shape without changing its color, then restores it for "+corner);
        shape.fail=true;try {background.draw(new Canvas());throw new AssertionError("missing draw failure");}catch(IllegalStateException expectedError) { }
        check(shape.getCornerRadius()==90&&nativeWrapper.getCallback()==background,"corner-only native shape and callbacks also restore after a draw failure");
        shape.fail=false;settings.putBoolean("volume_bar_corner_enabled",false);CloudCheck.config(settings);background.draw(new Canvas());
        check(shape.drawnCorner==90,"disabling button corner customization immediately draws its original native corner");
    }
    public static void main(String[] args)throws Throwable {
        ShadeHook.install("com.android.systemui","com.android.systemui",VolumePanelCheck.class.getClassLoader());
        check(!Api102Runtime.callbacks.containsKey(OWNER+"#computeTimeoutH$1"),"compact mode preserves native timeout policy");
        check(!Api102Runtime.callbacks.containsKey(OWNER+"#onStateChangedH"),"volume state and restrictions remain bound to native rows");
        check(Api102Runtime.callbacks.containsKey(VIEW+"#expandPanel")&&Api102Runtime.callbacks.containsKey(ANIM+"#getEndX"),"native expansion and geometry hooks are installed");
        OplusVolumeDialogImpl owner=new OplusVolumeDialogImpl();OplusVolumeDialogView d=owner.mOplusVolumeDialogView;d.mVolumeRecyclerview.res=volumeResources();d.mDialogView.res=volumeResources();
        OplusVolumeRow row=new OplusVolumeRow();owner.mRows.add(row);d.volumeListAdapter.rows.add(row);
        RecyclerView.ViewHolder h=holder(d,0,900,300);((ViewGroup)h.itemView).addView(row.view);row.sliderFrame.parent=row.view;row.slider.parent=row.sliderFrame;
        d.mSettingsButton.layout.width=96;d.mVolumePanelTipsText.setVisibility(4);
        row.sliderFrame.setPadding(14,11,14,22);
        // Native title text can still be empty when expandPanel's before callback runs.
        d.mVolumePanelTitleText=new View() { @Override public void measure(int w,int height) {measuredWidth=0;measuredHeight=24;} };
        d.mVolumePanelTitleText.layout=new LayoutParams(-2,24);d.mDialogView.addView(d.mVolumePanelTitleText);
        CloudCheck.config(config());
        row.slider.mCurBackgroundWidth=67.2f;row.slider.mCurBackgroundRadius=33.6f;
        CloudCheck.after(VIEW+"#updateRowsH",d,null);
        check(row.slider.layout.width==209&&row.slider.layout.height==21,"bar width changes without overwriting native match-parent height/reveal layout");
        check(row.sliderFrame.layout.height==21&&row.view.layout.height==21,"native row/frame vertical geometry remains intact");
        check(d.mVolumeRecyclerview.layout.height==940,"bar length preserves measured native viewport overhead");
        check(row.slider.mBackgroundWidth==209&&Math.abs(row.slider.mCurBackgroundWidth-250.8f)<.01f,"width customization retains native press deformation ratio");
        check(row.slider.mBackgroundRadius==50&&Math.abs(row.slider.mCurBackgroundRadius-60)<.01f,"base corner and current press corner remain proportional");
        check(row.slider.mPaddingVertical==50&&row.slider.mMaxHeight==0,"corner padding follows geometry without imposing an artificial max height");
        int recalculations=row.slider.ensured;
        before(BAR+"#onMeasure",row.slider,0,0);before(BAR+"#onMeasure",row.slider,0,0);
        check(row.slider.ensured==recalculations,"unchanged measurements never reset native animation state");
        check(d.mMoreRowStreamLl.layout.width==209&&d.mSettingsButton.layout.height==209&&d.mSettingsButton.layout.width==209,"all native buttons are square and share the bar width, ignoring retired button size");
        check(((Number)CloudCheck.after(VIEW+"#getIconHalfDelta",d,24f)).floatValue()==104.5f,"native sidebar button animation offsets follow shared bar width");
        HookCallback.Param draw=before(BAR+"#onDraw",row.slider,new Canvas());
        check(row.slider.mBackgroundColor==0&&Math.abs(row.slider.mCurBackgroundRadius-60)<.01f,"native material owns tint once; drawing does not replace animated geometry");
        draw.failure=new IllegalStateException("draw failure");QsTintCheck.after(BAR+"#onDraw",draw);
        check(row.slider.mBackgroundColor==0xff234567,"native background paint restores even after draw failure");
        String factory=BASE+"utils.material.VolumeBlurManager#getVolumeBarBackground";
        BlurConfig blur=new BlurConfig();blur.platformMixConfig=QsTintCheck.multi(0xff778899);Object nativeMix=blur.platformMixConfig;
        AutoBlurDrawable auto=new AutoBlurDrawable(new ViewBlurProxy(blur));
        HookCallback.Param material=CloudCheck.param(null,auto,new android.content.Context(),d.mDialogView,row.slider,28f,true);
        Api102Runtime.callbacks.get(factory).after(material);
        check(material.getResult()==auto&&"volume-bar".equals(Reflect.getAdditionalInstanceField(blur,"thunder.role")),"native blur drawable identity is retained and assigned its independent role");
        draw=before(QsTintCheck.B+"ViewBlurProxy#applyConfigToPlatformBlur",auto.proxy);
        check(Math.abs(blur.cornerRadius-60)<.01f,"bar material uses its host density and native animated corner");
        check(blur.blurRadius==123&&blur.platformMixConfig!=nativeMix,"native material submission receives volume blur and tint");
        QsTintCheck.after(QsTintCheck.B+"ViewBlurProxy#applyConfigToPlatformBlur",draw);
        check(blur.platformMixConfig==nativeMix,"material backend submission restores native mix state");

        for(String buttonFactory:new String[]{"getVolumeSettingsButtonBackground","getVolumeSideAccessoryBackground"}) {
            View buttonHost=d.mMoreRowStreamLl;buttonHost.actualWidth=buttonHost.layout.width;buttonHost.actualHeight=buttonHost.layout.height;
            BlurConfig buttonBlur=new BlurConfig();buttonBlur.platformMixConfig=QsTintCheck.multi(0xff112233);Object buttonNativeMix=buttonBlur.platformMixConfig;
            AutoBlurDrawable buttonAuto=new AutoBlurDrawable(new ViewBlurProxy(buttonBlur));
            HookCallback.Param buttonMaterial=CloudCheck.param(null,buttonAuto,new android.content.Context(),d.mDialogView,buttonHost,true);
            Api102Runtime.callbacks.get(BASE+"utils.material.VolumeBlurManager#"+buttonFactory).after(buttonMaterial);
            HookCallback.Param submission=before(QsTintCheck.B+"ViewBlurProxy#applyConfigToPlatformBlur",buttonAuto.proxy);
            BlurMixConfig.BlurMixMultiWithShader mix=(BlurMixConfig.BlurMixMultiWithShader)buttonBlur.platformMixConfig;
            check(buttonMaterial.getResult()==buttonAuto&&buttonBlur.blurRadius==123&&buttonBlur.cornerRadius==50&&mix.foregroundShaderParam.topLayerColor==0x4188aacc,
                    buttonFactory+" shares the bar blur, corner and ARGB, ignoring retired material");
            QsTintCheck.after(QsTintCheck.B+"ViewBlurProxy#applyConfigToPlatformBlur",submission);
            check(buttonBlur.platformMixConfig==buttonNativeMix,"button material preserves native backend state after submission");
        }

        row.slider.screenY=311;row.slider.actualHeight=880; // Completed native layout of the 320 dp main bar.
        Object controller=owner.mController,adapter=d.volumeListAdapter,nativeAnimator=d.volumeListAdapter.nativeAnimator;
        HookCallback.Param expand=before(VIEW+"#expandPanel",d);
        check(!expand.returnEarly,"native expand method is allowed to execute");
        owner.expandPanel();d.expandPanel();CloudCheck.after(BASE+"VolumeListAdapter#switchToDataSourcePanel",adapter,null,false);
        check(owner.mExpanded&&d.expansions==1&&d.volumeListAdapter.source==1,"native expanded flag, view expansion and panel data source run normally");
        check(owner.mController==controller&&d.volumeListAdapter==adapter&&d.volumeListAdapter.nativeAnimator==nativeAnimator,"native controller, adapter and animator instances are preserved");
        GradientDrawable original=new GradientDrawable();d.mDialogView.setBackground(original);
        VolumePlatformBlurHostHelper helper=new VolumePlatformBlurHostHelper(d);CloudCheck.after(HELPER+"#mountPanelBackground",helper,null);
        Drawable bounded=d.mDialogView.getBackground();bounded.draw(new Canvas());Rect panel=new Rect(original.getBounds().left,original.getBounds().top,original.getBounds().right,original.getBounds().bottom);
        before(VIEW+"#expandPanel",d);
        check(d.mDialogView.getBackground()==bounded,"repeated expansion keeps the existing transition geometry and captured anchor");
        check(bounded instanceof DrawableWrapper&&((DrawableWrapper)bounded).getDrawable()==original,"only background bounds are wrapped; native animated drawable stays the same");
        check(panel.width()==4*209+5*22&&panel.height()==880+209+3*22&&panel.right<=1072&&panel.top>=24,"panel wraps the measured main track plus a real shortcut row and equal spacing");
        before(BAR+"#onMeasure",row.slider,0,0);
        check(row.slider.layout.width==209,"expanded tracks retain the main bar width when the screen has room");
        check(d.mSettingsButton.layout.width==row.slider.layout.width&&d.mSettingsButton.layout.height==row.slider.layout.width,"compact settings button stays square and follows the fitted bar width");
        check(d.mVolumeRecyclerview.layout.height==880+11+22,"expanded track length follows the measured main bar instead of the former fixed 144 dp cap");
        check(d.mDialogView.layout.width==37&&d.mVolumeRecyclerview.parent==d.mDialogView,"native root and recycler hierarchy remain untouched during transition");
        VolumeItemAnimator animator=new VolumeItemAnimator(d.volumeListAdapter);
        float previous=-1,activeEndX=0,activeEndY=0;
        int trackWidth=row.slider.layout.width;
        for(int i=0;i<4;i++) {
            RecyclerView.ViewHolder item=i==0?h:holder(d,i,900-i*154,600);
            CloudCheck.after(BASE+"VolumeListAdapter#onBindViewHolder",adapter,null,item,i);
            item.itemView.actualWidth=item.itemView.layout.width;
            item.itemView.translationX=25;item.itemView.screenX+=25;
            float x=item.itemView.screenX-item.itemView.translationX+endpoint("getEndX",animator,item);
            float baseY=item.itemView.screenY-item.itemView.getTranslationY();
            float endY=endpoint("getTranslationY",animator,item),y=baseY+endY;
            check(item.itemView.getTranslationY()==endY&&item.itemView.screenY==300,"row "+i+" starts its native spring at the final Y pose so expansion only moves horizontally");
            float visibleLeft=x+(item.itemView.layout.width-trackWidth)/2f;
            check(Math.abs(visibleLeft-(panel.left+22+i*(trackWidth+22)))<.01f&&y+11==panel.top+22&&y+d.mVolumeRecyclerview.layout.height-22==panel.bottom-22-209-22,"native row "+i+" has the same visible track gap and panel padding on every edge");
            if(i>0)check(visibleLeft-previous-trackWidth==22,"adjacent painted tracks use exactly the outer panel spacing");previous=visibleLeft;
            if(i==3)check(visibleLeft+trackWidth==panel.right-22,"last painted track has the same trailing panel padding");
            if(i==0){activeEndX=x;activeEndY=y;}
            float start=item.itemView.screenX-item.itemView.translationX+endpoint("resolvePanelStartTranslationX",animator,item);
            check(start==900,"native expand/collapse returns row "+i+" to captured sidebar anchor");
        }
        HookCallback.Param flatMaterial=CloudCheck.param(null,new GradientDrawable(),new android.content.Context(),d.mDialogView,row.slider,28f,true);
        Api102Runtime.callbacks.get(factory).after(flatMaterial);
        Drawable flatBar=(Drawable)flatMaterial.getResult();
        int lastWidth=0;float nativeWidth=row.slider.mCurBackgroundWidth;int halfWidth=0;
        for(float fraction:new float[]{0,.25f,.5f,.75f,1}) {
            h.itemView.screenX=Math.round(900+(activeEndX-900)*fraction);h.itemView.screenY=Math.round(300+(activeEndY-300)*fraction);
            d.mDialogView.observer.dispatchOnPreDraw();bounded.draw(new Canvas());Rect frame=original.getBounds();
            check(frame.width()>=lastWidth&&frame.right==panel.right,"native motion at "+fraction+" drives an inward width change with a fixed sidebar edge");
            if(fraction==0)check(frame.width()<panel.width()/2,"panel starts as a narrow surface beside the original volume bar");
            if(fraction==.5f)halfWidth=frame.width();lastWidth=frame.width();
            HookCallback.Param widthDraw=before(BAR+"#draw",row.slider,new Canvas());
            float expected=nativeWidth*(209+(row.slider.mBackgroundWidth-209)*fraction)/row.slider.mBackgroundWidth;
            check(Math.abs(row.slider.mCurBackgroundWidth-expected)<.6f,"bar width at "+fraction+" follows native motion without scaling its icons or labels");
            flatBar.draw(new Canvas());
            java.lang.reflect.Field fillField=flatBar.getClass().getDeclaredField("fill");fillField.setAccessible(true);
            Drawable flatFill=(Drawable)fillField.get(flatBar);
            check(Math.abs(flatFill.getBounds().width()-row.slider.mCurBackgroundWidth)<=1,"flat bar material follows the same animated width at "+fraction);
            widthDraw.failure=new IllegalStateException("interrupted draw");QsTintCheck.after(BAR+"#draw",widthDraw);
            check(row.slider.mCurBackgroundWidth==nativeWidth,"native press pose restores after the width draw, including errors");
        }
        check(lastWidth==panel.width(),"native motion reaches the final compact width exactly");
        h.itemView.screenX=Math.round(900+(activeEndX-900)*.5f);h.itemView.screenY=Math.round(300+(activeEndY-300)*.5f);
        d.mDialogView.observer.dispatchOnPreDraw();bounded.draw(new Canvas());
        check(original.getBounds().width()==halfWidth,"interrupted collapse returns through the same intermediate width without restarting");
        h.itemView.screenX=Math.round(900+(activeEndX-900)*.25f);h.itemView.screenY=Math.round(300+(activeEndY-300)*.25f);
        d.mDialogView.observer.dispatchOnPreDraw();bounded.draw(new Canvas());
        check(original.getBounds().width()<halfWidth,"native collapse narrows the panel continuously toward the sidebar");
        h.itemView.screenX=Math.round(activeEndX);h.itemView.screenY=Math.round(activeEndY);
        d.mDialogView.observer.dispatchOnPreDraw();bounded.draw(new Canvas());
        ViewGroup quick=(ViewGroup)d.mDialogView.getChildAt(d.mDialogView.getChildCount()-1);
        ViewGroup segments=(ViewGroup)quick.getChildAt(0),zenControl=(ViewGroup)quick.getChildAt(1);
        android.widget.ImageView sound=(android.widget.ImageView)segments.getChildAt(1),vibrate=(android.widget.ImageView)segments.getChildAt(2),silent=(android.widget.ImageView)segments.getChildAt(3);
        View selector=segments.getChildAt(0),zen=zenControl;
        android.widget.ImageView zenOff=(android.widget.ImageView)zenControl.getChildAt(1),zenOn=(android.widget.ImageView)zenControl.getChildAt(2);
        check(segments.getChildCount()==4&&sound.layout.width==209&&sound.layout.height==209&&zen.layout.width==209,"three sound segments and DND share four square icon slots with the bar width");
        check(((android.widget.FrameLayout.LayoutParams)sound.layout).leftMargin==0&&((android.widget.FrameLayout.LayoutParams)vibrate.layout).leftMargin==231&&((android.widget.FrameLayout.LayoutParams)silent.layout).leftMargin==462&&((android.widget.FrameLayout.LayoutParams)zen.layout).leftMargin==693&&quick.layout.width==902,"four icon slots fill the bottom row and align with the four bar columns");
        check(sound.mResource==Math.abs("status_bar_qs_mute_inactive".hashCode())&&vibrate.mResource==Math.abs("status_bar_qs_vibrate_on".hashCode())&&silent.mResource==Math.abs("status_bar_qs_mute_active".hashCode()),"sound segments use the exact current ColorOS three-stage tile resources");
        check(zenOff.mResource==Math.abs("status_bar_qs_dnd_inactive".hashCode())&&zenOn.mResource==Math.abs("status_bar_qs_dnd_active".hashCode()),"DND uses the native ColorOS moon icons rather than generic AOSP resources");
        LayoutParams quickLp=(LayoutParams)quick.layout;
        check(quickLp.topMargin==panel.bottom-22-209&&quickLp.height==209,"shortcut row sits one shared spacing below the track and one spacing above the panel bottom");
        check(com.oplus.systemui.volume.utils.material.VolumeBlurManager.INSTANCE.shortcutMaterials>=2&&com.oplus.systemui.volume.utils.material.VolumeBlurManager.INSTANCE.accessoryMaterials==0,"expanded shortcuts use the same native BAR_BACKGROUND material as the tracks, without the brighter SIDE_ACCESSORY mix");
        check(sound.isEnabled()&&zen.isEnabled(),"shortcuts enable when native expansion reaches its endpoint");
        check(selector.getTranslationX()==0&&sound.getAlpha()==1&&zenOn.getAlpha()==0&&ValueAnimator.running.isEmpty(),"initial native state renders immediately without a startup switching animation");
        sound.performClick();check(owner.mController.ringerCalls==0,"selecting the current sound segment avoids redundant native writes");
        vibrate.performClick();check(owner.mState.ringerModeInternal==1,"vibration segment directly selects vibration through the native controller");
        ValueAnimator firstMotion=ValueAnimator.running.get(0);
        check(firstMotion.duration==200&&selector.getTranslationX()==0,"sound switching starts a short animation from the current selection pose");
        firstMotion.advance(.2f);float interruptedX=selector.getTranslationX(),interruptedAlpha=vibrate.getAlpha();
        check(interruptedX>0&&interruptedX<231&&interruptedAlpha>.6f&&interruptedAlpha<1&&vibrate.scaleX>.92f&&vibrate.scaleX<1,"intermediate sound frames slide the selector and smoothly transition icon emphasis");
        silent.performClick();check(owner.mState.ringerModeInternal==0,"silent segment directly selects silent");
        check(firstMotion.cancelled&&firstMotion.listeners.isEmpty()&&selector.getTranslationX()==interruptedX&&vibrate.getAlpha()==interruptedAlpha,"rapid reversal cancels stale callbacks and preserves the exact current selection pose");
        ValueAnimator.finishAll();
        check(selector.getTranslationX()==462&&silent.getAlpha()==1&&sound.getAlpha()==.6f,"sound selection reaches the exact silent slot without changing cell geometry");
        sound.performClick();check(owner.mState.ringerModeInternal==2&&owner.mController.ringerCalls==3,"ringing segment directly selects ringing without changing stream volume");
        ValueAnimator.finishAll();
        owner.mState.zenMode=3;d.mDialogView.observer.dispatchOnPreDraw();
        ValueAnimator zenMotion=ValueAnimator.running.get(0);zenMotion.advance(.2f);
        float interruptedZen=zenOn.getAlpha();
        check(interruptedZen>0&&interruptedZen<1&&Math.abs(zenOff.getAlpha()+interruptedZen-1)<.0001f&&zenOn.scaleX<1,"DND crossfades both native icons with a subtle scale transition at stationary panel frames");
        zen.performClick();check(owner.mState.zenMode==0,"DND shortcut disables the active native zen mode");
        check(zenMotion.cancelled&&zenMotion.listeners.isEmpty()&&zenOn.getAlpha()==interruptedZen,"reversing DND during transition continues from the visible icon blend");
        ValueAnimator.finishAll();
        check(zenOff.getAlpha()==1&&zenOn.getAlpha()==0&&zenControl.getChildAt(0).getAlpha()==0,"DND off finishes with its inactive native icon and no selected fill");
        zen.performClick();check(owner.mState.zenMode==3&&owner.mController.zenCalls==2,"DND shortcut restores the previous zen mode instead of forcing a new policy");
        ValueAnimator.finishAll();
        check(zenOn.getAlpha()==1&&zenOff.getAlpha()==0&&zenControl.getChildAt(0).getAlpha()==1,"DND on finishes with the native active icon and selected fill");
        owner.mState.ringerModeInternal=0;d.mDialogView.observer.dispatchOnPreDraw();
        check(silent.selected&&!sound.selected&&silent.getContentDescription().toString().contains("已选中"),"external native mode updates refresh segment selection and accessibility at stationary frames");
        ValueAnimator externalMotion=ValueAnimator.running.get(0);externalMotion.advance(.15f);
        check(selector.getTranslationX()>0&&selector.getTranslationX()<462&&owner.mController.ringerCalls==3&&owner.mController.zenCalls==2,"external state changes also animate without extra controller writes");
        owner.mState.disallowRinger=true;d.mDialogView.observer.dispatchOnPreDraw();sound.performClick();
        check(!sound.isEnabled()&&owner.mController.ringerCalls==3,"native ringer restrictions disable the shortcut and block writes");
        owner.mState.disallowRinger=false;
        View[] removed={d.mVolumePanelTitleIcon,d.mVolumePanelTitleText,d.mVolumePanelTipsText,d.mSettingsButton,d.mVolumeSettingText};
        for(View v:removed)check(v.getVisibility()==View.GONE,"compact title/tips/settings are removed from layout and touch");
        View label=h.itemView.findViewById(LABEL);
        check(label.getVisibility()==View.GONE,"adapter text label contributes no height to the compact row");
        label.setVisibility(View.VISIBLE);
        for(View v:removed)v.setVisibility(View.VISIBLE);
        d.mDialogView.observer.dispatchOnPreDraw();
        for(View v:removed)check(v.getVisibility()==View.GONE,"native late visibility writes stay hidden even at unchanged animation progress");
        check(label.getVisibility()==View.GONE,"native accessibility rebind cannot restore the text label during compact drawing");
        LayoutParams button=(LayoutParams)d.mSettingsButton.layout;
        OplusVolumeDialogView.CustomDialog nativeDialog=new OplusVolumeDialogView.CustomDialog(d);
        HookCallback.Param inside=before(VIEW+"$CustomDialog#dispatchTouchEvent",nativeDialog,new MotionEvent(0,panel.left+5,panel.top+5));
        check(!inside.returnEarly&&owner.dismissals==0,"inside touch continues through native dialog dispatch");
        HookCallback.Param outside=before(VIEW+"$CustomDialog#dispatchTouchEvent",nativeDialog,new MotionEvent(0,1,1));
        HookCallback.Param outsideUp=before(VIEW+"$CustomDialog#dispatchTouchEvent",nativeDialog,new MotionEvent(1,1,1));
        check(outsideUp.returnEarly,"outside gesture remains consumed until release");
        check(outside.returnEarly&&owner.dismissals==1,"outside tap requests native dismiss rather than destroying transition state");
        check(d.mDialogView.getBackground()==bounded,"compact surface remains through native collapse");
        CloudCheck.after(VIEW+"#resetSliderState",d,null);
        check(d.mDialogView.getBackground()==original&&button.leftToLeft==-1&&button.topMargin==0&&button.width==209&&button.height==209,"native terminal reset restores background and configured sidebar button size");
        check(d.mVolumeRecyclerview.layout.height==940,"native terminal reset restores the full configured sidebar length");
        check(d.mVolumePanelTipsText.getVisibility()==4&&d.mVolumePanelTitleText.getVisibility()==View.VISIBLE&&d.mSettingsButton.getVisibility()==View.VISIBLE,"terminal cleanup restores each accessory original visibility");
        check(d.mDialogView.observer.listeners.isEmpty(),"terminal reset removes the native frame observer");
        check(quick.getParent()==null&&sound.click==null&&vibrate.click==null&&silent.click==null&&zen.click==null,"native terminal cleanup removes the shortcut row and all four click listeners");
        float stoppedX=selector.getTranslationX();externalMotion.advance(1);
        check(externalMotion.cancelled&&externalMotion.listeners.isEmpty()&&ValueAnimator.running.isEmpty()&&selector.getTranslationX()==stoppedX,"terminal cleanup cancels switching animation and prevents late frame mutations");
        float nativeEnd=endpoint("getEndX",animator,h);
        check(nativeEnd==999,"completed transitions no longer override native animator endpoints");
        Bundle off=config();off.putBoolean("enabled",false);CloudCheck.config(off);VolumeHook.refresh();
        check(row.slider.layout.width==37&&row.slider.mBackgroundWidth==56&&row.slider.mBackgroundRadius==28&&row.slider.mPaddingVertical==28,"turning customization off restores native layout, geometry and padding");
        check(d.mMoreRowStreamLl.layout.width==37&&d.mSettingsButton.layout.width==96,"turning shared sizing off restores original native button dimensions");
        expand=before(VIEW+"#expandPanel",d);check(!expand.returnEarly,"disabled mode preserves native expansion");
        Bundle defaults=new Bundle();defaults.putBoolean("enabled",true);defaults.putBoolean("volume_compact",true);CloudCheck.config(defaults);
        h.itemView.screenX=12;h.itemView.translationX=0;row.slider.actualHeight=240;owner.mExpanded=false;
        expand=before(VIEW+"#expandPanel",d);d.expandPanel();owner.expandPanel();
        BlurConfig panelBlur=new BlurConfig();Object nativePath=new Object();panelBlur.pathProvider=nativePath;
        AutoBlurDrawable panelAuto=new AutoBlurDrawable(new ViewBlurProxy(panelBlur));
        HookCallback.Param panelMaterial=CloudCheck.param(null,panelAuto,new android.content.Context(),true,d.mDialogView,0f);
        Api102Runtime.callbacks.get(BASE+"utils.material.VolumeBlurManager#getVolumePanelBackground").after(panelMaterial);
        d.mDialogView.setBackground(panelAuto);CloudCheck.after(HELPER+"#mountPanelBackground",helper,null);
        draw=before(QsTintCheck.B+"ViewBlurProxy#applyConfigToPlatformBlur",panelAuto.proxy);
        check(panelBlur.cornerRadius==77&&panelBlur.pathProvider==nativePath,"default compact card updates cached native blur corners while retaining native shape provider");
        QsTintCheck.after(QsTintCheck.B+"ViewBlurProxy#applyConfigToPlatformBlur",draw);
        d.mDialogView.getBackground().draw(new Canvas());
        check(panelAuto.getBounds().left==Math.max(22,12+Math.round((h.itemView.actualWidth-56)/2f)-22),"left sidebar anchors panel padding to the visible track instead of its transparent holder");
        before(BAR+"#onMeasure",row.slider,0,0);check(row.slider.mBackgroundRadius<=row.slider.mBackgroundWidth/2,"compact sizing without custom corners keeps native radius within track width");
        d.volumeListAdapter.count=1;CloudCheck.after(BASE+"VolumeListAdapter#switchToDataSourcePanel",d.volumeListAdapter,null,false);
        d.mDialogView.getBackground().draw(new Canvas());
        check(panelAuto.getBounds().width()==4*row.slider.layout.width+5*22,"single-stream panel reserves exactly four shortcut slots without title/settings padding");
        Drawable during=d.mDialogView.getBackground();CloudCheck.config(off);VolumeHook.refresh();
        check(d.mDialogView.getBackground()==during,"settings refresh requests native collapse without stripping its compact background");
        CloudCheck.after(VIEW+"#resetSliderState",d,null);VolumeHook.refresh();
        check(row.slider.mBackgroundWidth==56&&row.slider.mBackgroundRadius==28,"default compact geometry also restores after native terminal hide");
        check(!ShadeHook.snapshot().qsCards.tint,"volume configuration remains independent of control-center materials");
        sizeScenario(330,2,4,false,false);
        sizeScenario(880,2,4,false,false);
        sizeScenario(1100,2,4,false,false);
        sizeScenario(240,2,1,false,false);
        sizeScenario(880,2,8,false,false);
        sizeScenario(330,2,4,true,false);
        sizeScenario(330,2,4,false,true);
        for(int corner:new int[]{0,18,64}) {
            shortcutMaterialScenario(false,corner);shortcutMaterialScenario(true,corner);
        }
        cornerOnlyScenario(0);cornerOnlyScenario(18);
        System.out.println("Volume native preservation checks passed: "+checks);
    }
}
