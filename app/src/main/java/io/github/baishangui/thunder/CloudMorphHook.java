package io.github.baishangui.thunder;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;
import android.view.animation.PathInterpolator;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Native scenes supply business lifecycle; one independent persistent body owns the visual form. */
final class CloudMorphHook {
    private static final String BASE="com.oplus.systemui.plugins.seedling.";
    private static final String MORPH="thunder.cloud-morph";
    private static final Map<View,Motion> active=new IdentityHashMap<>();
    private static final Map<View,CloudMorphSurface> sessions=new IdentityHashMap<>();
    // w belongs to the persistent outer host window, including periods without a Body.
    private static final Map<View,Boolean> hostVisibility=new WeakHashMap<>();
    private static final Map<View,ValueAnimator> capsuleClocks=new IdentityHashMap<>();
    private static final Map<View,Frame> capsuleGoals=new WeakHashMap<>();
    private static final Map<View,Motion> containerMotions=new IdentityHashMap<>();
    private static final Map<View,Request> pendingEntries=new IdentityHashMap<>();
    private static final Map<View,Frame> velocities=new WeakHashMap<>();
    private static final Map<View,Frame> capsuleAnchors=new WeakHashMap<>();
    private static final Map<Motion,Boolean> running=new IdentityHashMap<>();
    private static final Map<Object,Boolean> windowTargets=new IdentityHashMap<>();
    private static final Map<Object,View> windowOwners=new IdentityHashMap<>();
    private static final Map<Object,Motion> windows=new IdentityHashMap<>();
    private static final Handler layoutHandler=new Handler(Looper.getMainLooper());
    private static final PathInterpolator LINEAR=new PathInterpolator(0f,0f,1f,1f);
    static record Frame(float x,float y,float width,float height,float radius,float progress) { }
    private enum Mode { NORMAL_OPEN,NORMAL_CLOSE,INSTANT_OPEN,BLANK_OPEN,INSTANT_CLOSE,CAPSULE_ENTER,CAPSULE_UPDATE,CAPSULE_HIDE }
    private record Request(Object builder,Mode mode,ClassLoader loader) { }
    private record Target(View view,View owner,boolean instant) { }
    private record Spec(View capsule,View container,List<Target> cards,boolean opening,ClassLoader loader) { }

    private static Mode mode(Object builder) {
        Object type=Reflect.callMethod(builder,"f");
        if(!(type instanceof Enum<?> scene))return null;
        return switch(scene.name()) {
            case "CAPSULE_TO_CARD" -> Mode.NORMAL_OPEN;
            case "CARD_TO_CAPSULE","CARD_TO_CAPSULE_BY_INTERRUPT" -> Mode.NORMAL_CLOSE;
            case "CAPSULE_TO_LARGE_INSTANT_CARD","CAPSULE_TO_SMALL_INSTANT_CARD" -> Mode.INSTANT_OPEN;
            case "CAPSULE_TO_SMALL_INSTANT_CARD_WITH_BLANK" -> Mode.BLANK_OPEN;
            case "LARGE_INSTANT_CARD_TO_CAPSULE","SMALL_INSTANT_CARD_TO_CAPSULE","SMALL_INSTANT_CARD_TO_CAPSULE_WITH_BLANK" -> Mode.INSTANT_CLOSE;
            case "APP_TO_CAPSULE","APP_TO_CAPSULE_WITH_NONE","CAPSULE_APPEAR" -> Mode.CAPSULE_ENTER;
            case "CAPSULE_WIDTH_CHANGE","CAPSULE_SEIZE","CAPSULE_BUBBLE_SWITCH","CAPSULE_UPDATE_DATA_WITHOUT_ANIM" -> Mode.CAPSULE_UPDATE;
            case "CAPSULE_DISAPPEAR" -> Mode.CAPSULE_HIDE;
            default -> null;
        };
    }

    static void install(ClassLoader loader) {
        for(String method:new String[]{"j","e"})bind("window-"+method,() -> {
            HookCallback callback=new HookCallback() {
                @Override protected void beforeHookedMethod(Param p) {
                    if(method.equals("j")&&Boolean.TRUE.equals(p.args[0]))return;
                    if(Boolean.TRUE.equals(windowTargets.get(p.thisObject))){p.setResult(null);ShadeHook.resourceHit("cloud-morph-stale-hide-dropped");return;}
                    Motion motion=windows.get(p.thisObject);
                    if(motion==null||motion.closed||!motion.started||motion.visualEnded||motion.spec.opening)return;
                    if(method.equals("j")&&Boolean.TRUE.equals(p.args[0]))return;
                    motion.deferred.put(method,p.args.clone());p.setResult(null);
                    ShadeHook.resourceHit("cloud-morph-window-handoff");
                }
            };
            if(method.equals("j"))Api102Runtime.findAndHookMethod(BASE+"card.window.i",loader,method,boolean.class,callback);
            else Api102Runtime.findAndHookMethod(BASE+"card.window.i",loader,method,callback);
        });
        bind("capsule-layout",() -> Api102Runtime.findAndHookMethod(BASE+"capsule.ui.view.CapsuleContainer",loader,"onLayout",
            boolean.class,int.class,int.class,int.class,int.class,new HookCallback(){
                @Override protected void afterHookedMethod(Param p){
                    if(p.hasThrowable()||!enabled())return;
                    try{syncCapsule((View)p.thisObject,loader);}
                    catch(Throwable error){ShadeHook.frameworkFailure("cloud-capsule-sync",error);}
                }
            }));
        bind("capsule-input",() -> Api102Runtime.findAndHookMethod(BASE+"capsule.ui.view.CapsuleContainerRoot",loader,"dispatchTouchEvent",
            android.view.MotionEvent.class,new HookCallback(){
                @Override protected void beforeHookedMethod(Param p){
                    if(!CloudMorphSurface.forwardingTouch()&&CloudMorphSurface.sourceRootOwnsInput((View)p.thisObject))p.setResult(false);
                }
            }));
        // Native press feedback is intentionally preserved. Morph starts after release.
        bind("capsule-anchor",() -> Api102Runtime.findAndHookMethod(BASE+"capsule.ui.view.CapsuleContainerRoot",loader,"onTouchEvent",
            android.view.MotionEvent.class,new HookCallback() {
                @Override protected void beforeHookedMethod(Param p) {
                    if(!enabled()){refresh();return;}
                    Object handler=Reflect.getObjectField(p.thisObject,"b");
                    if(handler==null)return;
                    Object container=Reflect.getObjectField(handler,"b");
                    if(container==null)return;
                    View capsule=capsuleSurface(container);
                    if(capsule!=null){captureCapsule(capsule);Frame frame=capsuleAnchors.get(capsule);if(frame!=null&&container instanceof View parent)capsuleAnchors.put(parent,frame);ShadeHook.resourceHit("cloud-morph-touch");}
                }
            }));
        // Card scenes, including the blank variants y / J0, pass through B0.
        // Keep its outer fraction-condition fragment: it advances the host's event state.
        bind("builder",() -> Api102Runtime.findAndHookMethod(BASE+"animatorscenes.B0",loader,"invoke",new HookCallback() {
            @Override protected void beforeHookedMethod(Param p) {
                if(!enabled()){refresh();return;}
                Object builder=Reflect.getObjectField(p.thisObject,"b");Mode mode=mode(builder);
                Object kind=Reflect.callMethod(builder,"f");
                if(kind instanceof Enum<?> scene&&(scene.name().contains("_TO_APP")||scene.name().equals("LOCKSCREEN_TO_CARD")||scene.name().equals("CARD_TO_CAPSULE_WITHOUT_ANIM"))) {
                    windowTargets.clear();windowOwners.clear();pendingEntries.clear();
                    for(Motion motion:new ArrayList<>(running.keySet()))motion.finish(true,true);
                    for(CloudMorphSurface shared:new ArrayList<>(sessions.values()))shared.dispose();
                }
                if(mode!=null){p.setObjectExtra(MORPH,new Request(builder,mode,loader));ShadeHook.resourceHit("cloud-morph-build");}
            }
            @Override protected void afterHookedMethod(Param p) {
                if(p.hasThrowable()||p.getResult()==null||!(p.getObjectExtra(MORPH) instanceof Request request))return;
                List<?> fragments=(List<?>)Reflect.getObjectField(p.getResult(),"o");
                if(fragments.isEmpty())return;
                Object scene=fragments.get(0);
                if(!Reflect.findClass(BASE+"engine.animatorengine.h",loader).isInstance(scene))return;
                // Observe the outer lifecycle; never replace or pause the host's animator.
                Reflect.setAdditionalInstanceField(p.getResult(),MORPH,request);
                ShadeHook.observed("cloud-morph-entry",((Enum<?>)Reflect.callMethod(request.builder,"f")).name());
            }
        }));
        bind("clock",() -> Api102Runtime.findAndHookMethod(BASE+"engine.animatorengine.h",loader,"g",
            BASE+"engine.animatorengine.o",new HookCallback() {
                @Override protected void afterHookedMethod(Param p) {
                    Object tagged=Reflect.getAdditionalInstanceField(p.thisObject,MORPH);
                    if(tagged instanceof Request)Reflect.removeAdditionalInstanceField(p.thisObject,MORPH);
                    if(!(tagged instanceof Request request))return;
                    if(p.hasThrowable()||!(p.getResult() instanceof Animator original)||!enabled()){releaseEntry(request.builder,true);return;}
                    Motion motion=new Motion(request,original);
                    original.addListener(motion);
                    ShadeHook.resourceHit("cloud-morph-native-lifecycle");
                }
            }));
        // Reserve entry before native binding/layout. Otherwise onLayout can finish
        // its own appearance before the real app-arrival graph is even started.
        for(String scene:new String[]{"e","f","h"})bind("prepare-entry-"+scene,()->
            Api102Runtime.findAndHookMethod(BASE+"animatorscenes."+scene,loader,"p",BASE+"statemachine.event.b",BASE+"statemachine.fluid.Q","kotlin.jvm.functions.Function0",new HookCallback(){
                @Override protected void beforeHookedMethod(Param p){
                    if(!enabled())return;
                    Mode entry=mode(p.thisObject);
                    if(entry!=Mode.CAPSULE_ENTER)return;
                    Request request=new Request(p.thisObject,entry,loader);
                    // p() binds the CapsuleView itself; reserve the persistent container
                    // even when its incoming content has not been created yet.
                    View container=resolveContainer(request);
                    if(container!=null){pendingEntries.put(container,request);stopCapsuleClock(container);}
                }
                @Override protected void afterHookedMethod(Param p){if(p.hasThrowable())releaseEntry(p.thisObject,true);}
            }));
        // Entry scenes e/f/h can return their graph directly and never execute B0.
        for(String scene:new String[]{"e","f","h"})bind("direct-entry-"+scene,()->
            Api102Runtime.findAndHookMethod(BASE+"animatorscenes."+scene,loader,"c",BASE+"statemachine.event.b",BASE+"statemachine.fluid.Q",new HookCallback(){
                @Override protected void afterHookedMethod(Param p){
                    if(p.hasThrowable()||p.getResult()==null||!enabled()){releaseEntry(p.thisObject,true);return;}
                    Mode entry=mode(p.thisObject);
                    if(entry!=null){Reflect.setAdditionalInstanceField(p.getResult(),MORPH,new Request(p.thisObject,entry,loader));ShadeHook.observed("cloud-morph-direct-entry",entry.name());}
                }
            }));
        bind("host-visibility",()->Api102Runtime.findAndHookMethod(BASE+"capsule.h",loader,"w",boolean.class,new HookCallback(){
            @Override protected void afterHookedMethod(Param p){
                // Remember the outer host while disabled too: enabling our own
                // animation does not make the native H visibility bit toggle.
                if(p.hasThrowable())return;
                Object view=Reflect.callMethod(p.thisObject,"l");
                if(view instanceof View container){
                    boolean shown=(Boolean)p.args[0];hostVisibility.put(container,shown);
                    CloudMorphSurface shared=sessions.get(container);if(shared!=null)shared.hostVisibility(shown);
                }
            }
        }));
        bind("capsule-visibility",()->Api102Runtime.findAndHookMethod(BASE+"capsule.h",loader,"y",boolean.class,boolean.class,new HookCallback(){
            @Override protected void afterHookedMethod(Param p){
                if(p.hasThrowable()||!enabled())return;
                Object view=Reflect.callMethod(p.thisObject,"l");
                if(view instanceof View container){
                    CloudMorphSurface shared=sessions.get(container);Motion motion=containerMotions.get(container);
                    boolean shown=(Boolean)p.args[0];
                    if(shared!=null&&(shown||motion==null&&shared.current()!=null&&shared.current().progress()==0))shared.businessVisibility(shown);
                }
            }
        }));
        bind("clock-deopt",() -> Api102Runtime.deoptimizeCallers(Reflect.findClass(BASE+"engine.animatorengine.h",loader),"s","g"));
        bind("builder-deopt",() -> Api102Runtime.deoptimizeCallers(Reflect.findClass(BASE+"animatorscenes.B0",loader),"invoke"));
    }
    private static void bind(String key,Runnable action) {
        try {action.run();ShadeHook.hookRegistered("cloud-morph-"+key);}
        catch(Throwable error){ShadeHook.frameworkFailure("cloud-morph-"+key,error);}
    }
    private static boolean enabled() {
        HookConfig c=ShadeHook.snapshot();return c.enabled&&c.scopedCloud("cloud")&&c.cloudAnimationEnabled&&ValueAnimator.getDurationScale()>0f;
    }
    private static boolean capsuleOnly(Mode mode){return mode==Mode.CAPSULE_ENTER||mode==Mode.CAPSULE_UPDATE||mode==Mode.CAPSULE_HIDE;}
    private static Target instantTarget(Object owner) {
        return owner instanceof View parent&&Reflect.callMethod(owner,"getInstantView") instanceof View view?new Target(view,parent,true):null;
    }
    static View capsuleSurface(Object container) {
        if(Boolean.TRUE.equals(Reflect.callMethod(container,"j"))) {
            Object mini=Reflect.callMethod(container,"getMiniView");
            if(mini instanceof View view&&view.getWidth()>0&&view.getHeight()>0)return view;
        }
        Object capsule=Reflect.callMethod(container,"f",Reflect.getObjectField(container,"l"));
        return capsule instanceof View view?view:null;
    }
    private static boolean drawableCapsule(View view) {
        for(Class<?> type=view.getClass();type!=null;type=type.getSuperclass())
            if(type.getName().equals(BASE+"capsule.ui.view.CapsuleView"))return true;
        return false;
    }
    private static boolean visible(Target target) {
        return target!=null&&target.owner.isShown()&&target.view.isShown()&&target.view.isAttachedToWindow()
            &&target.view.getWidth()>0&&target.view.getHeight()>0;
    }
    private static Spec resolve(Request request) {
        Object scene=request.builder;ClassLoader loader=request.loader;
        boolean normal=request.mode==Mode.NORMAL_OPEN||request.mode==Mode.NORMAL_CLOSE;
        boolean opening=request.mode==Mode.NORMAL_OPEN||request.mode==Mode.INSTANT_OPEN||request.mode==Mode.BLANK_OPEN;
        Object window=capsuleOnly(request.mode)?null:Reflect.getObjectField(scene,normal?"j":"k");
        Object root=window==null?null:Reflect.getObjectField(window,"g");
        ArrayList<Target> cards=new ArrayList<>();
        if(capsuleOnly(request.mode)) {
            // No card/window dependency for the first app-to-cloud entrance.
        } else if(normal) {
            Object container=root==null?null:Reflect.callMethod(root,"getCardContainer");
            if(container!=null)for(Object card:(List<?>)Reflect.callMethod(container,"getCardViews"))if(card instanceof View v)cards.add(new Target(v,v,false));
        } else {
            Object secondary=Reflect.getObjectField(window,"h");
            Target primary=instantTarget(root==null?null:Reflect.callMethod(root,"getInstantContainer"));
            Target floating=instantTarget(secondary==null?null:Reflect.getObjectField(secondary,"r"));
            Target preferred=request.mode==Mode.INSTANT_OPEN?primary:floating;
            Target alternate=request.mode==Mode.INSTANT_OPEN?floating:primary;
            // p() can mount the live card in h.r while g still holds an empty/hidden placeholder.
            Target card=!visible(preferred)&&visible(alternate)?alternate:preferred!=null?preferred:alternate;
            if(card!=null)cards.add(card);
        }
        View container=resolveContainer(request);
        Object capsule=container==null?null:capsuleSurface(container);
        ShadeHook.observed("cloud-morph-resolve","cards="+cards.size()+";container="+(container==null?"null":container.getClass().getSimpleName())+
            ";capsule="+(capsule==null?"null":capsule.getClass().getSimpleName()));
        if(capsule instanceof View v)return new Spec(v,container,cards,opening,loader);
        ShadeHook.observed("cloud-morph-skip","no active capsule: "+scene.getClass().getName());return null;
    }
    private static View resolveContainer(Request request) {
        Context context=(Context)Reflect.getObjectField(request.builder,"a");
        try {
            Class<?> manager=Reflect.findClass(BASE+"capsule.i",request.loader);
            Object companion=manager.getDeclaredField("e").get(null);
            Object host=Reflect.getObjectField(Reflect.callMethod(companion,"u",context),"c");
            return host!=null&&Reflect.callMethod(host,"l") instanceof View container?container:null;
        }catch(ReflectiveOperationException error){throw new IllegalStateException("C17 capsule unavailable",error);}
    }
    private static CloudMorphSurface newSession(View container,ClassLoader loader) {
        CloudMorphSurface body=new CloudMorphSurface(container,loader);
        Boolean shown=hostVisibility.get(container);
        if(shown!=null)body.hostVisibility(shown);
        return body;
    }
    private static void releaseEntry(Object builder,boolean resume) {
        for(View container:new ArrayList<>(pendingEntries.keySet())) {
            Request pending=pendingEntries.get(container);
            if(pending!=null&&pending.builder==builder){
                pendingEntries.remove(container);
                if(resume&&enabled())syncCapsule(container,pending.loader);
            }
        }
    }
    private static void stopCapsuleClock(View container) {
        ValueAnimator previous=capsuleClocks.remove(container);
        if(previous!=null)previous.cancel();
        capsuleGoals.remove(container);
    }
    private static Frame entrance(Frame goal){
        // The incoming capsule grows from the same retracted form used by its
        // disappearance. Native scene start supplies the handoff; a prelaid-out
        // 95%-sized endpoint makes app arrival look complete before it begins.
        return CloudVisibilityState.presentation(goal,0);
    }
    private static void syncCapsule(View container,ClassLoader loader) {
        if(!container.isAttachedToWindow()||!container.isShown())return;
        View capsule=capsuleSurface(container);
        if(capsule==null||capsule.getWidth()<=0||capsule.getHeight()<=0)return;
        CloudMorphSurface shared=sessions.get(container);
        if(shared!=null&&shared.disposed()){sessions.remove(container);shared=null;}
        if(shared!=null)shared.syncCapsule(capsule);
        captureCapsule(capsule);Frame goal=capsuleAnchors.get(capsule);
        if(goal==null)return;
        capsuleAnchors.put(container,goal);
        if(pendingEntries.containsKey(container)){
            // Claim rendering at the first bound layout, before the host's deferred
            // graph starts. Otherwise native alpha/scale can reveal the endpoint first.
            if(shared==null){
                shared=newSession(container,loader);sessions.put(container,shared);
                shared.bind(capsule,null,null,false);shared.frame(entrance(goal));shared.opacity(0);shared.show();
            }
            return;
        }
        Motion moving=containerMotions.get(container);
        if(moving!=null&&!moving.closed){moving.retargetCapsule(goal);return;}
        if(shared==null){
            shared=newSession(container,loader);sessions.put(container,shared);
            shared.bind(capsule,null,null,false);shared.frame(entrance(goal));shared.opacity(0);shared.show();
        }else if(shared.current()!=null&&shared.current().progress()!=0f)return;
        if(goal.equals(capsuleGoals.get(container))||goal.equals(shared.current()))return;
        stopCapsuleClock(container);
        HookConfig config=ShadeHook.snapshot();
        CloudMotion trajectory=new CloudMotion(shared.current()==null?entrance(goal):shared.current(),goal,velocities.get(container),config.cloudAnimationDuration,config.cloudAnimationCurve,config.cloudAnimationBounce);
        CloudMorphSurface body=shared;float initialOpacity=body.opacity();
        ValueAnimator clock=ValueAnimator.ofFloat(0,1);clock.setDuration(config.cloudAnimationDuration);clock.setInterpolator(LINEAR);
        capsuleGoals.put(container,goal);capsuleClocks.put(container,clock);
        clock.addUpdateListener(a->{
            if(body.disposed()||!enabled()){stopCapsuleClock(container);return;}
            float fraction=((Number)a.getAnimatedValue()).floatValue();body.frame(trajectory.frame(fraction));body.opacity(mix(initialOpacity,1,CloudCardTiming.opacity(fraction)));velocities.put(container,trajectory.velocity(fraction));
        });
        clock.addListener(new AnimatorListenerAdapter(){
            @Override public void onAnimationEnd(Animator animator){
                if(capsuleClocks.get(container)!=clock)return;
                capsuleClocks.remove(container);capsuleGoals.remove(container);
                if(!body.disposed()){body.frame(goal);body.settle(false);}
            }
        });
        clock.start();ShadeHook.resourceHit("cloud-capsule-continuous-update");
    }
    static boolean ownsOutline(ViewOutlineProvider provider) {
        return provider!=null&&Boolean.TRUE.equals(Reflect.getAdditionalInstanceField(provider,MORPH));
    }
    private static float mix(float a,float b,float p){return a+(b-a)*p;}
    private static Frame withProgress(Frame frame,float progress){return new Frame(frame.x,frame.y,frame.width,frame.height,frame.radius,progress);}
    private static Frame mix(Frame a,Frame b,float p) {
        float width=Math.max(1f,mix(a.width,b.width,p)),height=Math.max(1f,mix(a.height,b.height,p));
        return new Frame(mix(a.x,b.x,p),mix(a.y,b.y,p),width,height,
            Math.max(0f,Math.min(mix(a.radius,b.radius,p),Math.min(width,height)/2f)),Math.max(0f,Math.min(1f,mix(a.progress,b.progress,p))));
    }
    private static float[] location(View view) {
        int[] xy=new int[2];view.getLocationOnScreen(xy);
        return new float[]{xy[0]-view.getTranslationX()-view.getPivotX()*(1f-view.getScaleX()),
            xy[1]-view.getTranslationY()-view.getPivotY()*(1f-view.getScaleY())};
    }
    static void captureCapsule(View view) {
        if(view.getWidth()<=0||view.getHeight()<=0)return;
        // The Drawable override belongs to the host's transition, not its layout.
        // Sampling it during app switches feeds a squeezed intermediate back as a goal.
        int width=view.getWidth();
        float[] xy=location(view);
        HookConfig config=ShadeHook.snapshot();
        float radius=config.cloudArea.cornerEnabled?config.cloudArea.corner*view.getResources().getDisplayMetrics().density:view.getHeight()/2f;
        capsuleAnchors.put(view,new Frame(xy[0]+(view.getWidth()-width)/2f,xy[1],width,view.getHeight(),Math.min(radius,Math.min(width,view.getHeight())/2f),0f));
    }
    static Frame cardFrame(View card,List<View> group,ClassLoader loader) {
        float[] xy=location(card);float dx=0,dy=0;
        HookConfig config=ShadeHook.snapshot();float density=card.getResources().getDisplayMetrics().density;
        if(config.cloudCardPositionEnabled){
            float left=xy[0],top=xy[1],right=left+card.getWidth(),bottom=top+card.getHeight();
            for(View peer:group){float[] at=location(peer);left=Math.min(left,at[0]);top=Math.min(top,at[1]);right=Math.max(right,at[0]+peer.getWidth());bottom=Math.max(bottom,at[1]+peer.getHeight());}
            var display=card.getResources().getDisplayMetrics();
            dx=Math.max(-left,Math.min(display.widthPixels-right,config.cloudCardOffsetX*density));
            dy=Math.max(-top,Math.min(display.heightPixels-bottom,config.cloudCardOffsetY*density));
        }
        float radius;
        try {
            CloudHook.beginNativeCorners();
            Object corners=Reflect.findClass(BASE+"util.c",loader).getDeclaredMethod("f").invoke(null);
            radius=((Number)Reflect.callMethod(corners,"e")).floatValue();
        }catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
        finally{CloudHook.endNativeCorners();}
        radius=CloudSurfacePolicy.radius(card.getWidth(),card.getHeight(),radius,config.cloudCardArea.cornerEnabled,config.cloudCardArea.corner,density);
        return new Frame(xy[0]+dx,xy[1]+dy,card.getWidth(),card.getHeight(),radius,1);
    }
    private static final class Surface {
        final View view,owner;
        final Frame capsule,expanded,start;
        final int index;
        float startOpacity;
        final float targetOpacity,entryCompression;
        float phaseStart,phaseEnd;
        CloudMotion trajectory;
        Frame target;
        final CloudMorphSurface shared;
        final boolean cardTransition;
        Frame current;
        Surface(Target nativeTarget,Spec spec,Frame capsule,Mode mode,int index)throws ReflectiveOperationException {
            view=nativeTarget==null?null:nativeTarget.view;owner=nativeTarget==null?null:nativeTarget.owner;this.capsule=capsule;this.index=index;
            cardTransition=!capsuleOnly(mode);
            List<View> group=spec.cards.stream().map(Target::view).toList();
            expanded=view!=null?cardFrame(view,group,spec.loader):capsule;
            CloudMorphSurface stale=sessions.get(spec.container);
            if(stale!=null&&stale.disposed())sessions.remove(spec.container);
            CloudMorphSurface lead=sessions.computeIfAbsent(spec.container,key->newSession(spec.container,spec.loader));
            shared=index==0?lead:lead.cardLayer(view);
            shared.capsuleMotionOwned(mode==Mode.CAPSULE_ENTER||mode==Mode.CAPSULE_HIDE);
            shared.cardGroup(group);
            Frame previous=shared.current();
            Frame enter=entrance(capsule);
            Frame behind=expanded;
            if(index>0){
                Frame first=cardFrame(spec.cards.get(0).view,group,spec.loader);
                behind=new Frame(first.x+first.width*.02f,first.y+8f,first.width*.96f,first.height*.96f,expanded.radius,1);
            }
            start=previous!=null?previous:index>0?(spec.opening?behind:expanded):capsuleOnly(mode)?(mode==Mode.CAPSULE_ENTER?enter:capsule):spec.opening?capsule:expanded;
            target=index>0?(spec.opening?expanded:behind):mode==Mode.CAPSULE_HIDE?CloudVisibilityState.presentation(capsule,0):spec.opening?expanded:capsule;
            startOpacity=previous==null?(index>0&&spec.opening||mode==Mode.CAPSULE_ENTER?0:1):shared.opacity();
            targetOpacity=index>0&&!spec.opening||mode==Mode.CAPSULE_HIDE?0:1;
            entryCompression=mode==Mode.CAPSULE_ENTER&&previous!=null&&startOpacity>=.999f
                &&Math.abs(start.width-capsule.width)<.5f&&Math.abs(start.height-capsule.height)<.5f
                &&Math.abs(start.x-capsule.x)<.5f&&Math.abs(start.y-capsule.y)<.5f?.03f:0;
            if(view!=null||previous==null)shared.bind(spec.capsule,view,owner,nativeTarget!=null&&nativeTarget.instant);
            else shared.syncCapsule(spec.capsule);
        }
        void acquire(boolean opening){shared.beginTransition(cardTransition,opening);shared.show();}
        void frame(Frame frame,boolean primary){current=frame;shared.frame(frame);}
        void restore(boolean opening,boolean cancelled){
            if(!cancelled){
                shared.capsuleMotionOwned(false);
                if(index==0||opening)shared.settle(opening);
            }
        }
        void releaseClips(){}
    }
    /** Native timeline owns mounting/events. Only the last visual state before draw is ours. */
    private static final class Motion extends AnimatorListenerAdapter implements ViewTreeObserver.OnPreDrawListener {
        final Request request;
        final Animator original;
        final ValueAnimator clock=ValueAnimator.ofFloat(0f,1f);
        final ArrayList<Surface> surfaces=new ArrayList<>();
        final Map<ViewTreeObserver,Boolean> observers=new IdentityHashMap<>();
        final Runnable awaitLayout=this::awaitLayout;
        final Object window;
        final java.util.LinkedHashMap<String,Object[]> deferred=new java.util.LinkedHashMap<>();
        final int curve,bounce,duration;
        CloudMotion trajectory;
        Spec spec;
        boolean closed,started,nativeEnded,visualEnded,retargeting,inheritPhase;
        int attempts;
        float progress;
        Motion(Request request,Animator original) {
            this.request=request;this.original=original;
            boolean normal=request.mode==Mode.NORMAL_OPEN||request.mode==Mode.NORMAL_CLOSE;
            window=capsuleOnly(request.mode)?request.builder:Reflect.getObjectField(request.builder,normal?"j":"k");
            HookConfig config=ShadeHook.snapshot();duration=config.cloudAnimationDuration;curve=config.cloudAnimationCurve;bounce=config.cloudAnimationBounce;
            clock.setDuration(duration);clock.setInterpolator(LINEAR);
            clock.addUpdateListener(a -> {
                if(closed)return;
                progress=((Number)a.getAnimatedValue()).floatValue();
                render();
            });
            clock.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator ignored){
                    if(closed||retargeting)return;visualEnded=true;
                    try{for(Surface surface:surfaces)surface.shared.settleInput(spec.opening);}
                    catch(Throwable error){ShadeHook.frameworkFailure("cloud-morph-input-settle",error);finish(true);return;}
                    if(nativeEnded)finish(false);
                }
            });
        }
        void watch(View view) {
            ViewTreeObserver observer=view.getViewTreeObserver();
            if(observer.isAlive()&&observers.put(observer,true)==null)observer.addOnPreDrawListener(this);
        }
        void watchHosts() {
            if(capsuleOnly(request.mode))return;
            boolean normal=request.mode==Mode.NORMAL_OPEN||request.mode==Mode.NORMAL_CLOSE;
            Object window=Reflect.getObjectField(request.builder,normal?"j":"k");
            Object root=window==null?null:Reflect.getObjectField(window,"g");if(root instanceof View view)watch(view);
            Object secondary=Reflect.getObjectField(window,"h");
            if(secondary!=null&&Reflect.getObjectField(secondary,"r") instanceof View view)watch(view);
        }
        boolean prepare() {
            spec=resolve(request);
            if(spec==null)return false;
            if(!spec.capsule.isAttachedToWindow()){ShadeHook.observed("cloud-morph-block","capsule detached");return false;}
            watch(spec.container);
            if(capsuleOnly(request.mode))captureCapsule(spec.capsule);
            Frame capsule=capsuleOnly(request.mode)?capsuleAnchors.get(spec.capsule):capsuleAnchors.get(spec.container);
            if(capsule==null)capsule=capsuleAnchors.get(spec.capsule);
            if(capsule==null){captureCapsule(spec.capsule);capsule=capsuleAnchors.get(spec.capsule);}
            if(capsule==null){ShadeHook.observed("cloud-morph-block","capsule anchor missing;size="+spec.capsule.getWidth()+"x"+spec.capsule.getHeight());return false;}
            if(!capsuleOnly(request.mode)&&spec.cards.isEmpty()){ShadeHook.observed("cloud-morph-block","card list empty");return false;}
            for(Target target:spec.cards)if(!target.view.isAttachedToWindow()||target.view.getWidth()<=0||target.view.getHeight()<=0){
                ShadeHook.observed("cloud-morph-block","card not ready;attached="+target.view.isAttachedToWindow()+";size="+target.view.getWidth()+"x"+target.view.getHeight());return false;
            }
            stopCapsuleClock(spec.container);
            Motion prior=containerMotions.get(spec.container);
            boolean continuing=inheritPhase||prior!=null&&prior.started&&!prior.visualEnded;
            if(prior!=null&&prior!=this)prior.finish(true,true);
            containerMotions.put(spec.container,this);
            pendingEntries.remove(spec.container);
            if(!capsuleOnly(request.mode)){windowTargets.put(window,spec.opening);windowOwners.put(window,spec.container);}
            if(capsuleOnly(request.mode)) {
                try{surfaces.add(new Surface(null,spec,capsule,request.mode,0));}
                catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
            }
            for(Target target:spec.cards) {
                Motion previous=active.get(target.view);
                if(previous!=null&&previous!=this)previous.finish(true,true);
                try {surfaces.add(new Surface(target,spec,capsule,request.mode,surfaces.size()));}
                catch(ReflectiveOperationException error){throw new IllegalStateException("C17 visual geometry unavailable",error);}
            }
            for(Surface surface:surfaces) {
                if(surface.view!=null){active.put(surface.view,this);watch(surface.view);}surface.acquire(spec.opening);
            }
            Surface primary=surfaces.get(0);
            primary.shared.retainCards(spec.cards.stream().skip(1).map(Target::view).toList());
            for(Surface surface:surfaces){
                surface.phaseStart=continuing?0:CloudCardTiming.start(spec.opening,surface.index,surfaces.size());
                surface.phaseEnd=continuing?1:CloudCardTiming.end(spec.opening,surface.index,surfaces.size());
                float span=surface.phaseEnd-surface.phaseStart;
                Frame from=surface.index==0?surface.start:withProgress(surface.start,surface.startOpacity);
                Frame to=surface.index==0?surface.target:withProgress(surface.target,surface.targetOpacity);
                surface.trajectory=new CloudMotion(from,to,velocities.get(surface.index==0?spec.container:surface.view),Math.max(1,Math.round(duration*span)),curve,bounce,surface.entryCompression);
            }
            trajectory=primary.trajectory;
            started=true;clock.start();
            ShadeHook.observed("cloud-morph-geometry",Math.round(primary.start.width)+"x"+Math.round(primary.start.height)+" -> "+
                Math.round((spec.opening?primary.expanded:primary.capsule).width)+"x"+Math.round((spec.opening?primary.expanded:primary.capsule).height));
            ShadeHook.resourceHit("cloud-continuous-morph");
            ShadeHook.resourceHit("cloud-morph-started");
            return true;
        }
        void retargetCapsule(Frame goal){
            if(!started||spec.opening||request.mode==Mode.CAPSULE_HIDE||surfaces.isEmpty())return;
            Surface surface=surfaces.get(0);
            Frame current=surface.shared.current();
            if(goal.equals(surface.target)||current==null)return;
            surface.target=goal;
            for(Surface item:surfaces){
                Frame presented=item.shared.current();
                if(presented==null)continue;
                item.startOpacity=item.shared.opacity();
                Frame from=item.index==0?presented:withProgress(presented,item.startOpacity);
                Frame to=item.index==0?item.target:withProgress(item.target,item.targetOpacity);
                item.trajectory=new CloudMotion(from,to,velocities.get(item.index==0?spec.container:item.view),duration,curve,bounce);
                item.phaseStart=0;item.phaseEnd=1;
            }
            trajectory=surface.trajectory;
            retargeting=true;clock.cancel();retargeting=false;
            visualEnded=false;progress=0;clock.start();
            ShadeHook.resourceHit("cloud-morph-layout-retarget");
        }
        void render() {
            if(closed||!started)return;
            try {
                if(!enabled()){finish(true);return;}
                for(Surface surface:surfaces)if(surface.shared.disposed()){finish(true,true);return;}
                for(int i=0;i<surfaces.size();i++) {
                    Surface surface=surfaces.get(i);
                    float phase=CloudCardTiming.fraction(progress,surface.phaseStart,surface.phaseEnd);
                    Frame presented=surface.trajectory.frame(phase);
                    if(i==0){
                        surface.frame(presented,true);
                        float fade=CloudCardTiming.opacity(phase);
                        surface.shared.opacity(request.mode==Mode.CAPSULE_HIDE?surface.startOpacity*Math.min(1f,4f*(1f-fade)):
                            mix(surface.startOpacity,surface.targetOpacity,fade));
                    }else{
                        // Followers emerge from and gather beneath the moving first
                        // card. Its current form is their anchor throughout contraction.
                        float reveal=presented.progress();
                        surface.shared.opacity(reveal);
                        surface.frame(CloudCardTiming.follower(surfaces.get(0).current,surfaces.get(0).expanded,surface.expanded,reveal),false);
                    }
                    velocities.put(i==0?spec.container:surface.view,surface.trajectory.velocity(phase));
                }

            } catch(Throwable error){ShadeHook.frameworkFailure("cloud-morph-frame",error);finish(true);}
        }
        @Override public void onAnimationStart(Animator ignored) {
            Motion previous=windows.get(window);
            inheritPhase=previous!=null&&previous.started&&!previous.visualEnded;
            if(previous!=null&&previous!=this)previous.finish(true,true);
            windows.put(window,this);
            running.put(this,true);
            ShadeHook.resourceHit("cloud-morph-native-start");
            try {
                watchHosts();
                if(!prepare())layoutHandler.postDelayed(awaitLayout,16L);
            } catch(Throwable error){ShadeHook.frameworkFailure("cloud-morph-start",error);finish(true);}
        }
        void awaitLayout() {
            if(closed||started)return;
            try {
                if(!enabled()){finish(true);return;}
                if(prepare())return;
                if(++attempts>=32) {
                    ShadeHook.observed("cloud-morph-skip","native target not available; host animation retained");
                    finish(true);return;
                }
                layoutHandler.postDelayed(awaitLayout,16L);
            } catch(Throwable error){ShadeHook.frameworkFailure("cloud-morph-layout",error);finish(true);}
        }
        @Override public boolean onPreDraw() {
            if(closed)return true;
            ShadeHook.resourceHit("cloud-morph-predraw");
            try {
                if(!started) {
                    if(prepare())layoutHandler.removeCallbacks(awaitLayout);
                    else if(spec!=null) {
                        // Keep the starting capsule intact while the host mounts its card.
                        spec.capsule.setVisibility(View.VISIBLE);spec.container.setVisibility(View.VISIBLE);
                        spec.capsule.setAlpha(1f);spec.capsule.setScaleX(1f);spec.capsule.setScaleY(1f);
                        spec.capsule.setRenderEffect(null);
                        spec.container.setAlpha(1f);spec.container.setScaleX(1f);spec.container.setScaleY(1f);
                        Frame anchor=capsuleAnchors.get(spec.container);
                        if(anchor==null)anchor=capsuleAnchors.get(spec.capsule);
                        if(anchor!=null&&drawableCapsule(spec.capsule))
                            Reflect.callMethod(spec.capsule,"setCapsuleDrawableWidth",Math.round(anchor.width));
                    }
                } else render();
            } catch(Throwable error){ShadeHook.frameworkFailure("cloud-morph-predraw",error);finish(true);}
            return true;
        }
        @Override public void onAnimationCancel(Animator ignored){
            // The native condition graph can cancel its own clock before our duration.
            // Only a NEW scene may retarget the shared body; native cancel is not a visual endpoint.
            nativeEnded=true;
            if(!started||visualEnded)finish(!started);
        }
        @Override public void onAnimationEnd(Animator ignored){
            nativeEnded=true;
            ShadeHook.observed("cloud-morph-native-end","progress="+progress+";duration="+duration+"ms");
            if(!started||visualEnded)finish(false);
        }
        void finish(boolean cancelled){finish(cancelled,false);}
        void finish(boolean cancelled,boolean superseded) {
            if(closed)return;
            ArrayList<int[]> visibility=new ArrayList<>();
            for(Surface surface:surfaces)visibility.add(new int[]{surface.view==null?View.VISIBLE:surface.view.getVisibility(),surface.owner==null?View.VISIBLE:surface.owner.getVisibility()});
            if(!cancelled&&started){progress=1f;render();}
            if(closed)return;
            closed=true;running.remove(this);
            releaseEntry(request.builder,false);
            if(spec!=null&&containerMotions.get(spec.container)==this)containerMotions.remove(spec.container);layoutHandler.removeCallbacks(awaitLayout);
            if(windows.get(window)==this)windows.remove(window);
            original.removeListener(this);clock.cancel();
            for(ViewTreeObserver observer:observers.keySet())if(observer.isAlive())observer.removeOnPreDrawListener(this);
            observers.clear();
            for(int i=0;i<surfaces.size();i++) {
                Surface surface=surfaces.get(i);
                if(active.get(surface.view)==this)active.remove(surface.view);
                try {surface.restore(spec.opening,cancelled);}
                catch(Throwable error){ShadeHook.frameworkFailure("cloud-morph-restore",error);}
                finally {
                    if(surface.view!=null)surface.view.setVisibility(visibility.get(i)[0]);if(surface.owner!=null)surface.owner.setVisibility(visibility.get(i)[1]);
                    surface.releaseClips();
                }
            }
            if(cancelled&&!superseded&&spec!=null){
                CloudMorphSurface shared=sessions.remove(spec.container);if(shared!=null)shared.dispose();
            }
            if(cancelled&&!superseded){windowTargets.remove(window);windowOwners.remove(window);}
            if(!superseded)for(var entry:deferred.entrySet())try {Reflect.callMethod(window,entry.getKey(),entry.getValue());}
                catch(Throwable error){ShadeHook.frameworkFailure("cloud-morph-window-release",error);}
            deferred.clear();
            if(!cancelled&&request.mode==Mode.CAPSULE_HIDE)for(Surface surface:surfaces)surface.shared.dispose();
        }
    }
    static void forget(View container,CloudMorphSurface surface){if(sessions.get(container)==surface){sessions.remove(container);stopCapsuleClock(container);velocities.remove(container);pendingEntries.remove(container);
        for(Object window:new ArrayList<>(windowOwners.keySet()))if(windowOwners.get(window)==container){windowOwners.remove(window);windowTargets.remove(window);}}}
    static void refresh() {
        if(enabled()){for(CloudMorphSurface shared:new ArrayList<>(sessions.values()))shared.refreshStyle();return;}
        for(View container:new ArrayList<>(capsuleClocks.keySet()))stopCapsuleClock(container);
        for(Motion motion:new ArrayList<>(running.keySet()))motion.finish(true);
        for(CloudMorphSurface shared:new ArrayList<>(sessions.values()))shared.dispose();
        sessions.clear();pendingEntries.clear();windowTargets.clear();windowOwners.clear();
    }
}
