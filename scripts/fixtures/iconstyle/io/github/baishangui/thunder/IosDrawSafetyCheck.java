package io.github.baishangui.thunder;

/** Styling must stop at resource loading: all drawing and state belong to SystemUI. */
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
        for (String key : Api102Runtime.callbacks.keySet()) {
            if ((key.contains("ImageView#") || key.contains("StatusBarIconView#")
                    || key.contains("VectorDrawable#") || key.contains("StatBatteryMeterView#")
                    || key.contains("StateFlowImpl#") || key.contains("BatteryViewBinder#")))
                throw new AssertionError("Style must only replace resources: "+key);
        }
        System.out.println("Verified absence of status view, drawing, battery, signal state and layout hooks.");
    }
}
