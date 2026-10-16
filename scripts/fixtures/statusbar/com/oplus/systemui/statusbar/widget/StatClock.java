package com.oplus.systemui.statusbar.widget;
public class StatClock extends com.android.systemui.statusbar.policy.Clock {
 public void reloadStatClockDimens(){setTextSize(0,14);}
 public void reloadDimens(){reloadStatClockDimens();}
 public void updateConfigurationChanged(android.content.res.Configuration c){reloadStatClockDimens();}
}
