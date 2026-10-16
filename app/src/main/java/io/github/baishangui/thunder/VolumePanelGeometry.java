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
    static int fitTrackWidth(int available,int count,int desired,int spacing) {
        int n=Math.max(1,count);
        return Math.max(1,Math.min(desired,(available-(n+1)*spacing)/n));
    }
    static int panelWidth(int count,int trackWidth,int spacing) {
        int n=Math.max(1,count);
        return n*trackWidth+(n+1)*spacing;
    }
    static float trackRowX(Bounds b,int index,int trackWidth,int holderWidth,int spacing) {
        // Native holders include transparent press/elevation padding. Measure gaps from
        // the painted track, not the holder, or inner gaps and outer padding diverge.
        return b.x()+spacing+Math.max(0,index)*(trackWidth+spacing)-(holderWidth-trackWidth)/2f;
    }
    static float progress(float x,float y,float fromX,float fromY,float toX,float toY,float fallback) {
        float dx=toX-fromX,dy=toY-fromY,distance=dx*dx+dy*dy;
        return clamp(distance<1?fallback:((x-fromX)*dx+(y-fromY)*dy)/distance);
    }
    static float clamp(float value) {return Math.max(0f,Math.min(1f,value));}
    static float mix(float from,float to,float progress) {return from+(to-from)*clamp(progress);}
    static Bounds widthAt(Bounds end,int startWidth,float progress) {
        int width=Math.round(mix(Math.min(end.width(),Math.max(1,startWidth)),end.width(),progress));
        return new Bounds(end.opensLeft()?end.x()+end.width()-width:end.x(),end.y(),width,end.height(),end.opensLeft());
    }
    static float translation(float target,float screenPosition,float currentTranslation) {
        return target-(screenPosition-currentTranslation);
    }
}
