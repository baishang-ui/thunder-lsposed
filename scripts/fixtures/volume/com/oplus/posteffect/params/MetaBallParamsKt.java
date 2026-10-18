package com.oplus.posteffect.params;
public final class MetaBallParamsKt {
 public static UniformResult buildMetaBallUniformArrays(float[] origin,MetaBallParams params){return new UniformResult();}
 public static void setMetaBallUniform(Object shader,MetaBallParams params,float[] origin){}
 public static final class UniformResult {
  public final float[] preCmpArray12=new float[24],preCmpArray13=new float[24],preCmpArray23=new float[24];
  public final float[] ball1Array={0,0,28,120,28,1.1f,0,0};
  public UniformResult(){java.util.Arrays.fill(preCmpArray12,7);preCmpArray12[11]=40;preCmpArray12[14]=40;preCmpArray12[17]=12;}
 }
}
