package io.github.baishangui.thunder;

import android.os.Bundle;

/** Immutable snapshot: no disk or Binder work in drawing callbacks. */
final class HookConfig {
    final boolean enabled;
    final boolean panelSaturationEnabled;
    final int panelSaturation;
    final boolean volumeSize, volumeCompact, volumeLiquidEntrance;
    final int volumeWidth, volumeHeight;
    final Area volumeBar, volumePanel;
    final long revision;
    final Area qs, shade, banner, cloudArea, cloudCardArea, notificationCard, qsCards, qsProgress;
    final boolean cloudHeightEnabled, cloudCornerEnabled;
    final int cloudHeight, cloudCorner;
    final boolean margins, cloud;
    final int cloudLockStyle, cloudAppColorStrength;
    final boolean cloudAppColor, cloudAppColorStrengthEnabled;
    final Area cloudLockArea, cloudLockCustomArea;
    final int margin;
    final int hiddenStatusIcons, statusIconStyle;
    final boolean statusBarStackSignals;
    final boolean statusBarMargins, statusBarCenterEnabled, statusBarCompressClock;
    final int statusBarLeft, statusBarRight, statusBarCenter;
    final boolean statusBarClock, statusBarClockSeconds, statusBarClockWeekday, statusBarClockBold;
    final int statusBarClockHourFormat, statusBarClockDate, statusBarClockSize;
    final boolean hideGestureBar, removeGestureBarInset;
    final boolean gestureStyle, gestureColors, gestureAutoColor;
    final int gestureLength, gestureThickness, gestureBottom, gestureLightColor, gestureDarkColor;
    final Area gestureArea;
    HookConfig(Bundle data) {
        volumeSize = data != null && data.getBoolean("volume_size",false);
        volumeCompact = data != null && data.getBoolean("volume_compact",false);
        volumeLiquidEntrance = data != null && data.getBoolean("volume_liquid_entrance",false);
        volumeWidth = bounded(data,"volume_width",56,32,96);
        volumeHeight = bounded(data,"volume_height",240,80,400);
        volumeBar = new Area(data,"volume_bar");
        volumePanel = new Area(data,"volume_panel");
        enabled = data != null && data.getBoolean("enabled", true);
        revision = data == null ? -1 : data.getLong("revision", 0);
        hideGestureBar = data != null && data.getBoolean("gesture_bar_hidden", false);
        removeGestureBarInset = data != null && data.getBoolean("gesture_bar_remove_inset", false);
        gestureStyle = data != null && data.getBoolean("gesture_style", false);
        gestureColors = data != null && data.getBoolean("gesture_colors", false);
        gestureAutoColor = data == null || data.getBoolean("gesture_auto_color", true);
        gestureLength = bounded(data,"gesture_length",120,40,320);
        gestureThickness = bounded(data,"gesture_thickness",4,1,16);
        gestureBottom = bounded(data,"gesture_bottom",6,0,32);
        gestureLightColor = data == null ? -1 : data.getInt("gesture_light_color",-1);
        gestureDarkColor = data == null ? 0xff222222 : data.getInt("gesture_dark_color",0xff222222);
        gestureArea = new Area(data,"gesture");
        statusBarStackSignals = data != null && data.getBoolean("status_bar_stack_signals",false);
        statusBarMargins = data != null && data.getBoolean("status_bar_margins", false);
        statusBarCenterEnabled = data != null && data.getBoolean("status_bar_center_enabled", false);
        statusBarCompressClock = data != null && data.getBoolean("status_bar_compress_clock", false);
        statusBarLeft = bounded(data,"status_bar_left",16,0,64);
        statusBarRight = bounded(data,"status_bar_right",16,0,64);
        statusBarCenter = bounded(data,"status_bar_center",8,0,80);
        statusBarClock = data != null && data.getBoolean("status_bar_clock", false);
        statusBarClockSeconds = data != null && data.getBoolean("status_bar_clock_seconds", false);
        statusBarClockWeekday = data != null && data.getBoolean("status_bar_clock_weekday", false);
        statusBarClockBold = data != null && data.getBoolean("status_bar_clock_bold", false);
        statusBarClockHourFormat = bounded(data,"status_bar_clock_hour_format",0,0,2);
        statusBarClockDate = bounded(data,"status_bar_clock_date",0,0,2);
        statusBarClockSize = bounded(data,"status_bar_clock_size",14,8,24);
        statusIconStyle = data != null && data.getInt("status_bar_icon_style", 0) == 1 ? 1 : 0;
        hiddenStatusIcons = data == null ? 0 : data.getInt("status_bar_hidden_icons", 0) & StatusBarIcons.ALL;
        panelSaturationEnabled = data != null && data.getBoolean("panel_saturation_enabled",false);
        panelSaturation = bounded(data,"panel_saturation",100,0,200);
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
        // Legacy preset value 1 now selects the saved custom settings as well.
        cloudLockStyle = bounded(data,"cloud_lock_style",0,0,2)==0 ? 0 : 2;
        cloudLockCustomArea = new Area(data,"cloud_lock");
        cloudLockArea = cloudLockCustomArea;
        cloudAppColor = data == null || data.getBoolean("cloud_app_color",true);
        cloudAppColorStrengthEnabled = data != null && data.getBoolean("cloud_app_color_strength_enabled",false);
        cloudAppColorStrength = bounded(data,"cloud_app_color_strength",100,0,200);
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
        Area(boolean blur,int radius,boolean tint,int color,boolean cornerEnabled,int corner) {
            this.blur=blur;this.radius=radius;this.tint=tint;this.color=color;
            this.cornerEnabled=cornerEnabled;this.corner=corner;sharedMaterial=false;
        }
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
            int defaultColor=key.equals("gesture")?0x66ffffff:key.equals("cloud_lock")?0x3cd8d8d8:key.startsWith("cloud")?0x20282828:(key.equals("notification_card")||key.equals("qs_card"))?0x33ffffff:0x33000000;
            color = data == null ? defaultColor : data.getInt(key+"_color", defaultColor);
        }
    }
    private static int bounded(Bundle b,String key,int value,int min,int max) {
        return Math.max(min,Math.min(max,b==null?value:b.getInt(key,value)));
    }
    static int clamp(int value) { return Math.max(0, Math.min(200, value)); }
}
