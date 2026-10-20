package io.github.baishangui.thunder;
import com.oplus.systemui.qs.media.OplusQsBaseMediaPanelView;
import com.oplus.systemui.qs.media.multilight.OplusQsMediaBackgroundDrawable;
import com.oplusos.systemui.common.outline.RoundRectOutlineProvider;
public final class QsMediaGeometryCheck {
 static int checks;
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);checks++;}
 public static void main(String[] args)throws Throwable {
  ShadeHook.install("com.android.systemui","com.android.systemui",QsMediaGeometryCheck.class.getClassLoader());
  var b=QsGeometryCheck.settings();CloudCheck.config(b);
  var host=new OplusQsBaseMediaPanelView();
  String base=host.getClass().getName(),draw=OplusQsMediaBackgroundDrawable.class.getName()+"#draw";
  QsGeometryCheck.afterIfPresent(base+"#onFinishInflate",host,null);
  check(Boolean.TRUE.equals(Reflect.getAdditionalInstanceField(host.bgDrawable,"thunder.qs-corner-card")),"media main background is registered independently of ordinary QS tiles");
  check(Boolean.TRUE.equals(Reflect.getAdditionalInstanceField(host.themeBgDrawable,"thunder.qs-corner-card")),"media theme background is registered");
  float density=host.getResources().getDisplayMetrics().density;
  for(int height:new int[]{200,800})for(int radius:new int[]{0,12,32,64}) {
   host.transitionDrawable.setBounds(new android.graphics.Rect(0,0,800,height));
   b.putInt("qs_card_corner",radius);CloudCheck.config(b);QsCornerHook.refresh();
   float expected=Math.min(radius*density,height/2f);
   var shape=QsGeometryCheck.provider(QsGeometryCheck.LAYER,host.transitionDrawable);
   check(shape.cornerRadius==expected,"media path follows live dp radius and bounds");
   var light=host.transitionDrawable.mediaLightDrawable.params;
   var p=QsTintCheck.before(draw,host.transitionDrawable,new android.graphics.Canvas());
   for(int i=6;i<10;i++)check(light.uCommonArray[i]==expected,"media light corner matches background path");
   check(light.uCommonArray[10]==(expected==height/2f?1f:3f),"media light uses bounded shape weight");
   check(light.uCornerType==(expected==height/2f?1:2),"media light uses round capsule or G2 branch matching native path");
   p.failure=new RuntimeException("native renderer failure");QsTintCheck.after(draw,p);
   check(light.uCommonArray[6]==47&&light.uCommonArray[10]==1.4f&&light.uCornerType==1,"media native shader values restore on draw failure");
   check(light.dirtyCommonArray&&light.dirtyConerType,"restored shader values upload on the following frame");
  }
  var path=new android.graphics.Path();
  QsTintCheck.before("com.oplus.systemui.qs.media.OplusQsNormalMediaPanelView#updateSpotLightPath",host,path,null);
  check(path.radius==64*density&&path.smooth,"media press effect builds native G2 path even without an adapter");
  int clips=host.bg.outlineInvalidations,updates=host.outlineUpdates;
  b.putBoolean("qs_card_corner_enabled",false);CloudCheck.config(b);QsCornerHook.refresh();
  check(host.bg.outlineInvalidations>clips&&host.outlineUpdates>updates,"disabling corners refreshes existing media background clip and native light cache");
  var p=QsTintCheck.before(draw,host.transitionDrawable,new android.graphics.Canvas());
  check(host.transitionDrawable.mediaLightDrawable.params.uCommonArray[6]==47,"disabled media light retains native radius");QsTintCheck.after(draw,p);
  check(QsGeometryCheck.provider(QsGeometryCheck.LAYER,host.transitionDrawable).cornerRadius==28,"disabled media path returns native provider");
  System.out.println("Verified "+checks+" media tile geometry checks.");
 }
}
