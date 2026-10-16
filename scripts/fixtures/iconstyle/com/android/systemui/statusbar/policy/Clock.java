package com.android.systemui.statusbar.policy;
public class Clock extends android.widget.TextView {
 public boolean mShowSeconds; public int mSecondsFixedWidth=84;
 public java.util.Calendar mCalendar=java.util.Calendar.getInstance();
 public int updates,secondUpdates;
 public void updateShowSeconds(){secondUpdates++;}
 public void updateClock(){updates++;}
 public void refreshWidth(){layout.width=mShowSeconds?computeSecondsFixedLayoutWidth():-2;}
 public void onAttachedToWindow(){}public void reloadDimens(){setTextSize(0,14);}
 public CharSequence getSmallTime(){return "native";}
 public void applyShowSecondsFromController(boolean value){mShowSeconds=value;}
 public void onMeasure(int w,int h){measure(w,h);}
 public int getSecondsWidthExtraPadding(){return getCompoundPaddingLeft()+getCompoundPaddingRight();}
 public int computeSecondsFixedLayoutWidth(){return (int)Math.ceil(getText().length()*getTextSize()*.6f)+4;}
}
