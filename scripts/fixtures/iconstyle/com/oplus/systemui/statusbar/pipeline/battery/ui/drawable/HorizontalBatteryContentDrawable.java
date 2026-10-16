package com.oplus.systemui.statusbar.pipeline.battery.ui.drawable;
public class HorizontalBatteryContentDrawable extends android.graphics.drawable.Drawable {
 public HorizontalBatteryContentDrawable(){setBounds(0,0,32,18);}
 public int level=75,charge;public boolean isShowPercentIn;public android.graphics.RectF rect=new android.graphics.RectF(0,0,26,12),noPercentRectF=rect;
 public boolean rtl; public boolean isRTLLayout(){return rtl;}
 public android.graphics.RectF getBodyRectForLayout(android.graphics.RectF content){return new android.graphics.RectF(content.left,content.top,content.right,content.bottom);}
 public float getOffsetX(){return 1f;}
 public android.graphics.Paint paint=new android.graphics.Paint();public int getBatteryLevel(){return level;}public int getChargeIconId(){return charge;}public int getOutlineColor(){return -1;}public int getProgressColor(){return 0xff34c759;}
}
