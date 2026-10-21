package io.github.baishangui.thunder;

/** Native card input handoff; deliberately independent of the clamped material/form fraction. */
final class CloudInputOwnership {
    private Object window,capsuleWindow;
    private boolean transitioning,expanded,handoff,dismissing;
    /** Content switches keep the native root's DOWN/MOVE/UP stream intact. */
    boolean capsuleRoot(Object value){if(capsuleWindow==value)return false;capsuleWindow=value;return true;}
    /** Same-window card rebinds preserve the lease through opening/closing reversals. */
    boolean bind(Object value) {
        if(window==value)return false;
        window=value;transitioning=false;expanded=false;handoff=false;dismissing=false;return true;
    }
    void begin(boolean cardTransition,boolean opening){
        if(cardTransition){transitioning=true;handoff=false;dismissing=!opening;}
        else {handoff|=transitioning||expanded;transitioning=false;expanded=false;}
    }
    /** Accepted dismissal releases Body input while native input stays disabled. */
    void dismiss(){dismissing=true;}
    void settle(boolean cardExpanded) {
        handoff=!cardExpanded&&(transitioning||expanded||handoff);
        transitioning=false;expanded=cardExpanded;dismissing=cardExpanded&&dismissing;
    }
    boolean acceptsInput(){return !dismissing;}
    boolean captureOutside(){return acceptsInput()&&(transitioning||expanded);}
    boolean keepLease(boolean nativeShown) {
        if(!nativeShown&&!transitioning&&!expanded)handoff=false;
        return nativeShown&&(transitioning||expanded||handoff);
    }
}
