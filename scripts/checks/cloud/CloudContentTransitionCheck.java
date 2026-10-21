package io.github.baishangui.thunder;

/** Explicit-time checks for deduplicated native-content blur-in transitions. */
public final class CloudContentTransitionCheck {
    private static int checks;
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
    private static void close(float expected,float actual,String message){check(Math.abs(expected-actual)<.0001f,message+": "+actual);}
    public static void main(String[] args) {
        CloudContentTransition transition=new CloudContentTransition();
        close(1,transition.sample(0),"an unused transition is fully clear");
        check(!transition.running(0),"an unused transition does not keep a clock");
        check(transition.begin(new String("first"),100,200),"first native revision starts blur-in");
        close(0,transition.sample(100),"new content begins at its blurred endpoint");
        close(0,transition.sample(90),"backward wall time stays within the starting endpoint");
        check(transition.running(100),"new content owns a clock");
        float previous=0;
        for(int time=100;time<=300;time++) {
            float phase=transition.sample(time);
            check(Float.isFinite(phase)&&phase>=previous&&phase>=0&&phase<=1,"native blur-in advances monotonically within legal bounds");
            previous=phase;
        }
        close(1,transition.sample(300),"native content is clear at configured duration");
        check(!transition.running(300),"completed content releases its clock");
        check(!transition.begin(new String("first"),400,200),"equal revision objects cannot restart a completed blur");
        close(1,transition.sample(400),"repeated pre-draw keeps completed content clear");
        check(transition.begin("second",500,200),"a different native revision starts a fresh transition");
        close(.5f,transition.sample(600),"half elapsed time reaches half visibility");
        check(!transition.begin(new String("second"),600,200),"equal token during animation preserves the running clock");
        close(.5f,transition.sample(600),"duplicate bind cannot restart blur halfway");
        check(transition.begin("third",600,200),"genuinely new content can replace an active transition");
        close(0,transition.sample(600),"a new revision receives its own blur-in");
        transition.finish();
        close(1,transition.sample(600),"finishing clears native content immediately");
        check(!transition.running(600),"finish releases the active clock");
        check(!transition.begin(new String("third"),700,200),"finish retains deduplication across pre-draws");
        transition.reset();
        check(transition.begin(new String("third"),800,200),"dispose reset permits a new lifecycle with the same revision");
        transition.finish();
        check(transition.begin("instant",900,0),"a zero-duration new revision is still recorded");
        close(1,transition.sample(900),"zero duration completes immediately");
        check(!transition.running(900),"zero duration never keeps a frame clock");
        check(!transition.begin(new String("instant"),901,200),"zero duration also retains its revision");
        verifyIndependentLayers();
        System.out.println("Verified "+checks+" native content transition time and revision checks.");
    }
    private static void verifyIndependentLayers() {
        var capsule=CloudContentTransition.layer(0,false,1);
        close(1,capsule.alpha(),"an outgoing settled capsule remains visible at form-transition start");
        close(0,capsule.blur(),"a settled capsule starts sharp");
        var card=CloudContentTransition.layer(1,true,1);
        close(1,card.alpha(),"an outgoing settled card does not blink blank when closing starts");
        close(0,card.blur(),"a settled card starts sharp");
        var outgoingCapsule=CloudContentTransition.layer(.6f,false,1);
        var incomingCard=CloudContentTransition.layer(.6f,true,0);
        close(.4f,outgoingCapsule.alpha(),"a new card revision cannot blank unchanged capsule content");
        close(0,incomingCard.alpha(),"the new card revision begins its own blur-in");
        close(1,incomingCard.blur(),"new card content starts blurred");
        var outgoingCard=CloudContentTransition.layer(.6f,true,1);
        var incomingCapsule=CloudContentTransition.layer(.6f,false,0);
        close(.6f,outgoingCard.alpha(),"a new capsule revision cannot blank unchanged card content");
        close(0,incomingCapsule.alpha(),"the new capsule revision begins independently");
        close(1,incomingCapsule.blur(),"new capsule content starts blurred");
        for(boolean cardSide:new boolean[]{false,true}) {
            var at=CloudContentTransition.layer(.6f,cardSide,1);
            var before=CloudContentTransition.layer(.6001f,cardSide,1);
            var after=CloudContentTransition.layer(.5999f,cardSide,1);
            check(Math.abs(before.alpha()-at.alpha())<.001f&&Math.abs(after.alpha()-at.alpha())<.001f&&
                Math.abs(before.blur()-at.blur())<.001f&&Math.abs(after.blur()-at.blur())<.001f,
                "content alpha and blur remain continuous when a form reverses direction");
        }
        var halfContent=CloudContentTransition.layer(1,true,.5f);
        close(.5f,halfContent.alpha(),"new content in an existing card fades independently of geometry");
        close(.5f,halfContent.blur(),"new content in an existing card clears blur with its own phase");
    }
}
