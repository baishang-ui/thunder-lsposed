package io.github.baishangui.thunder;

import android.graphics.Canvas;
import android.graphics.Matrix;
import android.view.View;

/** Native card feedback shared by the independent visual and input silhouette. */
final class CloudCardFeedback {
    static final class Transform {
        private final Matrix matrix=new Matrix();
        private final float[] point=new float[2];
        private View anchored;
        private float originX,originY;
        void anchor(View card) {
            anchored=card;
            if(card!=null){origin(card);originX=point[0];originY=point[1];}
        }
        private void origin(View card) {
            matrix.reset();
            if(card.getParent() instanceof View parent) {
                parent.transformMatrixToGlobal(matrix);
                point[0]=card.getLeft()-parent.getScrollX();point[1]=card.getTop()-parent.getScrollY();
                matrix.mapPoints(point);
            }else {
                card.transformMatrixToGlobal(matrix);point[0]=point[1]=0;matrix.mapPoints(point);
                point[0]-=card.getTranslationX()+card.getPivotX()*(1-card.getScaleX());
                point[1]-=card.getTranslationY()+card.getPivotY()*(1-card.getScaleY());
            }
        }
        void apply(View shape,View card,float x,float y,boolean follow) {
            if(anchored!=card)anchor(card);
            if(follow&&card!=null) {
                origin(card);x+=point[0]-originX;y+=point[1]-originY;
            }
            transform(shape,card,x,y,follow);
        }
    }
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
