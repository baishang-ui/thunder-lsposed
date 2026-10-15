package io.github.baishangui.thunder;

import android.os.Bundle;
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
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;System.out.println("PASS: "+message);}
    static HookCallback.Param before(String key,Object owner,Object... args)throws Throwable{HookCallback.Param p=new HookCallback.Param();p.thisObject=owner;p.args=args;Api102Runtime.callbacks.get(key).before(p);return p;}
    static Bundle config() {
        Bundle b=new Bundle();b.putBoolean("enabled",true);b.putBoolean("volume_compact",true);b.putBoolean("volume_size",true);b.putInt("volume_width",76);b.putInt("volume_height",320);
        b.putBoolean("volume_button_size",true);b.putInt("volume_button",60);
        b.putBoolean("volume_bar_tint",true);b.putInt("volume_bar_color",0x4188aacc);b.putBoolean("volume_bar_blur",true);b.putInt("volume_bar_radius",123);
        b.putBoolean("volume_bar_corner_enabled",true);b.putInt("volume_bar_corner",18);b.putBoolean("volume_panel_blur",true);b.putInt("volume_panel_radius",80);return b;
    }
    static RecyclerView.ViewHolder holder(OplusVolumeDialogView d,int position,int x,int y) {
        ViewGroup item=new ViewGroup();item.actualWidth=154;item.actualHeight=300;item.screenX=x;item.screenY=y;item.layout=new ViewGroup.LayoutParams(154,-1);d.mVolumeRecyclerview.addView(item);
        return new RecyclerView.ViewHolder(item,position);
    }
    static float endpoint(String method,VolumeItemAnimator a,RecyclerView.ViewHolder h)throws Throwable {
        return ((Number)CloudCheck.after(ANIM+"#"+method,a,999f,h)).floatValue();
    }
    public static void main(String[] args)throws Throwable {
        ShadeHook.install("com.android.systemui","com.android.systemui",VolumePanelCheck.class.getClassLoader());
        check(!Api102Runtime.callbacks.containsKey(OWNER+"#computeTimeoutH$1"),"compact mode preserves native timeout policy");
        check(!Api102Runtime.callbacks.containsKey(OWNER+"#onStateChangedH"),"volume state and restrictions remain bound to native rows");
        check(Api102Runtime.callbacks.containsKey(VIEW+"#expandPanel")&&Api102Runtime.callbacks.containsKey(ANIM+"#getEndX"),"native expansion and geometry hooks are installed");
        OplusVolumeDialogImpl owner=new OplusVolumeDialogImpl();OplusVolumeDialogView d=owner.mOplusVolumeDialogView;
        OplusVolumeRow row=new OplusVolumeRow();owner.mRows.add(row);d.volumeListAdapter.rows.add(row);
        RecyclerView.ViewHolder h=holder(d,0,900,300);((ViewGroup)h.itemView).addView(row.view);row.sliderFrame.parent=row.view;row.slider.parent=row.sliderFrame;
        d.mSettingsButton.layout.width=96;
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
        check(d.mMoreRowStreamLl.layout.width==165&&d.mSettingsButton.layout.height==165&&d.mSettingsButton.layout.width==330,"sidebar and native settings button use independent button sizing");
        check(((Number)CloudCheck.after(VIEW+"#getIconHalfDelta",d,24f)).floatValue()==82.5f,"native sidebar button animation offsets follow custom height");
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
        check(panel.width()<1080&&panel.height()<1920&&panel.right<=1072&&panel.top>=24,"actual drawable bounds form a compact card inside the visible screen");
        before(BAR+"#onMeasure",row.slider,0,0);
        check(row.slider.layout.width>0&&row.slider.layout.width<209,"compact row width fits the card when configured bars are too wide");
        check(d.mDialogView.layout.width==37&&d.mVolumeRecyclerview.parent==d.mDialogView,"native root and recycler hierarchy remain untouched during transition");
        VolumeItemAnimator animator=new VolumeItemAnimator(d.volumeListAdapter);
        float previous=-1;
        for(int i=0;i<4;i++) {
            RecyclerView.ViewHolder item=i==0?h:holder(d,i,900-i*154,300);
            CloudCheck.after(BASE+"VolumeListAdapter#onBindViewHolder",adapter,null,item,i);
            item.itemView.actualWidth=item.itemView.layout.width;
            item.itemView.translationX=25;item.itemView.screenX+=25;
            float x=item.itemView.screenX-item.itemView.translationX+endpoint("getEndX",animator,item);
            float y=item.itemView.screenY+endpoint("getTranslationY",animator,item);
            check(x>=panel.left&&x+item.itemView.layout.width<=panel.right&&x>previous&&y>=panel.top&&y+300<=panel.bottom,"native row "+i+" animation endpoint fits card and preserves physical order");previous=x;
            float start=item.itemView.screenX-item.itemView.translationX+endpoint("resolvePanelStartTranslationX",animator,item);
            check(start==900,"native expand/collapse returns row "+i+" to captured sidebar anchor");
        }
        LayoutParams button=(LayoutParams)d.mSettingsButton.layout;
        check(button.leftToLeft==0&&button.topToTop==0&&button.leftMargin>=panel.left&&button.topMargin<panel.bottom,"native title/settings constraints are positioned within the card");
        OplusVolumeDialogView.CustomDialog nativeDialog=new OplusVolumeDialogView.CustomDialog(d);
        HookCallback.Param inside=before(VIEW+"$CustomDialog#dispatchTouchEvent",nativeDialog,new MotionEvent(0,panel.left+5,panel.top+5));
        check(!inside.returnEarly&&owner.dismissals==0,"inside touch continues through native dialog dispatch");
        HookCallback.Param outside=before(VIEW+"$CustomDialog#dispatchTouchEvent",nativeDialog,new MotionEvent(0,1,1));
        HookCallback.Param outsideUp=before(VIEW+"$CustomDialog#dispatchTouchEvent",nativeDialog,new MotionEvent(1,1,1));
        check(outsideUp.returnEarly,"outside gesture remains consumed until release");
        check(outside.returnEarly&&owner.dismissals==1,"outside tap requests native dismiss rather than destroying transition state");
        check(d.mDialogView.getBackground()==bounded,"compact surface remains through native collapse");
        int oldWidth=button.width;CloudCheck.after(VIEW+"#resetSliderState",d,null);
        check(d.mDialogView.getBackground()==original&&button.leftToLeft==-1&&button.topMargin==0&&button.width==oldWidth,"native terminal reset restores original background and all accessory constraints");
        float nativeEnd=endpoint("getEndX",animator,h);
        check(nativeEnd==999,"completed transitions no longer override native animator endpoints");
        Bundle off=config();off.putBoolean("enabled",false);CloudCheck.config(off);VolumeHook.refresh();
        check(row.slider.layout.width==37&&row.slider.mBackgroundWidth==56&&row.slider.mBackgroundRadius==28&&row.slider.mPaddingVertical==28,"turning customization off restores native layout, geometry and padding");
        check(d.mMoreRowStreamLl.layout.width==37&&d.mSettingsButton.layout.width==96,"button dimensions restore independently");
        expand=before(VIEW+"#expandPanel",d);check(!expand.returnEarly,"disabled mode preserves native expansion");
        Bundle defaults=new Bundle();defaults.putBoolean("enabled",true);defaults.putBoolean("volume_compact",true);CloudCheck.config(defaults);
        h.itemView.screenX=12;h.itemView.translationX=0;owner.mExpanded=false;
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
        check(panelAuto.getBounds().left==22,"left sidebar expands inward from its physical position");
        before(BAR+"#onMeasure",row.slider,0,0);check(row.slider.mBackgroundRadius<=row.slider.mBackgroundWidth/2,"compact sizing without custom corners keeps native radius within track width");
        Drawable during=d.mDialogView.getBackground();CloudCheck.config(off);VolumeHook.refresh();
        check(d.mDialogView.getBackground()==during,"settings refresh requests native collapse without stripping its compact background");
        CloudCheck.after(VIEW+"#resetSliderState",d,null);VolumeHook.refresh();
        check(row.slider.mBackgroundWidth==56&&row.slider.mBackgroundRadius==28,"default compact geometry also restores after native terminal hide");
        check(!ShadeHook.snapshot().qsCards.tint,"volume configuration remains independent of control-center materials");
        System.out.println("Volume native preservation checks passed: "+checks);
    }
}
