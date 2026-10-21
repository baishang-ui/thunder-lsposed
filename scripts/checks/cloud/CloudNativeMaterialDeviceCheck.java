package io.github.baishangui.thunder;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

/** Exercises the production adapter against the phone's actual OplusBlurParam codec. */
public final class CloudNativeMaterialDeviceCheck {
    static final class Extension {Object mBlurParams;Extension(Object params){mBlurParams=params;}}
    static final class Blur extends Drawable {
        final Extension mBackgroundBlurDrawableExtImpl;
        int mBlurRadius=60,alpha=255;
        Blur(Object params){mBackgroundBlurDrawableExtImpl=new Extension(params);}
        @Override public void draw(Canvas canvas) { }
        @Override public void setAlpha(int alpha){this.alpha=alpha;}
        @Override public void setColorFilter(ColorFilter filter) { }
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
    static final class Manager {
        final Blur blur;Object applied;int radius;
        Manager(Blur blur){this.blur=blur;}
        Drawable getBackgroundBlurDrawable(){return blur;}
        void setBlurParams(Object params){applied=params;}
        void setBlurRadius(int radius){this.radius=radius;}
    }
    static void close(float expected,float actual){if(Math.abs(expected-actual)>.001f)throw new AssertionError(expected+" != "+actual);}
    public static void main(String[] args) {
        ClassLoader loader=CloudNativeMaterialDeviceCheck.class.getClassLoader();
        Object params=Reflect.newInstance(Reflect.findClass("com.oplus.graphics.OplusBlurParam",loader));
        Reflect.callMethod(params,"setBlurType",2);
        Reflect.callMethod(params,"setSmoothCornerType",1);
        Reflect.callMethod(params,"setSmoothCornerWeight",3f);
        Reflect.callMethod(params,"setMaterialParams",1,new float[]{.1f,.2f,.3f,.4f},new float[]{.5f,.6f,.7f,.8f});
        float[] original=(float[])Reflect.callMethod(params,"toFloatArray");
        Manager manager=new Manager(new Blur(params));
        CloudNativeMaterial material=new CloudNativeMaterial(manager,loader);material.capture(null,false);
        material.apply(0,false);close(0,manager.radius);close(0,manager.blur.alpha);
        Object round=Reflect.newInstance(params.getClass());
        Reflect.callMethod(round,"fromFloatArray",(Object)original.clone());
        Reflect.callMethod(round,"setSmoothCornerType",0);
        Reflect.callMethod(round,"setSmoothCornerWeight",0f);
        float[] expectedRound=(float[])Reflect.callMethod(round,"toFloatArray");
        float[] actualRound=(float[])Reflect.callMethod(manager.applied,"toFloatArray");
        for(int i=0;i<expectedRound.length;i++)close(expectedRound[i],actualRound[i]);
        material.apply(.5f,false);close(30,manager.radius);close(128,manager.blur.alpha);
        material.apply(1,false);close(60,manager.radius);close(255,manager.blur.alpha);
        float[] actual=(float[])Reflect.callMethod(manager.applied,"toFloatArray");
        for(int i=0;i<original.length;i++)close(original[i],actual[i]);
        System.out.println("CloudNativeMaterialDeviceCheck: native codec/radius/alpha checks passed");
    }
}
