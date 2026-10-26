package io.github.baishangui.thunder;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.DrawableWrapper;
import android.graphics.drawable.LayerDrawable;

/** C17 native material snapshots: read defaults once, keep the independent blur host throughout motion. */
final class CloudNativeMaterial {
    private final Object manager,params;
    final Drawable blur;
    private final float roundWeight;
    private float[] capsule,card,mixed;
    private int capsuleRadius,cardRadius;
    private float applied=Float.NaN;
    private boolean appliedCapsuleBlur;
    private float opacity=1f,formAlpha=1f;
    private int appliedAlpha=-1;

    CloudNativeMaterial(Object manager,ClassLoader loader) {
        this.manager=manager;
        blur=(Drawable)Reflect.callMethod(manager,"getBackgroundBlurDrawable");
        if(blur==null)throw new IllegalStateException("Independent native blur drawable missing");
        params=Reflect.newInstance(Reflect.findClass("com.oplus.graphics.OplusBlurParam",loader));
        roundWeight=CloudCornerGeometry.nonSmoothWeight(loader);
    }
    void capture(Drawable capsuleBackground,boolean capsuleBlur) {
        card=snapshot(blur);cardRadius=Reflect.getIntField(blur,"mBlurRadius");
        Drawable original=capsuleBlur?findBlur(capsuleBackground):null;
        capsule=original==null?card.clone():snapshot(original);
        capsuleRadius=original==null?0:Reflect.getIntField(original,"mBlurRadius");
        mixed=new float[card.length];applied=Float.NaN;appliedAlpha=-1;
    }
    void apply(float progress,boolean capsuleBlur) {
        float p=Math.max(0,Math.min(1,progress));if(card==null||p==applied&&capsuleBlur==appliedCapsuleBlur)return;
        applied=p;appliedCapsuleBlur=capsuleBlur;
        CloudMaterialBlend.mix(capsule,card,p,mixed);
        Reflect.callMethod(params,"fromFloatArray",mixed);
        // The folded capsule uses the host's circular end caps; expanded cards
        // retain its G2 shape. Material and outline must make the same choice.
        Reflect.callMethod(params,"setSmoothCornerType",p>.001f?1:0);
        // OplusBlurParam rejects zero instead of clearing the previous G2 weight.
        Reflect.callMethod(params,"setSmoothCornerWeight",p>.001f?CloudCornerGeometry.WEIGHT:roundWeight);
        Reflect.callMethod(manager,"setBlurParams",params);
        Reflect.callMethod(manager,"setBlurRadius",Math.round(capsuleRadius+(cardRadius-capsuleRadius)*p));
        formAlpha=capsuleBlur?1f:CloudCardTiming.opacity(p);applyAlpha();
    }
    /** Compositor blur regions do not inherit the containing View's alpha. */
    void opacity(float value) {
        opacity=Math.max(0,Math.min(1,value));applyAlpha();
    }
    private void applyAlpha() {
        int alpha=Math.round(255*formAlpha*opacity);
        if(alpha!=appliedAlpha){appliedAlpha=alpha;blur.setAlpha(alpha);}
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
