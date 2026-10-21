package io.github.baishangui.thunder;

import android.graphics.Canvas;
import android.graphics.Matrix;
import android.view.View;
import android.view.ViewGroup;
import java.util.function.Function;
import java.util.function.Predicate;

/** Draws the host's live children, with its native switch transforms and exterior moons. */
final class CloudCapsuleContent {
    static CloudContentTransform placement(ViewGroup container,View anchor,float width,float height) {
        CloudContentTransform fit=CloudContentTransform.fit(anchor.getWidth(),anchor.getHeight(),0,0,width,height);
        return new CloudContentTransform(fit.scale(),fit.x()-(anchor.getLeft()-container.getScrollX())*fit.scale(),
            fit.y()-(anchor.getTop()-container.getScrollY())*fit.scale());
    }
    static Matrix relative(View source,View root) {
        Matrix sourceGlobal=new Matrix(),rootGlobal=new Matrix(),inverse=new Matrix();
        source.transformMatrixToGlobal(sourceGlobal);root.transformMatrixToGlobal(rootGlobal);
        if(!rootGlobal.invert(inverse))throw new IllegalStateException("Noninvertible native capsule");
        Matrix result=new Matrix();result.setConcat(inverse,sourceGlobal);return result;
    }
    static void draw(Canvas canvas,ViewGroup container,View anchor,float width,float height,float alpha,
            boolean accessories,Predicate<View> capsule,Function<View,View> content) {
        draw(canvas,container,anchor,width,height,alpha,accessories,capsule,content,false);
    }
    static void draw(Canvas canvas,ViewGroup container,View anchor,float width,float height,float alpha,
            boolean accessories,Predicate<View> capsule,Function<View,View> content,boolean ownAnchorMotion) {
        if(alpha<=0||anchor.getWidth()<=0||anchor.getHeight()<=0)return;
        CloudContentTransform fit=placement(container,anchor,width,height);
        // Accessories deliberately have no anchor-bounded layer: native moons
        // sit outside the mini view, and the original parent does not clip them.
        int save=canvas.saveLayerAlpha(null,Math.round(255*Math.min(1,alpha)));
        canvas.translate(fit.x(),fit.y());canvas.scale(fit.scale(),fit.scale());
        for(int i=0;i<container.getChildCount();i++) {
            View child=container.getChildAt(i);
            boolean owned=ownAnchorMotion&&child==anchor;
            if(!owned&&(child.getVisibility()!=View.VISIBLE||child.getAlpha()<=0))continue;
            boolean capsuleChild=capsule.test(child);
            View item=capsuleChild?content.apply(child):null;
            boolean accessory=!capsuleChild&&child!=anchor;
            if(accessory!=accessories)continue;
            int childSave=canvas.save();
            canvas.translate(child.getLeft()-container.getScrollX(),child.getTop()-container.getScrollY());
            if(!owned)canvas.concat(child.getMatrix());
            if(!owned&&child.getAlpha()<1)canvas.saveLayerAlpha(null,Math.round(255*child.getAlpha()));
            if(item!=null) {
                if(owned||item.getVisibility()==View.VISIBLE&&item.getAlpha()>0) {
                    int contentSave=canvas.save();Matrix transform=relative(item,child);
                    if(owned) {
                        // Appear/Disappear fragments target both CapsuleView and
                        // its item via native q/o animators. The independent body
                        // already owns that global movement and opacity.
                        Matrix inverse=new Matrix();
                        if(item.getMatrix().invert(inverse))transform.preConcat(inverse);
                    }
                    canvas.concat(transform);
                    if(!owned&&item.getAlpha()<1)canvas.saveLayerAlpha(null,Math.round(255*item.getAlpha()));
                    item.draw(canvas);canvas.restoreToCount(contentSave);
                }
            }else if(!capsuleChild)child.draw(canvas);
            canvas.restoreToCount(childSave);
        }
        canvas.restoreToCount(save);
    }
}
