package android.graphics.drawable;
import android.content.res.ColorStateList;
public class GradientDrawable extends Drawable {
 public final State mGradientState=new State();public final Ext mGradientDrawableExt=new Ext();
 public final class State extends ConstantState {
  public int mSmoothType=-1,mStrokeWidth=-1;public float mSmoothWeight,mStrokeDashWidth,mStrokeDashGap;public boolean smooth;public ColorStateList mStrokeColors;
  public Drawable newDrawable(){GradientDrawable d=new GradientDrawable();d.radius=radius;d.radii=radii==null?null:radii.clone();d.color=color;d.colors=colors==null?null:colors.clone();d.orientation=orientation;d.mGradientState.mSmoothType=mSmoothType;d.mGradientState.mSmoothWeight=mSmoothWeight;d.mGradientState.smooth=smooth;if(mStrokeWidth>=0)d.setStroke(mStrokeWidth,mStrokeColors,mStrokeDashWidth,mStrokeDashGap);return d;}
 }
 public class Ext {
  public boolean getSmoothRoundStyle(){return mGradientState.smooth;}public int getGradientStateSmoothWeightStyle(){return mGradientState.mSmoothType;}public float getSmoothWeight(){return mGradientState.mSmoothWeight;}
  public void setSmoothRoundStyle(boolean s,float w,int t){mGradientState.smooth=s;mGradientState.mSmoothType=t;mGradientState.mSmoothWeight=w;}
 }
 public enum Orientation {TOP_BOTTOM,LEFT_RIGHT,BL_TR}
 private Orientation orientation=Orientation.TOP_BOTTOM;
 public Orientation getOrientation(){return orientation;}public void setOrientation(Orientation value){orientation=value;}
 public int strokeWidth,strokeColor;public void setStroke(int width,int color){setStroke(width,ColorStateList.valueOf(color),0,0);}
 public void setStroke(int width,ColorStateList color,float dashWidth,float dashGap){if(width<0)throw new IllegalArgumentException("negative native stroke width");strokeWidth=width;strokeColor=color==null?0:color.getDefaultColor();mGradientState.mStrokeWidth=width;mGradientState.mStrokeColors=color;mGradientState.mStrokeDashWidth=dashWidth;mGradientState.mStrokeDashGap=dashGap;}
 public float radius;public float[] radii;public ColorStateList color=ColorStateList.valueOf(0xff123456);public int[] colors;
 public void setCornerRadius(float r){radius=r;radii=null;}public float getCornerRadius(){return radius;}public float[] getCornerRadii(){return radii;}public void setCornerRadii(float[] r){radii=r;}
 public ColorStateList getColor(){return color;}public void setColor(int c){setColor(ColorStateList.valueOf(c));}public void setColor(ColorStateList c){color=c;colors=null;}public int[] getColors(){return colors;}public void setColors(int[] c){colors=c;}
 @Override public ConstantState getConstantState(){return mGradientState;}
}
