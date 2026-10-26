package io.github.baishangui.thunder;

import android.animation.Animator;
import android.os.Bundle;
import com.oplus.systemui.plugins.seedling.capsule.i;

/** Actual scene callbacks for a capsule update/deletion interrupting native card cleanup. */
public final class CloudDismissalCheck extends CloudMorphCheck {
    static void nativeRemove(Window window)throws Throwable {
        var callback=hook("card.window.i#e");
        var call=CloudCheck.param(window,"native");callback.before(call);
        if(call.getResult()!=null)window.e();
    }
    static Scene cardScene() {
        Scene scene=new Scene();
        var card=new com.oplus.systemui.plugins.seedling.card.ui.view.CardView();
        card.actualWidth=800;card.actualHeight=400;card.screenX=100;card.screenY=120;
        scene.j.g.cards.addView(card);scene.j.g.cards.cards.add(card);
        return scene;
    }
    static void verifyCleanup(String replacement)throws Throwable {
        Scene scene=cardScene(),entry=new Scene();
        Animator open=new Animator();build(scene,"CAPSULE_TO_CARD",open).start();complete(open);
        CloudMorphSurface body=surface();
        Animator close=new Animator();
        close.addListener(new android.animation.AnimatorListenerAdapter(){
            @Override public void onAnimationEnd(Animator animator){
                try{nativeHide(scene.j);nativeRemove(scene.j);}catch(Throwable error){throw new RuntimeException(error);}
            }
        });
        build(scene,"CARD_TO_CAPSULE",close).start();clock().advance(.25f);close.end();
        check(scene.j.hides==0&&scene.j.removes==0,"native window cleanup waits for the independent close");
        Animator capsule=new Animator();build(entry,replacement,capsule).start();complete(capsule);
        check(scene.j.hides==1&&scene.j.removes==1,
            "capsule takeover must execute deferred native hide/removal exactly once: "+replacement);
        check(!body.inputOwnership.captureOutside()&&!body.inputOwnership.keepLease(false),
            "a capsule/deleted surface cannot retain the old expanded window's input: "+replacement);
        check(!body.expanded,"capsule takeover settles its own input endpoint: "+replacement);
        if(!body.disposed)body.dispose();
        CloudMorphHook.forget(i.instance.c.container,body);
    }
    static void verifyDeletingClosingCard(int curve)throws Throwable {
        Bundle config=new Bundle();config.putBoolean("enabled",true);config.putBoolean("cloud_enabled",true);
        config.putInt("cloud_animation_curve",curve);CloudCheck.config(config);
        Scene scene=cardScene();
        Animator open=new Animator();build(scene,"CAPSULE_TO_CARD",open).start();complete(open);
        CloudMorphSurface body=surface();
        Animator close=new Animator();build(scene,"CARD_TO_CAPSULE",close).start();clock().advance(.2f);
        var before=body.presentation();
        Animator hide=new Animator();build(new Scene(),"CAPSULE_DISAPPEAR",hide).start();
        checkFrame(before,body.presentation(),.0001f,"deletion inherits the actual visible card without jumping");
        clock().advance(.5f);
        check(body.frame.height()<before.height()&&body.frame.height()>=80,
            "deleting a closing card must continue reducing height instead of squeezing a tall strip: curve "+curve);
        check(body.frame.y()<before.y()&&body.frame.progress()<before.progress(),
            "deleting a closing card must continue moving and changing content toward its capsule: curve "+curve);
        clock().end();
        check(body.frame.height()==80&&body.frame.y()==10&&body.frame.progress()==0,
            "card deletion reaches the hidden capsule geometry before releasing the surface: curve "+curve);
        complete(hide);close.end();
        check(body.disposed&&android.animation.ValueAnimator.running.isEmpty(),"deletion releases its surface and visual clock");
        Animator reopen=new Animator();build(scene,"CAPSULE_TO_CARD",reopen).start();complete(reopen);
        CloudMorphSurface next=surface();
        check(next!=body&&next.inputOwnership.captureOutside()&&next.inputOwnership.acceptsInput(),
            "a new expansion after deletion owns working content and outside input");
        next.dispose();CloudMorphHook.forget(i.instance.c.container,next);
    }
    static void verifyLateCleanup()throws Throwable {
        Scene scene=cardScene();
        Animator open=new Animator();build(scene,"CAPSULE_TO_CARD",open).start();complete(open);
        CloudMorphSurface body=surface();
        Animator close=new Animator();
        close.addListener(new android.animation.AnimatorListenerAdapter(){
            @Override public void onAnimationEnd(Animator animator){
                try{nativeHide(scene.j);nativeRemove(scene.j);}catch(Throwable error){throw new RuntimeException(error);}
            }
        });
        build(scene,"CARD_TO_CAPSULE",close).start();clock().advance(.25f);
        Animator update=new Animator();build(new Scene(),"CAPSULE_WIDTH_CHANGE",update).start();complete(update);
        check(scene.j.hides==0&&scene.j.removes==0,"capsule takeover leaves native cleanup timing with its lifecycle");
        close.end();
        check(scene.j.hides==1&&scene.j.removes==1,"native cleanup arriving after capsule takeover is allowed exactly once");
        check(!body.expanded&&!body.inputOwnership.captureOutside()&&body.inputOwnership.acceptsInput(),
            "late old-card cleanup preserves capsule input without restoring its expanded region");
        body.dispose();CloudMorphHook.forget(i.instance.c.container,body);
    }
    static void verifyCardButton(String deletion,boolean earlyCleanup)throws Throwable {
        Scene scene=cardScene();
        Animator open=new Animator();build(scene,"CAPSULE_TO_CARD",open).start();complete(open);
        CloudMorphSurface body=surface();var before=body.presentation();
        if(earlyCleanup){
            // The service can clear its RecyclerView before the delete graph
            // starts. Dismissal must use the presentation, not that empty list.
            scene.j.g.cards.cards.get(0).attached=false;scene.j.g.cards.cards.clear();
        }
        Animator hide=new Animator();
        hide.addListener(new android.animation.AnimatorListenerAdapter(){
            @Override public void onAnimationEnd(Animator animator){
                try{nativeHide(scene.j);nativeRemove(scene.j);}catch(Throwable error){throw new RuntimeException(error);}
            }
        });
        build(scene,deletion,hide).start();
        check(!body.inputOwnership.captureOutside()&&!body.inputOwnership.acceptsInput()&&!body.inputOwnership.acceptsCapsuleInput(),
            "card's close button immediately releases all Body input, including capsule reversal: "+deletion);
        checkFrame(before,body.presentation(),.0001f,"button dismissal starts at the visible expanded card");
        if(earlyCleanup){hide.end();check(scene.j.hides==0&&scene.j.removes==0,"early native removal waits for the visible dismissal");}
        clock().advance(.5f);
        check(body.frame.height()<before.height()&&body.frame.y()<before.y(),"card dismissal contracts height and position together: "+deletion);
        var deletingClock=clock();
        Animator update=new Animator();build(new Scene(),"CAPSULE_WIDTH_CHANGE",update).start();update.end();
        check(clock()==deletingClock&&!body.inputOwnership.acceptsInput(),"background capsule update cannot interrupt terminal deletion or revive input");
        clock().end();
        if(!earlyCleanup)hide.end();
        check(scene.j.hides==1&&scene.j.removes==1,"card button's native cleanup cannot be dropped as stale: "+deletion);
        check(body.disposed,"card button deletes its independent window: "+deletion);
        Animator next=new Animator();build(cardScene(),"CAPSULE_TO_CARD",next).start();complete(next);
        CloudMorphSurface reopened=surface();
        check(reopened!=body&&reopened.inputOwnership.acceptsInput()&&reopened.inputOwnership.captureOutside(),"next card remains clickable after button dismissal");
        reopened.dispose();CloudMorphHook.forget(i.instance.c.container,reopened);
    }
    static void verifyButtonReopen()throws Throwable{
        Scene scene=cardScene();Animator open=new Animator();build(scene,"CAPSULE_TO_CARD",open).start();complete(open);
        CloudMorphSurface body=surface();Animator hide=new Animator();
        hide.addListener(new android.animation.AnimatorListenerAdapter(){@Override public void onAnimationEnd(Animator animation){
            try{nativeHide(scene.j);nativeRemove(scene.j);}catch(Throwable error){throw new RuntimeException(error);}
        }});
        build(scene,"CARD_DISAPPEAR",hide).start();clock().advance(.25f);
        Animator reopen=new Animator();build(scene,"CAPSULE_TO_CARD",reopen).start();
        hide.end();
        check(scene.j.hides==0&&scene.j.removes==0,"late deleted-card callbacks cannot hide a newer opening");complete(reopen);
        check(!body.disposed&&body.inputOwnership.acceptsInput()&&body.inputOwnership.captureOutside(),"reopening during deletion restores card input");
        body.dispose();CloudMorphHook.forget(i.instance.c.container,body);
    }
    static void verifyLastServiceRemoved()throws Throwable {
        var container=i.instance.c.container;
        Scene scene=cardScene();Animator open=new Animator();build(scene,"CAPSULE_TO_CARD",open).start();complete(open);
        CloudMorphSurface body=surface();var before=body.presentation();
        container.unbound=true;container.capsule.attached=false;
        Animator hide=new Animator();
        hide.addListener(new android.animation.AnimatorListenerAdapter(){@Override public void onAnimationEnd(Animator animation){
            try{nativeHide(scene.j);nativeRemove(scene.j);}catch(Throwable error){throw new RuntimeException(error);}
        }});
        try {
            build(scene,"CARD_DISAPPEAR",hide).start();
            checkFrame(before,body.presentation(),.0001f,"last service deletion keeps its presented geometry after native capsule removal");
            check(!android.animation.ValueAnimator.running.isEmpty(),"last service deletion starts its visual clock without an active capsule");
            clock().advance(.5f);
            check(body.frame.height()<before.height(),"last service deletion contracts the retained presentation");
            clock().end();hide.end();
            check(body.disposed&&scene.j.removes==1,"last service deletion releases both independent and native windows");
        } finally {container.unbound=false;container.capsule.attached=true;}
    }
    public static void main(String[] args)throws Throwable {
        CloudHook.install(CloudDismissalCheck.class.getClassLoader());
        Bundle config=new Bundle();config.putBoolean("enabled",true);config.putBoolean("cloud_enabled",true);CloudCheck.config(config);
        var container=i.instance.c.container;var capsule=container.capsule;
        container.measuredWidth=960;container.measuredHeight=100;
        capsule.actualWidth=240;capsule.actualHeight=80;capsule.screenX=360;capsule.screenY=10;
        hook("capsule.ui.view.CapsuleContainerRoot#onTouchEvent").before(CloudCheck.param(new TouchRoot(),null,new Object()));
        for(String kind:new String[]{"CAPSULE_WIDTH_CHANGE","CAPSULE_APPEAR","CAPSULE_DISAPPEAR"})verifyCleanup(kind);
        for(int curve=0;curve<4;curve++)verifyDeletingClosingCard(curve);
        verifyLateCleanup();
        for(String kind:new String[]{"CARD_DISAPPEAR","CARD_DISAPPEAR_WITHOUT_ANIM","CARD_DISAPPEAR_DOO_ANIM",
            "LARGE_INSTANT_CARD_DISAPPEAR","SMALL_INSTANT_CARD_DISAPPEAR","CARD_CAPSULE_DISAPPEAR_BY_INTERRUPT",
            "LARGE_INSTANT_CARD_CAPSULE_DISAPPEAR_BY_INTERRUPT","SMALL_INSTANT_CARD_CAPSULE_DISAPPEAR_BY_INTERRUPT"}){
            verifyCardButton(kind,true);verifyCardButton(kind,false);
        }
        verifyButtonReopen();
        verifyLastServiceRemoved();
        for(int curve=0;curve<4;curve++){
            config.putInt("cloud_animation_curve",curve);CloudCheck.config(config);
            verifyCardButton("CARD_DISAPPEAR",true);verifyCardButton("CARD_DISAPPEAR",false);
        }
        System.out.println("Verified "+checks+" card deletion and capsule takeover checks.");
    }
}
