package io.github.baishangui.thunder;

import android.content.Context;
import android.content.Intent;
import java.util.Set;

public final class ProviderVisibilityCheck {
    public static void main(String[] args) {
        SettingsProvider provider = new SettingsProvider();
        provider.onCreate();
        if (!Context.uriGrants.keySet().equals(Set.of("com.android.systemui", "com.oplus.systemui.plugins", "com.android.settings")))
            throw new AssertionError("provider startup must establish reverse visibility for all scope packages");
        for (var grant : Context.uriGrants.values()) {
            if (!grant.uri().equals("content://io.github.baishangui.thunder.settings")
                    || grant.flags() != Intent.FLAG_GRANT_READ_URI_PERMISSION)
                throw new AssertionError("grants must only cover the module authority with read permission");
        }
        System.out.println("PASS: provider startup grants visibility only to the three scope packages");
        Context.uriGrants.clear(); Context.missingPackage = "com.android.systemui";
        provider.onCreate();
        if (!Context.uriGrants.keySet().equals(Set.of("com.oplus.systemui.plugins", "com.android.settings")))
            throw new AssertionError("an unavailable target must not prevent grants to the other target");
        System.out.println("PASS: a missing scope package does not break provider startup or the other grant");
    }
}
