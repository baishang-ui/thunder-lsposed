package android.view;

import android.animation.ValueAnimator;

public class ViewPropertyAnimator {
    public boolean cancelled;
    public long delay;
    public ValueAnimator.AnimatorUpdateListener update;
    public void cancel(){cancelled=true;}
    public ViewPropertyAnimator setStartDelay(long value){delay=value;return this;}
    public ViewPropertyAnimator setUpdateListener(ValueAnimator.AnimatorUpdateListener listener){update=listener;return this;}
    public void frame(long time,long duration){
        if(update==null)return;
        ValueAnimator clock=new ValueAnimator().setDuration(duration);
        clock.setCurrentPlayTime(time);update.onAnimationUpdate(clock);
    }
}
