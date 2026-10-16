package android.view.animation;

/** Cubic Bezier time mapping, sampled explicitly by the animation fixture. */
public final class PathInterpolator {
    final float x1,y1,x2,y2;
    public PathInterpolator(float a,float b,float c,float d){x1=a;y1=b;x2=c;y2=d;}
    float cubic(float t,float a,float b){float u=1-t;return 3*u*u*t*a+3*u*t*t*b+t*t*t;}
    public float getInterpolation(float x){
        float low=0,high=1;
        for(int i=0;i<24;i++){float mid=(low+high)/2;if(cubic(mid,x1,x2)<x)low=mid;else high=mid;}
        return cubic((low+high)/2,y1,y2);
    }
}
