package io.github.baishangui.thunder;
import android.os.*;
import android.view.SurfaceControl;
import com.android.server.wm.*;
import java.util.*;

/** Executes the production adapter against the observed firmware signatures. */
public class StatusBarFollowCheck {
    static void check(boolean yes,String message) {
        if(!yes)throw new AssertionError(message);
        System.out.println("PASS: "+message);
    }
    static HookCallback.Param call(boolean before,String type,String method,Object self,Object...args)throws Throwable {
        HookCallback.Param p=new HookCallback.Param();p.thisObject=self;p.args=args;
        HookCallback h=Api102Runtime.callbacks.get("com.android.server.wm."+type+"#"+method);
        if(h==null)throw new AssertionError("Missing hook "+type+"#"+method);
        if(before)h.before(p);else h.after(p);return p;
    }
    static void config(boolean enabled,long revision) throws Exception {
        var method=StatusBarFollowHook.class.getDeclaredMethod("settingsLoaded",Map.class);
        method.setAccessible(true);
        method.invoke(null,Map.of("enabled",true,"status_bar_follow_app",enabled,"revision",revision));
        wm.mH.drain();
    }
    static WindowManagerService wm;
    static android.window.TransitionInfo info(){return new android.window.TransitionInfo();}
    static void prepare(Transition t,android.window.TransitionInfo info)throws Throwable {
        call(false,"Transition","buildFinishTransaction",t,t.mFinishTransaction,info,new DisplayContent[]{wm.display});
    }
    static SurfaceControl bar(){return wm.display.policy.mStatusBar.mToken.surface;}
    static Object parent(){return wm.display.policy.mStatusBar.mToken.parent.surface;}
    public static void main(String[] args)throws Throwable {
        StatusBarFollowHook.install(StatusBarFollowCheck.class.getClassLoader());
        wm=new WindowManagerService();call(false,"WindowManagerService","<init>",wm);
        Transition off=new Transition();prepare(off,info());
        check(off.mStartTransaction.moves.isEmpty(),"default disabled leaves native transactions untouched");
        config(true,1);
        Transition disconnected=new Transition();prepare(disconnected,info());
        check(disconnected.mStartTransaction.moves.isEmpty(),"missing launcher connection preserves native statusbar");
        var endpoint=StatusBarFollowHook.class.getDeclaredField("endpoint");endpoint.setAccessible(true);
        android.content.ContentResolver.endpoint=(IBinder)endpoint.get(null);
        Binder.uid=10234;
        StatusBarOverlayIpc.call(android.content.ContentResolver.endpoint,StatusBarOverlayIpc.CONNECT,new Binder(),-1,0);
        Transition t=new Transition();var i=info();prepare(t,i);
        check(t.mStartTransaction.moves.size()==1 && t.mStartTransaction.moves.get(0).parent()!=i.changes.get(0).leash,"statusbar has an independent parent outside the application mask");
        check(t.mFinishTransaction.moves.get(0).parent()==parent(),"native finish already contains original parent restore");
        SurfaceControl.Transaction cleanup=new SurfaceControl.Transaction();
        call(false,"Transition","buildCleanupTransaction",null,cleanup,i);
        check(cleanup.moves.get(0).parent()==parent(),"native cleanup restores the statusbar too");
        HookCallback.Param fade=call(true,"FadeAnimationController","fadeWindowToken",new FadeAnimationController(),false,wm.display.policy.mStatusBar.mToken,64);
        check(Boolean.TRUE.equals(fade.getObjectExtra("replaced")),"only the owned statusbar fade is suppressed");
        HookCallback.Param unrelated=call(true,"FadeAnimationController","fadeWindowToken",new FadeAnimationController(),false,new WindowToken(),64);
        check(unrelated.getObjectExtra("replaced")==null,"other system windows retain native fade behavior");
        t.mStartTransaction.apply();
        check(bar().layer==Integer.MAX_VALUE,"statusbar is above content inside the independent overlay");
        config(false,2);
        check(bar().layer==17,"live restore also returns the original surface layer");
        check(bar().parent==parent(),"live disable immediately restores actual surface parent");
        call(false,"Transition","finishTransition",t,new ActionChain());
        config(true,3);
        Transition skipped=new Transition();var landscape=info();landscape.changes.get(0).end.bottom=500;prepare(skipped,landscape);
        check(skipped.mStartTransaction.moves.isEmpty(),"nonfullscreen or rotating geometry is skipped");
        var reasonField=StatusBarFollowHook.class.getDeclaredField("reason");reasonField.setAccessible(true);
        Object skippedReason=reasonField.get(null);
        call(false,"Transition","finishTransition",skipped,new ActionChain());
        check(skippedReason.equals(reasonField.get(null)),"unowned native completion preserves the diagnostic skip reason");
        wm.display.policy.mStatusBar.mToken.mSurfaceAnimator.leashed=true;
        Transition fading=new Transition();prepare(fading,info());
        check(fading.mStartTransaction.moves.isEmpty(),"existing WindowToken SurfaceAnimator ownership is preserved");
        wm.display.policy.mStatusBar.mToken.mSurfaceAnimator.leashed=false;
        wm.display.policy.mStatusBar.mSurfaceAnimator.animationType=16;
        Transition windowAnim=new Transition();prepare(windowAnim,info());
        check(windowAnim.mStartTransaction.moves.isEmpty(),"a real WindowState animation still prevents takeover");
        wm.display.policy.mStatusBar.mSurfaceAnimator.animationType=32;
        Transition returnHome=new Transition();var returnInfo=info();
        returnInfo.type=1021;returnInfo.flags=128;
        returnInfo.changes.get(0).mode=4;returnInfo.changes.get(1).mode=3;
        prepare(returnHome,returnInfo);
        check(returnHome.mStartTransaction.moves.size()==1,"captured ColorOS recents-to-home transition follows the outgoing fullscreen app");
        call(false,"Transition","finishTransition",returnHome,new ActionChain());
        Transition unknownCustom=new Transition();var unknownInfo=info();unknownInfo.type=1022;unknownInfo.flags=128;
        prepare(unknownCustom,unknownInfo);
        check(unknownCustom.mStartTransaction.moves.isEmpty(),"other custom transitions retain native behavior");
        wm.display.policy.keyguard=true;Transition locked=new Transition();prepare(locked,info());
        check(locked.mStartTransaction.moves.isEmpty(),"keyguard does not attach statusbar");wm.display.policy.keyguard=false;
        Transition first=new Transition();prepare(first,info());first.mStartTransaction.apply();
        Transition second=new Transition();prepare(second,info());
        check(second.mStartTransaction.moves.isEmpty() && bar().parent==parent(),"overlapping transitions restore and retain ownership barrier");
        call(false,"Transition","finishTransition",first,new ActionChain());
        Transition next=new Transition();prepare(next,info());next.mStartTransaction.apply();
        wm.display.policy.keyguard=true;call(false,"DisplayPolicy","finishPostLayoutPolicyLw",wm.display.policy);
        check(bar().parent==parent(),"keyguard appearing mid-animation restores statusbar");wm.display.policy.keyguard=false;
        call(false,"Transition","cleanUpOnFailure",next);
        Transition early=new Transition();var earlyInfo=info();
        var reservation=call(true,"LiteOSAnimController","startAsyncFadeAnimIfNeed",new LiteOSAnimController(),early,early.mStartTransaction,earlyInfo);
        var reservedFade=call(true,"FadeAnimationController","fadeWindowToken",new FadeAnimationController(),false,wm.display.policy.mStatusBar.mToken,64);
        check(Boolean.TRUE.equals(reservedFade.getObjectExtra("replaced")),"native pre-start fade is suppressed only after scene validation");
        Api102Runtime.callbacks.get("com.android.server.wm.LiteOSAnimController#startAsyncFadeAnimIfNeed").after(reservation);
        prepare(early,earlyInfo);early.mStartTransaction.apply();wm.mH.drainDelayed();
        check(bar().parent==parent(),"missing first launcher frame restores hidden statusbar");
        check(((String)reasonField.get(null)).contains("500ms"),"first-frame deadline restores before the six-second watchdog");
        call(false,"Transition","finishTransition",early,new ActionChain());
        System.out.println("Verified actual system_server hook registration, guards, transactions and recovery.");
    }
}
