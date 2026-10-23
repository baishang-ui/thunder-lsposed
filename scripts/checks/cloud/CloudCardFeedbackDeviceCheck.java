package io.github.baishangui.thunder;

import android.content.Context;
import android.graphics.Matrix;
import android.view.View;
import android.widget.FrameLayout;

/** Real Android ancestor transforms, scroll, subpixel motion and independent endpoint rebasing. */
public final class CloudCardFeedbackDeviceCheck {
    static int checks;
    private static void close(float expected,float actual) {
        checks++;if(Math.abs(expected-actual)>.002f)throw new AssertionError(expected+" != "+actual);
    }
    public static void run(Context context) {
        FrameLayout root=new FrameLayout(context),parent=new FrameLayout(context);
        View card=new View(context),shape=new View(context);
        root.addView(parent);parent.addView(card);
        root.layout(0,0,1000,2000);parent.layout(40,140,940,1400);card.layout(20,30,420,230);shape.layout(0,0,400,200);
        CloudCardFeedback.Transform motion=new CloudCardFeedback.Transform();motion.anchor(card);
        motion.apply(shape,card,80,300,true);close(80,shape.getTranslationX());close(300,shape.getTranslationY());
        // Both ancestors can move while the card's own properties stay unchanged.
        root.setTranslationX(7.125f);root.setTranslationY(13.25f);
        parent.setTranslationX(45.25f);parent.setTranslationY(90.5f);
        motion.apply(shape,card,80,300,true);close(132.375f,shape.getTranslationX());close(403.75f,shape.getTranslationY());
        parent.scrollTo(11,17);card.offsetTopAndBottom(-25);
        card.setTranslationX(18.5f);card.setTranslationY(-7.25f);
        card.setPivotX(100);card.setPivotY(150);card.setScaleX(.96f);card.setScaleY(.98f);
        motion.apply(shape,card,80,300,true);
        close(139.875f,shape.getTranslationX());close(354.5f,shape.getTranslationY());
        close(.96f,shape.getScaleX());close(.98f,shape.getScaleY());
        close(100,shape.getPivotX());close(150,shape.getPivotY());
        // Drawing and hit testing sample the same real View matrix.
        float[] hit={100,150};Matrix inverse=new Matrix();
        shape.getMatrix().mapPoints(hit);if(!shape.getMatrix().invert(inverse))throw new AssertionError("singular input matrix");
        inverse.mapPoints(hit);close(100,hit[0]);close(150,hit[1]);
        // A settings/layout rebase absorbs ancestor motion once, retaining own press feedback.
        motion.anchor(card);motion.apply(shape,card,200,500,true);
        close(218.5f,shape.getTranslationX());close(492.75f,shape.getTranslationY());
        parent.setTranslationY(120.75f);motion.apply(shape,card,200,500,true);close(523,shape.getTranslationY());
        motion.apply(shape,card,200,500,false);close(200,shape.getTranslationX());close(500,shape.getTranslationY());close(1,shape.getScaleX());
        // Rebinding a new native card cannot inherit the previous card's displacement.
        View replacement=new View(context);parent.addView(replacement);replacement.layout(0,0,400,200);
        motion.apply(shape,replacement,100,200,true);close(100,shape.getTranslationX());close(200,shape.getTranslationY());
    }
}
