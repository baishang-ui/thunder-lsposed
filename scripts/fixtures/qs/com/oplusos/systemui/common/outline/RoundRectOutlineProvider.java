package com.oplusos.systemui.common.outline;

public class RoundRectOutlineProvider extends android.view.ViewOutlineProvider {
 public float cornerRadius; public Float cornerWeight;
 public RoundRectOutlineProvider(float radius,Float weight){update(radius,weight);}
 public void update(float radius,Float weight){cornerRadius=radius;cornerWeight=weight;}
 public float getCornerRadius(){return cornerRadius;}
 public Float getCornerWeight(){return cornerWeight;}
 public Float getCornerWeight(android.graphics.drawable.Drawable d){return cornerWeight;}
 public Object adapterUsed;public boolean smoothPath;
 public void updatePath(android.graphics.drawable.Drawable d,android.graphics.Path path,Object oplusPath,Object adapter){path.radius=cornerRadius;smoothPath=cornerWeight!=null&&cornerWeight>1f&&adapter!=null;adapterUsed=adapter;path.bounds=d.getBounds();path.smooth=smoothPath;}
 public void getOutline(android.view.View view,android.graphics.Outline outline){outline.setRoundRect(0,0,view.getWidth(),view.getHeight(),cornerRadius);outline.smooth=cornerWeight!=null&&cornerWeight>1f;}
}
