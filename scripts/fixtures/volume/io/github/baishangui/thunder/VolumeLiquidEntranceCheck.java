package io.github.baishangui.thunder;

import android.graphics.Canvas;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import com.oplus.systemui.volume.OplusVolumeDialogImpl;
import com.oplus.systemui.volume.OplusVolumeSeekBar;
import com.oplus.systemui.volume.utils.VolumeAnimUtil;
import com.oplusos.systemui.common.blurability.platformblur.PlatformBlurHelper;
import com.oplus.posteffect.drawable.MetaBallBlurDrawable;

/** Calls the production callbacks. Native shader pixels and GPU frame pacing need a device. */
public final class VolumeLiquidEntranceCheck {
    static final String VIEW="com.oplus.systemui.volume.view.OplusVolumeDialogView#",
        HELPER="com.oplusos.systemui.common.blurability.platformblur.PlatformBlurHelper#",
        DRAW="com.oplusos.systemui.common.blurability.platformblur.PlatformBlurDrawable#draw";
    static int checks,ended;
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
    static HookCallback.Param before(String key,Object object,Object... args)throws Throwable {
        return VolumePanelCheck.before(key,object,args);
    }
    static void after(String key,HookCallback.Param p)throws Throwable {Api102Runtime.callbacks.get(key).after(p);}
    static HookCallback.Param enter(OplusVolumeDialogImpl owner,View target,float x)throws Throwable {
        return before(VIEW+"doShowOrHideAnimate",owner.mOplusVolumeDialogView,target,700L,0L,x,true,-1,null,(Runnable)()->ended++,true);
    }
    static void config(boolean enabled,boolean liquid) throws Exception {
        Bundle b=new Bundle();b.putBoolean("enabled",enabled);b.putBoolean("volume_liquid_entrance",liquid);CloudCheck.config(b);
    }
    static void scenario(boolean right)throws Throwable {
        config(true,true);android.os.SystemClock.now=1000;
        OplusVolumeDialogImpl owner=new OplusVolumeDialogImpl();
        ViewGroup root=owner.mOplusVolumeDialogView.mDialogView;root.actualWidth=1080;root.screenX=root.screenY=0;
        ViewGroup target=new ViewGroup();target.actualWidth=66;target.actualHeight=260;
        target.setPivotX(33);target.setPivotY(260);root.addView(target);
        OplusVolumeSeekBar bar=new OplusVolumeSeekBar();target.addView(bar);
        target.setTranslationX(right?120:-120);bar.screenX=right?1060:-36;
        int beforeEnds=ended;
        HookCallback.Param enter=enter(owner,target,right?120:-120);
        check(target.getPivotX()==33&&target.getPivotY()==260,"entrance preserves native bar/button pivots and their vertical gap");
        check(enter.args[1].equals(780L)&&!root.getClipChildren(),"entrance captures clipping and native duration");
        HookCallback.Param utility=before("com.oplus.systemui.volume.utils.VolumeAnimUtil#startViewPropertyAnimation",null,
                target,0L,620L,new Object(),0f,0f,1f,1f,null,null,null,13);
        check(target.getScaleX()==1&&target.getScaleY()==1&&target.getAlpha()==1,"both lobes keep their full size while only the native connecting neck deforms");
        float screenEdge=root.screenX+(right?root.actualWidth:0);
        // This fixture stores child screen coordinates independently. Apply the parent's
        // translation delta to the child as Android's view transform would do.
        float shift=target.getTranslationX()-(right?120:-120);bar.screenX+=Math.round(shift);
        int firstBarX=bar.screenX;
        float restingX=bar.screenX-target.getTranslationX();
        float barInside=right?screenEdge-bar.screenX:bar.screenX+56-screenEdge;
        check(Math.abs(barInside-56*.18f)<1f,"first animated frame already intersects the screen edge instead of traversing hidden space");
        check(utility.args[3] instanceof com.coui.appcompat.animation.COUISpringInterpolator,"uses the native spring");
        var spring=(com.coui.appcompat.animation.COUISpringInterpolator)utility.args[3];
        check(spring.response==.82&&spring.bounce==.58,"preserve the requested fast native spring response");
        check(spring.getInterpolation(200f/780f)>.9f&&spring.getInterpolation(200f/780f)<1f,"bar reaches its resting position around 200ms");
        check(spring.getInterpolation(350f/780f)>1.25f&&spring.getInterpolation(1f)==1f,"preserve the original overshoot and exact final position");
        after(VIEW+"doShowOrHideAnimate",enter);
        PlatformBlurHelper helper=new PlatformBlurHelper(bar);
        Reflect.setAdditionalInstanceField(helper.blurConfig,"thunder.role","volume-bar");
        HookCallback.Param ensure=before(HELPER+"ensureBlurDrawable",helper);
        check(helper.blurConfig.isSupportMetaBall,"native helper receives metaball capability");
        helper.ensureBlurDrawable();after(HELPER+"ensureBlurDrawable",ensure);
        check(!helper.blurConfig.isSupportMetaBall,"temporary native configuration restored");
        int[] contentDraws={0};
        com.oplus.systemui.volume.utils.material.OplusVolumeBackgroundWrapper wrapper=
                new com.oplus.systemui.volume.utils.material.OplusVolumeBackgroundWrapper(new android.graphics.drawable.Drawable(){
                    @Override public void draw(Canvas canvas){
                        check(getBounds().left==4&&getBounds().top==12&&getBounds().right==52&&getBounds().bottom==220,"liquid background uses the native visible bar bounds, not the padded view rectangle");
                        contentDraws[0]++;
                    }
                });
        bar.mMaterialHost.visual=new android.graphics.Rect(4,12,52,220);
        wrapper.content.setBounds(0,0,56,260);
        bar.setBackground(wrapper);
        before("com.oplus.systemui.volume.utils.material.OplusVolumeBackgroundWrapper#draw",wrapper,new Canvas());
        check(contentDraws[0]==1,"native solitary-bar press clipping cannot cut off the liquid bridge");
        check(wrapper.content.getBounds().top==0&&wrapper.content.getBounds().bottom==260,"temporary visible shape bounds restore after drawing");
        helper.blurDrawable.setBounds(0,0,56,240);
        MetaBallBlurDrawable backend=(MetaBallBlurDrawable)helper.blurDrawable.blurDrawable;
        check(backend.copies==1,"reciprocal material is prepared before the first visible draw");
        check(backend.getEnableShader()&&backend.lastCopy.getEnableShader(),"both liquid halves explicitly enable the native metaball shader, not just its geometry");
        bar.screenX=right?1100:-80;
        before(DRAW,helper.blurDrawable,new Canvas());
        check(backend.balls.isEmpty()&&backend.lastCopy.draws==0,"hidden edge anchor cannot appear before the bar reaches the screen");
        bar.screenX=firstBarX;
        Canvas tetherCanvas=new Canvas();
        before(DRAW,helper.blurDrawable,tetherCanvas);
        check(!tetherCanvas.translations.isEmpty()&&Math.abs(tetherCanvas.translations.get(0)[0]-backend.balls.get(0).posX)<.01f,"reciprocal shader is drawn at its own main ball center, not the bar center");
        check(tetherCanvas.depth==0,"peer canvas transform is restored before the native bar draw");
        check(backend.range>0&&backend.balls.size()==1,"bar adds the real edge metaball");
        PlatformBlurHelper progressHelper=new PlatformBlurHelper(bar);
        HookCallback.Param progressEnsure=before(HELPER+"ensureBlurDrawable",progressHelper);
        progressHelper.ensureBlurDrawable();after(HELPER+"ensureBlurDrawable",progressEnsure);
        before(DRAW,progressHelper.blurDrawable,new Canvas());
        check(backend.range>0&&backend.balls.size()==1,"a progress drawable sharing the same host cannot terminate or take over the background tether");
        MetaBallBlurDrawable.MetaBall edge=backend.balls.get(0);
        float physicalEdge=(right?root.actualWidth:0)-bar.screenX;
        float exposed=0;
        check(Math.abs(edge.posX+28-(physicalEdge+(right?1:-1)*(edge.width/2-exposed)))<1,"anchor remains off-screen; only the native deformation enters the display");
        check(Math.abs(edge.corner-Math.min(edge.width,edge.height)/2)<.01f,"edge lobe has its own fully rounded silhouette instead of the bar corner on a wide rectangle");
        float initialHeight=edge.height;
        check(initialHeight>=56*2&&initialHeight<=56*2.5f,"edge anchor is a compact droplet sized from bar width, not a full-height second bar");
        float origin=target.getTranslationX();
        android.os.SystemClock.now=1200;
        target.setTranslationX(origin*(1-spring.getInterpolation(200f/780f)));
        bar.screenX=Math.round(restingX+target.getTranslationX());
        before(DRAW,helper.blurDrawable,new Canvas());
        check(!backend.balls.isEmpty()&&edge.height==initialHeight,"fast native motion still has an intact liquid attachment on reaching the resting position");
        target.setTranslationX(0);bar.screenX=Math.round(restingX+target.getTranslationX());
        before(DRAW,helper.blurDrawable,new Canvas());
        check(edge.height==initialHeight&&peerSize(backend)==initialHeight,"withdrawal cannot shrink either end lobe");
        float geometryGap=Math.abs(edge.posX)-(56+edge.width)/2f;
        float nativeOverlap=nativeOverlap(edge,56);
        check(nativeOverlap>8&&nativeOverlap<50,"native bridge remains substantial at the resting position");
        float fixedStrength=edge.blendShape;
        int range=backend.range;
        int submissions=backend.submissions;
        int rangeChanges=backend.rangeChanges;
        MetaBallBlurDrawable peer=backend.lastCopy;
        float restingNeck=edge.shapeRangeFactorX;
        check(restingNeck>=1.5f,"native deformation stays broad instead of being squeezed into a thin needle");
        int peerBounds=peer.boundsChanges,peerSubmissions=peer.submissions;
        android.os.SystemClock.now=1200;
        before(DRAW,helper.blurDrawable,new Canvas());
        check(right?edge.posX>0:edge.posX<0,"anchor mirrors with physical volume-key side");
        check(backend.range==range&&backend.rangeChanges==rangeChanges,"moving tether keeps capture bounds stable");
        check(backend.submissions==submissions+1,"all source ball changes submit to the native shader once per frame");
        check(peer.boundsChanges==peerBounds&&peer.submissions==peerSubmissions+1,"edge material also keeps capture bounds stable and submits once");
        check(edge.height==initialHeight&&edge.blendShape==fixedStrength,"redrawing the same frame keeps the native reach stable");
        int invalidated=bar.invalidations;bar.observer.dispatchOnPreDraw();
        check(bar.invalidations>invalidated,"pre-draw rerecords material during render-node movement");
        var initialUniforms=uniforms(backend);
        check(initialUniforms.preCmpArray12[11]==40&&initialUniforms.preCmpArray12[14]==40,"both native protrusions start fully deformed");
        float previousDisplacement=40;
        for(float phase:new float[]{.15f,.3f,.5f,.7f,.85f,1f}) {
            android.os.SystemClock.now=1200+Math.round(220*android.animation.ValueAnimator.getDurationScale()*phase);
            target.setTranslationX(origin*(1-spring.getInterpolation((200+220*phase)/780f)));
            bar.screenX=Math.round(restingX+target.getTranslationX());
            before(DRAW,helper.blurDrawable,new Canvas());
            var barUniforms=uniforms(backend);
            var edgeUniforms=uniforms(peer);
            float displacement=barUniforms.preCmpArray12[11];
            check(!peer.removed&&!backend.balls.isEmpty(),"material survives until the bar-side protrusion fully retracts");
            check(displacement<previousDisplacement&&displacement>=0,"actual bar-side shader displacement decreases over multiple frames");
            check(displacement==edgeUniforms.preCmpArray12[11]&&displacement==barUniforms.preCmpArray12[14],"both reciprocal shaders retract the same contour");
            check(edge.height==initialHeight&&target.getScaleX()==1&&target.getScaleY()==1,"retraction does not scale either body or add a separate visible droplet");
            check(edge.shapeRangeFactorX==restingNeck&&nativeInfluence(edge,56)>.99f,"native support stays alive instead of crossing its abrupt distance cutoff");
            check(barUniforms.preCmpArray12[9]==7&&barUniforms.preCmpArray12[10]==7&&barUniforms.ball1Array[3]==120,"uniform hook preserves support dimensions and bar geometry");
            check(Math.abs(barUniforms.preCmpArray12[17]-12*displacement/40)<.001f,"smooth union fades with displacement, leaving no residual bridge");
            previousDisplacement=displacement;
        }
        check(previousDisplacement==0,"render an undeformed bar before removing the pair");
        before(DRAW,helper.blurDrawable,new Canvas());
        check(uniforms(backend).preCmpArray12[11]==40,"cleanup removes only this entrance's uniform annotation");
        check(backend.range==range&&backend.balls.isEmpty()&&bar.observer.listeners.isEmpty(),"separation releases tether and observer without changing the bar capture/color processing");
        check(peer.removed,"separation unregisters the reciprocal blur capture");
        check(backend.mainBall.shapeRangeFactorX==1.2f,"separation restores the native main ball's deformation range");
        check(backend.getEnableShader(),"separation retains the same native material path to prevent a color jump");
        before("com.oplus.systemui.volume.utils.material.OplusVolumeBackgroundWrapper#draw",wrapper,new Canvas());
        check(contentDraws[0]==2,"bar geometry stays consistent after separation until the spring settles");
        // An overshoot must not attach the tether again.
        target.setTranslationX(right?-3:3);before(DRAW,helper.blurDrawable,new Canvas());
        check(backend.balls.isEmpty(),"spring overshoot cannot reattach to the edge");
        target.setScaleX(1);target.setScaleY(1);target.setTranslationX(0);
        ((Runnable)enter.args[7]).run();
        check(backend.getEnableShader(),"spring completion cannot switch the settled bar's color pipeline");
        config(true,false);VolumeLiquidEntrance.refresh();
        check(!backend.getEnableShader()&&backend.range==0,"disabling liquid entrance restores the original material path and capture range");
        config(true,true);
        check(ended==beforeEnds+1,"native end action executes exactly once");
        check(root.getClipChildren()&&root.getClipToPadding()&&target.attachListeners.isEmpty(),"completion restores ancestor clipping and listeners");
        check(target.getPivotX()==33&&target.getPivotY()==260,"completion restores native pivot");
    }
    static com.oplus.posteffect.params.MetaBallParamsKt.UniformResult uniforms(MetaBallBlurDrawable backend)throws Throwable {
        String key="com.oplus.posteffect.params.MetaBallParamsKt#buildMetaBallUniformArrays";
        Object params=backend.getDrawableShader().getMetaBallParams();
        HookCallback.Param p=before(key,null,new float[4],params);
        var result=com.oplus.posteffect.params.MetaBallParamsKt.buildMetaBallUniformArrays(new float[4],backend.getDrawableShader().getMetaBallParams());
        p.setResult(result);after(key,p);return result;
    }
    // Reference from the firmware's MetaBallParamsKt.buildPreCmpArray. Checking
    // shader influence catches the old zero-width needle even if all hooks succeed.
    static float smooth(float t){t=Math.max(0,Math.min(1,t));return t*t*(3-2*t);}
    static float nativeInfluence(MetaBallBlurDrawable.MetaBall ball,float width){
        float gap=Math.abs(ball.posX)-(width+ball.width)/2;
        return smooth((ball.blendShape*2+5-Math.max(gap,0))/5);
    }
    static float nativeOverlap(MetaBallBlurDrawable.MetaBall ball,float width){
        float gap=Math.abs(ball.posX)-(width+ball.width)/2,total=ball.blendShape*2;
        float influence=nativeInfluence(ball,width);
        return total*smooth((width/2+gap)/(width/2+total))*influence-gap;
    }
    static float peerSize(MetaBallBlurDrawable backend){return backend.lastCopy.mainBall.height;}
    static void anchorProportions() throws Throwable {
        // The settings allow short/wide as well as long/narrow bars. A compact
        // anchor must stay bounded for all of them, at different display densities.
        for(float density:new float[]{1f,3f})for(int width:new int[]{32,56,96})for(int height:new int[]{80,240,400}) {
            OplusVolumeDialogImpl owner=new OplusVolumeDialogImpl();
            ViewGroup root=owner.mOplusVolumeDialogView.mDialogView;root.screenX=0;root.actualWidth=1080;
            ViewGroup target=new ViewGroup();root.addView(target);
            OplusVolumeSeekBar bar=new OplusVolumeSeekBar();target.addView(bar);
            bar.res=new android.content.res.Resources(){
                @Override public android.util.DisplayMetrics getDisplayMetrics(){
                    android.util.DisplayMetrics metrics=new android.util.DisplayMetrics();metrics.density=density;return metrics;
                }
            };
            int w=Math.round(width*density),h=Math.round(height*density);
            bar.screenX=1080-w/2;target.setTranslationX(w);
            HookCallback.Param p=enter(owner,target,w);after(VIEW+"doShowOrHideAnimate",p);
            PlatformBlurHelper helper=new PlatformBlurHelper(bar);
            Reflect.setAdditionalInstanceField(helper.blurConfig,"thunder.role","volume-bar");
            HookCallback.Param ensure=before(HELPER+"ensureBlurDrawable",helper);
            helper.ensureBlurDrawable();after(HELPER+"ensureBlurDrawable",ensure);
            helper.blurDrawable.setBounds(0,0,w,h);
            before(DRAW,helper.blurDrawable,new Canvas());
            MetaBallBlurDrawable backend=(MetaBallBlurDrawable)helper.blurDrawable.blurDrawable;
            MetaBallBlurDrawable.MetaBall anchor=backend.balls.get(0);
            check(anchor.width<=72*density&&anchor.width<=w*1.35f&&anchor.height<=h&&anchor.height<=w*2.3f,
                    "anchor stays compact across supported bar sizes and densities");
            check(anchor.corner>0&&anchor.corner<=Math.min(anchor.width,anchor.height)/2f,
                    "short/wide bars retain a valid rounded anchor");
            VolumeLiquidEntrance.finishDialog(owner.mOplusVolumeDialogView);
            check(backend.balls.isEmpty()&&backend.mainBall.shapeRangeFactorX==1.2f&&root.getClipChildren(),
                    "interruption restores the main ball and clipping at every aspect ratio");
        }
    }
    public static void main(String[] args)throws Throwable {
        VolumeLiquidEntrance.install(VolumeLiquidEntranceCheck.class.getClassLoader());
        config(true,true);
        check(uniforms(new MetaBallBlurDrawable()).preCmpArray12[11]==40,"unrelated notification or cloud materials are untouched");
        PlatformBlurHelper idle=new PlatformBlurHelper(new OplusVolumeSeekBar());
        Reflect.setAdditionalInstanceField(idle.blurConfig,"thunder.role","volume-bar");
        HookCallback.Param initial=before(HELPER+"ensureBlurDrawable",idle);
        idle.ensureBlurDrawable();after(HELPER+"ensureBlurDrawable",initial);
        MetaBallBlurDrawable idleMaterial=(MetaBallBlurDrawable)idle.blurDrawable.blurDrawable;
        check(idleMaterial.getEnableShader()&&idleMaterial.copies==0,"resting bar uses the entrance color path without allocating an edge capture");
        VolumeLiquidEntrance.refresh();
        check(idleMaterial.getEnableShader(),"unrelated settings refresh preserves the enabled color path");
        config(false,true);VolumeLiquidEntrance.refresh();
        check(!idleMaterial.getEnableShader(),"global disable restores the pre-liquid material path");
        scenario(true);scenario(false);
        android.animation.ValueAnimator.durationScale=.5f;scenario(true);
        android.animation.ValueAnimator.durationScale=2f;scenario(false);
        android.animation.ValueAnimator.durationScale=1;anchorProportions();
        OplusVolumeDialogImpl owner=new OplusVolumeDialogImpl();
        ViewGroup root=owner.mOplusVolumeDialogView.mDialogView;
        ViewGroup one=new ViewGroup(),two=new ViewGroup();root.addView(one);root.addView(two);
        OplusVolumeSeekBar first=new OplusVolumeSeekBar(),second=new OplusVolumeSeekBar();one.addView(first);two.addView(second);
        one.setTranslationX(120);two.setTranslationX(120);
        HookCallback.Param p=enter(owner,one,120);after(VIEW+"doShowOrHideAnimate",p);
        HookCallback.Param q=enter(owner,two,120);after(VIEW+"doShowOrHideAnimate",q);
        ((Runnable)p.args[7]).run();check(!root.getClipChildren(),"concurrent rows retain shared clipping lease");
        VolumeLiquidEntrance.finishDialog(owner.mOplusVolumeDialogView);
        check(root.getClipChildren()&&second.observer.listeners.isEmpty(),"interrupted hide/expand releases remaining row");
        p=enter(owner,one,120);after(VIEW+"doShowOrHideAnimate",p);
        for(View.OnAttachStateChangeListener listener:new java.util.ArrayList<>(one.attachListeners))listener.onViewDetachedFromWindow(one);
        check(root.getClipChildren()&&first.observer.listeners.isEmpty(),"detach cleans up even without animator end callback");
        for(int mode=0;mode<4;mode++) {
            config(mode!=0,mode!=1);VolumeAnimUtil.complex=mode!=2;owner.mExpanded=mode==3;
            p=enter(owner,one,120);after(VIEW+"doShowOrHideAnimate",p);
            check(p.args[1].equals(700L)&&root.getClipChildren()&&one.attachListeners.isEmpty(),"native path retained for disabled, reduced motion or expanded state "+mode);
        }
        config(true,true);VolumeAnimUtil.complex=true;owner.mExpanded=false;
        p=enter(owner,one,120);after(VIEW+"doShowOrHideAnimate",p);VolumeLiquidEntrance.refresh();
        check(root.getClipChildren()&&first.observer.listeners.isEmpty(),"live setting changes clean up existing effect");
        View accessory=owner.mOplusVolumeDialogView.mVerticalRowIconParent;
        accessory.setPivotX(31);accessory.setPivotY(0);
        p=enter(owner,accessory,120);after(VIEW+"doShowOrHideAnimate",p);
        check(accessory.getPivotX()==31&&accessory.getPivotY()==0,"button retains its native top pivot rather than stretching upwards into the bar");
        accessory.setTranslationX(14);accessory.setTranslationY(7);accessory.setScaleX(.8f);accessory.setScaleY(1.1f);
        VolumeLiquidEntrance.finishDialog(owner.mOplusVolumeDialogView);
        check(accessory.getTranslationX()==14&&accessory.getTranslationY()==7&&accessory.getPivotY()==0,"interruption cleanup cannot introduce a position or pivot jump");
        System.out.println("PASS: "+checks+" liquid entrance lifecycle/material checks (synthetic runtime, not GPU rendering)");
    }
}
