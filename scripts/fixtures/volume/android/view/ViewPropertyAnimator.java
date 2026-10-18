package android.view;
public class ViewPropertyAnimator {public boolean cancelled;public long delay;public void cancel(){cancelled=true;}public ViewPropertyAnimator setStartDelay(long value){delay=value;return this;}}
