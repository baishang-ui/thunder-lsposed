package android.window;
import java.util.*;import android.graphics.*;import android.view.SurfaceControl;
public class TransitionInfo {
 public List<Change> changes=new ArrayList<>(List.of(new Change(1,1),new Change(2,4)));
 public int type=1,flags;
 public List<Change> getChanges(){return changes;}public int getType(){return type;}public int getFlags(){return flags;}
 public int getRootCount(){return 1;}public Root getRoot(int i){return new Root();}
 public static class Root {public int getDisplayId(){return 0;}public Point getOffset(){return new Point();}}
 public static class Task {public int type;public int taskId=42;public int displayId=0;public Task(int t){type=t;}public int getActivityType(){return type;}public int getWindowingMode(){return 1;}}
 public static class Change {
  public SurfaceControl leash=new SurfaceControl();public Rect start=com.android.server.wm.WindowManagerService.bounds(),end=com.android.server.wm.WindowManagerService.bounds();public Task task;public int mode;
  public Change(int type,int mode){task=new Task(type);this.mode=mode;}
  public Task getTaskInfo(){return task;}public int getMode(){return mode;}public int getFlags(){return 0;}public Object getParent(){return null;}
  public int getStartRotation(){return 0;}public int getEndRotation(){return 0;}
  public Rect getStartAbsBounds(){return start;}public Rect getEndAbsBounds(){return end;}public SurfaceControl getLeash(){return leash;}
 }
}
