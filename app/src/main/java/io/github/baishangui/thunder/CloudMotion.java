package io.github.baishangui.thunder;

/** Analytic spring in screen pixels. Retargeting inherits position and velocity, independent of FPS. */
final class CloudMotion {
    private final float[] start,end,initial;
    private final float seconds,omega,damping,geometryOmega,phaseOmega;
    private final boolean closingCapsule,linearGeometrySafe,linearPhaseSafe;
    private final float[] compression;
    private final boolean linear;

    CloudMotion(CloudMorphHook.Frame from,CloudMorphHook.Frame to,CloudMorphHook.Frame velocity,int duration,int curve,int bounce) {
        this(from,to,velocity,duration,curve,bounce,0);
    }
    CloudMotion(CloudMorphHook.Frame from,CloudMorphHook.Frame to,CloudMorphHook.Frame velocity,int duration,int curve,int bounce,float compressionScale) {
        this(from,to,velocity,duration,curve,bounce,compressionScale,false);
    }
    CloudMotion(CloudMorphHook.Frame from,CloudMorphHook.Frame to,CloudMorphHook.Frame velocity,int duration,int curve,int bounce,float compressionScale,boolean preserveZeroVelocity) {
        start=values(from);end=values(to);initial=velocity==null?new float[6]:values(velocity);
        float scale=Math.max(0,Math.min(.03f,compressionScale));
        compression=new float[]{to.width()*scale*.5f,to.height()*scale*.5f,-to.width()*scale,-to.height()*scale,-to.radius()*scale,0};
        seconds=Math.max(1,duration)/1000f;linear=curve==3&&bounce==0;
        // Ordinary linear transitions restart at constant speed. A presentation
        // handoff can explicitly cancel an independent gate down to zero;
        // replacing that supplied zero would add the gate's motion back.
        if(linear&&(velocity==null||!preserveZeroVelocity&&stopped(initial)))for(int i=0;i<6;i++)initial[i]=(end[i]-start[i])/seconds;
        damping=1f-.5f*Math.max(0,Math.min(100,bounce))/100f;
        float response=switch(curve){case 1 -> 8f;case 2 -> 10f;case 3 -> 7f;default -> 9f;};
        omega=response/(damping*seconds);
        closingCapsule=to.progress()==0&&(from.progress()>0||from.width()>to.width()+.001f||from.height()>to.height()+.001f);
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
        float base=sampleBase(i,fraction,velocity);
        if(fraction<=0||fraction>=1||compression[i]==0)return base;
        float u=fraction,v=1-u;
        // Same-size service replacement still visibly arrives without jumping
        // to a fabricated starting rectangle. Both endpoint derivatives are zero.
        float pulse=velocity?32*u*v*(1-2*u)/seconds:16*u*u*v*v;
        return base+compression[i]*pulse;
    }
    private float sampleBase(int i,float fraction,boolean velocity) {
        if(fraction<=0)return velocity?initial[i]:start[i];
        if(fraction>=1)return velocity?0:end[i];
        boolean shape=closingCapsule||i==5;
        if(linear&&(!closingCapsule||linearGeometrySafe)&&(i!=5||linearPhaseSafe)){
            float delta=end[i]-start[i],u=1-fraction,correction=initial[i]*seconds-delta;
            return velocity?(delta+correction*u*(1-3*fraction))/seconds:start[i]+delta*fraction+correction*fraction*u*u;
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
        return (float)(velocity?speed*envelope+value*envelopeSpeed:end[i]+value*envelope);
    }
    CloudMorphHook.Frame frame(float fraction){
        if(fraction<=0)return new CloudMorphHook.Frame(start[0],start[1],start[2],start[3],start[4],start[5]);
        float width=Math.max(1,sample(2,fraction,false)),height=Math.max(1,sample(3,fraction,false));
        return new CloudMorphHook.Frame(sample(0,fraction,false),sample(1,fraction,false),width,height,
            Math.max(0,Math.min(sample(4,fraction,false),Math.min(width,height)/2)),Math.max(0,Math.min(1,sample(5,fraction,false))));
    }
    CloudMorphHook.Frame velocity(float fraction){
        return new CloudMorphHook.Frame(sample(0,fraction,true),sample(1,fraction,true),sample(2,fraction,true),sample(3,fraction,true),sample(4,fraction,true),sample(5,fraction,true));
    }
    float clampedPhaseVelocity(float fraction) {
        float value=sample(5,fraction,false),speed=sample(5,fraction,true);
        // A reversed phase can remain outside the visible interval even after
        // its spring turns back toward it. The clamp is still stationary there.
        return value<0||value>1||value==0&&speed<0||value==1&&speed>0?0:speed;
    }
}
