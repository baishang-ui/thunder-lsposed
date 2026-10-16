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
 static Bundle settings(){Bundle b=QsStateCheck.settings();b.putBoolean("qs_card_corner_enabled",true);b.putInt("qs_card_corner",8);return b;}
 static Object afterIfPresent(String key,Object owner,Object result,Object... args)throws Throwable {
  return Api102Runtime.callbacks.containsKey(key)?CloudCheck.after(key,owner,result,args):result;
 }
 static RoundRectOutlineProvider provider(String type,android.graphics.drawable.Drawable d)throws Throwable {
  return (RoundRectOutlineProvider)afterIfPresent(type+"#getPathProvider",d,Reflect.getObjectField(d,"pathProvider"));
 }
 static void providers()throws Throwable {
  Bundle b=settings();CloudCheck.config(b);
  GradientTileDrawable tile=new GradientTileDrawable();CloudCheck.after(QsTintCheck.FLAT+"#<init>",tile,null);
  RoundRectOutlineProvider nativeProvider=(RoundRectOutlineProvider)tile.pathProvider;
  RoundRectOutlineProvider selected=provider(WRAP,tile);
  float density=android.content.res.Resources.getSystem().getDisplayMetrics().density;
  check(selected.cornerRadius==8*density,"1x1 uses selected radius through native outline provider");
  check(selected.cornerWeight==3f,"1x1 uses shared G2 smooth corner weight");
  check(!Api102Runtime.callbacks.containsKey(WRAP+"#getPath"),"native smooth path is not replaced with ordinary addRoundRect");
  check(nativeProvider.cornerRadius==28,"shared firmware outline provider is not mutated");
  BlurConfig blur=new BlurConfig();blur.pathProvider=nativeProvider;
  Reflect.setAdditionalInstanceField(blur,"thunder.role","qs-card");
  ViewBlurProxy proxy=new ViewBlurProxy(blur);
  HookCallback.Param frame=QsTintCheck.before(QsTintCheck.B+"ViewBlurProxy#applyConfigToPlatformBlur",proxy);
  check(blur.pathProvider instanceof RoundRectOutlineProvider&&((RoundRectOutlineProvider)blur.pathProvider).cornerRadius==8*density,
    "blur backend keeps a native smooth path provider instead of clearing it");
  QsTintCheck.after(QsTintCheck.B+"ViewBlurProxy#applyConfigToPlatformBlur",frame);
  check(blur.pathProvider==nativeProvider,"blur provider restores after native backend invocation");
  android.view.View view=new android.view.View();view.setBackground(tile);
  android.graphics.Outline outline=new android.graphics.Outline();
  String clip="com.oplus.systemui.qs.base.widget.QsViewOutlineProvider$QsViewRoundRectOutlineProvider#getOutline";
  QsTintCheck.before(clip,new Object(),view,outline);
  check(outline.radius==8*density,"1x1 view clipping matches its smooth drawable and press shape");
  TileLayerDrawable transition=new TileLayerDrawable();
  check(provider(LAYER,transition).cornerRadius==8*density,"press feedback transition uses same native corner provider");
  com.oplus.systemui.plugins.drawable.PluginDrawable device=new com.oplus.systemui.plugins.drawable.PluginDrawable();
  RoundRectOutlineProvider deviceShape=provider(device.getClass().getName(),device);
  check(deviceShape.cornerRadius==8*density&&deviceShape.cornerWeight==3f,"device center outline uses shared smoothing and receives custom radius");
  android.view.View deviceView=new android.view.View();deviceView.setBackground(device);
  android.graphics.Path pressed=new android.graphics.Path();Object nativeAdapter=new Object();
  String press="com.oplus.systemui.qs.base.spotlight.SpotLightFrameLayout#updateSpotLightPath";
  if(Api102Runtime.callbacks.containsKey(press))QsTintCheck.before(press,deviceView,pressed,nativeAdapter);
  check(pressed.radius==8*density&&deviceShape.adapterUsed==nativeAdapter,
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
  float radius=8*android.content.res.Resources.getSystem().getDisplayMetrics().density;
  for(Float weight:new Float[]{null,0f,1f,1.4f,5f}) {
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
  float selected=8*android.content.res.Resources.getSystem().getDisplayMetrics().density*1.1f;
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
  check(Math.abs(view.mCurProgressRadius-8*density*1.1f)<.01f,"slider inner fill follows custom radius and native press enlargement");
  check(view.mCurProgressRadius==view.mCurBackgroundRadius,"slider inner and outer press shapes stay aligned");
  check(view.mProgressColor==0xffeeeeee&&view.mProgressRoundCornerWeight==3f,"progress color is preserved while smoothness matches the other tiles");
  HookCallback.Param factory=QsTintCheck.before(QsStateCheck.SEEK+"#getSeekBarActiveDrawable",null,view,
    new android.graphics.Rect(),view.mCurProgressRadius,1.4f,true,false,1f);
  check(Math.abs((Float)factory.args[2]-30.8f)<.01f,"slider factory keeps native radius in persistent configs during custom drawing");
  p.failure=new IllegalStateException("frame");QsTintCheck.after(type+"#onDraw",p);
  check(view.mBackgroundRadius==28&&view.mCurProgressRadius==30.8f,"slider native geometry restores even after drawing failure");
  check(Math.abs((Float)afterIfPresent(type+"#getBackgroundRadius",view,28f)-8*density*1.1f)<.01f,"slider press highlighter matches animated background radius");
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
  check(Math.abs(active.cornerRadius-8*density*1.1f)<.01f,"slider cached native inner fill follows custom corners and press scale");
  check(active.platformMixConfig==originalMix&&QsStateCheck.mix(active)==originalMix,
    "slider inner fill retains native contrasting material");QsTintCheck.after(QsTintCheck.B+"ViewBlurProxy#applyConfigToPlatformBlur",p);
  b.putBoolean("qs_card_corner_enabled",false);CloudCheck.config(b);
  p=QsTintCheck.before(type+"#onDraw",view,new Canvas());
  check(view.mCurProgressRadius==30.8f,"slider disabling corners restores native press geometry");QsTintCheck.after(type+"#onDraw",p);
 }
 static void unifiedCorners()throws Throwable {
  Bundle b=settings();
  var nativeShape=new RoundRectOutlineProvider(28f,3f);
  var small=new GradientTileDrawable();small.pathProvider=nativeShape;small.setBounds(new android.graphics.Rect(8,12,168,172));
  var wide=new GradientTileDrawable();wide.pathProvider=nativeShape;wide.setBounds(new android.graphics.Rect(0,0,640,320));
  RoundRectOutlineProvider smallShape=null;
  for(int value:new int[]{0,16,30,31,40,48,63,64}) {
   b.putInt("qs_card_corner",value);CloudCheck.config(b);
   smallShape=provider(WRAP,small);var wideShape=provider(WRAP,wide);
   check(Math.abs(smallShape.cornerRadius-Math.min(80f,value*android.content.res.Resources.getSystem().getDisplayMetrics().density))<.001f,"1x1 dp radius follows value "+value+" with a geometric half-edge clamp");
   check(wideShape!=smallShape&&Math.abs(wideShape.cornerRadius-Math.min(160f,value*android.content.res.Resources.getSystem().getDisplayMetrics().density))<.001f,"wide surface uses the same physical radius with its own provider at "+value);
   check(smallShape.cornerRadius==Math.min(80f,value*android.content.res.Resources.getSystem().getDisplayMetrics().density)&&nativeShape.cornerRadius==28,"another surface cannot overwrite 1x1 geometry at "+value);
  }
  b.putInt("qs_card_corner",20);CloudCheck.config(b);
  smallShape=provider(WRAP,small);
  var view=new android.view.View();view.setBackground(small);var outline=new android.graphics.Outline();
  QsTintCheck.before("com.oplus.systemui.qs.base.widget.QsViewOutlineProvider$QsViewRoundRectOutlineProvider#getOutline",new Object(),view,outline);
  check(outline.radius==55&&outline.smooth&&outline.left==8&&outline.top==12&&outline.right==168&&outline.bottom==172,
    "view clips to exact background G2 path and inset bounds instead of full host rectangle");
  small.setBounds(new android.graphics.Rect(8,12,248,252));QsCornerHook.refresh();
  check(smallShape.cornerRadius==55,"resizing a card keeps the common physical radius");
  b.putBoolean("qs_card_corner_enabled",false);CloudCheck.config(b);QsCornerHook.refresh();
  check(provider(WRAP,small)==nativeShape&&smallShape.cornerRadius==28,"relative geometry restores native provider and cached radius on disable");
 }
 static void spotlightRefresh()throws Throwable {
  Bundle b=settings();b.putInt("qs_card_corner",20);CloudCheck.config(b);
  var view=new android.view.View();var background=new GradientTileDrawable();background.setBounds(new android.graphics.Rect(4,6,164,166));view.setBackground(background);
  var pressed=new android.graphics.Path();
  QsTintCheck.before("com.oplus.systemui.qs.base.spotlight.SpotLightFrameLayout#updateSpotLightPath",view,pressed,null);
  check(pressed.radius==55&&pressed.bounds.left==4,"press path uses shared inset card geometry");
  com.oplus.systemui.qs.base.spotlight.SharedSpotLightEffect.updates=0;
  b.putInt("qs_card_corner",48);CloudCheck.config(b);QsCornerHook.refresh();
  check(com.oplus.systemui.qs.base.spotlight.SharedSpotLightEffect.updates>0&&com.oplus.systemui.qs.base.spotlight.SharedSpotLightEffect.updated.contains(view),
    "corner refresh rebuilds the firmware spotlight session clip instead of leaving its old circle cached");
  b.putBoolean("qs_card_corner_enabled",false);CloudCheck.config(b);com.oplus.systemui.qs.base.spotlight.SharedSpotLightEffect.updates=0;QsCornerHook.refresh();
  check(com.oplus.systemui.qs.base.spotlight.SharedSpotLightEffect.updates>0,"disabling corners also refreshes native spotlight session geometry");
 }
 static void relativeSlider()throws Throwable {
  Bundle b=settings();b.putInt("qs_card_corner",20);CloudCheck.config(b);
  var slider=new OplusQsVerticalSeekBar();slider.backgroundRect=new android.graphics.Rect(4,6,164,486);slider.mProgressRadius=26;slider.mCurProgressRadius=28.6f;
  var path=new android.graphics.Path();String type=slider.getClass().getName();
  var frame=QsTintCheck.before(type+"#onDraw",slider,new Canvas());
  check(Math.abs(slider.mCurBackgroundRadius-60.5f)<.001f&&Math.abs(slider.mCurProgressRadius-58.3f)<.001f,
    "slider shared radius retains press scaling and native inset between outer and inner tracks");
  QsTintCheck.before(type+"#updateSpotLightPath",slider,path,null);
  check(Math.abs(path.radius-slider.mCurBackgroundRadius)<.001f&&path.bounds.left==4,"slider spotlight matches current scaled track bounds and radius");
  QsTintCheck.after(type+"#onDraw",frame);
  check(slider.mBackgroundRadius==28&&slider.mProgressRadius==26,"relative slider restores native animation fields after draw");
 }
 static void ovalBackend()throws Throwable {
  Bundle b=settings();b.putInt("qs_card_corner",20);CloudCheck.config(b);
  var blur=new BlurConfig();blur.pathProvider=com.oplusos.systemui.common.outline.OvalOutlineProvider.INSTANCE;
  var auto=new AutoBlurDrawable(new ViewBlurProxy(blur));var tile=new MixColorTileDrawable(auto);
  tile.pathProvider=blur.pathProvider;tile.setBounds(new android.graphics.Rect(0,0,160,160));
  QsCornerHook.trackCard(tile);CloudCheck.after(QsTintCheck.TILE+"#<init>",tile,null,auto,null);
  blur.gradientStrokeCornerParam=new com.oplus.posteffect.params.CornerParams(com.oplus.posteffect.params.CornerType.ROUND,80f,0f);
  blur.leftTopCornerRadius=0;blur.rightBottomCornerRadius=23.5f;
  var p=QsTintCheck.before(QsTintCheck.B+"ViewBlurProxy#applyConfigToPlatformBlur",auto.getViewBlurProxy());
  check(blur.pathProvider instanceof RoundRectOutlineProvider&&((RoundRectOutlineProvider)blur.pathProvider).cornerRadius==55,
    "real oval 1x1 blur backend receives resized smooth card geometry");
  check(blur.cornerRadius==55&&blur.leftTopCornerRadius==55&&blur.rightBottomCornerRadius==55,
    "blur radii agree with scaled background instead of original dp radius");
  var stroke=(com.oplus.posteffect.params.CornerParams)blur.gradientStrokeCornerParam;
  check(stroke.getRadius()==55&&stroke.getType()==com.oplus.posteffect.params.CornerType.G2&&stroke.getWeight()==3,
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
 static void allTileTypes()throws Throwable {
  Bundle b=settings();CloudCheck.config(b);
  var small=new com.oplus.systemui.plugins.qs.customize.view.tile.OplusQSResizeableTileViewOneXOne();
  var wide=new com.oplus.systemui.plugins.qs.customize.view.tile.OplusQSResizeableTileViewTwoXOne();
  var stages=new com.oplus.systemui.plugins.qs.customize.view.tile.OplusQSResizeableThreeStageView();
  var large=new com.oplus.systemui.plugins.qs.customize.view.tile.OplusQSResizeableTileViewTwoXTwo();
  var cards=java.util.List.of(small,wide,stages,large);
  int size=200;
  for(var card:cards) {
   var bg=new GradientTileDrawable();bg.pathProvider=com.oplusos.systemui.common.outline.OvalOutlineProvider.INSTANCE;
   bg.setBounds(new android.graphics.Rect(4,6,size+4,206));size+=200;
   card.bgDrawable.setDrawable(bg);
   card.transitionDrawable.pathProvider=bg.pathProvider;card.transitionDrawable.setBounds(bg.getBounds());
   CloudCheck.after(com.oplus.systemui.plugins.qs.customize.view.tile.OplusQSResizeableTileView.class.getName()+"#<init>",card,null);
  }
  var media=new com.oplus.systemui.plugins.drawable.PluginDrawable();media.setBounds(new android.graphics.Rect(0,0,600,400));
  media.pathProvider=com.oplusos.systemui.common.outline.OvalOutlineProvider.INSTANCE;
  for(int value:new int[]{0,16,30,31,40,48,63,64}) {
   b.putInt("qs_card_corner",value);CloudCheck.config(b);QsCardHook.refresh();
   float expected=value*android.content.res.Resources.getSystem().getDisplayMetrics().density;
   for(var card:cards) {
    var bg=(GradientTileDrawable)card.bgDrawable.getDrawable();
    var shape=provider(WRAP,bg);var transition=provider(LAYER,card.transitionDrawable);
    float clamped=Math.min(expected,Math.min(bg.getBounds().width(),bg.getBounds().height())*.5f);
    check(shape.cornerRadius==clamped&&transition.cornerRadius==clamped&&shape.cornerWeight==3&&transition.cornerWeight==3,
      card.getClass().getSimpleName()+" background and transition share the common radius and G2 weight at "+value);
   }
   var shape=provider(media.getClass().getName(),media);
   check(shape.cornerRadius==expected&&shape.cornerWeight==3,"media/device card shares tile curvature at "+value);
  }
  b.putBoolean("qs_card_corner_enabled",false);CloudCheck.config(b);QsCornerHook.refresh();
  for(var card:cards)check(afterIfPresent(WRAP+"#getPathProvider",card.bgDrawable.getDrawable(),
    ((GradientTileDrawable)card.bgDrawable.getDrawable()).pathProvider)==com.oplusos.systemui.common.outline.OvalOutlineProvider.INSTANCE,
    "disabling restores the native oval for "+card.getClass().getSimpleName());
 }
 static void rebuiltBackground()throws Throwable {
  CloudCheck.config(settings());
  var tile=new com.oplus.systemui.plugins.qs.customize.view.tile.OplusQSResizeableTileViewOneXOne();
  CloudCheck.after(com.oplus.systemui.plugins.qs.customize.view.tile.OplusQSResizeableTileView.class.getName()+"#<init>",tile,null);
  var blur=new BlurConfig();blur.pathProvider=com.oplusos.systemui.common.outline.OvalOutlineProvider.INSTANCE;
  var auto=new AutoBlurDrawable(new ViewBlurProxy(blur));var replacement=new MixColorTileDrawable(auto);
  replacement.pathProvider=blur.pathProvider;
  tile.bgDrawable.setDrawable(replacement);
  CloudCheck.after(WRAP+"#setDrawable",tile.bgDrawable,null,replacement);
  check(provider(WRAP,replacement).cornerWeight==3&&replacement.pathInvalidations>0,
    "theme child replacement inherits card ownership and rebuilds its native oval path");
  var frame=QsTintCheck.before(QsTintCheck.B+"ViewBlurProxy#applyConfigToPlatformBlur",auto.getViewBlurProxy());
  check(blur.pathProvider instanceof RoundRectOutlineProvider&&blur.cornerRadius==QsCornerHook.radius(),
    "replacement blur backend receives new card ownership as well as its visible path");
  QsTintCheck.after(QsTintCheck.B+"ViewBlurProxy#applyConfigToPlatformBlur",frame);
  tile.bgDrawable.setDrawable(new GradientTileDrawable());
  ((GradientTileDrawable)tile.bgDrawable.getDrawable()).pathProvider=com.oplusos.systemui.common.outline.OvalOutlineProvider.INSTANCE;
  CloudCheck.after(tile.getClass().getName()+"#onDrawableUpdate",tile,null,java.util.List.of(),true);
  check(provider(WRAP,(GradientTileDrawable)tile.bgDrawable.getDrawable()).cornerRadius==QsCornerHook.radius(),
    "1x1 rebuild hook rechecks the new cached background even without a replacement callback");
  var indicator=new TileLayerDrawable();indicator.pathProvider=com.oplusos.systemui.common.outline.OvalOutlineProvider.INSTANCE;
  var unrelated=new TileDrawableWrapper(indicator);
  CloudCheck.after(WRAP+"#setDrawable",unrelated,null,indicator);
  check(afterIfPresent(LAYER+"#getPathProvider",indicator,indicator.pathProvider)==indicator.pathProvider,
    "unrelated wrapper replacement does not turn a circular indicator into a rounded tile");
 }
 static void separateSliderLayers()throws Throwable {
  CloudCheck.config(settings());
  var slider=new OplusQsVerticalSeekBar();slider.mProgressRadius=26;slider.mCurProgressRadius=28.6f;
  slider.mBackgroundRoundCornerWeight=5f;slider.mProgressRoundCornerWeight=2f;
  var base=new BlurConfig();var active=new BlurConfig();
  slider.baseMixColorDrawable=new AutoBlurDrawable(new ViewBlurProxy(base));
  slider.activeMixColorDrawable=new AutoBlurDrawable(new ViewBlurProxy(active));
  String type=slider.getClass().getName();
  var draw=QsTintCheck.before(type+"#onDraw",slider,new Canvas());
  float outer=slider.mCurBackgroundRadius,inner=slider.mCurProgressRadius;
  check(Math.abs(outer-inner-2.2f)<.001f&&slider.mBackgroundRoundCornerWeight==3&&slider.mProgressRoundCornerWeight==3,
    "slider inset is concentric and both native draw paths use the common G2 weight");
  for(var config:java.util.List.of(base,active)) {
   var proxy=config==base?((AutoBlurDrawable)slider.baseMixColorDrawable).getViewBlurProxy():((AutoBlurDrawable)slider.activeMixColorDrawable).getViewBlurProxy();
   ShadeHook.trackQsCard(proxy);
   var frame=QsTintCheck.before(QsTintCheck.B+"ViewBlurProxy#applyConfigToPlatformBlur",proxy);
   check(Math.abs(config.cornerRadius-(inner))<.001f&&config.radiusWeight==3,
     (config==base?"background":"progress")+" blur uses the animated inner clip radius");
   QsTintCheck.after(QsTintCheck.B+"ViewBlurProxy#applyConfigToPlatformBlur",frame);
  }
  var factory=QsTintCheck.before(QsStateCheck.SEEK+"#getSeekBarBackground",null,slider,new android.graphics.Rect(),outer,3f,true,false,1f);
  check(factory.args[2].equals(28.6f)&&factory.args[3].equals(5f),"background factory preserves native inner geometry used by the firmware material");
  QsTintCheck.after(type+"#onDraw",draw);
  check(slider.mBackgroundRoundCornerWeight==5&&slider.mProgressRoundCornerWeight==2&&slider.mCurProgressRadius==28.6f,
    "different native outer and inner settings restore after custom slider drawing");
 }
 static void deviceFirstRefresh()throws Throwable {
  Bundle b=settings();b.putBoolean("qs_card_corner_enabled",false);CloudCheck.config(b);
  var device=new com.oplus.systemui.plugins.drawable.PluginDrawable();device.setBounds(new android.graphics.Rect(0,0,240,240));
  var nativeShape=(RoundRectOutlineProvider)device.pathProvider;
  var content=new android.view.View();content.setBackground(device);content.setOutlineProvider(nativeShape);
  var host=new android.view.View();host.setBackground(null);host.setOutlineProvider(nativeShape);
  String builder="com.oplus.systemui.plugins.drawable.MixColorPluginDrawable$BuilderConfig#build";
  CloudCheck.after(builder,new com.oplus.systemui.plugins.drawable.MixColorPluginDrawable.BuilderConfig(),device,content,true);
  String clip=RoundRectOutlineProvider.class.getName()+"#getOutline";
  QsTintCheck.before(clip,nativeShape,host,new android.graphics.Outline());
  int invalidations=host.outlineInvalidations;
  b.putBoolean("qs_card_corner_enabled",true);b.putInt("qs_card_corner",17);CloudCheck.config(b);QsCornerHook.refresh();
  check(host.outlineInvalidations>invalidations&&content.outlineInvalidations>0,
    "first enabling corners invalidates both device content and its backgroundless parent without reattachment");
  var outline=new android.graphics.Outline();QsTintCheck.before(clip,nativeShape,host,outline);
  float density=android.content.res.Resources.getSystem().getDisplayMetrics().density;
  check(outline.radius==17*density&&outline.smooth&&host.getOutlineProvider()==nativeShape,
    "first device outline draw uses current dp geometry even when the host retains its original provider");
  b.putInt("qs_card_corner",30);CloudCheck.config(b);QsCornerHook.refresh();
  outline=new android.graphics.Outline();QsTintCheck.before(clip,nativeShape,host,outline);
  check(outline.radius==30*density,"device clip follows the next live radius without closing control center");
  b.putBoolean("qs_card_corner_enabled",false);CloudCheck.config(b);QsCornerHook.refresh();
  outline=new android.graphics.Outline();QsTintCheck.before(clip,nativeShape,host,outline);nativeShape.getOutline(host,outline);
  check(outline.radius==28&&nativeShape.cornerRadius==28,"disabling device corners restores the unmodified native clip");
 }
 static void notificationUnits()throws Throwable {
  Bundle b=settings();b.putBoolean("notification_card_corner_enabled",true);
  var tile=new GradientTileDrawable();tile.setBounds(new android.graphics.Rect(0,0,1000,1000));
  for(int value:new int[]{0,16,30,40,48,64}) {
   b.putInt("qs_card_corner",value);b.putInt("notification_card_corner",value);CloudCheck.config(b);
   var row=new BlurConfig();Reflect.setAdditionalInstanceField(row,"thunder.role","notification-card");
   var proxy=new ViewBlurProxy(row);var frame=QsTintCheck.before(QsTintCheck.B+"ViewBlurProxy#applyConfigToPlatformBlur",proxy);
   check(provider(WRAP,tile).cornerRadius==row.cornerRadius,
     "same "+value+" dp value produces the same actual radius for QS and notification cards");
   QsTintCheck.after(QsTintCheck.B+"ViewBlurProxy#applyConfigToPlatformBlur",frame);
  }
  check(QsCornerHook.radius()==64*android.content.res.Resources.getSystem().getDisplayMetrics().density,
    "QS maximum now reaches 64dp instead of the previous 32dp");
 }
 static void completeRoundStroke()throws Throwable {
  Bundle b=settings();float density=android.content.res.Resources.getSystem().getDisplayMetrics().density;
  for(int shortSide:new int[]{216,432}) {
   for(boolean square:new boolean[]{true,false}) {
    var tile=new GradientTileDrawable();tile.setBounds(new android.graphics.Rect(0,0,square?shortSide:shortSide*2,shortSide));
    QsCornerHook.trackCard(tile);
    for(int value:new int[]{24,28,30,31,32,40,48,64}) {
     b.putInt("qs_card_corner",value);CloudCheck.config(b);
     var shape=provider(WRAP,tile);
     var config=new BlurConfig();Reflect.setAdditionalInstanceField(config,"thunder.role","qs-card");
     QsCornerHook.trackBlur(new ViewBlurProxy(config),tile);config.pathProvider=shape;
     var nativeStroke=new com.oplus.posteffect.params.CornerParams(com.oplus.posteffect.params.CornerType.FULL,108f,1f);
     config.gradientStrokeCornerParam=nativeStroke;
     var frame=QsTintCheck.before(QsTintCheck.B+"ViewBlurProxy#applyConfigToPlatformBlur",new ViewBlurProxy(config));
     var stroke=(com.oplus.posteffect.params.CornerParams)config.gradientStrokeCornerParam;
     boolean full=value*density>=shortSide*.5f;
     check(stroke.getType()==(full?com.oplus.posteffect.params.CornerType.FULL:com.oplus.posteffect.params.CornerType.G2)
         &&stroke.getWeight()==(full?1f:3f)&&stroke.getRadius()==Math.min(value*density,shortSide*.5f),
       (square?"circle":"capsule")+" rim matches the background at "+value+" dp and short edge "+shortSide);
     QsTintCheck.after(QsTintCheck.B+"ViewBlurProxy#applyConfigToPlatformBlur",frame);
     check(config.gradientStrokeCornerParam==nativeStroke,"stroke override restores native configuration");
    }
   }
  }
 }
 public static void main(String[] args)throws Throwable {
  ShadeHook.install("com.android.systemui","com.android.systemui",QsGeometryCheck.class.getClassLoader());
  int failures=0;
  for(String scene:new String[]{"providers","threeStage","slider","nativeFallbacks","shaderStroke","missingPressAdapter","factorySmoothness","unifiedCorners","ovalBackend","spotlightRefresh","relativeSlider","allTileTypes","rebuiltBackground","separateSliderLayers","deviceFirstRefresh","notificationUnits","completeRoundStroke"})try{QsGeometryCheck.class.getDeclaredMethod(scene).invoke(null);}
  catch(java.lang.reflect.InvocationTargetException e){failures++;System.out.println("FAIL: "+scene+": "+e.getCause());}
  if(failures>0)throw new AssertionError(failures+" geometry scenes failed");
  System.out.println("Verified "+checks+" QS geometry checks; phone rendering remains unverified.");
 }
}
