package io.github.baishangui.thunder;

import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.DrawableWrapper;
import android.view.MotionEvent;
import android.view.View;
import java.lang.reflect.Method;

/** The expanded seekbar spotlight, using its native drawable, touch animation and shape. */
final class VolumeShortcutFeedback extends DrawableWrapper implements View.OnAttachStateChangeListener {
    private static final String SPOT="com.oplus.systemui.volume.utils.spotlight.";
    private final View host;
    private final Drawable previous;
    private final Path clip=new Path();
    private final Rect bounds=new Rect();
    private final Method shape,gate;
    private float corner;
    private float clipCorner=Float.NaN;
    private boolean enabled;

    static VolumeShortcutFeedback attach(View host,ClassLoader loader) throws ReflectiveOperationException {
        Drawable effect=(Drawable)Reflect.newInstance(Reflect.findClass("com.coui.appcompat.spotlight.COUISpotLightEffectDrawable",loader));
        Class<?> target=Reflect.findClass(SPOT+"VolumeSpotLightTarget",loader);
        Reflect.findClass(SPOT+"OplusVolumeSpotLightEffectParams",loader).getMethod("apply",target,android.content.Context.class,effect.getClass()).invoke(null,target.getField("SEEK_BAR_PANEL").get(null),host.getContext(),effect);
        return new VolumeShortcutFeedback(host,effect,loader);
    }

    private VolumeShortcutFeedback(View host,Drawable effect,ClassLoader loader) throws ReflectiveOperationException {
        super(effect);this.host=host;previous=host.getForeground();
        shape=Reflect.findClass("com.oplus.systemui.volume.utils.material.OplusVolumeShapePathHelper",loader)
                .getMethod("buildClipPath",Path.class,Rect.class,float.class,float.class,boolean.class);
        gate=Reflect.findClass("com.oplus.systemui.volume.utils.effect.OplusVolumeSpotLightGate",loader).getMethod("isVolumeSpotLightEnabled",android.content.Context.class,boolean.class);
        host.setForeground(this);host.addOnAttachStateChangeListener(this);
        host.setOnTouchListener((v,event)->{touch(event);return false;});
    }

    void update(float corner,boolean defaultTheme) {
        this.corner=corner;
        boolean active=defaultTheme&&host.isEnabled()&&host.isAttachedToWindow()
                &&spotlightAllowed(defaultTheme);
        if(active!=enabled) {
            enabled=active;Reflect.callMethod(getDrawable(),"setEnabled",active);
            if(!active)reset();
        }
    }

    private boolean spotlightAllowed(boolean theme) {
        try {return (Boolean)gate.invoke(null,host.getContext(),theme);}
        catch(ReflectiveOperationException error){throw new IllegalStateException("Volume spotlight policy",error);}
    }

    private void touch(MotionEvent event) {
        if(!host.isEnabled())return;
        if(enabled) {
            getDrawable().setBounds(0,0,host.getWidth(),host.getHeight());
            Reflect.callMethod(getDrawable(),"onTouchEvent",event);
            invalidateSelf();
        }
    }

    @Override public void draw(Canvas canvas) {
        if(previous!=null){previous.setBounds(getBounds());previous.draw(canvas);}
        if(!enabled)return;
        int width=host.getWidth(),height=host.getHeight();
        if(width<=0||height<=0)return;
        if(bounds.right!=width||bounds.bottom!=height||clipCorner!=corner) {
            bounds.left=0;bounds.top=0;bounds.right=width;bounds.bottom=height;
            try {shape.invoke(null,clip,bounds,corner,3f,true);clipCorner=corner;}
            catch(ReflectiveOperationException error){throw new IllegalStateException("Volume shortcut shape",error);}
        }
        int saved=canvas.save();
        try {canvas.clipPath(clip);getDrawable().setBounds(bounds);getDrawable().draw(canvas);}
        finally {canvas.restoreToCount(saved);}
    }

    private void reset() {Reflect.callMethod(getDrawable(),"cancel");}
    void release() {
        reset();host.setOnTouchListener(null);host.removeOnAttachStateChangeListener(this);
        if(host.getForeground()==this)host.setForeground(previous);
        getDrawable().setCallback(null);setCallback(null);
    }
    @Override public void onViewAttachedToWindow(View view){getDrawable().setCallback(this);}
    @Override public void onViewDetachedFromWindow(View view){reset();enabled=false;getDrawable().setCallback(null);}
}
