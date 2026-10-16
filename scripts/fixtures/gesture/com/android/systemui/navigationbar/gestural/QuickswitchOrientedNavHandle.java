package com.android.systemui.navigationbar.gestural;

import android.graphics.RectF;
import io.github.baishangui.thunder.GestureBarCheck;

public class QuickswitchOrientedNavHandle extends GestureBarCheck.Handle {
    public int mDeltaRotation;
    public RectF computeHomeHandleBounds() { return new RectF(0, 100, 10, 450); }
}
