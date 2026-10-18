package com.coui.appcompat.animation.dynamicanimation;
import android.view.View;
public class COUISpringAnimation {
    public final View view;public float target;public boolean cancelled;public int starts;
    public static final java.util.List<COUISpringAnimation> running=new java.util.ArrayList<>();
    public COUISpringAnimation(View view,Object property,float end){this.view=view;}
    public void setSpring(COUISpringForce force){}
    public void animateToFinalPosition(float next){target=next;starts++;cancelled=false;if(!running.contains(this))running.add(this);}
    public void cancel(){cancelled=true;running.remove(this);}
    public void advance(float t){view.setAlpha(view.getAlpha()+(target-view.getAlpha())*t);if(t==1)running.remove(this);}
    public static void finishAll(){for(COUISpringAnimation a:new java.util.ArrayList<>(running))a.advance(1);}
}
