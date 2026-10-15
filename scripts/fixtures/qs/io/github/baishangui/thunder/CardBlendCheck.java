package io.github.baishangui.thunder;

import android.os.Bundle;
import com.oplusos.systemui.common.blurability.*;

/** Native formulas transcribed from C17; these are parameter/math checks, not GPU rendering. */
public final class CardBlendCheck {
    static final String B="com.oplusos.systemui.common.blurability.";
    static final String HELPER="com.oplusos.systemui.common.util.ShaderBlendParamHelper#updateMixSingleShaderParams";
    static int count;
    static void check(boolean ok,String label) {if(!ok)throw new AssertionError(label);count++;}
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
                check(mix.getAlphaWithBlurAmount(),"all custom shaders fade through drawable alpha: "+role);
                if(multi)check(((BlurMixConfig.BlurMixMultiWithShader)mix).backgroundShaderParam.topMode==0,"empty extra shader");
                check(mix.getPlaceHolderColor()==(color>>>24==0?0:color),"single and multi software fallback use same selected tint");
                double alpha=(color>>>24)/255.0;
                // Native helper scales single/background colors; multi foreground stays unscaled.
                // PlatformBlurDrawable subsequently sets its own alpha to floor(255*amount).
                for(float amount:new float[]{0,.25f,.5f,1}) {
                    float shaderAmount=1;
                    if(!multi) {
                        check(Api102Runtime.callbacks.containsKey(HELPER),"single shader animation hook installed");
                        HookCallback.Param frame=QsTintCheck.before(HELPER,null,mix,amount);
                        shaderAmount=(Float)frame.args[1];
                    }
                    for(int background:new int[]{0,96,255})for(int shift:new int[]{0,8,16}) {
                        double src=(color>>shift&255)/255.0,dst=background/255.0;
                        double shaded=channel(tint.topMode,src,alpha*shaderAmount,dst);
                        double drawableAlpha=(int)(255*amount)/255.0;
                        double rendered=dst+(shaded-dst)*drawableAlpha;
                        check(Math.abs(rendered-(dst+(src-dst)*alpha*drawableAlpha))<1e-6,
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
        BlurMixConfig original=new BlurMixConfig.BlurMixSingleWithShader(new MixColorWithShader(5,0x92000000,2,0x66999999));
        BlurConfig panel=new BlurConfig();Reflect.setAdditionalInstanceField(panel,"thunder.role","panel");
        panel.platformMixConfig=CloudCheck.after("com.oplusos.systemui.common.util.NotifiAndQsPlatformBlurExKt#panelPlatformMixConfig",null,original,new android.content.Context(),false,false);
        b.putBoolean("enabled",false);CloudCheck.config(b);
        check(CloudCheck.after(B+"BlurConfig#getPlatformMixConfig",panel,panel.platformMixConfig)==original,"master off unwraps persisted producer replacement");
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
