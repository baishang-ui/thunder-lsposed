package com.oplus.posteffect.params;
public class CornerParams {
 private final CornerType type;private final float radius,weight;
 public CornerParams(CornerType type,float radius,float weight){this.type=type;this.radius=radius;this.weight=weight;}
 public CornerType getType(){return type;}public float getRadius(){return radius;}public float getWeight(){return weight;}
}
