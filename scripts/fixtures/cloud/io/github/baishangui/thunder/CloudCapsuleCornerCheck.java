package io.github.baishangui.thunder;

import android.os.Bundle;
import android.graphics.drawable.GradientDrawable;
import com.oplus.systemui.plugins.seedling.capsule.ui.view.CapsuleView;

/** A saturated capsule radius must produce semicircular ends in every native layer. */
public final class CloudCapsuleCornerCheck {
    private static int checks;
    private static final String BASE="com.oplus.systemui.plugins.seedling.capsule.ui.view.CapsuleView#";
    static final class SizedCapsule extends CapsuleView {
        int height=176;
        @Override public int getWidth(){return 600;}
        @Override public int getHeight(){return height;}
    }
    static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);checks++;}
    static void close(float expected,float actual,String text){check(Math.abs(expected-actual)<.001f,text+": "+actual);}
    public static void main(String[] args)throws Throwable {
        CloudHook.install(CloudCapsuleCornerCheck.class.getClassLoader());
        Bundle b=new Bundle();b.putBoolean("enabled",true);b.putBoolean("cloud_enabled",true);
        b.putBoolean("cloud_corner_enabled",true);b.putInt("cloud_corner",128);
        b.putInt("cloud_card_color",0);b.putBoolean("cloud_card_corner_enabled",true);b.putInt("cloud_card_corner",19);
        CloudCheck.config(b);
        check(ShadeHook.snapshot().cloudCorner==128&&ShadeHook.snapshot().cloudArea.corner==128,"capsule radius survives the expanded configuration range");
        check(ShadeHook.snapshot().cloudCardArea.corner==19,"expanded-card radius stays independent");
        SizedCapsule view=new SizedCapsule();
        GradientDrawable fill=(GradientDrawable)view.getBackground(),edge=(GradientDrawable)view.getForeground();
        fill.setCornerRadius(17);edge.setCornerRadius(17);
        fill.mGradientDrawableExt.setSmoothRoundStyle(true,1.1f,1);
        CloudCheck.after(BASE+"onAttachedToWindow",view,null);
        close(88,fill.getCornerRadius(),"large custom radius saturates at half the actual capsule height");
        close(88,edge.getCornerRadius(),"capsule stroke shares the circular radius");
        check(!fill.mGradientState.smooth&&!edge.mGradientState.smooth,"pill ends use the native circular curve instead of G2");
        var outline=(com.oplus.systemui.plugins.common.util.j)view.getOutlineProvider();
        check(outline.b.d().equals(0),"capsule native outline selects ROUND");
        close(88,((Number)outline.b.e()).floatValue(),"clip agrees with fill and stroke");
        view.height=220;
        for(var listener:java.util.List.copyOf(view.layoutListeners))listener.onLayoutChange(view,0,0,600,220,0,0,600,176);
        close(110,fill.getCornerRadius(),"native height relayout recomputes the saturated radius without a settings change");
        view.height=176;
        b.putInt("cloud_corner",9);CloudCheck.config(b);CloudHook.refresh();
        close(9*view.getResources().getDisplayMetrics().density,fill.getCornerRadius(),"smaller custom radius remains adjustable");
        b.putInt("cloud_corner",128);b.putBoolean("cloud_blur",true);b.putBoolean("cloud_tint",true);
        CloudCheck.config(b);CloudHook.refresh();
        close(88,com.oplus.view.ViewRootManager.last.corner,"native blur shares saturated capsule radius");
        check(com.oplus.view.ViewRootManager.last.params.smoothType==0,"native blur uses the same circular curve");
        b.putBoolean("cloud_corner_enabled",false);b.putBoolean("cloud_blur",false);b.putBoolean("cloud_tint",false);
        CloudCheck.config(b);CloudHook.refresh();
        close(17,fill.getCornerRadius(),"disabling custom shape restores native radius");
        check(fill.mGradientState.smooth&&fill.mGradientState.mSmoothWeight==1.1f,"disabling restores the saved vendor smoothing");
        CloudCheck.after(BASE+"onDetachedFromWindow",view,null);
        check(view.layoutListeners.isEmpty(),"detached capsules release geometry listeners");
        System.out.println("Verified "+checks+" capsule range and circular-layer checks.");
    }
}
