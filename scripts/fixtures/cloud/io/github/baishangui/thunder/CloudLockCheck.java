package io.github.baishangui.thunder;

import android.os.Bundle;
import android.view.View;
import android.graphics.drawable.GradientDrawable;
import com.oplus.posteffect.drawable.MetaBallBlurDrawable;
import com.oplus.systemui.notification.lockscreen.capsule.CapsuleItemView;
import com.oplus.systemui.notification.lockscreen.seedling.CapsuleSeedingData;
import com.oplusos.systemui.common.blurability.BlurConfig;
import com.oplusos.systemui.common.blurability.ViewBlurProxy;
import com.oplusos.systemui.common.blurability.BlurMixConfig;
import java.util.Arrays;

public class CloudLockCheck {
    private static final String PRODUCER="com.oplus.systemui.notification.row.NotificationBackgroundCustomRowHandler#updateDrawableMetaColor";
    private static final String META="com.oplus.posteffect.drawable.MetaBallBlurDrawable#";
    private static int checks;
    private static void check(boolean value,String message) { if(!value)throw new AssertionError(message);checks++; }
    public static class Manager {
        public boolean keyguard=true;
        public boolean isKeyguardMaterialColorState() { return keyguard; }
    }
    public static class SharedProxy extends ViewBlurProxy {
        int applies;
        HookConfig.Area material;
        SharedProxy(BlurConfig config) { super(config); }
        @Override public void applyBlurConfig() {
            applies++;
            material=CloudLockHook.area(getBlurConfig(),ShadeHook.snapshot());
        }
    }
    private static Bundle settings() {
        Bundle b=new Bundle();b.putBoolean("enabled",true);b.putBoolean("cloud_enabled",true);return b;
    }
    private static HookCallback.Param scope(Object target)throws Throwable {
        var p=CloudCheck.param(new Object(),null,new android.content.Context(),new Object(),target);
        Api102Runtime.callbacks.get(PRODUCER).before(p);return p;
    }
    private static void end(HookCallback.Param p)throws Throwable { Api102Runtime.callbacks.get(PRODUCER).after(p); }
    private static void solid(MetaBallBlurDrawable drawable,int value,boolean fail)throws Throwable {
        var p=CloudCheck.param(drawable,null,value);var hook=Api102Runtime.callbacks.get(META+"setMetaColor");
        hook.before(p);
        if(fail)p.failure=new IllegalStateException("native failure");else {
            drawable.color=(Integer)p.args[0];
            gradient(drawable,new int[]{drawable.color});
        }
        hook.after(p);
    }
    private static void gradient(MetaBallBlurDrawable drawable,int[] values)throws Throwable {
        var p=CloudCheck.param(drawable,null,GradientDrawable.Orientation.BL_TR,values);
        var hook=Api102Runtime.callbacks.get(META+"setGradientColors");hook.before(p);
        drawable.setGradientColors((GradientDrawable.Orientation)p.args[0],(int[])p.args[1]);hook.after(p);
    }
    public static void main(String[] args)throws Throwable {
        var loader=CloudLockCheck.class.getClassLoader();ShadeHook.install("com.android.systemui","com.android.systemui",loader);
        Bundle b=settings();CloudCheck.config(b);
        int[] original={0x806688aa,0x00446688,0xff123456};
        check(Arrays.equals(original,CloudLockHook.adjust(original,ShadeHook.snapshot())),"default settings preserve native mask");
        b.putBoolean("cloud_app_color_strength_enabled",true);b.putInt("cloud_app_color_strength",50);CloudCheck.config(b);
        MetaBallBlurDrawable app=new MetaBallBlurDrawable();var producer=scope(app);solid(app,original[0],false);end(producer);
        check(app.color==0x406688aa,"application solid alpha changes without changing RGB");
        check(app.colors[0]==0x406688aa,"nested gradient setter does not scale a solid mask twice");
        CloudLockHook.refresh();check(app.color==0x406688aa,"live refresh retains the original single-color command");
        var other=new MetaBallBlurDrawable();producer=scope(app);solid(other,0xaa123456,false);end(producer);
        check(other.color==0xaa123456,"unrelated drawables inside producer retain native color");
        producer=scope(app);gradient(app,original);end(producer);
        check(Arrays.equals(app.colors,new int[]{0x406688aa,0x00446688,0x80123456}),"every native gradient stop scales once");
        check(original[0]==0x806688aa&&app.orientation==GradientDrawable.Orientation.BL_TR,"gradient input and direction remain native");
        b.putInt("cloud_app_color_strength",0);CloudCheck.config(b);CloudLockHook.refresh();
        check(Arrays.equals(app.colors,new int[]{0x006688aa,0x00446688,0x00123456}),
            "zero slider strength disables every gradient stop without a separate switch");
        b.putInt("cloud_app_color_strength",100);CloudCheck.config(b);CloudLockHook.refresh();
        check(Arrays.equals(app.colors,original),"100 percent slider strength restores the original native mask");
        b.putInt("cloud_app_color_strength",200);CloudCheck.config(b);CloudLockHook.refresh();
        check(app.colors[0]==0xff6688aa&&app.colors[1]==0x00446688,"live strength doubles original alpha with saturation, not previous result");
        b.putBoolean("cloud_app_color",false);CloudCheck.config(b);CloudLockHook.refresh();
        check(app.colors[0]==0x006688aa&&app.colors[2]==0x00123456,"disabled mask clears solid and gradient opacity");
        b.putBoolean("cloud_enabled",false);CloudCheck.config(b);CloudLockHook.refresh();
        check(Arrays.equals(app.colors,original),"cloud master off restores native gradient");
        b.putBoolean("cloud_enabled",true);b.putBoolean("enabled",false);CloudCheck.config(b);CloudLockHook.refresh();
        check(Arrays.equals(app.colors,original),"module master off restores native gradient");
        b.putBoolean("enabled",true);b.putBoolean("cloud_app_color",true);b.putBoolean("cloud_app_color_strength_enabled",false);CloudCheck.config(b);
        producer=scope(app);solid(app,0xff887766,true);producer.failure=new RuntimeException("producer failed");end(producer);
        CloudLockHook.refresh();check(Arrays.equals(app.colors,original),"failed native write does not replace saved original");
        solid(app,0xff010203,false);check(app.color==0xff010203,"producer scope clears after failure, preserving AOD and animation colors");
        CloudLockHook.refresh();check(app.color==0xff010203,"live settings never replay app color over a subsequent AOD or animation write");
        producer=scope(app);var nested=scope(other);solid(other,0x80663322,false);end(nested);solid(app,0x806688aa,false);end(producer);
        CloudLockHook.refresh();check(app.color==0x806688aa,"nested producer scopes restore their previous target");

        b=settings();b.putInt("cloud_lock_style",2);b.putBoolean("cloud_lock_tint",true);
        b.putInt("cloud_lock_color",0x3c778899);CloudCheck.config(b);
        Manager manager=new Manager();BlurConfig blur=new BlurConfig();View view=new View();
        CloudLockHook.track(view,new ViewBlurProxy(blur),"CUSTOM",manager);
        check(CloudLockHook.area(blur,ShadeHook.snapshot()).color==0x3c778899,"custom material applies to lock-screen flow card");
        manager.keyguard=false;check(CloudLockHook.area(blur,ShadeHook.snapshot())==null,"shade cards keep existing independent material");
        CapsuleItemView item=new CapsuleItemView();item.entry=new CapsuleSeedingData();View background=new View();background.parent=item;
        BlurConfig capsule=new BlurConfig();CloudLockHook.track(background,new ViewBlurProxy(capsule),"CAPSULE",manager);
        check(CloudLockHook.area(capsule,ShadeHook.snapshot()).tint,"lock-screen flow capsule receives selected material");
        capsule.platformMixConfig=new BlurMixConfig.BlurMixSingleWithShader(new com.oplusos.systemui.common.blurability.MixColorWithShader(5,0xff123456,3,0xff654321));
        Reflect.setAdditionalInstanceField(capsule,"thunder.role","cloud-lock");
        String backend="com.oplusos.systemui.common.blurability.ViewBlurProxy#applyBlurConfig";
        ViewBlurProxy capsuleProxy=new ViewBlurProxy(capsule);
        Object nativeMix=capsule.platformMixConfig;
        var frame=QsTintCheck.before(backend,capsuleProxy);
        var mixed=(BlurMixConfig.BlurMixSingleWithShader)capsule.platformMixConfig;
        check(mixed.backgroundShaderParam.topLayerColor==0x3c778899&&mixed.backgroundShaderParam.bottomLayerColor==0,
            "actual native shader receives custom tint once and removes extra mixing");
        QsTintCheck.after(backend,frame);
        check(capsule.platformMixConfig==nativeMix,"native shader config restores after rendering");
        for(Object plain:new Object[]{new BlurMixConfig.BlurMixSingle(new com.oplusos.systemui.common.blurability.MixColor(4,0xff123456,0xff654321)),
                new BlurMixConfig.BlurMixMulti(new com.oplusos.systemui.common.blurability.MixColor(4,0xff123456,0),new com.oplusos.systemui.common.blurability.MixColor(5,0xff654321,0))}) {
            capsule.platformMixConfig=plain;
            frame=QsTintCheck.before(backend,capsuleProxy);
            check(capsule.platformMixConfig instanceof BlurMixConfig.BlurMixSingleWithShader&&
                ((BlurMixConfig.BlurMixSingleWithShader)capsule.platformMixConfig).backgroundShaderParam.topLayerColor==0x3c778899,
                "compositor-only lock material receives custom tint instead of silently ignoring it");
            QsTintCheck.after(backend,frame);
            check(capsule.platformMixConfig==plain,"plain compositor material restores after rendering");
        }
        capsule.platformMixConfig=nativeMix;
        b.putInt("cloud_lock_style",2);b.putBoolean("cloud_lock_blur",true);b.putInt("cloud_lock_radius",151);
        b.putBoolean("cloud_lock_tint",true);b.putInt("cloud_lock_color",0x44556677);CloudCheck.config(b);
        frame=QsTintCheck.before(backend,capsuleProxy);
        check(capsule.blurRadius==151&&capsule.motionBlurRadius==151,"custom lock blur reaches native backends");
        frame.failure=new RuntimeException("draw failed");QsTintCheck.after(backend,frame);
        check(capsule.blurRadius==0&&capsule.platformMixConfig==nativeMix,"backend failure restores lock material fields");
        String draw="com.oplus.systemui.notification.lockscreen.capsule.CapsuleBackgroundHelper#drawCapsuleBg";
        String fallback="com.oplus.systemui.notification.lockscreen.capsule.CapsuleBackgroundHelper#getBackgroundColor";
        var helper=new com.oplus.systemui.notification.lockscreen.capsule.CapsuleBackgroundHelper();helper.viewBlurProxy=capsuleProxy;
        SharedProxy shared=new SharedProxy(new BlurConfig());
        helper.smoothSharedViewBlurProxy=shared;
        var drawFrame=QsTintCheck.before(draw,helper,background,new android.graphics.Canvas());
        check(shared.material!=null&&shared.material.color==0x44556677,
            "pooled background inherits the drawing capsule, even without a parent or manager registration");
        check((Integer)CloudCheck.after(fallback,helper,0xff123456)==0x44556677,"no-blur fallback uses selected lock material inside the native clip");
        drawFrame.failure=new RuntimeException("fallback failed");QsTintCheck.after(draw,drawFrame);
        check((Integer)CloudCheck.after(fallback,helper,0xff123456)==0xff123456,"fallback drawing scope clears after native failure");
        CapsuleItemView ordinary=new CapsuleItemView();ordinary.entry=new Object();
        View otherBackground=new View();otherBackground.parent=ordinary;
        var otherHelper=new com.oplus.systemui.notification.lockscreen.capsule.CapsuleBackgroundHelper();
        otherHelper.smoothSharedViewBlurProxy=shared;
        drawFrame=QsTintCheck.before(draw,otherHelper,otherBackground,new android.graphics.Canvas());
        QsTintCheck.after(draw,drawFrame);
        check(shared.material==null,"shared proxy restores native material when reused by an ordinary notification");
        int applies=shared.applies;
        drawFrame=QsTintCheck.before(draw,helper,background,new android.graphics.Canvas());
        QsTintCheck.after(draw,drawFrame);
        check(shared.applies==applies+1&&shared.material!=null,
            "returning a pooled proxy to the same flow capsule reapplies material despite unchanged settings");
        applies=shared.applies;
        drawFrame=QsTintCheck.before(draw,helper,background,new android.graphics.Canvas());
        QsTintCheck.after(draw,drawFrame);
        check(shared.applies==applies,"unchanged frames do not rebuild shared background material");
        item.entry=new Object();check(CloudLockHook.area(capsule,ShadeHook.snapshot())==null,"ordinary notification capsules are excluded");
        b.putInt("cloud_lock_style",0);CloudCheck.config(b);manager.keyguard=true;
        check(CloudLockHook.area(blur,ShadeHook.snapshot())==null,"system style restores existing native or card material");
        System.out.println("Verified "+checks+" lock-screen material and application-mask checks; phone rendering still needs validation.");
    }
}
