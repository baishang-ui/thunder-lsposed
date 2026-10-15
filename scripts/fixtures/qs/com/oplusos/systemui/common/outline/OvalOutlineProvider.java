package com.oplusos.systemui.common.outline;
public class OvalOutlineProvider extends android.view.ViewOutlineProvider {
 public static final OvalOutlineProvider INSTANCE=new OvalOutlineProvider();
 public float getCornerRadius(android.graphics.drawable.Drawable d){return d.getBounds().height()/2f;}
 public Float getCornerWeight(android.graphics.drawable.Drawable d){return null;}
 public void getOutline(android.view.View view,android.graphics.Outline outline){outline.setRoundRect(0,0,view.getWidth(),view.getHeight(),view.getHeight()/2f);}
}
