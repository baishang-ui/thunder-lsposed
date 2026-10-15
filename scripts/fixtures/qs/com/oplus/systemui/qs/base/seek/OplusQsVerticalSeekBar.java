package com.oplus.systemui.qs.base.seek;
public class OplusQsVerticalSeekBar extends android.view.View {
 public boolean isDrawingWithMixColor;public int mBackgroundColor=0xff998877,mProgressColor=0xffeeeeee,invalidations;
 public android.graphics.drawable.Drawable mInactiveTrackDrawable=new android.graphics.drawable.Drawable();
 public float mBackgroundRadius=28f,mCurBackgroundRadius=30.8f,mProgressRadius=28f,mCurProgressRadius=30.8f;
 public float mBackgroundRoundCornerWeight=1.4f,mProgressRoundCornerWeight=1.4f;
 public boolean mIsSupportSmoothRoundCorner=true;
 public int mSmoothStyleType;
 public float getBackgroundRadius(){return mBackgroundRadius;}
 public android.graphics.Rect backgroundRect=new android.graphics.Rect(0,0,0,0);
 public android.graphics.Rect getBackgroundRect(){return backgroundRect;}
 public android.graphics.drawable.Drawable baseMixColorDrawable,activeMixColorDrawable;
 public void invalidate(){invalidations++;}
}
