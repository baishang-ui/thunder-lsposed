package com.oplus.systemui.volume.utils;

import android.view.View;
import android.widget.FrameLayout;

public final class VolumeLayoutUtil {
    public static void prepareSideVolumeProgressOverflow(View row, FrameLayout host) {}

    public static void prepareSideVolumeProgressOverflow$applyLayout(FrameLayout host, FrameLayout.LayoutParams params,
                                                                   int overflowTop, View row) {
        params.height = host.getHeight() + overflowTop;
        row.setLayoutParams(params);
    }
}
