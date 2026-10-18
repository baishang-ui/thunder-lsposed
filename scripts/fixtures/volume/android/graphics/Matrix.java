package android.graphics;
public class Matrix {
 public float tx,ty;
 public void setValues(float[] values){}
 public void reset(){tx=ty=0;}
 public boolean invert(Matrix out){out.tx=-tx;out.ty=-ty;return true;}
 public void mapPoints(float[] points){for(int i=0;i+1<points.length;i+=2){points[i]+=tx;points[i+1]+=ty;}}
}
