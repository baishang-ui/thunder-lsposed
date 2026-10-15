package com.oplus.systemui.qs.base.res.drawable;
public class GradientTileDrawable extends android.graphics.drawable.Drawable {
 public android.graphics.drawable.ColorDrawable colorDrawable=new android.graphics.drawable.ColorDrawable(0xff998877);
 public int userAlpha=255,invalidations;
 public int getAlpha(){return userAlpha;}
 public void invalidateSelf(){invalidations++;}
}