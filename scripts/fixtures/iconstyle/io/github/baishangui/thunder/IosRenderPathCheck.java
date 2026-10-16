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
        host.full="com.android.systemui:drawable/stat_battery_horizontal_progress";
        call=IosStatusBarCheck.param(host,1); xml.before(call);
        check(call.getResult()==null,"battery layer IDs are resolved by module Resources, not the host XML parser");
        host.full="com.android.systemui:drawable/stat_battery_horizontal_bg";
        call=IosStatusBarCheck.param(host,1); xml.before(call);
        check(call.getResult()==module.resources.xml,"battery silhouette uses the native vector path");
        host.full="com.android.systemui:dimen/status_bar_battery_horizontal_progress_start";
        HookCallback dimension=Api102Runtime.callbacks.get(Resources.class.getName()+"#getDimension");
        call=IosStatusBarCheck.param(host,1); call.setResult(99f); dimension.after(call);
        check(Math.abs(((Float)call.getResult())-3f*1.12f*host.getDisplayMetrics().density)<.001f,"fractional PUI battery dimension retains precision and host density");
        host=new Resources(); host.full="foreign:dimen/status_bar_battery_horizontal_progress_start";
        call=IosStatusBarCheck.param(host,1); call.setResult(99f); dimension.after(call);
        check(call.getResult().equals(99f),"battery dimensions are isolated to SystemUI");
        var value=new android.util.TypedValue();
        host=new Resources(); host.full="com.android.systemui:dimen/status_bar_battery_horizontal_width";
        HookCallback getValue=Api102Runtime.callbacks.get(Resources.class.getName()+"#getValue");
        call=IosStatusBarCheck.param(host,1,value,true); getValue.after(call);
        check(value.type==android.util.TypedValue.TYPE_DIMENSION,"raw dimension consumers receive a dimension");
        check(Math.abs((value.data >> 8)/32768f-32.48f)<.0001f && (value.data & 15)==1,"raw dimension value matches typed getter in dp");
        check(Math.abs((IosStatusBarHook.complexDimension(4.92f)>>8)/32768f-4.92f)<.0001f,"raw fractional dimensions retain subpixel precision");
        host=new Resources(); host.full="com.android.systemui:dimen/status_bar_wifi_signal_size";
        call=IosStatusBarCheck.param(host,1); call.setResult(30f); dimension.after(call);
        check(Math.abs((Float)call.getResult()-33.6f)<.001f,"native icon sizes enlarge by 12 percent at actual density");
        host=new Resources(); host.full="com.android.systemui:dimen/stat_bt_battery_inset_left";
        call=IosStatusBarCheck.param(host,1); call.setResult(99f); dimension.after(call);
        check(Math.abs((Float)call.getResult()+3f*1.12f*host.getDisplayMetrics().density)<.001f,"negative original Bluetooth insets retain direction when scaled");
        host=new Resources(); host.full="com.android.systemui:dimen/stat_bt_battery_inset_left";
        HookCallback pixelSize=Api102Runtime.callbacks.get(Resources.class.getName()+"#getDimensionPixelSize");
        call=IosStatusBarCheck.param(host,1); call.setResult(99); pixelSize.after(call);
        check((Integer)call.getResult()<0,"negative pixel-size results must not be clamped to positive one");
        host=new Resources(); host.full="com.android.systemui:dimen/status_bar_mobile_signal_size";
        value=new android.util.TypedValue(); value.type=android.util.TypedValue.TYPE_DIMENSION;
        value.data=(15<<8)|2;
        call=IosStatusBarCheck.param(host,1,value,true); getValue.after(call);
        check((value.data&15)==2 && Math.abs((value.data>>8)/32768f-16.8f)<.0001f,"native SP dimensions retain font-scale units after scaling");
        host=new Resources(); host.full="com.android.systemui:dimen/status_bar_battery_horizontal_height";
        call=IosStatusBarCheck.param(host,1); call.setResult(99f); dimension.after(call);
        check(Math.abs((Float)call.getResult()-17f*host.getDisplayMetrics().density)<.001f,"horizontal battery height is 17dp while its width remains 32.48dp");
        host=new Resources(); host.full="com.android.systemui:dimen/status_bar_battery_horizontal_progress_top";
        call=IosStatusBarCheck.param(host,1); call.setResult(99f); dimension.after(call);
        check(Math.abs((Float)call.getResult()-4.45f*host.getDisplayMetrics().density)<.001f,"progress inset follows the reduced vertical scale");
        host=new Resources(); host.full="com.android.systemui:dimen/battery_percent_in_text_size";
        call=IosStatusBarCheck.param(host,1); call.setResult(99f); dimension.after(call);
        check(Math.abs((Float)call.getResult()-10f*host.getDisplayMetrics().density)<.001f,"internal percentage follows the reduced battery height");
        host=new Resources(); host.full="com.android.systemui:dimen/status_bar_battery_horizontal_height";
        value=new android.util.TypedValue(); call=IosStatusBarCheck.param(host,1,value,true); getValue.after(call);
        check(Math.abs((value.data>>8)/32768f-17f)<.0001f,"raw and getter battery heights agree");
        for (String suffix : new String[]{"", "_new"}) {
            host=new Resources(); host.full="com.android.systemui:dimen/signal_cluster_battery_padding"+suffix;
            call=IosStatusBarCheck.param(host,1); call.setResult(0f); dimension.after(call);
            float expected=suffix.isEmpty()?2.344f:1f;
            check(Math.abs((Float)call.getResult()-expected*host.getDisplayMetrics().density)<.001f,"battery separator adds the missing 1dp side gap: "+suffix);
        }
        HookCallback label=Api102Runtime.callbacks.get("com.oplus.systemui.statusbar.pipeline.mobile.ui.view.OplusStatusBarMobileViewBinder$Os17Binding#bindNetworkTypeText");
        call=IosStatusBarCheck.param(null,new Object()); label.before(call);
        check(call.args[0]==null,"C17 text-based 5G and vendor image labels follow the native clear branch");
        HookCallback roaming=Api102Runtime.callbacks.get("com.oplus.systemui.statusbar.pipeline.mobile.ui.view.OplusStatusBarMobileViewBinder$Os17Binding#bindRoamingText");
        call=IosStatusBarCheck.param(null,true); roaming.before(call); check(Boolean.FALSE.equals(call.args[0]),"C17 roaming text stays hidden after updates");
        HookCallback height=Api102Runtime.callbacks.get("com.android.internal.policy.SystemBarUtils#getStatusBarHeight");
        HookCallback rotated=Api102Runtime.callbacks.get("com.android.internal.policy.SystemBarUtils#getStatusBarHeightForRotation");
        var context=new IosStatusBarCheck.ResourceContext();
        var outer=IosStatusBarCheck.param(null,context); height.before(outer);
        var inner=IosStatusBarCheck.param(null,context,1); rotated.before(inner); inner.setResult(100); rotated.after(inner);
        check(inner.getResult().equals(100),"delegated height helper does not add height twice");
        outer.setResult(inner.getResult()); height.after(outer);
        check(outer.getResult().equals(100+Math.round(4*context.resources.getDisplayMetrics().density)),"window and insets retain native cutout height plus 4dp");
        var failed=IosStatusBarCheck.param(null,context); height.before(failed); height.after(failed);
        call=IosStatusBarCheck.param(null,context,3); rotated.before(call); call.setResult(80); rotated.after(call);
        check(call.getResult().equals(80+Math.round(4*context.resources.getDisplayMetrics().density)),"failed height call unwinds recursion before another rotation");
        module.resources.broken=true; host.full="com.android.systemui:drawable/stat_sys_alarm";
        call=IosStatusBarCheck.param(host,1); xml.before(call); check(call.getResult()==null,"XML parser load failure lets system load original");
        System.out.println("Verified XML, Compose, typed resource and battery boundaries.");
    }
}
