package com.android.systemui.statusbar.phone.ui;

import java.util.HashSet;
import java.util.Set;

/** Synthetic C17 controller: owns the native hide list and rebuilds icon groups. */
public final class StatusBarIconControllerImpl {
    public final Set<String> mIconHideList = new HashSet<>();
    public final Set<String> blockedAtRefresh = new HashSet<>();
    public int refreshes;
    public void onTuningChanged(String key, String value) {
        if (!"icon_blacklist".equals(key)) return;
        mIconHideList.clear();
        if (value != null && !value.isEmpty()) mIconHideList.addAll(Set.of(value.split(",")));
        refreshStatusBarIconList();
    }
    public void refreshStatusBarIconList() {
        blockedAtRefresh.clear();
        blockedAtRefresh.addAll(mIconHideList);
        refreshes++;
    }
}
