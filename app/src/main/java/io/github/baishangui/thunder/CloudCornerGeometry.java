package io.github.baishangui.thunder;

import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;

/** One native G2 curve for custom flow-card content, material and edge layers. */
final class CloudCornerGeometry {
    static final float WEIGHT=3f;
    static boolean enabled() {
        HookConfig c=ShadeHook.snapshot();return c.enabled&&c.cloud&&c.cloudCardArea.cornerEnabled;
    }
    static Object stroke(Object original,float radius) {
        Object type=Reflect.callMethod(original,"getType");
        for(Object value:((Enum<?>)type).getDeclaringClass().getEnumConstants())
            if("G2".equals(((Enum<?>)value).name())) {
                ShadeHook.resourceHit("cloud-g2-stroke");return Reflect.newInstance(original.getClass(),value,radius,WEIGHT);
            }
        throw new IllegalStateException("Native G2 stroke type unavailable");
    }
    static void path(Path path,RectF bounds,float top,float bottom,ClassLoader loader) {
        Object ref=Reflect.getAdditionalInstanceField(path,"thunder.cloud-g2-adapter");
        Object adapter=ref instanceof java.lang.ref.WeakReference<?>?((java.lang.ref.WeakReference<?>)ref).get():null;
        if(adapter==null) {
            adapter=Reflect.newInstance(Reflect.findClass("com.oplus.graphics.OplusPathAdapter",loader),path,1);
            Reflect.setAdditionalInstanceField(path,"thunder.cloud-g2-adapter",new java.lang.ref.WeakReference<>(adapter));
        }
        ShadeHook.resourceHit("cloud-g2-path");path.rewind();
        Reflect.callMethod(adapter,"addSmoothRoundRect",bounds,new float[]{top,top,top,top,bottom,bottom,bottom,bottom},Path.Direction.CW,WEIGHT);
    }
    static final class Gradient {
        final java.lang.ref.WeakReference<Object> extension;
        final boolean smooth;
        final float weight;
        final int type;
        Gradient(GradientDrawable drawable) {
            Object nativeExtension=Reflect.getObjectField(drawable,"mGradientDrawableExt");
            extension=new java.lang.ref.WeakReference<>(nativeExtension);
            smooth=(Boolean)Reflect.callMethod(nativeExtension,"getSmoothRoundStyle");
            weight=((Number)Reflect.callMethod(nativeExtension,"getSmoothWeight")).floatValue();
            type=((Number)Reflect.callMethod(nativeExtension,"getGradientStateSmoothWeightStyle")).intValue();
        }
        // GradientDrawable's vendor enum uses 1 for G2; the plugin Triple uses 2.
        void apply(){Object value=extension.get();if(value!=null)Reflect.callMethod(value,"setSmoothRoundStyle",true,WEIGHT,1);}
        void restore(){Object value=extension.get();if(value!=null)Reflect.callMethod(value,"setSmoothRoundStyle",smooth,weight,type);}
    }
}
