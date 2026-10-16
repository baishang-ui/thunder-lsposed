package com.oplus.systemui.volume.utils.material;
public class VolumeBlurManager {
 public static final VolumeBlurManager INSTANCE=new VolumeBlurManager();public int shortcutMaterials,accessoryMaterials;public boolean shortcutBlur;
 public android.graphics.drawable.Drawable getVolumeBarBackground(android.content.Context c,android.view.View v,android.view.View host,float r,boolean b){return shortcutBackground(host);}
 public android.graphics.drawable.Drawable getVolumeSettingsButtonBackground(android.content.Context c,android.view.View v,android.view.View root,boolean b){return null;}
 public android.graphics.drawable.Drawable getVolumeSideAccessoryBackground(android.content.Context c,android.view.View v,android.view.View host,boolean b){
  accessoryMaterials++;return new android.graphics.drawable.GradientDrawable();
 }
 private android.graphics.drawable.Drawable shortcutBackground(android.view.View host){
  shortcutMaterials++;
  if(!shortcutBlur)return new android.graphics.drawable.GradientDrawable();
  // Native creation happens before the newly created shortcut hosts are laid out.
  host.actualWidth=host.actualHeight=0;
  com.oplusos.systemui.common.blurability.BlurConfig config=new com.oplusos.systemui.common.blurability.BlurConfig();
  config.platformMixConfig=new com.oplusos.systemui.common.blurability.BlurMixConfig.BlurMixSingleWithShader(new com.oplusos.systemui.common.blurability.MixColorWithShader(5,0xff112233,2,0xff445566));
  return new com.oplusos.systemui.common.blurability.drawable.AutoBlurDrawable(new com.oplusos.systemui.common.blurability.ViewBlurProxy(config));
 }
 public android.graphics.drawable.Drawable getVolumePanelBackground(android.content.Context c,boolean b,android.view.View root,float r){return null;}
}
