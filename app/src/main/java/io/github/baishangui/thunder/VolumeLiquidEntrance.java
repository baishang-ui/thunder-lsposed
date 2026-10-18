package io.github.baishangui.thunder;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** Side-key entrance only. Both halves of the tether use the firmware's metaball renderer. */
final class VolumeLiquidEntrance {
    private static final String BASE="com.oplus.systemui.volume.", BLUR="com.oplusos.systemui.common.blurability.";
    private static final String META="com.oplus.posteffect.drawable.MetaBallBlurDrawable";
    private static final long DURATION_MS=780L;
    private static final float RELEASE_START=.85f,NECK_RANGE=1.6f;
    private static final String PARAMS="com.oplus.posteffect.params.MetaBallParamsKt";
    private static final Map<View,Entrance> active=new IdentityHashMap<>();
    private static final Map<ViewGroup,ClipLease> clips=new IdentityHashMap<>();
    private static final Map<Drawable,MaterialState> materialShaders=new WeakHashMap<>();
    private static final ThreadLocal<Entrance> starting=new ThreadLocal<>();
    private static Object spring;

    static void install(ClassLoader loader) {
        hook(loader,PARAMS,"buildMetaBallUniformArrays",new HookCallback() {
            @Override protected void afterHookedMethod(Param p) {
                if(p.args[1]==null||p.hasThrowable()||p.getResult()==null)return;
                Object ref=Reflect.getAdditionalInstanceField(p.args[1],"volume.liquidEntrance");
                Entrance entrance=ref instanceof WeakReference<?>?(Entrance)((WeakReference<?>)ref).get():null;
                if(entrance==null||entrance.closed||entrance.detached)return;
                // Firmware precomputed pair 1/2: [11,14] are the two displacement
                // amplitudes, [17] is smooth-union strength. Decay these independently
                // of reach and support width so the bar's protrusion actually retracts.
                float[] pair=(float[])Reflect.getObjectField(p.getResult(),"preCmpArray12");
                if(pair.length<21)return;
                float gain=entrance.deformationGain;
                pair[11]*=gain;pair[14]*=gain;pair[17]*=gain;
            }
        });
        Api102Runtime.deoptimizeCallers(Reflect.findClass(PARAMS,loader),"setMetaBallUniform");
        Api102Runtime.deoptimizeCallers(Reflect.findClass("com.oplus.posteffect.agsl.DrawableShader",loader),"setMetaBallParams","setSize");
        hook(loader,BASE+"view.OplusVolumeDialogView","doShowOrHideAnimate",new HookCallback() {
            @Override protected void beforeHookedMethod(Param p) throws Throwable {
                starting.remove();
                View target=(View)p.args[0];
                finish(target);
                HookConfig c=ShadeHook.snapshot();
                if(!c.enabled||!c.volumeLiquidEntrance||!Boolean.TRUE.equals(p.args[4])
                        ||Math.abs(((Number)p.args[3]).floatValue())<1f
                        ||Reflect.getBooleanField(Reflect.getObjectField(p.thisObject,"volumeInteractor"),"mExpanded"))return;
                // Honor the system animation scale and its power-saving animation policy.
                Class<?> util=Reflect.findClass(BASE+"utils.VolumeAnimUtil",loader);
                if(!Boolean.TRUE.equals(util.getDeclaredMethod("enableComplexAnim",android.content.Context.class).invoke(null,target.getContext())))return;
                View bar=findBar(target);
                boolean accessory=target==Reflect.getObjectField(p.thisObject,"mVerticalRowIconParent");
                if(bar==null&&!accessory)return;
                if(spring==null)spring=Reflect.newInstance(Reflect.findClass("com.coui.appcompat.animation.COUISpringInterpolator",loader),.82d,.58d);
                Entrance entrance=new Entrance(p.thisObject,target,bar,((Number)p.args[3]).floatValue());
                active.put(target,entrance);starting.set(entrance);
                try {if(bar!=null) {
                    Object ref=Reflect.getAdditionalInstanceField(bar,"volume.proxy");
                    Object proxy=ref instanceof WeakReference<?>?((WeakReference<?>)ref).get():null;
                    if(proxy!=null) {
                        Object helper=Reflect.callMethod(proxy,"getPlatformBlurHelper$vendor__oplus__packages__apps__systemui__oplus_common__android_common__OplusCommon");
                        Reflect.callMethod(helper,"ensureBlurDrawable");
                        Object wrapper=Reflect.getObjectField(helper,"blurDrawable");
                        if(wrapper!=null)entrance.prepare(wrapper);
                    }
                }} catch(Throwable error) {entrance.close();starting.remove();throw error;}
                p.args[1]=DURATION_MS;
                Runnable end=(Runnable)p.args[7];
                p.args[7]=(Runnable)()->{try {if(end!=null)end.run();}finally {entrance.close();}};
            }
            @Override protected void afterHookedMethod(Param p) throws Throwable {
                Entrance entrance=starting.get();starting.remove();
                if(p.hasThrowable()&&entrance!=null)entrance.close();
            }
        });
        hook(loader,BASE+"utils.VolumeAnimUtil","startViewPropertyAnimation",new HookCallback() {
            @Override protected void beforeHookedMethod(Param p) {
                Entrance entrance=starting.get();if(entrance==null||p.args[0]!=entrance.target)return;
                // Keep both end lobes intact. Native relative-position blending shapes
                // only the neck, as in NotificationMenuRowMetaBallController.
                entrance.target.setScaleX(1f);entrance.target.setScaleY(1f);
                entrance.startAtEdge();
                entrance.target.setAlpha(1f);p.args[3]=spring;
            }
        });
        hook(loader,BLUR+"platformblur.PlatformBlurHelper","ensureBlurDrawable",new HookCallback() {
            @Override protected void beforeHookedMethod(Param p) {
                View host=(View)Reflect.getObjectField(p.thisObject,"hostView");
                HookConfig c=ShadeHook.snapshot();
                if(host==null||!host.getClass().getName().equals(BASE+"OplusVolumeSeekBar")
                        ||(!activeBar(host)&&(!c.enabled||!c.volumeLiquidEntrance)))return;
                Object config=Reflect.getObjectField(p.thisObject,"blurConfig");
                // The progress fill has a separate blur helper with the SAME host View.
                // Only the background proxy is marked by VolumeHook.material(). Tagging the
                // fill too makes its later draw look like a replaced background and detach.
                if(!"volume-bar".equals(Reflect.getAdditionalInstanceField(config,"thunder.role")))return;
                p.setObjectExtra("liquid.config",config);
                p.setObjectExtra("liquid.support",Reflect.getObjectField(config,"isSupportMetaBall"));
                Reflect.callMethod(config,"setSupportMetaBall",true);
                Object wrapper=Reflect.getObjectField(p.thisObject,"blurDrawable");
                if(wrapper!=null&&!Reflect.getObjectField(wrapper,"blurDrawable").getClass().getName().equals(META)) {
                    // Same cache reset as the vendor's setBlurEnabled(false), followed by its
                    // own factory/callback to bind capture, material, optics and lifecycle.
                    Reflect.callMethod(Reflect.getObjectField(wrapper,"blurDrawable"),"removeBlurDrawable",false);
                    Reflect.setObjectField(p.thisObject,"blurDrawable",null);
                }
            }
            @Override protected void afterHookedMethod(Param p) throws Throwable {
                Object config=p.getObjectExtra("liquid.config");if(config==null)return;
                Reflect.setObjectField(config,"isSupportMetaBall",p.getObjectExtra("liquid.support"));
                if(p.hasThrowable())return;
                Object wrapper=Reflect.getObjectField(p.thisObject,"blurDrawable");
                if(wrapper!=null) {
                    View host=(View)Reflect.getObjectField(p.thisObject,"hostView");
                    Reflect.setAdditionalInstanceField(wrapper,"volume.liquidHost",new WeakReference<>(host));
                    retainMaterialShader(Reflect.getObjectField(wrapper,"blurDrawable"));
                    Entrance entrance=forBar(host);if(entrance!=null)entrance.prepare(wrapper);
                }
            }
        });
        hook(loader,BLUR+"platformblur.PlatformBlurDrawable","draw",new HookCallback() {
            @Override protected void beforeHookedMethod(Param p) {
                Object ref=Reflect.getAdditionalInstanceField(p.thisObject,"volume.liquidHost");
                View host=ref instanceof WeakReference<?>?(View)((WeakReference<?>)ref).get():null;
                Entrance entrance=forBar(host);if(entrance==null)return;
                try {entrance.drawEdge(p.thisObject,(Canvas)p.args[0]);}
                catch(Throwable error) {entrance.close();ShadeHook.frameworkFailure("volume-liquid-material",error);}
            }
        });
        hook(loader,BASE+"utils.material.OplusVolumeBackgroundWrapper","draw",new HookCallback() {
            @Override protected void beforeHookedMethod(Param p) throws Throwable {
                for(Entrance entrance:active.values())if(entrance.bar!=null&&!entrance.closed
                        &&entrance.bar.getBackground()==p.thisObject) {
                    entrance.drawBackground(p.thisObject,(Canvas)p.args[0]);
                    p.setResult(null);return;
                }
            }
        });
        Api102Runtime.deoptimizeCallers(Reflect.findClass(BASE+"view.OplusVolumeDialogView",loader),"doShowOrHideAnimate");
        Api102Runtime.deoptimizeCallers(Reflect.findClass(BLUR+"drawable.AutoBlurDrawable",loader),"draw");
        for(String caller:new String[]{"$startShowOrHideAnimWithOnlyRow$2$1","$showOrHideAnimation$1$3"})
            Api102Runtime.deoptimizeCallers(Reflect.findClass(BASE+"view.OplusVolumeDialogView"+caller,loader),"run");
    }

    private static void hook(ClassLoader loader,String type,String name,HookCallback callback) {
        Class<?> cls=Reflect.findClass(type,loader);boolean found=false;
        for(Method method:cls.getDeclaredMethods())if(method.getName().equals(name)) {
            Class<?>[] types=method.getParameterTypes();Object[] signature=new Object[types.length+1];
            System.arraycopy(types,0,signature,0,types.length);signature[types.length]=callback;
            Api102Runtime.findAndHookMethod(cls,name,signature);found=true;
        }
        if(!found)throw new IllegalStateException(type+"."+name);
    }
    private static View findBar(View target) {
        if(target.getClass().getName().equals(BASE+"OplusVolumeSeekBar"))return target;
        if(target instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++) {
            View found=findBar(group.getChildAt(i));if(found!=null)return found;
        }
        return null;
    }
    private static Entrance forBar(View bar) {
        if(bar!=null)for(Entrance entrance:active.values())if(entrance.bar==bar)return entrance;
        return null;
    }
    private static boolean activeBar(View bar) {return forBar(bar)!=null;}
    private static void finish(View target) {Entrance entrance=active.get(target);if(entrance!=null)entrance.close();}
    static void finishDialog(Object dialog) {
        for(Entrance entrance:new ArrayList<>(active.values()))if(entrance.dialog==dialog)entrance.close();
    }
    static void refresh() {
        for(Entrance entrance:new ArrayList<>(active.values()))entrance.close();
        HookConfig config=ShadeHook.snapshot();
        if(!config.enabled||!config.volumeLiquidEntrance) {
            for(Map.Entry<Drawable,MaterialState> entry:new ArrayList<>(materialShaders.entrySet())) {
                Reflect.callMethod(entry.getKey(),"setBlendRange",entry.getValue().range);
                Reflect.callMethod(entry.getKey(),"setEnableBlurShader",entry.getValue().shader);
            }
            materialShaders.clear();
        }
    }
    private static final class MaterialState {
        final boolean shader;final int range;
        MaterialState(Object backend) {
            shader=Boolean.TRUE.equals(Reflect.callMethod(backend,"getEnableShader"));
            range=Reflect.getIntField(backend,"blendRange");
        }
    }
    private static void retainMaterialShader(Object backend) {
        if(!backend.getClass().getName().equals(META))return;
        // Shader and bitmap paths apply native optics differently. Keep one path for
        // entrance AND rest; restoring at separation produces a visible color step.
        Drawable drawable=(Drawable)backend;
        if(!materialShaders.containsKey(drawable))materialShaders.put(drawable,new MaterialState(backend));
        Reflect.callMethod(backend,"setEnableBlurShader",true);
    }

    private static final class ClipLease {
        final boolean children,padding;int users;
        ClipLease(ViewGroup group) {children=group.getClipChildren();padding=group.getClipToPadding();}
    }
    /** Native property setters are inert; its updatePosition/updateSize each submit a shader.
     * Batch the native ball fields and sync once, without reflective argument arrays per frame. */
    private static final class BallFields {
        final Field x,y,width,height,corner,blend,neck;
        BallFields(Class<?> type) throws ReflectiveOperationException {
            x=field(type,"posX");y=field(type,"posY");width=field(type,"width");
            height=field(type,"height");corner=field(type,"corner");blend=field(type,"blendShape");neck=field(type,"shapeRangeFactorX");
        }
        private static Field field(Class<?> type,String name) throws ReflectiveOperationException {
            Field value=type.getDeclaredField(name);value.setAccessible(true);return value;
        }
        void shape(Object ball,float px,float w,float h,float radius,float strength) throws IllegalAccessException {
            x.setFloat(ball,px);y.setFloat(ball,0);width.setFloat(ball,w);height.setFloat(ball,h);
            corner.setFloat(ball,radius);blend.setFloat(ball,strength);
        }
    }
    private static final class Entrance implements View.OnAttachStateChangeListener {
        final Object dialog;final View target,bar;final boolean right;float initialX;
        final ArrayList<ViewGroup> parents=new ArrayList<>();
        final Matrix global=new Matrix(),inverse=new Matrix();
        final float[] edge=new float[4];
        final int[] rootPosition=new int[2];
        final Rect captureBounds=new Rect(0,0,0,0);
        final Rect visibleBounds=new Rect(0,0,0,0),contentBounds=new Rect(0,0,0,0);
        ViewTreeObserver observer;
        final ViewTreeObserver.OnPreDrawListener redraw;
        Object nativeBar,edgeBall,barBall,mainBall,peerMain,barUniforms,peerUniforms;Drawable peer;
        BallFields fields;
        int range;
        boolean closed,detached,recoilFinished;
        long releaseStarted=-1;
        final long releaseDuration;
        volatile float deformationGain=1;
        float motionUnit=1f,maxBlendStrength=Float.NaN,furthestPull;
        float originalNeck,originalBlend;
        Entrance(Object dialog,View target,View bar,float initialX) {
            this.dialog=dialog;this.target=target;this.bar=bar;right=initialX>0;this.initialX=initialX;
            releaseDuration=Math.max(1,Math.round(220*ValueAnimator.getDurationScale()));
            redraw=()->{if(this.bar!=null)this.bar.invalidate();return true;};
            // Native rows scale about their lower end; accessories scale about their top.
            // Keeping those pivots also keeps the gap open throughout vertical deformation.
            target.addOnAttachStateChangeListener(this);
            if(bar!=null)for(View v=bar;v!=null;v=v.getParent() instanceof View?(View)v.getParent():null) {
                if(v instanceof ViewGroup group) {
                    ClipLease lease=clips.computeIfAbsent(group,ClipLease::new);lease.users++;
                    group.setClipChildren(false);group.setClipToPadding(false);parents.add(group);
                }
            }
            if(bar!=null) {observer=bar.getViewTreeObserver();observer.addOnPreDrawListener(redraw);}
        }
        void startAtEdge() {
            if(bar==null)return;
            Object material=Reflect.getObjectField(bar,"mMaterialHost");
            Reflect.callMethod(material,"resolveVisualShapeInto",visibleBounds);
            if(visibleBounds.isEmpty())return;
            global.reset();bar.transformMatrixToGlobal(global);
            if(!global.invert(inverse))return;
            edge[0]=visibleBounds.left;edge[1]=visibleBounds.top;
            edge[2]=visibleBounds.right;edge[3]=visibleBounds.top;
            global.mapPoints(edge);
            View root=bar.getRootView();root.getLocationOnScreen(rootPosition);
            float boundary=rootPosition[0]+(right?root.getWidth():0);
            float sliver=Math.abs(edge[2]-edge[0])*.18f;
            float inner=right?Math.min(edge[0],edge[2]):Math.max(edge[0],edge[2]);
            float delta=boundary+(right?-sliver:sliver)-inner;
            // Translation is expressed in the parent's coordinates, not the scaled bar's.
            global.reset();
            if(target.getParent() instanceof View parent)parent.transformMatrixToGlobal(global);
            edge[0]=edge[1]=edge[3]=0;edge[2]=1;global.mapPoints(edge);
            float unit=edge[2]-edge[0];if(Math.abs(unit)<.001f)return;
            inverse.mapPoints(edge);motionUnit=Math.abs(edge[2]-edge[0]);
            target.setTranslationX(target.getTranslationX()+delta/unit);
            initialX=target.getTranslationX();
        }
        void drawBackground(Object wrapper,Canvas canvas) throws Throwable {
            // View.drawBackground may restore the padded view rectangle. The native wrapper
            // normally clips it down to the actual seekbar geometry. Keep that geometry while
            // allowing the metaball tether to extend horizontally beyond the solitary bar.
            Object material=Reflect.getObjectField(bar,"mMaterialHost");
            Reflect.callMethod(material,"resolveVisualShapeInto",visibleBounds);
            Drawable content=(Drawable)Reflect.getObjectField(wrapper,"content");
            Rect bounds=content.getBounds();
            contentBounds.left=bounds.left;contentBounds.top=bounds.top;contentBounds.right=bounds.right;contentBounds.bottom=bounds.bottom;
            if(!visibleBounds.isEmpty())content.setBounds(visibleBounds);
            try {content.draw(canvas);}
            finally {content.setBounds(contentBounds);}
        }
        void prepare(Object wrapper) throws Throwable {
            if(closed||detached||nativeBar!=null)return;
            Object backend=Reflect.getObjectField(wrapper,"blurDrawable");
            if(!backend.getClass().getName().equals(META))return;
            nativeBar=backend;
            // A MetaBallBlurDrawable can still use the ordinary bitmap path. The system's
            // notification controller explicitly enables its shader on BOTH materials.
            retainMaterialShader(nativeBar);
            range=Math.round(96*bar.getResources().getDisplayMetrics().density);
            Reflect.callMethod(nativeBar,"setBlendMode",0);Reflect.callMethod(nativeBar,"setBlendRange",range);
            // Normally prepared before the animator starts, rather than allocating a second
            // blur capture on its first visible frame. Both captures retain stable bounds.
            peer=(Drawable)nativeBar.getClass().getDeclaredMethod("copy",android.content.Context.class).invoke(nativeBar,bar.getContext());
            Reflect.callMethod(peer,"setEnableBlurShader",true);
            Reflect.callMethod(peer,"clearMetaBalls");Reflect.callMethod(peer,"setMainBallIndex",2);
            barUniforms=Reflect.callMethod(Reflect.callMethod(nativeBar,"getDrawableShader"),"getMetaBallParams");
            peerUniforms=Reflect.callMethod(Reflect.callMethod(peer,"getDrawableShader"),"getMetaBallParams");
            WeakReference<Entrance> ref=new WeakReference<>(this);
            Reflect.setAdditionalInstanceField(barUniforms,"volume.liquidEntrance",ref);
            Reflect.setAdditionalInstanceField(peerUniforms,"volume.liquidEntrance",ref);
        }
        void drawEdge(Object wrapper,Canvas canvas) throws Throwable {
            if(closed||detached)return;
            if(recoilFinished) {detach();return;}
            Object backend=Reflect.getObjectField(wrapper,"blurDrawable");
            if(!backend.getClass().getName().equals(META))return;
            Rect bounds=((Drawable)wrapper).getBounds();if(bounds.isEmpty())return;
            if(nativeBar!=null&&nativeBar!=backend)detach();
            if(detached)return;
            prepare(wrapper);
            if(nativeBar==null)return;
            // PlatformBlurDrawable normally forwards these bounds inside draw(), after this
            // before-hook. Prime the native main ball before updating either half on frame 1.
            ((Drawable)nativeBar).setBounds(bounds);
            float density=bar.getResources().getDisplayMetrics().density;
            // Invert all ancestor transforms, including the elastic entrance scale. The edge
            // stays attached to the physical display instead of drifting with the row.
            global.reset();bar.transformMatrixToGlobal(global);if(!global.invert(inverse))return;
            View root=bar.getRootView();root.getLocationOnScreen(rootPosition);
            edge[0]=rootPosition[0]+(right?root.getWidth():0);edge[1]=rootPosition[1];
            edge[2]=edge[0]+1;edge[3]=edge[1];
            inverse.mapPoints(edge);
            // The anchor stays off-screen. Only the fused deformation is visible.
            if(right?bounds.left>=edge[0]:bounds.right<=edge[0])return;
            float screenUnit=density*Math.abs(edge[2]-edge[0]);
            furthestPull=Math.max(furthestPull,1-(right?1:-1)*target.getTranslationX()/Math.max(.001f,Math.abs(initialX)));
            if(furthestPull>=RELEASE_START&&releaseStarted<0)releaseStarted=SystemClock.uptimeMillis();
            float release=releaseStarted<0?0:Math.min(1,(SystemClock.uptimeMillis()-releaseStarted)/(float)releaseDuration);
            deformationGain=Math.max(0,1-release*release*release*(release*(release*6-15)+10));
            // A full-height peer produces two parallel pillars and a broad membrane.
            // Size the off-screen droplet from the bar's width so the native deformation
            // stays around its middle, independent of the configured bar length.
            float edgeWidth=Math.min(72*screenUnit,bounds.width()*1.35f);
            float edgeHeight=Math.min(bounds.height(),bounds.width()*2.3f);
            float edgeCorner=Math.min(edgeWidth,edgeHeight)/2f;
            float edgeX=edge[0]+(right?1:-1)*edgeWidth/2f;
            float centerX=bounds.exactCenterX();
            float corner=Math.min(bounds.width()/2f,((Number)Reflect.getObjectField(bar,"mCurBackgroundRadius")).floatValue());
            // Keep a visible bridge as the fast spring pulls out. Control the native
            // distance-to-release, rather than collapsing the transverse range to
            // zero: that leaves a long needle before the pair suddenly disappears.
            float gap=Math.abs(edgeX-centerX)-(bounds.width()+edgeWidth)/2f;
            float connectedReach=Math.min(bounds.width()*.85f,48*screenUnit);
            if(Float.isNaN(maxBlendStrength)) {
                float restGap=Math.max(0,gap+(right?1:-1)*target.getTranslationX()*motionUnit);
                maxBlendStrength=(restGap+Math.abs(initialX)*motionUnit*.35f+connectedReach)/2f;
            }
            // blendShape also controls whether the firmware considers the pair in
            // range. Keep that support alive while the uniform hook independently
            // returns both protrusions to their original contours over 220ms.
            float strength=Math.min(maxBlendStrength,Math.max(0,(gap+connectedReach)/2f));
            if(edgeBall==null) {
                edgeBall=Reflect.callMethod(nativeBar,"addMetaBall",2,
                        new PointF(edgeX-centerX,0),new PointF(edgeWidth,edgeHeight),edgeCorner,1.1f,strength,NECK_RANGE,2f);
                fields=new BallFields(edgeBall.getClass());
                mainBall=Reflect.getObjectField(nativeBar,"mainBall");
                originalNeck=fields.neck.getFloat(mainBall);
                originalBlend=fields.blend.getFloat(mainBall);
            }
            // The peer uses the same coordinate system and fixed capture rectangle as the
            // bar. Move its main ball inside that rectangle instead of resizing the capture.
            Rect pb=captureBounds;
            if(pb.left!=bounds.left||pb.top!=bounds.top||pb.right!=bounds.right||pb.bottom!=bounds.bottom) {
                peer.setBounds(bounds.left,bounds.top,bounds.right,bounds.bottom);
                pb.left=bounds.left;pb.top=bounds.top;pb.right=bounds.right;pb.bottom=bounds.bottom;
            }
            mainBall=Reflect.getObjectField(nativeBar,"mainBall");
            peerMain=Reflect.getObjectField(peer,"mainBall");
            if(barBall==null)barBall=Reflect.callMethod(peer,"addMetaBall",1,
                    new PointF(0,0),new PointF(bounds.width(),bounds.height()),corner,1.1f,strength,NECK_RANGE,2f);
            fields.shape(edgeBall,edgeX-centerX,edgeWidth,edgeHeight,edgeCorner,strength);
            fields.blend.setFloat(mainBall,strength);
            fields.shape(peerMain,edgeX-centerX,edgeWidth,edgeHeight,edgeCorner,strength);
            fields.shape(barBall,0,bounds.width(),bounds.height(),corner,strength);
            fields.neck.setFloat(mainBall,NECK_RANGE);fields.neck.setFloat(edgeBall,NECK_RANGE);
            fields.neck.setFloat(peerMain,NECK_RANGE);fields.neck.setFloat(barBall,NECK_RANGE);
            Reflect.callMethod(nativeBar,"syncToShader");Reflect.callMethod(peer,"syncToShader");
            // Draw an undeformed frame before removing the pair on the next draw.
            recoilFinished=release>=1;
            // The native shader centers its current/main ball in the drawable, subtracting
            // that ball's position from every uniform. Moving peerMain alone cannot move
            // the rendered lobe: translate its draw to the physical edge as well.
            int save=canvas.save();
            try {canvas.translate(edgeX-centerX,0);peer.draw(canvas);}
            finally {canvas.restoreToCount(save);}
        }
        void detach() {
            if(detached)return;detached=true;
            if(barUniforms!=null)Reflect.removeAdditionalInstanceField(barUniforms,"volume.liquidEntrance");
            if(peerUniforms!=null)Reflect.removeAdditionalInstanceField(peerUniforms,"volume.liquidEntrance");
            barUniforms=null;peerUniforms=null;
            if(observer!=null&&observer.isAlive())observer.removeOnPreDrawListener(redraw);
            observer=null;
            if(nativeBar!=null) {
                if(fields!=null&&mainBall!=null)try {
                    fields.neck.setFloat(mainBall,originalNeck);
                    fields.blend.setFloat(mainBall,originalBlend);
                } catch(IllegalAccessException error) {ShadeHook.frameworkFailure("volume-liquid-restore",error);}
                if(edgeBall!=null)Reflect.callMethod(nativeBar,"removeMetaBall",edgeBall);
                // Preserve capture padding and native metaball color processing at rest.
                // Resetting the range reconfigures both in one frame and changes luminance.
            }
            if(peer!=null) {Reflect.callMethod(peer,"removeBlurDrawable",false);peer.setCallback(null);}
            peer=null;edgeBall=null;barBall=null;nativeBar=null;mainBall=null;peerMain=null;fields=null;
        }
        void close() {
            if(closed)return;closed=true;
            try {detach();}finally {
                if(active.get(target)==this)active.remove(target);
                target.removeOnAttachStateChangeListener(this);
                for(ViewGroup group:parents) {
                    ClipLease lease=clips.get(group);
                    if(lease!=null&&--lease.users==0) {group.setClipChildren(lease.children);group.setClipToPadding(lease.padding);clips.remove(group);}
                }
                if(bar!=null)bar.invalidate();
            }
        }
        @Override public void onViewAttachedToWindow(View view) {}
        @Override public void onViewDetachedFromWindow(View view) {close();}
    }
}
