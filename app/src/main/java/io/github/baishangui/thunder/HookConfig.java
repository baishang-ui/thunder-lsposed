package io.github.baishangui.thunder;

import android.os.Bundle;

/** Immutable snapshot: no disk or Binder work in drawing callbacks. */
final class HookConfig {
    final boolean enabled;
    final boolean volumeSize, volumeCompact;
    final int volumeWidth, volumeHeight;
    final Area volumeBar, volumePanel;
    final long revision;
    final Area qs, shade, banner, cloudArea, cloudCardArea, notificationCard, qsCards, qsProgress;
    final boolean cloudHeightEnabled, cloudCornerEnabled;
    final int cloudHeight, cloudCorner;
    final boolean margins, cloud;
    final int margin;
    final int hiddenStatusIcons, statusIconStyle;
    final boolean statusBarStackSignals;
    final boolean hideGestureBar, removeGestureBarInset;
    HookConfig(Bundle data) {
        volumeSize = data != null && data.getBoolean("volume_size",false);
        volumeCompact = data != null && data.getBoolean("volume_compact",false);
        volumeWidth = bounded(data,"volume_width",56,32,96);
        volumeHeight = bounded(data,"volume_height",240,120,400);
        volumeBar = new Area(data,"volume_bar");
        volumePanel = new Area(data,"volume_panel");
        enabled = data != null && data.getBoolean("enabled", true);
        revision = data == null ? -1 : data.getLong("revision", 0);
        hideGestureBar = data != null && data.getBoolean("gesture_bar_hidden", false);
        removeGestureBarInset = data != null && data.getBoolean("gesture_bar_remove_inset", false);
        statusBarStackSignals = data != null && data.getBoolean("status_bar_stack_signals",false);
        statusIconStyle = data != null && data.getInt("status_bar_icon_style", 0) == 1 ? 1 : 0;
        hiddenStatusIcons = data == null ? 0 : data.getInt("status_bar_hidden_icons", 0) & StatusBarIcons.ALL;
        shade = new Area(data, data != null && data.containsKey("panel_radius") ? "panel" : "shade");
        qs = shade; banner = new Area(data, "banner");
        qsCards = new Area(data,"qs_card");
        qsProgress = new Area(qsCards);
        // v8–v11 stored one material. Seed rows from it until independent values are saved.
        notificationCard = new Area(data,data != null && data.containsKey("notification_card_radius")
            ? "notification_card" : "qs_card");
        margins = data != null && data.getBoolean("notification_margins", false);
        margin = Math.max(0, Math.min(64, data == null ? 30 : data.getInt("notification_margin", 30)));
        cloud = data != null && data.getBoolean("cloud_enabled", true);
        cloudArea = new Area(data,"cloud");
        cloudCardArea = new Area(data,data!=null && data.containsKey("cloud_card_color")?"cloud_card":"cloud");
        cloudHeightEnabled = data != null && data.getBoolean("cloud_height_enabled",false);
        cloudCornerEnabled = data != null && data.getBoolean("cloud_corner_enabled",false);
        cloudHeight = Math.max(20,Math.min(64,data == null ? 34 : data.getInt("cloud_height",34)));
        cloudCorner = Math.max(0,Math.min(48,data == null ? 20 : data.getInt("cloud_corner",20)));
    }
    static final class Area {
        final boolean blur, tint, cornerEnabled, sharedMaterial;
        final int radius, color, corner;
        /** Progress shares geometry while retaining its native contrast and blur material. */
        Area(Area geometry) {
            blur=false;tint=false;sharedMaterial=true;
            cornerEnabled=geometry.cornerEnabled;corner=geometry.corner;radius=geometry.radius;color=geometry.color;
        }
        Area(Bundle data, String key) {
            sharedMaterial = "qs_card".equals(key);
            cornerEnabled = data != null && data.getBoolean(key+"_corner_enabled",false);
            corner = Math.max(0,Math.min("cloud".equals(key)?48:64,data==null?28:data.getInt(key+"_corner","cloud".equals(key)?20:28)));
            blur = data != null && data.getBoolean(key+"_blur", false);
            tint = data != null && data.getBoolean(key+"_tint", false);
            radius = clamp(data == null ? 100 : data.getInt(key+"_radius", 100));
            int defaultColor=key.startsWith("cloud")?0x20282828:(key.equals("notification_card")||key.equals("qs_card"))?0x33ffffff:0x33000000;
            color = data == null ? defaultColor : data.getInt(key+"_color", defaultColor);
        }
    }
    private static int bounded(Bundle b,String key,int value,int min,int max) {
        return Math.max(min,Math.min(max,b==null?value:b.getInt(key,value)));
    }
    static int clamp(int value) { return Math.max(0, Math.min(200, value)); }
}
