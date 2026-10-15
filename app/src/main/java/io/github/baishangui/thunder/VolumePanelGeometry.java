package io.github.baishangui.thunder;

/** Physical screen coordinates. Native rows keep their own animation and touch transforms. */
final class VolumePanelGeometry {
    record Bounds(int x,int y,int width,int height,boolean opensLeft) { }
    static Bounds place(int left,int top,int right,int bottom,int anchorLeft,int anchorTop,
                        int anchorRight,int anchorBottom,int desiredWidth,int desiredHeight,int padding) {
        int width=Math.max(1,Math.min(desiredWidth,right-left));
        int height=Math.max(1,Math.min(desiredHeight,bottom-top));
        boolean opensLeft=anchorLeft-left>=right-anchorRight;
        // Enclose the starting bar: expansion fans inward from the existing sidebar.
        int x=opensLeft?anchorRight+padding-width:anchorLeft-padding;
        x=Math.max(left,Math.min(right-width,x));
        int y=Math.max(top,Math.min(bottom-height,anchorTop-padding));
        return new Bounds(x,y,width,height,opensLeft);
    }
    static float rowX(Bounds b,int index,int count,int rowWidth,int inset,int gap) {
        int n=Math.max(1,count),i=Math.max(0,Math.min(n-1,index));
        float available=Math.max(0,b.width()-2f*inset-rowWidth);
        float step=n==1?0:Math.min(rowWidth+gap,available/(n-1));
        float group=rowWidth+step*(n-1);
        return b.x()+(b.width()-group)/2f+i*step;
    }
    static float translation(float target,float screenPosition,float currentTranslation) {
        return target-(screenPosition-currentTranslation);
    }
}
