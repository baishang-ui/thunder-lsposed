package com.oplus.systemui.statusbar.widget;
public class StatClock extends com.android.systemui.statusbar.policy.Clock {
 public int cachedMinutesLayoutWidth=-1,publishedMinutes,publishedFull;
 public void onLayout(boolean changed,int l,int t,int r,int b){publishedMinutes=cachedMinutesLayoutWidth;publishedFull=mSecondsFixedWidth>0?mSecondsFixedWidth:cachedMinutesLayoutWidth;}
 public void reloadStatClockDimens(){setTextSize(0,14);}
 public void reloadDimens(){reloadStatClockDimens();}
 public void updateConfigurationChanged(android.content.res.Configuration c){reloadStatClockDimens();}
}
