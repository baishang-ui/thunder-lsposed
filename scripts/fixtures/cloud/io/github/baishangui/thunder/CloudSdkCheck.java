package io.github.baishangui.thunder;

import android.os.Bundle;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import java.util.Arrays;

public final class CloudSdkCheck {
    private static int checks;
    private static void check(boolean value,String message) {if(!value)throw new AssertionError(message);checks++;}
    private static HookCallback.Param rebuild(g7.f2 card)throws Throwable {
        var p=CloudCheck.param(card,null,new com.oplus.ule.lite.components.s1(),true);
        var hook=Api102Runtime.callbacks.get("g7.f2#L");hook.before(p);return p;
    }
    private static void finish(HookCallback.Param p)throws Throwable {Api102Runtime.callbacks.get("g7.f2#L").after(p);}
    public static void main(String[] args)throws Throwable {
        CloudSdkHook.install(CloudSdkCheck.class.getClassLoader());
        Bundle b=new Bundle();b.putBoolean("enabled",true);b.putBoolean("cloud_enabled",true);
        b.putBoolean("cloud_app_color_strength_enabled",true);b.putInt("cloud_app_color_strength",50);CloudCheck.config(b);
        g7.f2 card=new g7.f2();var p=rebuild(card);card.i.setBackground(new ColorDrawable(0x806688aa));finish(p);
        check(((ColorDrawable)card.i.getBackground()).getColor()==0x406688aa,"SDK background uses original alpha and preserves RGB");
        p=rebuild(card);finish(p);
        check(((ColorDrawable)card.i.getBackground()).getColor()==0x406688aa,"SDK cached backgrounds never accumulate opacity scaling");
        b.putBoolean("cloud_app_color",false);CloudCheck.config(b);CloudSdkHook.refresh();
        check(((ColorDrawable)card.i.getBackground()).getColor()==0x006688aa,"SDK mask off updates existing background");
        b.putBoolean("cloud_app_color",true);b.putInt("cloud_app_color_strength",200);CloudCheck.config(b);CloudSdkHook.refresh();
        check(((ColorDrawable)card.i.getBackground()).getColor()==0xff6688aa,"SDK strength doubles the original alpha with saturation");
        p=rebuild(card);GradientDrawable gradient=new GradientDrawable();gradient.setColors(new int[]{0x406688aa,0x00446688,0xff123456});card.i.setBackground(gradient);finish(p);
        check(Arrays.equals(gradient.getColors(),new int[]{0x806688aa,0x00446688,0xff123456}),"SDK gradient scales all stops, keeping transparent stops transparent");
        b.putBoolean("enabled",false);CloudCheck.config(b);CloudSdkHook.refresh();
        check(Arrays.equals(gradient.getColors(),new int[]{0x406688aa,0x00446688,0xff123456}),"module off restores SDK native gradient");
        b.putBoolean("enabled",true);b.putBoolean("cloud_app_color",false);CloudCheck.config(b);
        e7.a4 flow=new e7.a4();var hook=Api102Runtime.callbacks.get("e7.a4#onDraw");
        var frame=CloudCheck.param(flow,null,new android.graphics.Canvas());hook.before(frame);
        check(flow.d==0&&flow.e==0,"flow animation colors are hidden without changing content view opacity");
        frame.failure=new RuntimeException("draw failed");hook.after(frame);
        check(flow.d==.25f&&flow.e==.75f,"flow animation values restore even after failed draw");
        b.putBoolean("cloud_app_color",true);CloudCheck.config(b);
        frame=CloudCheck.param(flow,null,new android.graphics.Canvas());hook.before(frame);
        check(flow.d==.5f&&flow.e==1f,"flow alpha doubles and saturates within the native 0..1 range");hook.after(frame);
        System.out.println("Verified "+checks+" independent Seedling SDK background checks.");
    }
}
