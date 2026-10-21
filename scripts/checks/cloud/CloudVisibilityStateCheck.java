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
        CloudMorphHook.Frame hidden=CloudVisibilityState.presentation(capsule,0);
        close(hidden.width(),hidden.height());close(hidden.height()/2,hidden.radius());
        check(hidden.y()+hidden.height()/2<capsule.y()+capsule.height()/2,"hidden form retracts upward");
        CloudMorphHook.Frame interrupted=CloudVisibilityState.presentation(capsule,.35f);
        check(interrupted.equals(CloudVisibilityState.presentation(capsule,.35f)),"interrupted hide/show samples the same presented geometry");
        state.motion(1);state.host(.5f);close(1,state.composed());
        state.host(.125f);close(.5f,state.composed());
        System.out.println("CloudVisibilityStateCheck: host/motion composition and interrupted input checks passed");
    }
}
