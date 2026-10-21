package io.github.baishangui.thunder;

/** Pure production trajectory checks, including inherited velocity at a reversal. */
public final class CloudMotionCheck {
    private static int checks;
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
    private static CloudMorphHook.Frame frame(float width){return new CloudMorphHook.Frame(100,20,width,80,40,0);}
    private static CloudMorphHook.Frame phase(float progress){return new CloudMorphHook.Frame(0,0,1,1,0,progress);}
    public static void main(String[] args){
        var linear=new CloudMotion(frame(200),frame(800),null,500,3,0);
        check(Math.abs(linear.frame(.5f).width()-500)<.001,"constant-speed curve reaches halfway at half duration");
        var nextLinear=new CloudMotion(frame(800),frame(200),linear.velocity(1),500,3,0);
        check(Math.abs(nextLinear.frame(.5f).width()-500)<.001,"consecutive constant-speed transitions remain linear after settling");
        var mini=new CloudMorphHook.Frame(457.5f,10,45,45,22.5f,0);
        var arrivingCapsule=new CloudMorphHook.Frame(360,10,240,80,40,0);
        var compensatedZero=new CloudMorphHook.Frame(0,0,0,0,0,0);
        var miniHandoff=new CloudMotion(mini,arrivingCapsule,compensatedZero,500,3,0,0,true);
        check(miniHandoff.velocity(0).equals(compensatedZero),"a square handoff preserves an explicitly compensated zero velocity");
        check(Math.abs((miniHandoff.frame(.0001f).width()-mini.width())/.00005f)<.5f,
            "a new service does not jump to constant speed when the mini handoff cancels the independent gate velocity");
        check(miniHandoff.frame(1).equals(arrivingCapsule),"zero-speed mini handoff still reaches the new capsule endpoint");
        var forward=new CloudMotion(frame(200),frame(800),null,500,2,0);
        float last=200;
        for(int i=0;i<=100;i++){
            var f=forward.frame(i/100f);check(f.width()>=last-.001f&&f.width()<=800,"zero bounce is monotonic");last=f.width();
        }
        var current=forward.frame(.25f);var velocity=forward.velocity(.25f);
        var reverse=new CloudMotion(current,frame(200),velocity,500,2,18);
        check(reverse.frame(0).equals(current),"retarget starts at last presented geometry");
        check(Math.abs(reverse.velocity(0).width()-velocity.width())<.01,"retarget keeps incoming velocity");
        float numerical=(reverse.frame(.0001f).width()-current.width())/.00005f;
        check(Math.abs(numerical-velocity.width())<30,"actual initial derivative follows inherited velocity");
        check(reverse.frame(1).equals(frame(200)),"exact endpoint");
        for(int curve=0;curve<3;curve++)for(int bounceValue:new int[]{0,18,100}){
            var arrival=new CloudMotion(frame(200),frame(800),null,500,curve,bounceValue);
            check(Math.abs(arrival.frame(.9999f).width()-800)<.0001f,"spring converges continuously before endpoint: curve="+curve+";bounce="+bounceValue);
            check(Math.abs(arrival.velocity(.9999f).width())<.01f,"spring velocity settles continuously before endpoint");
        }
        var sameSize=frame(240);
        var entry=new CloudMotion(sameSize,sameSize,null,500,2,0,.08f);
        check(entry.frame(0).equals(sameSize)&&entry.frame(1).equals(sameSize),"entry pulse preserves both endpoint rectangles");
        check(entry.velocity(0).width()==0&&entry.velocity(1).width()==0,"entry pulse starts and finishes at rest");
        for(int i=0;i<=100;i++){
            var sample=entry.frame(i/100f);
            check(sample.width()>=240*.97f-.001f&&sample.width()<=240,"entry compression is capped at three percent");
            float scale=sample.width()/240;
            check(Math.abs(sample.height()-80*scale)<.001f&&Math.abs(sample.radius()-40*scale)<.001f,"entry compression keeps the capsule proportions");
            check(Math.abs(sample.x()+sample.width()/2-220)<.001f&&Math.abs(sample.y()+sample.height()/2-60)<.001f,"entry compression preserves the screen center");
        }
        var pulseAt=entry.frame(.25f);var pulseSpeed=entry.velocity(.25f);
        var interruptedEntry=new CloudMotion(pulseAt,sameSize,pulseSpeed,500,2,0);
        check(interruptedEntry.frame(0).equals(pulseAt)&&interruptedEntry.velocity(0).equals(pulseSpeed),"a pulse interruption inherits the same geometry and velocity");
        var card=new CloudMorphHook.Frame(100,120,800,400,28,1);
        var capsule=new CloudMorphHook.Frame(360,10,240,80,40,0);
        for(int curve=0;curve<4;curve++)for(int bounceValue:new int[]{0,18,100}){
            var reveal=new CloudMotion(capsule,card,null,500,curve,bounceValue);
            float previousProgress=0;
            for(int i=0;i<=100;i++){
                float progress=reveal.frame(i/100f).progress();
                check(progress>=previousProgress-.00001f,"card material and reveal never retract while geometry rebounds");
                previousProgress=progress;
            }
        }
        for(int curve=0;curve<4;curve++)for(int bounceValue:new int[]{0,18,100}){
            var close=new CloudMotion(card,capsule,null,500,curve,bounceValue);
            for(int i=0;i<=100;i++){
                var sample=close.frame(i/100f);
                check(sample.width()>=240-.001f&&sample.height()>=80-.001f,"closing spring must not squeeze the capsule below its target dimensions");
                check(sample.x()>=100-.001f&&sample.x()<=360+.001f&&sample.y()>=10-.001f&&sample.y()<=120+.001f,
                    "closing rebound must not carry the card past the capsule position");
                float ratio=sample.width()/sample.height();
                check(ratio>=2-.001f&&ratio<=3+.001f,"closing geometry preserves a regular aspect ratio through the capsule endpoint");
            }
            check(close.frame(1).equals(capsule),"bounded closure still reaches exact native capsule geometry");
        }
        var nearCapsule=new CloudMorphHook.Frame(350,14,245,82,40,0);
        var fastIn=new CloudMorphHook.Frame(0,0,-3000,-1800,0,0);
        for(int curve=0;curve<4;curve++)for(int bounceValue:new int[]{0,18,100}){
            var retargetClose=new CloudMotion(nearCapsule,capsule,fastIn,500,curve,bounceValue);
            check(retargetClose.frame(0).equals(nearCapsule)&&retargetClose.velocity(0).equals(fastIn),"late close retarget inherits its exact presented state and velocity");
            float initialSlope=(retargetClose.frame(.00001f).height()-nearCapsule.height())/.000005f;
            check(Math.abs(initialSlope-fastIn.height())<30,"bounded geometry preserves the actual initial derivative");
            for(int i=0;i<=100;i++){
                var sample=retargetClose.frame(i/100f);
                check(sample.width()>=240-.001f&&sample.height()>=80-.001f,"late close retarget brakes inherited speed before squeezing past the capsule");
            }
        }
        var fastPosition=new CloudMorphHook.Frame(10000,-12000,-3000,-1800,0,0);
        var fastClose=new CloudMotion(nearCapsule,capsule,fastPosition,500,2,100);
        check(fastClose.velocity(0).equals(fastPosition),"closing keeps incoming screen-space velocity before braking");
        for(int i=0;i<=100;i++){
            var sample=fastClose.frame(i/100f);
            check(sample.x()>=350-.001f&&sample.x()<=360+.001f&&sample.y()>=10-.001f&&sample.y()<=14+.001f,
                "late positional retarget brakes before overshooting the native capsule");
        }
        var lowerCard=new CloudMorphHook.Frame(100,760,800,400,28,1);
        check(CloudCardTiming.follower(capsule,card,lowerCard,0).equals(capsule),"hidden followers occupy the actual current capsule");
        check(CloudCardTiming.follower(card,card,lowerCard,1).equals(lowerCard),"fully revealed followers occupy their own native card positions");
        var shrinkingLead=new CloudMotion(card,capsule,null,500,2,100);
        for(int i=0;i<=100;i++){
            var lead=shrinkingLead.frame(i/100f);
            var parked=CloudCardTiming.follower(lead,card,lowerCard,0);
            check(parked.width()<=lead.width()+.001f&&parked.height()<=lead.height()+.001f,
                "a gathering follower fits underneath the changing lead instead of retaining its expanded size");
            check(Math.abs(parked.x()+parked.width()/2-lead.x()-lead.width()/2)<.001f,
                "the parked follower tracks the lead center throughout contraction");
            check(parked.y()>=lead.y()-.001f&&parked.y()<=lead.y()+lead.height()/2,
                "the gathering anchor stays underneath the lead as the lead moves toward the capsule");
            var revealing=CloudCardTiming.follower(lead,card,lowerCard,.5f);
            check(revealing.width()<=lead.width()+.001f&&revealing.height()<=lead.height()+.001f,
                "a follower cannot expand its dimensions ahead of the first card while emerging");
        }
        for(int curve=0;curve<4;curve++)for(int bounceValue:new int[]{0,18,100}){
            var leadOpen=new CloudMotion(capsule,card,null,500,curve,bounceValue);
            var revealOpen=new CloudMotion(phase(0),phase(1),null,500,curve,bounceValue);
            float at=.25f,epsilon=.0001f;
            var leadAt=leadOpen.frame(at);var revealAt=revealOpen.frame(at);
            var leadReverse=new CloudMotion(leadAt,capsule,leadOpen.velocity(at),500,curve,bounceValue);
            var revealReverse=new CloudMotion(revealAt,phase(0),revealOpen.velocity(at),500,curve,bounceValue);
            var before=CloudCardTiming.follower(leadAt,card,lowerCard,revealAt.progress());
            var restarted=CloudCardTiming.follower(leadReverse.frame(0),card,lowerCard,revealReverse.frame(0).progress());
            check(before.equals(restarted),"follower reversals preserve the exact presented frame");
            var ahead=CloudCardTiming.follower(leadOpen.frame(at+epsilon),card,lowerCard,revealOpen.frame(at+epsilon).progress());
            var continued=CloudCardTiming.follower(leadReverse.frame(epsilon),card,lowerCard,revealReverse.frame(epsilon).progress());
            check(Math.abs(ahead.x()-continued.x())<.01f&&Math.abs(ahead.y()-continued.y())<.01f&&
                Math.abs(ahead.width()-continued.width())<.01f&&Math.abs(ahead.height()-continued.height())<.01f,
                "the dynamic follower anchor inherits lead and reveal velocity at reversal");
        }
        var bounce=new CloudMotion(frame(200),frame(800),null,500,2,100);
        boolean overshoot=false;
        for(int i=0;i<=100;i++){
            var f=bounce.frame(i/100f);overshoot|=f.width()>800;
            check(Float.isFinite(f.width())&&f.width()>0&&f.radius()<=f.height()/2f,"finite legal dimensions at maximum bounce");
        }
        check(overshoot,"positive bounce has spring overshoot");
        System.out.println("Verified "+checks+" continuous cloud trajectory checks.");
    }
}
