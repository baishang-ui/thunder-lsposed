package com.oplus.systemui.volume.view;
public class OplusVolumeSideAccessoryButton extends android.widget.FrameLayout {
    public boolean nativeSpotlight;
    public OplusVolumeSideAccessoryButton(android.content.Context context){super(context);}
    public void refreshBlurLightEnableState(boolean defaultTheme){nativeSpotlight=defaultTheme;}
}
