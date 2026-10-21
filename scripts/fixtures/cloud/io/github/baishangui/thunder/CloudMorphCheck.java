package io.github.baishangui.thunder;

import android.animation.*;
import android.graphics.Outline;
import android.os.Bundle;
import android.view.*;
import com.oplus.systemui.plugins.seedling.capsule.i;
import java.util.*;

/** Real native start/mount/end stay intact; pre-draw overrides only transition visuals. */
public class CloudMorphCheck {
    static final String BASE="com.oplus.systemui.plugins.seedling.";
    static int checks;
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
    public static class Cards extends ViewGroup {
        public final List<View> cards=new ArrayList<>();public List<View> getCardViews(){return cards;}
    }
    public static class Root extends ViewGroup {
        public final Cards cards=new Cards();public final InstantContainer instant=new InstantContainer();
        public Cards getCardContainer(){return cards;}public InstantContainer getInstantContainer(){return instant;}
    }
    public static class InstantContainer extends ViewGroup {
        public com.oplus.view.ViewRootManager p=new com.oplus.view.ViewRootManager(this);
        public View content;public View getInstantView(){return content;}
    }
    public static class Window {
        public Root g=new Root();public Secondary h=new Secondary();public int hides,removes;
        public void j(boolean shown){if(!shown)hides++;g.setVisibility(shown?View.VISIBLE:View.GONE);}
        public void e(){removes++;g.attached=false;}
    }
    static void nativeHide(Window window)throws Throwable {
        var callback=Api102Runtime.callbacks.get(BASE+"card.window.i#j");check(callback!=null,"window-hide boundary exists");
        var p=CloudCheck.param(window,"native",false);callback.before(p);if(p.getResult()!=null)window.j(false);
    }
    public static class Secondary {public InstantContainer r=new InstantContainer();}
    public static class TouchRoot {public TouchHandler b=new TouchHandler();}
    public static class TouchHandler {public Object b=i.instance.c.container;}
    public static class NativeOnlyDrawable extends android.graphics.drawable.Drawable {
        boolean unsupportedWrite;
        @Override public void setColorFilter(android.graphics.ColorFilter filter){unsupportedWrite=true;throw new IllegalArgumentException("not implemented");}
    }
    enum Kind {
        CAPSULE_TO_CARD,CARD_TO_CAPSULE,CARD_TO_CAPSULE_BY_INTERRUPT,
        CAPSULE_TO_LARGE_INSTANT_CARD,CAPSULE_TO_SMALL_INSTANT_CARD,CAPSULE_TO_SMALL_INSTANT_CARD_WITH_BLANK,
        LARGE_INSTANT_CARD_TO_CAPSULE,SMALL_INSTANT_CARD_TO_CAPSULE,SMALL_INSTANT_CARD_TO_CAPSULE_WITH_BLANK,
        CARD_TO_APP,LOCKSCREEN_TO_CARD,CARD_TO_CAPSULE_WITHOUT_ANIM,
        APP_TO_CAPSULE,APP_TO_CAPSULE_WITH_NONE,CAPSULE_APPEAR,CAPSULE_WIDTH_CHANGE,CAPSULE_UPDATE_DATA_WITHOUT_ANIM,CAPSULE_DISAPPEAR,SINGLE_CAPSULE_TO_APP,MULTI_CAPSULE_TO_APP
    }
    public static class Scene {
        public Window j=new Window(),k=j;public android.content.Context a=new android.content.Context(),l=a,i=a;
        Kind kind;public Kind f(){return kind;}
        com.oplus.systemui.plugins.seedling.animatorscenes.Z condition;
    }
    static HookCallback hook(String key){var h=Api102Runtime.callbacks.get(BASE+key);check(h!=null,"Missing boundary: "+key);return h;}
    static Animator build(Scene scene,String name,Animator original)throws Throwable {
        scene.kind=Kind.valueOf(name);
        var graph=new com.oplus.systemui.plugins.seedling.engine.animatorengine.h();
        var builder=new com.oplus.systemui.plugins.seedling.animatorscenes.B0(scene,graph);scene.condition=builder.condition;
        var c=hook("animatorscenes.B0#invoke");var p=CloudCheck.param(builder,null);c.before(p);p.setResult(builder.invoke());c.after(p);
        var g=hook("engine.animatorengine.h#g");p=CloudCheck.param(p.getResult(),original,new Object());g.before(p);g.after(p);
        check(p.getResult()==original,"native animator identity and lifetime are preserved: "+name);
        check(scene.condition.d==250&&scene.condition.e==400&&!scene.condition.h,"native condition clock remains intact");
        return original;
    }
    static Animator directBuild(Scene scene,String source,String name,Animator original)throws Throwable {
        scene.kind=Kind.valueOf(name);
        var graph=new com.oplus.systemui.plugins.seedling.engine.animatorengine.h();
        var direct=CloudCheck.param(scene,graph);hook("animatorscenes."+source+"#c").after(direct);
        var p=CloudCheck.param(graph,original,new Object());hook("engine.animatorengine.h#g").after(p);
        check(p.getResult()==original,"direct entry preserves the native animator");
        return original;
    }
    static void nativeHostVisibility(boolean shown)throws Throwable {
        hook("capsule.h#w").after(CloudCheck.param(i.instance.c,null,shown));
    }
    static void nativeBusinessVisibility(boolean shown)throws Throwable {
        hook("capsule.h#y").after(CloudCheck.param(i.instance.c,null,shown,false));
    }
    static ValueAnimator clock(){check(ValueAnimator.running.size()==1,"one visual clock runs");return ValueAnimator.running.get(0);}
    static void complete(Animator nativeAnimator){for(ValueAnimator value:new ArrayList<>(ValueAnimator.running))value.end();nativeAnimator.end();}
    static CloudMorphSurface surface(){return CloudMorphSurface.created.get(CloudMorphSurface.created.size()-1);}
    static View card(int w,int h){View v=new View();v.actualWidth=w;v.actualHeight=h;return v;}
    public static void main(String[] args)throws Throwable {
        CloudHook.install(CloudMorphCheck.class.getClassLoader());
        Bundle b=new Bundle();b.putBoolean("enabled",true);b.putBoolean("cloud_enabled",true);CloudCheck.config(b);
        check(!Api102Runtime.callbacks.containsKey("com.oplus.systemui.plugins.r8.O3#a"),"native press is untouched");
        var container=i.instance.c.container;var capsule=container.capsule;
        capsule.actualWidth=240;capsule.actualHeight=80;capsule.drawableWidth=0;
        capsule.screenX=360;capsule.screenY=10;
        hook("capsule.ui.view.CapsuleContainerRoot#onTouchEvent").before(CloudCheck.param(new TouchRoot(),null,new Object()));
        // w is an outer-window gate even before the first independent Body exists.
        nativeHostVisibility(false);
        Scene entry=new Scene();
        var direct=hook("animatorscenes.e#c");entry.kind=Kind.APP_TO_CAPSULE;
        var graph=new com.oplus.systemui.plugins.seedling.engine.animatorengine.h();
        var directResult=CloudCheck.param(entry,graph);direct.after(directResult);
        check(Reflect.getAdditionalInstanceField(graph,"thunder.cloud-morph")!=null,"real app-to-capsule graph is tagged even without B0");
        entry.kind=Kind.APP_TO_CAPSULE_WITH_NONE;
        container.unbound=true;
        hook("animatorscenes.f#p").before(CloudCheck.param(entry,null,new Object(),new Object(),new Object()));
        container.unbound=false;
        hook("capsule.ui.view.CapsuleContainer#onLayout").after(CloudCheck.param(container,null,false,0,0,240,80));
        check(CloudMorphSurface.created.size()==1&&surface().opacity==0&&ValueAnimator.running.isEmpty(),"late native binding reserves a hidden entry surface instead of displaying the incoming capsule before its scene");
        Animator first=new Animator();directBuild(entry,"f","APP_TO_CAPSULE_WITH_NONE",first).start();
        check(!CloudMorphSurface.created.isEmpty(),"first app entrance must create independent capsule before any card exists");
        var entering=surface();
        check(entering.capsuleMotionOwned,"app entry owns capsule movement instead of multiplying native wrapper alpha and scale");
        check(entering.frame.width()<40&&entering.frame.height()<40&&entering.opacity==0,"first reliable native start presents the retracted dot instead of a nearly complete capsule");
        clock().advance(.3f);check(entering.frame.width()>40&&entering.frame.width()<240&&entering.opacity>0&&entering.opacity<1,"first direct entry morphs into a capsule and reveals content on the configured clock");
        clock().end();
        check(entering.capsuleMotionOwned,"early visual completion keeps native entry transforms suppressed until its graph ends");
        first.end();var initialBody=surface();
        check(!initialBody.capsuleMotionOwned,"settled entry returns native swipe transforms to the host");
        check(initialBody.attached&&!initialBody.expanded&&initialBody.frame.width()==240,"first app entrance settles as capsule");
        check(!initialBody.visible(),"entry created after w(false) must inherit the hidden host gate");
        nativeBusinessVisibility(true);
        check(!initialBody.visible()&&!initialBody.hostAllowed,"APP/close business y(true) must not override the shade host gate");
        nativeHostVisibility(true);
        check(initialBody.visible(),"w(true) releases the host gate without leaving entry alpha stuck at zero");
        nativeBusinessVisibility(false);
        check(!initialBody.visible(),"a real collapsed business hide still hides the capsule");
        nativeBusinessVisibility(true);
        check(initialBody.visible(),"business appearance restores the capsule while the host allows it");
        Animator sameSizeEntry=new Animator();directBuild(entry,"e","APP_TO_CAPSULE",sameSizeEntry).start();
        check(surface()==initialBody&&initialBody.frame.width()==240&&initialBody.opacity==1,"service replacement inherits the full presented body without resetting it");
        clock().advance(.5f);
        check(initialBody.frame.width()<239&&initialBody.frame.width()>=232.79f&&initialBody.frame.height()<80,"same-size service arrival has a bounded proportional compression");
        float pulseWidth=initialBody.frame.width();
        Animator interruptedEntry=new Animator();directBuild(entry,"h","CAPSULE_APPEAR",interruptedEntry).start();
        check(initialBody.frame.width()==pulseWidth,"entry interruption inherits the presented pulse geometry");
        complete(interruptedEntry);
        Scene unavailableEntry=new Scene();unavailableEntry.kind=Kind.APP_TO_CAPSULE;
        hook("animatorscenes.e#p").before(CloudCheck.param(unavailableEntry,null,new Object(),new Object(),new Object()));
        var noAnimatorGraph=new com.oplus.systemui.plugins.seedling.engine.animatorengine.h();
        hook("animatorscenes.e#c").after(CloudCheck.param(unavailableEntry,noAnimatorGraph));
        hook("engine.animatorengine.h#g").after(CloudCheck.param(noAnimatorGraph,null,new Object()));
        capsule.actualWidth=260;
        hook("capsule.ui.view.CapsuleContainer#onLayout").after(CloudCheck.param(container,null,false,0,0,260,80));
        check(ValueAnimator.running.size()==1,"entry without a native animator releases its reservation for later layout updates");
        clock().end();check(initialBody.frame.width()==260,"unavailable entry does not strand a stale capsule width");
        capsule.actualWidth=240;
        hook("capsule.ui.view.CapsuleContainer#onLayout").after(CloudCheck.param(container,null,false,0,0,240,80));clock().end();
        capsule.drawableWidth=24;
        Animator transientWidth=new Animator();build(entry,"CAPSULE_WIDTH_CHANGE",transientWidth).start();
        check(!initialBody.capsuleMotionOwned,"capsule update retains native horizontal switch transforms");
        complete(transientWidth);
        check(initialBody.frame.width()==240,"native animated Drawable width must not squash the stable capsule target");
        capsule.drawableWidth=0;
        Animator disappear=new Animator();build(entry,"CAPSULE_DISAPPEAR",disappear).start();clock().advance(.8f);
        check(initialBody.capsuleMotionOwned,"capsule disappearance uses one geometry and opacity timeline");
        check(initialBody.frame.width()<80&&initialBody.frame.height()<40,"business disappearance retracts the capsule geometry instead of only fading it");
        complete(disappear);
        Animator appearAgain=new Animator();build(entry,"CAPSULE_APPEAR",appearAgain).start();complete(appearAgain);initialBody=surface();
        Scene scene=new Scene();var normal=new com.oplus.systemui.plugins.seedling.card.ui.view.CardView();
        normal.actualWidth=800;normal.actualHeight=400;normal.screenX=100;normal.screenY=120;
        scene.j.g.cards.addView(normal);scene.j.g.cards.cards.add(normal);
        int[] starts={0},ends={0};Animator original=new Animator();
        original.addListener(new AnimatorListenerAdapter(){public void onAnimationStart(Animator a){starts[0]++;}public void onAnimationEnd(Animator a){ends[0]++;}});
        entry.kind=Kind.APP_TO_CAPSULE;
        hook("animatorscenes.e#p").before(CloudCheck.param(entry,null,new Object(),new Object(),new Object()));
        // Native pre-open y(false) precedes the Motion listener and must be cleared by card entry.
        nativeBusinessVisibility(false);
        build(scene,"CAPSULE_TO_CARD",original).start();var shared=surface();
        check(shared.visible()&&shared.cardTransition,"card transition clears only the pre-open business hide");
        check(shared==initialBody,"opening reuses first app entrance body");
        check(shared.frame.width()==240&&shared.attached,"independent surface starts at measured capsule geometry, including zero override");
        float last=240;
        for(float t:new float[]{.1f,.25f,.5f,.75f}) {
            clock().advance(t);float width=shared.frame.width();
            check(width>last&&width<800,"single continuous expansion");last=width;
            normal.setAlpha(1);normal.getViewTreeObserver().dispatchOnPreDraw();
            check(normal.getAlpha()==0&&surface()==shared,"native stage cannot replace unified visual object");
        }
        complete(original);
        check(shared.attached&&shared.expanded&&normal.getAlpha()==0&&container.getAlpha()==0,"expanded endpoint stays in unified surface");
        check(shared.inputOwnership.captureOutside(),"expanded card captures outside dismissal");
        check(shared.inputOwnership.acceptsInput(),"expanded card accepts content input");
        nativeBusinessVisibility(false);
        check(shared.visible(),"native capsule hide at the end of expansion cannot hide the displayed card");
        nativeHostVisibility(false);nativeBusinessVisibility(true);
        check(!shared.visible(),"expanded business show cannot bypass an outer host hide");
        nativeHostVisibility(true);
        check(shared.visible(),"expanded card recovers after the host gate opens");
        check(starts[0]==1&&ends[0]==1,"host callbacks run exactly once");
        Animator close=new Animator();close.addListener(new AnimatorListenerAdapter(){public void onAnimationEnd(Animator a){try{nativeHide(scene.j);}catch(Throwable e){throw new RuntimeException(e);}}});
        build(scene,"CARD_TO_CAPSULE",close).start();
        check(surface()==shared&&shared.frame.width()==800,"close reuses the identical persistent object");
        check(!shared.inputOwnership.captureOutside(),"close start immediately releases outside capture before any visual contraction");
        check(!shared.inputOwnership.acceptsInput(),"close start releases the entire Body input region immediately");
        clock().advance(.5f);close.end();
        check(!shared.inputOwnership.captureOutside(),"closing animation cannot reacquire outside capture");
        check(!shared.inputOwnership.acceptsInput(),"closing Body input cannot return during contraction");
        check(scene.j.hides==0&&shared.frame.width()>240,"native remove waits for visual contraction");
        clock().end();
        check(shared.attached&&!shared.expanded&&shared.frame.width()==240,"same object remains as capsule after close");
        check(container.getAlpha()==0&&normal.getAlpha()==0,"no endpoint alpha handoff to either native object");
        check(scene.j.hides==1,"native hide released once");
        scene.j.g.setVisibility(View.VISIBLE);
        Animator delayedOpen=new Animator();build(scene,"CAPSULE_TO_CARD",delayedOpen).start();complete(delayedOpen);
        check(shared.inputOwnership.captureOutside(),"reopening restores outside dismissal capture");
        check(shared.inputOwnership.acceptsInput(),"reopening restores content input");
        int hidesBeforeDelayed=scene.j.hides;
        Animator delayedClose=new Animator();
        delayedClose.addListener(new AnimatorListenerAdapter(){public void onAnimationEnd(Animator a){try{nativeHide(scene.j);}catch(Throwable e){throw new RuntimeException(e);}}});
        build(scene,"CARD_TO_CAPSULE",delayedClose).start();
        check(!shared.inputOwnership.captureOutside(),"delayed native close releases outside capture at scene start");
        check(!shared.inputOwnership.acceptsInput(),"delayed native close releases Body content input at scene start");
        check(shared.inputOwnership.keepLease(true),"closing keeps the transparent native window disabled");
        clock().advance(.35f);
        check(!shared.inputOwnership.captureOutside(),"intermediate close geometry cannot capture the display");
        check(!shared.inputOwnership.acceptsInput(),"intermediate close geometry remains touch transparent");
        clock().end();
        check(!shared.inputOwnership.captureOutside(),"visual close endpoint does not wait for native end to release outside capture");
        check(shared.inputOwnership.acceptsInput()&&!shared.expanded,"visual close endpoint restores capsule clicks while native end is delayed");
        check(shared.inputOwnership.keepLease(true)&&scene.j.hides==hidesBeforeDelayed,"native input lease survives delayed native cleanup");
        delayedClose.end();
        check(scene.j.hides==hidesBeforeDelayed+1&&!shared.inputOwnership.captureOutside(),"delayed native end preserves released outside capture and hides once");
        check(!shared.inputOwnership.keepLease(false),"native hidden state releases the remaining lease");
        capsule.actualWidth=244;
        hook("capsule.ui.view.CapsuleContainer#onLayout").after(CloudCheck.param(container,null,false,0,0,244,80));
        check(ValueAnimator.running.size()==1,"a newer card scene clears an older pending entry on the same container");clock().end();
        capsule.actualWidth=240;
        hook("capsule.ui.view.CapsuleContainer#onLayout").after(CloudCheck.param(container,null,false,0,0,240,80));clock().end();
        scene.j.g.setVisibility(View.VISIBLE);
        Animator forward=new Animator();build(scene,"CAPSULE_TO_CARD",forward).start();clock().advance(.4f);float interrupted=shared.frame.width();forward.cancel();
        check(!ValueAnimator.running.isEmpty(),"native graph cancellation must not freeze the unified body mid-morph");
                Animator reverse=new Animator();build(scene,"CARD_TO_CAPSULE_BY_INTERRUPT",reverse).start();
        check(surface()==shared&&shared.frame.width()==interrupted,"interruption reverses from actual visual bounds");complete(reverse);
        Animator lateClose=new Animator();build(scene,"CARD_TO_CAPSULE",lateClose).start();clock().advance(.25f);
        Animator reopened=new Animator();build(scene,"CAPSULE_TO_CARD",reopened).start();int hiddenBefore=scene.j.hides;
        check(shared.inputOwnership.captureOutside(),"reversal to opening restores outside capture immediately");
        check(shared.inputOwnership.acceptsInput(),"reversal to opening restores Body content input immediately");
        lateClose.addListener(new AnimatorListenerAdapter(){public void onAnimationEnd(Animator a){try{nativeHide(scene.j);}catch(Throwable e){throw new RuntimeException(e);}}});
        lateClose.end();check(scene.j.hides==hiddenBefore,"late native close cannot hide newly opening card");complete(reopened);

        for(String kind:new String[]{"CAPSULE_TO_LARGE_INSTANT_CARD","CAPSULE_TO_SMALL_INSTANT_CARD","CAPSULE_TO_SMALL_INSTANT_CARD_WITH_BLANK","LARGE_INSTANT_CARD_TO_CAPSULE","SMALL_INSTANT_CARD_TO_CAPSULE","SMALL_INSTANT_CARD_TO_CAPSULE_WITH_BLANK"}) {
            Scene s=new Scene();s.j.g.instant.content=card(700,300);s.j.h.r.content=card(700,300);
            Animator n=new Animator();build(s,kind,n).start();clock().advance(.5f);complete(n);
            check(surface()==shared&&shared.attached,"all status bar scenes retain one body: "+kind);
        }
        for(String kind:new String[]{"CARD_TO_APP","LOCKSCREEN_TO_CARD","CARD_TO_CAPSULE_WITHOUT_ANIM"}) {
            Animator n=new Animator();build(scene,kind,n).start();check(ValueAnimator.running.isEmpty(),"unrelated native scene unchanged: "+kind);complete(n);
        }
        Animator update=new Animator();build(scene,"CAPSULE_APPEAR",update).start();complete(update);shared=surface();
        capsule.actualWidth=300;
        hook("capsule.ui.view.CapsuleContainer#onLayout").after(CloudCheck.param(container,null,false,0,0,300,80));
        check(surface()==shared&&ValueAnimator.running.size()==1,"capsule width updates keep the same body and create a visual trajectory");
        clock().end();check(shared.frame.width()==300,"layout-only update reaches new capsule width");
        for(String kind:new String[]{"APP_TO_CAPSULE","CAPSULE_WIDTH_CHANGE","CAPSULE_UPDATE_DATA_WITHOUT_ANIM"}){
            Animator n=new Animator();build(scene,kind,n).start();complete(n);check(surface()==shared,"capsule lifecycle retains body: "+kind);
        }
        Animator delayedWidth=new Animator();build(scene,"CAPSULE_WIDTH_CHANGE",delayedWidth).start();clock().advance(.2f);capsule.actualWidth=340;
        hook("capsule.ui.view.CapsuleContainer#onLayout").after(CloudCheck.param(container,null,false,0,0,340,80));
        complete(delayedWidth);check(shared.frame.width()==340,"layout arriving during a width scene retargets final bounds");
        Animator leave=new Animator();build(scene,"MULTI_CAPSULE_TO_APP",leave).start();check(shared.disposed,"multi capsule app launch releases unified overlay");complete(leave);
        Scene missing=new Scene();Animator unavailable=new Animator();build(missing,"CAPSULE_TO_CARD",unavailable).start();unavailable.end();
        nativeHide(missing.j);check(missing.j.hides==1,"failed preparation must not block later native window hide");
        Animator disabled=new Animator();build(scene,"CAPSULE_TO_CARD",disabled).start();shared=surface();b.putBoolean("cloud_enabled",false);CloudCheck.config(b);CloudHook.refresh();
        check(shared.disposed&&!shared.attached&&container.getAlpha()==1,"disable removes floating object and restores native sources");complete(disabled);
        nativeHostVisibility(false);
        b.putBoolean("cloud_enabled",true);CloudCheck.config(b);
        Animator enabledEntry=new Animator();directBuild(entry,"h","CAPSULE_APPEAR",enabledEntry).start();complete(enabledEntry);
        check(!surface().visible(),"w(false) observed while disabled survives re-enabling and late Body creation");
        nativeBusinessVisibility(true);check(!surface().visible(),"re-enabled business show still respects the cached outer host gate");
        nativeHostVisibility(true);check(surface().visible(),"re-enabled session recovers on the real outer host show");
        System.out.println("Verified "+checks+" unified-object lifecycle checks (platform rendering verified separately on device).");
    }
}
