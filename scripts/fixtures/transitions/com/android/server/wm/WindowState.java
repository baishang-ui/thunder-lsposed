package com.android.server.wm;
public class WindowState {public WindowToken mToken=new WindowToken();public boolean visible=true;
 public SurfaceAnimator mSurfaceAnimator=new SurfaceAnimator();
 private final android.view.SurfaceControl insetsLeash=new android.view.SurfaceControl();
 public WindowState(){mToken.parent=new WindowToken();mToken.mParent=mToken.parent;mToken.surface.parent=mToken.parent.surface;
  mSurfaceAnimator.leashed=true;mSurfaceAnimator.animationType=32;}
 public boolean isVisible(){return visible;}public Object getAnimationLeash(){return mSurfaceAnimator.leashed?insetsLeash:null;}
}
