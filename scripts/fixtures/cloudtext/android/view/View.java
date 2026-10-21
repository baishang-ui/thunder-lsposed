package android.view;
import java.util.*;
public class View {
 public static final int NO_ID=-1;public View parent;public int id=NO_ID,invalidations;public float alpha=1;public Object effect;
 public final List<Runnable> callbacks=new ArrayList<>();
 public int getId(){return id;}public Object getParent(){return parent;}public int getWidth(){return 100;}public int getHeight(){return 40;}
 public android.content.res.Resources getResources(){return new android.content.res.Resources();}
 public void invalidate(){invalidations++;}public void postOnAnimation(Runnable r){callbacks.add(r);}public void removeCallbacks(Runnable r){callbacks.remove(r);}
 public void tick(){var copy=new ArrayList<>(callbacks);callbacks.clear();for(Runnable r:copy)r.run();}
}
