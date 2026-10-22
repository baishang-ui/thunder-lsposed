package io.github.baishangui.thunder;
import android.view.View;
/** Platform boundary double: real WindowManager/rendering are checked on Android. */
final class CloudMorphSurface {
    static boolean forwardingTouch(){return false;}
    static boolean sourceRootOwnsInput(View root){return false;}
    static final java.util.List<CloudMorphSurface> created=new java.util.ArrayList<>();
    final CloudInputOwnership inputOwnership=new CloudInputOwnership();
    boolean hostAllowed=true,businessAllowed=true,cardTransition,capsuleMotionOwned,capsuleHideOwned,capsuleEntryPresentation,simulateHostGate;
    float opacity=1,hostAmount=1;CloudMorphHook.Frame frame,presentedVelocity;int presentationVelocityCalls;boolean attached,expanded,disposed;View capsule,card,owner,container;
    final CloudVisibilityState hostGate=new CloudVisibilityState();
    float hostOpacityLimit=1,entryAlpha,initialLimit=1;boolean hostGateRunning,hostGateShowing;
    CloudMorphSurface(View container,ClassLoader loader){this.container=container;created.add(this);}
    boolean disposed(){return disposed;}
    boolean ownsContentSource(View source){
        if(disposed)return false;
        for(Object node=source;node instanceof View view;node=view.getParent())
            if(view==container||view==capsule||view==card||view==owner)return true;
        for(CloudMorphSurface layer:layers.values())if(layer.ownsContentSource(source))return true;
        return false;
    }
    CloudMorphHook.Frame current(){return frame;}
    CloudMorphHook.Frame presentation(){return CloudVisibilityState.presentation(frame,capsuleHideOwned?1:hostAmount);}
    float presentationOpacity(){return capsuleHideOwned?opacity:Math.min(opacity,hostOpacityLimit)*Math.min(1,hostAmount*4);}
    CloudMorphHook.Frame presentationVelocity(CloudMorphHook.Frame logicalVelocity){presentationVelocityCalls++;if(capsuleHideOwned)return logicalVelocity;if(presentedVelocity!=null)return presentedVelocity;return simulateHostGate?hostGate.presentationVelocity(frame,logicalVelocity):logicalVelocity;}
    float opacity(){return opacity;}
    final java.util.Map<View,CloudMorphSurface> layers=new java.util.LinkedHashMap<>();
    CloudMorphSurface cardLayer(View card){return layers.computeIfAbsent(card,key->new CloudMorphSurface(container,null));}
    void cardGroup(java.util.List<View> group){}
    void retainCards(java.util.List<View> cards){}
    void opacity(float value){opacity=value;}
    void bind(View capsule,View card,View owner,boolean instant){this.capsule=capsule;this.card=card;this.owner=owner;inputOwnership.bind(card);}
    void syncCapsule(View capsule){this.capsule=capsule;}
    void capsuleMotionOwned(boolean owned){capsuleMotionOwned=owned;}
    boolean contentMotionOwned;int contentChanges;
    int textSceneChanges;
    void textSceneChanged(Object revision){textSceneChanges++;}
    void contentMotionOwned(boolean owned){contentMotionOwned=owned;}
    // Count every notification: deduplication belongs to the production Hook.
    void contentChanged(){if(!disposed)contentChanges++;}
    boolean contentChangedForSource(View source){
        if(disposed||source==null)return false;
        boolean matched=related(source,capsule)||related(source,card);
        if(matched)contentChanged();
        for(CloudMorphSurface layer:layers.values())matched=layer.contentChangedForSource(source)||matched;
        return matched;
    }
    private static boolean related(View source,View content){
        if(content==null)return false;
        for(Object node=source;node instanceof View view;node=view.getParent())if(view==content)return true;
        for(Object node=content;node instanceof View view;node=view.getParent())if(view==source)return true;
        return false;
    }
    void capsuleHideOwned(boolean owned){capsuleHideOwned=owned;}
    boolean capsuleHideOwned(){return capsuleHideOwned;}
    boolean capsuleEntryHandoff(){return hostAllowed||capsuleHideOwned;}
    void capsuleEntryPresentation(boolean value){capsuleEntryPresentation=value;if(value)entryAlpha=presentationOpacity();}
    boolean capsuleEntryClosedHandoff(){return capsuleEntryPresentation&&!hostAllowed;}
    CloudMorphHook.Frame transitionVelocity(CloudMorphHook.Frame start,CloudMorphHook.Frame presentedVelocity){
        if(!capsuleEntryPresentation||hostAmount!=1)return presentedVelocity;
        var gate=hostGate.presentationVelocity(start,null);
        var speed=presentedVelocity==null?new CloudMorphHook.Frame(0,0,0,0,0,0):presentedVelocity;
        return new CloudMorphHook.Frame(speed.x()-gate.x(),speed.y()-gate.y(),speed.width()-gate.width(),speed.height()-gate.height(),speed.radius()-gate.radius(),speed.progress()-gate.progress());
    }
    void refreshStyle(){}
    void hostVisibility(boolean shown){hostAllowed=shown;if(simulateHostGate)beginHost(shown&&businessAllowed);}
    void businessVisibility(boolean shown){businessAllowed=shown;}
    void beginTransition(boolean cardTransition,boolean opening){
        this.cardTransition=cardTransition;inputOwnership.begin(cardTransition,opening);boolean entry=capsuleMotionOwned&&!capsuleHideOwned;
        if(cardTransition||entry)businessAllowed=true;
        if(entry&&capsuleEntryPresentation){
            hostOpacityLimit=hostAllowed?1:entryAlpha;hostAmount=1;hostGate.host(1);hostGateRunning=false;
            if(simulateHostGate&&!hostAllowed)beginHost(false);
        }
        presentedVelocity=null;
    }
    void beginHost(boolean shown){
        if(hostGate.host()!=hostAmount)hostGate.host(hostAmount);
        hostGateShowing=shown;initialLimit=hostOpacityLimit;
        var config=ShadeHook.snapshot();hostGate.beginHost(shown?1:0,config.cloudAnimationDuration,config.cloudAnimationCurve);
        hostGate.sampleHost(0);hostAmount=hostGate.host();hostGateRunning=true;
    }
    void sampleHost(float fraction){
        if(!hostGateRunning)return;
        hostGate.sampleHost(fraction);hostAmount=hostGate.host();
        if(hostGateShowing)hostOpacityLimit=initialLimit+(1-initialLimit)*CloudCardTiming.opacity(fraction);
        if(fraction==1){hostGate.finishHost();hostGateRunning=false;}
    }
    boolean visible(){return hostAllowed&&businessAllowed&&opacity>0;}
    void show(){attached=true;}
    void frame(CloudMorphHook.Frame value){frame=value;container.setAlpha(0);if(card!=null)card.setAlpha(0);if(owner!=null)owner.setAlpha(0);}
    void settleInput(boolean value){expanded=value;inputOwnership.settle(value);}
    void settle(boolean value){settleInput(value);}
    void dispose(){disposed=true;attached=false;container.setAlpha(1);if(card!=null)card.setAlpha(1);if(owner!=null)owner.setAlpha(1);}
}
