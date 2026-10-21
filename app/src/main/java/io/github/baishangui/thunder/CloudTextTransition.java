package io.github.baishangui.thunder;

import java.util.Objects;

/** One TextView's immutable text snapshot and blur-in clock. Dense ticks never rewind it. */
final class CloudTextTransition {
    private String text;
    private boolean initialized,active,hasChange;
    private long started,lastChange;
    private int duration;

    boolean update(String value,long now,int durationMs,boolean animateFirst) {
        String next=value==null?"":value;
        if(initialized&&Objects.equals(text,next))return false;
        boolean first=!initialized;
        int nextDuration=Math.max(0,durationMs);
        boolean quiet=first||!hasChange||now-lastChange>=Math.max(duration,nextDuration);
        sample(now);text=next;initialized=true;lastChange=now;
        if(!first||animateFirst)hasChange=true;
        if(next.isEmpty()){active=false;return true;}
        if((first&&animateFirst)||(!first&&quiet)) {
            started=now;duration=nextDuration;active=duration>0;
        }
        return true;
    }
    String text(){return text;}
    float sample(long now) {
        if(!active)return 1f;
        float p=Math.max(0,Math.min(1,(now-started)/(float)duration));
        if(p>=1){active=false;return 1f;}
        return p*p*(3f-2f*p);
    }
    boolean running(long now){sample(now);return active;}
    void finish(){active=false;}
    void reset(){finish();initialized=hasChange=false;text=null;started=lastChange=0;duration=0;}
}
