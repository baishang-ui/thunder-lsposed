package io.github.baishangui.thunder;

import java.lang.management.ManagementFactory;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.function.Function;

/** Stable pre-draw CPU/allocation probe. Host JVM only, not Android FPS. */
public final class CloudCardRosterPerformanceCheck {
    private static volatile CloudCardRoster.Change<Object> sink;
    public static void main(String[] args) {
        var bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
        long thread=Thread.currentThread().getId();
        var roster=new CloudCardRoster<Object>();
        List<Object> cards=List.of(new Object(),new Object(),new Object());
        var content=new IdentityHashMap<Object,Object>();
        for(Object card:cards)content.put(card,new Object());
        Function<Object,Object> source=content::get;
        for(int i=0;i<100000;i++)sink=roster.sync(cards,source);
        int iterations=240000;
        for(int round=0;round<3;round++) {
            long bytes=bean.getThreadAllocatedBytes(thread),start=System.nanoTime();
            for(int i=0;i<iterations;i++)sink=roster.sync(cards,source);
            long elapsed=System.nanoTime()-start,allocated=bean.getThreadAllocatedBytes(thread)-bytes;
            System.out.printf("cloud-stable-roster round=%d ns/tick=%.1f bytes/tick=%.1f%n",round+1,
                (double)elapsed/iterations,(double)allocated/iterations);
        }
    }
}
