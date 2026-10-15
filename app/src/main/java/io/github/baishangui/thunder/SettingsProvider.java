package io.github.baishangui.thunder;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Process;

/** Small, device-protected IPC surface. Only this app and SystemUI may call it. */
public final class SettingsProvider extends ContentProvider {
    public static final String AUTHORITY = BuildConfig.APPLICATION_ID + ".settings";
    public static SharedPreferences preferences(Context context) {
        return context.createDeviceProtectedStorageContext().getSharedPreferences("settings", Context.MODE_PRIVATE);
    }
    @Override public boolean onCreate() { return true; }
    @Override public Bundle call(String method, String arg, Bundle extras) {
        Context context = getContext();
        int uid = Binder.getCallingUid();
        boolean own = uid == Process.myUid();
        boolean systemUi = false;
        try { systemUi = uid == context.getPackageManager().getPackageUid("com.android.systemui", 0); }
        catch (Exception ignored) { }
        if (!own && !systemUi) throw new SecurityException("SystemUI only");
        SharedPreferences prefs = preferences(context);
        if ("settings".equals(method)) {
            Bundle result = new Bundle();
            result.putBoolean("enabled", prefs.getBoolean("enabled", true));
            result.putInt("status_bar_hidden_icons", prefs.getInt("status_bar_hidden_icons", 0) & StatusBarIcons.ALL);
            for (String area : new String[]{"panel", "banner"}) {
                result.putBoolean(area+"_blur", prefs.getBoolean(area+"_blur", "panel".equals(area) && prefs.getBoolean("shade_blur", false)));
                result.putInt(area+"_radius", Math.max(0, Math.min(200, prefs.getInt(area+"_radius", "panel".equals(area) ? prefs.getInt("shade_radius", 100) : 100))));
                result.putBoolean(area+"_tint", prefs.getBoolean(area+"_tint", "panel".equals(area) && prefs.getBoolean("shade_tint", false)));
                result.putInt(area+"_color", prefs.getInt(area+"_color", "panel".equals(area) ? prefs.getInt("shade_color", 0x92000000) : 0x33000000));
            }
            result.putBoolean("notification_margins", prefs.getBoolean("notification_margins", false));
            result.putInt("notification_margin", Math.max(0, Math.min(64, prefs.getInt("notification_margin", 30))));
            result.putBoolean("cloud_enabled", prefs.getBoolean("cloud_enabled", true));
            result.putBoolean("cloud_height_enabled",prefs.getBoolean("cloud_height_enabled",false));
            result.putInt("cloud_height",prefs.getInt("cloud_height",34));
            result.putBoolean("cloud_corner_enabled",prefs.getBoolean("cloud_corner_enabled",false));
            result.putInt("cloud_corner",prefs.getInt("cloud_corner",20));
            result.putBoolean("cloud_blur",prefs.getBoolean("cloud_blur",false));
            result.putInt("cloud_radius",prefs.getInt("cloud_radius",100));
            result.putBoolean("cloud_tint",prefs.getBoolean("cloud_tint",false));
            result.putInt("cloud_color",prefs.getInt("cloud_color",0x20282828));
            result.putLong("revision", prefs.getLong("revision", 0));
            return result;
        }
        if ("report".equals(method) && systemUi && extras != null) {
            // The caller cannot change user settings or supply arbitrary preference keys.
            context.createDeviceProtectedStorageContext().getSharedPreferences("runtime", 0).edit()
                .putString("hooks", extras.getString("hooks", ""))
                .putString("hits", extras.getString("hits", ""))
                .putString("error", extras.getString("error", ""))
                .putString("effective", extras.getString("effective", ""))
                .putLong("revision", extras.getLong("revision", -1))
                .putLong("time", System.currentTimeMillis())
                .putLong("elapsed", android.os.SystemClock.elapsedRealtime())
                .putInt("pid", extras.getInt("pid", -1))
                .putInt("hook_version", extras.getInt("hook_version", 0)).apply();
            return Bundle.EMPTY;
        }
        throw new IllegalArgumentException("Unsupported method");
    }
    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String o) { return null; }
    @Override public String getType(Uri u) { return null; }
    @Override public Uri insert(Uri u, ContentValues v) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri u, String s, String[] a) { throw new UnsupportedOperationException(); }
}
