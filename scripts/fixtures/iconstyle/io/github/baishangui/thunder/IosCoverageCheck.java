package io.github.baishangui.thunder;

/** C16 resource coverage with C17 SIM rows; source geometry is audited by Python. */
public final class IosCoverageCheck {
    public static void main(String[] args) {
        for (String name : new String[]{"stat_sys_airplane_mode", "stat_sys_alarm", "stat_sys_dnd",
                "stat_sys_data_bluetooth", "stat_sys_data_bluetooth_connected", "stat_sys_headset",
                "stat_sys_headset_mic", "stat_sys_location", "stat_sys_nfc",
                "stat_sys_ringer_silent", "stat_sys_ringer_vibrate", "stat_sys_vpn_ic",
                "stat_battery_horizontal_bg", "stat_bt_battery_black_0"})
            IosStatusBarCheck.check(IosStatusResources.replacement("com.android.systemui:drawable/"+name)!=0,"C16 artwork available: "+name);
        for (String name : new String[]{"stat_sys_hotspot", "stat_sys_cast", "stat_sys_rotate_portrait",
                "stat_sys_managed_profile_status", "stat_sys_sos", "ic_wifi_connecting", "ic_wifi_off",
                "stat_sys_high_performance", "stat_sys_data_saver", "stat_battery_vertical_bg",
                "stat_battery_vertical_frame", "stat_battery_pb", "usb_notification_icon"})
            IosStatusBarCheck.check(IosStatusResources.replacement("com.android.systemui:drawable/"+name)==0,"unprovided or incompatible resource remains native: "+name);
        for (int level=0;level<5;level++) {
            int primary=IosStatusResources.replacement("com.android.systemui:drawable/stat_signal_lte_signal_stacked_primary_"+level);
            int secondary=IosStatusResources.replacement("com.android.systemui:drawable/stat_signal_lte_signal_stacked_secondary_"+level);
            int wifi=IosStatusResources.replacement("com.android.systemui:drawable/stat_signal_wifi_signal_"+level+"_os17");
            IosStatusBarCheck.check(primary!=0 && secondary!=0 && primary!=secondary && wifi!=0,"native C17 row/state resources: "+level);
        }
        for (String name : new String[]{"stat_signal_wifi_6", "stat_signal_wifi_7", "stat_signal_activity_wifi_inout_os17",
                "stat_signal_connected_5g", "stat_signal_volte"})
            IosStatusBarCheck.check(IosStatusResources.replacement("com.android.systemui:drawable/"+name)!=0,"transparent badge suppression retained: "+name);
        System.out.println("Verified C16 allowlist, removal of C17-only icons, transparent badges and independent C17 SIM resources.");
    }
}
