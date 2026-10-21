package io.github.baishangui.thunder;

/** Analytic spring in screen pixels. Retargeting inherits position and velocity, independent of FPS. */
final class CloudMotion {
    private static final float ARRIVAL_LIMIT=.1f,ARRIVAL_RESPONSE_SECONDS=.012f;
    private final float[] start,end,initial;
    private final float seconds,omega,damping,geometryOmega,phaseOmega;
    private final boolean closingCapsule,linearGeometrySafe,linearPhaseSafe;
    private final float[] compression;
    private final boolean linear;
    private final boolean arrival;
    private final float arrivalWidth,arrivalHeight;
    private final float arrivalExcursion,arrivalEventStart;

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
        seconds=Math.max(1,duration)/1000f;linear=curve==3&&bounce==0;
        // Ordinary linear transitions restart at constant speed. App events
        // and presentation handoffs supply their actual visible tangent, even
        // at a zero-speed turning point or after cancelling an independent gate.
        if(linear&&(velocity==null||!preserveZeroVelocity&&arrivalExcursion==0&&stopped(initial)))for(int i=0;i<6;i++)initial[i]=(end[i]-start[i])/seconds;
        damping=1f-.5f*Math.max(0,Math.min(100,bounce))/100f;
        float response=switch(curve){case 1 -> 8f;case 2 -> 10f;case 3 -> 7f;default -> 9f;};
        omega=response/(damping*seconds);
        closingCapsule=!arrival&&to.progress()==0&&(from.progress()>0||from.width()>to.width()+.001f||from.height()>to.height()+.001f);
        boolean safeLinear=true;
        float geometricResponse=response/seconds;
        if(closingCapsule)for(int i=0;i<6;i++){
            float offset=start[i]-end[i];
            // Preserve an inherited approach velocity while braking early enough
            // that neither the outline nor its screen position crosses the capsule.
            if(Math.abs(offset)>.001f){
                geometricResponse=Math.max(geometricResponse,-initial[i]/offset);
                // The constant-speed Hermite continuation only stays on the
                // approach side while inherited travel is within five deltas.
                safeLinear&=initial[i]*seconds/(-offset)<=5f;
            }
        }
        geometryOmega=geometricResponse;linearGeometrySafe=safeLinear;
        float phaseOffset=start[5]-end[5];
        phaseOmega=Math.abs(phaseOffset)>.001f?Math.max(response/seconds,-initial[5]/phaseOffset):response/seconds;
        linearPhaseSafe=Math.abs(phaseOffset)<=.001f||initial[5]*seconds/(-phaseOffset)<=5f;
    }
    private static boolean stopped(float[] speed){for(float value:speed)if(Math.abs(value)>.001f)return false;return true;}
    private static float[] values(CloudMorphHook.Frame f){return new float[]{f.x(),f.y(),f.width(),f.height(),f.radius(),f.progress()};}
    private float sample(int i,float fraction,boolean velocity) {
        return sample(i,fraction,velocity?1:0);
    }
    private float sample(int i,float fraction,int derivative) {
        float base=sampleBase(i,fraction,derivative);
        if(fraction<=0||fraction>=1)return base;
        float u=fraction,v=1-u;
        // Same-size service replacement still visibly arrives without jumping
        // to a fabricated starting rectangle. Both endpoint derivatives are zero.
        float pulse=derivative==2?32*(1-6*u+6*u*u)/(seconds*seconds):derivative==1?32*u*v*(1-2*u)/seconds:16*u*u*v*v;
        float value=base+compression[i]*pulse;
        // Native APP_TO_CAPSULE expands past its destination even when old and
        // new services have the same measured width. Retain that actual event
        // travel before deriving inertial feedback; endpoint interpolation alone
        // would otherwise turn the whole event into a stationary rectangle.
        if(arrivalExcursion!=0&&(i==0||i==2))value+=arrivalExcursion*(i==0?-.5f:1)*arrivalExcursion(fraction,derivative);
        return value;
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
    private float sampleBase(int i,float fraction,int derivative) {
        if(fraction<=0)return derivative==0?start[i]:derivative==1?initial[i]:0;
        if(fraction>=1)return derivative==0?end[i]:0;
        boolean shape=closingCapsule||i==5;
        if(linear&&(!closingCapsule||linearGeometrySafe)&&(i!=5||linearPhaseSafe)){
            float delta=end[i]-start[i],u=1-fraction,correction=initial[i]*seconds-delta;
            return derivative==2?correction*(-4+6*fraction)/(seconds*seconds):derivative==1?(delta+correction*u*(1-3*fraction))/seconds:start[i]+delta*fraction+correction*fraction*u*u;
        }
        // A closing card uses one critically damped response for the whole frame.
        // Letting position rebound while dimensions settle carries the card past
        // its capsule and makes followers gather around a moving, offset anchor.
        // Material/reveal progress stays one-way even when expansion geometry
        // rebounds, so a trailing card cannot vanish then reappear at the end.
        double t=fraction*seconds,offset=start[i]-end[i],v=initial[i],w=i==5?phaseOmega:shape?geometryOmega:omega,z=shape?1:damping;
        double decay=Math.exp(-z*w*t),value,speed;
        if(z>=.9999){
            double b=v+w*offset;
            value=(offset+b*t)*decay;
            speed=(b-w*(offset+b*t))*decay;
        }else{
            double wd=w*Math.sqrt(1-z*z),a=offset,b=(v+z*w*offset)/wd;
            double sin=Math.sin(wd*t),cos=Math.cos(wd*t);
            value=decay*(a*cos+b*sin);
            speed=decay*((-a*wd*sin+b*wd*cos)-z*w*(a*cos+b*sin));
        }
        // A physical spring only approaches its goal asymptotically. A quintic
        // envelope closes the residual with zero velocity/acceleration rather
        // than dropping the remaining displacement on the final animator frame.
        double u=fraction,u2=u*u,u3=u2*u;
        double envelope=1-u3*(10-15*u+6*u2);
        double envelopeSpeed=-30*u2*(1-u)*(1-u)/seconds;
        if(derivative==2) {
            double acceleration=-2*z*w*speed-w*w*value;
            double envelopeAcceleration=-60*u*(1-u)*(1-2*u)/(seconds*seconds);
            return (float)(acceleration*envelope+2*speed*envelopeSpeed+value*envelopeAcceleration);
        }
        return (float)(derivative==1?speed*envelope+value*envelopeSpeed:end[i]+value*envelope);
    }
    CloudMorphHook.Frame frame(float fraction){
        return arrival&&fraction>0&&fraction<1?arrivalFrame(fraction,false):baseFrame(fraction);
    }
    private CloudMorphHook.Frame baseFrame(float fraction){
        if(fraction<=0)return new CloudMorphHook.Frame(start[0],start[1],start[2],start[3],start[4],start[5]);
        float width=Math.max(1,sample(2,fraction,false)),height=Math.max(1,sample(3,fraction,false));
        return new CloudMorphHook.Frame(sample(0,fraction,false),sample(1,fraction,false),width,height,
            Math.max(0,Math.min(sample(4,fraction,false),Math.min(width,height)/2)),Math.max(0,Math.min(1,sample(5,fraction,false))));
    }
    CloudMorphHook.Frame velocity(float fraction){
        return arrival&&fraction>0&&fraction<1?arrivalFrame(fraction,true):baseVelocity(fraction);
    }
    private CloudMorphHook.Frame baseVelocity(float fraction){
        return new CloudMorphHook.Frame(sample(0,fraction,true),sample(1,fraction,true),sample(2,fraction,true),sample(3,fraction,true),sample(4,fraction,true),sample(5,fraction,true));
    }
    private CloudMorphHook.Frame arrivalFrame(float fraction,boolean derivative) {
        CloudMorphHook.Frame base=baseFrame(fraction),speed=baseVelocity(fraction);
        float rawWidth=sample(2,fraction,false),rawHeight=sample(3,fraction,false);
        float vw=clampedSpeed(rawWidth,1,speed.width()),vh=clampedSpeed(rawHeight,1,speed.height());
        float aw=rawWidth<1||rawWidth==1&&speed.width()<0?0:sample(2,fraction,2);
        float ah=rawHeight<1||rawHeight==1&&speed.height()<0?0:sample(3,fraction,2);
        float centerVX=speed.x()+vw/2,centerVY=speed.y()+vh/2;
        float centerAX=sample(0,fraction,2)+aw/2,centerAY=sample(1,fraction,2)+ah/2;
        float gate=arrivalGate(fraction,false),gateSpeed=arrivalGate(fraction,true);
        float sx=1-strain(vw,centerVX,arrivalWidth,gate),sy=1-strain(vh,centerVY,arrivalHeight,gate);
        float sxSpeed=-strainSpeed(vw,aw,centerVX,centerAX,arrivalWidth,gate,gateSpeed);
        float sySpeed=-strainSpeed(vh,ah,centerVY,centerAY,arrivalHeight,gate,gateSpeed);
        float rawNewWidth=base.width()*sx,rawNewHeight=base.height()*sy;
        float width=Math.max(1,rawNewWidth),height=Math.max(1,rawNewHeight);
        float widthSpeed=clampedSpeed(rawNewWidth,1,vw*sx+base.width()*sxSpeed);
        float heightSpeed=clampedSpeed(rawNewHeight,1,vh*sy+base.height()*sySpeed);
        float shortSide=Math.min(base.width(),base.height()),newShortSide=Math.min(width,height);
        float shortSpeed=minSpeed(base.width(),base.height(),vw,vh),newShortSpeed=minSpeed(width,height,widthSpeed,heightSpeed);
        float rawRadius=sample(4,fraction,false),limit=shortSide/2;
        float radiusSpeed=minSpeed(rawRadius,limit,clampedSpeed(rawRadius,0,speed.radius()),shortSpeed/2);
        float radius=base.radius()*newShortSide/shortSide;
        if(!derivative)return new CloudMorphHook.Frame(base.x()+(base.width()-width)/2,base.y()+(base.height()-height)/2,width,height,radius,base.progress());
        float newRadiusSpeed=radiusSpeed*newShortSide/shortSide+base.radius()*(newShortSpeed*shortSide-newShortSide*shortSpeed)/(shortSide*shortSide);
        return new CloudMorphHook.Frame(centerVX-widthSpeed/2,centerVY-heightSpeed/2,widthSpeed,heightSpeed,newRadiusSpeed,clampedPhaseVelocity(fraction));
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
