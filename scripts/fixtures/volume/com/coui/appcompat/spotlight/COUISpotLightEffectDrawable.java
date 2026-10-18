package com.coui.appcompat.spotlight;
public class COUISpotLightEffectDrawable extends android.graphics.drawable.Drawable {
 public boolean enabled,cancelled;public int touches;public Object target;
 public void setEnabled(boolean v){enabled=v;}public void onTouchEvent(android.view.MotionEvent e){touches++;}public void cancel(){cancelled=true;}
}