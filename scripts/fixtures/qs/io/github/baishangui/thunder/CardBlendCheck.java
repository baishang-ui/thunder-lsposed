package io.github.baishangui.thunder;

import android.os.Bundle;
import com.oplusos.systemui.common.blurability.*;

/** Native formulas transcribed from C17; these are parameter/math checks, not GPU rendering. */
public final class CardBlendCheck {
    static final String B="com.oplusos.systemui.common.blurability.";
    static final String HELPER="com.oplusos.systemui.common.util.ShaderBlendParamHelper#updateMixSingleShaderParams";
    static int count;
    static void check(boolean ok,String label) {if(!ok)throw new AssertionError(label);count++;}
    // C17 PlatformBlurDrawable.applyBlurConfig() opacity, without the optional
    // full-opacity override. Keep capture opacity separate from tint opacity.
    static double drawableAlpha(BlurMixConfig mix,float amount) {
        return (int)(255*(mix.getAlphaWithBlurAmount()?amount:Math.max(0,Math.min(1,amount/.05d))))/255d;
    }
    static double channel(int mode,double src,double alpha,double dst) {
        if(mode==0)return dst;
        if(mode==1)return dst*(1-alpha)+src*alpha;
        if(mode!=4)throw new AssertionError("Unexpected shader mode "+mode);
        if(dst==0)return 0;
        double c=Math.abs(alpha-src);
        return c==0?alpha+dst*(1-alpha):Math.min(1,dst*alpha/(c+1e-8))*alpha+dst*(1-alpha);
    }
    public static final class BlurTypeMotion {}
    public static final class BlurTypeStatic {}
    public static final class Proxy extends ViewBlurProxy {
        final Object type;Proxy(BlurConfig c,Object t){super(c);type=t;}
        @Override public Object getBlurType(){return type;}
    }
    public static void main(String[] args)throws Throwable {
        ShadeHook.install("com.android.systemui","com.android.systemui",CardBlendCheck.class.getClassLoader());
        check(Math.abs(channel(4,0,1,.5)-.5)<1e-6,"firmware DODGE does not cover gray with opaque black");
        Bundle b=new Bundle();b.putBoolean("enabled",true);
        for(String key:new String[]{"qs_card","notification_card","shade","banner"})b.putBoolean(key+"_tint",true);
        for(String role:new String[]{"qs-card","notification-card","panel","banner"})for(boolean multi:new boolean[]{false,true}) {
            if(role.equals("panel")&&multi)continue; // Producer's declared return type is single shader.
            BlurConfig c=new BlurConfig();
            BlurMixConfig original=multi?QsTintCheck.multi(0xff123456):
                new BlurMixConfig.BlurMixSingleWithShader(new MixColorWithShader(5,0xff123456,2,0xff654321));
            c.platformMixConfig=original;
            Reflect.setAdditionalInstanceField(c,"thunder.role",role);
            for(int color:new int[]{0xff000000,0xffffffff,0x80000000,0x80ffffff,0x80446688,0x00446688}) {
                for(String key:new String[]{"qs_card","notification_card","shade","banner"})b.putInt(key+"_color",color);
                CloudCheck.config(b);
                BlurMixConfig mix=(BlurMixConfig)CloudCheck.after(B+"BlurConfig#getPlatformMixConfig",c,original);
                MixColorWithShader tint=multi?((BlurMixConfig.BlurMixMultiWithShader)mix).foregroundShaderParam:
                    ((BlurMixConfig.BlurMixSingleWithShader)mix).backgroundShaderParam;
                check(tint.bottomMode==0&&tint.bottomLayerColor==0,"no extra native tint: "+role);
                check(mix.getAlphaWithBlurAmount()==(!role.equals("panel")||original.getAlphaWithBlurAmount()),
                    "panel retains native capture opacity; cards fade through drawable alpha: "+role);
                if(multi)check(((BlurMixConfig.BlurMixMultiWithShader)mix).backgroundShaderParam.topMode==0,"empty extra shader");
                check(mix.getPlaceHolderColor()==(role.equals("panel")||color>>>24==0?0:color),"panel waits for its capture; card placeholders retain their material tint");
                double alpha=(color>>>24)/255.0;
                // Native helper scales single/background colors; multi foreground stays unscaled.
                // Panels may keep an opaque scaled capture and animate tint in the
                // shader; cards instead animate their entire drawable.
                for(float amount:new float[]{0,.01f,.25f,.5f,1}) {
                    float shaderAmount=1;
                    if(!multi) {
                        check(Api102Runtime.callbacks.containsKey(HELPER),"single shader animation hook installed");
                        HookCallback.Param frame=QsTintCheck.before(HELPER,null,mix,amount);
                        shaderAmount=(Float)frame.args[1];
                    }
                    for(int background:new int[]{0,96,255})for(int shift:new int[]{0,8,16}) {
                        double src=(color>>shift&255)/255.0,dst=background/255.0;
                        double shaded=channel(tint.topMode,src,alpha*shaderAmount,dst);
                        double captureAlpha=drawableAlpha(mix,amount);
                        double tintAmount=mix.getAlphaWithBlurAmount()?1:amount;
                        double rendered=dst+(shaded-dst)*captureAlpha;
                        check(Math.abs(rendered-(dst+(src-dst)*alpha*tintAmount*captureAlpha))<1e-6,
                            role+" "+(multi?"multi":"single")+" selected opacity is applied once during animation");
                    }
                }
                for(boolean absent:new boolean[]{false,true}) {
                    c.motionBlurMixConfig=absent?null:new BlurMixConfig.BlurMixSingle(new MixColor(4,0xff998877,0xff112233));
                    Object nativeMotion=c.motionBlurMixConfig;
                    Object motion=CloudCheck.after(B+"BlurConfig#getMotionBlurMixConfig",c,nativeMotion);
                    check(motion instanceof BlurMixConfig.BlurMixSingle,"motion material exists even if native config absent");
                    MixColor mc=((BlurMixConfig.BlurMixSingle)motion).mixColor;
                    check(mc.mode==(alpha==0?0:1)&&mc.top==(alpha==0?0:color)&&mc.bottom==0,"native COLORMIX has only selected color");
                    for(Object type:new Object[]{new BlurTypeMotion(),new BlurTypeStatic()}) {
                        String key=B+"ViewBlurProxy#"+(type instanceof BlurTypeMotion?"applyConfigToMotionBlur":"applyConfigToStaticBlur");
                        HookCallback.Param frame=QsTintCheck.before(key,new Proxy(c,type));
                        check(c.motionBlurMixConfig instanceof BlurMixConfig.BlurMixSingle,"direct backend receives custom material");
                        int mask=(Integer)CloudCheck.after(B+"BlurConfig#getBlurColor",c,0xff123456);
                        check(mask==(type instanceof BlurTypeStatic?color:0),"static uses mask; motion material has no independent paint");
                        frame.failure=new IllegalStateException("native draw failed");QsTintCheck.after(key,frame);
                        check(c.motionBlurMixConfig==nativeMotion&&c.platformMixConfig==original,"native fields restored after failure");
                    }
                }
            }
            for(String key:new String[]{"qs_card","notification_card","shade","banner"})b.putBoolean(key+"_tint",false);
            CloudCheck.config(b);
            check(CloudCheck.after(B+"BlurConfig#getPlatformMixConfig",c,original)==original,"system restore keeps original");
            check(CloudCheck.after(B+"BlurConfig#getMotionBlurMixConfig",c,c.motionBlurMixConfig)==c.motionBlurMixConfig,"native motion restore");
            // Panel producer can persist our replacement in BlurConfig. Restore must unwrap it too.
            for(String key:new String[]{"qs_card","notification_card","shade","banner"})b.putBoolean(key+"_tint",true);
            CloudCheck.config(b);
        }
        // With panel scaling enabled the system chooses alphaWithBlurAmount=false.
        // A translucent capture would show both the scaled and unscaled images,
        // even when the selected tint is fully transparent.
        for(boolean nativeFade:new boolean[]{false,true}) {
            BlurMixConfig nativePanel=new BlurMixConfig.BlurMixSingleWithShader(new MixColorWithShader(5,0,0,0));
            nativePanel.setAlphaWithBlurAmount(nativeFade);
            nativePanel.setMirrorScale(.94f);
            BlurConfig shrinking=new BlurConfig();Reflect.setAdditionalInstanceField(shrinking,"thunder.role","panel");
            shrinking.platformMixConfig=CloudCheck.after("com.oplusos.systemui.common.util.NotifiAndQsPlatformBlurExKt#panelPlatformMixConfig",null,nativePanel,new android.content.Context(),!nativeFade,false);
            for(int color:new int[]{0,0x80446688,0xff446688}) {
                b.putInt("shade_color",color);CloudCheck.config(b);
                BlurMixConfig customPanel=(BlurMixConfig)CloudCheck.after(B+"BlurConfig#getPlatformMixConfig",shrinking,shrinking.platformMixConfig);
                check(customPanel.getAlphaWithBlurAmount()==nativeFade,"producer and getter preserve native panel opacity policy");
                for(float amount:new float[]{0,.025f,.05f,.25f,.5f,.75f,1}) {
                    check(drawableAlpha(customPanel,amount)==drawableAlpha(nativePanel,amount),"custom tint does not expose a second unscaled background");
                    HookCallback.Param tintFrame=QsTintCheck.before(HELPER,null,customPanel,amount);
                    check(tintFrame.args[1].equals(nativeFade?1f:amount),"opaque panel animates tint in shader, fading panel applies opacity once");
                }
                HookCallback.Param backend=QsTintCheck.before(B+"ViewBlurProxy#applyConfigToPlatformBlur",new Proxy(shrinking,new BlurTypeStatic()));
                check(((BlurMixConfig)shrinking.platformMixConfig).getAlphaWithBlurAmount()==nativeFade,"direct backend retains native capture opacity");
                QsTintCheck.after(B+"ViewBlurProxy#applyConfigToPlatformBlur",backend);
            }
        }
        BlurMixConfig original=new BlurMixConfig.BlurMixSingleWithShader(new MixColorWithShader(5,0x92000000,2,0x66999999));
        BlurConfig panel=new BlurConfig();Reflect.setAdditionalInstanceField(panel,"thunder.role","panel");
        panel.platformMixConfig=CloudCheck.after("com.oplusos.systemui.common.util.NotifiAndQsPlatformBlurExKt#panelPlatformMixConfig",null,original,new android.content.Context(),false,false);
        String scaleKey=B+"BlurMixConfig#setMirrorScale";
        // C17 applyPanelMirrorScale writes through getPlatformMixConfig(), then the
        // backend reads that getter again. Exercise complete opening/closing frames.
        for(float scale:new float[]{1f,.985f,.97f,.94f,.97f,1f,.94f}) {
            BlurMixConfig animated=(BlurMixConfig)CloudCheck.after(B+"BlurConfig#getPlatformMixConfig",panel,panel.platformMixConfig);
            animated.setMirrorScale(scale);
            if(Api102Runtime.callbacks.containsKey(scaleKey)) {
                HookCallback.Param write=QsTintCheck.before(scaleKey,animated,scale);
                QsTintCheck.after(scaleKey,write);
            }
            check(original.getMirrorScale()==scale,"animation writes reach retained native source");
            BlurMixConfig rendered=(BlurMixConfig)CloudCheck.after(B+"BlurConfig#getPlatformMixConfig",panel,panel.platformMixConfig);
            check(rendered.getMirrorScale()==scale,"backend getter preserves current animation frame");
            HookCallback.Param render=QsTintCheck.before(B+"ViewBlurProxy#applyConfigToPlatformBlur",new Proxy(panel,new BlurTypeStatic()));
            check(((BlurMixConfig)panel.platformMixConfig).getMirrorScale()==scale,"direct backend preserves animated scale");
            QsTintCheck.after(B+"ViewBlurProxy#applyConfigToPlatformBlur",render);
        }
        // A color change must use the latest geometry, and disabling must restore it.
        b.putInt("shade_color",0x80446688);CloudCheck.config(b);
        check(((BlurMixConfig)CloudCheck.after(B+"BlurConfig#getPlatformMixConfig",panel,panel.platformMixConfig)).getMirrorScale()==.94f,"changing tint preserves native scale");
        b.putBoolean("enabled",false);CloudCheck.config(b);
        check(CloudCheck.after(B+"BlurConfig#getPlatformMixConfig",panel,panel.platformMixConfig)==original,"master off unwraps persisted producer replacement");
        check(original.getMirrorScale()==.94f,"disabling tint retains the latest shrink state");
        String nativeKey=B+"ViewBlurProxy#applyConfigToPlatformBlur";
        Object saved=panel.platformMixConfig;
        HookCallback.Param frame=QsTintCheck.before(nativeKey,new Proxy(panel,new BlurTypeStatic()));
        check(panel.platformMixConfig==original,"direct consumer unwraps producer replacement when disabled");
        QsTintCheck.after(nativeKey,frame);check(panel.platformMixConfig==saved,"temporary unwrapping preserves owner field");
        HookCallback.Param stock=QsTintCheck.before(HELPER,null,original,.25f);
        check(stock.args[1].equals(.25f),"unmarked native shaders keep their animation");
        System.out.println("Verified "+count+" blend regression checks; GPU rendering remains unverified.");
    }
}
