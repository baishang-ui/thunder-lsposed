package io.github.baishangui.thunder;

import android.graphics.drawable.Drawable;
import com.android.systemui.statusbar.StatusBarIconView;

/** Native callers must be able to keep casting, updating and animating their own drawable. */
public final class IosDrawSafetyCheck {
    public static void main(String[] args) throws Throwable {
        ShadeHook.install("com.android.systemui", "com.android.systemui", IosDrawSafetyCheck.class.getClassLoader());
        IosRenderPathCheck.config(true);
        StatusBarIconView image = new StatusBarIconView();
        Drawable original = new Drawable();
        var call = StatusBarIconCheck.param(image, new IosStatusBarCheck.NativeIcon());
        call.setResult(original);
        Api102Runtime.callbacks.get(StatusBarIconView.class.getName()+"#getIcon").after(call);
        if (call.getResult() != original) throw new AssertionError("Native icon identity/type must survive style binding");
        for (String key : Api102Runtime.callbacks.keySet()) {
            if (key.contains("#setImageDrawable") || key.contains("#setImageResource")
                    || key.contains("StateFlowImpl#") || key.contains("BatteryViewBinder#")
                    || key.contains("#onMeasure") && (key.contains("StatusIconContainer") || key.contains("Clock") || key.contains("StatusBarIconView")))
                throw new AssertionError("Style must not modify native binding/state/layout: "+key);
        }
        System.out.println("Verified native drawable identity and absence of state/layout hooks.");
    }
}
