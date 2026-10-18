package io.github.baishangui.thunder;

import android.os.Bundle;
import android.graphics.*;
import com.oplus.posteffect.drawable.BaseDrawable;
import com.oplusos.systemui.common.blurability.*;

/** Real Hook callbacks with host Paint stand-ins; device GPU validation is separate. */
public final class PanelSaturationCheck {
 static int checks;
 static final String B="com.oplusos.systemui.common.blurability.";
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);checks++;}
 static void config(Bundle b)throws Exception{var f=ShadeHook.class.getDeclaredField("config");f.setAccessible(true);f.set(null,new HookConfig(b));}
 static HookCallback.Param param(Object owner,Object result,Object... args){var p=new HookCallback.Param();p.thisObject=owner;p.args=args;p.setResult(result);return p;}
 static void track(BaseDrawable d,BlurConfig c)throws Throwable {
  var p=param(null,d,c,1f);Api102Runtime.callbacks.get(B+"platformblur.PlatformBlurDrawable#applyBlurConfig").after(p);
 }
 public static void main(String[] args)throws Throwable {
  ShadeHook.install("com.android.systemui","com.android.systemui",PanelSaturationCheck.class.getClassLoader());
  Bundle b=new Bundle();b.putBoolean("enabled",true);b.putBoolean("panel_saturation_enabled",true);b.putInt("panel_radius",94);
  BlurConfig blur=new BlurConfig();var original=new BlurMixConfig.BlurMixSingleWithShader(new MixColorWithShader(5,0x92000000,2,0x66999999));
  blur.platformMixConfig=original;Reflect.setAdditionalInstanceField(blur,"thunder.role","panel");
  BaseDrawable d=new BaseDrawable();d.paint.setAlpha(127);ColorFilter existing=null;
  HookCallback draw=Api102Runtime.callbacks.get("com.oplus.posteffect.drawable.BlendDrawable#onDrawContent");
  for(int percent:new int[]{0,50,100,150,200}) {
   b.putInt("panel_saturation",percent);config(b);track(d,blur);
   var p=param(d,null,new Canvas());draw.before(p);
   if(percent==100)check(d.paint.getColorFilter()==existing,"100% is identity without another filter");
   else {
    float[] m=((ColorMatrixColorFilter)d.paint.getColorFilter()).matrix;
    for(int gray:new int[]{0,64,128,255})for(int row=0;row<3;row++)
     check(Math.abs(gray*(m[row*5]+m[row*5+1]+m[row*5+2])-gray)<.01,"gray/black/white do not change at any saturation");
    float[] color={64,128,192};float[] out=new float[3];
    for(int row=0;row<3;row++)out[row]=m[row*5]*color[0]+m[row*5+1]*color[1]+m[row*5+2]*color[2];
    if(percent==0)check(Math.abs(out[0]-out[1])<.001&&Math.abs(out[1]-out[2])<.001,"zero is grayscale, not white");
    if(percent==200)check(out[2]-out[0]>color[2]-color[0]&&out[1]>0,"200 increases chroma, not black");
    check(m[18]==1&&m[19]==0,"alpha is unchanged");
   }
   check(d.paint.getAlpha()==127,"native animation alpha unchanged");
   p.failure=new IllegalStateException();draw.after(p);
   check(d.paint.getColorFilter()==existing,"exception restores original paint filter");
   int invalidations=d.invalidations;track(d,blur);check(d.invalidations==invalidations,"same value avoids re-recording");
   check(blur.platformMixConfig==original,"saturation never replaces native color mix");
  }
  b.putInt("panel_saturation",150);config(b);track(d,blur);
  ColorFilter nativeFilter=new ColorFilter();d.paint.setColorFilter(nativeFilter);Canvas layered=new Canvas();
  var layerCall=param(d,null,layered);draw.before(layerCall);
  check(d.paint.getColorFilter()==nativeFilter,"native filter preserved while drawing");
  check(layered.depth==1&&layered.layerPaint.getColorFilter() instanceof ColorMatrixColorFilter,"outer layer applies saturation after native filter");
  layerCall.failure=new IllegalStateException();draw.after(layerCall);
  check(layered.depth==0&&d.paint.getColorFilter()==nativeFilter,"exception restores canvas and native filter");
  d.paint.setColorFilter(null);
  for(int target:new int[]{0,50,150,200}) {
   b.putInt("panel_saturation",target);config(b);float previous=100;
   for(int step=0;step<=100;step++) {
    float progress=step/100f;
    var frame=param(null,d,blur,progress);Api102Runtime.callbacks.get(B+"platformblur.PlatformBlurDrawable#applyBlurConfig").after(frame);
    var call=param(d,null,new Canvas());draw.before(call);
    ColorFilter active=d.paint.getColorFilter();
    float current=active==null?100:100*(((ColorMatrixColorFilter)active).matrix[0]-((ColorMatrixColorFilter)active).matrix[5]);
    check(Math.abs(current-previous)<=1.51f,"adjacent 1% expansion frames cannot jump to full saturation");
    check(target<100?current<=previous+.001:current>=previous-.001,"expansion is monotonic");
    if(step==0)check(active==null,"closed panel has identity saturation");
    if(step==100)check(Math.abs(current-target)<.01,"fully open reaches the selected value");
    draw.after(call);previous=current;
    var reverse=param(null,d,blur,progress);Api102Runtime.callbacks.get(B+"platformblur.PlatformBlurDrawable#applyBlurConfig").after(reverse);
    call=param(d,null,new Canvas());draw.before(call);
    float replay=d.paint.getColorFilter()==null?100:100*(((ColorMatrixColorFilter)d.paint.getColorFilter()).matrix[0]-((ColorMatrixColorFilter)d.paint.getColorFilter()).matrix[5]);
    check(Math.abs(replay-current)<.01,"same progress yields same color while reversing a gesture");draw.after(call);
   }
  }
  b.putInt("panel_saturation",0);config(b);track(d,blur);d.contentDirty=false;
  b.putBoolean("panel_saturation_enabled",false);config(b);track(d,blur);
  check(d.contentDirty,"disable invalidates recorded colored content");
  var p=param(d,null,new Canvas());draw.before(p);check(d.paint.getColorFilter()==existing,"disabled uses native paint");draw.after(p);
  b.putBoolean("panel_saturation_enabled",true);b.putBoolean("enabled",false);config(b);p=param(d,null,new Canvas());draw.before(p);check(d.paint.getColorFilter()==existing,"master off is identity");draw.after(p);
  b.putBoolean("enabled",true);config(b);BlurConfig other=new BlurConfig();other.platformMixConfig=original;track(d,other);
  p=param(d,null,new Canvas());draw.before(p);check(d.paint.getColorFilter()==existing,"reused drawable with non-panel owner is restored");draw.after(p);
  android.os.Binder.uid=2000;SettingsProvider.preferences(new android.content.Context()).edit().putBoolean("panel_saturation_enabled",true).putInt("panel_saturation",200).apply();
  Bundle delivered=new SettingsProvider().call("settings",null,null);
  check(delivered.getBoolean("panel_saturation_enabled",false)&&new HookConfig(delivered).panelSaturation==200,"provider fallback delivers saturation");
  check(!new HookConfig(b).shade.tint,"saturation does not enable tint");
  System.out.println("Verified "+checks+" saturation checks; device rendering is validated separately.");
 }
}
