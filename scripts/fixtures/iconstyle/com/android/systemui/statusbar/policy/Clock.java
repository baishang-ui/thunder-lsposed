package com.android.systemui.statusbar.policy;
public class Clock extends android.widget.TextView {
 public boolean mShowSeconds; public int mSecondsFixedWidth=84;
 public int computeSecondsFixedLayoutWidth(){return (int)Math.ceil(getText().length()*getTextSize()*.6f)+4;}
}
