package io.github.baishangui.thunder;
import android.os.Binder;
import android.os.Bundle;
public final class SettingsUiProviderCheck {
    public static void main(String[] args) {
        SettingsProvider provider = new SettingsProvider();
        for (int uid : new int[]{3000, 4000, 10234}) {
            Binder.uid = uid;
            int before = SystemSettingsBridge.calls;
            try { provider.call("ui_update", null, new Bundle()); throw new AssertionError("untrusted write accepted: " + uid); }
            catch (SecurityException expected) { }
            if (SystemSettingsBridge.calls != before) throw new AssertionError("untrusted operation reached writer");
        }
        for (int uid : new int[]{1000, 2000}) {
            Binder.uid = uid;
            int before = SystemSettingsBridge.calls;
            provider.call("ui_snapshot", null, null);
            if (SystemSettingsBridge.calls != before + 1) throw new AssertionError("trusted UI did not reach bridge");
        }
        System.out.println("PASS: only module UID and Settings UID may call the native settings bridge");
    }
}
