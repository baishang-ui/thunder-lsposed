package io.github.baishangui.thunder;

import android.os.Bundle;
import android.widget.*;
import android.graphics.Canvas;
import com.oplus.systemui.volume.*;
import com.oplus.systemui.volume.view.*;
import com.oplusos.systemui.common.blurability.*;
import com.oplusos.systemui.common.blurability.drawable.AutoBlurDrawable;

/** Runs actual VolumeHook callbacks against Android/C17 stand-ins, not device rendering. */
public final class VolumePanelCheck {
    static final String BASE="com.oplus.systemui.volume.",OWNER=BASE+"OplusVolumeDialogImpl",VIEW=BASE+"view.OplusVolumeDialogView",BAR=BASE+"OplusVolumeSeekBar";
    static int checks;
    static void check(boolean v,String m){if(!v)throw new AssertionError(m);checks++;System.out.println("PASS: "+m);}
    static HookCallback.Param before(String key,Object owner,Object... args)throws Throwable{return QsTintCheck.before(key,owner,args);}
    static Bundle config() {
        Bundle b=new Bundle();b.putBoolean("enabled",true);b.putBoolean("volume_compact",true);b.putBoolean("volume_size",true);b.putInt("volume_width",76);b.putInt("volume_height",320);
        b.putBoolean("volume_button_size",true);b.putInt("volume_button",60);
        b.putBoolean("volume_bar_tint",true);b.putInt("volume_bar_color",0x4188aacc);b.putBoolean("volume_bar_blur",true);b.putInt("volume_bar_radius",123);
        b.putBoolean("volume_bar_corner_enabled",true);b.putInt("volume_bar_corner",18);b.putBoolean("volume_panel_blur",true);b.putInt("volume_panel_radius",80);return b;
    }
    static java.util.List<SeekBar> sliders(android.view.View view) {
        java.util.List<SeekBar> out=new java.util.ArrayList<>();
        if(view instanceof SeekBar s)out.add(s);
        if(view instanceof android.view.ViewGroup g)for(android.view.View child:g.children)out.addAll(sliders(child));
        return out;
    }
    public static void main(String[] args)throws Throwable {
        ShadeHook.install("com.android.systemui","com.android.systemui",VolumePanelCheck.class.getClassLoader());
        check(Api102Runtime.callbacks.containsKey(OWNER+"#expandPanel"),"volume expansion hooks installed");
        OplusVolumeDialogImpl owner=new OplusVolumeDialogImpl();OplusVolumeRow row=new OplusVolumeRow();owner.mRows.add(row);
        row.slider.screenX=900;row.slider.screenY=200;
        CloudCheck.config(config());
        CloudCheck.after(VIEW+"#updateRowsH",owner.mOplusVolumeDialogView,null);
        check(row.slider.layout.width==209&&row.slider.layout.height==880,"configured bar dimensions reach actual layout");
        check(row.slider.mBackgroundWidth==209&&row.slider.mMaxHeight==880,"native progress geometry and touch height match layout");
        check(owner.mOplusVolumeDialogView.mMoreRowStreamLl.layout.width==165,"sidebar button size applies independently");
        HookCallback.Param draw=before(BAR+"#onDraw",row.slider,new Canvas());
        check(row.slider.mBackgroundColor==0x4188aacc&&row.slider.mCurBackgroundRadius==28,"bar tint and clamped corners apply during draw");
        draw.failure=new IllegalStateException("frame failure");QsTintCheck.after(BAR+"#onDraw",draw);
        check(row.slider.mBackgroundColor==0xff234567&&row.slider.mCurProgressRadius==26,"native paint and progress radius restore after failed draw");
        String factory=BASE+"utils.material.VolumeBlurManager#getVolumeBarBackground";
        BlurConfig blur=new BlurConfig();blur.platformMixConfig=QsTintCheck.multi(0xff778899);Object nativeMix=blur.platformMixConfig;
        AutoBlurDrawable auto=new AutoBlurDrawable(new ViewBlurProxy(blur));
        HookCallback.Param material=CloudCheck.param(null,auto,new android.content.Context(),row.slider,row.view,28f,true);
        Api102Runtime.callbacks.get(factory).after(material);
        check("volume-bar".equals(Reflect.getAdditionalInstanceField(blur,"thunder.role")),"native volume blur gets its own role");
        draw=before(QsTintCheck.B+"ViewBlurProxy#applyConfigToPlatformBlur",auto.proxy);
        check(blur.blurRadius==123&&blur.platformMixConfig!=nativeMix,"volume blur and tint use the common native material pipeline");
        QsTintCheck.after(QsTintCheck.B+"ViewBlurProxy#applyConfigToPlatformBlur",draw);
        check(blur.platformMixConfig==nativeMix,"volume native shader is restored after backend submission");
        HookCallback.Param expand=CloudCheck.param(owner,"native marker");Api102Runtime.callbacks.get(OWNER+"#expandPanel").before(expand);
        check(expand.getResult()==null&&!owner.mExpanded,"custom expansion intercepts before native fullscreen state changes");
        PopupWindow popup=PopupWindow.latest;
        check(popup.showing&&popup.width<1080&&popup.height<1920&&popup.screen&&!popup.touchModal,"expansion is a bounded popup with screen coordinates and outside touch");
        check(popup.content.getBackground() instanceof android.graphics.drawable.LayerDrawable,"popup uses a background blur drawable after window attachment");
        check(popup.x<row.slider.screenX,"right-hand bar opens panel to its left");
        java.util.List<SeekBar> sliders=sliders(popup.content);check(sliders.size()==1,"panel contains native available volume streams");
        SeekBar slider=sliders.get(0);slider.listener.onStartTrackingTouch(slider);slider.listener.onProgressChanged(slider,11,true);
        check(owner.mController.calls==1&&owner.mController.stream==3&&owner.mController.level==11,"user drag uses native controller and actual stream");
        check(((Integer)CloudCheck.after(OWNER+"#computeTimeoutH$1",owner,3000))==16000,"dragging extends the native dismissal timeout");
        slider.listener.onStopTrackingTouch(slider);
        row.ss.level=4;row.slider.disabled=true;CloudCheck.after(OWNER+"#onStateChangedH",owner,null);
        check(slider.progress==4&&!slider.enabled&&owner.mController.calls==1,"external state updates progress and restrictions without writing volume back");
        before(OWNER+"#onConfigChanged",owner,new android.content.res.Configuration());
        check(!popup.showing&&row.slider.attachListeners.isEmpty()&&row.slider.layoutListeners.isEmpty(),"rotation dismisses popup and removes anchor listeners");
        row.slider.screenX=12;row.slider.disabled=false;before(OWNER+"#expandPanel",owner);popup=PopupWindow.latest;
        check(popup.x>row.slider.screenX,"left-hand bar opens panel to its right");
        Bundle off=config();off.putBoolean("enabled",false);CloudCheck.config(off);VolumeHook.refresh();
        check(!popup.showing&&row.slider.layout.width==37&&row.slider.layout.height==21,"disabling module closes popup and restores native layout");
        check(row.slider.mBackgroundWidth==56&&row.slider.mMaxHeight==280&&row.slider.mSeekbarMinWidth==40,"native internal geometry restores on disable");
        check(owner.mOplusVolumeDialogView.mMoreRowStreamLl.layout.width==37,"native button dimensions restore on disable");
        expand=CloudCheck.param(owner,"native marker");Api102Runtime.callbacks.get(OWNER+"#expandPanel").before(expand);
        check("native marker".equals(expand.getResult()),"disabled module leaves original expansion available");
        CloudCheck.config(config());
        android.graphics.drawable.GradientDrawable original=new android.graphics.drawable.GradientDrawable();
        material=CloudCheck.param(null,original,new android.content.Context(),row.slider,row.view,28f,true);
        Api102Runtime.callbacks.get(factory).after(material);
        android.graphics.drawable.Drawable fallback=(android.graphics.drawable.Drawable)material.getResult();
        check(fallback!=original,"non-blur backgrounds retain a reversible dynamic wrapper");
        fallback.setBounds(0,0,100,240);fallback.draw(new Canvas());
        check(original.draws==0,"custom flat tint does not stack the native opaque fill");
        CloudCheck.config(off);fallback.draw(new Canvas());
        check(original.draws==1,"turning custom material off draws the original background again");
        check(ShadeHook.snapshot().qsCards.tint==false,"volume styling leaves control-center materials independent");
        System.out.println("Volume panel checks passed: "+checks);
    }
}
