package io.github.baishangui.thunder;

import android.os.Bundle;
import java.lang.reflect.Field;
import java.util.Set;
import com.android.systemui.statusbar.phone.ui.StatusBarIconControllerImpl;

public final class StatusBarIconCheck {
    private static final String TYPE = StatusBarIconControllerImpl.class.getName();
    private static int checks;
    static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
        checks++;
        System.out.println("PASS: "+description);
    }
    static void config(boolean enabled, int mask) throws Exception {
        Bundle data = new Bundle();
        data.putBoolean("enabled", enabled);
        data.putInt("status_bar_hidden_icons", mask);
        Field field = ShadeHook.class.getDeclaredField("config");
        field.setAccessible(true);
        field.set(null, new HookConfig(data));
    }
    static HookCallback.Param param(Object object, Object... args) {
        HookCallback.Param p = new HookCallback.Param();
        p.thisObject = object;
        p.args = args;
        return p;
    }
    static void tune(StatusBarIconControllerImpl controller, String key, String value) throws Throwable {
        HookCallback hook = Api102Runtime.callbacks.get(TYPE+"#onTuningChanged");
        HookCallback.Param p = param(controller,key,value);
        hook.before(p);
        controller.onTuningChanged(key,value);
        hook.after(p);
    }
    public static void main(String[] args) throws Throwable {
        ShadeHook.install("com.android.systemui", "com.android.systemui", StatusBarIconCheck.class.getClassLoader());
        check(Api102Runtime.callbacks.containsKey(TYPE+"#onTuningChanged"), "C17 controller hooks registered");
        config(true,StatusBarIcons.BLUETOOTH | StatusBarIcons.NFC);
        StatusBarIconControllerImpl controller = new StatusBarIconControllerImpl();
        controller.mIconHideList.add("alarm_clock");
        Api102Runtime.callbacks.get(TYPE+"#<init>").after(param(controller));
        check(controller.blockedAtRefresh.equals(Set.of("alarm_clock","bluetooth","nfc")), "startup merges selected icons with the system hide list");
        int count = controller.refreshes;
        StatusBarIconHook.apply(controller);
        check(controller.refreshes == count, "repeated application avoids unnecessary group rebuilds");
        tune(controller,"icon_blacklist","vpn,bluetooth");
        check(controller.blockedAtRefresh.equals(Set.of("vpn","bluetooth","nfc")), "system hide-list changes retain module selections");
        config(true,0);
        StatusBarIconHook.refresh();
        check(controller.blockedAtRefresh.equals(Set.of("vpn","bluetooth")), "restoring defaults preserves icons independently hidden by the system");
        config(true,StatusBarIcons.ALL);
        StatusBarIconHook.refresh();
        check(controller.blockedAtRefresh.size()==10, "all ten verified slots reach the native block list");
        config(false,StatusBarIcons.ALL);
        StatusBarIconHook.refresh();
        check(controller.blockedAtRefresh.equals(Set.of("vpn","bluetooth")), "module off removes only module-owned exclusions");
        count = controller.refreshes;
        tune(controller,"unrelated_setting","hotspot");
        check(controller.refreshes == count, "unrelated tuner callbacks leave icons unchanged");
        config(true,StatusBarIcons.HEADSET);
        StatusBarIconControllerImpl second = new StatusBarIconControllerImpl();
        second.mIconHideList.add("zen");
        Api102Runtime.callbacks.get(TYPE+"#<init>").after(param(second));
        StatusBarIconHook.refresh();
        check(second.blockedAtRefresh.equals(Set.of("zen","headset")) && controller.blockedAtRefresh.equals(Set.of("vpn","bluetooth","headset")), "recreated controllers keep independent system preferences");
        System.out.println("Verified "+checks+" status icon checks; phone rendering remains unverified.");
    }
}
