package io.github.baishangui.thunder;

import android.app.KeyguardManager;
import android.animation.ValueAnimator;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.DrawableWrapper;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.WindowInsets;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;
import android.widget.FrameLayout;
import java.lang.reflect.Proxy;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** A persistent independent envelope; only the native material child changes geometry. */
final class CloudMorphSurface implements ViewTreeObserver.OnPreDrawListener {
    private static final String BASE="com.oplus.systemui.plugins.seedling.";
    private static final ThreadLocal<Integer> nativeInputDepth=ThreadLocal.withInitial(()->0);
    private static final Map<View,WeakReference<CloudMorphSurface>> inputOwners=new WeakHashMap<>();
    static boolean forwardingTouch(){return nativeInputDepth.get()>0;}
    static boolean sourceRootOwnsInput(View root) {
        WeakReference<CloudMorphSurface> reference=inputOwners.get(root);
        CloudMorphSurface owner=reference==null?null:reference.get();
        return owner!=null&&owner.inputVisible();
    }
    private void releaseInputRoot() {
        WeakReference<CloudMorphSurface> reference=inputOwners.get(capsuleRoot);
        if(reference!=null&&reference.get()==this)inputOwners.remove(capsuleRoot);
    }
    private final View container;
    private final ClassLoader loader;
    private final WindowManager windows;
    private final Body body;
    private final Shape shape;
    private final WindowManager.LayoutParams params;
    private final Map<View,Hidden> hidden=new IdentityHashMap<>();
    private final ArrayList<ViewTreeObserver> observers=new ArrayList<>();
    private final Rect outlineRect=new Rect();
    private final Path inputPath=new Path();
    private final Outline inputOutline=new Outline();
    private final Matrix inputMatrix=new Matrix();
    private final Region silhouetteRegion=new Region();
    private CloudMorphHook.Frame inputGeometry;
    private float inputPress;
    private int inputEnvelopeWidth,inputEnvelopeHeight;
    private final Region inputRegion=new Region(),shapeInputRegion=new Region(),inputClip=new Region();
    private final Region windowInputRegion=new Region(),nextWindowInputRegion=new Region();
    private final CloudInputBridge nativeInput=new CloudInputBridge();
    private final CloudInputOwnership inputOwnership=new CloudInputOwnership();
    private boolean nativeInputHeld,nativeInputSyncPosted,expandedWindowInput,expandedRegionSubmitted;
    private final Object outline;
    private Object material,insetsListener;
    private View capsule,card,owner,capsuleContent,cardContent,capsuleRoot,cardRoot,nativeMini;
    private boolean instant,attached,expanded,disposed,materialReady,settled,scaleListenerRegistered,capsuleMotionOwned,capsuleHideOwned,capsuleEntryPresentation;
    private boolean closingCapsuleGesture;
    private boolean contentMotionOwned=true;
    private record CapsuleLayer(View source,View item,float width,float height) { }
    private final CloudContentSwitch<CapsuleLayer> contentSwitch=new CloudContentSwitch<>();
    private CapsuleLayer selectedContent;
    private ValueAnimator contentClock;
    private float switchDirection=1;
    private View leftMoon,rightMoon;
    private final RectF leftMoonBounds=new RectF(),rightMoonBounds=new RectF();
    private float moonOpacity,moonVelocity;
    private Boolean moonsShown;
    private ValueAnimator moonClock;
    private float moonGap;
    private void syncMoons() {
        if(parent!=null)return;
        if(leftMoon==null) {
            leftMoon=(View)Reflect.callMethod(container,"getLeftMoon");
            rightMoon=(View)Reflect.callMethod(container,"getRightMoon");
            int resource=container.getResources().getIdentifier("capsule_moon_gap","dimen","com.oplus.systemui.plugins");
            moonGap=resource==0?2*container.getResources().getDisplayMetrics().density:container.getResources().getDimension(resource);
        }
        boolean shown=leftMoon.getVisibility()==View.VISIBLE||rightMoon.getVisibility()==View.VISIBLE;
        if(moonsShown!=null&&moonsShown==shown)return;
        if(moonClock!=null){ValueAnimator previous=moonClock;moonClock=null;previous.cancel();}
        if(moonsShown==null||!ValueAnimator.areAnimatorsEnabled()) {
            moonsShown=shown;moonOpacity=shown?1:0;moonVelocity=0;return;
        }
        moonsShown=shown;
        int duration=180;
        CloudMotion fade=new CloudMotion(new CloudMorphHook.Frame(0,moonOpacity,1,1,0,0),
            new CloudMorphHook.Frame(0,shown?1:0,1,1,0,0),new CloudMorphHook.Frame(0,moonVelocity,0,0,0,0),duration,2,0,0,true);
        ValueAnimator clock=ValueAnimator.ofFloat(0,1);moonClock=clock;clock.setDuration(duration);
        clock.setInterpolator(new android.view.animation.LinearInterpolator());
        clock.addUpdateListener(value->{if(disposed||moonClock!=clock)return;
            float t=((Number)value.getAnimatedValue()).floatValue();moonOpacity=fade.frame(t).y();moonVelocity=fade.velocity(t).y();
            body.invalidate();updateInputRegion();});
        clock.addListener(new AnimatorListenerAdapter(){@Override public void onAnimationEnd(Animator animation){
            if(moonClock==clock){moonClock=null;moonOpacity=shown?1:0;moonVelocity=0;}
        }});clock.start();
    }
    private void moonBounds(View moon,boolean right,RectF bounds) {
        CloudCapsuleContent.moonBounds(moon,shape.getWidth(),shape.getHeight(),moonGap,right,bounds);
    }
    private final CloudTextRenderer capsuleText=new CloudTextRenderer(this::invalidateContent);
    private final CloudTextRenderer cardText=new CloudTextRenderer(this::invalidateContent);
    private record ContentSources(View capsule,View card) { }
    private ContentSources contentSources;
    private int outlineWidth=-1,outlineHeight=-1;
    private float outlineRadius=-1;
    private float outlineWeight=-1;
    private float hostOpacityLimit=1f;
    private final ValueAnimator.DurationScaleChangeListener durationScaleChanged=this::onDurationScaleChanged;
    private final ArrayList<View> layoutSources=new ArrayList<>();
    private ViewTreeObserver geometryObserver;
    private ViewTreeObserver.OnPreDrawListener geometryRefresh;
    private final View.OnLayoutChangeListener cardGeometryChanged=(view,l,t,r,b,oldL,oldT,oldR,oldB)->queueCardGeometry();
    private HookConfig styledConfig;
    private View styledCapsule;
    private Drawable nativeCapsuleBackground,styledBackground,styledForeground,cardDecoration;
    private Drawable roundedCardDecoration;
    private float cardDecorationRadius=-1;
    private float cardDecorationWeight=-1;
    private final CloudSurfacePolicy.CapsuleBackground<Drawable> capsuleBackground=new CloudSurfacePolicy.CapsuleBackground<>();
    private CloudNativeMaterial nativeMaterial;
    private CloudNativeDecoration capsuleDecoration,capsuleForeground;
    private final CloudVisibilityState visibility=new CloudVisibilityState();
    private ValueAnimator hostVisibilityClock;
    private boolean hostVisibilityObserved,businessVisibilityObserved,lifetimeVisible=true;
    private CloudMorphHook.Frame current;
    private CloudMorphHook.Frame presented;
    private final CloudMorphSurface parent;
    private final CloudCardRoster<View> nativeCards=new CloudCardRoster<>();
    private java.util.List<View> cardGroup=java.util.List.of();
    void cardGroup(java.util.List<View> group){cardGroup=group;}
    private final Map<View,CloudMorphSurface> layers=new java.util.LinkedHashMap<>();
    private Gesture gesture;
    private record Hidden(float alpha,int accessibility) { }
    private final BroadcastReceiver screenLifetime=new BroadcastReceiver(){
        @Override public void onReceive(Context context,Intent intent){dispose();}
    };
    private final View.OnAttachStateChangeListener sourceLifetime=new View.OnAttachStateChangeListener(){
        @Override public void onViewAttachedToWindow(View view) { }
        @Override public void onViewDetachedFromWindow(View view){dispose();}
    };
    private final View.OnAttachStateChangeListener cardInputLifetime=new View.OnAttachStateChangeListener(){
        @Override public void onViewAttachedToWindow(View view){if(!disposed)updateInputRegion();}
        @Override public void onViewDetachedFromWindow(View view){
            releaseNativeInput();cancelGesture();if(!disposed)updateInputRegion();
        }
    };

    CloudMorphSurface(View container,ClassLoader loader) {this(container,loader,null);}
    private CloudMorphSurface(View container,ClassLoader loader,CloudMorphSurface parent) {
        this.parent=parent;this.container=container;this.loader=loader;
        windows=(WindowManager)container.getContext().getSystemService(Context.WINDOW_SERVICE);
        body=new Body(container.getContext());shape=new Shape(container.getContext());
        body.addView(shape,new FrameLayout.LayoutParams(1,1));
        params=new WindowManager.LayoutParams(1,1,2017,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS|
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED|WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,PixelFormat.TRANSLUCENT);
        params.gravity=Gravity.TOP|Gravity.LEFT;
        params.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        params.setFitInsetsTypes(0);params.setTitle("Thunder unified cloud");
        updateEnvelope(false);
        try {
            Object corners=Reflect.findClass(BASE+"util.c",loader).getDeclaredMethod("f").invoke(null);
            outline=Reflect.newInstance(Reflect.findClass("com.oplus.systemui.plugins.common.util.j",loader),outlineRect,corners);
            Reflect.setAdditionalInstanceField(outline,"thunder.cloud-morph",true);
        }catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
        shape.setOutlineProvider((ViewOutlineProvider)outline);shape.setClipToOutline(true);
        if(parent!=null)return;
        container.addOnAttachStateChangeListener(sourceLifetime);
        container.getContext().registerReceiver(screenLifetime,new IntentFilter(Intent.ACTION_SCREEN_OFF),Context.RECEIVER_NOT_EXPORTED);
        scaleListenerRegistered=ValueAnimator.registerDurationScaleChangeListener(durationScaleChanged);
    }
    private void onDurationScaleChanged(float scale) {
        if(scale<=0)container.post(()->{if(!disposed)retainSurface();});
    }
    boolean disposed(){return disposed;}
    CloudMorphHook.Frame current(){return disposed?null:current;}
    CloudMorphHook.Frame presentation(){return disposed?null:presented;}
    CloudMorphHook.Frame presentationVelocity(CloudMorphHook.Frame logicalVelocity) {
        if(disposed||current==null)return null;
        CloudMorphSurface host=parent==null?this:parent;
        return host.capsuleHideOwned?logicalVelocity:host.visibility.presentationVelocity(current,logicalVelocity);
    }
    float presentationOpacity(){return body.getAlpha();}
    float opacity(){return visibility.motion();}
    void opacity(float value){visibility.motion(value);applyOpacity();}
    /** Native w/y gates have an interruptible silhouette independent from the form trajectory. */
    void hostVisibility(boolean shown) {
        if(disposed||parent!=null)return;
        hostVisibilityObserved=true;
        visibility.allowHost(shown);setHostVisibility(visibility.targetShown());
    }
    void businessVisibility(boolean shown) {
        if(disposed||parent!=null)return;
        businessVisibilityObserved=true;
        visibility.allowBusiness(shown);setHostVisibility(visibility.targetShown());
    }
    private void setHostVisibility(boolean shown) {
        if(disposed||parent!=null)return;
        if(visibility.shown()==shown)return;
        visibility.shown(shown);
        if(!shown)cancelInputTree();
        if(hostVisibilityClock!=null){ValueAnimator previous=hostVisibilityClock;hostVisibilityClock=null;previous.cancel();}
        // Cached host/business gates are applied before a late-created window is mounted.
        // There is no prior visible frame to fade from in that case.
        if(!attached){visibility.host(shown?1f:0f);if(shown)hostOpacityLimit=1f;applyOpacity();return;}
        HookConfig config=ShadeHook.snapshot();
        float initialLimit=hostOpacityLimit;
        visibility.beginHost(shown?1f:0f,config.cloudAnimationDuration,config.cloudAnimationCurve);
        visibility.sampleHost(0); // Make the new gate's initial velocity available before scene handoff.
        ValueAnimator clock=ValueAnimator.ofFloat(0f,1f);
        hostVisibilityClock=clock;clock.setDuration(config.cloudAnimationDuration);
        clock.setInterpolator(new android.view.animation.LinearInterpolator());
        clock.addUpdateListener(value->{if(!disposed&&hostVisibilityClock==clock){
            float fraction=((Number)value.getAnimatedValue()).floatValue();
            visibility.sampleHost(fraction);
            if(shown)hostOpacityLimit=initialLimit+(1f-initialLimit)*CloudCardTiming.opacity(fraction);
            applyOpacity();
        }});
        clock.addListener(new AnimatorListenerAdapter(){
            @Override public void onAnimationEnd(Animator animation){if(hostVisibilityClock==clock){visibility.finishHost();hostVisibilityClock=null;}}
        });
        applyOpacity();clock.start();
    }
    private void applyOpacity() {
        float amount=capsuleHideOwned?visibility.motion():Math.min(visibility.motion(),hostOpacityLimit)*Math.min(1f,visibility.host()*4f);
        body.setAlpha(amount);
        int desired=lifetimeVisible&&amount>0f?View.VISIBLE:View.INVISIBLE;
        if(body.getVisibility()!=desired)body.setVisibility(desired);
        if(current!=null)applyFrameGeometry(current);
        for(CloudMorphSurface layer:layers.values())if(layer.current!=null)layer.applyFrameGeometry(layer.current);
        updateInputRegion();
    }
    private boolean inputVisible() {
        return !disposed&&attached&&current!=null&&lifetimeVisible&&visibility.acceptsInput()&&
            body.getVisibility()==View.VISIBLE&&(parent==null||parent.inputVisible());
    }
    private boolean inputEnabled(){return inputVisible()&&inputOwnership.acceptsInput()&&(parent==null||parent.inputEnabled());}
    private boolean capsuleReversalEnabled(){return parent==null&&inputVisible()&&inputOwnership.acceptsCapsuleInput()&&
        current.progress()<.4f&&capsule!=null&&capsuleRoot!=null;}
    private void cancelInputTree(){cancelGesture();body.touchLayer=null;for(CloudMorphSurface layer:layers.values())layer.cancelInputTree();}
    private void releaseNativeInput() {
        try{nativeInput.release();}catch(Throwable error){ShadeHook.frameworkFailure("cloud-unified-native-input-release",error);}
        finally{nativeInputHeld=false;}
    }
    private boolean ownsNativeInput() {
        boolean nativeShown=cardRoot!=null&&cardRoot.isAttachedToWindow()&&cardRoot.isShown()&&
            cardRoot.getRootView()!=body.getRootView()&&cardRoot.getRootView().isShown();
        boolean keep=inputOwnership.keepLease(nativeShown);
        return inputVisible()&&keep;
    }
    private boolean hasNativeInputLease(){if(nativeInputHeld)return true;for(CloudMorphSurface layer:layers.values())if(layer.hasNativeInputLease())return true;return false;}
    private boolean needsClosingInputLease(){if(!inputOwnership.acceptsInput()&&ownsNativeInput())return true;for(CloudMorphSurface layer:layers.values())if(layer.needsClosingInputLease())return true;return false;}
    private void releaseInactiveNativeInputTree(){if(!ownsNativeInput())releaseNativeInput();for(CloudMorphSurface layer:layers.values())layer.releaseInactiveNativeInputTree();}
    private CloudMorphSurface outsideTarget() {
        if(ownsNativeInput()&&inputOwnership.captureOutside())return this;
        ArrayList<CloudMorphSurface> ordered=new ArrayList<>(layers.values());java.util.Collections.reverse(ordered);
        for(CloudMorphSurface layer:ordered){CloudMorphSurface found=layer.outsideTarget();if(found!=null)return found;}
        return null;
    }
    private void syncNativeInputTree(){
        boolean own=ownsNativeInput()&&(!inputOwnership.acceptsInput()||inputOwnership.captureOutside()||nativeInputHeld);
        nativeInput.sync(cardRoot,windows,own);nativeInputHeld=own;
        for(CloudMorphSurface layer:layers.values())layer.syncNativeInputTree();
    }
    private void queueNativeInputSync() {
        if(parent!=null||disposed||nativeInputSyncPosted||!(expandedWindowInput&&expandedRegionSubmitted||hasNativeInputLease()||needsClosingInputLease()))return;
        nativeInputSyncPosted=true;
        // Opening waits for the submitted Body region. Closing has already released
        // Body input, but must still disable any transparent native window.
        body.postOnAnimation(()->{
            nativeInputSyncPosted=false;if(disposed)return;
            try{syncNativeInputTree();}catch(Throwable error){ShadeHook.frameworkFailure("cloud-unified-native-input",error);dispose();}
        });
    }
    private void forwardOutside(MotionEvent event) {
        MotionEvent forwarded=CloudInputBridge.outsideToRoot(event,body,cardRoot);
        nativeInputDepth.set(nativeInputDepth.get()+1);
        try{
            if(cardRoot.dispatchTouchEvent(forwarded)){
                CloudMorphSurface root=parent==null?this:parent;
                root.dismissInputTree();root.cancelInputTree();root.updateInputRegion();root.queueNativeInputSync();
            }
        }
        finally{int depth=nativeInputDepth.get()-1;if(depth==0)nativeInputDepth.remove();else nativeInputDepth.set(depth);forwarded.recycle();}
    }
    private void dismissInputTree(){inputOwnership.dismiss();for(CloudMorphSurface layer:layers.values())layer.dismissInputTree();}
    CloudMorphSurface cardLayer(View card){
        CloudMorphSurface layer=layers.get(card);
        if(layer==null||layer.disposed){layer=new CloudMorphSurface(container,loader,this);layers.put(card,layer);}
        return layer;
    }
    void retainCards(java.util.List<View> cards){
        for(View key:new ArrayList<>(layers.keySet()))if(!cards.contains(key)){layers.remove(key).dispose();}
    }

    void bind(View capsule,View card,View owner,boolean instant) {
        if(disposed)throw new IllegalStateException("Unified cloud source was detached");
        if(cardRoot!=null)cardRoot.removeOnAttachStateChangeListener(cardInputLifetime);
        if(this.card!=card||this.owner!=owner||this.instant!=instant)cancelGesture();
        unwatch();unwatchGeometry();restoreHidden();
        this.card=card;this.owner=owner;this.instant=instant;
        cardContent=card;
        if(card!=null&&!instant)try {
            Object content=Reflect.callMethod(card,"getContentView");
            if(content instanceof View view)cardContent=view;
        }catch(Throwable error){ShadeHook.frameworkFailure("cloud-unified-card-content",error);}
        cardRoot=findRoot(card,"card.ui.view.e","CardContainerRoot",owner!=null?owner:card);
        if(inputOwnership.bind(cardRoot==null?null:cardRoot.getRootView()))releaseNativeInput();
        if(cardRoot!=null)cardRoot.addOnAttachStateChangeListener(cardInputLifetime);
        watchGeometry(card);watchGeometry(owner);watchGeometry(cardRoot);
        syncCapsule(capsule);
        watch(container);watch(card);watch(owner);
        suppressNative();updateStyle();
    }
    /** Service/mini replacement does not replace the independent Body or move any business View. */
    void syncCapsule(View capsule) {
        if(disposed)return;
        if(nativeMini==null&&Reflect.callMethod(container,"getMiniView") instanceof View view)nativeMini=view;
        View content=capsule;
        if(capsule!=null)try {
            Object item=Reflect.callMethod(capsule,"getViewItem");
            Object value=item==null?null:Reflect.getObjectField(item,"b");
            if(value instanceof View view)content=view;
        }catch(Throwable ignored){
            if(capsule instanceof ViewGroup group&&group.getChildCount()>0)content=group.getChildAt(0);
        }
        if(this.capsule!=capsule||capsuleContent!=content) {
            switchCapsuleContent(capsule,content);
            View nextRoot=findRoot(capsule,"capsule.ui.view.CapsuleContainerRoot","CapsuleContainerRoot",container);
            // Horizontal switching replaces the active content before UP. Its
            // native root still owns the same gesture, including velocity and UP.
            if(inputOwnership.capsuleRoot(nextRoot))cancelGesture();
            releaseInputRoot();this.capsule=capsule;capsuleContent=content;
            capsuleRoot=nextRoot;
            if(parent==null&&capsuleRoot!=null)inputOwners.put(capsuleRoot,new WeakReference<>(this));
            styledCapsule=null;body.nodesChanged();
            unwatch();watch(container);watch(card);watch(owner);
        }
        contentSourcesChanged();watch(capsule);suppressNative();updateStyle();shape.invalidate();
    }
    private void contentSourcesChanged() {
        ContentSources next=new ContentSources(capsuleContent,cardContent);
        if(next.equals(contentSources))return;
        ContentSources previous=contentSources;contentSources=next;
        if(previous==null||previous.capsule()!=capsuleContent)capsuleText.bind(capsuleContent,true);
        if(previous==null||previous.card()!=cardContent)cardText.bind(cardContent,true);
    }
    /** Model/scene notifications refresh text bindings; only a changed string starts a transition. */
    void contentChanged(Object revision){if(!disposed)capsuleText.revision();}
    void textSceneChanged(Object revision){
        if(disposed)return;
        capsuleText.reveal(revision);cardText.reveal(revision);
    }
    private void invalidateContent(){if(!disposed){shape.invalidate();body.invalidate();}}
    boolean contentChangedForSource(View source,Object revision) {
        if(disposed||source==null)return false;
        boolean capsuleMatch=relatedContent(source,capsuleContent),cardMatch=relatedContent(source,cardContent);
        if(capsuleMatch)capsuleText.revision();
        if(cardMatch)cardText.revision();
        boolean matched=capsuleMatch||cardMatch;
        for(CloudMorphSurface layer:layers.values())matched=layer.contentChangedForSource(source,revision)||matched;
        return matched;
    }
    private static boolean relatedContent(View source,View content) {
        if(content==null)return false;
        for(Object parent=source;parent instanceof View view;parent=view.getParent())if(view==content)return true;
        for(Object parent=content;parent instanceof View view;parent=view.getParent())if(view==source)return true;
        return false;
    }
    void contentMotionOwned(boolean owned){contentMotionOwned=owned;shape.invalidate();body.invalidate();}
    private boolean ownsContentMotion(){return contentMotionOwned;}
    private void switchCapsuleContent(View source,View item) {
        if(source==null)return;
        // Reuse a retained layer when a swipe reverses before completing.
        CapsuleLayer next=null;
        for(var layer:contentSwitch.layers)
            if(layer.source.source()==source&&layer.source.item()==item){next=layer.source;break;}
        if(next==null)next=new CapsuleLayer(source,item,
            Math.max(1,source.getMeasuredWidth()>0?source.getMeasuredWidth():source.getWidth()),
            Math.max(1,source.getMeasuredHeight()>0?source.getMeasuredHeight():source.getHeight()));
        selectedContent=next;
        if(contentClock!=null){ValueAnimator previous=contentClock;contentClock=null;previous.cancel();}
        HookConfig config=ShadeHook.snapshot();
        // Content resolves ahead of the outline's settling tail. A long custom
        // morph must not leave two service labels superimposed for its duration.
        int duration=Math.max(120,Math.min(320,Math.round(config.cloudAnimationDuration*.65f)));
        float distance=switchDirection*12*source.getResources().getDisplayMetrics().density;
        ShadeHook.observed("cloud-content-switch","direction="+switchDirection+";source="+Integer.toHexString(System.identityHashCode(source))+";item="+item.getClass().getName()+";bounds="+source.getWidth()+"x"+source.getHeight()+";itemBounds="+item.getLeft()+","+item.getTop()+","+item.getWidth()+","+item.getHeight());
        if(!contentSwitch.select(next,duration,distance))return;
        if(!config.cloudAnimationEnabled||!ValueAnimator.areAnimatorsEnabled()){contentSwitch.finish();return;}
        ValueAnimator clock=ValueAnimator.ofFloat(0,1);contentClock=clock;
        clock.setDuration(duration);clock.setInterpolator(new android.view.animation.LinearInterpolator());
        clock.addUpdateListener(value->{
            if(disposed||contentClock!=clock)return;
            contentSwitch.sample(((Number)value.getAnimatedValue()).floatValue());invalidateContent();
        });
        clock.addListener(new AnimatorListenerAdapter(){
            @Override public void onAnimationEnd(Animator animation){
                if(contentClock!=clock)return;
                contentSwitch.finish();contentClock=null;invalidateContent();
            }
        });
        clock.start();
    }
    boolean ownsContentSource(View source) {
        if(disposed)return false;
        for(Object node=source;node instanceof View view;node=view.getParent())
            if(view==container||view==cardContent||view==card||view==owner)return true;
        for(CloudMorphSurface layer:layers.values())if(layer.ownsContentSource(source))return true;
        return false;
    }
    private View findRoot(View source,String nativeName,String readableName,View fallback) {
        for(Object parent=source;parent instanceof View view;parent=view.getParent()) {
            String name=view.getClass().getName();
            if(name.equals(BASE+nativeName)||name.endsWith(readableName))return view;
        }
        return fallback;
    }
    private void watch(View view) {
        if(view==null)return;
        ViewTreeObserver observer=view.getViewTreeObserver();
        if(observer.isAlive()&&!observers.contains(observer)){observer.addOnPreDrawListener(this);observers.add(observer);}
    }
    void show() {
        if(disposed)return;
        if(!attached){
            if(parent==null)windows.addView(body,params);
            else parent.body.addView(body,0,new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,FrameLayout.LayoutParams.MATCH_PARENT));
            attached=true;
        }
        applyOpacity();
        ShadeHook.observed("cloud-unified-object",Integer.toHexString(System.identityHashCode(body))+";window=2017;parent=statusbar-independent;envelope="+params.width+"x"+params.height);
    }
    private void updateEnvelope(boolean relayout) {
        // A display envelope covers late card targets and finite overshoot without buffer reallocations.
        Rect bounds=windows.getCurrentWindowMetrics().getBounds();
        if(params.width==bounds.width()&&params.height==bounds.height())return;
        params.x=0;params.y=0;params.width=Math.max(1,bounds.width());params.height=Math.max(1,bounds.height());
        if(parent==null&&relayout&&attached)windows.updateViewLayout(body,params);
    }
    void frame(CloudMorphHook.Frame frame) {
        if(disposed)throw new IllegalStateException("Unified cloud detached");
        if(frame==null)return;
        settled=false;applyFrameGeometry(frame);updateStyle();suppressNative();
    }
    void beginTransition(boolean cardTransition,boolean opening){
        inputOwnership.begin(cardTransition,opening);
        if(cardTransition&&!opening)cancelInputTree();
        // Native p() pre-hides the capsule before an entry graph. Once this
        // graph owns its body, its opacity clock also owns that business reveal.
        if(parent==null&&(cardTransition||capsuleMotionOwned&&!capsuleHideOwned)){
            visibility.allowBusiness(true);
            if(capsuleMotionOwned&&!capsuleHideOwned&&capsuleEntryPresentation) {
                // Entry starts at the actual presentation snapshot, so it can
                // finish the previous reveal itself. Rebase a still-closed host
                // gate around this snapshot without opening the real host gate.
                hostOpacityLimit=visibility.hostAllowed()?1f:body.getAlpha();
                if(hostVisibilityClock!=null){ValueAnimator previous=hostVisibilityClock;hostVisibilityClock=null;previous.cancel();}
                visibility.host(1);visibility.shown(true);
                setHostVisibility(visibility.targetShown());
            }else setHostVisibility(visibility.targetShown());
        }
        updateInputRegion();queueNativeInputSync();
    }
    boolean capsuleEntryHandoff(){return parent==null&&(visibility.hostAllowed()||capsuleHideOwned);}
    void capsuleEntryPresentation(boolean value){capsuleEntryPresentation=value;}
    boolean capsuleEntryClosedHandoff(){return capsuleEntryPresentation&&!visibility.hostAllowed();}
    CloudMorphHook.Frame transitionVelocity(CloudMorphHook.Frame start,CloudMorphHook.Frame presentedVelocity) {
        if(!capsuleEntryPresentation||visibility.host()!=1f)return presentedVelocity;
        // At the rebased p=1, the logical Jacobian is identity. Subtract the
        // independent gate's derivative, including linear-curve initial speed.
        CloudMorphHook.Frame gate=visibility.presentationVelocity(start,null);
        CloudMorphHook.Frame speed=presentedVelocity==null?new CloudMorphHook.Frame(0,0,0,0,0,0):presentedVelocity;
        return new CloudMorphHook.Frame(speed.x()-gate.x(),speed.y()-gate.y(),speed.width()-gate.width(),
            speed.height()-gate.height(),speed.radius()-gate.radius(),speed.progress()-gate.progress());
    }
    void capsuleMotionOwned(boolean owned){capsuleMotionOwned=owned;}
    void capsuleHideOwned(boolean owned){capsuleHideOwned=owned;}
    boolean capsuleHideOwned(){return capsuleHideOwned;}
    private void applyFrameGeometry(CloudMorphHook.Frame frame) {
        current=frame;
        CloudMorphSurface host=parent==null?this:parent;
        // A disappear scene takes over the already presented frame. Applying
        // the native w/y gate as well would shrink and fade that same exit twice.
        frame=CloudVisibilityState.presentation(frame,host.capsuleHideOwned?1f:host.visibility.host());
        if(frame.equals(presented))return;
        presented=frame;
        int width=Math.max(1,Math.round(frame.width())),height=Math.max(1,Math.round(frame.height()));
        FrameLayout.LayoutParams child=(FrameLayout.LayoutParams)shape.getLayoutParams();
        // This child is measured and laid out synchronously below. Updating its
        // existing params keeps future parent traversals correct without scheduling
        // another full envelope measure/layout on every animation frame.
        child.width=width;child.height=height;
        if(shape.getMeasuredWidth()!=width||shape.getMeasuredHeight()!=height)
            shape.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));
        if(shape.getLeft()!=0||shape.getTop()!=0||shape.getWidth()!=width||shape.getHeight()!=height)shape.layout(0,0,width,height);
        shape.setTranslationX(frame.x()-params.x);shape.setTranslationY(frame.y()-params.y);
        float radius=Math.max(0,Math.min(frame.radius(),Math.min(width,height)/2f));
        float weight=CloudCornerGeometry.WEIGHT*CloudCardTiming.opacity(frame.progress());
        outlineRect.set(0,0,width,height);
        if(outlineWidth!=width||outlineHeight!=height||outlineRadius!=radius||outlineWeight!=weight) {
            Object corners=Reflect.getObjectField(outline,"b");
            Reflect.setObjectField(outline,"b",Reflect.newInstance(corners.getClass(),2,radius,weight));
            if(material!=null&&materialReady)Reflect.callMethod(material,"setCornerRadius",radius);
            CloudHook.roundContinuous(shape.getBackground(),radius,weight);
            outlineWidth=width;outlineHeight=height;outlineRadius=radius;outlineWeight=weight;
            shape.invalidateOutline();
        }
        decorationGeometry(width,height,radius,weight);
        updateInputRegion();shape.invalidate();
    }
    private void decorationGeometry(int width,int height,float radius,float weight) {
        if(capsuleDecoration!=null)capsuleDecoration.geometry(width,height,radius,weight);
        if(capsuleForeground!=null)capsuleForeground.geometry(width,height,radius,weight);
        if(cardDecoration!=null&&(cardDecoration!=roundedCardDecoration||radius!=cardDecorationRadius||weight!=cardDecorationWeight)) {
            CloudHook.roundContinuous(cardDecoration,radius,weight);roundedCardDecoration=cardDecoration;
            cardDecorationRadius=radius;cardDecorationWeight=weight;
        }
    }
    void settleInput(boolean expanded) {
        inputOwnership.settle(expanded);
        this.expanded=expanded;updateInputRegion();
    }
    void settle(boolean expanded) {
        settleInput(expanded);settled=true;
        refreshStyle();if(disposed)return;
        suppressNative();body.nodesChanged();shape.invalidate();
        if(current!=null)ShadeHook.observed("cloud-unified-state",(expanded?"card":"capsule")+";object="+
            Integer.toHexString(System.identityHashCode(body))+";form="+current.progress()+";radius="+current.radius()+
            ";input="+inputRegion.getBounds().toShortString()+";envelope="+params.width+"x"+params.height+
            ";capsuleBackground="+(styledBackground==null?"null":styledBackground.getClass().getName())+
            ";capsuleForeground="+(styledForeground==null?"null":styledForeground.getClass().getName())+";nativeDecoration=true");
    }
    /** Settings call this after native factories, even if the same config was observed earlier. */
    void refreshStyle() {
        if(disposed||!retainSurface())return;
        styledConfig=null;
        if(settled&&current!=null)applyFrameGeometry(endpointFrame(expanded));
        for(CloudMorphSurface layer:layers.values())layer.refreshStyle();
        updateStyle();
    }
    private boolean retainSurface() {
        HookConfig config=ShadeHook.snapshot();
        if(CloudSurfacePolicy.keep(config.enabled,config.scopedCloud("cloud"),config.cloudAnimationEnabled,ValueAnimator.getDurationScale()))return true;
        dispose();return false;
    }
    private CloudMorphHook.Frame endpointFrame(boolean measureCard) {
        CloudMorphHook.Frame before=current;float x=before.x(),y=before.y(),width=before.width(),height=before.height();
        if(measureCard&&card!=null&&card.isAttachedToWindow()&&card.getWidth()>0&&card.getHeight()>0)
            return CloudMorphHook.cardFrame(card,cardGroup,loader);
        HookConfig config=ShadeHook.snapshot();HookConfig.Area area=expanded?config.cloudCardArea:config.cloudArea;
        float nativeRadius=height/2f;
        if(expanded&&!area.cornerEnabled)try {
            CloudHook.beginNativeCorners();
            Object corners=Reflect.findClass(BASE+"util.c",loader).getDeclaredMethod("f").invoke(null);
            nativeRadius=((Number)Reflect.callMethod(corners,"e")).floatValue();
        }catch(ReflectiveOperationException error){throw new IllegalStateException("Native card corners unavailable",error);}
        finally{CloudHook.endNativeCorners();}
        float radius=CloudSurfacePolicy.radius(width,height,nativeRadius,area.cornerEnabled,area.corner,container.getResources().getDisplayMetrics().density);
        return new CloudMorphHook.Frame(x,y,width,height,radius,expanded?1:0);
    }
    private void watchGeometry(View view) {
        if(view!=null&&!layoutSources.contains(view)){layoutSources.add(view);view.addOnLayoutChangeListener(cardGeometryChanged);}
    }
    private void clearGeometryRefresh() {
        if(geometryObserver!=null&&geometryObserver.isAlive())geometryObserver.removeOnPreDrawListener(geometryRefresh);
        geometryObserver=null;geometryRefresh=null;
    }
    private void unwatchGeometry() {
        clearGeometryRefresh();for(View view:layoutSources)view.removeOnLayoutChangeListener(cardGeometryChanged);layoutSources.clear();
    }
    private void queueCardGeometry() {
        if(disposed||!settled||!expanded||current==null||card==null||geometryObserver!=null)return;
        View source=cardRoot!=null?cardRoot:card;
        ViewTreeObserver observer=source.getViewTreeObserver();if(!observer.isAlive())return;
        geometryObserver=observer;
        geometryRefresh=()->{
            clearGeometryRefresh();
            try {
                if(!disposed&&settled&&expanded&&current!=null&&retainSurface()) {
                    applyFrameGeometry(endpointFrame(true));updateStyle();body.nodesChanged();
                }
            }catch(Throwable error){ShadeHook.frameworkFailure("cloud-unified-card-layout",error);dispose();}
            return true;
        };
        observer.addOnPreDrawListener(geometryRefresh);
        source.postInvalidateOnAnimation(); // One native traversal, removed before applying the geometry.
    }
    private void updateStyle() {
        if(disposed||!materialReady)return;
        nativeCapsuleBackground=capsuleBackground.resolve(capsule==null?null:capsule.getBackground(),this::fallbackCapsuleBackground);
        Drawable foreground=capsule==null?null:capsule.getForeground();
        HookConfig config=ShadeHook.snapshot();
        if(config==styledConfig&&styledCapsule==capsule&&styledBackground==nativeCapsuleBackground&&styledForeground==foreground) {
            if(capsuleDecoration!=null)capsuleDecoration.refresh();
            if(capsuleForeground!=null)capsuleForeground.refresh();
            nativeMaterial.apply(current==null?0:current.progress(),config.cloudArea.blur);return;
        }
        styledConfig=config;styledCapsule=capsule;styledBackground=nativeCapsuleBackground;styledForeground=foreground;
        Object factory=Reflect.newInstance(Reflect.findClass("com.heytap.log.nx.obus.a",loader),container.getContext(),3);
        // Recreate defaults only on source/config changes, never for each animation frame.
        Reflect.callMethod(factory,"b",shape,material);
        CloudHook.applyUnifiedMaterial(shape,material,config.cloudCardArea);
        cardDecoration=shape.getBackground();
        if(nativeMaterial==null)nativeMaterial=new CloudNativeMaterial(material,loader);
        nativeMaterial.capture(nativeCapsuleBackground,config.cloudArea.blur);
        capsuleDecoration=nativeCapsuleBackground!=null?
            new CloudNativeDecoration(nativeCapsuleBackground,container.getResources(),container.getContext().getTheme()):null;
        capsuleForeground=foreground==null?null:new CloudNativeDecoration(foreground,container.getResources(),container.getContext().getTheme());
        shape.setBackground(nativeMaterial.blur);
        nativeMaterial.apply(current==null?0:current.progress(),config.cloudArea.blur);
        shape.setOutlineProvider((ViewOutlineProvider)outline);shape.setClipToOutline(true);
        if(presented!=null) {
            float radius=Math.max(0,Math.min(presented.radius(),Math.min(presented.width(),presented.height())/2f));
            Reflect.callMethod(material,"setCornerRadius",radius);CloudHook.roundContinuous(shape.getBackground(),radius,CloudCornerGeometry.WEIGHT*CloudCardTiming.opacity(presented.progress()));
            decorationGeometry(Math.max(1,Math.round(presented.width())),Math.max(1,Math.round(presented.height())),radius,CloudCornerGeometry.WEIGHT*CloudCardTiming.opacity(presented.progress()));
        }
        shape.invalidate();
    }
    private Drawable fallbackCapsuleBackground() {
        Object mini=nativeMini!=null?nativeMini:Reflect.callMethod(container,"getMiniView");
        if(mini instanceof View view&&view.getBackground()!=null)return view.getBackground();
        if(container instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++) {
            View child=group.getChildAt(i);
            if(isNativeCapsule(child)&&child.getBackground()!=null)return child.getBackground();
        }
        // This is the ordinary CapsuleView constructor resource in this host.
        // Keep its colors when late binding has not supplied a source plate yet.
        int id=container.getResources().getIdentifier("capsule_view_bg_ic","drawable","com.oplus.systemui.plugins");
        if(id==0)throw new IllegalStateException("Native capsule fill resource unavailable");
        return container.getResources().getDrawable(id,container.getContext().getTheme());
    }
    private void hide(View view) {
        if(view==null||view==body||view==shape)return;
        Hidden saved=hidden.get(view);
        if(saved==null){saved=new Hidden(view.getAlpha(),view.getImportantForAccessibility());hidden.put(view,saved);}
        // Native animator changes are remembered as the newest value owned by the host.
        else if(view.getAlpha()!=0f)hidden.put(view,new Hidden(view.getAlpha(),saved.accessibility()));
        if(view.getAlpha()!=0f)view.setAlpha(0f);
        if(view.getImportantForAccessibility()!=View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS)
            view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
    }
    private void suppressNative(){if(!disposed){if(parent==null)hide(container);hide(card);hide(owner);}}
    private Object nativeCardContent(View view) {
        Object content=Reflect.callMethod(view,"getContentView");return content instanceof View?content:view;
    }
    private void syncNativeCards() {
        if(parent!=null||!settled||!expanded||instant||cardRoot==null||!cardRoot.isAttachedToWindow())return;
        Object source=Reflect.callMethod(cardRoot,"getCardContainer");
        if(!(source instanceof View nativeContainer))return;
        Object snapshot=Reflect.callMethod(nativeContainer,"getCardViews");
        if(!(snapshot instanceof java.util.List<?> list))return;
        ArrayList<View> ordered=new ArrayList<>();
        for(Object item:list)if(item instanceof View view&&view.isAttachedToWindow()&&view.getWidth()>0&&view.getHeight()>0)ordered.add(view);
        CloudCardRoster.Change<View> change=nativeCards.sync(ordered,this::nativeCardContent);
        if(change.changed()) {
            // The native RecyclerView decides both the current identity and its
            // newly bound content. A deleted lead never leaves a stale snapshot.
            retainCards(ordered);
            if(ordered.isEmpty()) {
                cancelInputTree();cardContent=null;card=null;owner=null;cardGroup=java.util.List.of();opacity(0);
                contentSourcesChanged();
                for(CloudMorphSurface layer:new ArrayList<>(layers.values()))layer.dispose();layers.clear();
                body.nodesChanged();return;
            }
            View lead=ordered.get(0);
            CloudMorphSurface promoted=layers.remove(lead);if(promoted!=null)promoted.dispose();
            if(card!=lead||cardContent!=nativeCardContent(lead)){cancelGesture();bind(capsule,lead,lead,false);}
            cardGroup=java.util.List.copyOf(ordered);
            applyFrameGeometry(CloudMorphHook.cardFrame(lead,cardGroup,loader));opacity(1);
            java.util.LinkedHashMap<View,CloudMorphSurface> nextLayers=new java.util.LinkedHashMap<>();
            for(int i=1;i<ordered.size();i++) {
                View peer=ordered.get(i);CloudMorphSurface layer=cardLayer(peer);
                if(layer.card!=peer||layer.cardContent!=nativeCardContent(peer)){layer.cancelGesture();layer.bind(capsule,peer,peer,false);}
                layer.cardGroup(cardGroup);layer.frame(CloudMorphHook.cardFrame(peer,cardGroup,loader));
                layer.opacity(1);layer.show();layer.settle(true);nextLayers.put(peer,layer);
            }
            layers.clear();layers.putAll(nextLayers);
            ArrayList<CloudMorphSurface> depth=new ArrayList<>(layers.values());java.util.Collections.reverse(depth);
            for(CloudMorphSurface layer:depth)body.bringChildToFront(layer.body);
            body.bringChildToFront(shape);body.nodesChanged();updateInputRegion();
            ShadeHook.resourceHit("cloud-unified-native-card-roster");
        }else if(card!=null&&cardContent!=nativeCardContent(card)) {
            cancelGesture();cardContent=(View)nativeCardContent(card);body.nodesChanged();contentSourcesChanged();
        }
    }
    @Override public boolean onPreDraw() {
        if(disposed)return true;
        try {
            if(!retainSurface())return true;
            syncNativeCards();syncMoons();
            KeyguardManager keyguard=(KeyguardManager)container.getContext().getSystemService(Context.KEYGUARD_SERVICE);
            boolean locked=keyguard!=null&&keyguard.isKeyguardLocked();
            lifetimeVisible=!locked&&container.isAttachedToWindow();
            if(!lifetimeVisible)cancelInputTree();
            // A fallback for hosts without a w/y event. An explicit event remains authoritative
            // while the native View reaches GONE at the end of its own alpha animation.
            if(parent==null&&!hostVisibilityObserved&&!businessVisibilityObserved&&current!=null&&current.progress()==0f)
                setHostVisibility(container.isShown());
            applyOpacity();
            queueNativeInputSync();
            suppressNative();updateStyle();
            float press=settled&&current!=null&&current.progress()==0f&&!expanded&&capsule!=null&&
                visibility.shown()&&visibility.host()==1f&&!capsuleMotionOwned?
                Math.max(.94f,Math.min(1.04f,capsule.getScaleX()*container.getScaleX())):1f;
            if(shape.getScaleX()!=press||shape.getScaleY()!=press){shape.setScaleX(press);shape.setScaleY(press);updateInputRegion();}
            // Invalidate the separate content only for a real native traversal. Never invalidate its root.
            shape.invalidate();
        }catch(Throwable error){ShadeHook.frameworkFailure("cloud-unified-predraw",error);dispose();}
        return true;
    }
    private void restoreHidden() {
        for(var entry:hidden.entrySet()) {
            View view=entry.getKey();Hidden state=entry.getValue();
            if(view.getAlpha()==0f)view.setAlpha(state.alpha());
            if(view.getImportantForAccessibility()==View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS)
                view.setImportantForAccessibility(state.accessibility());
        }
        hidden.clear();
    }
    private void unwatch(){for(ViewTreeObserver observer:observers)if(observer.isAlive())observer.removeOnPreDrawListener(this);observers.clear();}
    void dispose() {
        if(moonClock!=null){ValueAnimator previous=moonClock;moonClock=null;previous.cancel();}
        if(contentClock!=null){ValueAnimator previous=contentClock;contentClock=null;previous.cancel();}
        contentSwitch.clear();selectedContent=null;
        if(disposed)return;
        disposed=true;
        if(hostVisibilityClock!=null){hostVisibilityClock.cancel();hostVisibilityClock=null;}
        releaseNativeInput();
        for(CloudMorphSurface layer:new ArrayList<>(layers.values()))layer.dispose();layers.clear();
        cancelGesture();releaseInputRoot();unwatch();unwatchGeometry();restoreHidden();container.removeOnAttachStateChangeListener(sourceLifetime);
        if(cardRoot!=null)cardRoot.removeOnAttachStateChangeListener(cardInputLifetime);
        if(attached){
            try{removeInsets();}catch(Throwable error){ShadeHook.frameworkFailure("cloud-unified-insets-dispose",error);}
            if(parent==null)windows.removeViewImmediate(body);else parent.body.removeView(body);attached=false;
        }
        if(parent==null)container.getContext().unregisterReceiver(screenLifetime);
        if(scaleListenerRegistered){ValueAnimator.unregisterDurationScaleChangeListener(durationScaleChanged);scaleListenerRegistered=false;}
        if(parent==null)CloudMorphHook.forget(container,this);current=null;
        capsuleText.clear();cardText.clear();
        if(parent!=null&&!parent.disposed){parent.body.nodesChanged();parent.updateInputRegion();}
    }
    private void installInsets() {
        try {
            Class<?> type=Reflect.findClass("android.view.ViewTreeObserver$OnComputeInternalInsetsListener",loader);
            insetsListener=Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(proxy,method,args)->{
                if(method.getName().equals("onComputeInternalInsets")) {
                    Reflect.callMethod(args[0],"setTouchableInsets",3); // TOUCHABLE_INSETS_REGION
                    ((Region)Reflect.getObjectField(args[0],"touchableRegion")).set(windowInputRegion);
                    expandedRegionSubmitted=expandedWindowInput;
                    queueNativeInputSync();
                    return null;
                }
                if(method.getName().equals("hashCode"))return System.identityHashCode(proxy);
                if(method.getName().equals("equals"))return proxy==args[0];
                if(method.getName().equals("toString"))return "Thunder cloud shape insets";
                return null;
            });
            Reflect.callMethod(body.getViewTreeObserver(),"addOnComputeInternalInsetsListener",insetsListener);
        }catch(Throwable error){throw new IllegalStateException("Cannot safely install cloud touch region",error);}
    }
    private void removeInsets() {
        if(insetsListener!=null&&body.getViewTreeObserver().isAlive())
            Reflect.callMethod(body.getViewTreeObserver(),"removeOnComputeInternalInsetsListener",insetsListener);
        insetsListener=null;
    }
    private void updateInputRegion() {
        inputRegion.setEmpty();shapeInputRegion.setEmpty();
        boolean capsuleOnly=!inputEnabled()&&capsuleReversalEnabled();
        if(!inputEnabled()&&!capsuleOnly) {
            // The Body releases immediately; the transparent native card keeps
            // its NOT_TOUCHABLE lease until that native window hides or detaches.
            releaseInactiveNativeInputTree();
            if(parent!=null)parent.updateInputRegion();else{nextWindowInputRegion.setEmpty();submitInputRegion(false);}
            return;
        }
        float press=shape.getScaleX(),width=shape.getWidth()*press,height=shape.getHeight()*press;
        float left=shape.getTranslationX()+(shape.getWidth()-width)/2f,top=shape.getTranslationY()+(shape.getHeight()-height)/2f;
        float radius=Math.max(0,Math.min(presented.radius()*press,Math.min(width,height)/2f));
        // Frame, opacity and the native pre-draw may all observe one visual tick.
        // Rasterize the exact native silhouette once; input gates and card unions
        // still refresh immediately, even when the shape itself has not moved.
        if(!presented.equals(inputGeometry)||press!=inputPress||inputEnvelopeWidth!=params.width||inputEnvelopeHeight!=params.height) {
            inputPath.reset();inputOutline.setEmpty();
            ((ViewOutlineProvider)outline).getOutline(shape,inputOutline);
            Path nativePath=(Path)Reflect.getObjectField(inputOutline,"mPath");
            if(nativePath!=null&&!nativePath.isEmpty()) {
                inputPath.set(nativePath);inputMatrix.setScale(press,press);
                inputMatrix.postTranslate(left,top);inputPath.transform(inputMatrix);
            }else inputPath.addRoundRect(left,top,left+width,top+height,radius,radius,Path.Direction.CW);
            inputClip.set(0,0,params.width,params.height);silhouetteRegion.setPath(inputPath,inputClip);
            inputGeometry=presented;inputPress=press;inputEnvelopeWidth=params.width;inputEnvelopeHeight=params.height;
        }
        shapeInputRegion.set(silhouetteRegion);inputRegion.set(shapeInputRegion);
        if(capsuleOnly) {
            // Keep the closing card and every outside pixel transparent. Only
            // the already visible part of its actual capsule destination can
            // start a reversal; an invisible destination must never catch DOWN.
            shapeInputRegion.set(CloudInputBridge.capsuleRegion(shapeInputRegion,CloudMorphHook.capsuleAnchor(capsule),params.x,params.y));
            inputRegion.set(shapeInputRegion);
            nextWindowInputRegion.set(inputRegion);releaseInactiveNativeInputTree();submitInputRegion(false);return;
        }
        if(current.progress()<.4f&&moonOpacity>.01f&&leftMoon!=null) {
            for(int side=0;side<2;side++) {
                View moon=side==0?leftMoon:rightMoon;RectF bounds=side==0?leftMoonBounds:rightMoonBounds;
                moonBounds(moon,side==1,bounds);RectF mapped=new RectF(bounds);
                shape.getMatrix().mapRect(mapped);Rect rect=new Rect();mapped.roundOut(rect);
                shapeInputRegion.op(rect,Region.Op.UNION);inputRegion.op(rect,Region.Op.UNION);
            }
        }
        for(CloudMorphSurface layer:layers.values())if(layer.inputEnabled())inputRegion.op(layer.inputRegion,Region.Op.UNION);
        if(parent!=null){parent.updateInputRegion();return;}
        boolean expandedInput=outsideTarget()!=null;
        if(expandedInput) {
            // Leave empty status-bar space to the host's shade gestures. A moved card that
            // overlaps that strip still owns its actual shape after the union below.
            nextWindowInputRegion.set(0,statusBarBottom(),params.width,params.height);
            nextWindowInputRegion.op(inputRegion,Region.Op.UNION);
        }
        else {nextWindowInputRegion.set(inputRegion);releaseInactiveNativeInputTree();}
        submitInputRegion(expandedInput);
    }
    private void submitInputRegion(boolean expandedInput) {
        boolean regionChanged=CloudInputBridge.updateRegion(windowInputRegion,nextWindowInputRegion);
        if(expandedInput==expandedWindowInput&&!regionChanged)return;
        if(expandedInput!=expandedWindowInput)expandedRegionSubmitted=false;
        expandedWindowInput=expandedInput;
        // Traversal recomputes WindowManager's input region; the envelope dimensions stay constant.
        body.invalidate();
    }
    private int statusBarBottom() {
        WindowInsets insets=body.getRootWindowInsets();
        int top=insets==null?0:insets.getInsetsIgnoringVisibility(WindowInsets.Type.statusBars()).top;
        if(top<=0) {
            android.content.res.Resources resources=container.getResources();
            int id=resources.getIdentifier("status_bar_height","dimen","android");
            if(id!=0)top=resources.getDimensionPixelSize(id);
        }
        return Math.max(0,Math.min(params.height,top));
    }
    private CloudContentTransform placement(View source) {
        if(source==container&&capsule!=null&&capsule.getWidth()>0&&capsule.getHeight()>0) {
            return CloudCapsuleContent.placement((ViewGroup)container,capsule,shape.getWidth(),shape.getHeight());
        }
        return CloudContentTransform.fit(source.getWidth(),source.getHeight(),0,0,shape.getWidth(),shape.getHeight());
    }
    private Matrix sourceToRoot(View source,View root) {
        Matrix sourceGlobal=new Matrix(),rootGlobal=new Matrix(),inverse=new Matrix();
        source.transformMatrixToGlobal(sourceGlobal);root.transformMatrixToGlobal(rootGlobal);
        if(!rootGlobal.invert(inverse))throw new IllegalStateException("Noninvertible native root");
        Matrix result=new Matrix();result.setConcat(inverse,sourceGlobal);return result;
    }
    private Matrix shapeToSource(View source) {
        CloudContentTransform fit=placement(source);
        Matrix draw=new Matrix();draw.setValues(new float[]{fit.scale(),0,fit.x(),0,fit.scale(),fit.y(),0,0,1});
        Matrix inverse=new Matrix();draw.invert(inverse);return inverse;
    }
    private final class Gesture {
        final View root,source;
        final Matrix bodyToRoot;
        MotionEvent last;
        float downX;
        Gesture(View root,View source,boolean nativeAnchor) {
            this.root=root;this.source=source;
            if(nativeAnchor){bodyToRoot=sourceToRoot(body,root);return;}
            Matrix child=new Matrix(shape.getMatrix()),inverse=new Matrix();
            if(!child.invert(inverse))throw new IllegalStateException("Noninvertible cloud shape");
            Matrix toSource=new Matrix();toSource.setConcat(shapeToSource(source),inverse);
            bodyToRoot=new Matrix();bodyToRoot.setConcat(sourceToRoot(source,root),toSource);
        }
        boolean dispatch(MotionEvent event) {
            if(source==container) {
                if(event.getActionMasked()==MotionEvent.ACTION_DOWN)downX=event.getX();
                else if(event.getActionMasked()==MotionEvent.ACTION_MOVE||event.getActionMasked()==MotionEvent.ACTION_UP) {
                    float dx=event.getX()-downX;
                    if(Math.abs(dx)>container.getResources().getDisplayMetrics().density*4)switchDirection=dx<0?1:-1;
                }
            }
            if(last!=null)last.recycle();last=MotionEvent.obtain(event);
            MotionEvent forwarded=MotionEvent.obtain(event);forwarded.transform(bodyToRoot);
            nativeInputDepth.set(nativeInputDepth.get()+1);
            try{return root.dispatchTouchEvent(forwarded);}
            finally{
                int depth=nativeInputDepth.get()-1;if(depth==0)nativeInputDepth.remove();else nativeInputDepth.set(depth);
                forwarded.recycle();
            }
        }
        void release(){if(last!=null){last.recycle();last=null;}}
    }
    private void cancelGesture() {
        Gesture old=gesture;gesture=null;closingCapsuleGesture=false;if(old==null)return;
        try {
            if(old.last!=null){MotionEvent cancel=MotionEvent.obtain(old.last);cancel.setAction(MotionEvent.ACTION_CANCEL);
                try{old.dispatch(cancel);}finally{cancel.recycle();}}
        }catch(Throwable error){ShadeHook.frameworkFailure("cloud-unified-input-cancel",error);}
        finally{old.release();}
    }
    private final class Body extends FrameLayout {
        final NativeNodes nodes=new NativeNodes();
        CloudMorphSurface touchLayer;
        boolean outsideGesture;
        Body(Context context){super(context);setClipChildren(false);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);}
        @Override protected void dispatchDraw(Canvas canvas) {
            super.dispatchDraw(canvas);
            if(current==null||capsule==null||!(container instanceof ViewGroup group))return;
            float alpha=1f-Math.max(0,Math.min(1,current.progress()));if(alpha<=0)return;
            int save=canvas.save();canvas.concat(shape.getMatrix());
            if(leftMoon!=null) {
                moonBounds(leftMoon,false,leftMoonBounds);moonBounds(rightMoon,true,rightMoonBounds);
                CloudCapsuleContent.drawMoon(canvas,leftMoon,leftMoonBounds,alpha*moonOpacity);
                CloudCapsuleContent.drawMoon(canvas,rightMoon,rightMoonBounds,alpha*moonOpacity);
            }
            canvas.restoreToCount(save);
        }
        @Override protected void onAttachedToWindow(){super.onAttachedToWindow();if(parent==null)installInsets();updateInputRegion();}
        @Override protected void onConfigurationChanged(Configuration config) {
            super.onConfigurationChanged(config);cancelGesture();
            if(disposed||!retainSurface())return;
            updateEnvelope(true);refreshStyle();queueCardGeometry();updateInputRegion();
        }
        @Override public boolean dispatchTouchEvent(MotionEvent event) {
            int action=event.getActionMasked();
            if(outsideGesture&&action!=MotionEvent.ACTION_DOWN) {
                if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL)outsideGesture=false;
                return true;
            }
            boolean capsuleOnly=!inputEnabled();
            if(capsuleOnly&&!(capsuleReversalEnabled()||closingCapsuleGesture&&inputVisible()))return false;
            if(action==MotionEvent.ACTION_OUTSIDE) {
                CloudMorphSurface target=outsideTarget();if(target==null)return false;
                target.forwardOutside(event);return true;
            }
            if(action==MotionEvent.ACTION_DOWN){
                touchLayer=null;outsideGesture=false;
                if(parent==null&&!inputRegion.contains((int)event.getX(),(int)event.getY())) {
                    if(capsuleOnly)return false;
                    CloudMorphSurface target=outsideTarget();if(target==null)return false;
                    cancelInputTree();outsideGesture=true;target.forwardOutside(event);return true;
                }
                // Frontmost card wins. All embedded bodies share the window origin.
                ArrayList<CloudMorphSurface> ordered=new ArrayList<>(layers.values());
                for(CloudMorphSurface layer:ordered)if(!capsuleOnly&&layer.inputRegion.contains((int)event.getX(),(int)event.getY())&&
                    !shapeInputRegion.contains((int)event.getX(),(int)event.getY())){touchLayer=layer;break;}
            }
            if(touchLayer!=null){
                CloudMorphSurface target=touchLayer;
                try{return target.body.dispatchTouchEvent(event);}
                finally{if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL)touchLayer=null;}
            }
            if(action==MotionEvent.ACTION_DOWN) {
                cancelGesture();
                if(!inputRegion.contains((int)event.getX(),(int)event.getY()))return false;
                boolean cardMode=!capsuleOnly&&(expanded||(current.progress()>.5f&&cardContent!=null));
                View source=cardMode?cardContent:container,root=cardMode?cardRoot:capsuleRoot;
                if(source==null||root==null||!root.isAttachedToWindow()||source.getWidth()<=0||source.getHeight()<=0)return false;
                closingCapsuleGesture=capsuleOnly;gesture=new Gesture(root,source,capsuleOnly);
                ShadeHook.observed("cloud-unified-input",root.getClass().getName()+";source="+source.getClass().getName());
            }
            Gesture active=gesture;if(active==null)return false;
            try {active.dispatch(event);}
            finally {if((action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL)&&gesture==active){gesture=null;closingCapsuleGesture=false;active.release();}}
            return true;
        }
        @Override public boolean dispatchHoverEvent(MotionEvent event){return nodes.hover(event)||super.dispatchHoverEvent(event);}
        @Override public AccessibilityNodeProvider getAccessibilityNodeProvider(){return nodes;}
        void nodesChanged(){nodes.generation+=512;nodes.focused=-1;nodes.hovered=-1;sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);}
    }
    private final class Shape extends View {
        Shape(Context context){super(context);setWillNotDraw(false);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener(){
                int attempts;
                @Override public boolean onPreDraw() {
                    try {
                        if(material==null)material=Reflect.newInstance(Reflect.findClass("com.oplus.view.ViewRootManager",loader),Shape.this);
                        materialReady=true;styledConfig=null;updateStyle();
                        if(getBackground()==null) {
                            if(++attempts<8){postInvalidateOnAnimation();return true;}
                            throw new IllegalStateException("Unified cloud native material unavailable");
                        }
                        getViewTreeObserver().removeOnPreDrawListener(this);
                        if(current!=null)applyFrameGeometry(current);
                        ShadeHook.resourceHit("cloud-unified-material-attached");
                    }catch(Throwable error){getViewTreeObserver().removeOnPreDrawListener(this);
                        ShadeHook.frameworkFailure("cloud-unified-material",error);post(CloudMorphSurface.this::dispose);}
                    return true;
                }
            });
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);if(current==null)return;
            float form=Math.max(0,Math.min(1,current.progress()));
            int capsuleAlpha=Math.round(255*(1-form)),cardAlpha=Math.round(255*form);
            if(capsuleAlpha>0&&capsuleDecoration!=null) {
                int alpha=saveOpacity(canvas,capsuleAlpha);
                // Never sample the host animator's insetting bounds or visibility alpha.
                capsuleDecoration.drawable.setBounds(0,0,getWidth(),getHeight());capsuleDecoration.drawable.draw(canvas);
                canvas.restoreToCount(alpha);
            }
            if(cardAlpha>0&&cardDecoration!=null) {
                int decoration=saveOpacity(canvas,cardAlpha);
                cardDecoration.setBounds(0,0,getWidth(),getHeight());drawDecoration(canvas,cardDecoration);
                canvas.restoreToCount(decoration);
            }
            // Form changes crossfade the native trees. Individual changed text
            // regions own their blur inside TextView.onDraw; stable siblings stay sharp.
            float contentBlend=CloudCardTiming.opacity(form);
            drawCapsule(canvas,1f-contentBlend);
            drawContent(canvas,cardContent,contentBlend,instant);
            if(capsuleAlpha>0&&capsuleForeground!=null) {
                int foreground=saveOpacity(canvas,capsuleAlpha);
                capsuleForeground.drawable.draw(canvas);canvas.restoreToCount(foreground);
            }
        }
        private void drawCapsule(Canvas canvas,float alpha) {
            if(capsule==null||!(container instanceof ViewGroup group))return;
            if(ownsContentMotion()&&selectedContent!=null) {
                for(var layer:contentSwitch.layers) {
                    CapsuleLayer source=layer.source;
                    // Layout coordinates and their extent must be sampled from
                    // the same layout. Measured bounds may already describe the
                    // next frame while native children still use the old one.
                    float width=source.source().getWidth()>0?source.source().getWidth():source.width();
                    float height=source.source().getHeight()>0?source.source().getHeight():source.height();
                    CloudCapsuleContent.drawOwned(canvas,source.source(),source.item(),width,height,
                        getWidth(),getHeight(),alpha*layer.opacity,layer.offset);
                }
                return;
            }
            CloudCapsuleContent.draw(canvas,group,capsule,getWidth(),getHeight(),alpha,false,
                CloudMorphSurface::isNativeCapsule,CloudMorphSurface.this::capsuleItemContent,capsuleMotionOwned,ownsContentMotion(),nativeMini);
        }
        private void drawDecoration(Canvas canvas,Drawable drawable) {
            if(nativeMaterial!=null&&drawable==nativeMaterial.blur)return;
            if(drawable instanceof LayerDrawable layers) {
                for(int i=0;i<layers.getNumberOfLayers();i++)drawDecoration(canvas,layers.getDrawable(i));
            } else if(drawable instanceof DrawableWrapper wrapper)drawDecoration(canvas,wrapper.getDrawable());
            else drawable.draw(canvas);
        }
        private int saveOpacity(Canvas canvas,int opacity) {
            return opacity==255?canvas.save():canvas.saveLayerAlpha(0,0,getWidth(),getHeight(),opacity);
        }
        private void drawContent(Canvas canvas,View source,float alpha,boolean childrenOnly) {
            if(source==null||alpha<=0||source.getWidth()<=0||source.getHeight()<=0)return;
            CloudContentTransform fit=placement(source);
            int opacity=Math.round(255*Math.min(1,alpha));
            if(opacity==0)return;
            // Stable endpoints draw directly; group alpha needs an offscreen
            // surface only during the short crossfade, not every timer tick.
            int layer=saveOpacity(canvas,opacity);
            canvas.translate(fit.x(),fit.y());canvas.scale(fit.scale(),fit.scale());
            if(childrenOnly&&source instanceof ViewGroup group) {
                for(int i=0;i<group.getChildCount();i++) {
                    View child=group.getChildAt(i);if(child.getVisibility()!=VISIBLE||child.getAlpha()<=0)continue;
                    int save=canvas.save();canvas.translate(child.getLeft()-group.getScrollX(),child.getTop()-group.getScrollY());
                    canvas.concat(child.getMatrix());
                    if(child.getAlpha()<1)canvas.saveLayerAlpha(0,0,child.getWidth(),child.getHeight(),Math.round(255*child.getAlpha()));
                    child.draw(canvas);canvas.restoreToCount(save);
                }
            }else source.draw(canvas);
            canvas.restoreToCount(layer);
        }
    }
    private static boolean isNativeCapsule(View view) {
        for(Class<?> type=view.getClass();type!=null;type=type.getSuperclass())
            if(type.getName().equals(BASE+"capsule.ui.view.CapsuleView"))return true;
        return false;
    }
    private View capsuleItemContent(View view) {
        Object item=Reflect.callMethod(view,"getViewItem");
        Object value=item==null?null:Reflect.getObjectField(item,"b");
        return value instanceof View content?content:null;
    }
    /** Virtualizes native nodes without detaching their real Views or exposing duplicate hidden trees. */
    private final class NativeNodes extends AccessibilityNodeProvider {
        int focused=-1,generation=512;
        final ArrayList<Node> nodes=new ArrayList<>();
        private record Node(View view,int nativeId,int parent) { }
        View source(){return current!=null&&current.progress()>.5f&&cardContent!=null?cardContent:container;}
        void collect(View view,int parent) {
            if(view==null||view.getVisibility()!=View.VISIBLE||nodes.size()>=512)return;
            int id=nodes.size();nodes.add(new Node(view,HOST_VIEW_ID,parent));
            AccessibilityNodeProvider provider=view.getAccessibilityNodeProvider();
            if(provider!=null)collectVirtual(view,provider,HOST_VIEW_ID,id,0);
            else if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++)collect(group.getChildAt(i),id);
        }
        void collectVirtual(View view,AccessibilityNodeProvider provider,int nativeId,int parent,int depth) {
            if(depth>32||nodes.size()>=512)return;
            AccessibilityNodeInfo info=provider.createAccessibilityNodeInfo(nativeId);if(info==null)return;
            int viewId=((Number)Reflect.callMethod(view,"getAccessibilityViewId")).intValue();
            for(int i=0;i<info.getChildCount()&&nodes.size()<512;i++) {
                long child=((Number)Reflect.callMethod(info,"getChildId",i)).longValue();
                int virtual=(int)(child>>32);
                if((int)child!=viewId||virtual==HOST_VIEW_ID)continue;
                boolean duplicate=false;
                for(Node node:nodes)if(node.view()==view&&node.nativeId()==virtual){duplicate=true;break;}
                if(duplicate)continue;
                int id=nodes.size();nodes.add(new Node(view,virtual,parent));collectVirtual(view,provider,virtual,id,depth+1);
            }
            info.recycle();
        }
        void rebuild(){
            ArrayList<Node> previous=new ArrayList<>(nodes);nodes.clear();
            if(inputEnabled()){
                collect(source(),HOST_VIEW_ID);
                for(CloudMorphSurface layer:layers.values())if(layer.opacity()>.5f&&layer.current!=null)collect(layer.body.nodes.source(),HOST_VIEW_ID);
            }
            if(!previous.equals(nodes)){generation+=512;focused=-1;hovered=-1;}
        }
        private AccessibilityNodeInfo nativeInfo(Node node) {
            AccessibilityNodeProvider provider=node.view().getAccessibilityNodeProvider();
            if(provider!=null) {
                AccessibilityNodeInfo info=provider.createAccessibilityNodeInfo(node.nativeId());
                if(info==null)return null;
                // Replace provider child IDs with the independent surface's virtual namespace.
                for(int i=info.getChildCount()-1;i>=0;i--) {
                    long child=((Number)Reflect.callMethod(info,"getChildId",i)).longValue();
                    info.removeChild(node.view(),(int)(child>>32));
                }
                return info;
            }
            AccessibilityNodeInfo info=AccessibilityNodeInfo.obtain();node.view().onInitializeAccessibilityNodeInfo(info);return info;
        }
        @Override public AccessibilityNodeInfo createAccessibilityNodeInfo(int id) {
            rebuild();
            if(id==HOST_VIEW_ID) {
                AccessibilityNodeInfo host=AccessibilityNodeInfo.obtain(body);body.onInitializeAccessibilityNodeInfo(host);
                for(int n=0;n<nodes.size();n++)if(nodes.get(n).parent()==HOST_VIEW_ID)host.addChild(body,generation+n);return host;
            }
            id-=generation;
            if(id<0||id>=nodes.size()||shape.getWidth()<=0||shape.getHeight()<=0)return null;
            Node node=nodes.get(id);
            AccessibilityNodeInfo info=nativeInfo(node);if(info==null)return null;
            info.setSource(body,generation+id);
            if(node.parent()==HOST_VIEW_ID)info.setParent(body);else info.setParent(body,generation+node.parent());
            info.setVisibleToUser(body.getVisibility()==View.VISIBLE);
            for(int child=0;child<nodes.size();child++)if(nodes.get(child).parent()==id)info.addChild(body,generation+child);
            RectF bounds=nodeBounds(node,info),parentBounds=node.parent()<0?new RectF():nodeBounds(nodes.get(node.parent()),null);
            Rect inParent=new Rect();RectF localBounds=new RectF(bounds);localBounds.offset(-parentBounds.left,-parentBounds.top);
            localBounds.roundOut(inParent);info.setBoundsInParent(inParent);
            RectF visibleBounds=new RectF(inputRegion.getBounds());
            info.setVisibleToUser(body.getVisibility()==View.VISIBLE&&RectF.intersects(bounds,visibleBounds));
            bounds.offset(params.x,params.y);Rect screen=new Rect();bounds.roundOut(screen);info.setBoundsInScreen(screen);
            info.setAccessibilityFocused(focused==id);info.addAction(focused==id?
                AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS:AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS);
            return info;
        }
        private RectF nodeBounds(Node node,AccessibilityNodeInfo existing) {
            View view=node.view();CloudMorphSurface surface=CloudMorphSurface.this;
            for(CloudMorphSurface layer:layers.values())for(Object ancestor=view;ancestor instanceof View v;ancestor=v.getParent())
                if(v==layer.cardContent){surface=layer;break;}
            View source=surface.body.nodes.source();
            if((view==surface.leftMoon||view==surface.rightMoon)&&surface.leftMoon!=null) {
                RectF bounds=new RectF();surface.moonBounds(view,view==surface.rightMoon,bounds);
                surface.shape.getMatrix().mapRect(bounds);return bounds;
            }
            Matrix sourceGlobal=new Matrix(),inverse=new Matrix(),local=new Matrix();
            source.transformMatrixToGlobal(sourceGlobal);sourceGlobal.invert(inverse);
            RectF bounds;
            if(view.getAccessibilityNodeProvider()!=null) {
                AccessibilityNodeInfo info=existing==null?nativeInfo(node):existing;
                Rect nativeBounds=new Rect();if(info!=null)info.getBoundsInScreen(nativeBounds);
                bounds=new RectF(nativeBounds);inverse.mapRect(bounds);
                if(existing==null&&info!=null)info.recycle();
            } else {
                Matrix viewGlobal=new Matrix();view.transformMatrixToGlobal(viewGlobal);local.setConcat(inverse,viewGlobal);
                bounds=new RectF(0,0,view.getWidth(),view.getHeight());local.mapRect(bounds);
            }
            CloudContentTransform fit=surface.placement(source);
            bounds.set(fit.screenX(bounds.left),fit.screenY(bounds.top),fit.screenX(bounds.right),fit.screenY(bounds.bottom));
            surface.shape.getMatrix().mapRect(bounds);return bounds;
        }
        int hovered=-1;
        boolean hover(MotionEvent event) {
            rebuild();int next=-1;
            if(event.getActionMasked()!=MotionEvent.ACTION_HOVER_EXIT&&source()!=null&&shape.getWidth()>0&&shape.getHeight()>0) {
                for(int id=nodes.size()-1;id>=0;id--) {
                    Node node=nodes.get(id);RectF bounds=nodeBounds(node,null);
                    if(bounds.contains(event.getX(),event.getY())){next=id;break;}
                }
            }
            if(next!=hovered) {
                if(hovered>=0)sendNodeEvent(hovered,AccessibilityEvent.TYPE_VIEW_HOVER_EXIT);
                hovered=next;if(hovered>=0)sendNodeEvent(hovered,AccessibilityEvent.TYPE_VIEW_HOVER_ENTER);
            }
            return next>=0;
        }
        void sendNodeEvent(int id,int type) {
            AccessibilityEvent event=AccessibilityEvent.obtain(type);event.setSource(body,generation+id);
            event.setPackageName(body.getContext().getPackageName());
            if(body.getParent()!=null)body.getParent().requestSendAccessibilityEvent(body,event);
        }
        @Override public boolean performAction(int id,int action,Bundle args) {
            rebuild();id-=generation;if(id<0||id>=nodes.size())return false;
            if(action==AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS||action==AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS) {
                if(action==AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS&&focused>=0&&focused!=id)
                    sendNodeEvent(focused,AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED);
                focused=action==AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS?id:-1;
                Node node=nodes.get(id);AccessibilityNodeProvider provider=node.view().getAccessibilityNodeProvider();
                if(provider!=null)provider.performAction(node.nativeId(),action,args);
                sendNodeEvent(id,action==AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS?
                    AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED:AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED);return true;
            }
            Node node=nodes.get(id);AccessibilityNodeProvider provider=node.view().getAccessibilityNodeProvider();
            return provider!=null?provider.performAction(node.nativeId(),action,args):node.view().performAccessibilityAction(action,args);
        }
        @Override public AccessibilityNodeInfo findFocus(int focus){return focused<0?null:createAccessibilityNodeInfo(generation+focused);}
    }
}
