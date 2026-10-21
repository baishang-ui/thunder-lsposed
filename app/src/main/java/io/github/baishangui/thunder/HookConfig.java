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
    final boolean cloudHeightEnabled, cloudCornerEnabled, cloudAnimationEnabled;
    final int cloudAnimationDuration, cloudAnimationCurve, cloudAnimationBounce;
    final int cloudHeight, cloudCorner;
    final boolean cloudCardPositionEnabled;
    final int cloudCardOffsetX,cloudCardOffsetY;
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
    final Bundle styleData;
    private final java.util.Map<String,Area> tileMaterials = new java.util.HashMap<>();
    HookConfig(Bundle data) { this(data, true); }
    HookConfig(Bundle data, boolean effective) {
        styleData = data == null ? new Bundle() : new Bundle(data);
        if (effective) data = effectiveStyles(data);
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
        // v8–v11 stored one material. Seed rows from it until independent values are saved.
        notificationCard = new Area(data,data != null && data.containsKey("notification_card_radius")
            ? "notification_card" : "qs_card");
        margins = data != null && data.getBoolean("notification_margins", false);
        margin = Math.max(0, Math.min(64, data == null ? 30 : data.getInt("notification_margin", 30)));
        cloud = data != null && data.getBoolean("cloud_enabled", true);
        cloudCardPositionEnabled = data!=null&&data.getBoolean("cloud_card_position_enabled",false);
        cloudCardOffsetX = bounded(data,"cloud_card_offset_x",0,-100,100);
        cloudCardOffsetY = bounded(data,"cloud_card_offset_y",0,-100,300);
        cloudAnimationEnabled = data == null || data.getBoolean("cloud_animation_enabled",true);
        cloudAnimationDuration = bounded(data,"cloud_animation_duration",300,120,1000);
        cloudAnimationCurve = bounded(data,"cloud_animation_curve",0,0,3);
        cloudAnimationBounce = bounded(data,"cloud_animation_bounce",0,0,100);
        // Legacy preset value 1 now selects the saved custom settings as well.
        cloudLockStyle = bounded(data,"cloud_lock_style",0,0,2)==0 ? 0 : 2;
        Area lock=new Area(data,"cloud_lock");
        if(data!=null&&!data.containsKey("cloud_lock_corner_enabled")&&styleData.getBoolean("cloud_card_corner_enabled")&&
                (!effective||!styleData.containsKey("style_cloud_lock")||styleEnabled(styleData,"cloud_lock")))
            lock=new Area(lock.blur,lock.radius,lock.tint,lock.color,true,styleData.getInt("cloud_card_corner",20));
        cloudLockCustomArea = lock;
        cloudLockArea = cloudLockCustomArea;
        cloudAppColor = data == null || data.getBoolean("cloud_app_color",true);
        cloudAppColorStrengthEnabled = data != null && data.getBoolean("cloud_app_color_strength_enabled",false);
        cloudAppColorStrength = bounded(data,"cloud_app_color_strength",100,0,200);
        cloudArea = new Area(data,"cloud");
        cloudCardArea = new Area(data,data!=null && data.containsKey("cloud_card_color")?"cloud_card":"cloud");
        cloudHeightEnabled = data != null && data.getBoolean("cloud_height_enabled",false);
        cloudCornerEnabled = data != null && data.getBoolean("cloud_corner_enabled",false);
        cloudHeight = Math.max(20,Math.min(64,data == null ? 34 : data.getInt("cloud_height",34)));
        cloudCorner = Math.max(0,Math.min(128,data == null ? 20 : data.getInt("cloud_corner",20)));
        for(String key:TILE_KEYS) {
            Area a=new Area(styleData,styleData.containsKey(key+"_radius")?key:"qs_card");
            if(effective&&styleData.containsKey("style_"+key)&&!styleEnabled(styleData,key)) a=new Area(false,a.radius,false,a.color,false,a.corner);
            tileMaterials.put(key,a);
        }
        qsProgress = new Area(styleData.containsKey("style_qs_slider")?tileArea("qs_slider"):qsCards);
    }
    static final String[] STYLE_KEYS = {"panel", "notification", "banner", "qs_small", "qs_wide", "qs_slider", "qs_music",
        "cloud", "cloud_lock", "cloud_notification", "volume", "status_layout", "status_clock", "status_icons", "gesture"};
    static final String[] TILE_KEYS = {"qs_small", "qs_wide", "qs_slider", "qs_music", "cloud_notification"};
    static boolean styleEnabled(Bundle b, String group) {
        if (b == null) return false;
        if (b.containsKey("style_" + group)) return b.getBoolean("style_" + group);
        if (group.startsWith("qs_")) return active(b, "qs_card");
        switch (group) {
            case "panel": return active(b,"panel") || b.getBoolean("panel_saturation_enabled");
            case "notification": return active(b,"notification_card") || b.getBoolean("notification_margins");
            case "banner": return active(b,"banner");
            case "cloud": return b.getBoolean("cloud_enabled",true) && (active(b,"cloud") || active(b,"cloud_card") || b.getBoolean("cloud_card_position_enabled") ||
                b.getBoolean("cloud_height_enabled") || b.getBoolean("cloud_corner_enabled") || b.getBoolean("cloud_app_color_strength_enabled") || !b.getBoolean("cloud_app_color",true));
            case "cloud_lock": return b.getBoolean("cloud_enabled",true) && (b.getInt("cloud_lock_style")!=0 || b.getBoolean("cloud_lock_corner_enabled") || (!b.containsKey("cloud_lock_corner_enabled") && b.getBoolean("cloud_card_corner_enabled")));
            case "cloud_notification": return b.getBoolean("cloud_enabled",true) && (active(b,"notification_card") || b.getBoolean("cloud_card_corner_enabled"));
            case "volume": return active(b,"volume_bar") || active(b,"volume_panel") || b.getBoolean("volume_size") || b.getBoolean("volume_compact") || b.getBoolean("volume_liquid_entrance");
            case "status_layout": return b.getBoolean("status_bar_margins") || b.getBoolean("status_bar_center_enabled") || b.getBoolean("status_bar_compress_clock");
            case "status_clock": return b.getBoolean("status_bar_clock");
            case "status_icons": return b.getInt("status_bar_icon_style")!=0 || b.getInt("status_bar_hidden_icons")!=0 || b.getBoolean("status_bar_stack_signals");
            case "gesture": return b.getBoolean("gesture_style") || b.getBoolean("gesture_bar_hidden") || b.getBoolean("gesture_bar_remove_inset");
            default: return false;
        }
    }
    private static boolean active(Bundle b,String prefix) {
        return b.getBoolean(prefix+"_blur") || b.getBoolean(prefix+"_tint") || b.getBoolean(prefix+"_corner_enabled");
    }
    private static Bundle effectiveStyles(Bundle original) {
        if (original == null) return null;
        Bundle b=new Bundle(original);
        for(String group:STYLE_KEYS) {
            // No style flag means a legacy runtime snapshot; keep its exact semantics.
            if(!b.containsKey("style_"+group)||b.getBoolean("style_"+group))continue;
            String[] prefixes;
            switch(group) {
                case "panel": prefixes=new String[]{"panel_","shade_"};break;
                case "notification": prefixes=new String[]{"notification_"};break;
                case "banner": prefixes=new String[]{"banner_"};break;
                case "volume": prefixes=new String[]{"volume_"};break;
                case "status_layout": prefixes=new String[]{"status_bar_margins","status_bar_center_enabled","status_bar_compress_clock"};break;
                case "status_clock": prefixes=new String[]{"status_bar_clock"};break;
                case "status_icons": prefixes=new String[]{"status_bar_stack_signals"};b.putInt("status_bar_icon_style",0);b.putInt("status_bar_hidden_icons",0);break;
                case "gesture": prefixes=new String[]{"gesture_"};break;
                case "cloud": prefixes=new String[]{"cloud_blur","cloud_tint","cloud_height_enabled","cloud_corner_enabled","cloud_card_"};
                    b.putBoolean("cloud_app_color",true);b.putBoolean("cloud_app_color_strength_enabled",false);break;
                case "cloud_lock": prefixes=new String[]{"cloud_lock_"};b.putInt("cloud_lock_style",0);break;
                default: prefixes=new String[]{group+"_"};
            }
            for(String key:new java.util.ArrayList<>(b.keySet()))for(String prefix:prefixes)
                if(key.startsWith(prefix) && b.get(key) instanceof Boolean)b.putBoolean(key,false);
        }
        if(original.containsKey("style_status_clock")&&styleEnabled(original,"status_clock"))b.putBoolean("status_bar_clock",true);
        if(original.containsKey("style_gesture")&&styleEnabled(original,"gesture"))b.putBoolean("gesture_style",true);
        if(original.containsKey("style_cloud_lock")&&styleEnabled(original,"cloud_lock"))b.putInt("cloud_lock_style",2);
        if(original.containsKey("style_cloud")) b.putBoolean("cloud_enabled",
            styleEnabled(original,"cloud")||styleEnabled(original,"cloud_lock")||styleEnabled(original,"cloud_notification"));
        return b;
    }
    Area tileArea(String key) { return tileMaterials.getOrDefault(key,qsCards); }
    Area cloudNotificationArea() {
        if(!styleData.containsKey("style_cloud_notification"))return new Area(notificationCard.blur,notificationCard.radius,notificationCard.tint,notificationCard.color,cloud&&cloudCardArea.cornerEnabled,cloudCardArea.corner);
        return tileArea("cloud_notification");
    }
    Area lockCardArea() {
        if(!styleData.containsKey("style_cloud_lock"))return new Area(cloudLockArea.blur,cloudLockArea.radius,cloudLockArea.tint,cloudLockArea.color,cloudCardArea.cornerEnabled,cloudCardArea.corner);
        return cloudLockArea;
    }
    int appColorStrength(String group) {
        if(!enabled||!cloud||!scopedCloud(group))return 100;
        return !cloudAppColor?0:cloudAppColorStrengthEnabled?cloudAppColorStrength:100;
    }
    boolean scopedCloud(String group) {
        return styleData.containsKey("style_"+group)?styleEnabled(styleData,group):cloud;
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
            corner = Math.max(0,Math.min("cloud".equals(key)?128:64,data==null?28:data.getInt(key+"_corner","cloud".equals(key)?20:28)));
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
