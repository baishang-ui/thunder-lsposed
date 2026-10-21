package android.graphics;
import java.util.*;
public class Canvas {
 public boolean hardware,failDraw;public int saves,restores,nodeDraws,lastAlpha;public final List<String> events=new ArrayList<>();
 public Canvas(){}public Canvas(boolean hw){hardware=hw;}public boolean isHardwareAccelerated(){return hardware;}
 public int saveLayerAlpha(Object bounds,int alpha){events.add("layer:"+alpha);lastAlpha=alpha;return ++saves;}
 public void restoreToCount(int save){events.add("restore");restores++;}
 public void drawRenderNode(RenderNode node){if(failDraw){failDraw=false;throw new IllegalStateException("backend failed");}nodeDraws++;events.add("node");}
}
