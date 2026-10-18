package io.github.baishangui.thunder;

import android.os.Bundle;
import android.graphics.Canvas;
import com.oplus.posteffect.drawable.BaseDrawable;
import com.oplusos.systemui.common.blurability.*;

public final class PanelColorContinuityCheck {
    public static void main(String[] args) throws Throwable {
        ShadeHook.install("com.android.systemui", "com.android.systemui", PanelColorContinuityCheck.class.getClassLoader());
        Bundle settings=new Bundle();settings.putBoolean("enabled",true);settings.putBoolean("shade_tint",true);
        settings.putInt("shade_color",0xffcc3366);settings.putBoolean("panel_saturation_enabled",true);settings.putInt("panel_saturation",0);
        CloudCheck.config(settings);
        BlurConfig config=PanelStartFrameCheck.panel();
        BlurMixConfig mix=(BlurMixConfig)CloudCheck.after(PanelStartFrameCheck.B+"BlurConfig#getPlatformMixConfig",config,config.platformMixConfig);
        if(mix.getPlaceHolderColor()!=0)throw new AssertionError("No full-strength tint before the first capture arrives");
        HookCallback render=Api102Runtime.callbacks.get("com.oplus.posteffect.drawable.BlendDrawable#onDrawContent");
        if(render==null)throw new AssertionError("Saturation must cover shader and bitmap recordings at their common entry");
        BaseDrawable drawable=new BaseDrawable();PanelSaturationHook.track(drawable,config,1f);
        var call=CloudCheck.param(drawable,null,new Canvas());render.before(call);
        if(drawable.paint.getColorFilter()==null||drawable.bitmapPaint.getColorFilter()==null)
            throw new AssertionError("Both rendering paths must use the same saturation");
        call.failure=new IllegalStateException("record failed");render.after(call);
        if(drawable.paint.getColorFilter()!=null||drawable.bitmapPaint.getColorFilter()!=null)
            throw new AssertionError("Both paints must be restored after failed recordings");
        System.out.println("Verified first-capture tint and shader/bitmap saturation continuity.");
    }
}
