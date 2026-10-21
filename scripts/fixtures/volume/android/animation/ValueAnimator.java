package android.animation;

import java.util.*;
import android.view.animation.PathInterpolator;

/** An explicit frame clock: tests advance real handler interpolation without sleeping. */
public final class ValueAnimator extends Animator {
    public static float durationScale=1;
    public static float getDurationScale(){return durationScale;}
    public interface AnimatorUpdateListener {void onAnimationUpdate(ValueAnimator animator);}
    public static final List<ValueAnimator> running=new ArrayList<>();
    public final List<AnimatorUpdateListener> listeners=new ArrayList<>();
    public long duration;public boolean cancelled,paused;float from,to,value;PathInterpolator interpolator;
    public void pause(){paused=true;}public void resume(){paused=false;}public boolean isPaused(){return paused;}
    public void setCurrentPlayTime(long time){if(time==0)value=from;}
    public static ValueAnimator ofFloat(float... values){ValueAnimator a=new ValueAnimator();a.from=values[0];a.to=values[values.length-1];return a;}
    public ValueAnimator setDuration(long v){duration=v;return this;}
    public void setInterpolator(PathInterpolator v){interpolator=v;}
    public void addUpdateListener(AnimatorUpdateListener v){listeners.add(v);}
    public Object getAnimatedValue(){return value;}
    public void removeAllUpdateListeners(){listeners.clear();}
    public void cancel(){cancelled=true;running.remove(this);super.cancel();}
    public void end(){paused=false;value=to;for(var l:new ArrayList<>(listeners))l.onAnimationUpdate(this);running.remove(this);dispatchEnd();}
    public void start(){cancelled=false;paused=false;running.add(this);dispatchStart();advance(0);}
    public void advance(float t){
        if(paused||cancelled)return;
        value=from+(to-from)*(t==0||t==1?t:interpolator.getInterpolation(t));
        for(AnimatorUpdateListener l:new ArrayList<>(listeners))l.onAnimationUpdate(this);
        if(t>=1){running.remove(this);dispatchEnd();}
    }
    public static void finishAll(){for(ValueAnimator a:new ArrayList<>(running))a.advance(1);}
}
