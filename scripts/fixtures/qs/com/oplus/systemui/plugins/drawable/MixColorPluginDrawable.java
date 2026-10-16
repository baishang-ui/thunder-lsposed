package com.oplus.systemui.plugins.drawable;
public class MixColorPluginDrawable extends PluginDrawable {
 public static class BuilderConfig {
  public PluginDrawable build(android.view.View view,boolean reuse){return new PluginDrawable();}
 }
 public com.oplusos.systemui.common.blurability.drawable.AutoBlurDrawable autoBlurDrawable;
 public MixColorPluginDrawable(com.oplusos.systemui.common.blurability.drawable.AutoBlurDrawable bg){autoBlurDrawable=bg;}
}
