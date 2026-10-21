package io.github.baishangui.thunder;

/** Independent visibility geometry: shade changes never rewrite the form trajectory. */
final class CloudVisibilityState {
    private float motion=1f,host=1f;
    private boolean shown=true,hostAllowed=true,businessAllowed=true;
    private CloudMotion hostTrajectory;
    private CloudMorphHook.Frame hostVelocity;
    float motion(){return motion;}
    void motion(float value){motion=clamp(value);}
    float host(){return host;}
    void host(float value){host=clamp(value);hostTrajectory=null;hostVelocity=null;}
    void beginHost(float to,int duration,int curve) {
        hostTrajectory=new CloudMotion(phase(host),phase(clamp(to)),hostVelocity,duration,curve,0);
    }
    void sampleHost(float fraction) {
        if(hostTrajectory==null)return;
        host=hostTrajectory.frame(fraction).progress();
        hostVelocity=new CloudMorphHook.Frame(0,0,0,0,0,hostTrajectory.clampedPhaseVelocity(fraction));
    }
    void finishHost(){sampleHost(1);hostTrajectory=null;hostVelocity=null;}
    private static CloudMorphHook.Frame phase(float value){return new CloudMorphHook.Frame(0,0,1,1,0,value);}
    boolean shown(){return shown;}
    void shown(boolean value){shown=value;}
    boolean hostAllowed(){return hostAllowed;}
    void allowHost(boolean value){hostAllowed=value;}
    void allowBusiness(boolean value){businessAllowed=value;}
    boolean targetShown(){return hostAllowed&&businessAllowed;}
    float composed(){return motion*Math.min(1f,host*4f);}
    static CloudMorphHook.Frame presentation(CloudMorphHook.Frame logical,float amount) {
        if(logical==null)return null;
        float p=clamp(amount);
        if(p==1f)return logical;
        // A visibility gate has no destination in native screen coordinates.
        // Contract around this frame's center instead of fabricating an upward
        // target from its height. The host clock owns timing; keep this mapping
        // finite at both ends instead of adding another ease or sqrt singularity.
        float form=p,shortSide=Math.min(logical.width(),logical.height());
        float side=1f+(shortSide-1f)*form;
        float width=side+(logical.width()-shortSide)*form*form;
        float height=side+(logical.height()-shortSide)*form*form;
        float radius=side/2f+(logical.radius()*side/shortSide-side/2f)*form;
        return new CloudMorphHook.Frame(logical.x()+(logical.width()-width)/2f,
            logical.y()+(logical.height()-height)/2f,
            width,height,Math.min(radius,Math.min(width,height)/2f),logical.progress());
    }
    CloudMorphHook.Frame presentationVelocity(CloudMorphHook.Frame logical,CloudMorphHook.Frame logicalVelocity) {
        if(logical==null)return null;
        CloudMorphHook.Frame speed=logicalVelocity==null?new CloudMorphHook.Frame(0,0,0,0,0,0):logicalVelocity;
        float p=host,phaseSpeed=hostVelocity==null?0:hostVelocity.progress();
        // A boundary tangent can only point back into the visible interval.
        if(p==0&&phaseSpeed<0||p==1&&phaseSpeed>0)phaseSpeed=0;
        float shortSide=Math.min(logical.width(),logical.height());
        float shortSpeed=minVelocity(logical.width(),logical.height(),speed.width(),speed.height());
        float side=1+(shortSide-1)*p;
        float sideSpeed=shortSpeed*p+(shortSide-1)*phaseSpeed;
        float width=side+(logical.width()-shortSide)*p*p;
        float height=side+(logical.height()-shortSide)*p*p;
        float widthSpeed=sideSpeed+(speed.width()-shortSpeed)*p*p+2*(logical.width()-shortSide)*p*phaseSpeed;
        float heightSpeed=sideSpeed+(speed.height()-shortSpeed)*p*p+2*(logical.height()-shortSide)*p*phaseSpeed;
        float corner=(1-p)/2+p*logical.radius()/shortSide;
        float radius=side*corner;
        float radiusSpeed=sideSpeed*corner+side*((logical.radius()/shortSide-.5f)*phaseSpeed+
            p*(speed.radius()/shortSide-logical.radius()*shortSpeed/(shortSide*shortSide)));
        float limit=Math.min(width,height)/2;
        float limitSpeed=minVelocity(width,height,widthSpeed,heightSpeed)/2;
        return new CloudMorphHook.Frame(speed.x()+(speed.width()-widthSpeed)/2,
            speed.y()+(speed.height()-heightSpeed)/2,widthSpeed,heightSpeed,
            minVelocity(radius,limit,radiusSpeed,limitSpeed),speed.progress());
    }
    private static float minVelocity(float a,float b,float aSpeed,float bSpeed) {
        return a<b?aSpeed:a>b?bSpeed:Math.min(aSpeed,bSpeed);
    }
    boolean acceptsInput(){return shown&&composed()>.01f;}
    private static float clamp(float value){return Float.isFinite(value)?Math.max(0f,Math.min(1f,value)):0f;}
}
