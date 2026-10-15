package com.android.server.wm;
public class DisplayPolicy {public WindowState mStatusBar=new WindowState(),mNotificationShade=new WindowState();public boolean keyguard;
 public DisplayPolicy(){mNotificationShade.visible=false;}
 public boolean isKeyguardShowing(){return keyguard;}public boolean isKeyguardOccluded(){return false;}
 public WindowState getStatusBar(){return mStatusBar;}public void finishPostLayoutPolicyLw(){}
}
