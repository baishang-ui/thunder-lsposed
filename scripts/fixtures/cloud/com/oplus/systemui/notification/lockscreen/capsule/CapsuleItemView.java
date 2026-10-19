package com.oplus.systemui.notification.lockscreen.capsule;
public class CapsuleItemView extends android.view.View {
    public Object entry;
    public Location location=new Location();
    public int roundnessRefreshes;
    public float getActualRadius(){return 47;}
    public float getMaxRadius(){return 47;}
    public void applyRoundnessAndInvalidate(){roundnessRefreshes++;}
    public static class Location {
        public android.graphics.Rect clip=new android.graphics.Rect(0,0,400,200);
        public android.graphics.Rect getClipRect(){return clip;}
    }
    public Object getEntry() { return entry; }
}
