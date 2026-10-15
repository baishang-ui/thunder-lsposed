package android.graphics.drawable;
import android.graphics.*;import android.content.res.ColorStateList;
public class Drawable {public void getOutline(android.graphics.Outline o){}
 public abstract static class ConstantState {}private int[] state=new int[0];private Rect bounds=new Rect();private int alpha=255;private ColorFilter filter;
 private int level;public int getLevel(){return level;}public boolean setLevel(int v){level=v;return true;}public int draws,invalidations;public void draw(Canvas c){draws++;}public int getOpacity(){return -3;}public int getIntrinsicWidth(){return 37;}public int getIntrinsicHeight(){return 21;}
 public int[] getState(){return state;}public boolean setState(int[] value){state=value;return onStateChange(value);}protected boolean onStateChange(int[] value){return true;}public boolean isStateful(){return false;}
 public interface Callback {void invalidateDrawable(Drawable who);}private Callback callback;
 public Callback getCallback(){return callback;}public void setCallback(Callback c){callback=c;}public void invalidateSelf(){invalidations++;if(callback!=null)callback.invalidateDrawable(this);}
 public Rect getBounds(){return bounds;}public void setBounds(Rect b){bounds=b;}public void setBounds(int l,int t,int r,int b){bounds=new Rect(l,t,r,b);}
 public Drawable mutate(){return this;}public void setAlpha(int value){alpha=value;}public int getAlpha(){return alpha;}
 public void clearColorFilter(){setColorFilter(null);}public void setColorFilter(ColorFilter f){filter=f;}public ColorFilter getColorFilter(){return filter;}public void setTintList(ColorStateList tint){}
 public ConstantState getConstantState(){return null;}
}
