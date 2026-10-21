package android.view;
import java.util.*;
public class ViewGroup extends View {
 public final List<View> children=new ArrayList<>();public void addView(View v){children.add(v);v.parent=this;}
 public int getChildCount(){return children.size();}public View getChildAt(int i){return children.get(i);}
}
