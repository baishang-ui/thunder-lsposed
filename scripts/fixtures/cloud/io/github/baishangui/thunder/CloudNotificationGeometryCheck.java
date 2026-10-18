package io.github.baishangui.thunder;

import android.os.Bundle;
import android.view.View;
import com.oplusos.systemui.common.blurability.BlurConfig;
import com.oplusos.systemui.common.blurability.ViewBlurProxy;

/** Notification-hosted cloud content must use the same radius as its outer row. */
public final class CloudNotificationGeometryCheck {
    private static int checks;
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);checks++;}
    public static void main(String[] args)throws Throwable {
        ClassLoader loader=CloudNotificationGeometryCheck.class.getClassLoader();
        ShadeHook.install("com.android.systemui","com.android.systemui",loader);
        CloudHook.install(loader);CloudSdkHook.install(loader);
        Bundle b=new Bundle();b.putBoolean("enabled",true);b.putBoolean("cloud_enabled",false);
        b.putInt("notification_card_radius",100);b.putBoolean("notification_card_corner_enabled",true);
        b.putInt("notification_card_corner",22);b.putInt("cloud_card_color",0);
        b.putBoolean("cloud_card_corner_enabled",true);b.putInt("cloud_card_corner",64);
        CloudCheck.config(b);
        var sdk=new g7.f2();sdk.d="Notification";sdk.n=new com.oplus.ule.lite.components.s1();
        sdk.k.setBackground(new e7.sj());
        check(sdk.s0(true).a()==22,"notification SDK stroke follows notification radius with cloud master off");
        var expanded=Api102Runtime.callbacks.get("g7.f2#w0");
        var p=CloudCheck.param(sdk,null,new Object(),true);expanded.before(p);expanded.after(p);
        float density=sdk.b.getResources().getDisplayMetrics().density;
        for(int radius:new int[]{0,64,12,22}) {
            b.putInt("notification_card_corner",radius);CloudCheck.config(b);CloudSdkHook.refresh();
            check(sdk.outlineRadius==radius&&sdk.shaderRadius==radius*density,"notification content and COE follow live radius");
            check(((e7.sj)sdk.k.getBackground()).radius==radius*density&&sdk.l.shaderRadius==radius*density,"notification stroke and light follow live radius");
        }
        var row=new com.android.systemui.statusbar.notification.row.ExpandableNotificationRow();
        var media=new com.oplus.systemui.plugins.shared.template.component.media.view.MediaBackGroundView();media.parent=row;
        var stroke=Api102Runtime.callbacks.get("com.oplus.systemui.plugins.shared.template.component.view.CustomLottieView#i");
        p=CloudCheck.param(media,null,new android.graphics.Canvas());stroke.before(p);
        check(media.t==22*density,"notification template stroke follows enclosing row");
        p.failure=new RuntimeException();stroke.after(p);check(media.t==117,"template baseline restores after failure");
        var light=new com.oplus.systemui.plugins.shared.template.component.light.f();light.setCallback(media);
        var draw=Api102Runtime.callbacks.get("com.oplus.systemui.plugins.shared.template.component.light.f#draw");
        p=CloudCheck.param(light,null,new android.graphics.Canvas());draw.before(p);
        check(light.e.d[6]==22*density&&light.e.d[9]==22*density,"notification template light matches stroke");draw.after(p);
        media.parent=null;p=CloudCheck.param(media,null,new android.graphics.Canvas());stroke.before(p);
        check(media.t==117,"unowned media remains native");stroke.after(p);
        b.putBoolean("cloud_enabled",true);b.putInt("cloud_lock_style",2);b.putBoolean("cloud_lock_tint",true);
        b.putInt("cloud_lock_color",0x3c778899);CloudCheck.config(b);
        BlurConfig blur=new BlurConfig();var manager=new CloudLockCheck.Manager();
        CloudLockHook.track(row.mBackgroundNormal,new ViewBlurProxy(blur),"CUSTOM",manager);
        var area=CloudLockHook.area(blur,ShadeHook.snapshot());
        check(area.cornerEnabled&&area.corner==22&&area.color==0x3c778899,"lock material preserves notification geometry");
        manager.keyguard=false;check(CloudLockHook.area(blur,ShadeHook.snapshot())==null,"unlock returns to notification material");
        sdk.d="LockScreen";check(sdk.s0(true).a()==64,"independent lock card retains cloud radius");
        sdk.d="Notification";check(sdk.s0(true).a()==22,"notification card stays independent of expanded cloud radius");
        b.putBoolean("notification_card_corner_enabled",false);CloudCheck.config(b);CloudSdkHook.refresh();
        check(sdk.outlineRadius==20&&((e7.sj)sdk.k.getBackground()).radius==20*density&&sdk.l.shaderRadius==20*density,"turning off notification corners restores all SDK layers");
        b.putBoolean("notification_card_corner_enabled",true);b.putBoolean("enabled",false);CloudCheck.config(b);
        check(sdk.s0(true).a()==20,"module off preserves native SDK style");
        System.out.println("Verified "+checks+" notification cloud geometry checks.");
    }
}
