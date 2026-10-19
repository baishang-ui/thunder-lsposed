package com.android.systemui.statusbar.notification.row;
/** C17 setRadius updates the focus overlay and then calls the extension directly. */
public class NotificationBackgroundView extends android.view.View {
    public float top,bottom,strokeRadius,shadowRadius,focusRadius;
    public void setRadius(float top,float bottom,boolean smooth){
        this.top=top;this.bottom=bottom;
        focusRadius=Math.max(0,top-1);
        // The extension writes BaseDrawable.setCornerParams without applyBlurConfig.
        strokeRadius=top;shadowRadius=top;
    }
}
