package io.github.baishangui.thunder;

import android.graphics.drawable.Drawable;
import com.oplus.graphics.OplusBlurParam;

/** Actual production adapter with the vendor's rejecting setter and 18-slot codec. */
public final class CloudNativeMaterialCheck {
    static final class Extension {Object mBlurParams;Extension(Object params){mBlurParams=params;}}
    static final class Blur extends Drawable {
        final Extension mBackgroundBlurDrawableExtImpl;
        int mBlurRadius=60,alpha=255;
        Blur(Object params){mBackgroundBlurDrawableExtImpl=new Extension(params);}
        @Override public void setAlpha(int value){alpha=value;}
    }
    static final class Manager {
        final Blur blur;OplusBlurParam applied;int radius;
        Manager(Blur drawable){blur=drawable;}
        Drawable getBackgroundBlurDrawable(){return blur;}
        void setBlurParams(Object value){applied=(OplusBlurParam)value;}
        void setBlurRadius(int value){radius=value;}
    }
    static void close(float expected,float actual){if(Math.abs(expected-actual)>.0001f)throw new AssertionError(expected+" != "+actual);}
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static void assertRound(Manager manager,float[] original){
        check(manager.applied.getSmoothCornerType()==0,"folded material must be native ROUND");
        close(2,manager.applied.getSmoothCornerWeight());
        float[] actual=manager.applied.toFloatArray();close(2,actual[16]);close(0,actual[17]);
        for(int i=0;i<16;i++)close(original[i],actual[i]);
        check(manager.radius==0&&manager.blur.alpha==0,"folded no-blur capsule restores its radius and alpha");
    }
    public static void main(String[] args){
        OplusBlurParam params=new OplusBlurParam();params.setSmoothCornerWeight(3);params.setSmoothCornerWeight(0);
        close(3,params.getSmoothCornerWeight());
        float[] original=new float[18];original[0]=2;original[1]=1;original[2]=.8f;original[3]=1;
        for(int i=4;i<16;i++)original[i]=i/100f;
        original[16]=3;original[17]=1;params.fromFloatArray(original);
        int rejectedBefore=OplusBlurParam.rejectedWeights;
        Manager manager=new Manager(new Blur(params));
        CloudNativeMaterial material=new CloudNativeMaterial(manager,CloudNativeMaterialCheck.class.getClassLoader());
        material.capture(null,false);
        material.apply(1,false);close(3,manager.applied.getSmoothCornerWeight());
        check(manager.applied.getSmoothCornerType()==1,"expanded material retains native G2");
        material.apply(0,false);assertRound(manager,original);
        material.apply(.5f,false);check(manager.radius==30&&manager.blur.alpha==128,"intermediate blur radius and alpha still interpolate");
        material.apply(1,false);
        float[] card=manager.applied.toFloatArray();
        for(int i=0;i<18;i++)close(original[i],card[i]);
        material.apply(0,false);assertRound(manager,original);
        material.apply(1,false);material.opacity(.3f);
        check(manager.blur.alpha==77,"native compositor plate follows the follower's actual opacity");
        material.apply(1,false);
        check(manager.blur.alpha==77,"cached material progress must not restore a fading plate to full opacity");
        material.opacity(0);
        check(manager.blur.alpha==0,"a gathered or invisible follower cannot retain a native blur region");
        material.apply(1,false);check(manager.blur.alpha==0,"a hidden follower stays transparent during late native updates");
        material.opacity(1);material.apply(.1f,false);
        check(manager.blur.alpha==Math.round(255*CloudCardTiming.opacity(.1f)),"card plate and card content must share the same closing fade");
        material.apply(0,true);check(manager.blur.alpha==255,"configured capsule blur remains at its folded endpoint");
        material.opacity(0);check(manager.blur.alpha==0,"hiding the surface also hides configured capsule blur");
        material.opacity(1);material.apply(0,false);assertRound(manager,original);
        material.apply(1,false);material.opacity(.25f);material.capture(null,false);manager.blur.setAlpha(255);material.apply(1,false);
        check(manager.blur.alpha==64,"material recapture reapplies visibility even after a native factory reset");
        material.opacity(1);material.apply(0,false);assertRound(manager,original);
        check(OplusBlurParam.rejectedWeights==rejectedBefore,"production must never send an invalid zero smooth weight");
        System.out.println("CloudNativeMaterialCheck: rejecting codec and card-to-capsule ROUND weight 3 -> 2 checks passed");
    }
}
