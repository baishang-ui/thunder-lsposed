package android.graphics;
public class ColorMatrixColorFilter extends ColorFilter {
 public final float[] matrix;
 public ColorMatrixColorFilter(ColorMatrix m){matrix=m.values.clone();}
}
