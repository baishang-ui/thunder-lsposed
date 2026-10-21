package io.github.baishangui.thunder;

import java.util.Objects;

/** An explicit content revision owns one blur-in, independently of layout/draw traversals. */
final class CloudContentTransition {
    record Layer(float alpha,float blur) { }
    private Object revision;
    private boolean hasRevision,active;
    private long started;
    private int duration;

    boolean begin(Object revision,long nowMs,int durationMs) {
        if(hasRevision&&Objects.equals(this.revision,revision))return false;
        this.revision=revision;hasRevision=true;started=nowMs;duration=Math.max(0,durationMs);
        active=duration>0;return true;
    }
    float sample(long nowMs) {
        if(!active)return 1f;
        float raw=Math.max(0,Math.min(1,(nowMs-started)/(float)duration));
        if(raw>=1){active=false;return 1f;}
        return raw*raw*(3f-2f*raw);
    }
    boolean running(long nowMs){sample(nowMs);return active;}
    void finish(){active=false;}
    void reset(){finish();revision=null;hasRevision=false;}
    static Layer layer(float form,boolean card,float phase) {
        float p=Math.max(0,Math.min(1,form)),content=Math.max(0,Math.min(1,phase));
        float weight=card?p:1f-p;
        // Each source has its own revision phase. Reversing a shape transition
        // changes neither source phase, so both layers retain their opacity.
        return new Layer(weight*content,Math.max(1f-weight,1f-content));
    }
}
