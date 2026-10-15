package com.android.server.wm;
public class DisplayContent {public DisplayPolicy policy=new DisplayPolicy();
 public DisplayPolicy getDisplayPolicy(){return policy;}public android.graphics.Rect getBounds(){return WindowManagerService.bounds();}
 public Object getAsyncRotationController(){return null;}public boolean hasFixedRotationTransientLaunch(){return false;}
 public int getDisplayId(){return 0;}
}
