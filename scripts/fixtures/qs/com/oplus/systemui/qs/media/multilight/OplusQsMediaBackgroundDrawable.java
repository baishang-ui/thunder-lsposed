package com.oplus.systemui.qs.media.multilight;
public class OplusQsMediaBackgroundDrawable extends com.oplus.systemui.qs.base.res.drawable.TileLayerDrawable {
 public final Light mediaLightDrawable=new Light();
 public OplusQsMediaBackgroundDrawable(android.graphics.drawable.Drawable... layers){super(layers);}
 public static class Light {public Params params=new Params();}
 public static class Params {
  public float[] uCommonArray={400,200,0,0,400,200,47,47,47,47,1.4f,0};
  public int uCornerType=1;public boolean dirtyCommonArray,dirtyConerType;
 }
}
