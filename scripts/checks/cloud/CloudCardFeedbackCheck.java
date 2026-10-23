package io.github.baishangui.thunder;

import android.graphics.Canvas;
import android.view.View;

/** Exercises production feedback with native drawing/Views replaced only at the platform boundary. */
public final class CloudCardFeedbackCheck {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static void close(float expected,float actual){check(Math.abs(expected-actual)<.001f,expected+" != "+actual);}
    static final class Press {
        int draws,width,height,boundWidth,boundHeight;
        public void g(int width,int height){boundWidth=width;boundHeight=height;}
        public void b(Canvas canvas,int width,int height){
            draws++;this.width=width;this.height=height;
            canvas.drawRoundRect(0,0,width,height,0,0,new android.graphics.Paint());
        }
    }
    static final class Card extends View {public final Press f=new Press();}
    static final class Instant extends View {public final Press r=new Press();}
    public static void main(String[] args) {
        Card card=new Card();card.actualWidth=400;card.actualHeight=200;
        View shape=new View();shape.actualWidth=200;shape.actualHeight=100;
        card.setPivotX(100);card.setPivotY(150);card.setScaleX(.96f);card.setScaleY(.98f);
        card.setTranslationX(36);card.setTranslationY(-24);
        CloudCardFeedback.transform(shape,card,80,300,true);
        close(.96f,shape.getScaleX());close(.98f,shape.getScaleY());
        close(50,shape.getPivotX());close(75,shape.getPivotY());
        close(116,shape.getTranslationX());close(276,shape.getTranslationY());
        // The host's return spring changes transforms without layout or a new pointer event.
        card.setScaleX(1);card.setScaleY(1);card.setTranslationX(0);card.setTranslationY(0);
        CloudCardFeedback.transform(shape,card,80,300,true);
        close(1,shape.getScaleX());close(1,shape.getScaleY());
        close(80,shape.getTranslationX());close(300,shape.getTranslationY());
        // A morph must not inherit the concurrently running host transition transforms.
        card.setScaleX(.2f);card.setTranslationX(500);
        CloudCardFeedback.transform(shape,card,40,200,false);
        close(1,shape.getScaleX());close(1,shape.getScaleY());
        close(100,shape.getPivotX());close(50,shape.getPivotY());
        close(40,shape.getTranslationX());close(200,shape.getTranslationY());
        CloudCardFeedback.transform(shape,null,12,24,true);
        close(1,shape.getScaleX());close(12,shape.getTranslationX());
        check(CloudCardFeedback.press(card,card,false)==card.f,"regular card lost its native spotlight");
        Instant owner=new Instant();
        check(CloudCardFeedback.press(card,owner,true)==owner.r,"instant spotlight belongs to its container");
        check(owner.r.boundWidth==400&&owner.r.boundHeight==200,"hidden native container must initialize spotlight bounds before the first DOWN");
        Canvas canvas=new Canvas();
        CloudCardFeedback.draw(canvas,card.f,400,200,200,100);
        check(card.f.draws==1&&card.f.width==400&&card.f.height==200,"must draw the original native effect in its source coordinates");
        close(200,canvas.rectangles.get(0)[2]);close(100,canvas.rectangles.get(0)[3]);
        check(canvas.depth==0,"native spotlight must not leak its canvas transform");
        CloudCardFeedback.draw(canvas,null,400,200,200,100);
        CloudCardFeedback.draw(canvas,card.f,0,200,200,100);
        check(card.f.draws==1,"unbound/zero-sized feedback must be skipped");
        View parent=new View();parent.screenX=20;parent.screenY=140;
        card.parent=parent;card.left=36;card.top=52;
        card.setScaleX(1);card.setScaleY(1);card.setTranslationX(0);card.setTranslationY(0);
        CloudCardFeedback.Transform motion=new CloudCardFeedback.Transform();motion.anchor(card);
        motion.apply(shape,card,80,300,true);
        parent.setTranslationX(45);parent.setTranslationY(90);
        motion.apply(shape,card,80,300,true);
        close(125,shape.getTranslationX());close(390,shape.getTranslationY());
        // Native RecyclerView offsets and overscroll do not require child onLayout.
        card.top-=25;parent.scrollY=11;
        motion.apply(shape,card,80,300,true);close(354,shape.getTranslationY());
        card.setTranslationX(18);card.setTranslationY(-7);
        motion.apply(shape,card,80,300,true);close(143,shape.getTranslationX());close(347,shape.getTranslationY());
        motion.anchor(card);motion.apply(shape,card,200,500,true);
        close(218,shape.getTranslationX());close(493,shape.getTranslationY());
        motion.apply(shape,card,200,500,false);close(200,shape.getTranslationX());close(500,shape.getTranslationY());
        System.out.println("CloudCardFeedbackCheck: "+checks+" native feedback checks passed");
    }
}
