package io.github.baishangui.thunder;
import android.os.*;import android.graphics.Matrix;import android.view.SurfaceControl;import com.android.server.wm.*;
public class LauncherOverlayCheck {
 static void check(boolean b,String s){StatusBarFollowCheck.check(b,s);}
 static HookCallback.Param param(Object self,Object...args){var p=new HookCallback.Param();p.thisObject=self;p.args=args;return p;}
 static void frame(ma.p0 runner,xi.q tx,SurfaceControl surface,Matrix matrix)throws Throwable {
  var run=Api102Runtime.callbacks.get("ma.p0#run");var setter=Api102Runtime.callbacks.get("xi.q$b#i");
  var rp=param(runner);run.before(rp);
  try {var builder=new xi.q.b(tx,surface);builder.i(matrix);setter.after(param(builder,matrix));}finally{run.after(rp);}
 }
 public static void main(String[] args)throws Throwable {
  StatusBarFollowHook.install(LauncherOverlayCheck.class.getClassLoader());
  var wm=new WindowManagerService();StatusBarFollowCheck.wm=wm;
  StatusBarFollowCheck.call(false,"WindowManagerService","<init>",wm);
  var endpointField=StatusBarFollowHook.class.getDeclaredField("endpoint");endpointField.setAccessible(true);
  var endpoint=(IBinder)endpointField.get(null);android.content.ContentResolver.endpoint=endpoint;
  Binder.uid=3000;
  try {StatusBarOverlayIpc.call(endpoint,StatusBarOverlayIpc.CONNECT,new Binder(),-1,0);throw new AssertionError("untrusted client accepted");}catch(SecurityException expected){}
  check(true,"system endpoint rejects unrelated UID");
  Binder.uid=10234;LauncherStatusBarHook.install(LauncherOverlayCheck.class.getClassLoader());
  Api102Runtime.callbacks.get("android.app.Instrumentation#callApplicationOnCreate").before(param(new android.app.Instrumentation(),new android.app.Application()));
  var workerField=LauncherStatusBarHook.class.getDeclaredField("worker");workerField.setAccessible(true);Handler worker=(Handler)workerField.get(null);worker.drain();
  StatusBarFollowCheck.config(true,1);
  var transition=new Transition();var info=new android.window.TransitionInfo();StatusBarFollowCheck.prepare(transition,info);
  var overlay=transition.mStartTransaction.moves.get(0).parent();
  check(overlay.isValid(),"building native finish must not release the animation handle before its first frame");
  check(overlay.parent==wm.display.policy.mStatusBar.mToken.parent.surface,"overlay stays under original statusbar hierarchy above app masks");
  check(!overlay.shown,"overlay stays hidden until the real application frame");
  var clientField=LauncherStatusBarHook.class.getDeclaredField("client");clientField.setAccessible(true);var client=(Binder)clientField.get(null);
  check(!StatusBarOverlayIpc.call(endpoint,StatusBarOverlayIpc.ACQUIRE,client,99,0).containsKey("surface"),"unrelated task cannot acquire current overlay");
  var runner=new ma.p0();var tx=new xi.q();frame(runner,tx,runner.a.d,new Matrix(.1f,600,1600));
  check(tx.a.matrices.size()==2 && tx.a.matrices.containsKey(overlay),"application and statusbar matrices share the exact same transaction");
  check(java.util.Arrays.equals(tx.a.matrices.get(runner.a.d),tx.a.matrices.get(overlay)),"statusbar follows actual app scale and movement path");
  check(tx.a.shows.contains(overlay),"first transformed frame reveals overlay");
  transition.mStartTransaction.apply();tx.a.apply();worker.drain();
  StatusBarOverlayIpc.call(endpoint,StatusBarOverlayIpc.REJECT,client,42,999);
  check(wm.display.policy.mStatusBar.mToken.surface.parent==overlay,"stale generation cannot cancel a newer animation");
  var committed=StatusBarFollowHook.class.getDeclaredField("committed");committed.setAccessible(true);
  check(committed.getLong(null)==1,"server receives actual first-frame transaction commit confirmation");
  var unrelated=new xi.q();frame(runner,unrelated,new SurfaceControl(),new Matrix(.2f,50,300));
  check(unrelated.a.matrices.size()==1,"auxiliary surfaces cannot move statusbar");
  var end=new xi.q();frame(runner,end,runner.a.d,new Matrix());end.a.apply();
  check(overlay.matrix[0]==1 && overlay.matrix[4]==1 && overlay.matrix[2]==0 && overlay.matrix[5]==0,"fullscreen frame returns exact normal screen coordinates");
  transition.mFinishTransaction.apply();StatusBarFollowCheck.call(false,"Transition","finishTransition",transition,new ActionChain());
  check(overlay.parent==null && wm.display.policy.mStatusBar.mToken.surface.parent==wm.display.policy.mStatusBar.mToken.parent.surface,"native completion removes overlay and restores actual statusbar parent");
  var late=new xi.q();frame(runner,late,runner.a.d,new Matrix(.1f,500,500));late.a.apply();
  check(wm.display.policy.mStatusBar.mToken.surface.parent==wm.display.policy.mStatusBar.mToken.parent.surface,"late launcher frames cannot steal restored statusbar");
  var next=new Transition();StatusBarFollowCheck.prepare(next,new android.window.TransitionInfo());
  var bad=new ma.p0();bad.b.source.top=105;var badTx=new xi.q();frame(bad,badTx,bad.a.d,new Matrix());worker.drain();
  check(badTx.a.matrices.size()==1,"inset-local source cannot introduce final statusbar offset");
  StatusBarFollowCheck.call(false,"Transition","finishTransition",next,new ActionChain());
  var death=new Transition();StatusBarFollowCheck.prepare(death,new android.window.TransitionInfo());death.mStartTransaction.apply();
  client.die();wm.mH.drain();
  check(wm.display.policy.mStatusBar.mToken.surface.parent==wm.display.policy.mStatusBar.mToken.parent.surface,"launcher death restores statusbar immediately");
 }
}