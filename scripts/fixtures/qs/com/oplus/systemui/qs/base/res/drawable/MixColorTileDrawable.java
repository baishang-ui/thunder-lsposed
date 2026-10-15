package com.oplus.systemui.qs.base.res.drawable;
public class MixColorTileDrawable extends android.graphics.drawable.Drawable {
 public com.oplusos.systemui.common.blurability.drawable.AutoBlurDrawable autoBlurDrawable;
 public int maskColor=0xff112233,invalidations;
 public MixColorTileDrawable(com.oplusos.systemui.common.blurability.drawable.AutoBlurDrawable bg){autoBlurDrawable=bg;}
 public void invalidateSelf(){invalidations++;}
}