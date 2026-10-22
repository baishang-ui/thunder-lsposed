package io.github.baishangui.thunder;

/** Runs the production analytic trajectory on ART without modifying SystemUI. */
public final class CloudMotionDeviceCheck {
    private static volatile CloudMorphHook.Frame positionSink,velocitySink;
    private static void tick(CloudMotion motion,int i) {
        float p=(i%239+1)/240f;
        positionSink=motion.frame(p);velocitySink=motion.velocity(p);
        positionSink=motion.frame(p);velocitySink=motion.velocity(p);
    }
    private static void measure(String name,CloudMotion motion) {
        int iterations=120000;
        for(int i=0;i<iterations;i++)tick(motion,i);
        for(int round=0;round<3;round++) {
            long start=System.nanoTime();
            for(int i=0;i<iterations;i++)tick(motion,i);
            System.out.printf("ART cloud-%s round=%d ns/tick=%.1f%n",name,round+1,(System.nanoTime()-start)/(double)iterations);
        }
    }
    public static void main(String[] args) {
        try{run(args);}catch(Throwable failure){failure.printStackTrace(System.err);System.exit(1);}
    }
    private static void run(String[] args)throws Exception {
        if(args.length>0&&args[0].equals("--content-only")){CloudCapsuleContentDeviceCheck.main(new String[0]);return;}
        if(args.length==0||!args[0].equals("--benchmark-only"))CloudMotionCheck.main(new String[0]);
        var capsule=new CloudMorphHook.Frame(360,10,240,80,40,0);
        var card=new CloudMorphHook.Frame(100,120,800,400,28,1);
        measure("expand",new CloudMotion(capsule,card,null,500,0,18));
        measure("close",new CloudMotion(card,capsule,null,500,0,18));
        measure("arrival",CloudMotion.arrival(CloudVisibilityState.entrance(capsule),capsule,null,500,0,18,false,16,0));
        System.out.println("ART trajectory CPU probe complete; does not measure GPU rendering or displayed frame rate.");
    }
}
