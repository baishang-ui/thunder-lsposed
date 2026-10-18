package com.oplusos.systemui.common.blurability;

public class ViewBlurProxy {
    public BlurConfig config;
    // C17 initializes the proxy at full amount; Scrim supplies animation frames.
    public float blurAmount=1f;
    public int applied,invalidatedPaths;
    public ViewBlurProxy(BlurConfig c){config=c;}
    public BlurConfig getBlurConfig(){return config;}
    public static class BlurTypePlatformStatic {}
    public Object getBlurType(){return new BlurTypePlatformStatic();}
    public void invalidatePath(){invalidatedPaths++;}
    public void applyBlurConfig(){applied++;}
}
