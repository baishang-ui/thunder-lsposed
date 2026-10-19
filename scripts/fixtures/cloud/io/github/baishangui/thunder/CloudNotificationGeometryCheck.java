package io.github.baishangui.thunder;

import android.os.Bundle;
import android.view.View;
import com.oplusos.systemui.common.blurability.BlurConfig;
import com.oplusos.systemui.common.blurability.ViewBlurProxy;

/** Flow cards keep their own geometry in the shade, on keyguard and in SDK layers. */
public final class CloudNotificationGeometryCheck {
    private static int checks;
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);checks++;}
    public static void main(String[] args)throws Throwable {
        ClassLoader loader=CloudNotificationGeometryCheck.class.getClassLoader();
        ShadeHook.install("com.android.systemui","com.android.systemui",loader);
        CloudHook.install(loader);CloudSdkHook.install(loader);
        Bundle b=new Bundle();b.putBoolean("enabled",true);b.putBoolean("cloud_enabled",false);
        b.putInt("notification_card_radius",100);b.putBoolean("notification_card_corner_enabled",true);b.putInt("notification_card_corner",22);
        b.putInt("cloud_card_color",0);b.putBoolean("cloud_card_corner_enabled",true);b.putInt("cloud_card_corner",64);
        CloudCheck.config(b);
        var sdk=new g7.f2();sdk.d="Notification";sdk.n=new com.oplus.ule.lite.components.s1();
        sdk.k.setBackground(new e7.sj());
        check(sdk.s0(true).a()==20,"cloud master off preserves native SDK corners despite notification override");
        var row=new NotificationExpansionCheck.Row();row.mBackgroundNormal.parent=row;
        BlurConfig blur=new BlurConfig();var manager=new CloudLockCheck.Manager();manager.keyguard=false;
        var proxy=new ViewBlurProxy(blur);
        CloudLockHook.track(row.mBackgroundNormal,proxy,"CUSTOM",manager);
        NotificationCardHook.trackBlur(row.mBackgroundNormal,blur);
        Reflect.setAdditionalInstanceField(blur,"thunder.role","notification-card");
        check(!NotificationCardHook.area(row.mBackgroundNormal).cornerEnabled,"cloud master off excludes flow row from notification corners");
        b.putBoolean("cloud_enabled",true);CloudCheck.config(b);
        var expanded=Api102Runtime.callbacks.get("g7.f2#w0");
        var p=CloudCheck.param(sdk,null,new Object(),true);expanded.before(p);expanded.after(p);
        float density=sdk.b.getResources().getDisplayMetrics().density;
        var media=new com.oplus.systemui.plugins.shared.template.component.media.view.MediaBackGroundView();media.parent=row;
        var stroke=Api102Runtime.callbacks.get("com.oplus.systemui.plugins.shared.template.component.view.CustomLottieView#i");
        var light=new com.oplus.systemui.plugins.shared.template.component.light.f();light.setCallback(media);
        var draw=Api102Runtime.callbacks.get("com.oplus.systemui.plugins.shared.template.component.light.f#draw");
        String backend="com.oplusos.systemui.common.blurability.ViewBlurProxy#applyConfigToPlatformBlur";
        var ordinary=new View();
        for(int radius:new int[]{0,64,12,22}) {
            b.putInt("cloud_card_corner",radius);b.putInt("notification_card_corner",radius==22?3:22);
            CloudCheck.config(b);CloudSdkHook.refresh();
            check(sdk.outlineRadius==radius&&sdk.shaderRadius==radius*density,"SDK outline and COE use live cloud radius");
            check(((e7.sj)sdk.k.getBackground()).radius==radius*density&&sdk.l.shaderRadius==radius*density,"SDK stroke and light use live cloud radius");
            check(NotificationCardHook.area(ordinary).corner==b.getInt("notification_card_corner"),"ordinary notification keeps notification settings");
            p=QsTintCheck.before(backend,proxy);
            check(blur.cornerRadius==radius*density&&blur.leftTopCornerRadius==radius*density&&blur.rightBottomCornerRadius==radius*density,
                "CUSTOM background uses cloud radius on every edge");
            p.failure=new RuntimeException();QsTintCheck.after(backend,p);
            check(blur.cornerRadius==47,"native background restores after failure");
            p=CloudCheck.param(media,null,new android.graphics.Canvas());stroke.before(p);
            check(media.t==radius*density,"notification-hosted template stroke uses cloud radius");
            p.failure=new RuntimeException();stroke.after(p);check(media.t==117,"template baseline restores after failure");
            p=CloudCheck.param(light,null,new android.graphics.Canvas());draw.before(p);
            check(light.e.d[6]==radius*density&&light.e.d[9]==radius*density,"template light matches cloud stroke");draw.after(p);
        }
        b.putInt("notification_card_corner",1);CloudCheck.config(b);CloudSdkHook.refresh();
        check(sdk.outlineRadius==22&&NotificationCardHook.area(row.mBackgroundNormal).corner==22,"notification radius changes cannot change flow geometry");
        // Start from unmodified native row values, as happens when getters were inlined.
        var background=new com.android.systemui.statusbar.notification.row.NotificationBackgroundView();
        background.parent=row;row.mBackgroundNormal=background;
        CloudLockHook.track(background,proxy,"CUSTOM",manager);NotificationCardHook.trackBlur(background,blur);
        var receive=Api102Runtime.callbacks.get("com.android.systemui.statusbar.notification.row.NotificationBackgroundView#setRadius");
        if(receive==null)receive=new HookCallback(){};
        for(int radius:new int[]{0,12,64})for(float progress:new float[]{0,.5f,1}) {
            b.putInt("cloud_card_corner",radius);CloudCheck.config(b);row.top=progress;row.bottom=1-progress;
            p=CloudCheck.param(background,null,47*row.top,47*row.bottom,true);receive.before(p);
            background.setRadius((Float)p.args[0],(Float)p.args[1],(Boolean)p.args[2]);receive.after(p);
            check(background.strokeRadius==radius*density*progress&&background.shadowRadius==background.strokeRadius,
                "direct stroke and shadow updates use cloud corners despite bypassing blur hooks");
            check(background.bottom==radius*density*(1-progress)&&background.focusRadius==Math.max(0,background.top-1),
                "focus overlay and grouped animation share the receiving cloud geometry");
        }
        int rowRefreshes=row.refreshed;NotificationCardHook.refresh();
        check(row.refreshed>rowRefreshes,"direct receiver registers live corner refresh even when getters were inlined");
        b.putInt("cloud_card_corner",22);CloudCheck.config(b);row.top=row.bottom=1;
        b.putInt("cloud_lock_style",2);b.putBoolean("cloud_lock_tint",true);b.putInt("cloud_lock_color",0x3c778899);
        CloudCheck.config(b);manager.keyguard=true;
        var area=CloudLockHook.area(blur,ShadeHook.snapshot());
        check(area.cornerEnabled&&area.corner==22&&area.color==0x3c778899,"lock material combines with cloud geometry");
        manager.keyguard=false;area=CloudLockHook.area(blur,ShadeHook.snapshot());
        check(area.corner==22&&area.color==ShadeHook.snapshot().notificationCard.color,"unlock retains cloud corners and restores shade material");
        sdk.d="LockScreen";check(sdk.s0(true).a()==22,"independent lock card uses same cloud radius");
        sdk.d="Notification";
        var item=new com.oplus.systemui.notification.lockscreen.capsule.CapsuleItemView();
        item.entry=new com.oplus.systemui.notification.lockscreen.seedling.CapsuleSeedingData();
        var edge=new com.oplus.systemui.notification.lockscreen.capsule.stroke.StrokeView();edge.parent=item;
        var edgeLayer=new com.oplus.systemui.notification.lockscreen.capsule.stroke.StrokeLayer();edge.layers.add(edgeLayer);
        String edgeKey="com.oplus.systemui.notification.lockscreen.capsule.stroke.StrokeView#onDraw";
        var edgeDraw=Api102Runtime.callbacks.get(edgeKey);
        if(edgeDraw==null)edgeDraw=new HookCallback(){};
        String capsule="com.oplus.systemui.notification.lockscreen.capsule.CapsuleItemView#";
        for(int radius:new int[]{0,12,64}) {
            b.putInt("cloud_card_corner",radius);CloudCheck.config(b);
            float expected=Math.min(radius*density,100);
            check(((Number)CloudCheck.after(capsule+"getActualRadius",item,47f)).floatValue()==expected,
                "lock clipping and stroke receive cloud radius, including zero");
            check(((Number)CloudCheck.after(capsule+"getMaxRadius",item,47f)).floatValue()==expected,
                "lock roundness uses the same bounded cloud radius");
            edge.updateViewSize(400,200);
            p=CloudCheck.param(edge,null,new android.graphics.Canvas());edgeDraw.before(p);edge.onDraw((android.graphics.Canvas)p.args[0]);
            check(edgeLayer.drawnRadius==expected,"independent animated edge follows cloud radius after native size reset");
            p.failure=new RuntimeException();edgeDraw.after(p);
            check(edgeLayer.getCornerRadius()==100,"animated edge restores native cache even after draw failure");
        }
        edge.updateViewSize(400,40);p=CloudCheck.param(edge,null,new android.graphics.Canvas());edgeDraw.before(p);edge.onDraw((android.graphics.Canvas)p.args[0]);
        check(edgeLayer.drawnRadius==20,"animated edge clamps to its own current height during collapse");edgeDraw.after(p);
        int refreshes=item.roundnessRefreshes;CloudLockHook.refresh();
        check(item.roundnessRefreshes>refreshes,"live cloud changes rebuild cached lock clipping and stroke");
        item.entry=new Object();
        p=CloudCheck.param(edge,null,new android.graphics.Canvas());edgeDraw.before(p);edge.onDraw((android.graphics.Canvas)p.args[0]);edgeDraw.after(p);
        check(edgeLayer.drawnRadius==20,"reused ordinary lock capsule retains native animated edge");
        check(((Number)CloudCheck.after(capsule+"getActualRadius",item,47f)).floatValue()==47,
            "ordinary lock capsules retain native geometry");
        b.putBoolean("cloud_card_corner_enabled",false);CloudCheck.config(b);CloudSdkHook.refresh();
        p=CloudCheck.param(background,null,47f,31f,true);receive.before(p);
        check(p.args[0].equals(47f)&&p.args[1].equals(31f),"disabled cloud corner receiver preserves native values");receive.after(p);
        var ordinaryBackground=new com.android.systemui.statusbar.notification.row.NotificationBackgroundView();ordinaryBackground.parent=row;
        b.putBoolean("cloud_card_corner_enabled",true);CloudCheck.config(b);
        p=CloudCheck.param(ordinaryBackground,null,47f,31f,true);receive.before(p);
        check(p.args[0].equals(47f)&&p.args[1].equals(31f),"ordinary notifications retain their incoming radius at the cloud receiver");receive.after(p);
        b.putBoolean("cloud_card_corner_enabled",false);CloudCheck.config(b);
        item.entry=new com.oplus.systemui.notification.lockscreen.seedling.CapsuleSeedingData();
        p=CloudCheck.param(edge,null,new android.graphics.Canvas());edgeDraw.before(p);edge.onDraw((android.graphics.Canvas)p.args[0]);edgeDraw.after(p);
        check(edgeLayer.drawnRadius==20,"disabling cloud corners leaves animated edge native");
        int edgeInvalidations=edge.invalidations;CloudLockHook.refresh();
        check(edge.invalidations>edgeInvalidations,"live changes invalidate the separate animated edge view");
        check(sdk.outlineRadius==20&&((e7.sj)sdk.k.getBackground()).radius==20*density&&sdk.l.shaderRadius==20*density,"disabling cloud corners restores all SDK layers");
        check(!NotificationCardHook.area(row.mBackgroundNormal).cornerEnabled,"disabling cloud corners never falls back to notification override");
        p=CloudCheck.param(media,null,new android.graphics.Canvas());stroke.before(p);
        check(media.t==117,"disabled cloud template keeps native radius");stroke.after(p);
        media.parent=null;p=CloudCheck.param(media,null,new android.graphics.Canvas());stroke.before(p);
        check(media.t==117,"unowned media remains native");stroke.after(p);
        b.putBoolean("cloud_card_corner_enabled",true);b.putBoolean("enabled",false);CloudCheck.config(b);
        check(sdk.s0(true).a()==20,"module off preserves native SDK style");
        System.out.println("Verified "+checks+" independent notification cloud geometry checks.");
    }
}
