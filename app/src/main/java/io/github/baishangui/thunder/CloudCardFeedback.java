package io.github.baishangui.thunder;

import android.graphics.Canvas;
import android.view.View;

/** Native card feedback shared by the independent visual and input silhouette. */
final class CloudCardFeedback {
    static void transform(View shape,View card,float x,float y,boolean follow) {
        boolean active=follow&&card!=null&&card.getWidth()>0&&card.getHeight()>0;
        shape.setPivotX(active?card.getPivotX()*shape.getWidth()/card.getWidth():shape.getWidth()/2f);
        shape.setPivotY(active?card.getPivotY()*shape.getHeight()/card.getHeight():shape.getHeight()/2f);
        shape.setScaleX(active?card.getScaleX():1f);shape.setScaleY(active?card.getScaleY():1f);
        shape.setTranslationX(x+(active?card.getTranslationX():0));
        shape.setTranslationY(y+(active?card.getTranslationY():0));
    }
    static Object press(View card,View owner,boolean instant) {
        if(card==null)return null;
        Object press=Reflect.getObjectField(instant?owner:card,instant?"r":"f");
        prepare(press,card.getWidth(),card.getHeight());return press;
    }
    static void prepare(Object press,int width,int height) {
        // InstantContainer normally does this in dispatchDraw, which is skipped
        // when the independent surface hides that container. DOWN needs bounds too.
        if(press!=null&&width>0&&height>0)Reflect.callMethod(press,"g",width,height);
    }
    static void draw(Canvas canvas,Object press,int sourceWidth,int sourceHeight,int width,int height) {
        if(press==null||sourceWidth<=0||sourceHeight<=0||width<=0||height<=0)return;
        int save=canvas.save();
        try {
            prepare(press,sourceWidth,sourceHeight);
            canvas.scale((float)width/sourceWidth,(float)height/sourceHeight);
            // Reuse k.b(), including its native animated spotlight and corner hook.
            // Bounds and pointer state remain owned by the host; never recreate its shader.
            Reflect.callMethod(press,"b",canvas,sourceWidth,sourceHeight);
        }finally{canvas.restoreToCount(save);}
    }
}
