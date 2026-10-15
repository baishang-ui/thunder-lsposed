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
        if (!"com.android.systemui".equals(processName)
                || !"com.android.systemui".equals(param.getPackageName())
                || !param.isFirstPackage() || Build.VERSION.SDK_INT != 37) return;
        Api102Runtime.bind(this);
        ShadeHook.install(param.getPackageName(), processName, param.getClassLoader());
    }
}
