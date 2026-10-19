package com.oplus.systemui.notification.lockscreen.capsule.stroke;
public class StrokeLayer {
    private float radius;
    public float drawnRadius;
    public float getCornerRadius(){return radius;}
    public void setCornerRadius(float value){radius=value;}
    public void draw(){drawnRadius=radius;}
}
