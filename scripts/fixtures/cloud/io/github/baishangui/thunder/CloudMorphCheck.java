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
        APP_TO_CAPSULE,APP_TO_CAPSULE_WITH_NONE,CAPSULE_APPEAR,CAPSULE_SEIZE,CAPSULE_BUBBLE_SWITCH,CAPSULE_WIDTH_CHANGE,CAPSULE_UPDATE_DATA_WITHOUT_ANIM,CAPSULE_DISAPPEAR,SINGLE_CAPSULE_TO_APP,MULTI_CAPSULE_TO_APP
    }
    public static class Scene {
        public Window j=new Window(),k=j;public android.content.Context a=new android.content.Context(),l=a,i=a;
        Kind kind;public Kind f(){return kind;}Object state;
        com.oplus.systemui.plugins.seedling.animatorscenes.Z condition;
    }
    static HookCallback hook(String key){var h=Api102Runtime.callbacks.get(BASE+key);check(h!=null,"Missing boundary: "+key);return h;}
    static Animator build(Scene scene,String name,Animator original)throws Throwable {
        scene.kind=Kind.valueOf(name);
        var graph=new com.oplus.systemui.plugins.seedling.engine.animatorengine.h();
        var builder=new com.oplus.systemui.plugins.seedling.animatorscenes.B0(scene,graph);scene.condition=builder.condition;
        builder.d=scene.state;
        var c=hook("animatorscenes.B0#invoke");var p=CloudCheck.param(builder,null);c.before(p);p.setResult(builder.invoke());c.after(p);
        var g=hook("engine.animatorengine.h#g");p=CloudCheck.param(p.getResult(),original,new Object());g.before(p);g.after(p);
        check(p.getResult()==original,"native animator identity and lifetime are preserved: "+name);
        check(scene.condition.d==250&&scene.condition.e==400&&!scene.condition.h,"native condition clock remains intact");
        return original;
    }
    static Animator directBuild(Scene scene,String source,String name,Animator original)throws Throwable {
        scene.kind=Kind.valueOf(name);
        var graph=new com.oplus.systemui.plugins.seedling.engine.animatorengine.h();
        var direct=CloudCheck.param(scene,graph,new Object(),scene.state);hook("animatorscenes."+source+"#c").after(direct);
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
    static CloudMorphHook.Frame movingGate(CloudMorphSurface body){
        float dt=.0001f;var before=body.presentation();var after=CloudVisibilityState.presentation(body.frame,body.hostAmount-dt);
        return new CloudMorphHook.Frame((after.x()-before.x())/dt,(after.y()-before.y())/dt,(after.width()-before.width())/dt,
            (after.height()-before.height())/dt,(after.radius()-before.radius())/dt,0);
    }
    static void checkInitialWidthVelocity(CloudMorphSurface body,float startWidth,float expected,String message){
        var animator=clock();float fraction=.0001f;animator.advance(fraction);
        float actual=(body.frame.width()-startWidth)/(fraction*animator.duration/1000f);
        check(Math.abs(actual-expected)<Math.max(8,Math.abs(expected)*.08f),message+" expected="+expected+" actual="+actual);
    }
    static void verifyWrappedGraphOwnership(Scene scene)throws Throwable{
        scene.kind=Kind.APP_TO_CAPSULE;
        var inner=new com.oplus.systemui.plugins.seedling.engine.animatorengine.h();
        hook("animatorscenes.e#c").after(CloudCheck.param(scene,inner,new Object(),scene.state));
        var builder=new com.oplus.systemui.plugins.seedling.animatorscenes.B0(scene,inner);builder.d=scene.state;
        var build=CloudCheck.param(builder,null);hook("animatorscenes.B0#invoke").before(build);build.setResult(builder.invoke());hook("animatorscenes.B0#invoke").after(build);
        Animator innerAnimator=new Animator(),outerAnimator=new Animator();
        hook("engine.animatorengine.h#g").after(CloudCheck.param(inner,innerAnimator,new Object()));
        hook("engine.animatorengine.h#g").after(CloudCheck.param(build.getResult(),outerAnimator,new Object()));
        outerAnimator.start();var visual=clock();visual.advance(.25f);var before=surface().frame;
        innerAnimator.start();
        check(clock()==visual&&surface().frame.equals(before),"recursive B0/direct native starts cannot compete for the same body or restart its visual clock");
        check(innerAnimator.getListeners().isEmpty()&&outerAnimator.getListeners().size()==1,"one outer native lifecycle owns a wrapped capsule graph");
        complete(outerAnimator);innerAnimator.end();
    }
    @SuppressWarnings("unchecked")
    static void verifyWidthOnlyHide(Scene entry,CloudMorphSurface body,Bundle config)throws Throwable{
        var field=CloudMorphHook.class.getDeclaredField("velocities");field.setAccessible(true);
        var velocities=(Map<View,CloudMorphHook.Frame>)field.get(null);
        for(int curve=0;curve<4;curve++){
            config.putInt("cloud_animation_curve",curve);CloudCheck.config(config);
            var source=body.frame;
            // An interrupted entry can still be changing height and position.
            // The new width-only exit must keep its own source top/height/center.
            velocities.put(body.container,new CloudMorphHook.Frame(12,30,-120,70,5,0));
            Animator hide=new Animator();build(entry,"CAPSULE_DISAPPEAR",hide).start();
            for(float fraction:new float[]{.0001f,.1f,.35f,.7f,.95f}){
                clock().advance(fraction);
                check(body.frame.height()==source.height()&&body.frame.y()==source.y(),"width-only hide cannot inherit entry height or vertical drift at curve "+curve);
                check(Math.abs(body.frame.x()+body.frame.width()/2-source.x()-source.width()/2)<.001f,"width-only hide keeps the starting horizontal center at curve "+curve);
            }
            Animator replacement=new Animator();directBuild(entry,"e","APP_TO_CAPSULE",replacement).start();complete(replacement);hide.end();
        }
        config.putInt("cloud_animation_curve",0);CloudCheck.config(config);
    }
    @SuppressWarnings("unchecked")
    static CloudMorphHook.Frame logicalVelocity(CloudMorphSurface body)throws Exception{
        var field=CloudMorphHook.class.getDeclaredField("velocities");field.setAccessible(true);
        return ((Map<View,CloudMorphHook.Frame>)field.get(null)).get(body.container);
    }
    static float[] components(CloudMorphHook.Frame frame){return new float[]{frame.x(),frame.y(),frame.width(),frame.height(),frame.radius()};}
    static void checkFrame(CloudMorphHook.Frame expected,CloudMorphHook.Frame actual,float tolerance,String message){
        var left=components(expected);var right=components(actual);
        for(int i=0;i<left.length;i++)check(Math.abs(left[i]-right[i])<=tolerance,message+" component="+i+" expected="+left[i]+" actual="+right[i]);
    }
    static void checkPresentationVelocity(CloudMorphHook.Frame before,CloudMorphHook.Frame after,CloudMorphHook.Frame expected,float seconds,String message){
        var first=components(before);var next=components(after);var speed=components(expected);
        for(int i=0;i<first.length;i++){
            float actual=(next[i]-first[i])/seconds;
            check(Math.abs(actual-speed[i])<Math.max(8,Math.abs(speed[i])*.08f),message+" component="+i+" expected="+speed[i]+" actual="+actual);
        }
    }
    static void verifyClosedHostHandoff(Scene entry,CloudMorphSurface body,Bundle config)throws Throwable{
        for(int curve=0;curve<4;curve++)for(boolean invisible:new boolean[]{false,true}){
            config.putInt("cloud_animation_curve",curve);config.putBoolean("cloud_corner_enabled",true);config.putInt("cloud_corner",0);CloudCheck.config(config);
            body.simulateHostGate=false;body.hostAllowed=true;body.businessAllowed=true;body.hostAmount=1;body.hostOpacityLimit=1;body.hostGate.host(1);
            Animator appear=new Animator();build(entry,"CAPSULE_APPEAR",appear).start();complete(appear);
            Animator hide=new Animator();build(entry,"CAPSULE_DISAPPEAR",hide).start();clock().advance(.55f);
            if(invisible)body.opacity(0);
            body.simulateHostGate=true;nativeHostVisibility(false);body.sampleHost(.4f);
            var before=body.presentation();var speed=logicalVelocity(body);float alpha=body.presentationOpacity();
            Animator incoming=new Animator();directBuild(entry,"e","APP_TO_CAPSULE",incoming).start();
            checkFrame(before,body.presentation(),.0001f,"hide→host(false)→entry preserves the actual shape at curve "+curve);
            check(Math.abs(body.presentationOpacity()-alpha)<.0001f,"closed-host entry preserves brightness without a second alpha gate at curve "+curve);
            check(!body.hostAllowed&&body.hostAmount==1,"closed-host entry rebases its snapshot while retaining the real host(false) decision");
            float epsilon=.0001f;var visualClock=clock();visualClock.advance(epsilon);body.sampleHost(epsilon);
            checkPresentationVelocity(before,body.presentation(),speed,epsilon*visualClock.duration/1000f,"closed-host entry inherits the visible tangent at curve "+curve);
            for(float fraction:new float[]{.05f,.25f,.5f,.8f,1}){
                visualClock.advance(fraction);body.sampleHost(fraction);
                if(invisible)check(body.presentationOpacity()==0,"an invisible old hide cannot reappear under a closed host at curve "+curve);
            }
            incoming.end();hide.end();
            check(!body.hostAllowed&&body.hostAmount==0&&body.presentationOpacity()==0,"entry completion cannot reopen the control-center host gate at curve "+curve);
            if(invisible){
                body.sampleHost(1);nativeHostVisibility(true);float reopenedAlpha=body.presentationOpacity();
                check(reopenedAlpha==0,"host(true) starts from the closed handoff brightness at curve "+curve);
                body.sampleHost(.0001f);check(body.presentationOpacity()<.001f,"host(true) reveals a capped entry continuously at curve "+curve);
                body.sampleHost(1);check(body.presentationOpacity()==1,"a genuine host show eventually restores the new entry");
            }
        }
        for(int curve=0;curve<4;curve++){
            config.putInt("cloud_animation_curve",curve);CloudCheck.config(config);
            body.simulateHostGate=false;body.hostAllowed=true;body.businessAllowed=true;body.hostAmount=1;body.hostOpacityLimit=1;body.hostGate.host(1);
            Animator appear=new Animator();build(entry,"CAPSULE_APPEAR",appear).start();complete(appear);
            Animator hide=new Animator();build(entry,"CAPSULE_DISAPPEAR",hide).start();clock().advance(.35f);body.opacity(.05f);
            body.simulateHostGate=true;nativeHostVisibility(false);body.sampleHost(.4f);
            Animator incoming=new Animator();directBuild(entry,"e","APP_TO_CAPSULE",incoming).start();
            var visualClock=clock();visualClock.advance(.1f);body.sampleHost(.1f);float alpha=body.presentationOpacity();
            check(alpha>0&&alpha<=.05f,"closed entry retains the old dim body while its new raw opacity grows");
            nativeHostVisibility(true);
            check(Math.abs(body.presentationOpacity()-alpha)<.0001f,"host(true) cannot instantly discard a closed-handoff alpha cap at curve "+curve);
            body.sampleHost(.0001f);
            check(Math.abs(body.presentationOpacity()-alpha)<.001f,"host(true) begins revealing the closed-handoff cap continuously at curve "+curve);
            body.sampleHost(1);complete(incoming);hide.end();
            check(body.hostAllowed&&body.presentationOpacity()==1,"real host reveal restores full brightness after the closed entry");
        }
        config.putInt("cloud_animation_curve",3);config.putBoolean("cloud_corner_enabled",false);CloudCheck.config(config);
        body.simulateHostGate=false;body.hostAllowed=true;body.businessAllowed=true;body.hostAmount=1;body.hostOpacityLimit=1;body.hostGate.host(1);
        body.frame(new CloudMorphHook.Frame(440,10,80,80,40,0));
        Animator miniHide=new Animator();build(entry,"CAPSULE_DISAPPEAR",miniHide).start();
        body.simulateHostGate=true;nativeHostVisibility(false);
        var miniStart=body.presentation();var miniSpeed=logicalVelocity(body);
        Animator miniEntry=new Animator();directBuild(entry,"e","APP_TO_CAPSULE",miniEntry).start();
        checkFrame(miniStart,body.presentation(),.0001f,"same-frame mini hide→closed-host→wide entry preserves its square source");
        var zeroSpeed=logicalVelocity(body);var nativeCapsule=i.instance.c.container.capsule;int oldTargetWidth=nativeCapsule.targetWidth;
        nativeCapsule.targetWidth=260;
        hook("capsule.ui.view.CapsuleContainer#onLayout").after(CloudCheck.param(body.container,null,false,0,0,240,80));
        checkFrame(zeroSpeed,logicalVelocity(body),.0001f,"measurement retarget retains the closed-handoff zero logical speed");
        var miniClock=clock();miniClock.advance(.0001f);body.sampleHost(.0001f);
        checkPresentationVelocity(miniStart,body.presentation(),miniSpeed,.0001f*miniClock.duration/1000f,
            "linear mini handoff keeps an explicitly cancelled zero logical speed instead of restoring the default slope");
        miniClock.end();body.sampleHost(1);miniEntry.end();miniHide.end();
        nativeCapsule.targetWidth=oldTargetWidth;
        config.putInt("cloud_animation_curve",0);config.putBoolean("cloud_corner_enabled",false);CloudCheck.config(config);
        body.simulateHostGate=false;body.hostAllowed=true;body.businessAllowed=true;body.hostAmount=1;body.hostOpacityLimit=1;body.hostGate.host(1);
        Animator restore=new Animator();build(entry,"CAPSULE_APPEAR",restore).start();complete(restore);
        hook("capsule.ui.view.CapsuleContainer#onLayout").after(CloudCheck.param(body.container,null,false,0,0,240,80));
    }
    public static void main(String[] args)throws Throwable {
        CloudHook.install(CloudMorphCheck.class.getClassLoader());
        Bundle b=new Bundle();b.putBoolean("enabled",true);b.putBoolean("cloud_enabled",true);CloudCheck.config(b);
        check(!Api102Runtime.callbacks.containsKey("com.oplus.systemui.plugins.r8.O3#a"),"native press is untouched");
        var container=i.instance.c.container;var capsule=container.capsule;
        container.screenX=0;container.screenY=0;container.measuredWidth=960;container.measuredHeight=100;
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
        verifyWrappedGraphOwnership(entry);
        Animator sameSizeEntry=new Animator();directBuild(entry,"e","APP_TO_CAPSULE",sameSizeEntry).start();
        check(surface()==initialBody&&initialBody.frame.width()==240&&initialBody.opacity==1,"service replacement inherits the full presented body without resetting it");
        clock().advance(.5f);
        check(initialBody.frame.width()==240&&initialBody.frame.height()==80,"same-size service replacement preserves the current silhouette without an artificial compression pulse");
        float pulseWidth=initialBody.frame.width();
        Animator interruptedEntry=new Animator();directBuild(entry,"h","CAPSULE_APPEAR",interruptedEntry).start();
        check(initialBody.frame.width()==pulseWidth,"entry interruption inherits the presented pulse geometry");
        complete(interruptedEntry);
        // Native p() keeps the current key until its callback. The incoming view
        // can also retain its previous layout while measurement is already final.
        var incoming=new i.Capsule();incoming.actualWidth=120;incoming.actualHeight=80;
        incoming.targetWidth=360;incoming.targetHeight=80;incoming.screenX=420;incoming.screenY=10;
        container.incoming.put("incoming",incoming);
        entry.state=new com.oplus.systemui.plugins.seedling.statemachine.fluid.d("incoming");
        entry.kind=Kind.APP_TO_CAPSULE;
        hook("animatorscenes.e#p").before(CloudCheck.param(entry,null,new Object(),entry.state,new Object()));
        nativeBusinessVisibility(false);
        hook("capsule.ui.view.CapsuleContainer#onLayout").after(CloudCheck.param(container,null,false,0,0,240,80));
        check(initialBody.capsule==capsule&&initialBody.frame.width()==240&&initialBody.opacity==1,"replacement reservation preserves the currently presented capsule until native handoff");
        check(initialBody.visible(),"replacement pre-hide cannot close the existing body business gate before native entry starts");
        Animator replacement=new Animator();directBuild(entry,"e","APP_TO_CAPSULE",replacement).start();
        check(initialBody.capsule==incoming&&initialBody.frame.width()==240&&initialBody.opacity==1,"incoming service inherits the existing visible body at native start while current key is still old");
        clock().advance(.45f);
        check(initialBody.frame.width()>240&&initialBody.frame.width()<360&&initialBody.opacity==1,"existing capsule begins its continuous measured-size transition with the incoming content");
        float replacementMid=initialBody.frame.width();
        hook("capsule.ui.view.CapsuleContainer#onLayout").after(CloudCheck.param(container,null,false,0,0,240,80));
        check(initialBody.frame.width()==replacementMid&&initialBody.capsule==incoming,"old container key and stale incoming layout cannot retarget the new service back to the previous width");
        complete(replacement);check(initialBody.frame.width()==360,"service entry reaches incoming measured width without waiting for current key or layout width to change");
        check(initialBody.frame.x()==300,"incoming measurement stays centered on the native capsule anchor");
        hook("capsule.ui.view.CapsuleContainer#onLayout").after(CloudCheck.param(container,null,false,0,0,240,80));
        check(initialBody.frame.width()==360&&ValueAnimator.running.isEmpty(),"native-end old-key layout cannot send a completed incoming service back to the previous service size");
        Animator measuredRetarget=new Animator();directBuild(entry,"e","APP_TO_CAPSULE",measuredRetarget).start();
        var retargetClock=clock();retargetClock.advance(.4f);var beforeRetarget=initialBody.frame;
        incoming.targetWidth=380;
        hook("capsule.ui.view.CapsuleContainer#onLayout").after(CloudCheck.param(container,null,false,0,0,240,80));
        check(clock()==retargetClock&&!retargetClock.cancelled,"late native measurement retargets within the original entry clock instead of restarting a full duration");
        checkFrame(beforeRetarget,initialBody.frame,.0001f,"late measurement retains the current entry geometry");
        retargetClock.advance(.8f);var beforeSecondRetarget=initialBody.frame;var beforeSecondVelocity=logicalVelocity(initialBody);incoming.targetWidth=360;
        hook("capsule.ui.view.CapsuleContainer#onLayout").after(CloudCheck.param(container,null,false,0,0,240,80));
        check(clock()==retargetClock&&!retargetClock.cancelled,"repeated native layouts cannot keep extending the service switch deadline");
        checkFrame(beforeSecondRetarget,initialBody.frame,.0001f,"repeated retarget preserves its current rectangle");
        checkFrame(beforeSecondVelocity,logicalVelocity(initialBody),.0001f,"remaining-clock retarget inherits its current geometry speed");
        retargetClock.end();measuredRetarget.end();
        check(initialBody.frame.width()==360&&ValueAnimator.running.isEmpty(),"the latest native target settles at the original visual deadline");
        container.incoming.put("third",capsule);container.l="third";
        hook("capsule.ui.view.CapsuleContainer#onLayout").after(CloudCheck.param(container,null,false,0,0,240,80));
        clock().end();check(initialBody.frame.width()==240&&initialBody.capsule==capsule,"a genuinely later native selection releases the pending old-key endpoint");
        container.l="current";
        var unlaid=new i.Capsule();unlaid.actualWidth=0;unlaid.actualHeight=0;unlaid.targetWidth=320;unlaid.targetHeight=80;unlaid.parent=container;
        container.incoming.put("measured-first",unlaid);
        entry.state=new com.oplus.systemui.plugins.seedling.statemachine.fluid.d("measured-first");
        Animator measuredFirst=new Animator();directBuild(entry,"e","APP_TO_CAPSULE",measuredFirst).start();
        complete(measuredFirst);
        check(initialBody.frame.width()==320&&initialBody.frame.x()==320&&initialBody.frame.y()==10,"measurement before first layout uses the native parent center rather than a negative zero-layout origin");
        // A slide is CAPSULE_SEIZE and can return a graph directly, bypassing B0.
        entry.state=new com.oplus.systemui.plugins.seedling.statemachine.fluid.d("current");
        Animator slide=new Animator();directBuild(entry,"m","CAPSULE_SEIZE",slide).start();
        check(initialBody.frame.width()==320,"swipe inherits its currently presented shape");
        clock().advance(.5f);check(initialBody.frame.width()>240&&initialBody.frame.width()<320,"native swipe changes the capsule shape along with text and moons");
        complete(slide);check(initialBody.frame.width()==240,"swipe settles at the selected service width");
        entry.state=new com.oplus.systemui.plugins.seedling.statemachine.fluid.i(240,360);
        capsule.targetWidth=360;capsule.drawableWidth=24;
        Animator drawableGoal=new Animator();directBuild(entry,"A","CAPSULE_WIDTH_CHANGE",drawableGoal).start();
        clock().advance(.4f);check(initialBody.frame.width()>240&&initialBody.frame.width()<360,"drawable-width scene morphs despite unchanged layout bounds and a transient native width");
        complete(drawableGoal);check(initialBody.frame.width()==360,"last native width segment supplies the final drawable target");
        entry.state=new com.oplus.systemui.plugins.seedling.statemachine.fluid.i(360,285,410);
        Animator drawableWithoutMeasure=new Animator();directBuild(entry,"A","CAPSULE_WIDTH_CHANGE",drawableWithoutMeasure).start();
        clock().end();check(initialBody.frame.width()==410,"native terminal drawable width wins even before final measurement catches up");
        drawableWithoutMeasure.end();
        hook("capsule.ui.view.CapsuleContainer#onLayout").after(CloudCheck.param(container,null,false,0,0,240,80));
        check(initialBody.frame.width()==410&&ValueAnimator.running.isEmpty(),"native-end stale measurement cannot rewind an explicit drawable endpoint");
        capsule.targetWidth=410;
        hook("capsule.ui.view.CapsuleContainer#onLayout").after(CloudCheck.param(container,null,false,0,0,240,80));
        check(initialBody.frame.width()==410&&ValueAnimator.running.isEmpty(),"matching final measurement acknowledges the drawable endpoint without restarting it");
        capsule.targetWidth=-1;capsule.drawableWidth=0;entry.state=null;
        hook("capsule.ui.view.CapsuleContainer#onLayout").after(CloudCheck.param(container,null,false,0,0,240,80));clock().end();
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
        check(initialBody.frame.width()<80&&initialBody.frame.height()==80&&initialBody.frame.y()==10,"business disappearance retracts width while preserving its existing height and top edge");
        complete(disappear);
        Animator appearAgain=new Animator();build(entry,"CAPSULE_APPEAR",appearAgain).start();complete(appearAgain);initialBody=surface();
        initialBody.hostAmount=.55f;
        var hostRetracted=initialBody.presentation();float hostAlpha=initialBody.presentationOpacity();
        var hideSpeed=movingGate(initialBody);initialBody.presentedVelocity=hideSpeed;
        Animator overlappingHide=new Animator();build(entry,"CAPSULE_DISAPPEAR",overlappingHide).start();
        check(initialBody.capsuleHideOwned&&initialBody.frame.equals(hostRetracted)&&Math.abs(initialBody.opacity-hostAlpha)<.0001f,"business hide inherits the visible host geometry and alpha with one contraction owner");
        checkInitialWidthVelocity(initialBody,hostRetracted.width(),hideSpeed.width(),"business hide inherits the gate width velocity instead of braking at owner handoff");
        float hideCenterX=hostRetracted.x()+hostRetracted.width()/2,hideCenterY=hostRetracted.y()+hostRetracted.height()/2;
        clock().advance(.6f);
        check(Math.abs(initialBody.frame.x()+initialBody.frame.width()/2-hideCenterX)<.001f&&Math.abs(initialBody.frame.y()+initialBody.frame.height()/2-hideCenterY)<.001f,"overlapping hide contracts around its presented center without multiplying host geometry");
        complete(overlappingHide);
        Animator afterOverlap=new Animator();build(entry,"CAPSULE_APPEAR",afterOverlap).start();complete(afterOverlap);initialBody=surface();
        initialBody.hostAmount=.2f;initialBody.businessAllowed=false;
        var partialBusiness=initialBody.presentation();float partialAlpha=initialBody.presentationOpacity();
        var entrySpeed=movingGate(initialBody);initialBody.presentedVelocity=entrySpeed;
        Animator businessEntry=new Animator();directBuild(entry,"e","APP_TO_CAPSULE",businessEntry).start();
        check(initialBody.frame.equals(partialBusiness)&&Math.abs(initialBody.presentationOpacity()-partialAlpha)<.0001f&&initialBody.businessAllowed&&initialBody.hostAmount==1,"entry absorbs a partly hidden business gate from its actual geometry and brightness without multiplying a second reveal");
        checkInitialWidthVelocity(initialBody,partialBusiness.width(),entrySpeed.width(),"entry inherits the gate width velocity before reversing toward the capsule");
        clock().advance(.5f);
        check(initialBody.frame.width()>partialBusiness.width()&&initialBody.frame.width()<240&&initialBody.presentationOpacity()>partialAlpha&&initialBody.visible(),"business-hide interruption restores one continuous visible capsule trajectory");
        complete(businessEntry);
        initialBody.hostAllowed=false;initialBody.hostAmount=.2f;
        initialBody.presentedVelocity=movingGate(initialBody);int sampledGateSpeeds=initialBody.presentationVelocityCalls;
        Animator shadedEntry=new Animator();directBuild(entry,"e","APP_TO_CAPSULE",shadedEntry).start();
        check(!initialBody.visible()&&initialBody.hostAmount==.2f,"entry cannot absorb or reset a genuinely hidden control-center host gate");
        check(initialBody.presentationVelocityCalls==sampledGateSpeeds,"host(false) entry retains logical velocity instead of absorbing the independently running host gate");
        complete(shadedEntry);initialBody.hostAllowed=true;initialBody.hostAmount=1;
        verifyClosedHostHandoff(entry,initialBody,b);
        verifyWidthOnlyHide(entry,initialBody,b);
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
        System.out.println("Verified "+checks+" unified-object lifecycle checks (platform rendering requires separate device validation).");
    }
}
