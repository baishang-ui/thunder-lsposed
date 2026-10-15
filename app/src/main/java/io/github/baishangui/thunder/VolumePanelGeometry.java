package io.github.baishangui.thunder;

/** Screen coordinates in pixels. Keep the entire popup inside the usable display. */
final class VolumePanelGeometry {
    record Bounds(int x,int y,int width,int height,boolean opensLeft) { }
    static Bounds place(int left,int top,int right,int bottom,int anchorLeft,int anchorTop,
                        int anchorRight,int anchorBottom,int desiredWidth,int desiredHeight,int gap) {
        int width=Math.max(1,Math.min(desiredWidth,right-left));
        int height=Math.max(1,Math.min(desiredHeight,bottom-top));
        boolean opensLeft=anchorLeft-left>=right-anchorRight;
        int x=opensLeft?anchorLeft-gap-width:anchorRight+gap;
        x=Math.max(left,Math.min(right-width,x));
        int y=Math.max(top,Math.min(bottom-height,(anchorTop+anchorBottom-height)/2));
        return new Bounds(x,y,width,height,opensLeft);
    }
}
