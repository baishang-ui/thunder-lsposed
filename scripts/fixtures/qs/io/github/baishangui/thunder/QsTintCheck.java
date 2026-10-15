package io.github.baishangui.thunder;

import android.os.Bundle;
import android.graphics.Canvas;
import com.oplusos.systemui.common.blurability.*;
import com.oplusos.systemui.common.blurability.drawable.AutoBlurDrawable;
import com.oplus.systemui.qs.base.res.drawable.*;
import com.oplus.systemui.plugins.drawable.MixColorPluginDrawable;

public final class QsTintCheck {
    static final String B="com.oplusos.systemui.common.blurability.";
    static final String TILE=MixColorTileDrawable.class.getName(), FLAT=GradientTileDrawable.class.getName();
    static int count;
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);count++;System.out.println("PASS: "+message);}
    static HookCallback.Param before(String key,Object owner,Object... args)throws Throwable {
        HookCallback.Param p=CloudCheck.param(owner,null,args);Api102Runtime.callbacks.get(key).before(p);return p;
    }
    static void after(String key,HookCallback.Param p)throws Throwable {Api102Runtime.callbacks.get(key).after(p);}
    static BlurMixConfig.BlurMixMultiWithShader multi(int color) {
        return new BlurMixConfig.BlurMixMultiWithShader(new MixColorWithShader(4,color,2,0x33445566),new MixColorWithShader(5,0x22443322,2,0x11665544));
    }
    public static void main(String[] args)throws Throwable {
        ShadeHook.install("com.android.systemui","com.android.systemui",QsTintCheck.class.getClassLoader());
        check(Api102Runtime.callbacks.containsKey(TILE+"#<init>"),"control center native card backgrounds are registered");
        Bundle b=new Bundle();b.putBoolean("enabled",true);b.putBoolean("qs_card_tint",true);b.putInt("qs_card_color",0x4188aacc);
        b.putBoolean("panel_tint",true);b.putInt("panel_radius",100);b.putInt("panel_color",0x55443322);
        b.putBoolean("banner_tint",true);b.putInt("banner_color",0x66778899);CloudCheck.config(b);
        BlurMixConfig.BlurMixMultiWithShader original=multi(0x88998877);original.setMirrorScale(.65f);original.setAlphaWithBlurAmount(true);original.setPlaceHolderColor(0x33445566);
        BlurConfig c=new BlurConfig();c.platformMixConfig=original;c.blurRadius=117;
        ViewBlurProxy proxy=new ViewBlurProxy(c);AutoBlurDrawable auto=new AutoBlurDrawable(proxy);MixColorTileDrawable tile=new MixColorTileDrawable(auto);
        CloudCheck.after(TILE+"#<init>",tile,null,auto,new java.util.LinkedHashMap<>());
        BlurMixConfig.BlurMixMultiWithShader out=(BlurMixConfig.BlurMixMultiWithShader)CloudCheck.after(B+"BlurConfig#getPlatformMixConfig",c,original);
        check(out!=original && out.foregroundShaderParam.topLayerColor==0x4188aacc,"card receives its own ARGB independently of shared panel and banner");
        check(out.backgroundShaderParam.topMode==0 && out.backgroundShaderParam.bottomMode==0 && out.foregroundShaderParam.bottomMode==0,"custom card tint removes remaining native color layers");
        check(out.getMirrorScale()==.65f && out.getAlphaWithBlurAmount() && out.getPlaceHolderColor()==0x4188aacc,"card keeps native animation and mirror flags with a matching placeholder");
        check(c.platformMixConfig==original && original.foregroundShaderParam.topLayerColor==0x88998877,"customization does not mutate system or shared shader objects");
        HookCallback.Param nativeCall=before(B+"ViewBlurProxy#applyConfigToPlatformBlur",proxy);
        check(c.platformMixConfig!=original && c.blurRadius==117,"native card backend gets tint while retaining system blur radius");
        nativeCall.failure=new IllegalStateException();after(B+"ViewBlurProxy#applyConfigToPlatformBlur",nativeCall);
        check(c.platformMixConfig==original && c.blurRadius==117,"card native state restores after a failed frame");
        HookCallback.Param draw=before(TILE+"#draw",tile,new Canvas());
        check(tile.maskColor==0,"card draw suppresses the independent native mask that would cover custom tint");
        draw.failure=new IllegalStateException();after(TILE+"#draw",draw);
        check(tile.maskColor==0xff112233,"card mask restores after drawing, including failures");
        b.putInt("qs_card_color",0);CloudCheck.config(b);
        out=(BlurMixConfig.BlurMixMultiWithShader)CloudCheck.after(B+"BlurConfig#getPlatformMixConfig",c,original);
        check(out.foregroundShaderParam.topMode==0 && out.backgroundShaderParam.topMode==0 && out.getPlaceHolderColor()==0,"zero strength clears all card mix and placeholder layers");
        BlurConfig ordinary=new BlurConfig();ordinary.platformMixConfig=original;
        check(CloudCheck.after(B+"BlurConfig#getPlatformMixConfig",ordinary,original)==original,"ordinary notification backgrounds retain their native colors");
        b.putInt("qs_card_color",0x80446688);CloudCheck.config(b);
        GradientTileDrawable flat=new GradientTileDrawable();flat.userAlpha=128;
        int[] redraws={0};
        android.graphics.drawable.Drawable.Callback callback=who -> redraws[0]++;
        flat.colorDrawable.setCallback(callback);
        CloudCheck.after(FLAT+"#<init>",flat,null);
        draw=before(FLAT+"#draw",flat,new Canvas());
        check(flat.colorDrawable.getColor()==0x40446688,"non-blur control card tint follows native drawable opacity");
        draw.failure=new IllegalStateException();after(FLAT+"#draw",draw);
        check(flat.colorDrawable.getColor()==0xff998877,"non-blur card restores original fill after drawing failures");
        check(redraws[0]==0 && flat.colorDrawable.getCallback()==callback,
            "temporary fill changes preserve callbacks without scheduling an endless redraw loop");
        java.lang.reflect.Method refresh=ShadeHook.class.getDeclaredMethod("refreshProxies");refresh.setAccessible(true);
        int invalidations=flat.invalidations;refresh.invoke(null);
        check(flat.invalidations>invalidations,"live settings invalidate existing cards for color changes and system restore");
        // Plugin cards share the same native ViewBlurProxy API.
        BlurConfig pluginConfig=new BlurConfig();pluginConfig.platformMixConfig=multi(0xff112233);
        AutoBlurDrawable pluginAuto=new AutoBlurDrawable(new ViewBlurProxy(pluginConfig));
        MixColorPluginDrawable plugin=new MixColorPluginDrawable(pluginAuto);
        CloudCheck.after(MixColorPluginDrawable.class.getName()+"#<init>",plugin,null,pluginAuto,null);
        out=(BlurMixConfig.BlurMixMultiWithShader)CloudCheck.after(B+"BlurConfig#getPlatformMixConfig",pluginConfig,pluginConfig.platformMixConfig);
        check(out.foregroundShaderParam.topLayerColor==0x80446688,"plugin control center cards use the same selected tint");
        // State/theme rebuilds supply a new native multi-layer config.
        BlurMixConfig.BlurMixMultiWithShader rebuilt=multi(0xff000000);c.platformMixConfig=rebuilt;
        out=(BlurMixConfig.BlurMixMultiWithShader)CloudCheck.after(B+"BlurConfig#getPlatformMixConfig",c,rebuilt);
        check(out.foregroundShaderParam.topLayerColor==0x80446688,"new theme and state configs keep the selected card color");
        BlurConfig banner=new BlurConfig();banner.platformMixConfig=original;
        Reflect.setAdditionalInstanceField(banner,"thunder.role","banner");
        out=(BlurMixConfig.BlurMixMultiWithShader)CloudCheck.after(B+"BlurConfig#getPlatformMixConfig",banner,original);
        check(out.foregroundShaderParam.topLayerColor==0x66778899 && out.backgroundShaderParam.topMode==0,
            "multi-layer banner receives its own tint and clears secondary layers");
        b.putInt("banner_color",0);CloudCheck.config(b);
        out=(BlurMixConfig.BlurMixMultiWithShader)CloudCheck.after(B+"BlurConfig#getPlatformMixConfig",banner,original);
        check(out.foregroundShaderParam.topMode==0 && out.backgroundShaderParam.topMode==0 && out.getPlaceHolderColor()==0,
            "transparent banner clears both shaders and placeholder");
        b.putInt("qs_card_color",0x80446688);CloudCheck.config(b);
        BlurMixConfig.BlurMixSingleWithShader empty=new BlurMixConfig.BlurMixSingleWithShader(new MixColorWithShader(0,0,0,0));
        c.platformMixConfig=empty;
        BlurMixConfig.BlurMixSingleWithShader single=(BlurMixConfig.BlurMixSingleWithShader)CloudCheck.after(B+"BlurConfig#getPlatformMixConfig",c,empty);
        check(single.backgroundShaderParam.topMode!=0 && single.backgroundShaderParam.topLayerColor==0x80446688,
            "native empty shader can receive a visible custom card tint");
        c.platformMixConfig=rebuilt;
        b.putBoolean("qs_card_tint",false);CloudCheck.config(b);
        check(CloudCheck.after(B+"BlurConfig#getPlatformMixConfig",c,rebuilt)==rebuilt,"turning card tint off returns original native configuration");
        draw=before(TILE+"#draw",tile,new Canvas());check(tile.maskColor==0xff112233,"system restore retains native card mask");after(TILE+"#draw",draw);
        b.putBoolean("qs_card_tint",true);b.putBoolean("enabled",false);CloudCheck.config(b);
        check(CloudCheck.after(B+"BlurConfig#getPlatformMixConfig",c,rebuilt)==rebuilt,"module off preserves native card shaders");
        draw=before(FLAT+"#draw",flat,new Canvas());check(flat.colorDrawable.getColor()==0xff998877,"module off preserves non-blur card fill");after(FLAT+"#draw",draw);
        System.out.println("Verified "+count+" QS tint checks; phone rendering remains unverified.");
    }
}
