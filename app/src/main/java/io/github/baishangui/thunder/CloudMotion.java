package io.github.baishangui.thunder;

/** Analytic spring in screen pixels. Retargeting inherits position and velocity, independent of FPS. */
final class CloudMotion {
    private static final float ARRIVAL_LIMIT=.1f,ARRIVAL_RESPONSE_SECONDS=.012f;
    private static final float HALF_CRITICAL=1.67834699f;
    private final float[] start,end,initial;
    private final float seconds,omega,damping,phaseOmega,carryOmega,rebound;
    private final boolean linearPhaseSafe,linearGeometrySafe;
    private final float[] compression;
    private final boolean linear;
    private final boolean arrival;
    private final float arrivalWidth,arrivalHeight;
    private final float arrivalExcursion,arrivalEventStart;
    // One analytic evaluation per visual tick, shared by position, velocity and
    // the native/independent windows' repeated pre-draw observations.
    private final float[][] samples=new float[3][6];
    private float sampledFraction=Float.NaN,presentedFraction=Float.NaN;
    private CloudMorphHook.Frame sampledFrame,sampledVelocity;

    CloudMotion(CloudMorphHook.Frame from,CloudMorphHook.Frame to,CloudMorphHook.Frame velocity,int duration,int curve,int bounce) {
        this(from,to,velocity,duration,curve,bounce,0);
    }
    CloudMotion(CloudMorphHook.Frame from,CloudMorphHook.Frame to,CloudMorphHook.Frame velocity,int duration,int curve,int bounce,float compressionScale) {
        this(from,to,velocity,duration,curve,bounce,compressionScale,false);
    }
    CloudMotion(CloudMorphHook.Frame from,CloudMorphHook.Frame to,CloudMorphHook.Frame velocity,int duration,int curve,int bounce,float compressionScale,boolean preserveZeroVelocity) {
        this(from,to,velocity,duration,curve,bounce,compressionScale,preserveZeroVelocity,false);
    }
    static CloudMotion arrival(CloudMorphHook.Frame from,CloudMorphHook.Frame to,CloudMorphHook.Frame velocity,int duration,int curve,int bounce) {
        return arrival(from,to,velocity,duration,curve,bounce,false);
    }
    static CloudMotion arrival(CloudMorphHook.Frame from,CloudMorphHook.Frame to,CloudMorphHook.Frame velocity,int duration,int curve,int bounce,boolean preserveZeroVelocity) {
        return new CloudMotion(from,to,velocity,duration,curve,bounce,0,preserveZeroVelocity,true);
    }
    static CloudMotion arrival(CloudMorphHook.Frame from,CloudMorphHook.Frame to,CloudMorphHook.Frame velocity,int duration,int curve,int bounce,boolean preserveZeroVelocity,float nativeExcursionWidthPx,float eventStart) {
        return new CloudMotion(from,to,velocity,duration,curve,bounce,0,preserveZeroVelocity,true,nativeExcursionWidthPx,eventStart);
    }
    private CloudMotion(CloudMorphHook.Frame from,CloudMorphHook.Frame to,CloudMorphHook.Frame velocity,int duration,int curve,int bounce,float compressionScale,boolean preserveZeroVelocity,boolean arrival) {
        this(from,to,velocity,duration,curve,bounce,compressionScale,preserveZeroVelocity,arrival,0,0);
    }
    private CloudMotion(CloudMorphHook.Frame from,CloudMorphHook.Frame to,CloudMorphHook.Frame velocity,int duration,int curve,int bounce,float compressionScale,boolean preserveZeroVelocity,boolean arrival,float nativeExcursionWidthPx,float eventStart) {
        start=values(from);end=values(to);initial=velocity==null?new float[6]:values(velocity);
        this.arrival=arrival;arrivalWidth=Math.max(1,Math.max(from.width(),to.width()));arrivalHeight=Math.max(1,Math.max(from.height(),to.height()));
        arrivalExcursion=arrival&&!preserveZeroVelocity&&Float.isFinite(nativeExcursionWidthPx)?Math.max(0,Math.min(nativeExcursionWidthPx,arrivalWidth*.25f)):0;
        arrivalEventStart=Float.isFinite(eventStart)?Math.max(0,Math.min(1,eventStart)):0;
        float scale=Math.max(0,Math.min(.03f,compressionScale));
        compression=new float[]{to.width()*scale*.5f,to.height()*scale*.5f,-to.width()*scale,-to.height()*scale,-to.radius()*scale,0};
        seconds=Math.max(1,duration)/1000f;linear=curve==3;
        // Ordinary linear transitions restart at constant speed. App events
        // and presentation handoffs supply their actual visible tangent, even
        // at a zero-speed turning point or after cancelling an independent gate.
        if(linear&&(velocity==null||!preserveZeroVelocity&&arrivalExcursion==0&&stopped(initial)))for(int i=0;i<6;i++)initial[i]=(end[i]-start[i])/seconds;
        // Use a continuous physical response again. Match its half-travel time
        // across bounce amounts instead of accelerating it with 1/damping.
        // The size slider sets the spring excursion, never the total duration.
        rebound=.04f*Math.max(0,Math.min(100,bounce))/100f;
        double log=rebound==0?0:Math.log(rebound);
        damping=rebound==0?1f:(float)(-log/Math.sqrt(Math.PI*Math.PI+log*log));
        float response=switch(curve){case 1 -> 8f;case 2 -> 10f;case 3 -> 7f;default -> 9f;};
        omega=response*halfTravel(damping)/(HALF_CRITICAL*seconds);
        carryOmega=response/seconds;
        boolean safeLinear=true;
        for(int i=0;i<5;i++) {
            float delta=end[i]-start[i];
            if(Math.abs(initial[i]*seconds)>5*Math.max(1,Math.abs(delta)))safeLinear=false;
        }
        linearGeometrySafe=safeLinear;
        float phaseOffset=start[5]-end[5];
        phaseOmega=Math.abs(phaseOffset)>.001f?Math.max(response/seconds,-initial[5]/phaseOffset):response/seconds;
        linearPhaseSafe=Math.abs(phaseOffset)<=.001f||initial[5]*seconds/(-phaseOffset)<=5f;
    }
    private static float halfTravel(float damping) {
        if(damping>=.9999f)return HALF_CRITICAL;
        double q=Math.sqrt(1-damping*damping),low=0,high=HALF_CRITICAL;
        for(int i=0;i<18;i++) {
            double at=(low+high)*.5;
            double residual=Math.exp(-damping*at)*(Math.cos(q*at)+damping/q*Math.sin(q*at));
            if(residual>.5)low=at;else high=at;
        }
        return (float)((low+high)*.5);
    }
    private static boolean stopped(float[] speed){for(float value:speed)if(Math.abs(value)>.001f)return false;return true;}
    private static float[] values(CloudMorphHook.Frame f){return new float[]{f.x(),f.y(),f.width(),f.height(),f.radius(),f.progress()};}
    private float sample(int i,float fraction,boolean velocity) {
        return sample(i,fraction,velocity?1:0);
    }
    private float sample(int i,float fraction,int derivative) {
        sampleAll(fraction);
        return samples[derivative][i];
    }
    private float arrivalExcursion(float fraction,int derivative) {
        float u=fraction,v=1-u,span=1-arrivalEventStart,at=arrivalEventStart+span*u;
        float carry=pulse(arrivalEventStart,0),tangent=span*pulse(arrivalEventStart,1);
        // A layout retarget inherits the already presented excursion in from
        // and velocity. Subtract its Hermite carry so this remaining event adds
        // neither a position jump nor another starting impulse.
        if(derivative==2)return (span*span*pulse(at,2)-carry*(12*u-6)-tangent*(6*u-4))/(seconds*seconds);
        if(derivative==1)return (span*pulse(at,1)-carry*6*u*(u-1)-tangent*v*(1-3*u))/seconds;
        return pulse(at,0)-carry*(1+2*u)*v*v-tangent*u*v*v;
    }
    private static float pulse(float u,int derivative) {
        return derivative==2?32*(1-6*u+6*u*u):derivative==1?32*u*(1-u)*(1-2*u):16*u*u*(1-u)*(1-u);
    }
    private void sampleAll(float fraction) {
        if(sampledFraction==fraction)return;
        sampledFraction=fraction;
        if(fraction<=0||fraction>=1) {
            for(int i=0;i<6;i++) {
                samples[0][i]=fraction<=0?start[i]:end[i];
                samples[1][i]=fraction<=0?initial[i]:0;samples[2][i]=0;
            }
            return;
        }
        double u=fraction,t=fraction*seconds;
        // Let the spring run freely through its main travel. Only its tiny
        // settling tail is tapered, with C2 joins at both ends of the taper.
        double tail=Math.max(0,(u-.6)/.4),tail2=tail*tail,tail3=tail2*tail;
        double envelope=1-tail3*(10-15*tail+6*tail2);
        double envelopeSpeed=-30*tail2*(1-tail)*(1-tail)/(.4*seconds);
        double envelopeAcceleration=-60*tail*(1-tail)*(1-2*tail)/(.16*seconds*seconds);
        float v=1-fraction;
        float pulse=16*fraction*fraction*v*v;
        float pulseSpeed=32*fraction*v*(1-2*fraction)/seconds;
        float pulseAcceleration=32*(1-6*fraction+6*fraction*fraction)/(seconds*seconds);
        float excursion=0,excursionSpeed=0,excursionAcceleration=0;
        if(arrivalExcursion!=0) {
            excursion=arrivalExcursion*arrivalExcursion(fraction,0);
            excursionSpeed=arrivalExcursion*arrivalExcursion(fraction,1);
            excursionAcceleration=arrivalExcursion*arrivalExcursion(fraction,2);
        }
        double decay=Math.exp(-damping*omega*t),phaseDecay=Math.exp(-phaseOmega*t),carryDecay=Math.exp(-carryOmega*t);
        double wd=omega*Math.sqrt(Math.max(0,1-damping*damping));
        double sin=Math.sin(wd*t),cos=Math.cos(wd*t);
        for(int i=0;i<6;i++) {
            boolean shape=i==5;
            double delta=end[i]-start[i],value,speed,acceleration;
            if(linear&&(shape?linearPhaseSafe:linearGeometrySafe)){
                double correction=initial[i]*seconds-delta;
                value=-delta*(1-u)+correction*u*v*v;
                speed=(delta+correction*v*(1-3*u))/seconds;
                acceleration=correction*(-4+6*u)/(seconds*seconds);
                // A linear body needs a finite braking segment before its optional
                // rebound. This branch never changes to an ease-out spring.
                double tailLinear=Math.max(0,Math.min(1,(u-.55)/.25));
                double e=1-tailLinear*tailLinear*tailLinear*(10+tailLinear*(-15+6*tailLinear));
                double ev=-30*tailLinear*tailLinear*(1-tailLinear)*(1-tailLinear)/(.25*seconds);
                double ea=-60*tailLinear*(1-tailLinear)*(1-2*tailLinear)/(.0625*seconds*seconds);
                double rise=Math.max(0,Math.min(1,u<=.8?(u-.55)/.25:(1-u)/.2));
                double rate=u<=.8?1/(.25*seconds):-1/(.2*seconds);
                double bounce=shape?0:rebound*rise*rise*rise*(10+rise*(-15+6*rise));
                double bv=shape?0:rebound*30*rise*rise*(1-rise)*(1-rise)*rate;
                double ba=shape?0:rebound*60*rise*(1-rise)*(1-2*rise)*rate*rate;
                samples[0][i]=(float)(end[i]+value*e+delta*bounce);
                samples[1][i]=(float)(speed*e+value*ev+delta*bv);
                samples[2][i]=(float)(acceleration*e+2*speed*ev+value*ea+delta*ba);
            }else {
                double offset=-delta,speed0=initial[i],w=shape?phaseOmega:omega,z=shape?1:damping;
                double decayAt=shape?phaseDecay:decay;
                if(z>=.9999){
                    double b=(shape?speed0:0)+w*offset;
                    value=(offset+b*t)*decayAt;
                    speed=(b-w*(offset+b*t))*decayAt;
                }else{
                    double b=z*w*offset/wd;
                    value=decayAt*(offset*cos+b*sin);
                    speed=decayAt*((-offset*wd*sin+b*wd*cos)-z*w*(offset*cos+b*sin));
                }
                acceleration=-2*z*w*speed-w*w*value;
                if(!shape) {
                    // Damp carried gesture momentum independently; lowering the
                    // rebound damping must not amplify a late reversal's speed.
                    value+=speed0*t*carryDecay;
                    speed+=speed0*(1-carryOmega*t)*carryDecay;
                    acceleration+=speed0*(carryOmega*carryOmega*t-2*carryOmega)*carryDecay;
                }
                samples[0][i]=(float)(end[i]+value*envelope);
                samples[1][i]=(float)(speed*envelope+value*envelopeSpeed);
                samples[2][i]=(float)(acceleration*envelope+2*speed*envelopeSpeed+value*envelopeAcceleration);
            }
            samples[0][i]+=compression[i]*pulse;
            samples[1][i]+=compression[i]*pulseSpeed;
            samples[2][i]+=compression[i]*pulseAcceleration;
            if(i==0||i==2) {
                float factor=i==0?-.5f:1;
                samples[0][i]+=factor*excursion;samples[1][i]+=factor*excursionSpeed;samples[2][i]+=factor*excursionAcceleration;
            }
        }
    }
    CloudMorphHook.Frame frame(float fraction){
        present(fraction);return sampledFrame;
    }
    private void present(float fraction) {
        if(presentedFraction==fraction)return;
        if(arrival&&fraction>0&&fraction<1)arrivalFrame(fraction);
        else {sampledFrame=baseFrame(fraction);sampledVelocity=baseVelocity(fraction);}
        presentedFraction=fraction;
    }
    private CloudMorphHook.Frame baseFrame(float fraction){
        if(fraction<=0)return new CloudMorphHook.Frame(start[0],start[1],start[2],start[3],start[4],start[5]);
        float rawWidth=sample(2,fraction,false),rawHeight=sample(3,fraction,false);
        float width=Math.max(1,rawWidth),height=Math.max(1,rawHeight);
        return new CloudMorphHook.Frame(sample(0,fraction,false)+(rawWidth-width)/2,
            sample(1,fraction,false)+(rawHeight-height)/2,width,height,
            Math.max(0,Math.min(sample(4,fraction,false),Math.min(width,height)/2)),Math.max(0,Math.min(1,sample(5,fraction,false))));
    }
    CloudMorphHook.Frame velocity(float fraction){
        present(fraction);return sampledVelocity;
    }
    private CloudMorphHook.Frame baseVelocity(float fraction){
        if(fraction<=0)return new CloudMorphHook.Frame(initial[0],initial[1],initial[2],initial[3],initial[4],initial[5]);
        float w=sample(2,fraction,false),h=sample(3,fraction,false),r=sample(4,fraction,false);
        float vw=clampedSpeed(w,1,sample(2,fraction,true)),vh=clampedSpeed(h,1,sample(3,fraction,true));
        float limit=Math.min(Math.max(1,w),Math.max(1,h))/2;
        float limitSpeed=minSpeed(Math.max(1,w),Math.max(1,h),vw,vh)/2;
        float vr=minSpeed(r,limit,clampedSpeed(r,0,sample(4,fraction,true)),limitSpeed);
        if(r<0)vr=0;
        return new CloudMorphHook.Frame(sample(0,fraction,true)+(sample(2,fraction,true)-vw)/2,
            sample(1,fraction,true)+(sample(3,fraction,true)-vh)/2,vw,vh,vr,clampedPhaseVelocity(fraction));
    }
    private void arrivalFrame(float fraction) {
        float rawWidth=sample(2,fraction,false),rawHeight=sample(3,fraction,false);
        float baseWidth=Math.max(1,rawWidth),baseHeight=Math.max(1,rawHeight);
        float vw=clampedSpeed(rawWidth,1,sample(2,fraction,true)),vh=clampedSpeed(rawHeight,1,sample(3,fraction,true));
        float aw=rawWidth<1||rawWidth==1&&sample(2,fraction,true)<0?0:sample(2,fraction,2);
        float ah=rawHeight<1||rawHeight==1&&sample(3,fraction,true)<0?0:sample(3,fraction,2);
        float centerVX=sample(0,fraction,true)+vw/2,centerVY=sample(1,fraction,true)+vh/2;
        float centerAX=sample(0,fraction,2)+aw/2,centerAY=sample(1,fraction,2)+ah/2;
        float gate=arrivalGate(fraction,false),gateSpeed=arrivalGate(fraction,true);
        float sx=1-strain(vw,centerVX,arrivalWidth,gate),sy=1-strain(vh,centerVY,arrivalHeight,gate);
        float sxSpeed=-strainSpeed(vw,aw,centerVX,centerAX,arrivalWidth,gate,gateSpeed);
        float sySpeed=-strainSpeed(vh,ah,centerVY,centerAY,arrivalHeight,gate,gateSpeed);
        float rawNewWidth=baseWidth*sx,rawNewHeight=baseHeight*sy;
        float width=Math.max(1,rawNewWidth),height=Math.max(1,rawNewHeight);
        float widthSpeed=clampedSpeed(rawNewWidth,1,vw*sx+baseWidth*sxSpeed);
        float heightSpeed=clampedSpeed(rawNewHeight,1,vh*sy+baseHeight*sySpeed);
        float shortSide=Math.min(baseWidth,baseHeight),newShortSide=Math.min(width,height);
        float shortSpeed=minSpeed(baseWidth,baseHeight,vw,vh),newShortSpeed=minSpeed(width,height,widthSpeed,heightSpeed);
        float rawRadius=sample(4,fraction,false),limit=shortSide/2;
        float radiusSpeed=minSpeed(rawRadius,limit,clampedSpeed(rawRadius,0,sample(4,fraction,true)),shortSpeed/2);
        float baseRadius=Math.max(0,Math.min(rawRadius,limit)),radius=baseRadius*newShortSide/shortSide;
        sampledFrame=new CloudMorphHook.Frame(sample(0,fraction,false)+(baseWidth-width)/2,sample(1,fraction,false)+(baseHeight-height)/2,
            width,height,radius,Math.max(0,Math.min(1,sample(5,fraction,false))));
        float newRadiusSpeed=radiusSpeed*newShortSide/shortSide+baseRadius*(newShortSpeed*shortSide-newShortSide*shortSpeed)/(shortSide*shortSide);
        sampledVelocity=new CloudMorphHook.Frame(centerVX-widthSpeed/2,centerVY-heightSpeed/2,widthSpeed,heightSpeed,newRadiusSpeed,clampedPhaseVelocity(fraction));
    }
    private static float clampedSpeed(float value,float lower,float speed){return value<lower||value==lower&&speed<0?0:speed;}
    private static float minSpeed(float a,float b,float av,float bv){return a<b?av:a>b?bv:Math.min(av,bv);}
    private static float strain(float sizeSpeed,float centerSpeed,float extent,float gate) {
        // Twelve milliseconds of inertial travel, normalized to this form's
        // extent. Slow arrivals deform less; a stationary form has no pulse.
        double rate=(sizeSpeed+Math.sqrt(centerSpeed*centerSpeed+.0001)-.01)/extent;
        return (float)(ARRIVAL_LIMIT*Math.tanh(ARRIVAL_RESPONSE_SECONDS*rate/ARRIVAL_LIMIT)*gate);
    }
    private static float strainSpeed(float sizeSpeed,float sizeAcceleration,float centerSpeed,float centerAcceleration,float extent,float gate,float gateSpeed) {
        double norm=Math.sqrt(centerSpeed*centerSpeed+.0001),rate=(sizeSpeed+norm-.01)/extent;
        double rateSpeed=(sizeAcceleration+centerSpeed*centerAcceleration/norm)/extent,tanh=Math.tanh(ARRIVAL_RESPONSE_SECONDS*rate/ARRIVAL_LIMIT);
        return (float)(ARRIVAL_RESPONSE_SECONDS*(1-tanh*tanh)*rateSpeed*gate+ARRIVAL_LIMIT*tanh*gateSpeed);
    }
    private float arrivalGate(float fraction,boolean derivative) {
        // Only the boundary ramps are timed. The middle follows motion speed,
        // including reversals, rather than a fixed compression pulse.
        if(fraction<.12f)return smoothGate(fraction/.12f,derivative)/(derivative?.12f*seconds:1);
        if(fraction>.82f)return smoothGate((1-fraction)/.18f,derivative)/(derivative?-.18f*seconds:1);
        return derivative?0:1;
    }
    private static float smoothGate(float value,boolean derivative) {
        return derivative?30*value*value*(1-value)*(1-value):value*value*value*(10-15*value+6*value*value);
    }
    float clampedPhaseVelocity(float fraction) {
        float value=sample(5,fraction,false),speed=sample(5,fraction,true);
        // A reversed phase can remain outside the visible interval even after
        // its spring turns back toward it. The clamp is still stationary there.
        return value<0||value>1||value==0&&speed<0||value==1&&speed>0?0:speed;
    }
}
