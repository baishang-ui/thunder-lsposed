package com.oplusos.systemui.common.blurability.platformblur;
public class PlatformBlurHelper {
 public android.view.View hostView;
 public final Config blurConfig=new Config();
 public PlatformBlurDrawable blurDrawable;
 public static class Config {public boolean isSupportMetaBall;public void setSupportMetaBall(boolean b){isSupportMetaBall=b;}}
 public PlatformBlurHelper(android.view.View view){hostView=view;}
 public void ensureBlurDrawable(){if(blurDrawable==null)blurDrawable=new PlatformBlurDrawable();}
}
