package io.github.baishangui.thunder;

import android.graphics.Canvas;
import android.graphics.RecordingCanvas;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import java.util.function.Consumer;

/** Effects belong only to this independent display list, never to a live host View. */
final class CloudContentLayer {
    private static final int STEPS=32;
    private final RenderNode node;
    private final RenderEffect[] effects=new RenderEffect[STEPS+1];
    private float effectRadius=-1;

    CloudContentLayer(String name){node=new RenderNode(name);node.setClipToBounds(false);}
    void draw(Canvas canvas,int width,int height,float alpha,float blur,float maxRadius,Consumer<Canvas> content) {
        draw(canvas,width,height,0,alpha,blur,maxRadius,content);
    }
    void draw(Canvas canvas,int width,int height,int outset,float alpha,float blur,float maxRadius,Consumer<Canvas> content) {
        if(alpha<=0||width<=0||height<=0)return;
        float opacity=Math.max(0,Math.min(1,alpha));
        int step=Math.max(0,Math.min(STEPS,Math.round(blur*STEPS)));
        if(step==0||!canvas.isHardwareAccelerated()) {
            if(opacity==1f){content.accept(canvas);return;}
            int save=canvas.saveLayerAlpha(null,Math.round(255*opacity));
            content.accept(canvas);canvas.restoreToCount(save);return;
        }
        int padding=Math.max(0,outset);
        node.setPosition(-padding,-padding,width+padding,height+padding);
        RecordingCanvas recording=node.beginRecording(width+padding*2,height+padding*2);
        try{recording.translate(padding,padding);content.accept(recording);}finally{node.endRecording();}
        if(effectRadius!=maxRadius) {
            java.util.Arrays.fill(effects,null);effectRadius=maxRadius;
        }
        if(step>0&&effects[step]==null) {
            float radius=Math.max(.01f,maxRadius*step/STEPS);
            effects[step]=RenderEffect.createBlurEffect(radius,radius,Shader.TileMode.CLAMP);
        }
        node.setRenderEffect(step==0?null:effects[step]);node.setAlpha(opacity);
        canvas.drawRenderNode(node);
    }
    void clear(){node.discardDisplayList();java.util.Arrays.fill(effects,null);}
}
