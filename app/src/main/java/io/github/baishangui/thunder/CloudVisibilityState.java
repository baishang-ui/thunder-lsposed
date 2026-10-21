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
    static CloudMorphHook.Frame entrance(CloudMorphHook.Frame goal) {
        if(goal==null)return null;
        // First arrival keeps its independent point-to-capsule shape. A hide
        // only folds width and must not redefine the first entrance rectangle.
        return new CloudMorphHook.Frame(goal.x()+(goal.width()-1)/2,goal.y()+(goal.height()-1)/2,1,1,.5f,goal.progress());
    }
    static CloudMorphHook.Frame hidden(CloudMorphHook.Frame frame){return presentation(frame,0);}
    static CloudMorphHook.Frame shapeForWidth(CloudMorphHook.Frame start,CloudMorphHook.Frame sampled) {
        // A disappearing capsule changes width, keeping its original rounded
        // ends until the new short side itself limits the native radius.
        float radius=Math.min(start.radius(),Math.min(sampled.width(),sampled.height())/2f);
        return new CloudMorphHook.Frame(sampled.x(),sampled.y(),sampled.width(),sampled.height(),radius,sampled.progress());
    }
    static CloudMorphHook.Frame velocityForWidth(CloudMorphHook.Frame start,CloudMorphHook.Frame sampled,CloudMorphHook.Frame velocity) {
        float limit=Math.min(sampled.width(),sampled.height())/2f;
        float limitSpeed=minVelocity(sampled.width(),sampled.height(),velocity.width(),velocity.height())/2f;
        float radiusSpeed=minVelocity(start.radius(),limit,0,limitSpeed);
        return new CloudMorphHook.Frame(velocity.x(),velocity.y(),velocity.width(),velocity.height(),radiusSpeed,velocity.progress());
    }
    static CloudMorphHook.Frame presentation(CloudMorphHook.Frame logical,float amount) {
        if(logical==null)return null;
        float p=clamp(amount);
        if(p==1f)return logical;
        // Host visibility folds width around the current horizontal center.
        // Height and the vertical anchor belong to the logical form trajectory.
        // The host clock supplies timing once for this reversible mapping.
        float width=1+(logical.width()-1)*p,height=logical.height();
        return new CloudMorphHook.Frame(logical.x()+(logical.width()-width)/2f,
            logical.y(),width,height,Math.min(logical.radius(),Math.min(width,height)/2f),logical.progress());
    }
    CloudMorphHook.Frame presentationVelocity(CloudMorphHook.Frame logical,CloudMorphHook.Frame logicalVelocity) {
        if(logical==null)return null;
        CloudMorphHook.Frame speed=logicalVelocity==null?new CloudMorphHook.Frame(0,0,0,0,0,0):logicalVelocity;
        float p=host,phaseSpeed=hostVelocity==null?0:hostVelocity.progress();
        // A boundary tangent can only point back into the visible interval.
        if(p==0&&phaseSpeed<0||p==1&&phaseSpeed>0)phaseSpeed=0;
        float width=1+(logical.width()-1)*p,height=logical.height();
        float widthSpeed=speed.width()*p+(logical.width()-1)*phaseSpeed;
        float heightSpeed=speed.height();
        float limit=Math.min(width,height)/2;
        float limitSpeed=minVelocity(width,height,widthSpeed,heightSpeed)/2;
        return new CloudMorphHook.Frame(speed.x()+(speed.width()-widthSpeed)/2,
            speed.y(),widthSpeed,heightSpeed,
            minVelocity(logical.radius(),limit,speed.radius(),limitSpeed),speed.progress());
    }
    private static float minVelocity(float a,float b,float aSpeed,float bSpeed) {
        return a<b?aSpeed:a>b?bSpeed:Math.min(aSpeed,bSpeed);
    }
    boolean acceptsInput(){return shown&&composed()>.01f;}
    private static float clamp(float value){return Float.isFinite(value)?Math.max(0f,Math.min(1f,value)):0f;}
}
