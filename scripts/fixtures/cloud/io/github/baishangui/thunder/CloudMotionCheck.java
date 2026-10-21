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
        verifyArrivalFeedback();
        verifyNativeArrivalExcursion();
        System.out.println("Verified "+checks+" continuous cloud trajectory checks.");
    }
    private static void verifyNativeArrivalExcursion(){
        var capsule=new CloudMorphHook.Frame(360,10,240,80,40,0);
        var point=CloudVisibilityState.entrance(capsule);
        var near=new CloudMorphHook.Frame(361,10,238,80,40,0);
        for(int curve=0;curve<4;curve++)for(int bounce:new int[]{0,100})for(var from:new CloudMorphHook.Frame[]{capsule,near,point}){
            var base=CloudMotion.arrival(from,capsule,null,500,curve,bounce);
            var arrival=CloudMotion.arrival(from,capsule,null,500,curve,bounce,false,16,0);
            check(arrival.frame(0).equals(from)&&arrival.frame(1).equals(capsule),"actual app excursion preserves both native endpoint frames");
            check(arrival.velocity(0).equals(base.velocity(0))&&arrival.velocity(1).equals(base.velocity(1)),"actual app excursion retains native entry boundary velocities");
            check(arrival.frame(.5f).width()>base.frame(.5f).width()+8,"an actual app event retains a visible native width excursion for matching, nearby, and first-entry sizes");
            for(int i=1;i<100;i++){
                float at=i/100f;var sample=arrival.frame(at);var plain=base.frame(at);
                check(Float.isFinite(sample.width())&&sample.width()>=1&&sample.height()>=1&&sample.radius()<=Math.min(sample.width(),sample.height())/2+.00001f,
                    "native app excursion has finite, legal capsule dimensions: curve="+curve+";at="+at+";frame="+sample);
                check(Math.abs(sample.x()+sample.width()/2-plain.x()-plain.width()/2)<.001f&&
                    Math.abs(sample.y()+sample.height()/2-plain.y()-plain.height()/2)<.001f,"native app excursion preserves the actual entry center");
                check(Math.abs(sample.height()-plain.height())<.001f,"native width excursion does not invent vertical entry movement");
                closeVelocity(arrival.velocity(at),delta(sample,arrival.frame(at+.00005f),.000025f),20,"native app excursion publishes its full visible velocity");
            }
            for(float at:new float[]{.12f,.3f,.5f,.7f,.9f}){
                var current=arrival.frame(at);var speed=arrival.velocity(at);
                int remaining=Math.round(500*(1-at));
                var retarget=CloudMotion.arrival(current,capsule,speed,remaining,curve,bounce,false,16,at);
                check(retarget.frame(0).equals(current)&&retarget.velocity(0).equals(speed),"same-event layout retarget inherits the complete excursion state");
                float step=.00005f*500/remaining;
                closeVelocity(speed,delta(current,retarget.frame(step),.000025f),20,"same-event retarget preserves its visible tangent without repeating an entry impulse curve="+curve+";bounce="+bounce+";at="+at+";from="+from+";current="+current);
                check(retarget.frame(1).equals(capsule),"same-event retarget completes at the actual native destination");
            }
            var closed=CloudMotion.arrival(from,capsule,base.velocity(0),500,curve,bounce,true,16,0);
            var closedBase=CloudMotion.arrival(from,capsule,base.velocity(0),500,curve,bounce,true);
            for(int i=0;i<=100;i++)check(closed.frame(i/100f).equals(closedBase.frame(i/100f)),"closed-host handoff does not add an independent visible app excursion");
        }
        var zero=new CloudMorphHook.Frame(0,0,0,0,0,0);
        var pastPeak=new CloudMorphHook.Frame(352,10,256,80,40,0);
        var linearRetarget=CloudMotion.arrival(pastPeak,capsule,zero,250,3,0,false,16,.5f);
        check(linearRetarget.velocity(0).equals(zero),"a linear same-event retarget preserves an explicit zero at the native excursion turning point");
        var remaining=CloudMotion.arrival(pastPeak,capsule,zero,250,0,0,false,16,.5f);
        var replayed=CloudMotion.arrival(pastPeak,capsule,zero,250,0,0,false,16,0);
        check(remaining.frame(.5f).width()<replayed.frame(.5f).width()-8,"same-event retarget carries the original event phase instead of breathing through a second full excursion");
        var finished=CloudMotion.arrival(capsule,capsule,null,500,0,0,false,16,1);
        for(int i=0;i<=100;i++)check(finished.frame(i/100f).equals(capsule),"completed app event layout changes cannot replay its width excursion");
        var fast=CloudMotion.arrival(capsule,capsule,null,400,0,0,false,16,0);
        var slow=CloudMotion.arrival(capsule,capsule,null,800,0,0,false,16,0);
        check(fast.frame(.15f).width()<slow.frame(.15f).width()-.01f,"faster native event travel produces stronger reverse deformation without app gesture velocity");
    }
    private static void verifyArrivalFeedback(){
        var capsule=new CloudMorphHook.Frame(360,10,240,80,40,0);
        var point=CloudVisibilityState.entrance(capsule);
        for(int curve=0;curve<4;curve++)for(int bounce:new int[]{0,100}){
            var base=new CloudMotion(point,capsule,null,500,curve,bounce);
            var arrival=CloudMotion.arrival(point,capsule,null,500,curve,bounce);
            check(arrival.frame(0).equals(base.frame(0))&&arrival.frame(1).equals(base.frame(1)),"speed feedback preserves exact entry endpoint frames");
            check(arrival.velocity(0).equals(base.velocity(0))&&arrival.velocity(1).equals(base.velocity(1)),"speed feedback preserves both endpoint velocities");
            var still=CloudMotion.arrival(capsule,capsule,null,500,curve,bounce);
            for(int i=0;i<=100;i++){
                float at=i/100f;
                var frame=arrival.frame(at);var unmodified=base.frame(at);
                check(still.frame(at).equals(capsule),"a stationary velocity-only trajectory does not create a separate app event");
                check(frame.width()>=unmodified.width()*.8999f&&frame.width()<=unmodified.width()*1.1001f&&
                    frame.height()>=unmodified.height()*.8999f&&frame.height()<=unmodified.height()*1.1001f,"speed feedback stays bounded at every configured curve and bounce");
                check(Math.abs(frame.x()+frame.width()/2-unmodified.x()-unmodified.width()/2)<.001f&&
                    Math.abs(frame.y()+frame.height()/2-unmodified.y()-unmodified.height()/2)<.001f,"reverse deformation retains the actual trajectory center");
                check(Math.abs(frame.radius()-Math.min(frame.width(),frame.height())/2)<.001f,"speed deformation retains native semicircular capsule ends");
                if(i>0&&i<100){
                    float epsilon=.00005f,dt=epsilon*.5f;
                    closeVelocity(arrival.velocity(at),delta(frame,arrival.frame(at+epsilon),dt),15,"published feedback velocity matches the actual deformed microstep");
                }
            }
            for(float at:new float[]{.12f,.3f,.7f,.82f}){
                var frame=arrival.frame(at);var speed=arrival.velocity(at);
                var retarget=CloudMotion.arrival(frame,capsule,speed,350,curve,bounce);
                check(retarget.frame(0).equals(frame)&&retarget.velocity(0).equals(speed),"retargeting a speed deformation preserves its actual frame and velocity");
                var expected=arrival.frame(at+.00005f);
                var continued=retarget.frame(.00005f*.5f/.35f);
                check(Math.abs(expected.x()-continued.x())<.01f&&Math.abs(expected.y()-continued.y())<.01f&&
                    Math.abs(expected.width()-continued.width())<.01f&&Math.abs(expected.height()-continued.height())<.01f&&
                    Math.abs(expected.radius()-continued.radius())<.01f,"restarting the feedback boundary window does not reset the presented deformation");
            }
        }
        for(int curve=0;curve<4;curve++){
            var fast=CloudMotion.arrival(point,capsule,null,400,curve,0);
            var slow=CloudMotion.arrival(point,capsule,null,800,curve,0);
            var base=new CloudMotion(point,capsule,null,400,curve,0);
            check(base.frame(.15f).width()-fast.frame(.15f).width()>base.frame(.15f).width()-slow.frame(.15f).width()+.01f,
                "the same trajectory deforms more at higher actual pixel speed");
        }
        var smaller=new CloudMorphHook.Frame(400,20,160,60,30,0);
        var shrinkBase=new CloudMotion(capsule,smaller,null,500,0,0);
        var shrink=CloudMotion.arrival(capsule,smaller,null,500,0,0);
        check(shrink.frame(.15f).width()>shrinkBase.frame(.15f).width(),"a shrinking arrival deforms against its own direction instead of always compressing");
        var translated=new CloudMorphHook.Frame(560,10,240,80,40,0);
        var travelBase=new CloudMotion(capsule,translated,null,500,0,0);
        var travel=CloudMotion.arrival(capsule,translated,null,500,0,0);
        check(travel.frame(.15f).width()<travelBase.frame(.15f).width(),"real center travel also produces bounded inertial deformation");
        var mini=new CloudMorphHook.Frame(457.5f,10,45,45,22.5f,0);
        var zero=new CloudMorphHook.Frame(0,0,0,0,0,0);
        var miniArrival=CloudMotion.arrival(mini,capsule,zero,500,3,0,true);
        check(miniArrival.velocity(0).equals(zero),"arrival feedback preserves an explicitly compensated zero handoff");
    }
    private static CloudMorphHook.Frame delta(CloudMorphHook.Frame from,CloudMorphHook.Frame to,float seconds){
        return new CloudMorphHook.Frame((to.x()-from.x())/seconds,(to.y()-from.y())/seconds,
            (to.width()-from.width())/seconds,(to.height()-from.height())/seconds,
            (to.radius()-from.radius())/seconds,(to.progress()-from.progress())/seconds);
    }
    private static void closeVelocity(CloudMorphHook.Frame expected,CloudMorphHook.Frame actual,float tolerance,String message){
        float[] a={expected.x(),expected.y(),expected.width(),expected.height(),expected.radius(),expected.progress()};
        float[] b={actual.x(),actual.y(),actual.width(),actual.height(),actual.radius(),actual.progress()};
        for(int i=0;i<a.length;i++)check(Float.isFinite(a[i])&&Math.abs(a[i]-b[i])<tolerance,message+" channel="+i+" expected="+a[i]+" actual="+b[i]);
    }
}
