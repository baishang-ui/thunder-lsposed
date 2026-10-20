package io.github.baishangui.thunder;

import android.os.Bundle;
import android.view.View;
import com.oplusos.systemui.common.blurability.*;
import com.oplus.posteffect.params.*;

/** Expanded flow cards must never reshape the native lock-screen mini capsule. */
public final class CloudCapsuleIsolationCheck {
    static int checks;
    static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);checks++;}
    static Object after(String key,Object owner,Object result,Object... args)throws Throwable {
        return QsGeometryCheck.afterIfPresent(key,owner,result,args);
    }
    public static void main(String[] args)throws Throwable {
        ClassLoader loader=CloudCapsuleIsolationCheck.class.getClassLoader();
        ShadeHook.install("com.android.systemui","com.android.systemui",loader);CloudHook.install(loader);CloudSdkHook.install(loader);
        Bundle b=new Bundle();b.putBoolean("enabled",true);b.putBoolean("cloud_enabled",true);
        b.putInt("cloud_card_color",0);b.putBoolean("cloud_card_corner_enabled",true);
        b.putBoolean("cloud_lock_corner_enabled",true);b.putInt("cloud_lock_corner",0);
        b.putInt("cloud_lock_style",2);b.putBoolean("cloud_lock_tint",true);b.putInt("cloud_lock_color",0x3c778899);
        var item=new com.oplus.systemui.notification.lockscreen.capsule.CapsuleItemView();
        item.entry=new com.oplus.systemui.notification.lockscreen.seedling.CapsuleSeedingData();
        var blur=new BlurConfig();var nativeShape=new Object();blur.pathProvider=nativeShape;
        blur.gradientStrokeCornerParam=new CornerParams(CornerType.ROUND,47,1.1f);blur.radiusWeight=1.1f;
        var proxy=new ViewBlurProxy(blur);CloudLockHook.track(item,proxy,"CAPSULE",null);
        var media=new com.oplus.systemui.plugins.shared.template.component.media.view.MediaBackGroundView();media.parent=item;
        var sdk=new g7.f2();sdk.d="LockScreen";sdk.n=new com.oplus.ule.lite.components.s1();sdk.b.parent=item;
        var nativeStyle=new f8.j5(1,20,1.1f);
        String capsule=item.getClass().getName()+"#",backend="com.oplusos.systemui.common.blurability.ViewBlurProxy#applyConfigToPlatformBlur";
        for(int dp:new int[]{0,8,13,64}) {
            b.putInt("cloud_card_corner",dp);CloudCheck.config(b);
            for(int height:new int[]{200,120,80,40,28}) {
                item.location.clip=new android.graphics.Rect(0,0,400,height);
                float nativeRadius=height/2f;
                check(after(capsule+"getActualRadius",item,nativeRadius).equals(nativeRadius),"mini capsule retains native radius at height="+height+" card="+dp);
                check(after(capsule+"getMaxRadius",item,147f).equals(147f),"card radius cannot change native capsule roundness maximum");
                var p=QsTintCheck.before(backend,proxy);
                check(blur.cornerRadius==47&&blur.radiusWeight==1.1f&&blur.pathProvider==nativeShape,"native capsule material geometry is unchanged throughout collapse");
                check(CloudLockHook.area(blur,ShadeHook.snapshot()).tint,"capsule material settings remain active independently of expanded-card geometry");QsTintCheck.after(backend,p);
            }
            var p=QsTintCheck.before("com.oplus.systemui.plugins.shared.template.component.view.CustomLottieView#i",media,new android.graphics.Canvas());
            check(media.t==117,"mini capsule media content keeps native corners");QsTintCheck.after("com.oplus.systemui.plugins.shared.template.component.view.CustomLottieView#i",p);
            check(after("g7.f2#s0",sdk,nativeStyle,true)==nativeStyle,"SDK content reparented into mini capsule keeps native shape");
            sdk.b.parent=null;
            check(((f8.j5)after("g7.f2#s0",sdk,nativeStyle,true)).a()==dp,"independent expanded lock-screen SDK card still follows live radius");sdk.b.parent=item;
        }
        // An SDK card already customized while expanded must restore its caches
        // when the same content is reparented into the native mini capsule.
        sdk.b.parent=null;
        var expanded=Api102Runtime.callbacks.get("g7.f2#w0");
        var frame=CloudCheck.param(sdk,null,new Object(),true);expanded.before(frame);expanded.after(frame);
        CloudSdkHook.refresh();check(sdk.outlineRadius==64,"expanded SDK cache uses custom radius before reparenting");
        sdk.b.parent=item;
        frame=CloudCheck.param(sdk,null,new Object(),true);expanded.before(frame);expanded.after(frame);
        check(sdk.outlineRadius==20&&sdk.l.shaderRadius==20*sdk.b.getResources().getDisplayMetrics().density,
            "reparenting into mini capsule restores existing SDK outline and light caches");
        var edge=new com.oplus.systemui.notification.lockscreen.capsule.stroke.StrokeView();edge.parent=item;
        var layer=new com.oplus.systemui.notification.lockscreen.capsule.stroke.StrokeLayer();edge.layers.add(layer);edge.updateViewSize(400,40);
        var p=NotificationExpansionCheck.before(edge.getClass().getName()+"#onDraw",edge,new android.graphics.Canvas());edge.onDraw(new android.graphics.Canvas());NotificationExpansionCheck.after(edge.getClass().getName()+"#onDraw",p);
        check(layer.drawnRadius==20&&layer.drawnWeight==1,"collapsed capsule retains its native rounded ends and stroke curve");
        var card=new com.oplus.systemui.plugins.seedling.card.ui.view.CardView();card.parent=item;
        View root=new View();root.parent=card;
        var foreground=new android.graphics.drawable.GradientDrawable();foreground.setCornerRadius(117);root.foreground=foreground;
        after("com.oplus.systemui.plugins.shared.template.page.entity.t#<init>",new com.oplus.systemui.plugins.shared.template.page.entity.t(root),null);
        check(foreground.getCornerRadius()==117,"a shared card class inside the capsule cannot broaden the customization scope");
        var row=new NotificationExpansionCheck.Row();var background=row.mBackgroundNormal;background.parent=row;
        var rowBlur=new BlurConfig();CloudLockHook.track(background,new ViewBlurProxy(rowBlur),"CUSTOM",new CloudLockCheck.Manager());
        media.parent=row;
        p=QsTintCheck.before("com.oplus.systemui.plugins.shared.template.component.view.CustomLottieView#i",media,new android.graphics.Canvas());
        check(media.t==64*media.getResources().getDisplayMetrics().density,"expanded lock-screen row retains custom card radius");QsTintCheck.after("com.oplus.systemui.plugins.shared.template.component.view.CustomLottieView#i",p);
        View reusedRoot=new View();reusedRoot.parent=row;
        var reusedForeground=new android.graphics.drawable.GradientDrawable();reusedForeground.setCornerRadius(117);reusedRoot.foreground=reusedForeground;
        after("com.oplus.systemui.plugins.shared.template.page.entity.t#<init>",new com.oplus.systemui.plugins.shared.template.page.entity.t(reusedRoot),null);
        check(reusedForeground.getCornerRadius()==64*reusedRoot.getResources().getDisplayMetrics().density,"expanded foreground is customized before collapse");
        reusedRoot.setOutlineProvider(new com.oplus.systemui.plugins.common.util.j(null,new kotlin.Triple(2,117f,3f)));
        reusedRoot.parent=item;
        p=QsTintCheck.before("com.oplus.systemui.plugins.common.util.j#getOutline",reusedRoot.getOutlineProvider(),reusedRoot,new android.graphics.Outline());
        check(reusedForeground.getCornerRadius()==117,"first mini-card outline restores a reused foreground without a settings change or restart");QsTintCheck.after("com.oplus.systemui.plugins.common.util.j#getOutline",p);
        var factory=new com.heytap.log.nx.obus.a();View nativeBackground=new View();nativeBackground.parent=item;
        String create="com.heytap.log.nx.obus.a#b";p=QsTintCheck.before(create,factory,nativeBackground,null);
        var baseline=new kotlin.Triple(1,117f,1.1f);
        var producer=Api102Runtime.callbacks.get("com.oplus.systemui.plugins.seedling.util.c#f");
        var read=CloudCheck.param(null,baseline);producer.before(read);producer.after(read);
        check(read.getResult()==baseline,"mini-card native background creation cannot inherit the expanded-card geometry producer");
        factory.b(nativeBackground,null);QsTintCheck.after(create,p);
        check(!CloudHook.readingNativeCorners(),"native mini-card background scope is released after creation");
        System.out.println("Verified "+checks+" expanded-card / native-capsule isolation checks.");
    }
}
