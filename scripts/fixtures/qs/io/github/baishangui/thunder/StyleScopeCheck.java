package io.github.baishangui.thunder;

import android.os.Bundle;
import android.view.View;
import com.oplus.systemui.qs.base.res.drawable.*;
import com.oplusos.systemui.common.blurability.*;
import com.oplusos.systemui.common.blurability.drawable.AutoBlurDrawable;
import com.oplusos.systemui.common.outline.RoundRectOutlineProvider;

/** Exercise scoped configuration through the real material/geometry hook callbacks. */
public final class StyleScopeCheck {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);System.out.println("PASS: "+message);}
    static Bundle settings() {
        Bundle b=new Bundle();b.putBoolean("enabled",true);
        for(String group:HookConfig.STYLE_KEYS)b.putBoolean("style_"+group,false);
        b.putBoolean("style_qs_small",true);b.putBoolean("style_qs_wide",true);
        for(String key:new String[]{"qs_small","qs_wide"}) {
            b.putBoolean(key+"_tint",true);b.putBoolean(key+"_blur",true);b.putBoolean(key+"_corner_enabled",true);
        }
        b.putInt("qs_small_radius",51);b.putInt("qs_small_corner",7);b.putInt("qs_small_color",0x55667788);
        b.putInt("qs_wide_radius",83);b.putInt("qs_wide_corner",17);b.putInt("qs_wide_color",0x99112233);
        return b;
    }
    static MixColorTileDrawable tile(String key,BlurConfig config)throws Throwable {
        MixColorTileDrawable d=new MixColorTileDrawable(new AutoBlurDrawable(new ViewBlurProxy(config)));
        CloudCheck.after(QsTintCheck.TILE+"#<init>",d,null,d.autoBlurDrawable,null);
        QsCardHook.markStyle(d,key);return d;
    }
    public static void main(String[] args)throws Throwable {
        ShadeHook.install("com.android.systemui","com.android.systemui",StyleScopeCheck.class.getClassLoader());
        Bundle b=settings();CloudCheck.config(b);
        BlurConfig small=new BlurConfig(),wide=new BlurConfig();
        small.platformMixConfig=QsTintCheck.multi(0xff123456);wide.platformMixConfig=QsTintCheck.multi(0xff123456);
        MixColorTileDrawable s=tile("qs_small",small),w=tile("qs_wide",wide);
        // Mark card surfaces so their semantic state is owned by the card rather than its icon.
        QsCornerHook.trackCard(s);QsCornerHook.trackCard(w);
        check(QsCardHook.area(s).color==0x55667788&&QsCardHook.area(w).color==0x99112233,"tile types select independent saved colors");
        check(QsCardHook.area(small).radius==51&&QsCardHook.area(wide).radius==83,"blur configs retain the owning tile type");
        var sr=QsGeometryCheck.provider(QsGeometryCheck.WRAP,s);
        var wr=QsGeometryCheck.provider(QsGeometryCheck.WRAP,w);
        float density=android.content.res.Resources.getSystem().getDisplayMetrics().density;
        check(sr.cornerRadius==7*density&&wr.cornerRadius==17*density,"native geometry uses each tile type's radius");
        b.putBoolean("style_qs_small",false);CloudCheck.config(b);QsCardHook.refresh();
        check(!QsCardHook.area(small).blur&&!QsCardHook.area(small).tint&&!QsCardHook.area(small).cornerEnabled,"disabled small tiles restore all native material flags");
        check(QsCardHook.area(wide).blur&&QsCardHook.area(wide).tint&&wr.cornerRadius==17*density,"disabling small tiles leaves wide tiles customized");
        check(QsGeometryCheck.provider(QsGeometryCheck.WRAP,s)==s.pathProvider,"disabled tile returns its original outline provider");
        b.putBoolean("style_qs_music",true);b.putBoolean("qs_music_corner_enabled",true);b.putInt("qs_music_corner",12);b.putInt("qs_music_radius",54);
        b.putBoolean("style_qs_slider",true);b.putBoolean("qs_slider_corner_enabled",true);b.putInt("qs_slider_corner",9);b.putInt("qs_slider_radius",67);
        b.putBoolean("qs_slider_tint",true);b.putInt("qs_slider_color",0x44556677);CloudCheck.config(b);
        var music=new com.oplus.systemui.qs.media.OplusQsBaseMediaPanelView();
        CloudCheck.after(music.getClass().getName()+"#onFinishInflate",music,null);
        var musicShape=QsGeometryCheck.provider(QsGeometryCheck.LAYER,music.transitionDrawable);
        check(musicShape.cornerRadius==12*density&&QsCardHook.area(music.bgDrawable).radius==54,"music tile owns a material and radius separate from wide tiles");
        var slider=new com.oplus.systemui.qs.base.seek.OplusQsVerticalSeekBar();
        check(QsCardHook.area(slider).corner==9&&QsCardHook.area(slider).color==0x44556677,"brightness and volume tracks select the slider style");
        check(!ShadeHook.snapshot().qsProgress.tint&&!ShadeHook.snapshot().qsProgress.blur,"slider progress retains native contrasting material");
        b.putBoolean("style_qs_music",false);CloudCheck.config(b);QsCardHook.refresh();
        check(!QsCardHook.area(music.transitionDrawable).cornerEnabled&&QsCardHook.area(slider).cornerEnabled,
            "turning off music tiles leaves slider customization enabled");
        b.putBoolean("style_cloud",false);b.putBoolean("style_cloud_lock",true);b.putBoolean("style_cloud_notification",false);
        b.putBoolean("cloud_lock_blur",true);b.putInt("cloud_lock_radius",63);b.putInt("cloud_lock_style",2);
        b.putBoolean("cloud_card_corner_enabled",true);b.putInt("cloud_card_corner",19);
        b.putBoolean("notification_card_tint",true);b.putBoolean("cloud_notification_tint",true);b.putInt("cloud_notification_radius",81);
        CloudCheck.config(b);HookConfig c=ShadeHook.snapshot();
        check(c.cloud&&!c.cloudArea.blur&&!c.cloudCardArea.cornerEnabled&&c.lockCardArea().cornerEnabled,"lock style can remain enabled while base flow-cloud style is off");
        BlurConfig cloud=new BlurConfig();Reflect.setAdditionalInstanceField(cloud,"thunder.cloud-type","CUSTOM");
        CloudLockCheck.Manager manager=new CloudLockCheck.Manager();
        Reflect.setAdditionalInstanceField(cloud,"thunder.cloud-manager",new java.lang.ref.WeakReference<>(manager));
        check(CloudLockHook.area(cloud,c).blur&&CloudLockHook.area(cloud,c).radius==63,"lockscreen cloud selects its own material");
        manager.keyguard=false;
        check(!CloudLockHook.area(cloud,c).tint&&!CloudLockHook.area(cloud,c).cornerEnabled,"notification-hosted cloud restores native style independently of ordinary notifications");
        b.putBoolean("style_cloud_notification",true);CloudCheck.config(b);
        check(CloudLockHook.area(cloud,ShadeHook.snapshot()).tint,"notification cloud resumes its saved tint when enabled");
        b.putBoolean("style_cloud",true);b.putBoolean("style_cloud_lock",false);b.putBoolean("style_cloud_notification",false);
        b.putBoolean("cloud_app_color_strength_enabled",true);b.putInt("cloud_app_color_strength",50);CloudCheck.config(b);
        c=ShadeHook.snapshot();
        check(c.appColorStrength("cloud")==50&&c.appColorStrength("cloud_lock")==100&&c.appColorStrength("cloud_notification")==100,
            "application tint returns to native strength for disabled cloud hosts");
        check(ResourceOverrides.color("com.oplus.systemui.plugins:color/coui_color_primary_text_blue_dark",c)==null,
            "shared resource colors cannot leak a custom style into disabled hosts");
        System.out.println("Verified scoped style material and geometry restoration.");
    }
}
