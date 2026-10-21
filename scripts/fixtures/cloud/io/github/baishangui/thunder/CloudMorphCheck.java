package io.github.baishangui.thunder;

import android.animation.*;
import android.graphics.Outline;
import android.os.Bundle;
import android.view.*;
import com.oplus.systemui.plugins.seedling.capsule.i;
import java.util.*;

/** C17 native scene boundaries; native lifecycle callbacks stay bound to their original animator. */
public class CloudMorphCheck {
    static final String BASE="com.oplus.systemui.plugins.seedling.";
    static int checks;
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
    public static class Cards extends ViewGroup {
        public final List<View> cards=new ArrayList<>();
        public List<View> getCardViews(){return cards;}
    }
    public static class Root extends ViewGroup {
        public final Cards cards=new Cards();public final InstantContainer instant=new InstantContainer();
        public Cards getCardContainer(){return cards;}
        public InstantContainer getInstantContainer(){return instant;}
    }
    public static class InstantContainer extends ViewGroup {
        public com.oplus.view.ViewRootManager p=new com.oplus.view.ViewRootManager(this);
        public View content=new View();public View getInstantView(){return content;}
        public void setAnimatorState(Object v){}
    }
    public static class Window {public Root g=new Root();public Secondary h=new Secondary();}
    public static class Secondary {public InstantContainer r=new InstantContainer();}
    public static class TouchRoot {public TouchHandler b=new TouchHandler();}
    public static class TouchHandler {public Object b=i.instance.c.container;}
    enum Kind {
        CAPSULE_TO_CARD,CARD_TO_CAPSULE,CARD_TO_CAPSULE_BY_INTERRUPT,
        CAPSULE_TO_LARGE_INSTANT_CARD,CAPSULE_TO_SMALL_INSTANT_CARD,CAPSULE_TO_SMALL_INSTANT_CARD_WITH_BLANK,
        LARGE_INSTANT_CARD_TO_CAPSULE,SMALL_INSTANT_CARD_TO_CAPSULE,SMALL_INSTANT_CARD_TO_CAPSULE_WITH_BLANK,
        CARD_TO_APP,LOCKSCREEN_TO_CARD,CARD_TO_CAPSULE_WITHOUT_ANIM
    }
    public static class Scene {
        public Window j=new Window(),k=j;
        public android.content.Context a=new android.content.Context(),l=a,i=a;
        Kind kind;
        Runnable beforeClock;
        com.oplus.systemui.plugins.seedling.animatorscenes.Z condition;
        public Kind f(){return kind;}
    }
    static HookCallback hook(String key){var h=Api102Runtime.callbacks.get(BASE+key);check(h!=null,"Missing native morph boundary: "+key);return h;}
    static Animator build(Scene scene,String name,Animator original)throws Throwable {
        scene.kind=switch(name) {
            case "t" -> Kind.CAPSULE_TO_CARD;case "Q" -> Kind.CARD_TO_CAPSULE;case "P" -> Kind.CARD_TO_CAPSULE_BY_INTERRUPT;
            case "v" -> Kind.CAPSULE_TO_LARGE_INSTANT_CARD;case "w" -> Kind.CAPSULE_TO_SMALL_INSTANT_CARD;
            case "y" -> Kind.CAPSULE_TO_SMALL_INSTANT_CARD_WITH_BLANK;case "J0" -> Kind.SMALL_INSTANT_CARD_TO_CAPSULE_WITH_BLANK;
            case "I0" -> Kind.SMALL_INSTANT_CARD_TO_CAPSULE;case "k0" -> Kind.LARGE_INSTANT_CARD_TO_CAPSULE;
            case "app" -> Kind.CARD_TO_APP;case "lock" -> Kind.LOCKSCREEN_TO_CARD;
            default -> Kind.CARD_TO_CAPSULE_WITHOUT_ANIM;
        };
        var graph=new com.oplus.systemui.plugins.seedling.engine.animatorengine.h();
        var builder=new com.oplus.systemui.plugins.seedling.animatorscenes.B0(scene,graph);
        scene.condition=builder.condition;
        var c=hook("animatorscenes.B0#invoke");var p=CloudCheck.param(builder,null);c.before(p);
        Object wrapper=builder.invoke();p.setResult(wrapper);c.after(p);
        if(scene.beforeClock!=null){Runnable ready=scene.beforeClock;scene.beforeClock=null;ready.run();}
        var g=hook("engine.animatorengine.h#g");p=CloudCheck.param(wrapper,original,new Object());g.before(p);g.after(p);
        check(builder.condition.d==250&&builder.condition.e==400,"native clock metadata is not mutated during construction");
        return (Animator)p.getResult();
    }
    static float radius(View v){Outline o=new Outline();v.getOutlineProvider().getOutline(v,o);return o.radius;}
    static float width(View v){return ((com.oplus.systemui.plugins.common.util.j)v.getOutlineProvider()).a.width();}
    static float centerX(View v){var r=((com.oplus.systemui.plugins.common.util.j)v.getOutlineProvider()).a;return v.screenX+r.exactCenterX();}
    public static void main(String[] args)throws Throwable {
        CloudHook.install(CloudMorphCheck.class.getClassLoader());
        Bundle b=new Bundle();b.putBoolean("enabled",true);b.putBoolean("cloud_enabled",true);
        b.putBoolean("cloud_card_corner_enabled",true);b.putInt("cloud_card_corner",32);CloudCheck.config(b);
        Scene scene=new Scene();var card=new com.oplus.systemui.plugins.seedling.card.ui.view.CardView();
        card.actualWidth=800;card.actualHeight=400;card.screenX=100;card.screenY=120;
        scene.j.g.cards.addView(card);scene.j.g.cards.cards.add(card);
        var capsule=i.instance.c.container.capsule;capsule.actualWidth=240;capsule.actualHeight=80;capsule.screenX=360;capsule.screenY=10;
        List<String> events=new ArrayList<>();AnimatorSet original=new AnimatorSet();ValueAnimator leaf=ValueAnimator.ofFloat(0,1);
        original.children.add(leaf);leaf.addUpdateListener(a->{throw new AssertionError("native segmented animation must never run");});
        leaf.addListener(new AnimatorListenerAdapter(){
            public void onAnimationStart(Animator a){check(a==leaf,"original fragment identity retained");events.add("child-start");}
            public void onAnimationEnd(Animator a){events.add("child-end");}
        });
        original.addListener(new AnimatorListenerAdapter(){public void onAnimationStart(Animator a){events.add("root-start");}public void onAnimationEnd(Animator a){events.add("root-end");}});
        Animator replacement=build(scene,"t",original);
        check(replacement instanceof ValueAnimator&&replacement!=original,"one frame clock replaces both native stages");
        ValueAnimator motion=(ValueAnimator)replacement;motion.start();
        check(width(card)==240&&radius(card)==40,"opening starts at exact capsule dimensions and radius");
        check(Math.abs(centerX(card)-480)<1,"opening starts at capsule screen position");
        check(card.getAlpha()==1&&capsule.getAlpha()==0,"single background stays opaque during handoff");
        check(i.instance.c.container.getAlpha()==0,"capsule edge siblings are hidden with its body");
        float last=240;
        for(float t:new float[]{.1f,.25f,.5f,.75f}){
            motion.advance(t);float next=width(card);check(next>last&&next<800,"opening grows continuously without overshoot");last=next;
            check(Math.abs(card.bg.c.corner-radius(card))<.01f,"native material and content share the current radius");
            check(Math.abs(radius(card.bg)-radius(card))<.01f,"independent material outline uses the same current radius");
            var callback=Api102Runtime.callbacks.get("com.oplus.systemui.plugins.common.util.j#getOutline");
            var p=CloudCheck.param(card.getOutlineProvider(),null,card,new Outline());Object before=Reflect.getObjectField(card.getOutlineProvider(),"b");callback.before(p);
            check(Reflect.getObjectField(card.getOutlineProvider(),"b")==before,"custom card settings cannot overwrite animated radius");callback.after(p);
        }
        float interruptedWidth=width(card),interruptedX=centerX(card);motion.cancel();
        ValueAnimator close=(ValueAnimator)build(scene,"Q",new AnimatorSet());close.start();
        check(width(card)==interruptedWidth&&Math.abs(centerX(card)-interruptedX)<1,"reversal continues from the last rendered bounds");
        close.advance(.5f);check(width(card)<interruptedWidth&&width(card)>240,"closing shrinks directly toward capsule");close.advance(1);
        check(card.getAlpha()==0&&capsule.getAlpha()==1,"closing hands off to capsule at its exact endpoint");
        check(card.getScaleX()==1&&card.getScaleY()==1&&card.getTranslationX()==0&&card.getTranslationY()==0,"close restores native transforms");
        check(scene.j.g.cards.getClipChildren()&&scene.j.g.cards.getClipToPadding(),"ancestor clipping restored");
        check(events.equals(List.of("root-start","child-start","child-end","root-end")),"native completion order preserved on cancel");
        ValueAnimator forward=(ValueAnimator)build(scene,"t",new AnimatorSet());forward.start();forward.advance(.5f);
        float retargetWidth=width(card);ValueAnimator reverse=(ValueAnimator)build(scene,"P",new AnimatorSet());reverse.start();
        check(forward.cancelled&&width(card)==retargetWidth,"host interruption scene cancels and retargets a running clock");reverse.advance(1);
        ValueAnimator open=(ValueAnimator)build(scene,"t",new AnimatorSet());open.start();open.advance(1);
        check(card.getAlpha()==1&&capsule.getAlpha()==1&&i.instance.c.container.getAlpha()==1,"expanded endpoint restores native capsule alpha for later scenes");
        check(card.getForeground().getAlpha()==0,"native entrance mask cannot reappear after the morph");
        ValueAnimator detached=(ValueAnimator)build(scene,"t",new AnimatorSet());detached.start();card.attached=false;detached.advance(.25f);
        check(detached.cancelled&&scene.j.g.cards.getClipChildren(),"detach cancels animation and releases clipping");card.attached=true;
        ValueAnimator paused=(ValueAnimator)build(scene,"t",new AnimatorSet());paused.start();b.putBoolean("cloud_enabled",false);CloudCheck.config(b);CloudHook.refresh();
        check(paused.cancelled&&scene.j.g.cards.getClipChildren(),"live disable cancels active morph and releases clipping");b.putBoolean("cloud_enabled",true);CloudCheck.config(b);
        List<String> cancelledEvents=new ArrayList<>();Animator nativeCancel=new Animator();
        nativeCancel.addListener(new AnimatorListenerAdapter(){public void onAnimationCancel(Animator a){cancelledEvents.add("cancel");a.removeListener(this);}public void onAnimationEnd(Animator a){cancelledEvents.add("end");}});
        ValueAnimator cancelled=(ValueAnimator)build(scene,"t",nativeCancel);cancelled.start();cancelled.cancel();
        check(cancelledEvents.equals(List.of("cancel")),"cancelled native monitor cannot also complete its scene");
        for(String name:new String[]{"v","w","y","I0","k0","J0"}) {
            boolean expand=name.equals("v")||name.equals("w");InstantContainer instant=expand?scene.j.g.instant:scene.j.h.r;
            expand=expand||name.equals("y");
            instant.actualWidth=700;instant.actualHeight=300;
            instant.content.actualWidth=620;instant.content.actualHeight=260;
            var animation=build(scene,name,new AnimatorSet());check(animation instanceof ValueAnimator,"instant card uses same morph: "+name);
            ((ValueAnimator)animation).start();
            check(instant.content.getAlpha()==1,"actual material stays opaque from the first capsule frame: "+name);
            ((ValueAnimator)animation).advance(.25f);
            check(width(instant.content)>240&&width(instant.content)<620,"actual content surface grows without a shrink prelude: "+name);
            ((ValueAnimator)animation).advance(1);
            check(instant.content.getAlpha()==(expand?1:0),"instant endpoint restored: "+name);
        }
        for(String name:new String[]{"app","lock","no-animation"})check(build(scene,name,original)==original,"unrelated scene retains native animation: "+name);
        var late=scene.j.h.r;late.content=null;
        View ready=new View();ready.actualWidth=640;ready.actualHeight=280;
        scene.beforeClock=()->late.content=ready;
        var lateAnimation=build(scene,"y",new AnimatorSet());
        check(lateAnimation instanceof ValueAnimator,"blank branch resolves its actual card after native layout preparation");
        ((ValueAnimator)lateAnimation).start();check(width(ready)==240,"late card begins at capsule bounds without a pre-shrink");
        ((ValueAnimator)lateAnimation).advance(1);
        scene.j.g.instant.content=null;
        var mounted=build(scene,"v",new AnimatorSet());
        check(mounted instanceof ValueAnimator,"instant opening uses the mounted secondary host when the primary is only a placeholder");
        ((ValueAnimator)mounted).start();check(width(ready)==240,"mounted content is the actual morph surface");((ValueAnimator)mounted).advance(1);
        b.putBoolean("cloud_enabled",false);CloudCheck.config(b);
        check(build(scene,"t",original)==original,"disabled cloud keeps complete native animation");
        b.putBoolean("cloud_enabled",true);CloudCheck.config(b);ValueAnimator.durationScale=0;
        check(build(scene,"t",original)==original,"system animation-disabled setting retained");ValueAnimator.durationScale=1;
        var touch=hook("capsule.ui.view.CapsuleContainerRoot#onTouchEvent");
        touch.before(CloudCheck.param(new TouchRoot(),null,new Object()));
        capsule.actualWidth=0;capsule.actualHeight=0;
        var anchorOpen=(ValueAnimator)build(scene,"t",new AnimatorSet());anchorOpen.start();
        check(!anchorOpen.isPaused()&&width(card)==240,"a capsule hidden before card start still uses its pre-click anchor");anchorOpen.advance(1);
        capsule.actualWidth=240;capsule.actualHeight=80;
        Scene cold=new Scene();cold.j.g.instant.content=null;cold.j.h.r.content=null;
        ValueAnimator coldOpen=(ValueAnimator)build(cold,"v",new AnimatorSet());coldOpen.start();
        check(coldOpen.isPaused()&&capsule.getAlpha()==1,"cold opening keeps capsule visible while target is not mounted");
        check(cold.condition.completed==0,"host condition cannot finish while the target is missing");
        View first=new View();first.actualWidth=0;first.actualHeight=0;cold.j.g.instant.content=first;
        java.lang.reflect.Field worker=CloudMorphHook.class.getDeclaredField("layoutHandler");worker.setAccessible(true);
        android.os.Handler scheduler=(android.os.Handler)worker.get(null);scheduler.advance(16);
        check(coldOpen.isPaused(),"zero-size first card waits instead of falling back to shrink animation");
        first.actualWidth=640;first.actualHeight=300;first.setAlpha(0);first.getBackground().setAlpha(0);
        cold.j.g.instant.setScaleX(.3f);cold.j.g.instant.setScaleY(.3f);cold.j.g.instant.setTranslationY(25f);
        first.getBackground().setBounds(new android.graphics.Rect(0,0,1,1));scheduler.advance(16);
        check(!coldOpen.isPaused()&&width(first)==240,"first completed layout starts at the capsule without interruption");
        check(first.getAlpha()==1&&first.getBackground().getAlpha()==255,"cold card material is visible from its first morph frame");
        check(cold.j.g.instant.getScaleX()==1&&cold.j.g.instant.getScaleY()==1&&cold.j.g.instant.getTranslationY()==0,
            "cold owner's previous shrink transform cannot multiply the actual card morph");
        coldOpen.advance(.25f);check(width(first)>240&&width(first)<640,"cold first opening grows immediately");coldOpen.advance(1);
        check(first.getBackground().getBounds().width()==640,"opening leaves the full native material bounds");
        check(cold.condition.completed==1,"host condition completes exactly once after full expansion");
        ViewGroup freshRoot=new ViewGroup();View freshText=new View();freshText.setAlpha(0);freshRoot.addView(freshText);
        freshRoot.actualWidth=660;freshRoot.actualHeight=300;cold.j.g.instant.content=freshRoot;
        freshRoot.setOutlineProvider(new com.oplus.systemui.plugins.common.util.j(new android.graphics.Rect(200,100,400,180),new kotlin.Triple(2,40f,3f)));
        freshRoot.setClipToOutline(true);
        var fresh=(ValueAnimator)build(cold,"v",new AnimatorSet());fresh.start();fresh.advance(.5f);
        check(freshText.getAlpha()>0,"initial zero-alpha content is revealed instead of multiplied by zero");fresh.advance(1);
        check(freshText.getAlpha()==1,"cold content finishes fully visible");
        Outline settled=new Outline();freshRoot.getOutlineProvider().getOutline(freshRoot,settled);
        check(settled.left==0&&settled.top==0&&settled.right==660&&settled.bottom==300,
            "completed opening cannot restore a stale compressed native outline");
        cold.j.g.instant.content=null;cold.j.h.r.content=null;
        var waiting=(ValueAnimator)build(cold,"v",new AnimatorSet());waiting.start();waiting.cancel();scheduler.advance(32);
        check(scheduler.pending()==0&&cold.condition.completed==0,"cancelling during first layout removes its wait without completing a different scene");
        var timedOut=(ValueAnimator)build(cold,"v",new AnimatorSet());timedOut.start();scheduler.advance(600);
        check(timedOut.cancelled&&scheduler.pending()==0,"missing card is bounded and cannot leave an animation waiting forever");
        System.out.println("Verified "+checks+" continuous cloud morph checks.");
    }
}
