package io.github.baishangui.thunder;

import android.content.SharedPreferences;
import android.os.Handler;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Keep this object alive: framework preference listeners may be held weakly. */
final class LiveSettings {
    private final SharedPreferences preferences;
    private final Consumer<Map<String, ?>> loaded;
    private final Consumer<Throwable> failed;
    private final Runnable reload = this::reload;
    private final SharedPreferences.OnSharedPreferenceChangeListener listener;
    private long revision = Long.MIN_VALUE;

    LiveSettings(SharedPreferences preferences, Handler worker,
            Consumer<Map<String, ?>> loaded, Consumer<Throwable> failed) {
        this.preferences = preferences;
        this.loaded = loaded;
        this.failed = failed;
        listener = (prefs, key) -> {
            // One atomic commit sends many key notifications. Refresh once, off the Binder thread.
            if (key == null || "revision".equals(key)) {
                worker.removeCallbacks(reload);
                worker.post(reload);
            }
        };
    }

    void start() {
        preferences.registerOnSharedPreferenceChangeListener(listener);
        reload();
    }

    private synchronized void reload() {
        try {
            Map<String, ?> values = new HashMap<>(preferences.getAll());
            Object next = values.get("revision");
            if (!(next instanceof Long) || (Long) next < 0)
                throw new IllegalStateException("Open module app to sync API 102 settings");
            if ((Long) next <= revision) return;
            loaded.accept(values);
            revision = (Long) next;
        } catch (Throwable error) {
            failed.accept(error);
        }
    }
}
