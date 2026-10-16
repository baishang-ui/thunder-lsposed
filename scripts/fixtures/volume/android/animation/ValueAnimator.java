package android.animation;

import java.util.*;
import android.view.animation.PathInterpolator;

/** An explicit frame clock: tests advance real handler interpolation without sleeping. */
public final class ValueAnimator {
    public interface AnimatorUpdateListener {void onAnimationUpdate(ValueAnimator animator);}
    public static final List<ValueAnimator> running=new ArrayList<>();
    public final List<AnimatorUpdateListener> listeners=new ArrayList<>();
    public long duration;public boolean cancelled;float from,to,value;PathInterpolator interpolator;
    public static ValueAnimator ofFloat(float... values){ValueAnimator a=new ValueAnimator();a.from=values[0];a.to=values[values.length-1];return a;}
    public ValueAnimator setDuration(long v){duration=v;return this;}
    public void setInterpolator(PathInterpolator v){interpolator=v;}
    public void addUpdateListener(AnimatorUpdateListener v){listeners.add(v);}
    public Object getAnimatedValue(){return value;}
    public void removeAllUpdateListeners(){listeners.clear();}
    public void cancel(){cancelled=true;running.remove(this);}
    public void start(){running.add(this);advance(0);}
    public void advance(float t){
        value=from+(to-from)*(t==0||t==1?t:interpolator.getInterpolation(t));
        for(AnimatorUpdateListener l:new ArrayList<>(listeners))l.onAnimationUpdate(this);
        if(t>=1)running.remove(this);
    }
    public static void finishAll(){for(ValueAnimator a:new ArrayList<>(running))a.advance(1);}
}
