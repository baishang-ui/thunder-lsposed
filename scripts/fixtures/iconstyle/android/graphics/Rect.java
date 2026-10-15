package android.graphics;
public class Rect {
 public int left=0,top=0,right=100,bottom=60;
 public Rect(){}public Rect(int l,int t,int r,int b){left=l;top=t;right=r;bottom=b;}
 public int width(){return right-left;}public int height(){return bottom-top;}public boolean isEmpty(){return width()<=0||height()<=0;}
 public float exactCenterX(){return (left+right)/2f;}public float exactCenterY(){return (top+bottom)/2f;}
}
