package com.oplus.systemui.notification.lockscreen.capsule;
public class CapsuleBackgroundView extends android.view.View {
 public CapsuleBackgroundHelper capsuleHelper=new CapsuleBackgroundHelper();
 public float strokeRadius=47;public boolean strokeSmooth;
 public void setStrokeRadius(float radius,boolean smooth){strokeRadius=radius;strokeSmooth=smooth;}
}
