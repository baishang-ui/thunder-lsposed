package io.github.baishangui.thunder;

import android.content.Context;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;

/** system_server has no ApplicationThread: use AMS's external-provider API. */
final class SystemProviderCall {
    static Bundle call(Context context, String method, Bundle extras) throws ReflectiveOperationException {
        long identity = Binder.clearCallingIdentity();
        IBinder token = new Binder();
        Object am = null, holder = null;
        try {
            Class<?> activityManager = Class.forName("android.app.ActivityManager");
            var getService = activityManager.getDeclaredMethod("getService");
            getService.setAccessible(true);
            am = getService.invoke(null);
            holder = Reflect.callMethod(am, "getContentProviderExternal", SettingsProvider.AUTHORITY, 0, token, "thunder-statusbar");
            if (holder == null) throw new IllegalStateException("外部 Provider 未就绪");
            Object provider = Reflect.getObjectField(holder, "provider");
            return (Bundle) Reflect.callMethod(provider, "call", context.getAttributionSource(),
                SettingsProvider.AUTHORITY, method, null, extras);
        } finally {
            try {
                if (holder != null && am != null)
                    Reflect.callMethod(am, "removeContentProviderExternalAsUser", SettingsProvider.AUTHORITY, token, 0);
            } finally { Binder.restoreCallingIdentity(identity); }
        }
    }
    private SystemProviderCall() { }
}
