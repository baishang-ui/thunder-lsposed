package android.graphics;
public class Outline {
 public boolean smooth;public float radius;public int left,top,right,bottom;
 public void setRoundRect(int l,int t,int r,int b,float radius){left=l;top=t;right=r;bottom=b;this.radius=radius;}
 public void setPath(Path path){radius=path.radius;smooth=path.smooth;if(path.bounds!=null){left=path.bounds.left;top=path.bounds.top;right=path.bounds.right;bottom=path.bounds.bottom;}}
}
