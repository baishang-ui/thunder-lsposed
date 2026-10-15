package com.android.server.wm;
public class WindowManagerService {
 public android.content.Context mContext=new android.content.Context();public Object mGlobalLock=new Object();public android.os.Handler mH=new android.os.Handler(null);public DisplayContent display=new DisplayContent();
 public DisplayContent getDefaultDisplayContentLocked(){return display;}
 public static android.graphics.Rect bounds(){var r=new android.graphics.Rect();r.right=1080;r.bottom=2400;return r;}
}
