package io.github.baishangui.thunder;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.PathInterpolator;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** C17 capsule/card scenes share one bounds clock; the host still owns windows and lifecycle. */
final class CloudMorphHook {
    private static final String BASE="com.oplus.systemui.plugins.seedling.";
    private static final String MORPH="thunder.cloud-morph";
    private static final Map<View,Motion> active=new IdentityHashMap<>();
    private static final Map<View,Pending> interrupted=new WeakHashMap<>();
    private static final Map<ViewGroup,ClipLease> clips=new IdentityHashMap<>();
    private static final PathInterpolator CURVE=new PathInterpolator(.32f,.72f,0f,1f);
    private record Frame(float x,float y,float width,float height,float radius,float progress) { }
    private record Pending(Frame frame,long time) { }
    private record Spec(View capsule,View container,List<View> cards,boolean opening,ClassLoader loader) { }

    static void install(ClassLoader loader) {
        // Normal cards, interrupted close, and the large/small instant-card scenes.
        for(String name:new String[]{"t","Q","P","v","w","I0","k0"}) {
            boolean normal=name.equals("t")||name.equals("Q")||name.equals("P");
            boolean opening=name.equals("t")||name.equals("v")||name.equals("w");
            bind("scene-"+name,() -> Api102Runtime.findAndHookMethod(BASE+"animatorscenes."+name,loader,"c",
                BASE+"statemachine.event.b",BASE+"statemachine.fluid.Q",new HookCallback() {
                    @Override protected void afterHookedMethod(Param p) {
                        if(p.hasThrowable()||p.getResult()==null||!enabled())return;
                        Spec spec=resolve(p.thisObject,normal,opening,loader);
                        if(spec!=null)Reflect.setAdditionalInstanceField(p.getResult(),MORPH,spec);
                    }
                }));
        }
        bind("clock",() -> Api102Runtime.findAndHookMethod(BASE+"engine.animatorengine.h",loader,"g",
            BASE+"engine.animatorengine.o",new HookCallback() {
                @Override protected void afterHookedMethod(Param p) {
                    Object value=Reflect.getAdditionalInstanceField(p.thisObject,MORPH);
                    if(p.hasThrowable()||!(value instanceof Spec spec)||!(p.getResult() instanceof Animator original)||!enabled())return;
                    Motion motion=new Motion(spec,original);
                    if(!motion.prepare())return;
                    p.setResult(motion.animator);
                    ShadeHook.resourceHit("cloud-continuous-morph");
                }
            }));
        bind("clock-deopt",() -> Api102Runtime.deoptimizeCallers(Reflect.findClass(BASE+"engine.animatorengine.h",loader),"s","g"));
    }
    private static void bind(String key,Runnable action) {
        try {action.run();ShadeHook.hookRegistered("cloud-morph-"+key);}
        catch(Throwable error){ShadeHook.frameworkFailure("cloud-morph-"+key,error);}
    }
    private static boolean enabled() {
        HookConfig c=ShadeHook.snapshot();return c.enabled&&c.cloud&&ValueAnimator.getDurationScale()>0f;
    }
    private static Spec resolve(Object scene,boolean normal,boolean opening,ClassLoader loader) {
        Object window=Reflect.getObjectField(scene,normal?"j":"k");
        Object root=Reflect.getObjectField(window,"g");
        ArrayList<View> cards=new ArrayList<>();
        if(normal) {
            Object container=root==null?null:Reflect.callMethod(root,"getCardContainer");
            if(container!=null)for(Object card:(List<?>)Reflect.callMethod(container,"getCardViews"))if(card instanceof View v)cards.add(v);
        } else {
            Object secondary=Reflect.getObjectField(window,"h");
            Object card=opening?(root==null?null:Reflect.callMethod(root,"getInstantContainer")):
                secondary==null?null:Reflect.getObjectField(secondary,"r");
            if(card instanceof View v)cards.add(v);
        }
        if(cards.isEmpty())return null;
        Context context=(Context)Reflect.getObjectField(scene,normal?"l":"i");
        try {
            Class<?> manager=Reflect.findClass(BASE+"capsule.i",loader);
            Object companion=manager.getDeclaredField("e").get(null);
            Object host=Reflect.getObjectField(Reflect.callMethod(companion,"u",context),"c");
            Object container=host==null?null:Reflect.callMethod(host,"l");
            Object capsule=container==null?null:Reflect.callMethod(container,"f",Reflect.getObjectField(container,"l"));
            return capsule instanceof View v&&container instanceof View parent?new Spec(v,parent,cards,opening,loader):null;
        } catch(ReflectiveOperationException error){throw new IllegalStateException("C17 capsule unavailable",error);}
    }
    static boolean ownsOutline(ViewOutlineProvider provider) {
        return provider!=null&&Boolean.TRUE.equals(Reflect.getAdditionalInstanceField(provider,MORPH));
    }
    private static float mix(float a,float b,float p){return a+(b-a)*p;}
    private static Frame mix(Frame a,Frame b,float p) {
        return new Frame(mix(a.x,b.x,p),mix(a.y,b.y,p),mix(a.width,b.width,p),mix(a.height,b.height,p),
            mix(a.radius,b.radius,p),mix(a.progress,b.progress,p));
    }
    private static float[] location(View view) {
        int[] xy=new int[2];view.getLocationOnScreen(xy);
        return new float[]{xy[0]-view.getTranslationX()-view.getPivotX()*(1f-view.getScaleX()),
            xy[1]-view.getTranslationY()-view.getPivotY()*(1f-view.getScaleY())};
    }
    private static final class ClipLease {
        final boolean children,padding;int owners;
        ClipLease(ViewGroup group){children=group.getClipChildren();padding=group.getClipToPadding();}
    }
    private static final class Surface {
        final View view,content;
        final ViewOutlineProvider outline;
        final boolean clip;
        final float contentAlpha;
        final Object background;
        final ViewOutlineProvider backgroundOutline;
        final boolean backgroundClip;
        final Object backgroundProvider;
        final Rect backgroundRect=new Rect();
        final Object material;
        final ArrayList<Runnable> restoreMaterial=new ArrayList<>();
        final Rect backgroundBounds;
        final float x,y;
        final Frame capsule,expanded,start;
        final Object provider;
        final Rect bounds=new Rect();
        final ArrayList<ViewGroup> parents=new ArrayList<>();
        Frame current;
        Surface(View view,Spec spec,Frame capsule)throws ReflectiveOperationException {
            this.view=view;this.capsule=capsule;
            outline=view.getOutlineProvider();clip=view.getClipToOutline();
            boolean instant=view.getClass().getName().endsWith("InstantContainer");
            content=(View)Reflect.callMethod(view,instant?"getInstantView":"getContentView");
            contentAlpha=content==null?1f:content.getAlpha();
            background=instant?null:Reflect.callMethod(view,"getBgView");
            backgroundOutline=background instanceof View bg?bg.getOutlineProvider():null;
            backgroundClip=background instanceof View bg&&bg.getClipToOutline();
            material=instant?Reflect.getObjectField(view,"p"):background==null?null:Reflect.getObjectField(background,"c");
            CloudHook.remember(view.getBackground(),restoreMaterial);
            if(background instanceof View bg)CloudHook.remember(bg.getBackground(),restoreMaterial);
            backgroundBounds=background==null?null:(Rect)Reflect.callMethod(background,"getBounds");
            float[] xy=location(view);x=xy[0];y=xy[1];
            Object corners=Reflect.findClass(BASE+"util.c",spec.loader).getDeclaredMethod("f").invoke(null);
            float radius=((Number)Reflect.callMethod(corners,"e")).floatValue();
            expanded=new Frame(x,y,view.getWidth(),view.getHeight(),Math.min(radius,Math.min(view.getWidth(),view.getHeight())/2f),1f);
            Pending pending=interrupted.remove(view);
            start=pending!=null&&SystemClock.uptimeMillis()-pending.time<=300L?pending.frame:spec.opening?capsule:expanded;
            provider=Reflect.newInstance(Reflect.findClass("com.oplus.systemui.plugins.common.util.j",spec.loader),bounds,corners);
            Reflect.setAdditionalInstanceField(provider,MORPH,true);
            backgroundProvider=background instanceof View?Reflect.newInstance(provider.getClass(),backgroundRect,corners):null;
            if(backgroundProvider!=null)Reflect.setAdditionalInstanceField(backgroundProvider,MORPH,true);
        }
        void acquire() {
            view.setScaleX(1f);view.setScaleY(1f);
            view.setOutlineProvider((ViewOutlineProvider)provider);view.setClipToOutline(true);
            if(background instanceof View bg){bg.setOutlineProvider((ViewOutlineProvider)backgroundProvider);bg.setClipToOutline(true);}
            // The window has already been laid out; release parent clipping for the trip to the status bar.
            Object parent=view.getParent();
            for(int depth=0;parent instanceof ViewGroup group&&depth<32;depth++,parent=group.getParent()) {
                ClipLease lease=clips.computeIfAbsent(group,ClipLease::new);lease.owners++;
                group.setClipChildren(false);group.setClipToPadding(false);parents.add(group);
            }
        }
        void frame(Frame frame,boolean primary) {
            current=frame;
            float left=(view.getWidth()-frame.width)/2f,top=(view.getHeight()-frame.height)/2f;
            bounds.left=Math.round(left);bounds.top=Math.round(top);
            bounds.right=Math.round(left+frame.width);bounds.bottom=Math.round(top+frame.height);
            view.setTranslationX(frame.x-x-left);view.setTranslationY(frame.y-y-top);
            Object corners=Reflect.getObjectField(provider,"b");
            Reflect.setObjectField(provider,"b",Reflect.newInstance(corners.getClass(),2,frame.radius,CloudCornerGeometry.WEIGHT));
            if(backgroundProvider!=null) {
                backgroundRect.left=0;backgroundRect.top=0;backgroundRect.right=bounds.width();backgroundRect.bottom=bounds.height();
                Reflect.setObjectField(backgroundProvider,"b",Reflect.getObjectField(provider,"b"));
                ((View)background).invalidateOutline();
            }
            view.setAlpha(primary?1f:frame.progress);
            if(content!=null&&content!=view)content.setAlpha(frame.progress);
            if(view.getForeground()!=null)view.getForeground().setAlpha(0);
            if(background!=null)Reflect.callMethod(view,"setLeftTopRightBottomForBlur",bounds);
            if(material!=null)Reflect.callMethod(material,"setCornerRadius",frame.radius);
            CloudHook.round(view.getBackground(),frame.radius);
            if(background instanceof View bg)CloudHook.round(bg.getBackground(),frame.radius);
            view.invalidateOutline();view.invalidate();
        }
        void restore(boolean opening,boolean cancelled) {
            if(cancelled&&current!=null)interrupted.put(view,new Pending(current,SystemClock.uptimeMillis()));else interrupted.remove(view);
            view.setTranslationX(0f);view.setTranslationY(0f);view.setScaleX(1f);view.setScaleY(1f);
            view.setOutlineProvider(outline);view.setClipToOutline(clip);
            if(background instanceof View bg){bg.setOutlineProvider(backgroundOutline);bg.setClipToOutline(backgroundClip);bg.invalidateOutline();}
            view.setAlpha(opening?1f:0f);
            if(content!=null&&content!=view)content.setAlpha(contentAlpha);
            // H.k / z.k install an opaque entrance mask before the scene is built.
            // Its property animator normally fades to zero; it is no longer played.
            if(view.getForeground()!=null)view.getForeground().setAlpha(0);
            for(Runnable restore:restoreMaterial)restore.run();
            if(material!=null)Reflect.callMethod(material,"setCornerRadius",expanded.radius);
            if(background!=null) {
                Reflect.callMethod(background,"setBounds",backgroundBounds);
                Reflect.callMethod(view,"setLeftTopRightBottomForBlur",new Rect(0,0,view.getWidth(),view.getHeight()));
                Reflect.callMethod(background,"setBounds",backgroundBounds);
            }
            view.invalidateOutline();view.invalidate();
        }
        void releaseClips() {
            for(ViewGroup group:parents) {
                ClipLease lease=clips.get(group);
                if(lease!=null&&--lease.owners==0){group.setClipChildren(lease.children);group.setClipToPadding(lease.padding);clips.remove(group);}
            }
            parents.clear();
        }
    }
    private record NativeListener(Animator animator,Animator.AnimatorListener listener) { }
    private static void dispatch(NativeListener callback,int phase) {
        try {
            if(phase==0)callback.listener.onAnimationStart(callback.animator);
            else if(phase==1)callback.listener.onAnimationCancel(callback.animator);
            else callback.listener.onAnimationEnd(callback.animator);
        } catch(Throwable error){ShadeHook.frameworkFailure("cloud-morph-lifecycle",error);}
    }
    private static void listeners(Animator animator,List<NativeListener> result,boolean reverse) {
        ArrayList<Animator.AnimatorListener> own=animator.getListeners();
        if(!reverse&&own!=null)for(var listener:own)result.add(new NativeListener(animator,listener));
        if(animator instanceof AnimatorSet set)for(Animator child:set.getChildAnimations())listeners(child,result,reverse);
        if(reverse&&own!=null)for(var listener:own)result.add(new NativeListener(animator,listener));
    }
    private static final class Motion extends AnimatorListenerAdapter {
        final Spec spec;
        final Animator original;
        final ValueAnimator animator=ValueAnimator.ofFloat(0f,1f);
        final ArrayList<Surface> surfaces=new ArrayList<>();
        boolean closed,cancelled;
        Motion(Spec spec,Animator original){this.spec=spec;this.original=original;}
        boolean prepare() {
            if(!spec.capsule.isAttachedToWindow()||spec.capsule.getHeight()<=0)return false;
            int width=((Number)Reflect.callMethod(spec.capsule,"getCapsuleDrawableWidth")).intValue();
            if(width<=0)return false;
            float[] xy=location(spec.capsule);
            Frame capsule=new Frame(xy[0]+(spec.capsule.getWidth()-width)/2f,xy[1],width,spec.capsule.getHeight(),spec.capsule.getHeight()/2f,0f);
            for(View card:spec.cards)if(!card.isAttachedToWindow()||card.getWidth()<=0||card.getHeight()<=0)return false;
            try {for(View card:spec.cards) {
                Motion previous=active.get(card);if(previous!=null&&previous!=this)previous.animator.cancel();
                surfaces.add(new Surface(card,spec,capsule));
            }}
            catch(ReflectiveOperationException error){throw new IllegalStateException("C17 morph geometry unavailable",error);}
            animator.setDuration(300L);animator.setInterpolator(CURVE);animator.addListener(this);
            animator.addUpdateListener(a -> {
                if(closed)return;
                if(!enabled()||!spec.capsule.isAttachedToWindow()){animator.cancel();return;}
                try {
                    float p=((Number)a.getAnimatedValue()).floatValue();
                    for(int i=0;i<surfaces.size();i++) {
                        Surface surface=surfaces.get(i);
                        if(!surface.view.isAttachedToWindow()){animator.cancel();return;}
                        surface.frame(mix(surface.start,spec.opening?surface.expanded:surface.capsule,p),i==0);
                    }
                } catch(Throwable error){ShadeHook.frameworkFailure("cloud-morph-frame",error);animator.cancel();}
            });
            return true;
        }
        @Override public void onAnimationStart(Animator ignored) {
            ArrayList<NativeListener> callbacks=new ArrayList<>();listeners(original,callbacks,false);
            for(NativeListener callback:callbacks)dispatch(callback,0);
            for(Surface surface:surfaces) {
                Motion previous=active.get(surface.view);if(previous!=null&&previous!=this)previous.animator.cancel();
                active.put(surface.view,this);surface.acquire();
            }
            spec.capsule.setAlpha(0f);
            spec.container.setAlpha(0f); // Includes the native edge/moon siblings of CapsuleView.
        }
        @Override public void onAnimationCancel(Animator ignored) {
            cancelled=true;
            ArrayList<NativeListener> callbacks=new ArrayList<>();listeners(original,callbacks,false);
            for(NativeListener callback:callbacks)dispatch(callback,1);
        }
        @Override public void onAnimationEnd(Animator ignored) {
            if(closed)return;closed=true;
            try {
                // The host completion listeners update event state, remove windows and notify the status bar.
                // Complete their lifecycle once; cancelled native monitors remove themselves in onAnimationCancel.
                ArrayList<NativeListener> callbacks=new ArrayList<>();listeners(original,callbacks,true);
                for(NativeListener callback:callbacks)dispatch(callback,2);
            } finally {
                for(Surface surface:surfaces) {
                    active.remove(surface.view);
                    try {surface.restore(spec.opening,cancelled);}
                    catch(Throwable error){ShadeHook.frameworkFailure("cloud-morph-restore",error);}
                    finally {surface.releaseClips();}
                }
                // Native end listeners own GONE/VISIBLE. Restore alpha for later native scenes,
                // including a close that starts after this feature has been disabled.
                spec.capsule.setAlpha(1f);spec.container.setAlpha(1f);
            }
        }
    }
    static void refresh() {
        if(enabled())return;
        for(Motion motion:new ArrayList<>(active.values()))motion.animator.cancel();
        interrupted.clear();
    }
}
