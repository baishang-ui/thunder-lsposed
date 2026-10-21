package com.oplus.systemui.plugins.seedling.util;
public class d extends android.graphics.drawable.Drawable {
 public Object g=new Object();public kotlin.Triple d;public int a,b,c,boundsChanges;
 public d(kotlin.Triple corners){d=corners;}public d(int top,int middle,int bottom,kotlin.Triple corners){a=top;b=middle;c=bottom;d=corners;}
 public void onBoundsChange(android.graphics.Rect bounds){boundsChanges++;}
}
