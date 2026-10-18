package android.graphics;
public class ColorMatrix {
 public float[] values=new float[20];
 public void setSaturation(float s) {
  float r=.213f*(1-s),g=.715f*(1-s),b=.072f*(1-s);
  values=new float[]{r+s,g,b,0,0,r,g+s,b,0,0,r,g,b+s,0,0,0,0,0,1,0};
 }
}
