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
        DRAW="com.oplusos.systemui.common.blurability.platformblur.PlatformBlurDrawable#draw",
        ANIMATE="com.oplus.systemui.volume.utils.VolumeAnimUtil#startViewPropertyAnimation";
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
        config(true,true);
        OplusVolumeDialogImpl owner=new OplusVolumeDialogImpl();
        ViewGroup root=owner.mOplusVolumeDialogView.mDialogView;root.actualWidth=1080;root.screenX=root.screenY=0;
        ViewGroup target=new ViewGroup();target.actualWidth=66;target.actualHeight=260;
        target.setPivotX(33);target.setPivotY(260);root.addView(target);
        OplusVolumeSeekBar bar=new OplusVolumeSeekBar();target.addView(bar);
        target.setTranslationX(right?120:-120);bar.screenX=right?1060:-36;
        int beforeEnds=ended;
        HookCallback.Param enter=enter(owner,target,right?120:-120);
        check(target.getPivotX()==33&&target.getPivotY()==260,"entrance preserves native bar/button pivots and their vertical gap");
        long duration=(long)enter.args[1];
        check(duration==520&&!root.getClipChildren(),"entrance captures clipping and the shorter native duration");
        Object nativeInterpolator=new Object();
        HookCallback.Param utility=before(ANIMATE,null,target,0L,duration,nativeInterpolator,0f,0f,1f,1f,null,null,null,13);
        utility.setResult(target.animator);after(ANIMATE,utility);
        check(target.getScaleX()==.66f&&target.getScaleY()==.48f&&target.getAlpha()==1,"bar starts as a narrower, shorter capsule");
        check(utility.args[4].equals(target.getTranslationX())&&((int)utility.args[11]&4)==0&&utility.args[3]==nativeInterpolator,
                "the host animator keeps its timing policy and delegates pose to its own frame callback");
        float screenEdge=root.screenX+(right?root.actualWidth:0);
        // This fixture stores child screen coordinates independently. Apply the parent's
        // translation delta to the child as Android's view transform would do.
        float shift=target.getTranslationX()-(right?120:-120);bar.screenX+=Math.round(shift);
        int firstBarX=bar.screenX;
        float restingX=bar.screenX-target.getTranslationX();
        float barInside=right?screenEdge-bar.screenX:bar.screenX+56-screenEdge;
        check(Math.abs(barInside-56*.22f)<1f,"first animated frame already intersects the screen edge instead of traversing hidden space");
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
        check(!tetherCanvas.translations.isEmpty()&&Math.abs(tetherCanvas.translations.get(0)[0]-backend.balls.get(0).posX)<.01f
                &&Math.abs(tetherCanvas.translations.get(0)[1]-backend.balls.get(0).posY)<.01f,"reciprocal shader is drawn at its own main ball center, not the bar center");
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
        check(initialHeight>=56*2&&initialHeight<=56*2.05f&&edge.posY==0,"edge contact is centered on the compact bar");
        View accessory=owner.mOplusVolumeDialogView.mVerticalRowIconParent;
        accessory.setTranslationX(right?120:-120);
        HookCallback.Param accessoryEnter=enter(owner,accessory,right?120:-120);
        HookCallback.Param buttons=before(ANIMATE,null,accessory,0L,duration,nativeInterpolator,0f,0f,1f,1f,null,null,null,13);
        buttons.setResult(accessory.animator);after(ANIMATE,buttons);
        after(VIEW+"doShowOrHideAnimate",accessoryEnter);
        check(accessory.getScaleX()==.66f&&accessory.getScaleY()==.66f&&accessory.getTranslationX()==target.getTranslationX(),
                "native buttons start smaller and share the bar's edge correction");
        float origin=target.getTranslationX();
        target.animator.frame(Math.round(.20f*duration),duration);
        accessory.animator.frame(Math.round(.20f*duration),duration);
        check(Math.abs(target.getTranslationX()/origin-.655f)<.01f&&target.getScaleY()<.60f&&accessory.getTranslationX()==target.getTranslationX(),
                "the bar and buttons pull together while the bar remains visibly short");
        bar.screenX=Math.round(restingX+target.getTranslationX());
        before(DRAW,helper.blurDrawable,new Canvas());
        check(!backend.balls.isEmpty()&&edge.height==initialHeight&&edge.posY==0,"liquid connection stays centered before release");
        target.animator.frame(Math.round(.39f*duration),duration);bar.screenX=Math.round(restingX+target.getTranslationX());
        check(Math.abs(target.getTranslationX()/origin-.25f)<.015f,"the initial pull flows straight into release without a held middle phase");
        before(DRAW,helper.blurDrawable,new Canvas());
        check(edge.height==initialHeight&&peerSize(backend)==edge.height,"both materials agree on the centered contact");
        check(nativeInfluence(edge,56)>.99f,"native pair stays in range while entering release");
        float fixedStrength=edge.blendShape;
        int range=backend.range;
        int submissions=backend.submissions;
        int rangeChanges=backend.rangeChanges;
        MetaBallBlurDrawable peer=backend.lastCopy;
        float restingNeck=edge.shapeRangeFactorX;
        check(restingNeck<1.2f&&restingNeck>.80f,"the centered contact narrows while the native pair stays attached");
        int peerBounds=peer.boundsChanges,peerSubmissions=peer.submissions;
        before(DRAW,helper.blurDrawable,new Canvas());
        check(right?edge.posX>0:edge.posX<0,"anchor mirrors with physical volume-key side");
        check(backend.range==range&&backend.rangeChanges==rangeChanges,"moving tether keeps capture bounds stable");
        check(backend.submissions==submissions+1,"all source ball changes submit to the native shader once per frame");
        check(peer.boundsChanges==peerBounds&&peer.submissions==peerSubmissions+1,"edge material also keeps capture bounds stable and submits once");
        check(edge.height==peerSize(backend)&&edge.blendShape==fixedStrength,"redrawing the same frame keeps the native reach stable");
        int invalidated=bar.invalidations;bar.observer.dispatchOnPreDraw();
        check(bar.invalidations>invalidated,"pre-draw rerecords material during render-node movement");
        var initialUniforms=uniforms(backend);
        check(initialUniforms.preCmpArray12[11]==40&&initialUniforms.preCmpArray12[14]==40,"both native protrusions start fully deformed");
        float previousDisplacement=40;
        for(float phase:new float[]{.15f,.3f,.5f,.7f,.85f,1f}) {
            target.animator.frame(Math.round((.39f+.32f*phase)*duration),duration);
            bar.screenX=Math.round(restingX+target.getTranslationX());
            before(DRAW,helper.blurDrawable,new Canvas());
            var barUniforms=uniforms(backend);
            var edgeUniforms=uniforms(peer);
            float displacement=barUniforms.preCmpArray12[11];
            check(!peer.removed&&!backend.balls.isEmpty(),"material survives until the bar-side protrusion fully retracts");
            check(displacement<previousDisplacement&&displacement>=0,"actual bar-side shader displacement decreases over multiple frames");
            check(displacement==edgeUniforms.preCmpArray12[11]&&displacement==barUniforms.preCmpArray12[14],"both reciprocal shaders detach the same centered contour");
            check(edge.height==peerSize(backend)&&edge.posY==0,"release cannot pull the connection toward either end of the bar");
            check(edge.shapeRangeFactorX<=restingNeck&&nativeInfluence(edge,56)>.99f,"native support stays alive while the neck narrows");
            check(barUniforms.preCmpArray12[9]==7&&barUniforms.preCmpArray12[10]==7&&barUniforms.ball1Array[3]==120,"uniform hook preserves support dimensions and bar geometry");
            check(Math.abs(barUniforms.preCmpArray12[17]-12*barUniforms.preCmpArray12[14]/40)<.001f,"smooth union follows the retreating edge contact");
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
        target.animator.frame(Math.round(.85f*duration),duration);before(DRAW,helper.blurDrawable,new Canvas());
        check(backend.balls.isEmpty()&&target.getTranslationX()/origin>=0&&target.getTranslationX()/origin<.01f,
                "the bar settles without overshooting or reattaching to the edge");
        target.animator.frame(duration,duration);accessory.animator.frame(duration,duration);
        check(target.getScaleX()==1&&target.getScaleY()==1&&target.getTranslationX()==0&&accessory.getScaleX()==1&&accessory.getScaleY()==1,
                "native frame completion settles the bar and buttons at the fully configured size");
        ((Runnable)enter.args[7]).run();
        ((Runnable)accessoryEnter.args[7]).run();
        check(backend.getEnableShader()&&target.animator.update==null&&accessory.animator.update==null,
                "completion leaves the settled material active and removes the entrance frame listeners");
        config(true,false);VolumeLiquidEntrance.refresh();
        check(!backend.getEnableShader()&&backend.range==0,"disabling liquid entrance restores the original material path and capture range");
        config(true,true);
        check(ended==beforeEnds+2,"both native end actions execute exactly once");
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
        check(!root.getClipChildren(),"unrelated live setting changes preserve an in-flight entrance");
        ((Runnable)p.args[7]).run();
        check(root.getClipChildren()&&first.observer.listeners.isEmpty(),"preserved entrance releases its clip lease at completion");
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
