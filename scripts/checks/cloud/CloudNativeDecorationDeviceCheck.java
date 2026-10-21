package io.github.baishangui.thunder;

import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.Drawable;

/** Runs with Android graphics: host drawable animation must not deform the independent fill. */
public final class CloudNativeDecorationDeviceCheck {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static Bitmap pixels(Drawable drawable,int width,int height) {
        Bitmap pixels=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
        drawable.draw(new Canvas(pixels));return pixels;
    }
    static void shrinkingCapsule(CloudNativeDecoration decoration) {
        // Draw the native copy directly: no View outline, canvas clip, scale or
        // larger source capsule may manufacture the narrowing silhouette.
        int height=176;
        for(int width:new int[]{600,220,176,80,1}) {
            float radius=Math.min(88f,width/2f);
            decoration.geometry(width,height,radius,false);
            Bitmap frame=pixels(decoration.drawable,width,height);
            check(decoration.drawable.getBounds().width()==width&&decoration.drawable.getBounds().height()==height,
                "native capsule bounds must narrow while retaining height: "+width);
            check(frame.getPixel(width/2,height/2)==Color.RED,
                "native capsule must keep its center throughout width-only disappearance: "+width);
            if(width>1) {
                check(Color.alpha(frame.getPixel(1,1))==0&&Color.alpha(frame.getPixel(width-2,1))==0,
                    "narrowed capsule must draw curved end corners rather than rectangular cut edges: "+width);
                check(frame.getPixel(1,height/2)==Color.RED&&frame.getPixel(width-2,height/2)==Color.RED,
                    "curved native ends must reach the new left/right bounds: "+width);
                check(frame.getPixel(width/2,1)==Color.RED&&frame.getPixel(width/2,height-2)==Color.RED,
                    "width-only shrink must preserve the full native capsule height: "+width);
            }
            frame.recycle();
        }
    }
    public static void main(String[] args) throws Exception {
        GradientDrawable source=new GradientDrawable();source.setColor(0x8045789a);source.setCornerRadius(30);
        source.setBounds(30,0,130,40);source.setAlpha(0);
        CloudNativeDecoration decoration=new CloudNativeDecoration(source,null,null);
        GradientDrawable copy=(GradientDrawable)decoration.drawable;
        check(copy!=source,"must have a separate drawable");
        check(copy.getAlpha()==255,"native fade alpha must not hide the independent decoration");
        check(copy.getColor().getDefaultColor()==0x8045789a,"keep the application's color and color alpha");
        check(copy.getCornerRadius()==30,"copy must preserve the native drawable's corner drawing");
        check(Reflect.getIntField(source.getConstantState(),"mStrokeWidth")==-1,
            "plain native fill must exercise the default no-stroke sentinel");
        decoration.geometry(300,160,80,false);source.setBounds(55,0,85,40);source.setAlpha(19);
        check(copy.getBounds().right==300,"native width animator must not edit the copied bounds");
        source.setColor(Color.RED);decoration.refresh();
        check(copy.getColor().getDefaultColor()==Color.RED,"same-drawable app color updates must stay live");
        check(copy.getBounds().right==300&&copy.getAlpha()==255,"refresh must retain independent geometry and alpha");
        check(copy.getCornerRadius()==80,"native refresh must retain the current capsule corner");
        decoration.geometry(240,80,1000,false);
        Bitmap pill=pixels(copy,240,80);
        check(Color.alpha(pill.getPixel(1,1))==0&&Color.alpha(pill.getPixel(8,8))==0,
            "native drawable must draw the maximum pill without any external canvas/outline clip");
        check(pill.getPixel(1,40)==Color.RED&&pill.getPixel(238,40)==Color.RED,
            "maximum radius must retain circular left and right ends");
        decoration.geometry(240,80,8,false);
        Bitmap small=pixels(copy,240,80);
        check(small.getPixel(8,8)==Color.RED&&Color.alpha(small.getPixel(1,1))==0,
            "a smaller chosen radius must actually change the native pixels");
        shrinkingCapsule(decoration);
        check(source.getCornerRadius()==30,"independent geometry cannot edit native source radius");
        GradientDrawable foreground=new GradientDrawable();foreground.setColor(Color.TRANSPARENT);foreground.setStroke(4,Color.BLUE);
        CloudNativeDecoration edge=new CloudNativeDecoration(foreground,null,null);edge.geometry(240,80,40,false);
        Bitmap blue=pixels(edge.drawable,240,80);
        check(Color.blue(blue.getPixel(120,1))>240&&Color.alpha(blue.getPixel(120,40))==0,
            "native foreground stroke must draw on the capsule curve with a transparent center");
        foreground.setStroke(8,Color.GREEN);edge.refresh();
        Bitmap green=pixels(edge.drawable,240,80);
        check(Color.green(green.getPixel(120,5))>240&&Color.alpha(green.getPixel(120,40))==0,
            "same-object native stroke width/color changes must reach the copied foreground");
        check(Color.alpha(green.getPixel(1,1))==0,"foreground must share the capsule radius rather than a fixed old corner");
        foreground.setStroke(0,Color.GREEN);edge.refresh();
        check(Color.alpha(pixels(edge.drawable,240,80).getPixel(120,1))==0,
            "native dark-intensity stroke removal must hide the copied edge");
        foreground.setStroke(4,Color.argb(64,255,0,0));edge.refresh();
        int translucent=pixels(edge.drawable,240,80).getPixel(120,1);
        check(Color.red(translucent)>240&&Color.alpha(translucent)>=62&&Color.alpha(translucent)<=65,
            "native service stroke restoration must preserve its 64/255 color alpha");
        LayerDrawable inset=new LayerDrawable(new Drawable[]{source});
        inset.setLayerInset(0,24,8,24,8);inset.setLayerSize(0,100,40);
        CloudNativeDecoration layered=new CloudNativeDecoration(inset,null,null);
        LayerDrawable fill=(LayerDrawable)layered.drawable;layered.geometry(300,160,80,false);
        check(fill.getDrawable(0).getBounds().equals(fill.getBounds()),"native layer insets and fixed size must not leave another silhouette inside the unified capsule");
        check(((GradientDrawable)fill.getDrawable(0)).getCornerRadius()==80,"nested native fill draws the same capsule corner");
        check(inset.getLayerInsetLeft(0)==24&&inset.getLayerWidth(0)==100,"normalizing the copy must preserve the native layer geometry");
        if(args.length>0) {
            ClassLoader nativeLoader=new dalvik.system.PathClassLoader(args[0],CloudNativeDecorationDeviceCheck.class.getClassLoader());
            Class<?> triple=nativeLoader.loadClass("kotlin.Triple");
            Drawable nativeFill=(Drawable)Reflect.newInstance(nativeLoader.loadClass("com.oplus.systemui.plugins.seedling.util.d"),
                Color.RED,Color.GREEN,Color.BLUE,Reflect.newInstance(triple,2,30f,3f));
            check(nativeFill.getConstantState()==null,"firmware gradient reproduces the missing ConstantState boundary");
            CloudNativeDecoration nativeDecoration=new CloudNativeDecoration(nativeFill,null,null);
            Drawable copied=nativeDecoration.drawable;nativeDecoration.geometry(300,160,80,false);
            Object corners=Reflect.getObjectField(copied,"d");
            check(Reflect.callMethod(corners,"d").equals(0)&&((Number)Reflect.callMethod(corners,"e")).floatValue()==80,
                "firmware gradient must draw the current capsule curve itself");
            Bitmap firmware=pixels(copied,300,160);
            check(Color.alpha(firmware.getPixel(1,1))==0&&Color.alpha(firmware.getPixel(150,80))>0,
                "firmware gradient must draw its rounded silhouette without external clipping");
            Reflect.setObjectField(nativeFill,"a",Color.YELLOW);nativeDecoration.refresh();
            check(Reflect.getIntField(copied,"a")==Color.YELLOW&&copied.getBounds().right==300,
                "firmware gradient color changes preserve the independent bounds");
            check(((Number)Reflect.callMethod(Reflect.getObjectField(nativeFill,"d"),"e")).floatValue()==30,
                "native gradient outline stays untouched");
        }
        System.out.println("CloudNativeDecorationDeviceCheck: native capsule pixels, foreground refresh and independent geometry checks passed");
    }
}
