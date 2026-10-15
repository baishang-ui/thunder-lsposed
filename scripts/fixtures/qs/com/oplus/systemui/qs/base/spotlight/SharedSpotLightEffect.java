package com.oplus.systemui.qs.base.spotlight;
public class SharedSpotLightEffect {
 public static java.util.Set<android.view.View> updated=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
 public static int updates;public static android.view.View last;
 public static void draw(android.view.View view,android.graphics.Canvas canvas){}
 public static void updateBounds(android.view.View view,int width,int height){updates++;last=view;updated.add(view);}
}
