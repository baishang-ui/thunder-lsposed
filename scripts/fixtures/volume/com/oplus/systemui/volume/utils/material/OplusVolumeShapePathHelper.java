package com.oplus.systemui.volume.utils.material;
public class OplusVolumeShapePathHelper {
    public static void buildClipPath(android.graphics.Path path,android.graphics.Rect b,float radius,float weight,boolean smooth) {
        path.radius=radius;path.bounds=new android.graphics.Rect(b.left,b.top,b.right,b.bottom);path.smooth=smooth&&weight>1;
    }
}
