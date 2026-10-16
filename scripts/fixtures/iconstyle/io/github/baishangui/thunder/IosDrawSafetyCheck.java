package io.github.baishangui.thunder;

/** SystemUI owns drawing and state; only its local percentage layout area is adjusted. */
public final class IosDrawSafetyCheck {
    public static void main(String[] args) throws Throwable {
        IosStatusBarCheck.config(true, 0);
        ShadeHook.install("com.android.systemui", "com.android.systemui", IosDrawSafetyCheck.class.getClassLoader());
        IosStatusBarCheck.config(true, 1);
        IosStatusBarHook.prepare(new IosStatusBarCheck.ResourceContext());
        var resources = new android.content.res.Resources();
        resources.full = "com.android.systemui:drawable/stat_sys_alarm";
        if (IosStatusBarHook.replacement(resources, 1) != 0 || !IosStatusBarHook.state().contains("restart-required=true"))
            throw new AssertionError("Enabling style requires a new SystemUI process");
        var label=Api102Runtime.callbacks.get("com.oplus.systemui.statusbar.pipeline.mobile.ui.view.OplusStatusBarMobileViewBinder$Os17Binding#bindNetworkTypeText");
        Object model=new Object(); var call=IosStatusBarCheck.param(null,model); label.before(call);
        IosStatusBarCheck.check(call.args[0]==model,"default style preserves network labels until restart");
        var height=Api102Runtime.callbacks.get("com.android.internal.policy.SystemBarUtils#getStatusBarHeight");
        call=IosStatusBarCheck.param(null,new android.content.Context()); height.before(call); call.setResult(100); height.after(call);
        IosStatusBarCheck.check(call.getResult().equals(100),"default style preserves status height");
        var bodyHook=Api102Runtime.callbacks.get("com.oplus.systemui.statusbar.pipeline.battery.ui.drawable.HorizontalBatteryContentDrawable#getBodyRectForLayout");
        var battery=new com.oplus.systemui.statusbar.pipeline.battery.ui.drawable.HorizontalBatteryContentDrawable();
        var content=new android.graphics.RectF(0,0,34,17);
        var body=new android.graphics.RectF(0,0,31,17);
        call=IosStatusBarCheck.param(battery,content);call.setResult(body);bodyHook.after(call);
        IosStatusBarCheck.check(body.left==0 && body.right==31,"default style preserves native battery text area");
        var offsetHook=Api102Runtime.callbacks.get("com.oplus.systemui.statusbar.pipeline.battery.ui.drawable.HorizontalBatteryContentDrawable#getOffsetX");
        call=IosStatusBarCheck.param(battery);call.setResult(1f);offsetHook.after(call);
        IosStatusBarCheck.check(call.getResult().equals(1f),"default style preserves native battery text offset");
        for (String key : Api102Runtime.callbacks.keySet()) {
            if ((key.contains("ImageView#") || key.contains("StatusBarIconView#")
                    || key.contains("VectorDrawable#") || key.contains("StatBatteryMeterView#")
                    || key.contains("StateFlowImpl#") || key.contains("BatteryViewBinder#")
                    || (key.contains("battery.ui.drawable.") && (key.endsWith("#draw") || key.endsWith("#drawContent")))))
                throw new AssertionError("Style must preserve native drawing and state: "+key);
        }
        System.out.println("Verified absence of global view, drawing, battery and signal state hooks.");
    }
}
