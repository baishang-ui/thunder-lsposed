package io.github.baishangui.thunder;

/** Production opacity state: interrupted shade fades must not rewrite entry/exit motion. */
public final class CloudVisibilityStateCheck {
    static void close(float expected,float actual){if(Math.abs(expected-actual)>.0001f)throw new AssertionError(expected+" != "+actual);}
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args) {
        CloudVisibilityState state=new CloudVisibilityState();
        state.motion(.6f);state.host(.7f);close(.6f,state.composed());
        state.shown(false);close(.6f,state.motion());close(.7f,state.host());
        check(!state.acceptsInput(),"shade exit must stop input before the first faded frame");
        state.host(.35f);state.shown(true);close(.35f,state.host());close(.6f,state.composed());
        check(state.acceptsInput(),"interrupted fade-in must keep the sampled alpha");
        state.host(1);close(.6f,state.composed());
        state.motion(0);check(!state.acceptsInput(),"entry opacity zero must not capture touches");
        state.motion(2);state.host(-1);close(0,state.composed());
        state.host(Float.NaN);state.motion(Float.NaN);close(0,state.composed());
        state.allowHost(false);state.allowBusiness(false);state.allowBusiness(true);
        check(!state.targetShown(),"a business show cannot override the shade's host hide");
        state.allowHost(true);check(state.targetShown(),"host show resumes only when business is also visible");
        state.allowBusiness(false);check(!state.targetShown(),"a hidden capsule stays hidden after host show");
        state.allowBusiness(true);check(state.targetShown(),"card transition can clear only the business gate");
        CloudMorphHook.Frame capsule=new CloudMorphHook.Frame(360,10,240,80,40,0);
        check(CloudVisibilityState.presentation(capsule,1).equals(capsule),"fully shown geometry is untouched");
        CloudMorphHook.Frame middle=CloudVisibilityState.presentation(capsule,.5f);
        check(middle.width()<180&&middle.height()<80,"shade hiding changes width and height before the alpha tail");
        close(capsule.x()+capsule.width()/2,middle.x()+middle.width()/2);
        close(20.75f,CloudVisibilityState.presentation(capsule,.25f).height());
        close(60.25f,CloudVisibilityState.presentation(capsule,.75f).height());
        CloudMorphHook.Frame hidden=CloudVisibilityState.presentation(capsule,0);
        close(hidden.width(),hidden.height());close(hidden.height()/2,hidden.radius());
        close(capsule.x()+capsule.width()/2,hidden.x()+hidden.width()/2);
        close(capsule.y()+capsule.height()/2,hidden.y()+hidden.height()/2);
        check(hidden.y()>=capsule.y(),"a local hide must not invent a target above the source capsule");
        for(CloudMorphHook.Frame shape:new CloudMorphHook.Frame[]{capsule,
            new CloudMorphHook.Frame(48,92,800,120,8,0),new CloudMorphHook.Frame(120,80,80,240,0,0)}){
            float previousWidth=0,previousHeight=0;
            for(int i=0;i<=1000;i++){
                var sample=CloudVisibilityState.presentation(shape,i/1000f);
                close(shape.x()+shape.width()/2,sample.x()+sample.width()/2);
                close(shape.y()+shape.height()/2,sample.y()+sample.height()/2);
                check(sample.width()>=previousWidth&&sample.height()>=previousHeight,"hide geometry converges without an aspect-ratio reversal");
                check(sample.width()<=shape.width()&&sample.height()<=shape.height()&&sample.radius()<=Math.min(sample.width(),sample.height())/2,
                    "all intermediate outlines stay within the original centered envelope");
                previousWidth=sample.width();previousHeight=sample.height();
            }
            float epsilon=.0001f;
            var nearHidden=CloudVisibilityState.presentation(shape,epsilon);
            var point=CloudVisibilityState.presentation(shape,0);
            check(Math.abs(nearHidden.width()-point.width())/epsilon<shape.width()*3&&Math.abs(nearHidden.height()-point.height())/epsilon<shape.height()*3,
                "hidden endpoint has a finite tangent instead of a square-root singularity");
            var nearShown=CloudVisibilityState.presentation(shape,1-epsilon);
            check(Math.abs(nearShown.width()-shape.width())/epsilon<shape.width()*3&&Math.abs(nearShown.height()-shape.height())/epsilon<shape.height()*3,
                "geometry remains bounded while the host clock supplies its settling velocity");
        }
        CloudMorphHook.Frame interrupted=CloudVisibilityState.presentation(capsule,.35f);
        check(interrupted.equals(CloudVisibilityState.presentation(capsule,.35f)),"interrupted hide/show samples the same presented geometry");
        state.motion(1);state.host(.5f);close(1,state.composed());
        state.host(.125f);close(.5f,state.composed());
        for(int curve=0;curve<4;curve++){
            CloudVisibilityState host=new CloudVisibilityState();host.host(0);host.beginHost(1,500,curve);
            CloudMotion incoming=new CloudMotion(phase(0),phase(1),null,500,curve,0);
            float at=.25f,epsilon=.0001f;
            host.sampleHost(at);float before=host.host();
            var beforeFrame=CloudVisibilityState.presentation(capsule,before);
            var expectedNext=CloudVisibilityState.presentation(capsule,incoming.frame(at+epsilon).progress());
            host.beginHost(0,500,curve);host.sampleHost(0);
            close(before,host.host());
            check(beforeFrame.equals(CloudVisibilityState.presentation(capsule,host.host())),"host reversal preserves its presented rectangle");
            host.sampleHost(epsilon);
            var continued=CloudVisibilityState.presentation(capsule,host.host());
            check(Math.abs(expectedNext.width()-continued.width())<.01f&&Math.abs(expectedNext.height()-continued.height())<.01f,
                "host reversal inherits phase speed through the geometry mapping");
            for(int i=1;i<=1000;i++){
                host.sampleHost(i/1000f);
                var sample=CloudVisibilityState.presentation(capsule,host.host());
                check(Float.isFinite(sample.width())&&Float.isFinite(sample.height())&&sample.width()>=1&&sample.height()>=1,
                    "composed host geometry stays finite through a full interrupted hide");
                close(480,sample.x()+sample.width()/2);close(50,sample.y()+sample.height()/2);
            }
            host.finishHost();close(0,host.host());
            host.beginHost(1,500,curve);host.sampleHost(epsilon);
            close(incoming.frame(epsilon).progress(),host.host());
            host.host(.5f);host.sampleHost(1);close(.5f,host.host());
            if(curve!=3){
                var start=CloudVisibilityState.presentation(capsule,incoming.frame(0).progress());
                var nearStart=CloudVisibilityState.presentation(capsule,incoming.frame(.00001f).progress());
                var nearEnd=CloudVisibilityState.presentation(capsule,incoming.frame(.99999f).progress());
                check(Math.abs(nearStart.width()-start.width())<.0001f&&Math.abs(nearEnd.width()-capsule.width())<.0001f,
                    "the host clock supplies resting endpoint speed without a second geometry ease");
            }
        }
        verifyPresentationVelocity();
        System.out.println("CloudVisibilityStateCheck: host/motion composition and interrupted input checks passed");
    }
    private static void verifyPresentationVelocity() {
        CloudMorphHook.Frame[] shapes={new CloudMorphHook.Frame(360,10,240,80,40,0),
            new CloudMorphHook.Frame(120,80,80,240,8,.4f),new CloudMorphHook.Frame(24,16,100,100,20,.5f)};
        CloudMorphHook.Frame[] speeds={new CloudMorphHook.Frame(12,-7,-30,15,7.5f,.4f),
            new CloudMorphHook.Frame(-8,9,15,-30,2,-.2f),new CloudMorphHook.Frame(4,6,20,-10,3,.1f)};
        float epsilon=.0001f,seconds=.5f,dt=epsilon*seconds;
        for(int curve=0;curve<4;curve++)for(int direction=0;direction<2;direction++){
            float from=direction,to=1-direction;
            CloudMotion trajectory=new CloudMotion(phase(from),phase(to),null,500,curve,0);
            for(float at:new float[]{.1f,.25f,.5f,.85f})for(int i=0;i<shapes.length;i++){
                CloudVisibilityState host=new CloudVisibilityState();host.host(from);host.beginHost(to,500,curve);host.sampleHost(at);
                var logical=shapes[i];var logicalSpeed=speeds[i];
                var projected=host.presentationVelocity(logical,logicalSpeed);
                var current=CloudVisibilityState.presentation(logical,host.host());
                var next=CloudVisibilityState.presentation(advance(logical,logicalSpeed,dt),trajectory.frame(at+epsilon).progress());
                closeVelocity(projected,delta(current,next,dt),1.5f,"host and logical derivatives compose through size, center and clamped radius");
                var stillProjected=host.presentationVelocity(logical,null);
                var stillNext=CloudVisibilityState.presentation(logical,trajectory.frame(at+epsilon).progress());
                closeVelocity(stillProjected,delta(current,stillNext,dt),1.5f,"a stationary capsule keeps its host contraction speed during ownership handoff");
                CloudMotion handedOff=new CloudMotion(current,logical,stillProjected,500,curve,0);
                closeVelocity(stillProjected,handedOff.velocity(0),.0001f,"handoff begins with the presentation derivative instead of the stale logical speed");
            }
        }
        CloudVisibilityState resting=new CloudVisibilityState();
        closeVelocity(speeds[0],resting.presentationVelocity(shapes[0],speeds[0]),.0001f,"a resting open host leaves logical velocity unchanged");
        resting.host(0);
        var hiddenSpeed=resting.presentationVelocity(shapes[0],speeds[0]);
        close(0,hiddenSpeed.width());close(0,hiddenSpeed.height());close(0,hiddenSpeed.radius());
        close(speeds[0].x()+speeds[0].width()/2,hiddenSpeed.x());
        close(speeds[0].y()+speeds[0].height()/2,hiddenSpeed.y());
        // A fast reversal can briefly carry the raw phase past its clamp. Its
        // visible derivative is zero until it comes back inside the interval.
        for(int direction=0;direction<2;direction++){
            CloudVisibilityState clipped=new CloudVisibilityState();
            clipped.host(direction);clipped.beginHost(1-direction,500,3);clipped.sampleHost(.94f);
            clipped.beginHost(direction,500,3);
            for(float at:new float[]{.091f,.18f}){
                clipped.sampleHost(at);close(1-direction,clipped.host());
                closeVelocity(new CloudMorphHook.Frame(0,0,0,0,0,0),clipped.presentationVelocity(shapes[0],null),.0001f,
                    "a saturated phase is stationary before and after its raw spring turns back inward");
                closeVelocity(clipped.presentationVelocity(shapes[0],null),
                    delta(CloudVisibilityState.presentation(shapes[0],clipped.host()),nextHostFrame(clipped,shapes[0],at+epsilon),dt),
                    .0001f,"saturated presentation velocity agrees with the next visible microstep");
            }
        }
    }
    private static CloudMorphHook.Frame nextHostFrame(CloudVisibilityState host,CloudMorphHook.Frame shape,float fraction) {
        host.sampleHost(fraction);return CloudVisibilityState.presentation(shape,host.host());
    }
    private static CloudMorphHook.Frame advance(CloudMorphHook.Frame frame,CloudMorphHook.Frame speed,float seconds) {
        return new CloudMorphHook.Frame(frame.x()+speed.x()*seconds,frame.y()+speed.y()*seconds,
            frame.width()+speed.width()*seconds,frame.height()+speed.height()*seconds,
            frame.radius()+speed.radius()*seconds,frame.progress()+speed.progress()*seconds);
    }
    private static CloudMorphHook.Frame delta(CloudMorphHook.Frame from,CloudMorphHook.Frame to,float seconds) {
        return new CloudMorphHook.Frame((to.x()-from.x())/seconds,(to.y()-from.y())/seconds,
            (to.width()-from.width())/seconds,(to.height()-from.height())/seconds,
            (to.radius()-from.radius())/seconds,(to.progress()-from.progress())/seconds);
    }
    private static void closeVelocity(CloudMorphHook.Frame expected,CloudMorphHook.Frame actual,float tolerance,String message) {
        float[] a={expected.x(),expected.y(),expected.width(),expected.height(),expected.radius(),expected.progress()};
        float[] b={actual.x(),actual.y(),actual.width(),actual.height(),actual.radius(),actual.progress()};
        for(int i=0;i<a.length;i++)check(Float.isFinite(b[i])&&Math.abs(a[i]-b[i])<=tolerance,
            message+"; channel="+i+" expected="+a[i]+" actual="+b[i]);
    }
    private static CloudMorphHook.Frame phase(float value){return new CloudMorphHook.Frame(0,0,1,1,0,value);}
}
