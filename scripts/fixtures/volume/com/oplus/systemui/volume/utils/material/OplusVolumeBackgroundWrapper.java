package com.oplus.systemui.volume.utils.material;
public class OplusVolumeBackgroundWrapper extends android.graphics.drawable.Drawable {
 public final android.graphics.drawable.Drawable content;
 public OplusVolumeBackgroundWrapper(android.graphics.drawable.Drawable d){content=d;}
 public void draw(android.graphics.Canvas canvas){throw new AssertionError("entrance must not clip the tether");}
}
