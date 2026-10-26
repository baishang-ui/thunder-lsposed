package android.view;
public class ViewTreeObserver {
 public interface OnPreDrawListener {boolean onPreDraw();}
 public interface OnGlobalLayoutListener {void onGlobalLayout();}
 public java.util.List<OnGlobalLayoutListener> layoutListeners=new java.util.ArrayList<>();
 public void addOnGlobalLayoutListener(OnGlobalLayoutListener listener){layoutListeners.add(listener);}
 public void removeOnGlobalLayoutListener(OnGlobalLayoutListener listener){layoutListeners.remove(listener);}
 public void dispatchOnGlobalLayout(){for(OnGlobalLayoutListener listener:new java.util.ArrayList<>(layoutListeners))listener.onGlobalLayout();}
 public java.util.List<OnPreDrawListener> listeners=new java.util.ArrayList<>();
 public boolean isAlive(){return true;}
 public void addOnPreDrawListener(OnPreDrawListener listener){listeners.add(listener);}
 public void removeOnPreDrawListener(OnPreDrawListener listener){listeners.remove(listener);}
 public void dispatchOnPreDraw(){for(OnPreDrawListener listener:new java.util.ArrayList<>(listeners))listener.onPreDraw();}
}
