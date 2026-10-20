package io.github.baishangui.thunder;
import android.view.View;
import android.graphics.drawable.GradientDrawable;
/** Android foreground invalidation synchronously rebuilds the same view outline. */
public final class CloudTemplateReentryCheck {
 static int checks;
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);checks++;}
 static final class Stroke extends GradientDrawable {
  View owner;int depth,maxDepth,writes;boolean failNext;
  void changed(){
   writes++;depth++;maxDepth=Math.max(maxDepth,depth);
   try {
    if(depth>4)throw new AssertionError("foreground setCornerRadius recursively re-enters pageRadius");
    if(failNext){failNext=false;throw new IllegalStateException("native drawable setter failed");}
    if(owner!=null)CloudTemplateHook.pageRadius(owner);
   }finally{depth--;}
  }
  @Override public void setCornerRadius(float radius){super.setCornerRadius(radius);changed();}
  @Override public void setCornerRadii(float[] radii){super.setCornerRadii(radii);changed();}
 }
 public static void main(String[] args)throws Throwable {
  CloudHook.install(CloudTemplateReentryCheck.class.getClassLoader());
  var b=new android.os.Bundle();b.putBoolean("enabled",true);b.putBoolean("cloud_enabled",true);
  b.putInt("cloud_card_color",0);b.putBoolean("cloud_card_corner_enabled",true);b.putInt("cloud_card_corner",13);CloudCheck.config(b);
  View root=new View();root.parent=new com.oplus.systemui.plugins.seedling.card.ui.view.CardView();
  Stroke first=new Stroke();first.setCornerRadius(117);root.foreground=first;first.owner=root;
  var page=new com.oplus.systemui.plugins.shared.template.page.entity.t(root);
  CloudCheck.after("com.oplus.systemui.plugins.shared.template.page.entity.t#<init>",page,null);
  float density=root.getResources().getDisplayMetrics().density;
  check(first.getCornerRadius()==13*density&&first.maxDepth==1,"first attachment updates foreground without recursively rebuilding its outline");
  for(int dp:new int[]{0,12,64,13}) {
   b.putInt("cloud_card_corner",dp);CloudCheck.config(b);CloudTemplateHook.refresh();
   check(first.getCornerRadius()==dp*density&&first.maxDepth==1,"live corner change remains non-reentrant: "+dp);
  }
  b.putBoolean("cloud_card_corner_enabled",false);CloudCheck.config(b);CloudTemplateHook.refresh();
  check(first.getCornerRadius()==117&&first.maxDepth==1,"disabling restores the native foreground without recursive restore calls");
  int writes=first.writes;CloudTemplateHook.refresh();check(first.writes==writes,"unchanged native foreground is not invalidated again");
  b.putBoolean("cloud_card_corner_enabled",true);CloudCheck.config(b);CloudTemplateHook.refresh();
  Stroke replacement=new Stroke();float[] original={3,4,5,6,7,8,9,10};replacement.setCornerRadii(original);replacement.owner=root;root.foreground=replacement;
  CloudTemplateHook.pageRadius(root);
  check(first.getCornerRadius()==117&&replacement.getCornerRadius()==13*density,"foreground replacement restores old drawable and updates new drawable atomically");
  b.putBoolean("cloud_card_corner_enabled",false);CloudCheck.config(b);CloudTemplateHook.refresh();
  check(java.util.Arrays.equals(replacement.getCornerRadii(),original)&&replacement.maxDepth==1,"per-edge restore also survives synchronous outline invalidation");
  b.putBoolean("cloud_card_corner_enabled",true);b.putInt("cloud_card_corner",32);CloudCheck.config(b);replacement.failNext=true;
  try{CloudTemplateHook.pageRadius(root);throw new AssertionError("setter failure expected");}catch(IllegalStateException expected){}
  CloudTemplateHook.pageRadius(root);
  check(replacement.getCornerRadius()==32*density&&replacement.maxDepth==1,"failed native setter releases the re-entry guard and permits retry");
  b.putBoolean("enabled",false);CloudCheck.config(b);CloudTemplateHook.refresh();
  check(java.util.Arrays.equals(replacement.getCornerRadii(),original),"module off restores original per-edge geometry");
  System.out.println("Verified "+checks+" synchronous foreground/outline re-entry checks.");
 }
}
