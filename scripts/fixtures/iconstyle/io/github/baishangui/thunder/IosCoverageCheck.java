package io.github.baishangui.thunder;
import java.nio.file.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.widget.*;
import com.android.systemui.statusbar.StatusBarIconView;
public final class IosCoverageCheck {
 static int checks,failures;
 static class Animated extends Drawable implements Animatable {boolean running;public void start(){running=true;}public void stop(){running=false;}public boolean isRunning(){return running;}}
 static class CountedImage extends ImageView {int updates;@Override public void setLayoutParams(android.view.ViewGroup.LayoutParams lp){super.setLayoutParams(lp);updates++;}}
 static class CountedFrame extends FrameLayout {int updates;@Override public void setLayoutParams(android.view.ViewGroup.LayoutParams lp){super.setLayoutParams(lp);updates++;}}
 static class CountedText extends TextView {int updates;@Override public void setLayoutParams(android.view.ViewGroup.LayoutParams lp){super.setLayoutParams(lp);updates++;}}
 static void check(boolean v,String s){checks++;if(!v){failures++;System.out.println("FAIL: "+s);}}
 static HookCallback.Param param(Object o,Object... args){return StatusBarIconCheck.param(o,args);}
 public static void main(String[] args)throws Throwable{
  ShadeHook.install("com.android.systemui","com.android.systemui",IosCoverageCheck.class.getClassLoader());IosRenderPathCheck.config(true);
  for(String n:Files.readAllLines(java.nio.file.Path.of("scripts/fixtures/iconstyle/status-resources.txt"))){
   var s=IosStatusIcons.system("com.android.systemui:drawable/"+n);
   check(s!=null,"C17 asset has replacement: "+n);
   if(s==null)continue;
   var icon=new IosIconDrawable(s);icon.setBounds(0,0,120,80);Canvas c=new Canvas();icon.draw(c);
   check(c.depth==0 && (!c.operations.isEmpty() || n.contains("activity_")),"drawable geometry exists: "+n);
  }
  float previousRight=Float.NaN,x=0;
  for(var kind:new IosStatusIcons.Kind[]{IosStatusIcons.Kind.WIFI,IosStatusIcons.Kind.CELLULAR,IosStatusIcons.Kind.BLUETOOTH}) {
   var plain=new IosStatusIcons.Symbol(kind);var original=new IosIconDrawable(plain);original.setBounds(0,0,210,200);
   Canvas before=new Canvas();original.draw(before);
   for(int flag:new int[]{IosStatusIcons.WARNING,IosStatusIcons.ROAMING,IosStatusIcons.NO_VOICE,IosStatusIcons.CONNECTING}) {
    var marked=plain.marked(flag);var changed=new IosIconDrawable(marked);changed.setBounds(0,0,210,200);Canvas after=new Canvas();changed.draw(after);
    check(IosIconDrawable.cellWidth(plain)==IosIconDrawable.cellWidth(marked),"status changes keep cell width: "+kind+"/"+flag);
    check(java.util.Arrays.equals(before.pathBounds.get(0),after.pathBounds.get(0)),"status changes keep base glyph position and size: "+kind+"/"+flag);
   }
  }
  for(var kind:new IosStatusIcons.Kind[]{IosStatusIcons.Kind.CELLULAR,IosStatusIcons.Kind.WIFI,IosStatusIcons.Kind.BATTERY}){
   var symbol=new IosStatusIcons.Symbol(kind);float w=IosIconDrawable.cellWidth(symbol);
   var drawable=new IosIconDrawable(symbol);drawable.setBounds(0,0,Math.round(w*10),200);Canvas canvas=new Canvas();drawable.draw(canvas);
   float l=canvas.pathBounds.stream().map(a->a[0]).min(Float::compare).orElse(0f)/10+x;
   float r=canvas.pathBounds.stream().map(a->a[2]).max(Float::compare).orElse(0f)/10+x;
   if(!Float.isNaN(previousRight))check(Math.abs(l-previousRight-3)<.15f,"signal/Wi-Fi/battery share a 3 dp visible gap");
   previousRight=r;x+=w;
  }
  var modern=new com.android.systemui.statusbar.pipeline.shared.ui.view.ModernStatusBarView();modern.slot="cast";
  ImageView loaded=new ImageView();loaded.parent=modern;loaded.drawable=new Drawable();
  HookCallback setter=Api102Runtime.callbacks.get(ImageView.class.getName()+"#setImageDrawable");
  setter.after(param(loaded,loaded.drawable));
  check(loaded.getDrawable() instanceof IosIconDrawable.Replacement,"loaded drawable status icons replaced, without resource setter");
  var legacy=new StatusBarIconView();legacy.drawable=new IosIconDrawable.Replacement(new Drawable(),new IosStatusIcons.Symbol(IosStatusIcons.Kind.ALARM));
  HookCallback draw=Api102Runtime.callbacks.get(legacy.getClass().getName()+"#onDraw");
  check(draw!=null,"native status icon double scale is intercepted");
  if(draw!=null){var call=param(legacy,new Canvas());draw.before(call);check(legacy.mIconScale==1,"replacement bypasses native content scale");draw.after(call);check(legacy.mIconScale==.6f,"native draw scale restored after call");}
  var measuring=Api102Runtime.callbacks.get(legacy.getClass().getName()+"#onMeasure");
  var mc=param(legacy,0,0);measuring.before(mc);check(legacy.mScaleToFitNewIconSize==1,"measurement uses complete cell width");measuring.after(mc);
  check(legacy.mScaleToFitNewIconSize==.7f,"native measurement scale restored");
  var root=new com.android.systemui.statusbar.pipeline.wifi.ui.view.ModernStatusBarWifiView();
  root.setPadding(7,1,9,2);root.layout=new FrameLayout.LayoutParams(40,30,16);
  var image=new ImageView();image.parent=root;image.setPadding(3,2,4,1);
  var lp=new FrameLayout.LayoutParams(37,21,85);lp.leftMargin=5;lp.rightMargin=6;lp.setMarginStart(5);lp.setMarginEnd(6);image.layout=lp;
  image.drawable=new IosIconDrawable.Replacement(new Drawable(),new IosStatusIcons.Symbol(IosStatusIcons.Kind.WIFI));
  IosStatusBarMetrics.observe(image);
  check(image.getPaddingLeft()==0 && image.getPaddingRight()==0 && lp.getMarginStart()==0 && lp.getMarginEnd()==0,"native image padding and margins removed once");
  check(root.getPaddingLeft()==0 && root.getPaddingRight()==0 && root.layout.width==-2,"status cell follows tight content without double-counted padding");
  check(image.layout.width==Math.round(IosIconDrawable.cellWidth(new IosStatusIcons.Symbol(IosStatusIcons.Kind.WIFI))*image.res.getDisplayMetrics().density),"cell uses glyph ink width at device density");
  IosRenderPathCheck.config(false);IosStatusBarMetrics.refresh();
  check(image.getPaddingLeft()==3 && lp.getMarginStart()==5 && lp.getMarginEnd()==6 && image.layout.width==37 && ((FrameLayout.LayoutParams)image.layout).gravity==85,"native image layout restored");
  check(root.getPaddingLeft()==7 && root.getPaddingRight()==9 && root.layout.width==40,"native parent layout restored");
  IosRenderPathCheck.config(true);
  var batteryStyleHook=Api102Runtime.callbacks.get("com.oplus.systemui.statusbar.pipeline.battery.ui.binder.BatteryViewBinder#bind$updateBatteryIconStyle");
  for(int style:new int[]{1,3,4,5,0,2}) {
   var nativeStyle=new com.oplus.systemui.statusbar.pipeline.battery.ui.model.BatteryIconStyle(style);
   var cell=new ImageView();var meter=new com.oplus.systemui.statusbar.pipeline.battery.ui.view.StatBatteryMeterView();
   var vm=new com.oplus.systemui.statusbar.pipeline.battery.ui.viewmodel.LocationBasedBatteryViewModel();
   var call=param(null,cell,meter,vm,new FrameLayout(),new TextView(),nativeStyle);
   batteryStyleHook.before(call);
   var applied=(com.oplus.systemui.statusbar.pipeline.battery.ui.model.BatteryIconStyle)call.args[5];
   boolean visible=style==1||style==3||style==4||style==5;
   check(applied.iconStyle==(visible?1:style),"battery style replaced including vertical/circle/legacy, hidden preserved: "+style);
   check(nativeStyle.iconStyle==style && applied.localeList==nativeStyle.localeList && applied.themeId==nativeStyle.themeId && applied.scaleFactor==nativeStyle.scaleFactor,"immutable native style and color/density metadata preserved: "+style);
   com.oplus.systemui.statusbar.pipeline.battery.ui.binder.BatteryViewBinder.bind$updateBatteryIconStyle(cell,meter,vm,(FrameLayout)call.args[3],(TextView)call.args[4],applied);
   batteryStyleHook.after(call);check(call.args[5]==nativeStyle,"hook arguments restored: "+style);
   IosRenderPathCheck.config(false);IosStatusBarHook.refresh();
   check(com.oplus.systemui.statusbar.pipeline.battery.ui.binder.BatteryViewBinder.styles.get(cell)==style,"toggle off rebinds original battery model: "+style);
   check(style==1?cell.layout.width==80:cell.layout.width==26,"native rebind owns restored battery dimensions: "+style);
   IosRenderPathCheck.config(true);IosStatusBarHook.refresh();
  }
  var statusContainer=new com.android.systemui.statusbar.phone.StatusIconContainer();
  var wifiRoot=new com.android.systemui.statusbar.pipeline.wifi.ui.view.ModernStatusBarWifiView();statusContainer.addView(wifiRoot);
  var combined=new FrameLayout();wifiRoot.addView(combined);
  var base=new CountedImage();base.drawable=new Drawable();base.mResource=501;base.res.full="com.android.systemui:drawable/stat_signal_wifi_signal_3_os17";
  var activity=new CountedImage();activity.drawable=new Drawable();activity.mResource=502;activity.res.full="com.android.systemui:drawable/stat_signal_wifi_activity_inout";
  combined.addView(base,new FrameLayout.LayoutParams(30,18,85));combined.addView(activity,new FrameLayout.LayoutParams(8,8,85));
  var prepare=Api102Runtime.callbacks.get(statusContainer.getClass().getName()+"#onMeasure");
  prepare.before(param(statusContainer,0,0));
  check(base.drawable instanceof IosIconDrawable.Replacement && activity.drawable instanceof IosIconDrawable.Replacement,"XML src assigned before attachment is replaced during native measurement");
  check(((FrameLayout.LayoutParams)activity.layout).getMarginStart()>=base.layout.width,"signal and activity occupy separate adjacent cells instead of overlapping");
  int updates=base.updates+activity.updates;
  prepare.before(param(statusContainer,0,0));check(updates==base.updates+activity.updates,"repeat native measurement is stable without a layout request loop");
  IosRenderPathCheck.config(false);IosStatusBarHook.refresh();
  check(((FrameLayout.LayoutParams)activity.layout).getMarginStart()==0 && ((FrameLayout.LayoutParams)activity.layout).gravity==85,"original overlaid geometry restored when disabled");
  IosRenderPathCheck.config(true);
  var marker=new TextView();marker.setText("7");marker.setGravity(85);marker.id=609;marker.res.full="com.android.systemui:id/wifi_left_tv";
  var originalTypeface=marker.getTypeface();combined.addView(marker,new FrameLayout.LayoutParams(7,10,85));
  var spacer=new android.view.View();spacer.id=610;spacer.res.full="com.android.systemui:id/wifi_signal_spacer";spacer.layout.width=12;wifiRoot.addView(spacer);
  prepare.before(param(statusContainer,0,0));
  check(marker.getTextSize()==27.5f && marker.getGravity()==android.view.Gravity.CENTER,"native text network badge follows common type and baseline");
  check(((FrameLayout.LayoutParams)marker.layout).getMarginStart()>=base.layout.width && spacer.layout.width==0,"text badge receives its own cell and redundant spacer is removed");
  base.setVisibility(android.view.View.GONE);activity.setVisibility(android.view.View.GONE);
  prepare.before(param(statusContainer,0,0));
  check(((FrameLayout.LayoutParams)marker.layout).getMarginStart()==0,"last visible overlay starts at zero after signal and activity disappear");
  base.setVisibility(android.view.View.VISIBLE);activity.setVisibility(android.view.View.VISIBLE);
  prepare.before(param(statusContainer,0,0));
  check(((FrameLayout.LayoutParams)marker.layout).getMarginStart()>=base.layout.width,"returning signal rebuilds adjacent spacing");
  var mobileRoot=new com.android.systemui.statusbar.pipeline.mobile.ui.view.ModernStatusBarMobileView();statusContainer.addView(mobileRoot);
  var mobileFrame=new FrameLayout();mobileRoot.addView(mobileFrame);
  var mobileSignal=new ImageView();mobileSignal.drawable=new com.android.settingslib.graph.SignalDrawable();
  var mobileType=new ImageView();mobileType.drawable=new IosIconDrawable.Replacement(new Drawable(),new IosStatusIcons.Symbol(IosStatusIcons.Kind.LABEL,0,4,0,0,"5G+"));
  var mobileActivity=new ImageView();mobileActivity.drawable=new IosIconDrawable.Replacement(new Drawable(),new IosStatusIcons.Symbol(IosStatusIcons.Kind.ACTIVITY,3,3));
  mobileFrame.addView(mobileSignal,new FrameLayout.LayoutParams(30,18,85));
  mobileFrame.addView(mobileType,new FrameLayout.LayoutParams(10,12,85));
  mobileFrame.addView(mobileActivity,new FrameLayout.LayoutParams(8,8,85));
  prepare.before(param(statusContainer,0,0));
  check(((FrameLayout.LayoutParams)mobileType.layout).getMarginStart()==mobileSignal.layout.width,"direct SignalDrawable reserves its cell before network label");
  check(((FrameLayout.LayoutParams)mobileActivity.layout).getMarginStart()==mobileSignal.layout.width+mobileType.layout.width,"direct signal, label and activity do not overlap");
  mobileType.setVisibility(4);prepare.before(param(statusContainer,0,0));
  check(((FrameLayout.LayoutParams)mobileType.layout).getMarginStart()==0 && mobileType.getVisibility()==4,"invisible network label drops its old group offset without changing native visibility");
  mobileType.setVisibility(android.view.View.VISIBLE);prepare.before(param(statusContainer,0,0));
  check(((FrameLayout.LayoutParams)mobileType.layout).getMarginStart()==mobileSignal.layout.width,"network label restores adjacent position when visible again");
  var bigRoot=new com.android.systemui.statusbar.pipeline.mobile.ui.view.ModernStatusBarMobileView();statusContainer.addView(bigRoot);
  var bigCombo=new androidx.constraintlayout.widget.ConstraintLayout();bigRoot.addView(bigCombo);
  var bigSignalFrame=new CountedFrame();var signalFrameParams=new androidx.constraintlayout.widget.ConstraintLayout.LayoutParams(-2,0);
  signalFrameParams.startToEnd=900;signalFrameParams.endToEnd=0;signalFrameParams.topToTop=0;signalFrameParams.bottomToBottom=0;
  bigCombo.addView(bigSignalFrame,signalFrameParams);
  var bigSignal=new ImageView();bigSignal.drawable=new com.android.settingslib.graph.SignalDrawable();bigSignalFrame.addView(bigSignal,new FrameLayout.LayoutParams(19,16,85));
  var bigType=new CountedText();bigType.id=711;bigType.res.full="com.android.systemui:id/mobile_type_text";bigType.setText("5G+");
  var bigTypeParams=new androidx.constraintlayout.widget.ConstraintLayout.LayoutParams(-2,-2);
  bigTypeParams.endToEnd=0;bigTypeParams.bottomToBottom=0;bigTypeParams.verticalBias=1;
  bigCombo.addView(bigType,bigTypeParams);
  prepare.before(param(statusContainer,0,0));
  check(signalFrameParams.startToStart==0 && signalFrameParams.startToEnd==-1 && signalFrameParams.endToEnd==-1,"nested native signal frame is the first cell in the ColorOS constraint layout");
  check(bigTypeParams.startToStart==0 && bigTypeParams.endToEnd==-1 && bigTypeParams.getMarginStart()==bigSignal.layout.width,"large 5G label follows signal instead of overlapping its lower edge");
  check(bigTypeParams.topToTop==0 && bigTypeParams.bottomToBottom==0 && bigTypeParams.verticalBias==.5f,"ColorOS network label is vertically centered with signal");
  int originalSignalWidth=bigSignal.layout.width;
  int bigUpdates=bigSignalFrame.updates+bigType.updates;
  prepare.before(param(statusContainer,0,0));
  check(bigTypeParams.getMarginStart()==originalSignalWidth,"repeated constraint arrangement keeps a stable offset");
  check(bigSignalFrame.updates+bigType.updates==bigUpdates,"constraint cells do not request layout repeatedly");
  var nestedRoot=new com.android.systemui.statusbar.pipeline.mobile.ui.view.ModernStatusBarMobileView();statusContainer.addView(nestedRoot);
  var nestedOuter=new FrameLayout();nestedRoot.addView(nestedOuter);
  var nestedFrame=new FrameLayout();nestedOuter.addView(nestedFrame,new FrameLayout.LayoutParams(40,18,85));
  var nestedSignal=new ImageView();nestedSignal.drawable=new com.android.settingslib.graph.SignalDrawable();nestedFrame.addView(nestedSignal,new FrameLayout.LayoutParams(19,16,85));
  prepare.before(param(statusContainer,0,0));
  check(((FrameLayout.LayoutParams)nestedFrame.layout).gravity==(android.view.Gravity.START|android.view.Gravity.CENTER_VERTICAL),"nested native frame joins adjacent layout");
  bigSignalFrame.setVisibility(android.view.View.GONE);prepare.before(param(statusContainer,0,0));
  check(bigTypeParams.getMarginStart()==0,"constraint metadata closes the gap when the entire signal frame disappears");
  bigSignalFrame.setVisibility(android.view.View.VISIBLE);prepare.before(param(statusContainer,0,0));
  check(bigTypeParams.getMarginStart()==originalSignalWidth,"constraint metadata follows a returning signal");
  var nestedActivity=new ImageView();nestedActivity.drawable=new IosIconDrawable.Replacement(new Drawable(),new IosStatusIcons.Symbol(IosStatusIcons.Kind.ACTIVITY,3,3));
  bigSignalFrame.addView(nestedActivity,new FrameLayout.LayoutParams(8,8,85));prepare.before(param(statusContainer,0,0));
  check(bigTypeParams.getMarginStart()==bigSignal.layout.width+nestedActivity.layout.width,"outer constraint cell includes nested activity width");
  IosRenderPathCheck.config(false);IosStatusBarHook.refresh();
  check(marker.getTextSize()==14 && marker.getTypeface()==originalTypeface && marker.getGravity()==85 && marker.layout.width==7 && spacer.layout.width==12,"native text badge and spacers restored");
  check(((FrameLayout.LayoutParams)nestedFrame.layout).gravity==85,"nested native frame gravity restored on disable");
  check(signalFrameParams.startToEnd==900 && signalFrameParams.startToStart==-1 && signalFrameParams.endToEnd==0 && signalFrameParams.height==0,"native signal constraints restored after disabling");
  check(bigTypeParams.startToStart==-1 && bigTypeParams.endToEnd==0 && bigTypeParams.topToTop==-1 && bigTypeParams.verticalBias==1,"native bottom-aligned 5G label constraints restored after disabling");
  IosRenderPathCheck.config(true);
  var animated=new Animated();var animatedReplacement=new IosIconDrawable.Replacement(animated,new IosStatusIcons.Symbol(IosStatusIcons.Kind.BLUETOOTH).marked(IosStatusIcons.CONNECTING));
  animatedReplacement.start();check(animated.running && animatedReplacement.isRunning(),"native animation lifecycle forwarded through replacement");
  animatedReplacement.stop();check(!animated.running && !animatedReplacement.isRunning(),"native animations stop after detach");
  var foreignLegacy=new StatusBarIconView();foreignLegacy.drawable=new Drawable();setter.after(param(foreignLegacy,foreignLegacy.drawable));
  check(!(foreignLegacy.drawable instanceof IosIconDrawable.Replacement),"loaded observer cannot bypass legacy package identity guard");
  var chargingIndicator=new ImageView();chargingIndicator.drawable=new Drawable();var chargingMeter=new com.oplus.systemui.statusbar.pipeline.battery.ui.view.StatBatteryMeterView();
  var chargingHook=Api102Runtime.callbacks.get("com.oplus.systemui.statusbar.pipeline.battery.ui.binder.BatteryViewBinder#bind$updateChargingView");
  chargingHook.after(param(null,chargingIndicator,chargingMeter,new com.oplus.systemui.statusbar.pipeline.battery.ui.model.ChargeIcon()));
  check(chargingIndicator.getVisibility()==android.view.View.GONE,"charging mark is shown once inside battery instead of duplicated outside");
  var outerOnly=(com.oplus.systemui.statusbar.pipeline.battery.ui.drawable.HorizontalBatteryContentDrawable)chargingMeter.batteryStyleDrawable;
  outerOnly.charge=0;var outerCanvas=new Canvas();Api102Runtime.callbacks.get(outerOnly.getClass().getName()+"#draw").before(param(outerOnly,outerCanvas));
  check(outerCanvas.pathBounds.size()>2,"external-only native charging state is carried into the internal replacement bolt");
  var reverseHook=Api102Runtime.callbacks.get("com.oplus.systemui.statusbar.pipeline.battery.ui.binder.BatteryViewBinder#bind$updateReverseChargeView");
  var reverse=new ImageView();reverse.drawable=new Drawable();reverseHook.after(param(null,reverse,chargingMeter,new Object()));
  check(reverse.drawable instanceof IosIconDrawable.Replacement && ((IosIconDrawable.Replacement)reverse.drawable).symbol().kind()==IosStatusIcons.Kind.REVERSE_CHARGE,"reverse charging keeps a distinct replaced indicator");
  IosRenderPathCheck.config(false);IosStatusBarHook.refresh();check(chargingIndicator.getVisibility()==android.view.View.VISIBLE,"native outer charging mark restored");IosRenderPathCheck.config(true);
  check(IosStatusIcons.system("com.android.systemui:drawable/qs_wifi_icon")==null,"QS identity outside replacement set");
  check(IosStatusIcons.system("other:drawable/stat_sys_alarm")==null,"foreign packages excluded");
  if(failures>0)throw new AssertionError(failures+"/"+checks+" coverage checks failed");
  System.out.println("Verified "+checks+" firmware coverage checks.");
 }
}
