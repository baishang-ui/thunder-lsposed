package io.github.baishangui.thunder;

import android.content.res.ColorStateList;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;

/** Runs the actual production copier and geometry against native Drawable contracts. */
public final class CloudNativeDecorationCheck {
    private static int checks;
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
    private static void close(float expected,float actual,String message){check(Math.abs(expected-actual)<.001f,message+": "+actual);}
    private static void geometry(CloudNativeDecoration decoration,int width,int height,float radius,boolean smooth){
        Reflect.callMethod(decoration,"geometry",width,height,radius,smooth);
    }
    public static void main(String[] args)throws Throwable {
        CloudHook.install(CloudNativeDecorationCheck.class.getClassLoader());
        GradientDrawable source=new GradientDrawable();source.setCornerRadius(30);source.setBounds(8,12,208,92);
        source.mGradientDrawableExt.setSmoothRoundStyle(true,3,1);
        source.setColors(new int[]{0xff123456,0xff456789});source.setOrientation(GradientDrawable.Orientation.BL_TR);
        source.setStroke(2,ColorStateList.valueOf(0xff6789ab),4,6);
        CloudNativeDecoration decoration=new CloudNativeDecoration(source,null,null);
        GradientDrawable copy=(GradientDrawable)decoration.drawable;
        check(copy!=source,"native background copy has independent identity");
        close(30,copy.getCornerRadius(),"copy preserves the native radius before a frame is supplied");
        check(copy.mGradientState.smooth,"copy preserves the initial native smoothing");
        GradientDrawable nativeFill=new GradientDrawable();
        check(nativeFill.mGradientState.mStrokeWidth==-1,"native no-stroke fill uses its real negative sentinel");
        CloudNativeDecoration noStroke=new CloudNativeDecoration(nativeFill,null,null);
        check(((GradientDrawable)noStroke.drawable).mGradientState.mStrokeWidth==0,"native no-stroke sentinel becomes a valid zero-width stroke on the independent fill");
        geometry(decoration,600,80,128,false);
        close(40,copy.getCornerRadius(),"frame saturates the native background radius at half height");
        check(!copy.mGradientState.smooth,"capsule frame selects native ROUND");
        check(copy.getBounds().left==0&&copy.getBounds().right==600&&copy.getBounds().bottom==80,"copy uses the current independent frame bounds");
        check(source.getBounds().left==8&&source.getBounds().right==208&&source.getCornerRadius()==30,"frame geometry leaves source bounds and radius intact");
        source.setColors(new int[]{0xff112233,0xff778899});source.setOrientation(GradientDrawable.Orientation.LEFT_RIGHT);
        source.setStroke(5,ColorStateList.valueOf(0xffabcdef),7,9);source.setAlpha(23);source.setBounds(0,0,10,10);
        decoration.refresh();
        check(copy.getColors()!=source.getColors()&&java.util.Arrays.equals(copy.getColors(),source.getColors()),"native gradient colors refresh without sharing the source color array");
        check(copy.getOrientation()==GradientDrawable.Orientation.LEFT_RIGHT,"native gradient orientation refreshes");
        check(copy.strokeWidth==5&&copy.strokeColor==0xffabcdef&&copy.mGradientState.mStrokeDashWidth==7&&copy.mGradientState.mStrokeDashGap==9,"live native foreground stroke data refreshes");
        close(40,copy.getCornerRadius(),"color and stroke refresh preserve current geometry");
        check(copy.getBounds().right==600&&copy.getAlpha()==255,"native width and alpha animation cannot overwrite the independent presentation");
        geometry(decoration,600,80,20,true);decoration.refresh();
        close(20,copy.getCornerRadius(),"smaller intermediate frame radius survives refresh");
        check(copy.mGradientState.smooth,"expanded frame retains native smoothing");
        geometry(decoration,600,100,128,false);close(50,copy.getCornerRadius(),"changed height updates native circular ends");

        GradientDrawable foreground=new GradientDrawable();foreground.setCornerRadius(30);foreground.setStroke(1,0x40eeeeee);
        CloudNativeDecoration edge=new CloudNativeDecoration(foreground,null,null);
        geometry(edge,600,100,128,false);
        foreground.setStroke(3,0x40333333);edge.refresh();
        GradientDrawable edgeCopy=(GradientDrawable)edge.drawable;
        close(50,edgeCopy.getCornerRadius(),"native foreground receives the same frame silhouette");
        check(edgeCopy.strokeWidth==3&&edgeCopy.strokeColor==0x40333333,"native foreground remains live after copying");
        close(30,foreground.getCornerRadius(),"foreground source geometry stays native");
        foreground.setStroke(0,0x40333333);edge.refresh();
        check(edgeCopy.strokeWidth==0,"native dark-intensity zero stroke removes the copied foreground edge");
        foreground.setStroke(4,0x40333333);edge.refresh();
        check(edgeCopy.strokeWidth==4&&edgeCopy.strokeColor>>>24==64,"native foreground stroke restoration preserves its own alpha 64");

        LayerDrawable layers=new LayerDrawable(new Drawable[]{source,foreground});
        layers.setLayerInset(0,9,11,13,15);layers.setLayerSize(0,200,80);layers.setBounds(20,30,220,110);
        CloudNativeDecoration plate=new CloudNativeDecoration(layers,null,null);
        LayerDrawable plateCopy=(LayerDrawable)plate.drawable;geometry(plate,600,100,50,false);
        check(plateCopy.getPaddingMode()==LayerDrawable.PADDING_MODE_STACK,"native layers use independent stacked bounds");
        check(plateCopy.getLayerInsetLeft(0)==0&&plateCopy.getLayerWidth(0)==-1,"native width animation insets and fixed sizes do not leak into the copy");
        close(50,((GradientDrawable)plateCopy.getDrawable(0)).getCornerRadius(),"layer fill uses current frame radius");
        close(50,((GradientDrawable)plateCopy.getDrawable(1)).getCornerRadius(),"layer foreground uses current frame radius");
        check(layers.getLayerInsetLeft(0)==9&&layers.getLayerWidth(0)==200,"copying preserves original layer metadata");

        var nativeGradient=new com.oplus.systemui.plugins.seedling.util.d(1,2,3,new kotlin.Triple(2,30f,3f));
        CloudNativeDecoration nativeDecoration=new CloudNativeDecoration(nativeGradient,null,null);
        var nativeCopy=(com.oplus.systemui.plugins.seedling.util.d)nativeDecoration.drawable;
        check(nativeCopy.d!=nativeGradient.d&&nativeCopy.d.d().equals(2),"firmware gradient gets an independent native corner tuple");
        close(30,((Number)nativeCopy.d.e()).floatValue(),"firmware gradient copy preserves native radius");
        geometry(nativeDecoration,600,80,128,false);
        close(40,((Number)nativeCopy.d.e()).floatValue(),"firmware gradient receives the actual frame radius");
        check(nativeCopy.d.d().equals(0),"firmware gradient uses native ROUND");
        nativeGradient.a=8;nativeGradient.b=9;nativeGradient.c=10;nativeDecoration.refresh();
        check(nativeCopy.a==8&&nativeCopy.b==9&&nativeCopy.c==10&&nativeCopy.boundsChanges==1,"firmware gradient refreshes its native color cache");
        close(40,((Number)nativeCopy.d.e()).floatValue(),"firmware color refresh keeps the current frame tuple");
        close(30,((Number)nativeGradient.d.e()).floatValue(),"firmware source geometry remains intact");
        check(Boolean.TRUE.equals(Reflect.getAdditionalInstanceField(nativeCopy,"thunder.cloud-capsule-decoration")),"capsule firmware copy carries its scope marker");
        check(Reflect.getAdditionalInstanceField(nativeGradient,"thunder.cloud-capsule-decoration")==null,"original card gradient stays unmarked");
        Bundle config=new Bundle();config.putBoolean("enabled",true);config.putBoolean("cloud_enabled",true);config.putBoolean("cloud_card_tint",true);config.putInt("cloud_card_color",0xff445566);CloudCheck.config(config);
        var draw=Api102Runtime.callbacks.get("com.oplus.systemui.plugins.seedling.util.d#draw");
        HookCallback.Param capsuleDraw=new HookCallback.Param();capsuleDraw.thisObject=nativeCopy;draw.before(capsuleDraw);
        check(!capsuleDraw.returnEarly,"expanded card tint cannot suppress the independent capsule's native drawing");
        HookCallback.Param cardDraw=new HookCallback.Param();cardDraw.thisObject=nativeGradient;draw.before(cardDraw);
        check(cardDraw.returnEarly,"original expanded card gradient still follows card tint suppression");

        ColorDrawable nativeColor=new ColorDrawable(0x80445566);nativeColor.setAlpha(10);
        CloudNativeDecoration color=new CloudNativeDecoration(nativeColor,null,null);
        check(((ColorDrawable)color.drawable).getColor()==0x80445566&&color.drawable.getAlpha()==255,"solid native plate copy keeps base color and independent opacity");
        nativeColor.setColor(0x80667788);color.refresh();
        check(((ColorDrawable)color.drawable).getColor()==0x80667788,"solid native color remains live");
        var blur=new com.android.internal.graphics.drawable.BackgroundBlurDrawable();
        CloudNativeDecoration blurDecoration=new CloudNativeDecoration(blur,null,null);
        check(blurDecoration.drawable instanceof ColorDrawable&&((ColorDrawable)blurDecoration.drawable).getColor()==0,"source ViewRoot blur layer is represented by the independent blur host");
        System.out.println("Verified "+checks+" actual native decoration copy, geometry and refresh checks.");
    }
}
