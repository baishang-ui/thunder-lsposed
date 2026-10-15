package com.oplusos.systemui.common.blurability;
public class BlurMixConfig {
 private float mirrorScale=1; private boolean alphaWithBlurAmount;
 public float getMirrorScale(){return mirrorScale;}
 public void setMirrorScale(float value){mirrorScale=value;}
 public boolean getAlphaWithBlurAmount(){return alphaWithBlurAmount;}
 public void setAlphaWithBlurAmount(boolean value){alphaWithBlurAmount=value;}
 public static class BlurMixSingle extends BlurMixConfig {public final MixColor mixColor;public BlurMixSingle(MixColor m){mixColor=m;}public MixColor getMixColor(){return mixColor;}}
 public static class BlurMixSingleWithShader extends BlurMixConfig {
  public MixColorWithShader getBackgroundShaderParam(){return backgroundShaderParam;}public final MixColorWithShader backgroundShaderParam;
  public BlurMixSingleWithShader(MixColorWithShader value){backgroundShaderParam=value;}
 }

 public static class BlurMixMultiWithShader extends BlurMixConfig {
  public final MixColorWithShader foregroundShaderParam,backgroundShaderParam;private int placeHolderColor;
  public BlurMixMultiWithShader(MixColorWithShader fg,MixColorWithShader bg){foregroundShaderParam=fg;backgroundShaderParam=bg;}
  public MixColorWithShader getForegroundShaderParam(){return foregroundShaderParam;}
  public MixColorWithShader getBackgroundShaderParam(){return backgroundShaderParam;}
  public int getPlaceHolderColor(){return placeHolderColor;}public void setPlaceHolderColor(int color){placeHolderColor=color;}
 }
}
