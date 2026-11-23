package com.oplusos.systemui.common.blurability.platformblur;
public class PlatformBlurDrawable extends android.graphics.drawable.Drawable {
 public Object blurDrawable=new com.oplus.posteffect.drawable.MetaBallBlurDrawable();
 public Object getBlurDrawable(){return blurDrawable;}
 public boolean changeBlurColor(com.oplusos.systemui.common.blurability.BlurMixConfig mix,boolean reset){return true;}
 public void draw(android.graphics.Canvas canvas){}
}
