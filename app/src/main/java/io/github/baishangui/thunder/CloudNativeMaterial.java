package io.github.baishangui.thunder;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.DrawableWrapper;
import android.graphics.drawable.LayerDrawable;

/** C17 native material snapshots: read defaults once, keep the independent blur host throughout motion. */
final class CloudNativeMaterial {
    private final Object manager,params;
    final Drawable blur;
    private float[] capsule,card,mixed;
    private int capsuleRadius,cardRadius;
    private float applied=Float.NaN;

    CloudNativeMaterial(Object manager,ClassLoader loader) {
        this.manager=manager;
        blur=(Drawable)Reflect.callMethod(manager,"getBackgroundBlurDrawable");
        if(blur==null)throw new IllegalStateException("Independent native blur drawable missing");
        params=Reflect.newInstance(Reflect.findClass("com.oplus.graphics.OplusBlurParam",loader));
    }
    void capture(Drawable capsuleBackground,boolean capsuleBlur) {
        card=snapshot(blur);cardRadius=Reflect.getIntField(blur,"mBlurRadius");
        Drawable original=capsuleBlur?findBlur(capsuleBackground):null;
        capsule=original==null?card.clone():snapshot(original);
        capsuleRadius=original==null?0:Reflect.getIntField(original,"mBlurRadius");
        mixed=new float[card.length];applied=Float.NaN;
    }
    void apply(float progress,boolean capsuleBlur) {
        float p=Math.max(0,Math.min(1,progress));if(p==applied||card==null)return;applied=p;
        CloudMaterialBlend.mix(capsule,card,p,mixed);
        Reflect.callMethod(params,"fromFloatArray",mixed);
        // The folded capsule uses the host's circular end caps; expanded cards
        // retain its G2 shape. Material and outline must make the same choice.
        Reflect.callMethod(params,"setSmoothCornerType",p>.001f?1:0);
        Reflect.callMethod(params,"setSmoothCornerWeight",p>.001f?CloudCornerGeometry.WEIGHT:0f);
        Reflect.callMethod(manager,"setBlurParams",params);
        Reflect.callMethod(manager,"setBlurRadius",Math.round(capsuleRadius+(cardRadius-capsuleRadius)*p));
        blur.setAlpha(Math.round(255*(capsuleBlur?1:p)));
    }
    private static float[] snapshot(Drawable drawable) {
        Object extension=Reflect.getObjectField(drawable,"mBackgroundBlurDrawableExtImpl");
        Object params=Reflect.getObjectField(extension,"mBlurParams");
        return ((float[])Reflect.callMethod(params,"toFloatArray")).clone();
    }
    private static Drawable findBlur(Drawable drawable) {
        if(drawable==null)return null;
        if(drawable.getClass().getName().equals("com.android.internal.graphics.drawable.BackgroundBlurDrawable"))return drawable;
        if(drawable instanceof LayerDrawable layers)for(int i=0;i<layers.getNumberOfLayers();i++) {
            Drawable result=findBlur(layers.getDrawable(i));if(result!=null)return result;
        }
        if(drawable instanceof DrawableWrapper wrapper)return findBlur(wrapper.getDrawable());
        return null;
    }
}
