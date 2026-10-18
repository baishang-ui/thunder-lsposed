package com.oplus.posteffect.drawable;
import android.content.Context;
import android.graphics.PointF;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
public class MetaBallBlurDrawable extends Drawable {
 public final com.oplus.posteffect.agsl.DrawableShader drawableShader=new com.oplus.posteffect.agsl.DrawableShader();
 public com.oplus.posteffect.agsl.DrawableShader getDrawableShader(){return drawableShader;}
 public int color;public int[] colors;public android.graphics.drawable.GradientDrawable.Orientation orientation;
 public void setMetaColor(int value){color=value;setGradientColors(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,new int[]{value});}
 public void setGradientColors(android.graphics.drawable.GradientDrawable.Orientation direction,int[] values){orientation=direction;colors=values.clone();}
 public int range,blendRange,index=1,draws,rangeChanges,submissions,boundsChanges,copies;public boolean removed;
 public MetaBall mainBall;
 public MetaBallBlurDrawable lastCopy;
 public boolean shader;
 public boolean getEnableShader(){return shader;}
 public void setEnableBlurShader(boolean enabled){shader=enabled;}
 public final java.util.List<MetaBall> balls=new java.util.ArrayList<>();
 public void setBlendMode(int i){}
 public void setBlendRange(int r){if(range!=r)rangeChanges++;range=blendRange=r;}
 public void setBounds(android.graphics.Rect b){setBounds(b.left,b.top,b.right,b.bottom);}
 public void setBounds(int l,int t,int r,int b){super.setBounds(l,t,r,b);boundsChanges++;if(mainBall==null)mainBall=new MetaBall();}
 public void setMainBallIndex(int i){index=i;}
 public void clearMetaBalls(){balls.clear();}
 public MetaBall addMetaBall(int i,PointF p,PointF size,float corner,float weight,float blend,float x,float y){MetaBall b=new MetaBall();b.x=p.x;b.y=p.y;b.posX=p.x;b.posY=p.y;b.width=size.x;b.height=size.y;balls.add(b);return b;}
 public void removeMetaBall(MetaBall b){balls.remove(b);}
 public void invalidateParam(MetaBall b){submissions++;}
 public void syncToShader(){submissions++;}
 public MetaBallBlurDrawable copy(Context c){copies++;lastCopy=new MetaBallBlurDrawable();return lastCopy;}
 public void removeBlurDrawable(boolean fromManager){if(fromManager)throw new AssertionError("must unregister own capture");removed=true;}
 public void draw(Canvas c){draws++;}
 public static class MetaBall {
  public float x,y,posX,posY,width,height,blend,blendShape,corner;
  public float shapeRangeFactorX=1.2f,shapeRangeFactorY=2f;
  public void updatePosition(float x,float y){this.x=x;this.y=y;posX=x;posY=y;}
  public void updateSize(float w,float h){width=w;height=h;}
  public void setBlendShape(float b){blend=b;}
 }
}
