package io.github.baishangui.thunder;

/** Shared parameterized curve for expansion and contraction. No frame-time allocation. */
final class CloudMorphTiming {
    private static float cubic(float t,float a,float b) {
        float u=1f-t;return 3f*u*u*t*a+3f*u*t*t*b+t*t*t;
    }
    static float progress(float time,int curve,int bounce) {
        float t=Math.max(0f,Math.min(1f,time));if(t==0f||t==1f)return t;
        float x1=.32f,y1=.72f,x2=0f,y2=1f;
        if(curve==1){x1=.77f;y1=0f;x2=.175f;}
        else if(curve==2){x1=.23f;y1=1f;x2=.32f;}
        float base=t;
        if(curve!=3) {
            float low=0f,high=1f;
            for(int i=0;i<18;i++){float middle=(low+high)*.5f;if(cubic(middle,x1,x2)<t)low=middle;else high=middle;}
            base=cubic((low+high)*.5f,y1,y2);
        }
        if(bounce<=0)return base;
        // One overshoot, one return. No periodic term and no second bounce.
        float tension=Math.min(100,bounce)/50f;
        float u=base-1f;
        return 1f+u*u*((tension+1f)*u+tension);
    }
}
