package io.github.baishangui.thunder;

/** Independent visibility geometry: shade changes never rewrite the form trajectory. */
final class CloudVisibilityState {
    private float motion=1f,host=1f;
    private boolean shown=true,hostAllowed=true,businessAllowed=true;
    float motion(){return motion;}
    void motion(float value){motion=clamp(value);}
    float host(){return host;}
    void host(float value){host=clamp(value);}
    boolean shown(){return shown;}
    void shown(boolean value){shown=value;}
    void allowHost(boolean value){hostAllowed=value;}
    void allowBusiness(boolean value){businessAllowed=value;}
    boolean targetShown(){return hostAllowed&&businessAllowed;}
    float composed(){return motion*Math.min(1f,host*4f);}
    static CloudMorphHook.Frame presentation(CloudMorphHook.Frame logical,float amount) {
        if(logical==null)return null;
        float p=clamp(amount);
        if(p==1f)return logical;
        float dot=Math.max(1f,Math.min(logical.width(),logical.height())*.18f);
        float width=dot+(logical.width()-dot)*p;
        float height=dot+(logical.height()-dot)*(float)Math.sqrt(p);
        float radius=dot/2f+(logical.radius()-dot/2f)*p;
        return new CloudMorphHook.Frame(logical.x()+(logical.width()-width)/2f,
            logical.y()+(logical.height()-height)/2f-logical.height()*.6f*(1f-p),
            width,height,Math.min(radius,Math.min(width,height)/2f),logical.progress());
    }
    boolean acceptsInput(){return shown&&composed()>.01f;}
    private static float clamp(float value){return Float.isFinite(value)?Math.max(0f,Math.min(1f,value)):0f;}
}
