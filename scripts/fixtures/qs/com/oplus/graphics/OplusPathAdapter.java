package com.oplus.graphics;
public class OplusPathAdapter {
 public final android.graphics.Path path;
 public OplusPathAdapter(android.graphics.Path path,int style){this.path=path;}
 public void addSmoothRoundRect(android.graphics.RectF rect,float[] radii,android.graphics.Path.Direction direction,float weight){path.radius=radii[0];path.bottomRadius=radii[4];path.smooth=true;path.weight=weight;}
}
