package android.graphics.drawable;
import android.graphics.*;import android.content.res.ColorStateList;
public class DrawableWrapper extends Drawable implements Drawable.Callback {
 public void getOutline(Outline o){nativeIcon.getOutline(o);}private Drawable nativeIcon;public Drawable getDrawable(){return nativeIcon;}public void setDrawable(Drawable d){nativeIcon=d;}public DrawableWrapper(Drawable d){nativeIcon=d;if(d!=null)d.setCallback(this);}
 public void invalidateDrawable(Drawable d){invalidateSelf();}
 @Override public void draw(Canvas c){nativeIcon.setBounds(getBounds());nativeIcon.draw(c);}
 @Override public int getIntrinsicWidth(){return nativeIcon.getIntrinsicWidth();}@Override public int getIntrinsicHeight(){return nativeIcon.getIntrinsicHeight();}
 @Override public void setAlpha(int a){nativeIcon.setAlpha(a);}@Override public void setColorFilter(ColorFilter f){nativeIcon.setColorFilter(f);}
 @Override public void setTintList(ColorStateList tint){nativeIcon.setTintList(tint);}@Override protected boolean onStateChange(int[] state){return nativeIcon.setState(state);}
}
