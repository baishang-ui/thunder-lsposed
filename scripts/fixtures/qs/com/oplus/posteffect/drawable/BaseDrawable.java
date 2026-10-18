package com.oplus.posteffect.drawable;
public class BaseDrawable extends android.graphics.drawable.Drawable {
 public final android.graphics.Paint bitmapPaint=new android.graphics.Paint();
 public android.graphics.Paint getPaint(){return bitmapPaint;}
 public boolean contentDirty;
 public int invalidations;
 public final android.graphics.Paint paint=new android.graphics.Paint();
 public android.graphics.Paint getDrawableShaderPaint(){return paint;}
 public void drawBlurShader(android.graphics.Canvas c){}
 public void draw(android.graphics.Canvas c){}
 @Override public void invalidateSelf(){invalidations++;}
}
