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
    public static class Scene {public Window j=new Window(),k=j;public android.content.Context l=new android.content.Context(),i=l;}
    static HookCallback hook(String key){var h=Api102Runtime.callbacks.get(BASE+key);check(h!=null,"Missing native morph boundary: "+key);return h;}
    static Animator build(Scene scene,String name,Animator original)throws Throwable {
        Object graph=new Object();var c=hook("animatorscenes."+name+"#c");
        var p=CloudCheck.param(scene,graph,new Object(),new Object());c.before(p);c.after(p);
        var g=hook("engine.animatorengine.h#g");p=CloudCheck.param(graph,original,new Object());g.after(p);
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
        for(String name:new String[]{"v","w","I0","k0"}) {
            boolean expand=name.equals("v")||name.equals("w");InstantContainer instant=expand?scene.j.g.instant:scene.j.h.r;
            instant.actualWidth=700;instant.actualHeight=300;
            var animation=build(scene,name,new AnimatorSet());check(animation instanceof ValueAnimator,"instant card uses same morph: "+name);
            ((ValueAnimator)animation).start();((ValueAnimator)animation).advance(1);
            check(instant.getAlpha()==(expand?1:0),"instant endpoint restored: "+name);
        }
        b.putBoolean("cloud_enabled",false);CloudCheck.config(b);
        check(build(scene,"t",original)==original,"disabled cloud keeps complete native animation");
        b.putBoolean("cloud_enabled",true);CloudCheck.config(b);ValueAnimator.durationScale=0;
        check(build(scene,"t",original)==original,"system animation-disabled setting retained");ValueAnimator.durationScale=1;
        card.actualWidth=0;check(build(scene,"t",original)==original,"unmeasured card safely retains native animation");
        System.out.println("Verified "+checks+" continuous cloud morph checks.");
    }
}
