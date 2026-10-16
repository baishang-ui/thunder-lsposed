package io.github.baishangui.thunder;

import android.content.res.Resources;
import android.graphics.drawable.*;
import android.os.Bundle;

public final class IosStatusBarCheck {
    static int checks;
    static void check(boolean value,String message) { if(!value) throw new AssertionError(message); checks++; }
    static HookCallback.Param param(Object object,Object... args) { return StatusBarIconCheck.param(object,args); }
    static void config(boolean enabled,int style) throws Exception {
        Bundle b=new Bundle(); b.putBoolean("enabled",enabled); b.putInt("status_bar_icon_style",style);
        var field=ShadeHook.class.getDeclaredField("config"); field.setAccessible(true); field.set(null,new HookConfig(b));
    }
    static final class ResourceContext extends android.content.Context {
        final Resources resources = new Resources();
        @Override public Resources getResources() { return resources; }
    }
    public static void main(String[] args) throws Throwable {
        config(true,1);
        ShadeHook.install("com.android.systemui","com.android.systemui",IosStatusBarCheck.class.getClassLoader());
        var host=new Resources(); host.full="com.android.systemui:drawable/stat_signal_wifi_signal_3_os17";
        var original=new VectorDrawable(); original.setAlpha(123); original.setBounds(1,2,3,4);
        check(IosStatusBarHook.drawable(host,1,480,original)==original,"before resources available, return native drawable");
        var module=new ResourceContext(); IosStatusBarHook.prepare(module);
        var callback=Api102Runtime.callbacks.get(Resources.class.getName()+"#getDrawableForDensity");
        var call=param(host,1,480,null); call.setResult(original); callback.after(call);
        check(call.getResult()==module.resources.loaded && call.getResult() instanceof VectorDrawable,"framework receives native vector from compiled XML");
        check(module.resources.loadedId==IosStatusResources.replacement(host.full) && module.resources.loadedDensity==480,"load exact static level resource at native density");
        check(original.getAlpha()==123 && original.getBounds().left==1 && original.getLevel()==0,"original drawable state never read or mutated for status calculations");
        check(IosStatusBarHook.drawable(host,-1,0,original)==original,"unknown resource ID falls back");
        host.full="foreign:drawable/stat_signal_wifi_signal_3_os17";
        check(IosStatusBarHook.drawable(host,1,0,original)==original,"foreign package excluded");
        host.full="com.android.systemui:drawable/notification_app_icon";
        check(IosStatusBarHook.drawable(host,1,0,original)==original,"unlisted and notification resources excluded");
        host.full="com.android.systemui:drawable/stat_signal_wifi_signal_3_os17";
        check(IosStatusBarHook.drawable(host,1,0,new Drawable()).getClass()==Drawable.class,"unrecognized concrete drawable type preserved");
        config(true,0);
        check(IosStatusBarHook.drawable(host,1,0,original)==module.resources.loaded,"style does not change until SystemUI restart");
        check(IosStatusBarHook.state().contains("restart-required=true"),"pending restart reported separately from loaded style");
        config(false,1);
        check(IosStatusBarHook.state().contains("restart-required=true"),"module switch cannot leave mixed cached resources");
        config(true,1);
        check(!IosStatusBarHook.state().contains("restart-required"),"reverting pending choice clears restart indication");
        module.resources.broken=true;
        check(IosStatusBarHook.drawable(host,1,0,original)==original,"resource load failure returns system drawable");
        module.resources.broken=false;
        check(IosStatusBarHook.replacement(host,1)==0,"failed resource isolated without repeated attempts");
        host.full="com.android.systemui:drawable/stat_signal_wifi_signal_2_os17";
        check(IosStatusBarHook.drawable(host,2,0,original)==module.resources.loaded,"one bad resource does not disable healthy resources");
        System.out.println("Verified "+checks+" native resource substitution and restart checks.");
    }
}
