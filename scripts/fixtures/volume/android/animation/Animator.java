package android.animation;

import java.util.ArrayList;

public class Animator {
    public interface AnimatorListener {
        default void onAnimationStart(Animator a){}
        default void onAnimationEnd(Animator a){}
        default void onAnimationCancel(Animator a){}
        default void onAnimationRepeat(Animator a){}
    }
    private final ArrayList<AnimatorListener> callbacks=new ArrayList<>();
    public void addListener(AnimatorListener l){callbacks.add(l);}
    public void removeListener(AnimatorListener l){callbacks.remove(l);}
    public ArrayList<AnimatorListener> getListeners(){return new ArrayList<>(callbacks);}
    protected void dispatchStart(){for(var l:getListeners())l.onAnimationStart(this);}
    protected void dispatchEnd(){for(var l:getListeners())l.onAnimationEnd(this);}
    protected void dispatchCancel(){for(var l:getListeners())l.onAnimationCancel(this);}
    public void start(){dispatchStart();}
    public void cancel(){dispatchCancel();dispatchEnd();}
}
