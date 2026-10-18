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
import com.oplus.systemui.plugins.qs.customize.view.tile.OplusQSThreeStageLayout;
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
        int barDraws=row.slider.invalidations;
        d.mDialogView.observer.dispatchOnPreDraw();
        if(count>4)check(row.slider.invalidations>barDraws,"screen-fitted tracks still redraw interpolated geometry when native row motion changes");
        check(d.mVolumeRecyclerview.layout.height==mainHeight+33,"measured main height "+mainHeight+" is inherited without a fixed expanded length");
        check(original.getBounds().top==311-22&&original.getBounds().height()==mainHeight+row.slider.layout.width+66,"panel remains anchored at the same visible Y and wraps track plus shortcuts for height "+mainHeight);
        check(original.getBounds().width()<=1036&&row.slider.layout.width<=(count>4?208:209),"only the real usable screen width limits the track group");
        ViewGroup quick=(ViewGroup)d.mDialogView.getChildAt(d.mDialogView.getChildCount()-1);
        // Detach the driver to exercise the native background-progress fallback at the endpoint.
        h.itemView.attached=false;d.mDialogView.observer.dispatchOnPreDraw();
        OplusQSThreeStageLayout segments=(OplusQSThreeStageLayout)quick.getChildAt(0);
        segments.frameLayoutViews.get(1).performClick();
        check(owner.mState.ringerModeInternal==((hardwareSlider||noVibrator)?2:1),"direct vibration segment respects vibrator capability and physical alert slider");
        check(segments.selectedIndex==2-owner.mState.ringerModeInternal,"rejected native segment reconciles with authoritative ringer state");
        segments.frameLayoutViews.get(2).performClick();
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

            {
                check(group.getForeground() instanceof VolumeShortcutFeedback,"both shortcuts share native seekbar feedback");
                View selection=i==0?((OplusQSThreeStageLayout)group).indicatorView:group.getChildAt(0);
                Drawable selected=selection.getBackground();
                check(selection.layout.width==group.layout.height&&selection.layout.height==group.layout.height,"selected surface fills one shortcut segment");
                if(nativeBlur)check(selected instanceof AutoBlurDrawable&&((AutoBlurDrawable)selected).proxy.config.cornerRadius==expected,"DND selected surface uses the native BAR_PROGRESS blur and shared corner");
                else check(selected instanceof com.oplusos.systemui.common.view.SmoothCornersGradientDrawable&&((com.oplusos.systemui.common.view.SmoothCornersGradientDrawable)selected).radius==expected,"solid DND progress uses the native smooth shape and shared corner");
            }
            if(nativeBlur) {
                AutoBlurDrawable auto=(AutoBlurDrawable)background;BlurConfig blur=auto.proxy.config;
                check(group.getWidth()==0&&"volume-buttons".equals(Reflect.getAdditionalInstanceField(blur,"thunder.role")),"actual unlaid shortcut background is registered with shared material role");
                HookCallback.Param submission=before(QsTintCheck.B+"ViewBlurProxy#applyConfigToPlatformBlur",auto.proxy);
                BlurMixConfig.BlurMixSingleWithShader mix=(BlurMixConfig.BlurMixSingleWithShader)blur.platformMixConfig;
                check(blur.cornerRadius==expected&&blur.blurRadius==123&&mix.backgroundShaderParam.topLayerColor==0x4188aacc,"unlaid shortcut submits shared corner, blur and exact ARGB for "+corner);
                QsTintCheck.after(QsTintCheck.B+"ViewBlurProxy#applyConfigToPlatformBlur",submission);
                Object nativeMix=blur.platformMixConfig;int nativeBlurRadius=blur.blurRadius;float nativeCorner=blur.cornerRadius;
                String hot=BASE+"utils.material.VolumeBlurManager#applyPlatformBlurMixAfterConfigWrite";
                HookCallback.Param update=before(hot,null,auto.proxy,false);
                BlurMixConfig.BlurMixSingleWithShader hotMix=(BlurMixConfig.BlurMixSingleWithShader)blur.platformMixConfig;
                check(hotMix.backgroundShaderParam.topLayerColor==0x4188aacc&&blur.blurRadius==123&&blur.cornerRadius==expected,
                        "native cached shader update receives the shared ARGB, blur and corner without a proxy submission");
                update.failure=new IllegalStateException("hot shader failure");QsTintCheck.after(hot,update);
                check(blur.platformMixConfig==nativeMix&&blur.blurRadius==nativeBlurRadius&&blur.cornerRadius==nativeCorner,
                        "hot shader overrides restore the native configuration even after failure");
                String light=BASE+"utils.material.VolumeBlurManager#hotUpdateStaticPlatformLightAndStroke";
                HookCallback.Param geometry=before(light,null,null,blur);
                check(blur.cornerRadius==expected&&blur.leftTopCornerRadius==expected&&blur.rightBottomCornerRadius==expected,
                        "direct cached light/stroke update reads the same shared corner including zero");
                QsTintCheck.after(light,geometry);
                check(blur.cornerRadius==nativeCorner&&blur.platformMixConfig==nativeMix,"cached light/stroke update restores its native config");
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
        VolumePlatformBlurHostHelper helper=new VolumePlatformBlurHostHelper(d);
        for(float phase:new float[]{0f,.3f,1f,.6f,0f}) {
            CloudCheck.after(HELPER+"#applyBarPanelBottomProgress",helper,null,phase);
            for(int i=0;i<2;i++)check(com.oplus.systemui.volume.utils.material.VolumeBlurManager.phases.get(quick.getChildAt(i))==phase,
                    "shortcut native material follows expand/reverse phase "+phase);
            int writes=com.oplus.systemui.volume.utils.material.VolumeBlurManager.phaseWrites;
            CloudCheck.after(HELPER+"#applyBarPanelBottomProgress",helper,null,phase);
            d.mDialogView.observer.dispatchOnPreDraw();
            check(com.oplus.systemui.volume.utils.material.VolumeBlurManager.phaseWrites==writes,"unchanged native phase and pre-draw do not resubmit shortcut material");
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
        check(java.util.Arrays.stream(com.oplus.systemui.qs.base.res.SepQSResPool.Builder.class.getDeclaredMethods()).filter(m->m.getName().equals("build")).count()==2,"native builder fixture includes both the covariant implementation and its synthetic bridge");
        failedShortcutInitializationRestoresAnUnmountedPanel();
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
        check(before(BASE+"anim.PanelExpandPreludeAnimator#start",d.panelExpandPrelude).returnEarly&&d.panelExpandPrelude.cancelled,"compact expansion suppresses only its own fullscreen button shrink/fade prelude");
        check(before(VIEW+"#startBtnThickAnim",d,20f,true).returnEarly,"compact handoff does not start a competing upward button animation");
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
            check(start==900,"all native row starts share the captured source anchor instead of an offscreen alarm exception");
        }
        HookCallback.Param flatMaterial=CloudCheck.param(null,new GradientDrawable(),new android.content.Context(),d.mDialogView,row.slider,28f,true);
        Api102Runtime.callbacks.get(factory).after(flatMaterial);
        Drawable flatBar=(Drawable)flatMaterial.getResult();
        int lastWidth=0;float nativeWidth=row.slider.mCurBackgroundWidth;int halfWidth=0;
        View untouched=new View();((ViewGroup)h.itemView).addView(untouched);
        int sourceBarX=row.slider.screenX,sourceGroupX=d.mVerticalRowIconParent.screenX,sourceGroupY=d.mVerticalRowIconParent.screenY;
        // Native ConstraintLayout rebases the accessory parent before the first row frame.
        d.mVerticalRowIconParent.screenX-=180;d.mVerticalRowIconParent.screenY-=32;
        for(float fraction:new float[]{0,.25f,.5f,.75f,1}) {
            h.itemView.screenX=Math.round(900+(activeEndX-900)*fraction);h.itemView.screenY=Math.round(300+(activeEndY-300)*fraction);
            row.slider.screenX=sourceBarX+h.itemView.screenX-900;
            d.mDialogView.observer.dispatchOnPreDraw();bounded.draw(new Canvas());Rect frame=original.getBounds();
            android.graphics.Path panelClip=(android.graphics.Path)Reflect.getObjectField(bounded,"clip");
            check(panelClip.smooth&&panelClip.bounds!=null&&panelClip.bounds.left==frame.left&&panelClip.bounds.top==frame.top&&panelClip.bounds.width()==frame.width()&&panelClip.bounds.height()==frame.height(),"panel at "+fraction+" uses the native smooth corner path at its animated bounds");
            check(frame.width()>=lastWidth&&frame.right>=panel.right,"native motion at "+fraction+" expands from the original sidebar while moving the outer edge inward");
            if(fraction==0)check(frame.width()<panel.width()/2,"panel starts as a narrow surface beside the original volume bar");
            if(fraction==.5f)halfWidth=frame.width();lastWidth=frame.width();
            ViewGroup morphRow=(ViewGroup)d.mDialogView.getChildAt(d.mDialogView.getChildCount()-1);
            View morphSound=morphRow.getChildAt(0);
            var morph=com.coui.appcompat.animation.dynamicanimation.COUISpringAnimation.running.stream().filter(a->a.view==morphRow).findFirst().orElse(null);
            if(morph!=null)morph.advance(.28f);
            d.mDialogView.observer.dispatchOnPreDraw();
            float reveal=morphRow.getAlpha();
            check(morphSound.layout.width==Math.round(VolumePanelGeometry.mix(trackWidth,2*231+209,reveal))&&morphRow.clipBounds==null,
                    "native sound host resizes from a circle to its capsule at "+fraction+" without a reveal clip");
            check(Math.abs(d.mVerticalRowIconParent.screenX-sourceGroupX-(row.slider.screenX-sourceBarX))<1f&&d.mVerticalRowIconParent.screenY==sourceGroupY,
                    "original buttons move with the active volume row at "+fraction+" without drifting upwards");
            if(fraction<.35f)check(d.mVerticalRowIconParent.getAlpha()==1f,"original buttons stay visible during initial movement before the shape spring starts");
            if(fraction>=.5f)check(reveal>0&&reveal<1,"capsule shape has a native spring intermediate pose independent of eased row progress");
            OplusQSThreeStageLayout morphNative=(OplusQSThreeStageLayout)morphSound;
            check(morphNative.frameLayoutViews.get(0).getAlpha()==VolumePanelGeometry.clamp((reveal-.25f)/.5f),"native icons enter after the circle has begun to widen instead of crowding inside it");
            HookCallback.Param widthDraw=before(BAR+"#draw",row.slider,new Canvas());
            if(fraction==0)check(widthDraw.getObjectExtra("volume.widthPose")==null,"identical start/end geometry skips temporary pose allocation and field writes");
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
        RecyclerView.ViewHolder alarmHolder=new RecyclerView.ViewHolder(d.mVolumeRecyclerview.getChildAt(3),3);
        float alarmTarget=alarmHolder.itemView.screenX-alarmHolder.itemView.getTranslationX()+endpoint("getEndX",animator,alarmHolder);
        check(alarmTarget<900-20,"alarm has a short inward travel from the same fixed source, retaining equal final column spacing");
        h.itemView.screenX=Math.round(900+(activeEndX-900)*.5f);row.slider.screenX=sourceBarX+h.itemView.screenX-900;
        for(float alpha:new float[]{0,.25f,.75f,.25f,0}) {
            alarmHolder.itemView.setAlpha(alpha);d.mDialogView.observer.dispatchOnPreDraw();
            float fixedStart=alarmHolder.itemView.screenX-alarmHolder.itemView.getTranslationX()+endpoint("resolvePanelStartTranslationX",animator,alarmHolder);
            check(fixedStart==900,"alarm moves and fades from the fixed sidebar origin without following source movement at "+alpha);
        }
        alarmHolder.itemView.setAlpha(1);
        float previousY=h.itemView.getTranslationY();h.itemView.screenY+=24;row.slider.screenY+=24;
        d.mDialogView.observer.dispatchOnPreDraw();
        check(h.itemView.getTranslationY()==previousY-24,"pre-draw compensates native source rebinding before the first painted expansion frame");
        row.slider.screenY-=24;
        h.itemView.screenX=Math.round(activeEndX);row.slider.screenX=sourceBarX+h.itemView.screenX-900;d.mDialogView.observer.dispatchOnPreDraw();
        RecyclerView.ViewHolder added=new RecyclerView.ViewHolder(d.mVolumeRecyclerview.getChildAt(1),1);
        HookCallback.Param entrance=before(ANIM+"#animateAddImpl",animator,added,4);
        HookCallback.Param stagger=before("android.view.ViewPropertyAnimator#setStartDelay",added.itemView.animate(),33L);
        check(stagger.args[0].equals(132L),"new rows keep their native stagger with a short delay behind the original bar/button handoff");
        QsTintCheck.after(ANIM+"#animateAddImpl",entrance);
        check(before("android.view.ViewPropertyAnimator#setStartDelay",added.itemView.animate(),33L).args[0].equals(33L),"entrance delay does not affect unrelated animations on the UI thread");
        entrance=before(ANIM+"#animateMoveImpl",animator,h,0,0,0,0,4);
        check(before("android.view.ViewPropertyAnimator#setStartDelay",h.itemView.animate(),0L).args[0].equals(0L),"the original volume row starts moving immediately");
        QsTintCheck.after(ANIM+"#animateMoveImpl",entrance);
        OplusVolumeDialogView unrelated=new OplusVolumeDialogImpl().mOplusVolumeDialogView;
        check(!before(BASE+"anim.PanelExpandPreludeAnimator#start",unrelated.panelExpandPrelude).returnEarly,"other volume panels retain the unmodified native prelude");
        check(untouched.invalidations==0,"expansion does not recursively invalidate unrelated row children");
        h.itemView.screenX=Math.round(900+(activeEndX-900)*.5f);h.itemView.screenY=Math.round(300+(activeEndY-300)*.5f);
        d.mDialogView.observer.dispatchOnPreDraw();bounded.draw(new Canvas());
        check(original.getBounds().width()==halfWidth,"interrupted collapse returns through the same intermediate width without restarting");
        h.itemView.screenX=Math.round(900+(activeEndX-900)*.25f);h.itemView.screenY=Math.round(300+(activeEndY-300)*.25f);
        d.mDialogView.observer.dispatchOnPreDraw();bounded.draw(new Canvas());
        check(original.getBounds().width()<halfWidth,"native collapse narrows the panel continuously toward the sidebar");
        h.itemView.screenX=Math.round(activeEndX);h.itemView.screenY=Math.round(activeEndY);
        d.mDialogView.observer.dispatchOnPreDraw();bounded.draw(new Canvas());
        com.coui.appcompat.animation.dynamicanimation.COUISpringAnimation.finishAll();d.mDialogView.observer.dispatchOnPreDraw();
        ViewGroup quick=(ViewGroup)d.mDialogView.getChildAt(d.mDialogView.getChildCount()-1);
        OplusQSThreeStageLayout segments=(OplusQSThreeStageLayout)quick.getChildAt(0);
        segments.indicatorView.getBackground().setState(new int[0]);
        d.mDialogView.observer.dispatchOnPreDraw();
        check(segments.indicatorView.getBackground().getState().length==0,"volume progress does not depend on QS active flags");
        ViewGroup zenControl=(ViewGroup)quick.getChildAt(1);
        View sound=segments.frameLayoutViews.get(0),vibrate=segments.frameLayoutViews.get(1),silent=segments.frameLayoutViews.get(2),zen=zenControl;
        com.oplus.systemui.plugins.qs.customize.view.tile.OplusQSIconView zenIcon=(com.oplus.systemui.plugins.qs.customize.view.tile.OplusQSIconView)zenControl.getChildAt(1);
        check(segments.layout.width==2*231+209&&segments.layout.height==209&&zen.layout.width==209,"native three-stage layout and DND fit the existing shortcut row");
        check(segments.drawableUpdates==2&&segments.indicatorView.layout.width==209&&segments.iconViews.get(0).layout.width==55,"native normal/theme surfaces and bounded indicator/icon geometry are configured");
        check(zenIcon.state.spec.equals("dnd")&&zenIcon.state.lottiePrefix.equals("qs_dnd_lottie")&&zenIcon.state.lottieSupport==17&&zenIcon.iconViewProxy.detail,"DND uses the actual native QS icon and its Lottie asset policy inside the volume dialog");
        check(segments.disableTheme&&zenIcon.themeDisabled,"both shortcut icon palettes match the normal native highlight instead of mixing QS theme colors");
        String palette="com.oplus.systemui.plugins.qs.customize.view.tile.OplusQSThreeStageLayout#getActiveIconColor";
        check(((Number)CloudCheck.after(palette,segments,0xffffffff,true)).intValue()==AdaptiveTextColor.foreground(0xff778899),"volume native selected icons retain contrast against the actual opaque native highlight");
        check(((Number)CloudCheck.after(palette,new OplusQSThreeStageLayout(new android.content.Context()),0xffabcdef,true)).intValue()==0xffabcdef,"control-center instances retain their original native palette");
        check(zenControl.getChildAt(0).getBackground() instanceof com.oplusos.systemui.common.view.SmoothCornersGradientDrawable,"DND selection uses volume progress rather than a smaller QS highlight");
        for(View segment:segments.frameLayoutViews)check(!segment.isClickable()&&segment.click!=null,"physical touch reaches native drag/press handling while accessibility click listeners remain");
        var writer=new com.oplus.systemui.volume.utils.material.VolumeBlurConfigWriter();
        HookCallback.Param remap=before(BASE+"utils.material.VolumeBlurConfigWriter#writeSideAccessoryPlatform",writer,new Object(),new android.content.Context(),new Object(),.6f,zenControl);
        check(remap.returnEarly&&writer.barWrites==1,"native DND button size/theme updates retain BAR_BACKGROUND instead of replacing it with SIDE_ACCESSORY material");
        check(!before(BASE+"utils.material.VolumeBlurConfigWriter#writeSideAccessoryPlatform",writer,new Object(),new android.content.Context(),new Object(),.6f,new View()).returnEarly,"other native volume side buttons keep their own material policy");
        check(segments.indicatorView.getBackground() instanceof com.oplusos.systemui.common.view.SmoothCornersGradientDrawable,"sound selection uses native volume progress material");
        for(View selection:new View[]{segments.indicatorView,zenControl.getChildAt(0)}) {
            selection.actualWidth=selection.actualHeight=209;
            for(View.OnLayoutChangeListener listener:selection.layoutListeners)listener.onLayoutChange(selection,0,0,209,209,0,0,0,0);
            check(selection.getBackground().getBounds().width()==209,"both progress materials refresh bounds after layout");
        }
        for(View control:new View[]{segments,zenControl}) {
            check(control.getForeground() instanceof VolumeShortcutFeedback,"both controls share the native volume feedback adapter");
            var feedback=(VolumeShortcutFeedback)control.getForeground();
            var effect=(com.coui.appcompat.spotlight.COUISpotLightEffectDrawable)feedback.getDrawable();
            check(effect.target==com.oplus.systemui.volume.utils.spotlight.VolumeSpotLightTarget.SEEK_BAR_PANEL,"shortcut spotlight uses expanded seekbar preset");
            check(!control.touch.onTouch(control,new MotionEvent(MotionEvent.ACTION_DOWN,0,0)),"feedback does not consume native gesture");
            check(control.getScaleX()==1&&control.getScaleY()==1&&ValueAnimator.running.isEmpty(),"expanded seekbar feedback does not add a generic button shrink animation");
            control.touch.onTouch(control,new MotionEvent(MotionEvent.ACTION_CANCEL,0,0));ValueAnimator.finishAll();
            check(control.getScaleX()==1&&control.getScaleY()==1&&effect.touches==2,"cancel forwards to native spotlight and restores press pose");
        }
        LayoutParams quickLp=(LayoutParams)quick.layout;
        check(quickLp.topMargin==panel.bottom-22-209&&quickLp.height==209,"shortcut row sits one shared spacing below the track and one spacing above the panel bottom");
        check(com.oplus.systemui.volume.utils.material.VolumeBlurManager.INSTANCE.shortcutMaterials>=2&&com.oplus.systemui.volume.utils.material.VolumeBlurManager.INSTANCE.accessoryMaterials==0,"expanded shortcuts use the same native BAR_BACKGROUND material as the tracks, without the brighter SIDE_ACCESSORY mix");
        check(sound.isEnabled()&&zen.isEnabled(),"shortcuts enable when native expansion reaches its endpoint");
        check(segments.selectedIndex==0&&!zenIcon.state.value&&ValueAnimator.running.isEmpty(),"initial sound state goes directly to the native component without an app animation");
        sound.performClick();check(owner.mController.ringerCalls==0,"current native sound segment avoids redundant writes");
        vibrate.performClick();check(owner.mState.ringerModeInternal==1&&segments.selectedIndex==1,"native vibration selection routes to the controller using ringer mode one");
        silent.performClick();check(owner.mState.ringerModeInternal==0&&segments.selectedIndex==2,"native silent selection maps segment two to ringer mode zero");
        sound.performClick();check(owner.mState.ringerModeInternal==2&&owner.mController.ringerCalls==3,"native ringing selection maps segment zero to ringer mode two");
        check(ValueAnimator.running.isEmpty(),"sound switching is entirely delegated to vendor springs and Lottie");
        owner.mState.zenMode=3;d.mDialogView.observer.dispatchOnPreDraw();
        com.coui.appcompat.animation.dynamicanimation.COUISpringAnimation zenMotion=com.coui.appcompat.animation.dynamicanimation.COUISpringAnimation.running.stream().filter(a->a.view==zenControl.getChildAt(0)).findFirst().orElseThrow();zenMotion.advance(.2f);
        float interruptedZen=zenControl.getChildAt(0).getAlpha();
        check(interruptedZen>0&&interruptedZen<1&&zenIcon.animatedUpdates>0&&ValueAnimator.running.isEmpty(),"DND state changes run native Lottie and a native highlight spring without a handcrafted icon crossfade");
        check(zenIcon.submittedTint!=null&&zenIcon.submittedTint.getDefaultColor()==zenIcon.iconViewProxy.tint.getDefaultColor(),"DND Lottie captures the target contrast tint when its native animation is submitted");
        zen.performClick();check(owner.mState.zenMode==0,"DND shortcut disables the active native zen mode");
        check(!zenMotion.cancelled&&zenMotion.target==0&&zenControl.getChildAt(0).getAlpha()==interruptedZen,"reversing DND retargets the existing native spring without snapping the visible highlight");
        com.coui.appcompat.animation.dynamicanimation.COUISpringAnimation.finishAll();
        check(!zenIcon.state.value&&zenControl.getChildAt(0).getAlpha()==0,"DND off finishes with its inactive native icon and no selected fill");
        zen.performClick();check(owner.mState.zenMode==3&&owner.mController.zenCalls==2,"DND shortcut restores the previous zen mode instead of forcing a new policy");
        com.coui.appcompat.animation.dynamicanimation.COUISpringAnimation.finishAll();
        check(zenIcon.state.value&&zenControl.getChildAt(0).getAlpha()==1,"DND on finishes with the native active icon and selected fill");
        owner.mState.ringerModeInternal=0;d.mDialogView.observer.dispatchOnPreDraw();
        check(silent.selected&&!sound.selected&&silent.getContentDescription().toString().contains("已选中"),"external native mode updates refresh segment selection and accessibility at stationary frames");
        check(segments.selectedIndex==2&&owner.mController.ringerCalls==3&&owner.mController.zenCalls==2,"external state updates go through the native selection API without extra controller writes");
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
        before(VIEW+"#showOrHideAnimation",d,0f,false,1,null);
        com.coui.appcompat.animation.dynamicanimation.COUISpringAnimation.finishAll();
        h.itemView.screenX=900;row.slider.screenX=sourceBarX;row.slider.setAlpha(.35f);
        d.mDialogView.observer.dispatchOnPreDraw();
        var morphReturn=com.coui.appcompat.animation.dynamicanimation.COUISpringAnimation.running.stream().filter(a->a.view==quick).findFirst().orElseThrow();
        float beforeReturn=quick.getAlpha();morphReturn.advance(.3f);d.mDialogView.observer.dispatchOnPreDraw();
        check(morphReturn.target==0&&quick.getAlpha()<beforeReturn&&quick.getAlpha()>0,"capsule reverses smoothly on the same native spring during dismissal");
        com.coui.appcompat.animation.dynamicanimation.COUISpringAnimation.finishAll();d.mDialogView.observer.dispatchOnPreDraw();
        check(Math.abs(d.mVerticalRowIconParent.getAlpha()-.35f)<.001f,"returning sidebar buttons fade with the original volume bar during panel dismissal");
        row.slider.setAlpha(0f);d.mDialogView.observer.dispatchOnPreDraw();
        row.slider.setAlpha(1f);d.mDialogView.observer.dispatchOnPreDraw();
        check(d.mVerticalRowIconParent.getAlpha()==0f&&quick.getAlpha()==0f,"native remove-animation alpha reset cannot flash old buttons back before terminal hide");
        CloudCheck.after(VIEW+"#resetSliderState",d,null);
        check(d.mDialogView.getBackground()==original&&button.leftToLeft==-1&&button.topMargin==0&&button.width==209&&button.height==209,"native terminal reset restores background and configured sidebar button size");
        check(d.mVolumeRecyclerview.layout.height==940,"native terminal reset restores the full configured sidebar length");
        check(d.mVolumePanelTipsText.getVisibility()==4&&d.mVolumePanelTitleText.getVisibility()==View.VISIBLE&&d.mSettingsButton.getVisibility()==View.VISIBLE,"terminal cleanup restores each accessory original visibility");
        check(d.mDialogView.observer.listeners.isEmpty(),"terminal reset removes the native frame observer");
        check(quick.getParent()==null&&sound.click==null&&vibrate.click==null&&silent.click==null&&zen.click==null,"native terminal cleanup removes the shortcut row and all four click listeners");
        check(segments.cleared&&segments.indicatorSpringTranslationX.cancelled&&segments.springAnimationBounce.cancelled&&segments.lottieViews.stream().allMatch(v->v.cancelled),"terminal cleanup cancels vendor indicator springs, pending gestures and Lottie animations");
        check(segments.iconSpringAnimatorSets.stream().allMatch(v->v.cancelled)&&segments.scaleSyncAnimatorHelper.stream().allMatch(v->v.spring.cancelled)&&segments.iconAlphaSpringAnimatorSets.stream().allMatch(v->v.alphaSpringAnim.cancelled&&v.colorSpringAnim.cancelled),"terminal cleanup cancels native icon translation, scale and color springs");
        check(segments.pressScaleHelper.stream().allMatch(v->v.spring.cancelled),"terminal cleanup also cancels native press feedback springs");
        check(zenIcon.recycled&&zen.touch==null&&zenMotion.cancelled&&morphReturn.cancelled,"terminal cleanup recycles the native DND icon and cancels both shape and selection springs");
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
        check(panelBlur.cornerRadius==77&&panelBlur.pathProvider==null&&panelBlur.radiusWeight==3f&&panelBlur.enableMotionSmoothCorner,"offset compact panel uses native backend local smooth geometry without a view-space path provider");
        QsTintCheck.after(QsTintCheck.B+"ViewBlurProxy#applyConfigToPlatformBlur",draw);
        check(panelBlur.pathProvider==nativePath&&panelBlur.radiusWeight==null&&!panelBlur.enableMotionSmoothCorner,"panel smooth shape overrides restore the native config after submission");
        d.mDialogView.getBackground().draw(new Canvas());
        check(panelAuto.getBounds().left==Math.max(22,12+Math.round((h.itemView.actualWidth-56)/2f)-22)+44,"left sidebar mirrors the short inward movement from the visible track");
        RecyclerView.ViewHolder leftAdded=holder(d,3,900,300);
        float leftBase=leftAdded.itemView.screenX-leftAdded.itemView.getTranslationX();
        float leftEnd=leftBase+endpoint("getEndX",animator,leftAdded);
        float leftStart=leftBase+endpoint("resolvePanelStartTranslationX",animator,leftAdded);
        check(leftStart<=leftEnd-row.slider.layout.width-22,"left-side panels give the last added row an outward start and the matching reverse exit");
        GradientDrawable plainPanel=new GradientDrawable();plainPanel.setCornerRadius(77);
        HookCallback.Param plainFactory=CloudCheck.param(null,plainPanel,new android.content.Context(),true,d.mDialogView,77f);
        Api102Runtime.callbacks.get(BASE+"utils.material.VolumeBlurManager#getVolumePanelBackground").after(plainFactory);
        Drawable smoothFlat=(Drawable)plainFactory.getResult();smoothFlat.setBounds(panelAuto.getBounds());smoothFlat.draw(new Canvas());
        check(((android.graphics.Path)Reflect.getObjectField(smoothFlat,"clip")).smooth&&plainPanel.getCornerRadius()==77,"default panel flat material uses native smooth clipping and restores the underlying circular radius");
        defaults.putBoolean("volume_panel_tint",true);defaults.putInt("volume_panel_color",0x44112233);CloudCheck.config(defaults);
        smoothFlat.draw(new Canvas());
        Object smoothFill=Reflect.getObjectField(smoothFlat,"fill");
        check(smoothFill instanceof com.oplusos.systemui.common.view.SmoothCornersGradientDrawable&&Reflect.getObjectField(smoothFill,"weight").equals(3f),"tinted panel fill uses the native smooth drawable rather than intersecting a circular fill");
        defaults.putBoolean("volume_panel_tint",false);CloudCheck.config(defaults);
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
    static void failedShortcutInitializationRestoresAnUnmountedPanel()throws Throwable {
        CloudCheck.config(config());
        OplusVolumeDialogImpl owner=new OplusVolumeDialogImpl();OplusVolumeDialogView d=owner.mOplusVolumeDialogView;
        d.mDialogView.res=volumeResources();d.mDialogView.setBackground(null);
        OplusVolumeRow row=new OplusVolumeRow();owner.mRows.add(row);d.volumeListAdapter.rows.add(row);
        RecyclerView.ViewHolder h=holder(d,0,900,300);((ViewGroup)h.itemView).addView(row.view);row.sliderFrame.parent=row.view;row.slider.parent=row.sliderFrame;
        OplusQSThreeStageLayout.failConstruction=true;
        try {
            HookCallback.Param result=before(VIEW+"#expandPanel",d);
            check(!result.returnEarly&&d.mDialogView.getBackground()==null,"failed native shortcut initialization preserves a null native background and allows native expansion");
            check(d.mSettingsButton.getVisibility()==View.VISIBLE&&d.mDialogView.observer.listeners.isEmpty(),"failed shortcut initialization restores accessories without leaving a compact frame observer");
        } finally {OplusQSThreeStageLayout.failConstruction=false;}
    }
}
