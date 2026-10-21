package io.github.baishangui.thunder;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;

/** Runs with Android graphics: host drawable animation must not deform the independent fill. */
public final class CloudNativeDecorationDeviceCheck {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args) {
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
        System.out.println("CloudNativeDecorationDeviceCheck: independent bounds/alpha/color checks passed");
    }
}
