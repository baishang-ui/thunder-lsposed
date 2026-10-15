package android.graphics.drawable;
public class DrawableWrapper extends Drawable {
 private Drawable drawable;
 public DrawableWrapper(Drawable d){drawable=d;}
 public Drawable getDrawable(){return drawable;}
 public void setDrawable(Drawable d){drawable=d;}
}
