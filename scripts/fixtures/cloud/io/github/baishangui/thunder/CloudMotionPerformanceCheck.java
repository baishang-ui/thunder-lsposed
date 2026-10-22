package io.github.baishangui.thunder;

import java.lang.management.ManagementFactory;

/** Host CPU/allocation probe; not an ART, GPU or display frame-rate measurement. */
public final class CloudMotionPerformanceCheck {
    private static volatile CloudMorphHook.Frame positionSink,velocitySink;
    private static void measure(String name,CloudMotion motion) {
        var bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
        long thread=Thread.currentThread().getId();
        int iterations=240000;
        for(int i=0;i<iterations;i++){float p=(i%239+1)/240f;positionSink=motion.frame(p);velocitySink=motion.velocity(p);}
        for(int round=0;round<3;round++) {
            long bytes=bean.getThreadAllocatedBytes(thread),start=System.nanoTime();
            for(int i=0;i<iterations;i++) {
                float p=(i%239+1)/240f;
                positionSink=motion.frame(p);velocitySink=motion.velocity(p);
                // Native and independent windows can observe the same visual tick.
                positionSink=motion.frame(p);velocitySink=motion.velocity(p);
            }
            long elapsed=System.nanoTime()-start,allocated=bean.getThreadAllocatedBytes(thread)-bytes;
            System.out.printf("cloud-%s round=%d ns/tick=%.1f bytes/tick=%.1f%n",name,round+1,
                (double)elapsed/iterations,(double)allocated/iterations);
        }
    }
    public static void main(String[] args) {
        var capsule=new CloudMorphHook.Frame(360,10,240,80,40,0);
        var card=new CloudMorphHook.Frame(100,120,800,400,28,1);
        measure("expand",new CloudMotion(capsule,card,null,500,0,18));
        measure("close",new CloudMotion(card,capsule,null,500,0,18));
        measure("arrival",CloudMotion.arrival(CloudVisibilityState.entrance(capsule),capsule,null,500,0,18,false,16,0));
    }
}
