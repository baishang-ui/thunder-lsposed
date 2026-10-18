package com.oplusos.systemui.common.blurability.drawable;
public class AutoBlurDrawable extends android.graphics.drawable.Drawable {
 public final com.oplusos.systemui.common.blurability.ViewBlurProxy proxy;
 public AutoBlurDrawable(com.oplusos.systemui.common.blurability.ViewBlurProxy proxy){this.proxy=proxy;}
 public com.oplusos.systemui.common.blurability.ViewBlurProxy getViewBlurProxy(){return proxy;}
 public void draw(android.graphics.Canvas canvas){}
}
