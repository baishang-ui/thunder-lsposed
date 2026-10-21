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
    static final class Mini extends android.view.View {
        int size=176;
        @Override public int getWidth(){return size;}
        @Override public int getHeight(){return size;}
    }
    static final class MiniContainer extends com.oplus.systemui.plugins.seedling.capsule.ui.view.CapsuleContainer {
        final Mini mini=new Mini();
        public android.view.View getMiniView(){return mini;}
    }
    static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);checks++;}
    static void close(float expected,float actual,String text){check(Math.abs(expected-actual)<.001f,text+": "+actual);}
    public static void main(String[] args)throws Throwable {
        CloudHook.install(CloudCapsuleCornerCheck.class.getClassLoader());
        Bundle b=new Bundle();b.putBoolean("enabled",true);b.putBoolean("cloud_enabled",true);
        b.putBoolean("cloud_corner_enabled",false);b.putInt("cloud_corner",128);
        b.putInt("cloud_card_color",0);b.putBoolean("cloud_card_corner_enabled",true);b.putInt("cloud_card_corner",19);
        CloudCheck.config(b);
        check(ShadeHook.snapshot().cloudCorner==128&&ShadeHook.snapshot().cloudArea.corner==128,"capsule radius survives the expanded configuration range");
        check(ShadeHook.snapshot().cloudCardArea.corner==19,"expanded-card radius stays independent");
        SizedCapsule view=new SizedCapsule();
        GradientDrawable fill=(GradientDrawable)view.getBackground(),edge=(GradientDrawable)view.getForeground();
        fill.setCornerRadius(17);edge.setCornerRadius(17);
        fill.mGradientDrawableExt.setSmoothRoundStyle(true,1.1f,1);
        CloudCheck.after(BASE+"onAttachedToWindow",view,null);
        close(88,fill.getCornerRadius(),"default capsule has semicircular ends without enabling custom corners");
        close(88,edge.getCornerRadius(),"capsule stroke shares the circular radius");
        check(!fill.mGradientState.smooth&&!edge.mGradientState.smooth,"pill ends use the native circular curve instead of G2");
        var outline=(com.oplus.systemui.plugins.common.util.j)view.getOutlineProvider();
        check(outline.b.d().equals(0),"capsule native outline selects ROUND");
        close(88,((Number)outline.b.e()).floatValue(),"clip agrees with fill and stroke");
        view.height=220;
        for(var listener:java.util.List.copyOf(view.layoutListeners))listener.onLayoutChange(view,0,0,600,220,0,0,600,176);
        close(110,fill.getCornerRadius(),"default radius follows native height relayout without a settings change");
        view.height=176;
        b.putBoolean("cloud_corner_enabled",true);b.putInt("cloud_corner",9);CloudCheck.config(b);CloudHook.refresh();
        close(9*view.getResources().getDisplayMetrics().density,fill.getCornerRadius(),"smaller custom radius remains adjustable");
        b.putInt("cloud_corner",128);b.putBoolean("cloud_blur",true);b.putBoolean("cloud_tint",true);
        CloudCheck.config(b);CloudHook.refresh();
        close(88,com.oplus.view.ViewRootManager.last.corner,"native blur shares saturated capsule radius");
        check(com.oplus.view.ViewRootManager.last.params.smoothType==0,"native blur uses the same circular curve");
        close(2,com.oplus.view.ViewRootManager.last.params.smoothWeight,"native blur clears the old G2 weight with the host's valid round value");
        b.putBoolean("cloud_corner_enabled",false);b.putBoolean("cloud_blur",false);b.putBoolean("cloud_tint",false);
        CloudCheck.config(b);CloudHook.refresh();
        close(88,fill.getCornerRadius(),"disabling custom shape returns to the default circular capsule");
        check(!fill.mGradientState.smooth,"default capsule keeps ROUND after leaving custom corners");
        for(boolean blur:new boolean[]{false,true})for(boolean tint:new boolean[]{false,true}) {
            b.putBoolean("cloud_blur",blur);b.putBoolean("cloud_tint",tint);CloudCheck.config(b);CloudHook.refresh();
            if(blur)close(88,com.oplus.view.ViewRootManager.last.corner,"default blur capsule stays circular with tint="+tint);
            else close(88,fill.getCornerRadius(),"default native fill stays circular with tint="+tint);
            close(88,edge.getCornerRadius(),"default stroke stays circular with blur="+blur+", tint="+tint);
            check(!edge.mGradientState.smooth,"default stroke does not regain G2 smoothing");
        }
        b.putBoolean("cloud_enabled",false);CloudCheck.config(b);CloudHook.refresh();
        close(17,fill.getCornerRadius(),"disabling the cloud feature restores native radius");
        check(fill.mGradientState.smooth&&fill.mGradientState.mSmoothWeight==1.1f,"disabling the feature restores the saved vendor smoothing");
        b.putBoolean("cloud_enabled",true);b.putBoolean("cloud_blur",false);b.putBoolean("cloud_tint",false);
        CloudCheck.config(b);CloudHook.refresh();
        close(88,fill.getCornerRadius(),"re-enabling uses the default circular radius");
        CloudCheck.after(BASE+"onDetachedFromWindow",view,null);
        check(view.layoutListeners.isEmpty(),"detached capsules release geometry listeners");
        for(boolean animated:new boolean[]{false,true}) {
            b.putBoolean("cloud_animation_enabled",animated);b.putBoolean("cloud_corner_enabled",false);
            CloudCheck.config(b);
            MiniContainer container=new MiniContainer();Mini mini=container.mini;
            GradientDrawable miniFill=(GradientDrawable)mini.getBackground();
            miniFill.setCornerRadius(20);miniFill.mGradientDrawableExt.setSmoothRoundStyle(true,2,1);
            QsGeometryCheck.afterIfPresent("com.oplus.systemui.plugins.seedling.capsule.ui.view.CapsuleContainer#onFinishInflate",container,null);
            close(88,miniFill.getCornerRadius(),"default mini capsule is circular with animations="+animated);
            check(!miniFill.mGradientState.smooth,"mini source no longer keeps its XML G2 curve");
            for(var listener:java.util.List.copyOf(mini.attachListeners))listener.onViewAttachedToWindow(mini);
            check(((com.oplus.systemui.plugins.common.util.j)mini.getOutlineProvider()).b.d().equals(0),"mini clip uses native ROUND");
            mini.size=220;
            for(var listener:java.util.List.copyOf(mini.layoutListeners))listener.onLayoutChange(mini,0,0,220,220,0,0,176,176);
            close(110,miniFill.getCornerRadius(),"default mini radius tracks the current short side");
            mini.size=176;
            for(var listener:java.util.List.copyOf(mini.layoutListeners))listener.onLayoutChange(mini,0,0,176,176,0,0,220,220);
            b.putBoolean("cloud_blur",true);b.putBoolean("cloud_tint",true);CloudCheck.config(b);CloudHook.refresh();
            close(88,com.oplus.view.ViewRootManager.last.corner,"mini blur saturates at the same circular radius");
            close(0,com.oplus.view.ViewRootManager.last.params.smoothType,"mini blur selects ROUND");
            close(2,com.oplus.view.ViewRootManager.last.params.smoothWeight,"mini blur uses the native non-smooth weight");
            b.putBoolean("cloud_blur",false);b.putBoolean("cloud_tint",false);
            b.putBoolean("cloud_corner_enabled",false);CloudCheck.config(b);CloudHook.refresh();
            close(88,miniFill.getCornerRadius(),"disabling custom mini corners keeps the default circle");
            check(!miniFill.mGradientState.smooth,"default mini keeps ROUND");
            for(boolean blur:new boolean[]{false,true})for(boolean tint:new boolean[]{false,true}) {
                b.putBoolean("cloud_blur",blur);b.putBoolean("cloud_tint",tint);CloudCheck.config(b);CloudHook.refresh();
                if(blur)close(88,com.oplus.view.ViewRootManager.last.corner,"default mini blur stays circular with tint="+tint);
                else close(88,miniFill.getCornerRadius(),"default mini fill stays circular with tint="+tint);
            }
            b.putBoolean("enabled",false);CloudCheck.config(b);CloudHook.refresh();
            close(20,miniFill.getCornerRadius(),"disabling the module restores the mini's original XML radius");
            check(miniFill.mGradientState.smooth&&miniFill.mGradientState.mSmoothWeight==2,"disabling the module restores the mini's original smoothing");
            b.putBoolean("enabled",true);b.putBoolean("cloud_blur",false);b.putBoolean("cloud_tint",false);
            b.putBoolean("cloud_corner_enabled",true);CloudCheck.config(b);CloudHook.refresh();
            for(var listener:java.util.List.copyOf(mini.attachListeners))listener.onViewDetachedFromWindow(mini);
            close(20,miniFill.getCornerRadius(),"mini detach restores native geometry");
            check(mini.layoutListeners.isEmpty(),"mini detach releases size listeners");
            for(var listener:java.util.List.copyOf(mini.attachListeners))listener.onViewAttachedToWindow(mini);
            close(88,miniFill.getCornerRadius(),"reused mini reattaches with saturated round corners");
            check(mini.attachListeners.size()==1&&mini.layoutListeners.size()==1,"mini reattach keeps exactly one lifecycle and size listener");
            for(var listener:java.util.List.copyOf(mini.attachListeners))listener.onViewDetachedFromWindow(mini);
        }
        System.out.println("Verified "+checks+" capsule range and circular-layer checks.");
    }
}
