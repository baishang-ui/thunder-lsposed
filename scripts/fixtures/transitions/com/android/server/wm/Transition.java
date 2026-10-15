package com.android.server.wm;
import android.view.SurfaceControl;import android.window.TransitionInfo;
public class Transition {
 public SurfaceControl.Transaction mStartTransaction=new SurfaceControl.Transaction(),mFinishTransaction=new SurfaceControl.Transaction();
 public void buildFinishTransaction(SurfaceControl.Transaction t,TransitionInfo info,DisplayContent[] d){}
 public static void buildCleanupTransaction(SurfaceControl.Transaction t,TransitionInfo info){}
 public void onTransactionReady(int i,SurfaceControl.Transaction t){}
 public void finishTransition(ActionChain c){}public void abort(){}public void cleanUpOnFailure(){}
}
