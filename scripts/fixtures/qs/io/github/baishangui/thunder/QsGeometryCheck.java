package io.github.baishangui.thunder;

import android.graphics.Canvas;
import android.os.Bundle;
import com.oplus.systemui.qs.base.res.drawable.*;
import com.oplus.systemui.qs.base.seek.OplusQsVerticalSeekBar;
import com.oplusos.systemui.common.outline.RoundRectOutlineProvider;
import com.oplusos.systemui.common.blurability.*;
import com.oplusos.systemui.common.blurability.drawable.AutoBlurDrawable;

public class QsGeometryCheck {
 static final String WRAP=TileDrawableWrapper.class.getName(),LAYER=TileLayerDrawable.class.getName();
 static int checks;
 static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);checks++;System.out.println("PASS: "+text);}
 static Bundle settings(){Bundle b=QsStateCheck.settings();b.putBoolean("qs_card_corner_enabled",true);b.putInt("qs_card_corner",16);return b;}
 static Object afterIfPresent(String key,Object owner,Object result,Object... args)throws Throwable {
  return Api102Runtime.callbacks.containsKey(key)?CloudCheck.after(key,owner,result,args):result;
 }
 static RoundRectOutlineProvider provider(String type,TileDrawableWrapper d)throws Throwable {
  return (RoundRectOutlineProvider)afterIfPresent(type+"#getPathProvider",d,d.pathProvider);
 }
 static void providers()throws Throwable {
  Bundle b=settings();CloudCheck.config(b);
  GradientTileDrawable tile=new GradientTileDrawable();CloudCheck.after(QsTintCheck.FLAT+"#<init>",tile,null);
  RoundRectOutlineProvider nativeProvider=(RoundRectOutlineProvider)tile.pathProvider;
  RoundRectOutlineProvider selected=provider(WRAP,tile);
  float density=android.content.res.Resources.getSystem().getDisplayMetrics().density;
  check(selected.cornerRadius==16*density,"1x1 uses selected radius through native outline provider");
  check(selected.cornerWeight.equals(nativeProvider.cornerWeight),"1x1 retains native smooth corner weight");
  check(!Api102Runtime.callbacks.containsKey(WRAP+"#getPath"),"native smooth path is not replaced with ordinary addRoundRect");
  check(nativeProvider.cornerRadius==28,"shared firmware outline provider is not mutated");
  BlurConfig blur=new BlurConfig();blur.pathProvider=nativeProvider;
  Reflect.setAdditionalInstanceField(blur,"thunder.role","qs-card");
  ViewBlurProxy proxy=new ViewBlurProxy(blur);
  HookCallback.Param frame=QsTintCheck.before(QsTintCheck.B+"ViewBlurProxy#applyConfigToPlatformBlur",proxy);
  check(blur.pathProvider instanceof RoundRectOutlineProvider&&((RoundRectOutlineProvider)blur.pathProvider).cornerRadius==16*density,
    "blur backend keeps a native smooth path provider instead of clearing it");
  QsTintCheck.after(QsTintCheck.B+"ViewBlurProxy#applyConfigToPlatformBlur",frame);
  check(blur.pathProvider==nativeProvider,"blur provider restores after native backend invocation");
  android.view.View view=new android.view.View();view.setBackground(tile);
  android.graphics.Outline outline=new android.graphics.Outline();
  String clip="com.oplus.systemui.qs.base.widget.QsViewOutlineProvider$QsViewRoundRectOutlineProvider#getOutline";
  QsTintCheck.before(clip,new Object(),view,outline);
  check(outline.radius==16*density,"1x1 view clipping matches its smooth drawable and press shape");
  TileLayerDrawable transition=new TileLayerDrawable();
  check(provider(LAYER,transition).cornerRadius==16*density,"press feedback transition uses same native corner provider");
  com.oplus.systemui.plugins.drawable.PluginDrawable device=new com.oplus.systemui.plugins.drawable.PluginDrawable();
  RoundRectOutlineProvider deviceShape=provider(device.getClass().getName(),device);
  check(deviceShape.cornerRadius==16*density&&deviceShape.cornerWeight==1.4f,"device center outline retains smoothing and receives custom radius");
  android.view.View deviceView=new android.view.View();deviceView.setBackground(device);
  android.graphics.Path pressed=new android.graphics.Path();Object nativeAdapter=new Object();
  String press="com.oplus.systemui.qs.base.spotlight.SpotLightFrameLayout#updateSpotLightPath";
  if(Api102Runtime.callbacks.containsKey(press))QsTintCheck.before(press,deviceView,pressed,nativeAdapter);
  check(pressed.radius==16*density&&deviceShape.adapterUsed==nativeAdapter,
    "device center press highlighter uses the same smooth native shape and adapter");
  android.view.View unrelated=new android.view.View();unrelated.setBackground(new android.graphics.drawable.Drawable());
  android.graphics.Path untouched=new android.graphics.Path();
  QsTintCheck.before(press,unrelated,untouched,nativeAdapter);
  check(untouched.radius==0,"unrelated spotlight surfaces keep native clipping");
  b.putInt("qs_card_corner",0);CloudCheck.config(b);QsCardHook.refresh();
  check(selected.cornerRadius==0&&deviceShape.cornerRadius==0,"live corner changes update already cached tile and device outlines");
  b.putBoolean("qs_card_corner_enabled",false);CloudCheck.config(b);QsCardHook.refresh();
  check(selected.cornerRadius==28&&deviceShape.cornerRadius==28,"cached outlines restore native radius when disabled");
  check(provider(WRAP,tile)==nativeProvider,"disabled corners return original provider");
  b.putBoolean("qs_card_corner_enabled",true);b.putBoolean("enabled",false);CloudCheck.config(b);
  check(provider(WRAP,tile)==nativeProvider,"module disabled keeps native smooth outline");
 }
 static void threeStage()throws Throwable {
  CloudCheck.config(settings());
  var card=new com.oplus.systemui.plugins.qs.customize.view.tile.OplusQSResizeableThreeStageView();
  BlurConfig blur=new BlurConfig();blur.platformMixConfig=QsTintCheck.multi(0xff3377cc);
  MixColorTileDrawable bg=new MixColorTileDrawable(new AutoBlurDrawable(new ViewBlurProxy(blur)));
  bg.setState(new int[]{com.oplus.systemui.qs.base.res.model.TileViewFlag.Active.getAttr()});
  CloudCheck.after(QsTintCheck.TILE+"#<init>",bg,null,bg.autoBlurDrawable,null);card.bgDrawable.setDrawable(bg);
  String type=card.getClass().getName();afterIfPresent(type+"#<init>",card,null);
  check(((BlurMixConfig.BlurMixMultiWithShader)QsStateCheck.mix(blur)).foregroundShaderParam.topLayerColor==0x4188aacc,
    "vibrate/silent three-stage 2x1 receives shared background material");
  GradientTileDrawable rebuilt=new GradientTileDrawable();rebuilt.setState(bg.getState());card.themeDrawable.setDrawable(rebuilt);
  afterIfPresent(type+"#onDrawableUpdate",card,null,java.util.List.of(),true);
  HookCallback.Param p=QsTintCheck.before(QsTintCheck.FLAT+"#draw",rebuilt,new Canvas());
  check(rebuilt.colorDrawable.getColor()==0x4188aacc,"three-stage theme rebuild keeps shared background tint");QsTintCheck.after(QsTintCheck.FLAT+"#draw",p);
 }
 static void nativeFallbacks()throws Throwable {
  Bundle b=settings();CloudCheck.config(b);
  float radius=16*android.content.res.Resources.getSystem().getDisplayMetrics().density;
  for(Float weight:new Float[]{null,0f,1f}) {
   GradientTileDrawable tile=new GradientTileDrawable();tile.pathProvider=new RoundRectOutlineProvider(28f,weight);
   RoundRectOutlineProvider shape=provider(WRAP,tile);
   android.graphics.Path path=new android.graphics.Path();shape.updatePath(tile,path,null,new Object());
   check(shape.smoothPath&&shape.cornerWeight==3f,"native ordinary/capsule weight "+weight+" becomes firmware G2 smooth geometry");
   var outline=new android.graphics.Outline();shape.getOutline(new android.view.View(),outline);
   check(outline.smooth,"view clipping also uses smooth geometry for native weight "+weight);
  }
  var tile=new com.oplus.systemui.plugins.qs.customize.view.tile.OplusQSResizeableTileView();
  tile.transitionDrawable.pathProvider=com.oplusos.systemui.common.outline.OvalOutlineProvider.INSTANCE;
  afterIfPresent(tile.getClass().getName()+"#<init>",tile,null);
  Object shape=afterIfPresent(LAYER+"#getPathProvider",tile.transitionDrawable,tile.transitionDrawable.pathProvider);
  check(shape instanceof RoundRectOutlineProvider&&((RoundRectOutlineProvider)shape).cornerRadius==radius,
    "real 1x1 oval background receives selected smooth corners");
  TileLayerDrawable indicator=new TileLayerDrawable();indicator.pathProvider=com.oplusos.systemui.common.outline.OvalOutlineProvider.INSTANCE;
  check(afterIfPresent(LAYER+"#getPathProvider",indicator,indicator.pathProvider)==indicator.pathProvider,
    "nested circular icons and three-stage indicators stay circular");
  for(String role:new String[]{"qs-card","qs-progress"}) {
   BlurConfig blur=new BlurConfig();Reflect.setAdditionalInstanceField(blur,"thunder.role",role);
   var proxy=new ViewBlurProxy(blur);
   HookCallback.Param p=QsTintCheck.before(QsTintCheck.B+"ViewBlurProxy#applyConfigToPlatformBlur",proxy);
   check(blur.radiusWeight==3f&&blur.pathProvider instanceof RoundRectOutlineProvider,
     role+" backend without a provider uses explicit G2 smooth geometry");
   check(blur.enableMotionSmoothCorner,"motion blur uses smooth corners as well");
   QsTintCheck.after(QsTintCheck.B+"ViewBlurProxy#applyConfigToPlatformBlur",p);
   check(blur.radiusWeight==null&&blur.pathProvider==null&&!blur.enableMotionSmoothCorner,
     role+" restores native smoothness flags and provider after backend invocation");
  }
  var slider=new OplusQsVerticalSeekBar();slider.mIsSupportSmoothRoundCorner=false;
  slider.mBackgroundRoundCornerWeight=0;slider.mProgressRoundCornerWeight=0;
  HookCallback.Param frame=QsTintCheck.before(slider.getClass().getName()+"#onDraw",slider,new Canvas());
  check(slider.mIsSupportSmoothRoundCorner&&slider.mBackgroundRoundCornerWeight==3f&&slider.mProgressRoundCornerWeight==3f,
    "slider ordinary fallback switches both clipping paths to firmware smooth rendering");
  check(slider.mSmoothStyleType==1,"slider native draw and clip paths select the same G2 algorithm as card backgrounds");
  QsTintCheck.after(slider.getClass().getName()+"#onDraw",frame);
  check(!slider.mIsSupportSmoothRoundCorner&&slider.mBackgroundRoundCornerWeight==0&&slider.mProgressRoundCornerWeight==0,
    "slider native smoothness settings restore after drawing");
  check(slider.mSmoothStyleType==0,"slider native smoothing algorithm restores after drawing");
 }
 static void shaderStroke()throws Throwable {
  CloudCheck.config(settings());
  BlurConfig blur=new BlurConfig();Reflect.setAdditionalInstanceField(blur,"thunder.role","qs-card");
  var nativeCorners=new com.oplus.posteffect.params.CornerParams(com.oplus.posteffect.params.CornerType.ROUND,28f,0f);
  blur.gradientStrokeCornerParam=nativeCorners;
  HookCallback.Param p=QsTintCheck.before(QsTintCheck.B+"ViewBlurProxy#applyConfigToPlatformBlur",new ViewBlurProxy(blur));
  var selected=(com.oplus.posteffect.params.CornerParams)blur.gradientStrokeCornerParam;
  check(selected.getType()==com.oplus.posteffect.params.CornerType.G2&&selected.getWeight()==3f,
    "AGSL stroke corner type matches G2 path instead of retaining ordinary rounding");
  QsTintCheck.after(QsTintCheck.B+"ViewBlurProxy#applyConfigToPlatformBlur",p);
  check(blur.gradientStrokeCornerParam==nativeCorners,"AGSL native corner type restores after invocation");
 }
 static void missingPressAdapter()throws Throwable {
  CloudCheck.config(settings());
  var device=new com.oplus.systemui.plugins.drawable.PluginDrawable();
  var deviceView=new android.view.View();deviceView.setBackground(device);
  var shape=provider(device.getClass().getName(),device);
  var pressed=new android.graphics.Path();
  QsTintCheck.before("com.oplus.systemui.qs.base.spotlight.SpotLightFrameLayout#updateSpotLightPath",deviceView,pressed,null);
  check(shape.smoothPath&&shape.adapterUsed instanceof com.oplus.graphics.OplusPathAdapter,
    "press host without a smooth adapter still renders native smooth geometry");
  var tile=new com.oplus.systemui.plugins.qs.customize.view.tile.OplusQSResizeableTileView();
  tile.transitionDrawable.pathProvider=new RoundRectOutlineProvider(28f,null);
  shape=provider(LAYER,tile.transitionDrawable);
  QsTintCheck.before(tile.getClass().getName()+"#updateSpotLightPath",tile,new android.graphics.Path(),null);
  check(shape.smoothPath,"resizable tile press feedback also constructs its missing native adapter");
  var slider=new OplusQsVerticalSeekBar();
  pressed=new android.graphics.Path();
  if(Api102Runtime.callbacks.containsKey(slider.getClass().getName()+"#updateSpotLightPath"))
   QsTintCheck.before(slider.getClass().getName()+"#updateSpotLightPath",slider,pressed,null);
  float selected=16*android.content.res.Resources.getSystem().getDisplayMetrics().density*1.1f;
  check(Math.abs(pressed.radius-selected)<.01f,"slider press path uses animated background corners through the native smooth provider");
 }
 static void factorySmoothness()throws Throwable {
  CloudCheck.config(settings());
  var slider=new OplusQsVerticalSeekBar();slider.mBackgroundRoundCornerWeight=0;
  HookCallback.Param frame=QsTintCheck.before(slider.getClass().getName()+"#onDraw",slider,new Canvas());
  HookCallback.Param factory=QsTintCheck.before(QsStateCheck.SEEK+"#getSeekBarActiveDrawable",null,slider,
    new android.graphics.Rect(),slider.mCurProgressRadius,slider.mBackgroundRoundCornerWeight,true,false,1f);
  check(factory.args[3].equals(0f),"native factory cache retains original smoothing weight during custom drawing");
  QsTintCheck.after(slider.getClass().getName()+"#onDraw",frame);
 }
 static void slider()throws Throwable {
  Bundle b=settings();CloudCheck.config(b);float density=android.content.res.Resources.getSystem().getDisplayMetrics().density;
  OplusQsVerticalSeekBar view=new OplusQsVerticalSeekBar();String type=view.getClass().getName();
  HookCallback.Param p=QsTintCheck.before(type+"#onDraw",view,new Canvas());
  check(Math.abs(view.mCurProgressRadius-16*density*1.1f)<.01f,"slider inner fill follows custom radius and native press enlargement");
  check(view.mCurProgressRadius==view.mCurBackgroundRadius,"slider inner and outer press shapes stay aligned");
  check(view.mProgressColor==0xffeeeeee&&view.mProgressRoundCornerWeight==1.4f,"progress colors and native smoothness remain unchanged");
  HookCallback.Param factory=QsTintCheck.before(QsStateCheck.SEEK+"#getSeekBarActiveDrawable",null,view,
    new android.graphics.Rect(),view.mCurProgressRadius,1.4f,true,false,1f);
  check(Math.abs((Float)factory.args[2]-30.8f)<.01f,"slider factory keeps native radius in persistent configs during custom drawing");
  p.failure=new IllegalStateException("frame");QsTintCheck.after(type+"#onDraw",p);
  check(view.mBackgroundRadius==28&&view.mCurProgressRadius==30.8f,"slider native geometry restores even after drawing failure");
  check(Math.abs((Float)afterIfPresent(type+"#getBackgroundRadius",view,28f)-16*density*1.1f)<.01f,"slider press highlighter matches animated background radius");
  BlurConfig active=new BlurConfig();active.platformMixConfig=QsTintCheck.multi(0xffeeeeee);active.radiusWeight=1.4f;
  Object originalMix=active.platformMixConfig;
  AutoBlurDrawable auto=new AutoBlurDrawable(new ViewBlurProxy(active));
  afterIfPresent(QsStateCheck.SEEK+"#getSeekBarActiveDrawable",null,auto,view,new android.graphics.Rect(),28f,1.4f,true,false,1f);
  view.activeMixColorDrawable=auto;view.isDrawingWithMixColor=true;
  p=QsTintCheck.before(type+"#onDraw",view,new Canvas());
  HookCallback.Param nativeFrame=QsTintCheck.before(QsTintCheck.B+"ViewBlurProxy#applyConfigToPlatformBlur",auto.getViewBlurProxy());
  check(Math.abs(active.cornerRadius-view.mCurProgressRadius)<.01f,"pressed native blur fill uses the exact animated inner radius");
  check(((RoundRectOutlineProvider)active.pathProvider).cornerWeight==view.mProgressRoundCornerWeight&&active.radiusWeight==view.mProgressRoundCornerWeight,
    "slider native fill, outer track and blur backend use the same smooth weight");
  QsTintCheck.after(QsTintCheck.B+"ViewBlurProxy#applyConfigToPlatformBlur",nativeFrame);QsTintCheck.after(type+"#onDraw",p);
  p=QsTintCheck.before(QsTintCheck.B+"ViewBlurProxy#applyConfigToPlatformBlur",auto.getViewBlurProxy());
  check(Math.abs(active.cornerRadius-16*density*1.1f)<.01f,"slider cached native inner fill follows custom corners and press scale");
  check(active.platformMixConfig==originalMix&&QsStateCheck.mix(active)==originalMix,
    "slider inner fill retains native contrasting material");QsTintCheck.after(QsTintCheck.B+"ViewBlurProxy#applyConfigToPlatformBlur",p);
  b.putBoolean("qs_card_corner_enabled",false);CloudCheck.config(b);
  p=QsTintCheck.before(type+"#onDraw",view,new Canvas());
  check(view.mCurProgressRadius==30.8f,"slider disabling corners restores native press geometry");QsTintCheck.after(type+"#onDraw",p);
 }
 static void relativeCorners()throws Throwable {
  Bundle b=settings();
  var nativeShape=new RoundRectOutlineProvider(28f,3f);
  var small=new GradientTileDrawable();small.pathProvider=nativeShape;small.setBounds(new android.graphics.Rect(8,12,168,172));
  var wide=new GradientTileDrawable();wide.pathProvider=nativeShape;wide.setBounds(new android.graphics.Rect(0,0,640,320));
  RoundRectOutlineProvider smallShape=null;
  for(int value:new int[]{0,16,30,31,40,48,63,64}) {
   b.putInt("qs_card_corner",value);CloudCheck.config(b);
   smallShape=provider(WRAP,small);var wideShape=provider(WRAP,wide);
   check(Math.abs(smallShape.cornerRadius-80f*value/64f)<.001f,"1x1 relative radius follows value "+value+" without saturating at 30");
   check(wideShape!=smallShape&&Math.abs(wideShape.cornerRadius-160f*value/64f)<.001f,"shared firmware provider scales independently for wide surface at "+value);
   check(smallShape.cornerRadius==80f*value/64f&&nativeShape.cornerRadius==28,"another surface cannot overwrite 1x1 geometry at "+value);
  }
  b.putInt("qs_card_corner",40);CloudCheck.config(b);
  smallShape=provider(WRAP,small);
  var view=new android.view.View();view.setBackground(small);var outline=new android.graphics.Outline();
  QsTintCheck.before("com.oplus.systemui.qs.base.widget.QsViewOutlineProvider$QsViewRoundRectOutlineProvider#getOutline",new Object(),view,outline);
  check(outline.radius==50&&outline.smooth&&outline.left==8&&outline.top==12&&outline.right==168&&outline.bottom==172,
    "view clips to exact background G2 path and inset bounds instead of full host rectangle");
  small.setBounds(new android.graphics.Rect(8,12,248,252));QsCornerHook.refresh();
  check(smallShape.cornerRadius==75,"cached provider recalculates from resized background bounds");
  b.putBoolean("qs_card_corner_enabled",false);CloudCheck.config(b);QsCornerHook.refresh();
  check(provider(WRAP,small)==nativeShape&&smallShape.cornerRadius==28,"relative geometry restores native provider and cached radius on disable");
 }
 static void spotlightRefresh()throws Throwable {
  Bundle b=settings();b.putInt("qs_card_corner",40);CloudCheck.config(b);
  var view=new android.view.View();var background=new GradientTileDrawable();background.setBounds(new android.graphics.Rect(4,6,164,166));view.setBackground(background);
  var pressed=new android.graphics.Path();
  QsTintCheck.before("com.oplus.systemui.qs.base.spotlight.SpotLightFrameLayout#updateSpotLightPath",view,pressed,null);
  check(pressed.radius==50&&pressed.bounds.left==4,"press path uses scaled inset card geometry");
  com.oplus.systemui.qs.base.spotlight.SharedSpotLightEffect.updates=0;
  b.putInt("qs_card_corner",48);CloudCheck.config(b);QsCornerHook.refresh();
  check(com.oplus.systemui.qs.base.spotlight.SharedSpotLightEffect.updates>0&&com.oplus.systemui.qs.base.spotlight.SharedSpotLightEffect.updated.contains(view),
    "corner refresh rebuilds the firmware spotlight session clip instead of leaving its old circle cached");
  b.putBoolean("qs_card_corner_enabled",false);CloudCheck.config(b);com.oplus.systemui.qs.base.spotlight.SharedSpotLightEffect.updates=0;QsCornerHook.refresh();
  check(com.oplus.systemui.qs.base.spotlight.SharedSpotLightEffect.updates>0,"disabling corners also refreshes native spotlight session geometry");
 }
 static void relativeSlider()throws Throwable {
  Bundle b=settings();b.putInt("qs_card_corner",40);CloudCheck.config(b);
  var slider=new OplusQsVerticalSeekBar();slider.backgroundRect=new android.graphics.Rect(4,6,164,486);slider.mProgressRadius=26;slider.mCurProgressRadius=28.6f;
  var path=new android.graphics.Path();String type=slider.getClass().getName();
  var frame=QsTintCheck.before(type+"#onDraw",slider,new Canvas());
  check(Math.abs(slider.mCurBackgroundRadius-55)<.001f&&Math.abs(slider.mCurProgressRadius-52.8f)<.001f,
    "slider relative radius retains press scaling and native inset between outer and inner tracks");
  QsTintCheck.before(type+"#updateSpotLightPath",slider,path,null);
  check(Math.abs(path.radius-slider.mCurBackgroundRadius)<.001f&&path.bounds.left==4,"slider spotlight matches current scaled track bounds and radius");
  QsTintCheck.after(type+"#onDraw",frame);
  check(slider.mBackgroundRadius==28&&slider.mProgressRadius==26,"relative slider restores native animation fields after draw");
 }
 static void ovalBackend()throws Throwable {
  Bundle b=settings();b.putInt("qs_card_corner",40);CloudCheck.config(b);
  var blur=new BlurConfig();blur.pathProvider=com.oplusos.systemui.common.outline.OvalOutlineProvider.INSTANCE;
  var auto=new AutoBlurDrawable(new ViewBlurProxy(blur));var tile=new MixColorTileDrawable(auto);
  tile.pathProvider=blur.pathProvider;tile.setBounds(new android.graphics.Rect(0,0,160,160));
  QsCornerHook.trackCard(tile);CloudCheck.after(QsTintCheck.TILE+"#<init>",tile,null,auto,null);
  blur.gradientStrokeCornerParam=new com.oplus.posteffect.params.CornerParams(com.oplus.posteffect.params.CornerType.ROUND,80f,0f);
  blur.leftTopCornerRadius=0;blur.rightBottomCornerRadius=23.5f;
  var p=QsTintCheck.before(QsTintCheck.B+"ViewBlurProxy#applyConfigToPlatformBlur",auto.getViewBlurProxy());
  check(blur.pathProvider instanceof RoundRectOutlineProvider&&((RoundRectOutlineProvider)blur.pathProvider).cornerRadius==50,
    "real oval 1x1 blur backend receives resized smooth card geometry");
  check(blur.cornerRadius==50&&blur.leftTopCornerRadius==50&&blur.rightBottomCornerRadius==50,
    "blur radii agree with scaled background instead of original dp radius");
  var stroke=(com.oplus.posteffect.params.CornerParams)blur.gradientStrokeCornerParam;
  check(stroke.getRadius()==50&&stroke.getType()==com.oplus.posteffect.params.CornerType.G2&&stroke.getWeight()==3,
    "edge highlight uses exact scaled G2 geometry rather than a clipped circle");
  QsTintCheck.after(QsTintCheck.B+"ViewBlurProxy#applyConfigToPlatformBlur",p);
  check(blur.pathProvider==com.oplusos.systemui.common.outline.OvalOutlineProvider.INSTANCE&&blur.cornerRadius==47,
    "native oval backend configuration restores after rendering");
  var iconBlur=new BlurConfig();iconBlur.pathProvider=com.oplusos.systemui.common.outline.OvalOutlineProvider.INSTANCE;
  var iconAuto=new AutoBlurDrawable(new ViewBlurProxy(iconBlur));var icon=new MixColorTileDrawable(iconAuto);
  CloudCheck.after(QsTintCheck.TILE+"#<init>",icon,null,iconAuto,null);
  p=QsTintCheck.before(QsTintCheck.B+"ViewBlurProxy#applyConfigToPlatformBlur",iconAuto.getViewBlurProxy());
  check(iconBlur.pathProvider==com.oplusos.systemui.common.outline.OvalOutlineProvider.INSTANCE,
    "unmarked circular icon blur keeps its native oval provider");
  QsTintCheck.after(QsTintCheck.B+"ViewBlurProxy#applyConfigToPlatformBlur",p);
 }
 public static void main(String[] args)throws Throwable {
  ShadeHook.install("com.android.systemui","com.android.systemui",QsGeometryCheck.class.getClassLoader());
  int failures=0;
  for(String scene:new String[]{"providers","threeStage","slider","nativeFallbacks","shaderStroke","missingPressAdapter","factorySmoothness","relativeCorners","ovalBackend","spotlightRefresh","relativeSlider"})try{QsGeometryCheck.class.getDeclaredMethod(scene).invoke(null);}
  catch(java.lang.reflect.InvocationTargetException e){failures++;System.out.println("FAIL: "+scene+": "+e.getCause());}
  if(failures>0)throw new AssertionError(failures+" geometry scenes failed");
  System.out.println("Verified "+checks+" QS geometry checks; phone rendering remains unverified.");
 }
}
