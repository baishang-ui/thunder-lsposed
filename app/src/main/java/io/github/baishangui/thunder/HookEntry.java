package io.github.baishangui.thunder;

import android.os.Build;
import io.github.libxposed.api.XposedModule;

/** Modern API 102 lifecycle; no legacy entry or XposedHelpers dependency. */
public final class HookEntry extends XposedModule {
    private String processName;
    @Override public void onModuleLoaded(ModuleLoadedParam param) {
        processName = param.getProcessName();
    }
    @Override public void onPackageReady(PackageReadyParam param) {
        if (Build.VERSION.SDK_INT != 37) return;
        String packageName = param.getPackageName();
        boolean host = "com.android.systemui".equals(processName);
        boolean plugin = "com.oplus.systemui.plugins".equals(processName);
        if (host && "com.android.systemui".equals(packageName) && param.isFirstPackage()) {
            Api102Runtime.bind(this);
            ShadeHook.install(packageName, processName, param.getClassLoader());
        } else if ((host || plugin) && "com.oplus.systemui.plugins".equals(packageName)) {
            // A plugin can be a secondary package in SystemUI or own its process.
            Api102Runtime.bind(this);
            ShadeHook.installCloud(packageName, processName, param.getClassLoader());
        }
    }
}
