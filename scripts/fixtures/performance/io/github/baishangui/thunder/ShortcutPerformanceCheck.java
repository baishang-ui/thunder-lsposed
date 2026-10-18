package io.github.baishangui.thunder;

import android.graphics.Canvas;
import android.view.View;
import com.oplus.systemui.volume.utils.material.OplusVolumeShapePathHelper;

/** Cache geometry only: native touch, policy and lifecycle must still run. */
public final class ShortcutPerformanceCheck {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception {
        View view=new View();view.actualWidth=180;view.actualHeight=60;
        var previous=view.getForeground();
        var feedback=VolumeShortcutFeedback.attach(view,ShortcutPerformanceCheck.class.getClassLoader());
        var effect=(com.coui.appcompat.spotlight.COUISpotLightEffectDrawable)feedback.getDrawable();
        Canvas canvas=new Canvas();
        feedback.update(30,true);
        int before=OplusVolumeShapePathHelper.builds;
        for(int i=0;i<120;i++){feedback.update(30,true);feedback.draw(canvas);}
        check(OplusVolumeShapePathHelper.builds-before==1,"stationary feedback builds one native clip for 120 frames");
        view.actualWidth=200;feedback.draw(canvas);
        check(OplusVolumeShapePathHelper.builds-before==2,"resizing rebuilds clip");
        feedback.update(24,true);feedback.draw(canvas);
        check(OplusVolumeShapePathHelper.builds-before==3,"corner change rebuilds clip");
        var path=(android.graphics.Path)Reflect.getObjectField(feedback,"clip");
        check(path.radius==24&&path.bounds.right==200&&path.smooth,"cached shape uses current native geometry");
        view.touch.onTouch(view,new android.view.MotionEvent(0,10,10));
        check(effect.touches==1,"native touch still receives press");
        feedback.update(24,false);feedback.draw(canvas);
        check(!effect.enabled&&effect.cancelled,"theme policy immediately disables and cancels feedback");
        feedback.update(24,true);feedback.onViewDetachedFromWindow(view);
        check(effect.getCallback()==null,"detach releases native callbacks");
        feedback.onViewAttachedToWindow(view);feedback.update(24,true);feedback.draw(canvas);
        check(effect.enabled,"reattach restores feedback");
        feedback.release();
        check(view.getForeground()==previous&&view.touch==null&&view.attachListeners.isEmpty(),"release restores foreground and listeners");
        System.out.println("PASS: stationary feedback geometry cache, resize, corner, touch, theme, detach and release");
    }
}
