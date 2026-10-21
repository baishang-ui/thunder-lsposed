package io.github.baishangui.thunder;
import android.view.View;
/** Platform boundary double: real WindowManager/rendering are checked on Android. */
final class CloudMorphSurface {
    static boolean forwardingTouch(){return false;}
    static boolean sourceRootOwnsInput(View root){return false;}
    static final java.util.List<CloudMorphSurface> created=new java.util.ArrayList<>();
    final CloudInputOwnership inputOwnership=new CloudInputOwnership();
    boolean hostAllowed=true,businessAllowed=true,cardTransition,capsuleMotionOwned;
    float opacity=1;CloudMorphHook.Frame frame;boolean attached,expanded,disposed;View capsule,card,owner,container;
    CloudMorphSurface(View container,ClassLoader loader){this.container=container;created.add(this);}
    boolean disposed(){return disposed;}
    CloudMorphHook.Frame current(){return frame;}
    float opacity(){return opacity;}
    final java.util.Map<View,CloudMorphSurface> layers=new java.util.LinkedHashMap<>();
    CloudMorphSurface cardLayer(View card){return layers.computeIfAbsent(card,key->new CloudMorphSurface(container,null));}
    void cardGroup(java.util.List<View> group){}
    void retainCards(java.util.List<View> cards){}
    void opacity(float value){opacity=value;}
    void bind(View capsule,View card,View owner,boolean instant){this.capsule=capsule;this.card=card;this.owner=owner;inputOwnership.bind(card);}
    void syncCapsule(View capsule){this.capsule=capsule;}
    void capsuleMotionOwned(boolean owned){capsuleMotionOwned=owned;}
    void refreshStyle(){}
    void hostVisibility(boolean shown){hostAllowed=shown;}
    void businessVisibility(boolean shown){businessAllowed=shown;}
    void beginTransition(boolean cardTransition,boolean opening){this.cardTransition=cardTransition;inputOwnership.begin(cardTransition,opening);if(cardTransition)businessAllowed=true;}
    boolean visible(){return hostAllowed&&businessAllowed&&opacity>0;}
    void show(){attached=true;}
    void frame(CloudMorphHook.Frame value){frame=value;container.setAlpha(0);if(card!=null)card.setAlpha(0);if(owner!=null)owner.setAlpha(0);}
    void settleInput(boolean value){expanded=value;inputOwnership.settle(value);}
    void settle(boolean value){settleInput(value);}
    void dispose(){disposed=true;attached=false;container.setAlpha(1);if(card!=null)card.setAlpha(1);if(owner!=null)owner.setAlpha(1);}
}
