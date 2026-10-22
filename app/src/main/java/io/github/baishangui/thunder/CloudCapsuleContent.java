package io.github.baishangui.thunder;

import android.graphics.Canvas;
import android.graphics.Matrix;
import android.view.View;
import android.view.ViewGroup;
import java.util.function.Function;
import java.util.function.Predicate;

/** Draws the host's live children, with its native switch transforms and exterior moons. */
final class CloudCapsuleContent {
    /** Draw a retained live item without the native scene's wrapper transforms.
     * Layout offsets inside the item remain native; entry/switch alpha and scale
     * on its root are owned by the independent presentation instead. */
    static void drawOwned(Canvas canvas,View source,View item,float sourceWidth,float sourceHeight,
            float width,float height,float alpha,float offset) {
        int opacity=Math.round(255*Math.max(0,Math.min(1,alpha)));
        if(opacity==0||sourceWidth<=0||sourceHeight<=0)return;
        // The native CapsuleView width spring is independent of our body.
        // Fitting that animated wrapper repeatedly shrinks and releases its
        // otherwise stationary icon/text. Fit the live content itself once in
        // the presented body's coordinate space; retain its internal layout.
        boolean retained=item!=null&&item!=source;
        float contentWidth=retained?item.getWidth():sourceWidth;
        float contentHeight=retained?item.getHeight():sourceHeight;
        if(contentWidth<=0||contentHeight<=0)return;
        CloudContentTransform fit=CloudContentTransform.fit(contentWidth,contentHeight,0,0,width,height);
        int save=opacity==255?canvas.save():canvas.saveLayerAlpha(0,0,width,height,opacity);
        canvas.translate(fit.x()+offset,fit.y());canvas.scale(fit.scale(),fit.scale());
        if(retained)item.draw(canvas);
        else if(source instanceof ViewGroup mini)drawMiniContent(canvas,mini);
        canvas.restoreToCount(save);
    }
    static void moonBounds(View moon,float width,float height,float gap,boolean right,android.graphics.RectF bounds) {
        float scale=Math.min(1,height/Math.max(1,moon.getHeight()));
        float w=moon.getWidth()*scale,h=moon.getHeight()*scale;
        float x=right?width+gap:-gap-w,y=(height-h)/2f;
        bounds.set(x,y,x+w,y+h);
    }
    static void drawMoon(Canvas canvas,View moon,android.graphics.RectF bounds,float alpha) {
        int opacity=Math.round(255*Math.max(0,Math.min(1,alpha)));
        if(opacity==0||moon==null||moon.getWidth()<=0||moon.getHeight()<=0)return;
        int save=opacity==255?canvas.save():canvas.saveLayerAlpha(null,opacity);
        canvas.translate(bounds.left,bounds.top);
        canvas.scale(bounds.width()/moon.getWidth(),bounds.height()/moon.getHeight());
        // ImageView supplies the native crescent artwork. Its scene wrapper
        // matrix/alpha are replaced by edge attachment and one owned fade.
        moon.draw(canvas);canvas.restoreToCount(save);
    }
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
        draw(canvas,container,anchor,width,height,alpha,accessories,capsule,content,ownAnchorMotion,false);
    }
    static void draw(Canvas canvas,ViewGroup container,View anchor,float width,float height,float alpha,
            boolean accessories,Predicate<View> capsule,Function<View,View> content,boolean ownAnchorMotion,boolean ownContentMotion) {
        draw(canvas,container,anchor,width,height,alpha,accessories,capsule,content,ownAnchorMotion,ownContentMotion,null);
    }
    static void draw(Canvas canvas,ViewGroup container,View anchor,float width,float height,float alpha,
            boolean accessories,Predicate<View> capsule,Function<View,View> content,boolean ownAnchorMotion,boolean ownContentMotion,View miniView) {
        if(alpha<=0||anchor.getWidth()<=0||anchor.getHeight()<=0)return;
        CloudContentTransform fit=placement(container,anchor,width,height);
        // Accessories deliberately have no anchor-bounded layer: native moons
        // sit outside the mini view, and the original parent does not clip them.
        int opacity=Math.round(255*Math.min(1,alpha));
        if(opacity==0)return;
        int save=opacity==255?canvas.save():canvas.saveLayerAlpha(null,opacity);
        canvas.translate(fit.x(),fit.y());canvas.scale(fit.scale(),fit.scale());
        for(int i=0;i<container.getChildCount();i++) {
            View child=container.getChildAt(i);
            boolean capsuleChild=capsule.test(child);
            boolean miniChild=child==miniView;
            // An explicit content revision replaces the old native side slide
            // with a centered blur-in. Other capsule items are the outgoing
            // native switch tree, not another copy of the incoming content.
            if(ownContentMotion&&(capsuleChild||miniChild)&&child!=anchor)continue;
            boolean owned=(ownAnchorMotion||ownContentMotion)&&child==anchor;
            if(!owned&&(child.getVisibility()!=View.VISIBLE||child.getAlpha()<=0))continue;
            View item=capsuleChild?content.apply(child):null;
            boolean accessory=!capsuleChild&&!miniChild&&child!=anchor;
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
            }else if(!capsuleChild) {
                if((child==anchor||miniChild)&&child instanceof ViewGroup mini)drawMiniContent(canvas,mini);
                else child.draw(canvas);
            }
            canvas.restoreToCount(childSave);
        }
        canvas.restoreToCount(save);
    }
    private static void drawMiniContent(Canvas canvas,ViewGroup mini) {
        // Its XML background has a fixed 20dp G2 radius. The independent body
        // already supplies the fill and clipping, so only the live mini content
        // belongs here; drawing mini itself would put the old silhouette back.
        for(int i=0;i<mini.getChildCount();i++) {
            View child=mini.getChildAt(i);
            if(child.getVisibility()!=View.VISIBLE||child.getAlpha()<=0)continue;
            int save=canvas.save();
            canvas.translate(child.getLeft()-mini.getScrollX(),child.getTop()-mini.getScrollY());
            canvas.concat(child.getMatrix());
            if(child.getAlpha()<1)canvas.saveLayerAlpha(null,Math.round(255*child.getAlpha()));
            child.draw(canvas);canvas.restoreToCount(save);
        }
    }
}
