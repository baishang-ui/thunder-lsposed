package io.github.baishangui.thunder;
import android.os.Bundle;
import android.graphics.drawable.Drawable;
import com.oplus.systemui.plugins.shared.template.component.light.f;
import com.oplus.systemui.plugins.shared.template.component.media.view.MediaBackGroundView;
import com.oplus.systemui.plugins.shared.template.page.view.PageBackgroundView;
public class CloudTemplateCheck {
 static int checks;static void check(boolean b,String m){if(!b)throw new AssertionError(m);checks++;}
 static HookCallback hook(){var h=Api102Runtime.callbacks.get("com.oplus.systemui.plugins.shared.template.component.light.f#draw");check(h!=null,"native template mask draw hook must be installed");return h;}
 public static void main(String[] args)throws Throwable {
 CloudHook.install(CloudTemplateCheck.class.getClassLoader());var h=hook();
 Bundle b=new Bundle();b.putBoolean("enabled",true);b.putBoolean("cloud_enabled",true);b.putBoolean("cloud_app_color",true);b.putBoolean("cloud_app_color_strength_enabled",true);
 f light=new f();light.setCallback(new MediaBackGroundView());
 for(int strength:new int[]{0,50,100,200,50}){
 b.putInt("cloud_app_color_strength",strength);CloudCheck.config(b);
 var p=CloudCheck.param(light,null,new android.graphics.Canvas());h.before(p);float factor=strength/100f;
 check(light.e.i[0]==Math.min(1,.24f*factor)&&light.e.i[1]==Math.min(1,.1f*factor),"shadow alpha follows strength");
 check(light.e.n[10]==Math.min(1,.5f*factor)&&light.e.n[11]==Math.min(1,.8f*factor),"edge alpha follows strength and saturates");
 check(light.e.t[4]==Math.min(1,.3f*factor)&&light.e.i[2]==.2f&&light.e.i[5]==94,"circle alpha scales without changing RGB or geometry");
 light.e.t[12]=123;light.e.j=false;light.e.o=false;light.e.u=false;p.failure=new RuntimeException("draw failed");h.after(p);
 check(light.e.i[0]==.24f&&light.e.n[11]==.8f&&light.e.t[4]==.3f&&light.e.t[12]==123,"native alpha restores even on exception while animation progresses");
 if(strength!=100)check(light.e.j&&light.e.o&&light.e.u,"restore marks native GPU uniforms dirty");
 }
 f other=new f();var p=CloudCheck.param(other,null,new android.graphics.Canvas());h.before(p);check(other.e.i[0]==.24f,"unrelated template lights excluded");h.after(p);
 class Page extends Drawable implements Drawable.Callback {public void invalidateDrawable(Drawable d){}}; Page page=new Page();page.setCallback(new PageBackgroundView());light.setCallback(page);
 b.putInt("cloud_app_color_strength",0);CloudCheck.config(b);p=CloudCheck.param(light,null,new android.graphics.Canvas());h.before(p);check(light.e.n[10]==0,"page callback chain recognized");h.after(p);
 int invalidations=light.invalidations;CloudTemplateHook.refresh();check(light.invalidations>invalidations,"live settings invalidate existing backgrounds");
 var pulse=new com.oplus.systemui.plugins.shared.template.animator.drawable.b();pulse.setCallback(page);
 var ph=Api102Runtime.callbacks.get("com.oplus.systemui.plugins.shared.template.animator.drawable.b#draw");
 p=CloudCheck.param(pulse,null,new android.graphics.Canvas());ph.before(p);check(pulse.c==0&&pulse.d==0,"page pulse hidden at zero");ph.after(p);check(pulse.c==.25f&&pulse.d==.75f,"pulse originals restored");
 b.putInt("cloud_app_color_strength",200);CloudCheck.config(b);p=CloudCheck.param(pulse,null,new android.graphics.Canvas());ph.before(p);check(pulse.c==.5f&&pulse.d==1,"pulse doubled with saturation");ph.after(p);
 b.putBoolean("enabled",false);CloudCheck.config(b);p=CloudCheck.param(light,null,new android.graphics.Canvas());h.before(p);check(light.e.i[0]==.24f,"module disabled keeps native color");h.after(p);
 System.out.println("Verified "+checks+" native template mask checks.");}}
