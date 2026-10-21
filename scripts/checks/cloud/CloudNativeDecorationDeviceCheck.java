package io.github.baishangui.thunder;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.Drawable;

/** Runs with Android graphics: host drawable animation must not deform the independent fill. */
public final class CloudNativeDecorationDeviceCheck {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args) throws Exception {
        GradientDrawable source=new GradientDrawable();source.setColor(0x8045789a);source.setCornerRadius(30);
        source.setBounds(30,0,130,40);source.setAlpha(0);
        CloudNativeDecoration decoration=new CloudNativeDecoration(source,null,null);
        GradientDrawable copy=(GradientDrawable)decoration.drawable;
        check(copy!=source,"must have a separate drawable");
        check(copy.getAlpha()==255,"native fade alpha must not hide the independent decoration");
        check(copy.getColor().getDefaultColor()==0x8045789a,"keep the application's color and color alpha");
        check(copy.getCornerRadius()==0,"only the shape outline clips the fill");
        copy.setBounds(0,0,300,160);source.setBounds(55,0,85,40);source.setAlpha(19);
        check(copy.getBounds().right==300,"native width animator must not edit the copied bounds");
        source.setColor(Color.RED);decoration.refresh();
        check(copy.getColor().getDefaultColor()==Color.RED,"same-drawable app color updates must stay live");
        check(copy.getBounds().right==300&&copy.getAlpha()==255,"refresh must retain independent geometry and alpha");
        LayerDrawable inset=new LayerDrawable(new Drawable[]{source});
        inset.setLayerInset(0,24,8,24,8);inset.setLayerSize(0,100,40);
        CloudNativeDecoration layered=new CloudNativeDecoration(inset,null,null);
        LayerDrawable fill=(LayerDrawable)layered.drawable;fill.setBounds(0,0,300,160);
        check(fill.getDrawable(0).getBounds().equals(fill.getBounds()),"native layer insets and fixed size must not leave another silhouette inside the unified capsule");
        check(((GradientDrawable)fill.getDrawable(0)).getCornerRadius()==0,"nested fill uses only the unified outline");
        check(inset.getLayerInsetLeft(0)==24&&inset.getLayerWidth(0)==100,"normalizing the copy must preserve the native layer geometry");
        if(args.length>0) {
            ClassLoader nativeLoader=new dalvik.system.PathClassLoader(args[0],CloudNativeDecorationDeviceCheck.class.getClassLoader());
            Class<?> triple=nativeLoader.loadClass("kotlin.Triple");
            Drawable nativeFill=(Drawable)Reflect.newInstance(nativeLoader.loadClass("com.oplus.systemui.plugins.seedling.util.d"),
                Color.RED,Color.GREEN,Color.BLUE,Reflect.newInstance(triple,2,30f,3f));
            check(nativeFill.getConstantState()==null,"firmware gradient reproduces the missing ConstantState boundary");
            CloudNativeDecoration nativeDecoration=new CloudNativeDecoration(nativeFill,null,null);
            Drawable copied=nativeDecoration.drawable;copied.setBounds(0,0,300,160);
            Object corners=Reflect.getObjectField(copied,"d");
            check(Reflect.callMethod(corners,"d").equals(0)&&((Number)Reflect.callMethod(corners,"e")).floatValue()==0,
                "firmware gradient must not clip the new capsule with its original corners");
            Reflect.setObjectField(nativeFill,"a",Color.YELLOW);nativeDecoration.refresh();
            check(Reflect.getIntField(copied,"a")==Color.YELLOW&&copied.getBounds().right==300,
                "firmware gradient color changes preserve the independent bounds");
            check(((Number)Reflect.callMethod(Reflect.getObjectField(nativeFill,"d"),"e")).floatValue()==30,
                "native gradient outline stays untouched");
        }
        System.out.println("CloudNativeDecorationDeviceCheck: independent bounds/alpha/color checks passed");
    }
}
