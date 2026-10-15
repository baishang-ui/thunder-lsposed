package com.oplus.systemui.statusbar.pipeline.battery.ui.model;
public final class BatteryIconStyle {
 public final int iconStyle,densityDpi,frameColorMode,themeId,batteryOutChargeBgMode;
 public final boolean oldStylePercentIn,isThemeModeColor,isPercentOutBgMode,isPercentOutPbMode;
 public final Object localeList;public final float scaleFactor;
 public BatteryIconStyle(int style){this(style,false,new Object(),440,1.25f,2,true,8,true,true,3);}
 private BatteryIconStyle(int i,boolean p,Object l,int d,float s,int f,boolean t,int id,boolean bg,boolean pb,int charge){iconStyle=i;oldStylePercentIn=p;localeList=l;densityDpi=d;scaleFactor=s;frameColorMode=f;isThemeModeColor=t;themeId=id;isPercentOutBgMode=bg;isPercentOutPbMode=pb;batteryOutChargeBgMode=charge;}
 public int getIconStyle(){return iconStyle;}
 public BatteryIconStyle copy(int i,boolean p,Object l,int d,float s,int f,boolean t,int id,boolean bg,boolean pb,int c){return new BatteryIconStyle(i,p,l,d,s,f,t,id,bg,pb,c);}
}
