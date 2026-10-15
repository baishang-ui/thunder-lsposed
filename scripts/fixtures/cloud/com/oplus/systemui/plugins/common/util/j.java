package com.oplus.systemui.plugins.common.util;
public class j extends android.view.ViewOutlineProvider {
    public android.graphics.Rect a;
    public kotlin.Triple b;
    public j(android.graphics.Rect rect,kotlin.Triple corners){a=rect;b=corners;}
    public void getOutline(android.view.View view,android.graphics.Outline outline){
        outline.setRoundRect(a==null?0:a.left,a==null?0:a.top,a==null?view.getWidth():a.right,a==null?view.getHeight():a.bottom,((Number)b.e()).floatValue());
    }
}
