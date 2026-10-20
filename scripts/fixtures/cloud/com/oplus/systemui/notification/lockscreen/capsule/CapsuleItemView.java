package com.oplus.systemui.notification.lockscreen.capsule;
public class CapsuleItemView extends android.view.View {
    public Object entry;
    public Location location=new Location();
    public int roundnessRefreshes;
    public final PathProvider pathProvider=new PathProvider();
    // C17 onClipInfoChange writes the default radius directly, without getActualRadius.
    public void onClipInfoChange(CapsuleViewState.ScrollClipInfo info) {
        location.clip=info.rect;
        pathProvider.updateMetaData(47f,false,null);
    }
    public static class PathProvider {
        public float radius=47;
        public boolean smooth=true;
        public int writes;
        public void updateMetaData(Float radius,Boolean smooth,android.graphics.RectF rect) {
            if(radius!=null)this.radius=radius;
            if(smooth!=null)this.smooth=smooth;
            writes++;
        }
    }
    public float getActualRadius(){return 47;}
    public float getMaxRadius(){return 47;}
    public void applyRoundnessAndInvalidate(){roundnessRefreshes++;}
    public static class Location {
        public android.graphics.Rect clip=new android.graphics.Rect(0,0,400,200);
        public android.graphics.Rect getClipRect(){return clip;}
    }
    public Object getEntry() { return entry; }
}
