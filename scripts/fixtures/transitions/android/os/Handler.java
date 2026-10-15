package android.os;import java.util.*;
public class Handler {public Handler(Object o){}public static int posts;public int delayedSchedules;public final List<Runnable> queue=new ArrayList<>(),delayed=new ArrayList<>();
 public boolean post(Runnable r){posts++;queue.add(r);return true;}public boolean postDelayed(Runnable r,long delay){delayedSchedules++;delayed.add(r);return true;}
 public boolean hasCallbacks(Runnable r){return queue.contains(r)||delayed.contains(r);}
 public void removeCallbacks(Runnable r){queue.removeIf(x->x==r);delayed.removeIf(x->x==r);}
 public void drain(){while(!queue.isEmpty())queue.remove(0).run();}
 public void drainDelayed(){var q=new ArrayList<>(delayed);delayed.clear();q.forEach(Runnable::run);drain();}
}
