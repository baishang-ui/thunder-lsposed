package io.github.baishangui.thunder;

import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
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
    private static final long DURATION_MS=520L;
    private static final float RELEASE_START=.76f,RELEASE_END=.99f;
    private static final float START_WIDTH=.66f,START_LENGTH=.48f;
    private static final String PARAMS="com.oplus.posteffect.params.MetaBallParamsKt";
    private static final Map<View,Entrance> active=new IdentityHashMap<>();
    private static final Map<ViewGroup,ClipLease> clips=new IdentityHashMap<>();
    private static final Map<Drawable,MaterialState> materialShaders=new WeakHashMap<>();
    private static final ThreadLocal<Entrance> starting=new ThreadLocal<>();
    private static final String PLACEHOLDER_CLIP="volume.placeholder-clip";

    record Pose(View target,float x,float scaleX,float scaleY,float alpha) {
        Pose(View target) {this(target,target.getTranslationX(),target.getScaleX(),target.getScaleY(),target.getAlpha());}
    }

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
        hook(loader,BASE+"utils.VolumeAnimUtil","resetShowOrHideAnimateView",new HookCallback() {
            @Override protected void beforeHookedMethod(Param p) {
                if(active.containsKey(p.args[0]))p.setResult(null);
            }
        });
        hook(loader,BASE+"view.OplusVolumeDialogView","flushRowAndIconParent",new HookCallback() {
            @Override protected void beforeHookedMethod(Param p) {finishDialog(p.thisObject);}
        });
        hook(loader,BASE+"view.OplusVolumeDialogView","doShowOrHideAnimate",new HookCallback() {
            @Override protected void beforeHookedMethod(Param p) throws Throwable {
                starting.remove();
                View target=(View)p.args[0];
                Entrance previous=active.get(target);
                Pose interrupted=previous==null?null:new Pose(target);
                if(previous!=null)previous.handoff();
                HookConfig c=ShadeHook.snapshot();
                boolean showing=Boolean.TRUE.equals(p.args[4]);
                if(!c.enabled||!c.volumeLiquidEntrance||(!showing&&interrupted==null)
                        ||Math.abs(((Number)p.args[3]).floatValue())<1f
                        ||Reflect.getBooleanField(Reflect.getObjectField(p.thisObject,"volumeInteractor"),"mExpanded"))return;
                // Honor the system animation scale and its power-saving animation policy.
                Class<?> util=Reflect.findClass(BASE+"utils.VolumeAnimUtil",loader);
                if(!Boolean.TRUE.equals(util.getDeclaredMethod("enableComplexAnim",android.content.Context.class).invoke(null,target.getContext())))return;
                View bar=findBar(target);
                boolean accessory=target==Reflect.getObjectField(p.thisObject,"mVerticalRowIconParent");
                if(bar==null&&!accessory)return;
                Entrance entrance=new Entrance(p.thisObject,target,bar,((Number)p.args[3]).floatValue(),(long)p.args[2],showing,interrupted);
                active.put(target,entrance);starting.set(entrance);
                try {if(entrance.tether) {
                    Object ref=Reflect.getAdditionalInstanceField(bar,"volume.proxy");
                    Object proxy=ref instanceof WeakReference<?>?((WeakReference<?>)ref).get():null;
                    if(proxy!=null) {
                        Object helper=Reflect.callMethod(proxy,"getPlatformBlurHelper$vendor__oplus__packages__apps__systemui__oplus_common__android_common__OplusCommon");
                        Reflect.callMethod(helper,"ensureBlurDrawable");
                        Object wrapper=Reflect.getObjectField(helper,"blurDrawable");
                        if(wrapper!=null)entrance.prepare(wrapper);
                    }
                }} catch(Throwable error) {entrance.close();starting.remove();throw error;}
                if(showing)p.args[1]=interrupted==null?DURATION_MS:Math.round(220+180*(1-interrupted.scaleY()));
                Runnable end=(Runnable)p.args[7];
                p.args[7]=(Runnable)()->{
                    if(entrance.closed)return;
                    try {if(entrance.animator!=null)entrance.applyPose(1f);entrance.close();}
                    finally {if(end!=null)end.run();}
                };
            }
            @Override protected void afterHookedMethod(Param p) throws Throwable {
                Entrance entrance=starting.get();starting.remove();
                if(p.hasThrowable()&&entrance!=null)entrance.close();
            }
        });
        hook(loader,BASE+"utils.VolumeAnimUtil","startViewPropertyAnimation",new HookCallback() {
            @Override protected void beforeHookedMethod(Param p) {
                Entrance entrance=starting.get();if(entrance==null||p.args[0]!=entrance.target)return;
                entrance.startMotion();
                entrance.endX=((Number)p.args[4]).floatValue();
                p.args[4]=entrance.initialX;
                p.args[11]=((int)p.args[11])&~(4|8);
            }
            @Override protected void afterHookedMethod(Param p) {
                Entrance entrance=starting.get();if(entrance==null||p.args[0]!=entrance.target||p.hasThrowable())return;
                if(p.getResult() instanceof ViewPropertyAnimator animator) {
                    entrance.animator=animator;
                    animator.setUpdateListener(frame->entrance.applyPose(frame.getCurrentPlayTime()/(float)frame.getDuration()));
                }
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
                    retainMaterialShader(Reflect.getObjectField(wrapper,"blurDrawable"),host);
                    Entrance entrance=forBar(host);if(entrance!=null)entrance.prepare(wrapper);
                }
            }
        });
        hook(loader,BLUR+"platformblur.PlatformBlurDrawable","draw",new HookCallback() {
            @Override protected void beforeHookedMethod(Param p) {
                Object ref=Reflect.getAdditionalInstanceField(p.thisObject,"volume.liquidHost");
                View host=ref instanceof WeakReference<?>?(View)((WeakReference<?>)ref).get():null;
                if(host==null)return;
                Canvas canvas=(Canvas)p.args[0];
                Entrance entrance=forBar(host);
                if(entrance!=null)try {entrance.drawEdge(p.thisObject,canvas);}
                catch(Throwable error) {entrance.detach();ShadeHook.frameworkFailure("volume-liquid-material",error);}
                Object backend=Reflect.getObjectField(p.thisObject,"blurDrawable");
                if(!backend.getClass().getName().equals(META)||Reflect.getBooleanField(backend,"hasReceivedFirstUpdate")
                        ||(Integer)Reflect.callMethod(backend,"getPlaceHolderColor")==0)return;
                Rect bounds=((Drawable)p.thisObject).getBounds();if(bounds.isEmpty())return;
                Path clip=(Path)Reflect.getAdditionalInstanceField(p.thisObject,PLACEHOLDER_CLIP);
                if(clip==null) {
                    clip=new Path();
                    Reflect.setAdditionalInstanceField(p.thisObject,PLACEHOLDER_CLIP,clip);
                }
                float radius=((Number)Reflect.getObjectField(host,"mCurBackgroundRadius")).floatValue();
                VolumeHook.panelClip(clip,bounds,Math.min(radius,Math.min(bounds.width(),bounds.height())/2f));
                p.setObjectExtra(PLACEHOLDER_CLIP,canvas.save());
                canvas.clipPath(clip);
            }
            @Override protected void afterHookedMethod(Param p) {
                Object save=p.getObjectExtra(PLACEHOLDER_CLIP);
                if(save instanceof Integer)((Canvas)p.args[0]).restoreToCount((Integer)save);
            }
        });
        hook(loader,BASE+"utils.material.OplusVolumeBackgroundWrapper","draw",new HookCallback() {
            @Override protected void beforeHookedMethod(Param p) throws Throwable {
                for(Entrance entrance:active.values())if(entrance.tether&&!entrance.closed
                        &&entrance.bar.getBackground()==p.thisObject) {
                    if(Reflect.getObjectField(p.thisObject,"handoffStatic")!=null)continue;
                    entrance.drawBackground(p.thisObject,(Canvas)p.args[0]);
                    p.setResult(null);return;
                }
            }
        });
        Api102Runtime.deoptimizeCallers(Reflect.findClass(BASE+"view.OplusVolumeDialogView",loader),"doShowOrHideAnimate");
        Api102Runtime.deoptimizeCallers(Reflect.findClass(BASE+"view.OplusVolumeDialogView",loader),
            "showOrHideAnimation","startShowOrHideAnimWithOnlyRow","resetSeekBarAndResetAnimateState","flushRowAndIconParent");
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
    private static float smooth(float from,float to,float progress) {
        float time=VolumePanelGeometry.clamp((progress-from)/(to-from));
        return time*time*(3-2*time);
    }
    private static float travel(float time) {
        float phase=VolumePanelGeometry.clamp(time),remaining=1-phase;
        float remainingSquared=remaining*remaining;
        return 1-remainingSquared*remainingSquared*remaining*(1+5*phase);
    }
    static Pose takeBarPose(View bar) {
        Entrance entrance=forBar(bar);
        if(entrance==null)return null;
        Pose pose=new Pose(entrance.target);
        entrance.handoff();
        return pose;
    }
    static void finishDialog(Object dialog) {
        for(Entrance entrance:new ArrayList<>(active.values()))if(entrance.dialog==dialog)entrance.handoff();
    }
    static ArrayList<Pose> takeDialogPoses(Object dialog) {
        ArrayList<Pose> result=null;
        if(active.isEmpty())return null;
        for(Entrance entrance:new ArrayList<>(active.values()))if(entrance.dialog==dialog) {
            if(result==null)result=new ArrayList<>();
            result.add(new Pose(entrance.target));
            entrance.handoff();
        }
        return result;
    }
    static void settleExpand(ArrayList<Pose> poses) {
        if(poses==null)return;
        for(Pose pose:poses) {
            View target=pose.target();
            if(!target.isAttachedToWindow()||(pose.scaleX()==1f&&pose.scaleY()==1f))continue;
            target.setScaleX(pose.scaleX());target.setScaleY(pose.scaleY());
            ViewPropertyAnimator settle=target.animate();
            Reflect.callMethod(settle,"scaleX",1f);
            Reflect.callMethod(settle,"scaleY",1f);
            Reflect.callMethod(settle,"setStartDelay",0L);
            Reflect.callMethod(settle,"setDuration",180L);
            Reflect.callMethod(settle,"start");
        }
    }
    static void refresh() {
        HookConfig config=ShadeHook.snapshot();
        if(!config.enabled||!config.volumeLiquidEntrance) {
            for(Entrance entrance:new ArrayList<>(active.values()))entrance.detach();
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
    private static void retainMaterialShader(Object backend,View host) {
        if(!backend.getClass().getName().equals(META))return;
        // Shader and bitmap paths apply native optics differently. Keep one path for
        // entrance AND rest; restoring at separation produces a visible color step.
        Drawable drawable=(Drawable)backend;
        if(materialShaders.containsKey(drawable))return;
        materialShaders.put(drawable,new MaterialState(backend));
        Reflect.callMethod(backend,"setBlendRange",Math.round(96*host.getResources().getDisplayMetrics().density));
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
        void shape(Object ball,float px,float w,float h,float radius,float strength,float support) throws IllegalAccessException {
            x.setFloat(ball,px);y.setFloat(ball,0f);width.setFloat(ball,w);height.setFloat(ball,h);
            corner.setFloat(ball,radius);blend.setFloat(ball,strength);neck.setFloat(ball,support);
        }
    }
    private static final class Entrance implements View.OnAttachStateChangeListener {
        final Object dialog;final View target,bar;final boolean right;final long delay;
        final boolean showing,tether;
        final Pose interrupted;
        float startScaleX,startScaleY,startAlpha;
        float initialX,endX,edgeCorrection,pull;
        boolean edgePositioned;
        ViewPropertyAnimator animator;
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
        boolean closed,detached,recoilFinished;
        volatile float deformationGain=1;
        float originalNeck,originalBlend;
        Entrance(Object dialog,View target,View bar,float initialX,long delay,boolean showing,Pose interrupted) {
            this.dialog=dialog;this.target=target;this.bar=bar;right=initialX>0;this.initialX=initialX;this.delay=delay;
            this.showing=showing;this.interrupted=interrupted;tether=bar!=null&&showing&&interrupted==null;
            redraw=()->{if(this.bar!=null)this.bar.invalidate();return true;};
            // Native rows scale about their lower end; accessories scale about their top.
            // Keeping those pivots also keeps the gap open throughout vertical deformation.
            target.addOnAttachStateChangeListener(this);
            if(tether)for(View v=bar;v!=null;v=v.getParent() instanceof View?(View)v.getParent():null) {
                if(v instanceof ViewGroup group) {
                    ClipLease lease=clips.computeIfAbsent(group,ClipLease::new);lease.users++;
                    group.setClipChildren(false);group.setClipToPadding(false);parents.add(group);
                }
            }
            if(tether) {observer=bar.getViewTreeObserver();observer.addOnPreDrawListener(redraw);}
        }
        void startMotion() {
            if(interrupted!=null) {
                initialX=interrupted.x();startScaleX=interrupted.scaleX();startScaleY=interrupted.scaleY();startAlpha=interrupted.alpha();
                target.setTranslationX(initialX);target.setScaleX(startScaleX);target.setScaleY(startScaleY);target.setAlpha(startAlpha);
                return;
            }
            startScaleX=START_WIDTH;startScaleY=bar==null?START_WIDTH:START_LENGTH;startAlpha=1f;
            target.setAlpha(startAlpha);
            target.setScaleX(START_WIDTH);
            target.setScaleY(bar==null?START_WIDTH:START_LENGTH);
            initialX=target.getTranslationX();
            if(bar==null) {
                for(Entrance entrance:active.values())if(entrance.dialog==dialog&&entrance.bar!=null&&entrance.right==right
                        &&entrance.delay==delay&&entrance.edgePositioned) {
                    target.setTranslationX(target.getTranslationX()+entrance.edgeCorrection);break;
                }
                initialX=target.getTranslationX();
                return;
            }
            Object material=Reflect.getObjectField(bar,"mMaterialHost");
            Reflect.callMethod(material,"resolveVisualShapeInto",visibleBounds);
            if(visibleBounds.isEmpty())return;
            global.reset();bar.transformMatrixToGlobal(global);
            edge[0]=visibleBounds.left;edge[1]=visibleBounds.top;
            edge[2]=visibleBounds.right;edge[3]=visibleBounds.top;
            global.mapPoints(edge);
            View root=bar.getRootView();root.getLocationOnScreen(rootPosition);
            float boundary=rootPosition[0]+(right?root.getWidth():0);
            float sliver=Math.abs(edge[2]-edge[0])*.22f;
            float inner=right?Math.min(edge[0],edge[2]):Math.max(edge[0],edge[2]);
            float delta=boundary+(right?-sliver:sliver)-inner;
            // Translation is expressed in the parent's coordinates, not the scaled bar's.
            global.reset();
            if(target.getParent() instanceof View parent)parent.transformMatrixToGlobal(global);
            edge[0]=edge[1]=edge[3]=0;edge[2]=1;global.mapPoints(edge);
            float unit=edge[2]-edge[0];if(Math.abs(unit)<.001f)return;
            edgeCorrection=delta/unit;
            target.setTranslationX(target.getTranslationX()+edgeCorrection);
            initialX=target.getTranslationX();
            edgePositioned=true;
        }
        void applyPose(float time) {
            if(closed)return;
            pull=interrupted==null?travel(time):smooth(0f,1f,time);
            target.setTranslationX(VolumePanelGeometry.mix(initialX,endX,pull));
            float growth=interrupted==null?smooth(.02f,.84f,time):pull;
            target.setScaleX(VolumePanelGeometry.mix(startScaleX,showing?1f:.5f,growth));
            target.setScaleY(VolumePanelGeometry.mix(startScaleY,showing?1f:.5f,growth));
            target.setAlpha(VolumePanelGeometry.mix(startAlpha,showing?1f:0f,pull));
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
            if(!tether||closed||detached||nativeBar!=null)return;
            Object backend=Reflect.getObjectField(wrapper,"blurDrawable");
            if(!backend.getClass().getName().equals(META))return;
            nativeBar=backend;
            // A MetaBallBlurDrawable can still use the ordinary bitmap path. The system's
            // notification controller explicitly enables its shader on BOTH materials.
            retainMaterialShader(nativeBar,bar);
            Reflect.callMethod(nativeBar,"setBlendMode",0);
            // Normally prepared before the animator starts, rather than allocating a second
            // blur capture on its first visible frame. Both captures retain stable bounds.
            peer=(Drawable)nativeBar.getClass().getDeclaredMethod("copy",android.content.Context.class).invoke(nativeBar,bar.getContext());
            Reflect.callMethod(peer,"setEnableBlurShader",true);
            Reflect.callMethod(peer,"setPlaceHolderColor",0);
            Reflect.callMethod(peer,"clearMetaBalls");Reflect.callMethod(peer,"setMainBallIndex",2);
            barUniforms=Reflect.callMethod(Reflect.callMethod(nativeBar,"getDrawableShader"),"getMetaBallParams");
            peerUniforms=Reflect.callMethod(Reflect.callMethod(peer,"getDrawableShader"),"getMetaBallParams");
            WeakReference<Entrance> ref=new WeakReference<>(this);
            Reflect.setAdditionalInstanceField(barUniforms,"volume.liquidEntrance",ref);
            Reflect.setAdditionalInstanceField(peerUniforms,"volume.liquidEntrance",ref);
        }
        void drawEdge(Object wrapper,Canvas canvas) throws Throwable {
            if(!tether||closed||detached)return;
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
            float tension=smooth(.36f,.94f,pull);
            deformationGain=1-smooth(RELEASE_START,RELEASE_END,pull);
            float width=bounds.width(),height=bounds.height();
            float support=VolumePanelGeometry.mix(1.55f,.80f,tension);
            float edgeWidth=Math.min(72*screenUnit,width*1.25f);
            float edgeHeight=Math.min(height,width*2.05f);
            float edgeCorner=Math.min(edgeWidth,edgeHeight)/2f;
            float edgeX=edge[0]+(right?1:-1)*edgeWidth/2f-bounds.exactCenterX();
            float corner=Math.min(width/2f,((Number)Reflect.getObjectField(bar,"mCurBackgroundRadius")).floatValue());
            float gap=Math.abs(edgeX)-(width+edgeWidth)/2f;
            float connectedReach=Math.min(width*.90f,44*screenUnit);
            float strength=Math.min(66*screenUnit,Math.max(width*.38f,(gap+connectedReach)/2f));
            if(edgeBall==null) {
                edgeBall=Reflect.callMethod(nativeBar,"addMetaBall",2,
                        new PointF(edgeX,0),new PointF(edgeWidth,edgeHeight),edgeCorner,1.1f,strength,support,2f);
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
                    new PointF(0,0),new PointF(width,height),corner,1.1f,strength,support,2f);
            fields.shape(edgeBall,edgeX,edgeWidth,edgeHeight,edgeCorner,strength,support);
            fields.shape(peerMain,edgeX,edgeWidth,edgeHeight,edgeCorner,strength,support);
            fields.shape(barBall,0,width,height,corner,strength,support);
            fields.blend.setFloat(mainBall,strength);fields.neck.setFloat(mainBall,support);
            Reflect.callMethod(nativeBar,"syncToShader");Reflect.callMethod(peer,"syncToShader");
            // Draw an undeformed frame before removing the pair on the next draw.
            recoilFinished=pull>=RELEASE_END;
            // The native shader centers its current/main ball in the drawable, subtracting
            // that ball's position from every uniform. Moving peerMain alone cannot move
            // the rendered lobe: translate its draw to the physical edge as well.
            int save=canvas.save();
            try {canvas.translate(edgeX,0);peer.draw(canvas);}
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
                if(animator!=null) {animator.setUpdateListener(null);animator=null;}
                target.removeOnAttachStateChangeListener(this);
                for(ViewGroup group:parents) {
                    ClipLease lease=clips.get(group);
                    if(lease!=null&&--lease.users==0) {group.setClipChildren(lease.children);group.setClipToPadding(lease.padding);clips.remove(group);}
                }
                if(bar!=null)bar.invalidate();
            }
        }
        void handoff() {
            ViewPropertyAnimator running=animator;
            close();
            if(running!=null)running.cancel();
        }
        @Override public void onViewAttachedToWindow(View view) {}
        @Override public void onViewDetachedFromWindow(View view) {close();}
    }
}
