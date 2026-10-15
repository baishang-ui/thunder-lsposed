package io.github.baishangui.thunder;

import android.os.Bundle;
import android.view.View;
import com.oplusos.systemui.common.blurability.*;
import android.graphics.drawable.GradientDrawable;
import com.oplus.systemui.plugins.seedling.capsule.ui.view.CapsuleView;
import com.oplus.systemui.plugins.seedling.card.ui.view.CardBackgroundView;
import com.oplus.view.ViewRootManager;

public final class NotificationStyleCheck {
    static final String B="com.oplusos.systemui.common.blurability.";
    static final String M="com.oplus.systemui.notification.blur.ViewBlurManager#";
    enum CardType { NOTIFICATION,CUSTOM,FULLSCREENBANNER,MENU }
    static int count;
    static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);count++;System.out.println("PASS: "+label);}
    static Object after(String key,Object owner,Object result,Object... args)throws Throwable{return CloudCheck.after(key,owner,result,args);}
    static HookCallback.Param before(String key,Object owner,Object... args)throws Throwable{return QsTintCheck.before(key,owner,args);}
    public static final class Manager {
        public ViewBlurProxy proxy;
        public ViewBlurProxy getBlurProxyForView(View v){return proxy;}
    }
    public static final class Row extends View {
        public View mBackgroundNormal;
        public float getTopRoundness(){return .5f;}
        public float getBottomRoundness(){return 0f;}
    }
    public static void main(String[] args)throws Throwable {
        ShadeHook.install("com.android.systemui","com.android.systemui",NotificationStyleCheck.class.getClassLoader());
        CloudHook.install(NotificationStyleCheck.class.getClassLoader());
        Bundle b=new Bundle();b.putBoolean("enabled",true);
        b.putBoolean("qs_card_tint",true);b.putInt("qs_card_color",0x77665544);
        b.putBoolean("qs_card_corner_enabled",true);b.putInt("qs_card_corner",31);
        b.putBoolean("qs_card_blur",true);b.putInt("qs_card_radius",142);
        b.putBoolean("banner_tint",true);b.putInt("banner_color",0x557799bb);
        b.putBoolean("banner_corner_enabled",true);b.putInt("banner_corner",25);
        CloudCheck.config(b);
        b.putBoolean("notification_card_tint",true);b.putInt("notification_card_color",0x416688aa);
        b.putBoolean("notification_card_blur",true);b.putInt("notification_card_radius",73);
        b.putBoolean("notification_card_corner_enabled",true);b.putInt("notification_card_corner",13);
        CloudCheck.config(b);
        check(ShadeHook.snapshot().qsCards!=ShadeHook.snapshot().notificationCard,
            "control cards and notification rows have independent material snapshots");
        float density=new View().getResources().getDisplayMetrics().density;
        BlurConfig c=new BlurConfig();c.platformMixConfig=QsTintCheck.multi(0x88998877);
        c.motionBlurMixConfig=new BlurMixConfig.BlurMixSingle(new MixColor(4,0x55555555,0x44444444));
        ViewBlurProxy proxy=new ViewBlurProxy(c);Manager manager=new Manager();manager.proxy=proxy;
        View view=new View();
        after(M+"requireBlurProxyForView",manager,proxy,view,CardType.NOTIFICATION,1f,null);
        BlurMixConfig.BlurMixMultiWithShader mix=(BlurMixConfig.BlurMixMultiWithShader)after(B+"BlurConfig#getPlatformMixConfig",c,c.platformMixConfig);
        check(mix.foregroundShaderParam.topLayerColor==0x416688aa&&mix.backgroundShaderParam.topMode==0,
            "notification list uses its independent tint and clears native secondary color layers");
        BlurConfig qs=new BlurConfig();
        qs.platformMixConfig=new BlurMixConfig.BlurMixMultiWithShader(new MixColorWithShader(9,0xff112233,2,0xff445566),
            new MixColorWithShader(3,0xff778899,5,0xff223344));
        qs.motionBlurMixConfig=new BlurMixConfig.BlurMixSingle(new MixColor(7,0xff332211,0xff665544));
        Reflect.setAdditionalInstanceField(qs,"thunder.role","qs-card");
        BlurMixConfig.BlurMixMultiWithShader qsMix=(BlurMixConfig.BlurMixMultiWithShader)after(B+"BlurConfig#getPlatformMixConfig",qs,qs.platformMixConfig);
        check(qsMix.foregroundShaderParam.topMode==mix.foregroundShaderParam.topMode&&qsMix.foregroundShaderParam.topMode==1&&
            qsMix.foregroundShaderParam.topLayerColor==0x77665544&&mix.foregroundShaderParam.topLayerColor==0x416688aa&&
            qsMix.backgroundShaderParam.topMode==0&&qsMix.getPlaceHolderColor()==0x77665544&&mix.getPlaceHolderColor()==0x416688aa,
            "QS and notification shaders use their own colors with the same blending formula");
        BlurMixConfig.BlurMixSingle qsMotion=(BlurMixConfig.BlurMixSingle)after(B+"BlurConfig#getMotionBlurMixConfig",qs,qs.motionBlurMixConfig);
        BlurMixConfig.BlurMixSingle rowMotion=(BlurMixConfig.BlurMixSingle)after(B+"BlurConfig#getMotionBlurMixConfig",c,c.motionBlurMixConfig);
        check(qsMotion.mixColor.mode==1 && rowMotion.mixColor.mode==1 && qsMotion.mixColor.top==0x77665544&&rowMotion.mixColor.top==0x416688aa &&
            qsMotion.mixColor.bottom==0 && rowMotion.mixColor.bottom==0,
            "motion materials use the same native COLORMIX with no secondary color");
        ViewBlurProxy qsProxy=new ViewBlurProxy(qs);
        for(String backend:new String[]{"applyBlurConfig","applyConfigToPlatformBlur","applyConfigToMotionBlur","applyConfigToStaticBlur","applyConfigToBlendWallpaper"}) {
            HookCallback.Param qsFrame=before(B+"ViewBlurProxy#"+backend,qsProxy);
            HookCallback.Param rowFrame=before(B+"ViewBlurProxy#"+backend,proxy);
            check(qs.cornerRadius==31*density&&c.cornerRadius==13*density&&qs.blurRadius==142&&c.blurRadius==73&&
                qs.motionBlurRadius==142&&c.motionBlurRadius==73,"independent blur and corners reach both card surfaces in "+backend);
            QsTintCheck.after(B+"ViewBlurProxy#"+backend,rowFrame);QsTintCheck.after(B+"ViewBlurProxy#"+backend,qsFrame);
        }

        HookCallback.Param frame=before(B+"ViewBlurProxy#applyConfigToPlatformBlur",proxy);
        check(c.cornerRadius==13*density&&c.leftTopCornerRadius==13*density&&c.rightBottomCornerRadius==13*density&&c.blurRadius==73,
            "notification native background receives all four corners and independent blur");
        frame.failure=new IllegalStateException();QsTintCheck.after(B+"ViewBlurProxy#applyConfigToPlatformBlur",frame);
        check(c.cornerRadius==47&&c.leftTopCornerRadius==47&&c.blurRadius==0,
            "notification geometry and blur restore after backend failures");
        c.leftTopCornerRadius=23.5f;c.leftBottomCornerRadius=0f;
        frame=before(B+"ViewBlurProxy#applyConfigToPlatformBlur",proxy);
        check(c.leftTopCornerRadius==13*density*.5f&&c.leftBottomCornerRadius==0,
            "native notification corners preserve grouped edges and partial roundness");
        QsTintCheck.after(B+"ViewBlurProxy#applyConfigToPlatformBlur",frame);
        c.leftTopCornerRadius=47f;c.leftBottomCornerRadius=47f;
        after(M+"requireBlurProxyForView",manager,proxy,view,CardType.CUSTOM,1f,null);
        check("notification-card".equals(Reflect.getAdditionalInstanceField(c,"thunder.role")),"custom notification rows join list styling");
        before(M+"blurForHeadsUp",manager,view,true);
        after(M+"requireBlurProxyForView",manager,proxy,view,CardType.CUSTOM,1f,null);
        check("banner".equals(Reflect.getAdditionalInstanceField(c,"thunder.role")),
            "requesting an existing notification proxy preserves its active heads-up role");
        frame=before(B+"ViewBlurProxy#applyConfigToMotionBlur",proxy);
        check(c.cornerRadius==25*density,"heads-up reuses the view with independent banner corners");
        check(((BlurMixConfig.BlurMixSingle)c.motionBlurMixConfig).mixColor.top==0x557799bb,
            "heads-up motion backend receives the independent banner tint");
        QsTintCheck.after(B+"ViewBlurProxy#applyConfigToMotionBlur",frame);
        before(M+"cancelBlurForHeadsUp",manager,view,true);
        check("notification-card".equals(Reflect.getAdditionalInstanceField(c,"thunder.role")),"canceling heads-up restores the original notification role");
        check(NotificationCardHook.area(view).corner==13,"notification view switches its clipping configuration back after heads-up");
        after(M+"requireBlurProxyForView",manager,proxy,view,CardType.FULLSCREENBANNER,1f,null);
        frame=before(B+"ViewBlurProxy#applyConfigToPlatformBlur",proxy);
        check(c.cornerRadius==25*density,"full-screen banner receives the same independent banner corners");
        QsTintCheck.after(B+"ViewBlurProxy#applyConfigToPlatformBlur",frame);
        after(M+"requireBlurProxyForView",manager,proxy,view,CardType.NOTIFICATION,1f,null);
        Row row=new Row();row.mBackgroundNormal=view;
        check(after("com.android.systemui.statusbar.notification.row.ExpandableNotificationRow#getTopCornerRadius",row,70f).equals(13*density*.5f),
            "row content clipping uses selected radius while preserving animation roundness");
        check(after("com.android.systemui.statusbar.notification.row.ExpandableNotificationRow#getBottomCornerRadius",row,70f).equals(0f),
            "grouped square edges remain square");
        b.putInt("notification_card_color",0);b.putInt("notification_card_corner",0);CloudCheck.config(b);
        mix=(BlurMixConfig.BlurMixMultiWithShader)after(B+"BlurConfig#getPlatformMixConfig",c,c.platformMixConfig);
        qsMix=(BlurMixConfig.BlurMixMultiWithShader)after(B+"BlurConfig#getPlatformMixConfig",qs,qs.platformMixConfig);
        check(qsMix.foregroundShaderParam.topLayerColor==0x77665544&&qsMix.getPlaceHolderColor()==0x77665544,
            "clearing notification tint preserves the existing control-card proxy");
        check(mix.foregroundShaderParam.topMode==0&&mix.backgroundShaderParam.topMode==0&&mix.getPlaceHolderColor()==0,
            "zero notification tint removes shader and placeholder layers");
        frame=before(B+"ViewBlurProxy#applyConfigToPlatformBlur",proxy);
        check(c.cornerRadius==0&&c.leftBottomCornerRadius==0,"zero notification corner reaches every native corner");
        QsTintCheck.after(B+"ViewBlurProxy#applyConfigToPlatformBlur",frame);
        b.putBoolean("notification_card_corner_enabled",false);b.putBoolean("notification_card_tint",false);b.putBoolean("notification_card_blur",false);CloudCheck.config(b);
        frame=before(B+"ViewBlurProxy#applyConfigToPlatformBlur",proxy);
        check(c.cornerRadius==47&&c.platformMixConfig==proxy.config.platformMixConfig,"disabled notification controls preserve native parameters");
        check(ShadeHook.snapshot().qsCards.tint&&ShadeHook.snapshot().qsCards.blur&&ShadeHook.snapshot().qsCards.corner==31,
            "restoring notifications keeps control card overrides enabled");
        QsTintCheck.after(B+"ViewBlurProxy#applyConfigToPlatformBlur",frame);
        b.putBoolean("qs_card_corner_enabled",true);b.putInt("qs_card_corner",16);CloudCheck.config(b);
        com.oplus.systemui.qs.base.res.drawable.GradientTileDrawable flat=new com.oplus.systemui.qs.base.res.drawable.GradientTileDrawable();
        after(flat.getClass().getName()+"#<init>",flat,null);
        Object nativeProvider=flat.pathProvider;
        String providerHook="com.oplus.systemui.qs.base.res.drawable.TileDrawableWrapper#getPathProvider";
        var selected=(com.oplusos.systemui.common.outline.RoundRectOutlineProvider)after(providerHook,flat,nativeProvider);
        check(selected!=nativeProvider&&selected.cornerRadius==16*density,"control-card fallback fill uses the selected native smooth provider");
        b.putInt("qs_card_corner",0);CloudCheck.config(b);
        selected=(com.oplusos.systemui.common.outline.RoundRectOutlineProvider)after(providerHook,flat,nativeProvider);
        check(selected.cornerRadius==0,"zero control-card corner removes fallback rounding");
        b.putBoolean("qs_card_corner_enabled",false);CloudCheck.config(b);
        check(after(providerHook,flat,nativeProvider)==nativeProvider,
            "disabling control-card corners restores the native smooth provider");
        String capsule=CapsuleView.class.getName();
        b.putBoolean("cloud_enabled",true);b.putBoolean("cloud_corner_enabled",true);b.putInt("cloud_corner",7);
        b.putBoolean("cloud_tint",true);b.putInt("cloud_color",0x33112233);
        b.putBoolean("cloud_card_corner_enabled",true);b.putInt("cloud_card_corner",31);
        b.putBoolean("cloud_card_tint",true);b.putInt("cloud_card_color",0x88776655);
        b.putBoolean("cloud_card_blur",true);b.putInt("cloud_card_radius",141);CloudCheck.config(b);
        CapsuleView cap=new CapsuleView();GradientDrawable nativeFill=(GradientDrawable)cap.getBackground();int nativeColor=nativeFill.getColor().color;nativeFill.setCornerRadius(56f);
        after(capsule+"#onAttachedToWindow",cap,null);
        check(nativeFill.getColor().color==0x33112233&&nativeFill.radius==7*density,"capsule tint works even when custom blur is off");
        CardBackgroundView card=new CardBackgroundView();after(CardBackgroundView.class.getName()+"#onAttachedToWindow",card,null);
        check(card.c.corner==31*density&&card.c.radius==141&&card.c.params.top[3]==136/255f,
            "expanded cloud card uses independent corner, tint and blur");
        check(card.c.params.smoothType==1&&card.c.params.smoothWeight==3f,"native cloud material keeps the firmware smooth-corner type and weight");
        String outlineKey="com.oplus.systemui.plugins.common.util.j#getOutline";
        check(Api102Runtime.callbacks.containsKey(outlineKey),"cached parent outlines have a live corner hook");
        kotlin.Triple nativeCorners=new kotlin.Triple(2,117f,3f);
        com.oplus.systemui.plugins.common.util.j provider=new com.oplus.systemui.plugins.common.util.j(new android.graphics.Rect(),nativeCorners);
        android.graphics.Outline outline=new android.graphics.Outline();
        HookCallback.Param outlineCall=before(outlineKey,provider,card,outline);
        provider.getOutline(card,outline);QsTintCheck.after(outlineKey,outlineCall);
        check(outline.radius==31*density&&outline.right==100&&provider.b==nativeCorners,
            "cached parent outline follows card radius, preserves animation bounds and restores its native config");
        b.putInt("cloud_color",0);b.putInt("cloud_corner",0);CloudCheck.config(b);CloudHook.refresh();
        check(nativeFill.getColor().color==0&&nativeFill.radius==0,"live capsule changes clear tint and corners without recreating the view");
        com.oplus.systemui.plugins.seedling.util.d gradient=new com.oplus.systemui.plugins.seedling.util.d(new kotlin.Triple(2,117f,3f));
        HookCallback.Param gradientDraw=CloudCheck.param(gradient,"native draw",new android.graphics.Canvas());
        Api102Runtime.callbacks.get("com.oplus.systemui.plugins.seedling.util.d#draw").before(gradientDraw);
        check(gradientDraw.getResult()==null,"rebuilt gradient shaders stay suppressed during custom mixing");
        String geometryKey="com.oplus.systemui.plugins.seedling.util.c#f";
        HookCallback.Param geometryCall=before(geometryKey,null);
        check(ResourceOverrides.dimension("com.oplus.systemui.plugins:dimen/round_corner_radius_fluid_cloud",ShadeHook.snapshot())==null,
            "native geometry sampling retains original dimensions for later system restore");
        geometryCall.setResult(nativeCorners);QsTintCheck.after(geometryKey,geometryCall);
        provider.b=(kotlin.Triple)geometryCall.getResult();
        check(!CloudHook.readingNativeCorners(),"native geometry sampling scope is released after the callback");
        b.putBoolean("cloud_enabled",false);CloudCheck.config(b);CloudHook.refresh();
        outlineCall=before(outlineKey,provider,card,outline);provider.getOutline(card,outline);QsTintCheck.after(outlineKey,outlineCall);
        check(outline.radius==117f,"cloud off restores the parent outline even when it cached a customized Triple");
        gradientDraw=CloudCheck.param(gradient,"native draw",new android.graphics.Canvas());
        Api102Runtime.callbacks.get("com.oplus.systemui.plugins.seedling.util.d#draw").before(gradientDraw);
        check("native draw".equals(gradientDraw.getResult()),"cloud off allows the native gradient to draw again");
        check(cap.getBackground()==nativeFill&&nativeFill.getColor().color==nativeColor&&nativeFill.radius==56f&&!cap.getClipToOutline(),
            "cloud off restores the capsule native fill and clipping");
        System.out.println("Verified "+count+" functional background checks; device rendering remains unverified.");
    }
}
