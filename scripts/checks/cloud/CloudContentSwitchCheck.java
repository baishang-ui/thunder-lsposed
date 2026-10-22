package io.github.baishangui.thunder;

public final class CloudContentSwitchCheck {
    private static int checks;
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
    public static void main(String[] args) {
        Object a=new Object(),b=new Object(),c=new Object();
        CloudContentSwitch<Object> motion=new CloudContentSwitch<>();
        check(!motion.select(a,500,12)&&motion.layers.size()==1,"first content appears without a redundant crossfade");
        check(motion.select(b,500,12),"replacement has an actual transition");
        check(motion.layers.get(0).opacity==1&&motion.layers.get(1).opacity==0,"replacement does not instantly show the incoming service");
        motion.sample(.2f);
        float old=motion.layers.get(0).opacity,offset=motion.layers.get(0).offset;
        check(old>0&&old<1,"outgoing live service remains during the crossfade");
        motion.select(a,500,-12);motion.sample(0);
        check(motion.layers.get(0).opacity==old&&motion.layers.get(0).offset==offset,"reversing preserves the exact displayed weights and offsets");
        motion.sample(.0001f);
        check(Math.abs(motion.layers.get(0).opacity-old)<.001f,"reversal has no first-frame jump");
        motion.select(c,500,12);
        for(int i=0;i<=100;i++) {
            motion.sample(i/100f);float sum=0;
            for(var layer:motion.layers){sum+=layer.opacity;check(Float.isFinite(layer.offset),"finite interrupted offset");}
            check(Math.abs(sum-1)<.0001f,"interrupted crossfades conserve total opacity");
        }
        motion.finish();check(motion.layers.size()==1&&motion.layers.get(0).source==c,"completion releases all outgoing view references");
        check(!motion.select(c,500,12),"same-source layouts never replay the service switch");
        motion.clear();check(motion.layers.isEmpty(),"disposal releases sources");
        motion.select(a,250,12);
        for(int i=0;i<1000;i++)motion.select(new Object(),250,12);
        check(motion.layers.size()==2,"same-frame bursts release never-visible outgoing sources before allocating more trajectories");
        motion.sample(.25f);float burstOpacity=0;
        for(var layer:motion.layers)burstOpacity+=layer.opacity;
        check(Math.abs(burstOpacity-1)<.0001f,"pruning never-visible sources preserves opacity");
        motion.clear();

        System.out.println("Verified "+checks+" interrupted live content switch checks.");
    }
}
