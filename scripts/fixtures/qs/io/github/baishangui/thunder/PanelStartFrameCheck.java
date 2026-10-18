package io.github.baishangui.thunder;

import android.os.Bundle;
import com.oplusos.systemui.common.blurability.*;

/** Models C17's final radius clamp, after the real module's radius/getter hooks. */
public final class PanelStartFrameCheck {
    static final String B="com.oplusos.systemui.common.blurability.";
    static final String CONSUMER=B+"ViewBlurProxy#applyConfigToPlatformBlur";
    static final String SET_RADIUS="com.oplus.posteffect.BlurParam#setBlurRadius";
    static int count;
    static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);count++;}
    static int submit(int radius)throws Throwable {
        HookCallback.Param p=CloudCheck.param(null,null,radius);
        HookCallback hook=Api102Runtime.callbacks.get(SET_RADIUS);
        if(hook!=null)hook.before(p);
        return (Integer)p.args[0];
    }
    static BlurConfig panel() {
        BlurConfig c=new BlurConfig();c.blurRadius=100;
        c.platformMixConfig=new BlurMixConfig.BlurMixSingleWithShader(new MixColorWithShader(5,0x92000000,2,0x66999999));
        Reflect.setAdditionalInstanceField(c,"thunder.role","panel");return c;
    }
    static int nativeFrame(BlurConfig c,float amount,int minimum)throws Throwable {
        int radius=(Integer)CloudCheck.after(B+"BlurConfig#platformBlurRadiusForAmount",c,c.blurRadius,amount);
        BlurMixConfig mix=(BlurMixConfig)CloudCheck.after(B+"BlurConfig#getPlatformMixConfig",c,c.platformMixConfig);
        // Transcribed from PlatformBlurDrawable.applyBlurConfig(): an opaque
        // single shader imposes a 15/25px floor *after* platformBlurRadiusForAmount.
        if(!mix.getAlphaWithBlurAmount())radius=amount==0?0:Math.max(radius,minimum);
        return submit(radius);
    }
    public static void main(String[] args)throws Throwable {
        ShadeHook.install("com.android.systemui","com.android.systemui",PanelStartFrameCheck.class.getClassLoader());
        Bundle b=new Bundle();b.putBoolean("enabled",true);b.putBoolean("shade_blur",true);b.putBoolean("shade_tint",true);
        BlurConfig c=panel();ViewBlurProxy proxy=new ViewBlurProxy(c);
        for(boolean tint:new boolean[]{false,true})for(int radius:new int[]{0,1,73,200})for(int floor:new int[]{15,25}) {
            b.putBoolean("shade_tint",tint);b.putInt("shade_radius",radius);CloudCheck.config(b);
            for(float amount:new float[]{0,.001f,.005f,.01f,.025f,.049f,.05f,.075f,.1f,.5f,1,.05f,.001f,0}) {
                proxy.blurAmount=amount;
                Object saved=c.platformMixConfig;
                HookCallback.Param frame=QsTintCheck.before(CONSUMER,proxy);
                check(nativeFrame(c,amount,floor)==(int)(radius*amount),"final compositor radius stays continuous at "+amount+", radius="+radius+", native floor="+floor);
                check(!((BlurMixConfig)c.platformMixConfig).getAlphaWithBlurAmount(),"continuous radius does not reintroduce a translucent scaled background");
                frame.failure=new IllegalStateException("render failed");QsTintCheck.after(CONSUMER,frame);
                check(c.platformMixConfig==saved,"frame restores the native owner config after failure");
                check(submit(floor)==floor,"radius scope is cleared after each frame");
            }
        }
        b.putInt("shade_radius",73);CloudCheck.config(b);proxy.blurAmount=.01f;
        HookCallback.Param outer=QsTintCheck.before(CONSUMER,proxy);
        check(submit(25)==0,"panel frame overrides the native minimum");
        BlurConfig card=panel();Reflect.setAdditionalInstanceField(card,"thunder.role","notification-card");
        HookCallback.Param nested=QsTintCheck.before(CONSUMER,new ViewBlurProxy(card));
        check(submit(25)==25,"nested unrelated card does not inherit panel radius");
        nested.failure=new IllegalStateException("nested render failed");QsTintCheck.after(CONSUMER,nested);
        check(submit(25)==0,"nested failure restores the surrounding panel radius");
        QsTintCheck.after(CONSUMER,outer);check(submit(25)==25,"outer completion clears panel radius");
        b.putBoolean("shade_blur",false);CloudCheck.config(b);
        HookCallback.Param stock=QsTintCheck.before(CONSUMER,proxy);
        check(submit(25)==25,"tint-only customization retains the system radius policy");
        QsTintCheck.after(CONSUMER,stock);
        b.putBoolean("shade_blur",true);b.putBoolean("enabled",false);CloudCheck.config(b);
        stock=QsTintCheck.before(CONSUMER,proxy);check(submit(25)==25,"master off retains system radius policy");QsTintCheck.after(CONSUMER,stock);
        System.out.println("Verified "+count+" panel start-frame checks; actual device rendering remains unverified.");
    }
}
