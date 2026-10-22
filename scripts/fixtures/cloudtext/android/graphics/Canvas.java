package android.graphics;
import java.util.*;
public class Canvas {
 public boolean hardware,failDraw;public int saves,restores,nodeDraws,lastAlpha;public final List<String> events=new ArrayList<>();
 public Canvas(Bitmap bitmap){}public void drawBitmap(Bitmap bitmap,float x,float y,Object paint){events.add("bitmap");}
 public Canvas(){}public Canvas(boolean hw){hardware=hw;}public boolean isHardwareAccelerated(){return hardware;}
 public int save(){events.add("save");return ++saves;}
 public void clipOutPath(Path path){events.add("out:"+path.start+":"+path.end);}
 public void clipPath(Path path){events.add("clip:"+path.start+":"+path.end);}
 public void translate(float x,float y){events.add("translate:"+x+":"+y);}
 public int saveLayerAlpha(Object bounds,int alpha){events.add("layer:"+alpha);lastAlpha=alpha;return ++saves;}
 public void restoreToCount(int save){events.add("restore");restores++;}
 public void drawRenderNode(RenderNode node){if(failDraw){failDraw=false;throw new IllegalStateException("backend failed");}nodeDraws++;events.add("node");}
}
