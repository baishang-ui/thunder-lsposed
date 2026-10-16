package io.github.baishangui.thunder;

import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.drawable.VectorDrawable;

public final class IosRenderPathCheck {
    static void check(boolean value,String message) { IosStatusBarCheck.check(value,message); }
    public static void main(String[] args) throws Throwable {
        IosStatusBarCheck.config(true,1);
        ShadeHook.install("com.android.systemui","com.android.systemui",IosRenderPathCheck.class.getClassLoader());
        var module=new IosStatusBarCheck.ResourceContext(); IosStatusBarHook.prepare(module);
        var host=new Resources(); host.full="com.android.systemui:drawable/stat_signal_lte_signal_stacked_secondary_2";
        HookCallback xml=Api102Runtime.callbacks.get(Resources.class.getName()+"#getXml");
        var call=IosStatusBarCheck.param(host,1); xml.before(call);
        check(call.getResult()==module.resources.xml,"Compose receives compiled XML for native vector parsing");
        check(module.resources.loadedId==IosStatusResources.replacement(host.full),"SIM row and strength selected by exact system resource ID");
        host.full="other:drawable/stat_signal_lte_signal_stacked_secondary_2";
        call=IosStatusBarCheck.param(host,1); xml.before(call); check(call.getResult()==null,"foreign XML untouched");
        var array=new TypedArray(); array.resources.full="com.android.systemui:drawable/stat_sys_alarm";
        HookCallback typed=Api102Runtime.callbacks.get(TypedArray.class.getName()+"#getDrawableForDensity");
        call=IosStatusBarCheck.param(array,0,320); call.setResult(new VectorDrawable()); typed.after(call);
        check(call.getResult()==module.resources.loaded && module.resources.loadedDensity==320,"XML src resolves resource replacement with native density");
        host.full="com.android.systemui:drawable/stat_sys_battery_unknown";
        call=IosStatusBarCheck.param(host,1); xml.before(call); check(call.getResult()==null,"dynamic battery remains native");
        module.resources.broken=true; host.full="com.android.systemui:drawable/stat_sys_alarm";
        call=IosStatusBarCheck.param(host,1); xml.before(call); check(call.getResult()==null,"XML parser load failure lets system load original");
        System.out.println("Verified XML, Compose, typed resource and battery boundaries.");
    }
}
