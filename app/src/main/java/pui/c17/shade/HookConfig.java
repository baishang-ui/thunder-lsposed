package pui.c17.shade;

import android.os.Bundle;

/** Immutable snapshot: no disk or Binder work in drawing callbacks. */
final class HookConfig {
    final boolean enabled;
    final long revision;
    final Area qs, shade, banner, cloudArea;
    final boolean cloudHeightEnabled, cloudCornerEnabled;
    final int cloudHeight, cloudCorner;
    final boolean margins, cloud;
    final int margin;
    HookConfig(Bundle data) {
        enabled = data != null && data.getBoolean("enabled", true);
        revision = data == null ? -1 : data.getLong("revision", 0);
        shade = new Area(data, data != null && data.containsKey("panel_radius") ? "panel" : "shade");
        qs = shade; banner = new Area(data, "banner");
        margins = data != null && data.getBoolean("notification_margins", false);
        margin = Math.max(0, Math.min(64, data == null ? 30 : data.getInt("notification_margin", 30)));
        cloud = data != null && data.getBoolean("cloud_pui", true);
        cloudArea = new Area(data,"cloud");
        cloudHeightEnabled = data != null && data.getBoolean("cloud_height_enabled",false);
        cloudCornerEnabled = data != null && data.getBoolean("cloud_corner_enabled",false);
        cloudHeight = Math.max(20,Math.min(64,data == null ? 34 : data.getInt("cloud_height",34)));
        cloudCorner = Math.max(0,Math.min(48,data == null ? 20 : data.getInt("cloud_corner",20)));
    }
    static final class Area {
        final boolean blur, tint;
        final int radius, color;
        Area(Bundle data, String key) {
            blur = data != null && data.getBoolean(key+"_blur", false);
            tint = data != null && data.getBoolean(key+"_tint", false);
            radius = clamp(data == null ? 100 : data.getInt(key+"_radius", 100));
            color = data == null ? 0x33000000 : data.getInt(key+"_color", 0x33000000);
        }
    }
    static int clamp(int value) { return Math.max(0, Math.min(200, value)); }
}
