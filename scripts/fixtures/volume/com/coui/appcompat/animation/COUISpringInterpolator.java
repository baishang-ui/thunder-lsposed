package com.coui.appcompat.animation;
public class COUISpringInterpolator {
 public final double response,bounce;
 public COUISpringInterpolator(double r,double b){response=r;bounce=b;}
 // Reference equation from this device's COUISpringInterpolator, including
 // its final-value normalization; used to verify real entrance phase timing.
 public float getInterpolation(float phase){return (float)(origin(phase)/origin(1));}
 private double origin(double phase){
  double frequency=2*Math.PI/response,damping=1-bounce;
  double angular=frequency*Math.sqrt(1-damping*damping);
  return 1-Math.exp(-damping*frequency*phase)*(Math.cos(angular*phase)+damping*frequency/angular*Math.sin(angular*phase));
 }
}
