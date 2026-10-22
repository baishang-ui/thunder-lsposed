package io.github.baishangui.thunder;
import android.animation.*;
import android.os.Bundle;
import com.oplus.systemui.plugins.seedling.capsule.i;
import java.util.*;

/** Runs actual scene scheduling; only the Android window boundary is substituted. */
public final class CloudStackCheck {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Throwable {
        CloudHook.install(CloudStackCheck.class.getClassLoader());
        Bundle settings=new Bundle();settings.putBoolean("enabled",true);settings.putBoolean("cloud_enabled",true);CloudCheck.config(settings);
        var capsule=i.instance.c.container.capsule;capsule.actualWidth=240;capsule.actualHeight=80;
        capsule.screenX=360;capsule.screenY=10;
        var scene=new CloudMorphCheck.Scene();
        for(int n=0;n<3;n++){
            var card=new com.oplus.systemui.plugins.seedling.card.ui.view.CardView();
            card.actualWidth=800;card.actualHeight=300;card.screenX=100;card.screenY=120+n*320;
            scene.j.g.cards.addView(card);scene.j.g.cards.cards.add(card);
        }
        Animator open=new Animator();CloudMorphCheck.build(scene,"CAPSULE_TO_CARD",open).start();
        check(CloudMorphSurface.created.size()==3,"every card must join the unified animation");
        var lead=CloudMorphSurface.created.get(0);var second=CloudMorphSurface.created.get(1);var third=CloudMorphSurface.created.get(2);
        check(second.frame.width()==lead.frame.width()&&second.frame.height()==lead.frame.height()&&second.frame.y()==lead.frame.y(),
            "followers begin under the first capsule instead of materializing at expanded-card size");
        CloudMorphCheck.clock().advance(.4f);
        check(lead.frame.width()>240&&second.opacity>0&&third.opacity>0,"cards must overlap in motion instead of waiting at phase boundaries");
        check(second.frame.width()<=lead.frame.width()&&third.frame.width()<=lead.frame.width()&&
            second.frame.height()<=lead.frame.height()&&third.frame.height()<=lead.frame.height(),
            "followers inherit the first card's current size while emerging from underneath it");
        CloudMorphCheck.clock().advance(.66f);
        check(second.opacity>third.opacity&&third.opacity>0,"followers continuously trail with a small stagger");
        CloudMorphCheck.complete(open);
        check(second.frame.y()==440&&third.frame.y()==760,"all cards reach native group positions");
        check(lead.inputOwnership.captureOutside()&&second.inputOwnership.captureOutside()&&third.inputOwnership.captureOutside(),"all expanded cards participate in outside dismissal");
        Animator close=new Animator();CloudMorphCheck.build(scene,"CARD_TO_CAPSULE",close).start();
        check(!lead.inputOwnership.captureOutside()&&!second.inputOwnership.captureOutside()&&!third.inputOwnership.captureOutside(),"close start releases outside capture for the entire card stack");
        check(!lead.inputOwnership.acceptsInput()&&!second.inputOwnership.acceptsInput()&&!third.inputOwnership.acceptsInput(),"close start makes the entire card stack touch transparent");
        CloudMorphCheck.clock().advance(.04f);
        check(lead.frame.width()<800,"lead contraction starts immediately alongside gathering");
        CloudMorphCheck.clock().advance(.3f);
        check(!lead.inputOwnership.captureOutside()&&!second.inputOwnership.captureOutside()&&!third.inputOwnership.captureOutside(),"moving followers cannot retain outside capture during contraction");
        check(!lead.inputOwnership.acceptsInput()&&!second.inputOwnership.acceptsInput()&&!third.inputOwnership.acceptsInput(),"moving followers cannot intercept content touches during contraction");
        check(lead.frame.width()<800&&lead.frame.width()>240&&second.frame.y()<440&&third.frame.y()<760,"gathering flows into lead contraction without a pause");
        CloudMorphCheck.clock().advance(.6f);
        check(second.opacity>0&&third.opacity>0&&lead.frame.width()<800,"followers continue gathering with the lead instead of disappearing early");
        float width=lead.frame.width(),y=second.frame.y();
        Animator reverse=new Animator();CloudMorphCheck.build(scene,"CAPSULE_TO_CARD",reverse).start();
        check(lead.inputOwnership.captureOutside()&&second.inputOwnership.captureOutside()&&third.inputOwnership.captureOutside(),"opening reversal restores outside capture for every card");
        check(lead.inputOwnership.acceptsInput()&&second.inputOwnership.acceptsInput()&&third.inputOwnership.acceptsInput(),"opening reversal restores content input for every card");
        check(lead.frame.width()==width&&second.frame.y()==y,"reversal preserves each presented card geometry");
        CloudMorphCheck.clock().advance(.005f);
        check(second.frame.y()!=y,"in-flight follower reversal retains velocity without restarting the stagger delay");
        CloudMorphCheck.complete(reverse);
        Animator fullClose=new Animator();CloudMorphCheck.build(scene,"CARD_TO_CAPSULE",fullClose).start();
        CloudMorphCheck.clock().end();
        check(!lead.inputOwnership.captureOutside()&&!second.inputOwnership.captureOutside()&&!third.inputOwnership.captureOutside(),"visual endpoint cannot leave a follower capturing while native end is delayed");
        check(lead.inputOwnership.acceptsInput()&&!lead.expanded,"visual endpoint restores primary capsule clicks before native end");
        check(lead.inputOwnership.keepLease(true)&&second.inputOwnership.keepLease(true)&&third.inputOwnership.keepLease(true),"delayed native cleanup keeps every transparent native card disabled");
        fullClose.end();
        check(second.opacity==0&&second.frame.y()<440,"completed close parks satellites behind the lead");
        check(second.frame.width()==lead.frame.width()&&second.frame.height()==lead.frame.height()&&second.frame.y()==lead.frame.y(),
            "gathered followers track the shrinking lead all the way into its capsule");
        Animator fullReopen=new Animator();CloudMorphCheck.build(scene,"CAPSULE_TO_CARD",fullReopen).start();
        check(second.frame.y()<440,"reopening starts from parked position, not native endpoint");CloudMorphCheck.complete(fullReopen);
        Animator resizeClose=new Animator();CloudMorphCheck.build(scene,"CARD_TO_CAPSULE",resizeClose).start();
        CloudMorphCheck.clock().advance(.3f);
        var primaryBeforeResize=lead.frame;var secondBeforeResize=second.frame;var thirdBeforeResize=third.frame;
        float secondAlpha=second.opacity,thirdAlpha=third.opacity;
        capsule.actualWidth=300;
        CloudMorphCheck.hook("capsule.ui.view.CapsuleContainer#onLayout").after(CloudCheck.param(i.instance.c.container,null,false,0,0,300,80));
        scene.j.g.getViewTreeObserver().dispatchOnPreDraw();
        check(lead.frame.equals(primaryBeforeResize)&&second.frame.equals(secondBeforeResize)&&third.frame.equals(thirdBeforeResize),"layout retarget preserves every currently presented rectangle");
        check(second.opacity==secondAlpha&&third.opacity==thirdAlpha,"layout retarget preserves follower opacity instead of restarting the fade");
        CloudMorphCheck.complete(resizeClose);
        check(lead.frame.width()==300&&second.opacity==0&&third.opacity==0,"retarget reaches the new capsule while followers finish gathering");
        settings.putInt("cloud_animation_bounce",100);CloudCheck.config(settings);
        Animator bouncedOpen=new Animator();CloudMorphCheck.build(scene,"CAPSULE_TO_CARD",bouncedOpen).start();
        CloudMorphCheck.complete(bouncedOpen);
        Animator bouncedClose=new Animator();CloudMorphCheck.build(scene,"CARD_TO_CAPSULE",bouncedClose).start();
        float revealSecond=1,revealThird=1;
        for(int tick=0;tick<=100;tick++){
            CloudMorphCheck.clock().advance(tick/100f);
            check(lead.frame.x()>=99&&lead.frame.x()<=371&&lead.frame.y()>=5&&lead.frame.y()<=121,
                "maximum closing rebound is bounded to four percent of travel");
            check(second.opacity<=revealSecond+.00001f&&third.opacity<=revealThird+.00001f,
                "gathered cards do not return after merging at maximum rebound");
            revealSecond=second.opacity;revealThird=third.opacity;
        }
        CloudMorphCheck.complete(bouncedClose);
        check(second.frame.equals(lead.frame)&&third.frame.equals(lead.frame),"all followers finish under the first capsule even at maximum rebound");
        settings.putBoolean("cloud_card_position_enabled",true);settings.putInt("cloud_card_offset_x",-24);settings.putInt("cloud_card_offset_y",72);CloudCheck.config(settings);
        Animator shifted=new Animator();CloudMorphCheck.build(scene,"CAPSULE_TO_CARD",shifted).start();CloudMorphCheck.complete(shifted);
        check(lead.frame.x()==34&&lead.frame.y()==318,"configured dp offsets move the lead");
        check(second.frame.x()==34&&second.frame.y()==638,"the same offset preserves card spacing");
        System.out.println("Verified multi-card staging, position and interruption.");
    }
}
