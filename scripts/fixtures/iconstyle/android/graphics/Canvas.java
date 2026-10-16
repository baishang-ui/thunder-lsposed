package android.graphics;
import java.util.*;
public class Canvas {
 public boolean hardware=true;public boolean isHardwareAccelerated(){return hardware;}
 public final List<float[]> rectangles=new ArrayList<>();private float sx=1,sy=1,tx,ty;private final List<float[]> transforms=new ArrayList<>();
 public int color,depth;public float textX,textY;public Paint.Align textAlign;public Paint.Style textStyle;public float textSize;public int clips;public final List<Paint.Style> styles=new ArrayList<>();public final List<String> operations=new ArrayList<>();public final List<Integer> colors=new ArrayList<>();
 public void drawColor(int c){color=c;}public int save(){transforms.add(new float[]{sx,sy,tx,ty});return ++depth;}public void restoreToCount(int count){float[] t=transforms.get(count-1);sx=t[0];sy=t[1];tx=t[2];ty=t[3];while(transforms.size()>=count)transforms.remove(transforms.size()-1);depth=count-1;}
 public void translate(float x,float y){tx+=x*sx;ty+=y*sy;}public void scale(float x,float y){sx*=x;sy*=y;}public void scale(float x,float y,float px,float py){translate(px,py);scale(x,y);translate(-px,-py);}
 public void clipRect(float l,float t,float r,float b){clips++;}
 public final List<Integer> alphas=new ArrayList<>();
 private void record(String type,Paint p){operations.add(type);colors.add(p.getColor());styles.add(p.style);alphas.add(p.getAlpha());}
 public final List<float[]> pathBounds=new ArrayList<>();
 public void drawPath(Path path,Paint paint){
  var matcher=java.util.regex.Pattern.compile("[-+]?(?:\\d*\\.)?\\d+(?:[eE][-+]?\\d+)?").matcher(path.data);
  List<Float> coords=new ArrayList<>();while(matcher.find())coords.add(Float.parseFloat(matcher.group()));
  float l=Float.MAX_VALUE,t=l,r=-l,b=-l;
  for(int i=0;i+1<coords.size();i+=2){float x=coords.get(i)*sx+tx,y=coords.get(i+1)*sy+ty;l=Math.min(l,x);r=Math.max(r,x);t=Math.min(t,y);b=Math.max(b,y);}
  pathBounds.add(new float[]{l,t,r,b});record("path:"+path.data,paint);
 }
 public void drawArc(float l,float t,float r,float b,float start,float sweep,boolean center,Paint paint){record("arc",paint);}
 public void drawCircle(float x,float y,float radius,Paint paint){record("circle",paint);}
 public void drawRoundRect(float l,float t,float r,float b,float rx,float ry,Paint paint){rectangles.add(new float[]{l*sx+tx,t*sy+ty,r*sx+tx,b*sy+ty,rx*sx,ry*sy});record("roundRect",paint);}
 public void drawText(String text,float x,float y,Paint paint){textX=x;textY=y;textAlign=paint.align;textSize=paint.textSize;record("text:"+text,paint);}
}
