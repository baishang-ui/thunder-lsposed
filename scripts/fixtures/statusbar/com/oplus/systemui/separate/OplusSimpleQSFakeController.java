package com.oplus.systemui.separate;
import android.view.ViewGroup;
import android.widget.TextView;

/** Relevant native C17 equations, with coordinates exposed for regression tests. */
public class OplusSimpleQSFakeController {
 public static class Header {public TextView clock;public TextView getClockView(){return clock;}}
 public static class Fake extends android.view.View {public android.view.View clock;public android.view.View getMView(){return clock;}}
 public Header oplusQSSimpleHeader=new Header();public ViewGroup fakeClockViewContainer=new ViewGroup();
 public float statusbarClockPadding=10,clockScaleFactor=2,systemFontScale=1;
 public boolean isLargeDisplay,isUpdateFakeViewLocation;
 public float fakeToClockTranslationX,fakeToClockTranslationY;
 public float sourceX=80,targetX=30,sourceCenterY=20,targetCenterY=120;
 public void updateClockPivot$1(){
  var q=oplusQSSimpleHeader.clock;boolean rtl=q.getLayoutDirection()==1;
  q.setPivotX(rtl?q.getWidth():0);q.setPivotY(q.getBaseline());
  fakeClockViewContainer.setPivotX(rtl?fakeClockViewContainer.getWidth()-statusbarClockPadding:statusbarClockPadding);
  fakeClockViewContainer.setPivotY(q.getBaseline()+(fakeClockViewContainer.getHeight()-q.getHeight())/2f);
 }
 public static void access$updateFakeToStatusBarTranslation(OplusSimpleQSFakeController c){
  c.fakeToClockTranslationX=c.sourceX+(c.oplusQSSimpleHeader.clock.getLayoutDirection()==1?c.fakeClockViewContainer.getWidth()-c.statusbarClockPadding: c.statusbarClockPadding)
   -(c.targetX+(c.oplusQSSimpleHeader.clock.getLayoutDirection()==1?c.oplusQSSimpleHeader.clock.getWidth():0));
  c.fakeToClockTranslationY=c.targetCenterY-c.sourceCenterY;
 }
 private float scale(float f){float s=1+(clockScaleFactor+systemFontScale-1)*f;return isLargeDisplay?Math.min(3,s):s;}
 public void updateFakeClockScale(float f){fakeClockViewContainer.setScaleX(scale(f));fakeClockViewContainer.setScaleY(scale(f));}
 public void updateQSClockScale(float f){oplusQSSimpleHeader.clock.setScaleX(scale(f));oplusQSSimpleHeader.clock.setScaleY(scale(f));}
 public float notificationTranslationY;
 public static void access$updateFakeSimpleQSTranslation(OplusSimpleQSFakeController c,float f){
  c.fakeClockViewContainer.setTranslationX(-c.fakeToClockTranslationX*f);
  c.fakeClockViewContainer.setTranslationY(c.fakeToClockTranslationY*f);
  c.notificationTranslationY=c.fakeToClockTranslationY*f;
 }
 public static void access$updateSimpleQSTranslation(OplusSimpleQSFakeController c,float f){
  c.oplusQSSimpleHeader.clock.setTranslationX(c.fakeToClockTranslationX*(1-f));
  c.oplusQSSimpleHeader.clock.setTranslationY(-c.fakeToClockTranslationY*(1-f));
 }
 public static void access$resetfakeViewsStartLocation(OplusSimpleQSFakeController c){}
 public static void access$resetQsViewsExpandLocation(OplusSimpleQSFakeController c){}
}
