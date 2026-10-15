package android.view;
public class ViewTreeObserver {
 public interface OnPreDrawListener {boolean onPreDraw();}
 public java.util.List<OnPreDrawListener> listeners=new java.util.ArrayList<>();
 public boolean isAlive(){return true;}
 public void addOnPreDrawListener(OnPreDrawListener listener){listeners.add(listener);}
 public void removeOnPreDrawListener(OnPreDrawListener listener){listeners.remove(listener);}
 public void dispatchOnPreDraw(){for(OnPreDrawListener listener:new java.util.ArrayList<>(listeners))listener.onPreDraw();}
}
