package com.oplus.systemui.plugins.common.util;
public class k {
    public static void c(android.view.View v,android.graphics.Rect r,float scale,kotlin.Triple corners,int flags){
        if((flags&8)!=0||scale<=0)scale=1;
        v.setOutlineProvider(new j(r,new kotlin.Triple(corners.d(),((Number)corners.e()).floatValue()/scale,corners.f())));
        v.setClipToOutline(true);
    }
}
