package com.android.server.wm;
import android.view.SurfaceControl;import android.graphics.Rect;
public class WindowToken {public SurfaceControl surface=new SurfaceControl();public WindowToken parent, mParent;public int mLastLayer=17;public SurfaceControl mLastRelativeToLayer;public SurfaceAnimator mSurfaceAnimator=new SurfaceAnimator();
 public SurfaceControl getSurfaceControl(){return surface;}public WindowToken getParent(){return parent;}
 public SurfaceControl getAnimationLeash(){return null;}public Rect getBounds(){return WindowManagerService.bounds();}
}
