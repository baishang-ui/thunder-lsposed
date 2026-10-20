package com.oplus.systemui.notification.lockscreen.capsule.stroke;
public class StrokeLayer {
    private float radius;
    private float weight=1;public float drawnWeight;
    public float getWeight(){return weight;}
    public void setWeight(float value){weight=value;}
    public float drawnRadius;
    public float getCornerRadius(){return radius;}
    public void setCornerRadius(float value){radius=value;}
    public void draw(){drawnRadius=radius;drawnWeight=weight;}
}
