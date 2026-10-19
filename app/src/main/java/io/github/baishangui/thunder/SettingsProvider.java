package io.github.baishangui.thunder;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Process;

/** Small, device-protected IPC surface. Only this app, SystemUI, Settings and the SystemUI plugin may call it. */
public final class SettingsProvider extends ContentProvider {
    public static final String AUTHORITY = BuildConfig.APPLICATION_ID + ".settings";
    public static SharedPreferences preferences(Context context) {
        return context.createDeviceProtectedStorageContext().getSharedPreferences("settings", Context.MODE_PRIVATE);
    }
    @Override public boolean onCreate() {
        // <queries> only makes the scope packages visible to us. A URI grant also
        // makes this provider visible to those callers, whose manifests we do not own.
        // Providers initialize before Application.onCreate, including when the
        // framework starts the module's service provider before the UI is opened.
        for (String target : new String[]{"com.android.systemui", "com.oplus.systemui.plugins", "com.android.settings"}) {
            try {
                getContext().grantUriPermission(target, Uri.parse("content://" + AUTHORITY),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (IllegalArgumentException | SecurityException error) {
                // One absent/disabled package must not stop the other scope or this provider.
                android.util.Log.w("Thunder", "Cannot grant settings provider access to " + target, error);
            }
        }
        return true;
    }
    @Override public Bundle call(String method, String arg, Bundle extras) {
        Context context = getContext();
        int uid = Binder.getCallingUid();
        boolean own = uid == Process.myUid();
        boolean systemUi = false;
        try { systemUi = uid == context.getPackageManager().getPackageUid("com.android.systemui", 0); }
        catch (Exception ignored) { }
        boolean plugin = false;
        try { plugin = uid == context.getPackageManager().getPackageUid("com.oplus.systemui.plugins", 0); }
        catch (Exception ignored) { }
        boolean settingsHost = false;
        try { settingsHost = uid == context.getPackageManager().getPackageUid("com.android.settings", 0); }
        catch (Exception ignored) { }
        if (method.startsWith("ui_")) {
            if (!own && !settingsHost) throw new SecurityException("Module/Settings only");
            return SystemSettingsBridge.call(context, method, extras);
        }
        if (!own && !systemUi && !plugin)
            throw new SecurityException("Module/system interfaces only");
        SharedPreferences prefs = preferences(context);
        if ("settings".equals(method)) {
            Bundle result = new Bundle();
            for (String key : new String[]{"volume_size","volume_compact","volume_liquid_entrance"})
                result.putBoolean(key,prefs.getBoolean(key,false));
            result.putInt("volume_width",Math.max(32,Math.min(96,prefs.getInt("volume_width",56))));
            result.putInt("volume_height",Math.max(80,Math.min(400,prefs.getInt("volume_height",240))));
            result.putBoolean("enabled", prefs.getBoolean("enabled", true));
            result.putBoolean("panel_saturation_enabled", prefs.getBoolean("panel_saturation_enabled", false));
            result.putInt("panel_saturation", Math.max(0,Math.min(200,prefs.getInt("panel_saturation",100))));
            result.putBoolean("gesture_bar_hidden", prefs.getBoolean("gesture_bar_hidden", false));
            result.putBoolean("gesture_bar_remove_inset", prefs.getBoolean("gesture_bar_remove_inset", false));
            for (String key : new String[]{"gesture_style", "gesture_colors"})
                result.putBoolean(key, prefs.getBoolean(key, false));
            result.putBoolean("gesture_auto_color", prefs.getBoolean("gesture_auto_color", true));
            result.putInt("gesture_length", Math.max(40, Math.min(320, prefs.getInt("gesture_length", 120))));
            result.putInt("gesture_thickness", Math.max(1, Math.min(16, prefs.getInt("gesture_thickness", 4))));
            result.putInt("gesture_bottom", Math.max(0, Math.min(32, prefs.getInt("gesture_bottom", 6))));
            result.putInt("gesture_light_color", prefs.getInt("gesture_light_color", -1));
            result.putInt("gesture_dark_color", prefs.getInt("gesture_dark_color", 0xff222222));
            result.putBoolean("gesture_blur", prefs.getBoolean("gesture_blur", false));
            result.putInt("gesture_radius", Math.max(0, Math.min(200, prefs.getInt("gesture_radius", 100))));
            result.putBoolean("gesture_tint", prefs.getBoolean("gesture_tint", false));
            result.putInt("gesture_color", prefs.getInt("gesture_color", 0x66ffffff));
            result.putBoolean("gesture_corner_enabled", prefs.getBoolean("gesture_corner_enabled", false));
            result.putInt("gesture_corner", Math.max(0, Math.min(64, prefs.getInt("gesture_corner", 28))));
            result.putBoolean("status_bar_stack_signals", prefs.getBoolean("status_bar_stack_signals", false));
            result.putInt("status_bar_icon_style", prefs.getInt("status_bar_icon_style", 0) == 1 ? 1 : 0);
            result.putInt("status_bar_hidden_icons", prefs.getInt("status_bar_hidden_icons", 0) & StatusBarIcons.ALL);
            for (String key : new String[]{"status_bar_margins", "status_bar_center_enabled", "status_bar_compress_clock",
                    "status_bar_clock", "status_bar_clock_seconds", "status_bar_clock_weekday", "status_bar_clock_bold"})
                result.putBoolean(key, prefs.getBoolean(key, false));
            result.putInt("status_bar_left", Math.max(0, Math.min(64, prefs.getInt("status_bar_left", 16))));
            result.putInt("status_bar_right", Math.max(0, Math.min(64, prefs.getInt("status_bar_right", 16))));
            result.putInt("status_bar_center", Math.max(0, Math.min(80, prefs.getInt("status_bar_center", 8))));
            result.putInt("status_bar_clock_hour_format", Math.max(0, Math.min(2, prefs.getInt("status_bar_clock_hour_format", 0))));
            result.putInt("status_bar_clock_date", Math.max(0, Math.min(2, prefs.getInt("status_bar_clock_date", 0))));
            result.putInt("status_bar_clock_size", Math.max(8, Math.min(24, prefs.getInt("status_bar_clock_size", 14))));
            result.putBoolean("qs_card_tint", prefs.getBoolean("qs_card_tint", false));
            result.putInt("qs_card_color", prefs.getInt("qs_card_color", 0x33ffffff));
            for (String area : new String[]{"panel", "banner", "notification_card", "cloud_card", "volume_bar", "volume_panel"}) {
                if ("notification_card".equals(area) && !prefs.contains("notification_card_radius")) continue;
                result.putBoolean(area+"_blur", prefs.getBoolean(area+"_blur", "panel".equals(area) && prefs.getBoolean("shade_blur", false)));
                result.putInt(area+"_radius", Math.max(0, Math.min(200, prefs.getInt(area+"_radius", "panel".equals(area) ? prefs.getInt("shade_radius", 100) : 100))));
                result.putBoolean(area+"_tint", prefs.getBoolean(area+"_tint", "panel".equals(area) && prefs.getBoolean("shade_tint", false)));
                result.putInt(area+"_color", prefs.getInt(area+"_color", "panel".equals(area) ? prefs.getInt("shade_color", 0x92000000) : "notification_card".equals(area)?0x33ffffff:"cloud_card".equals(area)?0x20282828:0x33000000));
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
            result.putInt("cloud_lock_style",Math.max(0,Math.min(2,prefs.getInt("cloud_lock_style",0))));
            result.putBoolean("cloud_lock_blur",prefs.getBoolean("cloud_lock_blur",false));
            result.putInt("cloud_lock_radius",Math.max(0,Math.min(200,prefs.getInt("cloud_lock_radius",100))));
            result.putBoolean("cloud_lock_tint",prefs.getBoolean("cloud_lock_tint",false));
            result.putInt("cloud_lock_color",prefs.getInt("cloud_lock_color",0x3cd8d8d8));
            result.putBoolean("cloud_app_color",prefs.getBoolean("cloud_app_color",true));
            result.putBoolean("cloud_app_color_strength_enabled",prefs.getBoolean("cloud_app_color_strength_enabled",false));
            result.putInt("cloud_app_color_strength",Math.max(0,Math.min(200,prefs.getInt("cloud_app_color_strength",100))));
            for(String key:new String[]{"qs_card","banner","notification_card","cloud_card","volume_bar","volume_panel"}) {
                if ("notification_card".equals(key) && !prefs.contains("notification_card_radius")) continue;
                result.putBoolean(key+"_corner_enabled",prefs.getBoolean(key+"_corner_enabled",
                    "cloud_card".equals(key)&&prefs.getBoolean("cloud_corner_enabled",false)));
                result.putInt(key+"_corner",Math.max(0,Math.min(64,prefs.getInt(key+"_corner",
                    "cloud_card".equals(key)?prefs.getInt("cloud_corner",20):28))));
            }
            if(!prefs.contains("cloud_card_color")) {
                for(String suffix:new String[]{"blur","tint"})result.putBoolean("cloud_card_"+suffix,prefs.getBoolean("cloud_"+suffix,false));
                result.putInt("cloud_card_radius",prefs.getInt("cloud_radius",100));
                result.putInt("cloud_card_color",prefs.getInt("cloud_color",0x20282828));
            }
            if(prefs.contains("qs_card_radius")) {
                result.putBoolean("qs_card_blur",prefs.getBoolean("qs_card_blur",false));
                result.putInt("qs_card_radius",prefs.getInt("qs_card_radius",100));
            }
            HookConfig config = new HookConfig(result);
            HookConfig.Area cards = config.qsCards;
            result.putBoolean("qs_card_blur",cards.blur); result.putInt("qs_card_radius",cards.radius);
            result.putBoolean("qs_card_tint",cards.tint); result.putInt("qs_card_color",cards.color);
            result.putBoolean("qs_card_corner_enabled",cards.cornerEnabled); result.putInt("qs_card_corner",cards.corner);
            HookConfig.Area rows = config.notificationCard;
            result.putBoolean("notification_card_blur",rows.blur); result.putInt("notification_card_radius",rows.radius);
            result.putBoolean("notification_card_tint",rows.tint); result.putInt("notification_card_color",rows.color);
            result.putBoolean("notification_card_corner_enabled",rows.cornerEnabled); result.putInt("notification_card_corner",rows.corner);
            result.putLong("revision", prefs.getLong("revision", 0));
            return result;
        }
        if ("report".equals(method) && extras != null && (systemUi || plugin)) {
            String process = extras.getString("process", "");
            boolean cloudProcess = "com.oplus.systemui.plugins".equals(process);
            if (cloudProcess && !plugin) throw new SecurityException("Plugin UID required");
            if (!cloudProcess && !(systemUi && (process.isEmpty() || "com.android.systemui".equals(process))))
                throw new IllegalArgumentException("Unsupported report process");
            // The caller cannot change user settings or supply arbitrary preference keys.
            context.createDeviceProtectedStorageContext().getSharedPreferences(cloudProcess ? "runtime_cloud" : "runtime", 0).edit()
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
