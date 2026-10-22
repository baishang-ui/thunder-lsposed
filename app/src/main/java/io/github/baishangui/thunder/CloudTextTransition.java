package io.github.baishangui.thunder;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Immutable grapheme snapshots, independent edit clocks and a separate scene reveal. */
final class CloudTextTransition {
    private record Glyph(String value,int start,int end,long started,int duration,boolean rolling) {
        float sample(long now){return phase(now,started,duration);}
    }
    private String text;
    private List<Glyph> glyphs=List.of();
    private long sceneStarted;
    private int sceneDuration;

    boolean update(String value,long now,int durationMs,boolean animateFirst) {
        String next=value==null?"":value;
        if(Objects.equals(text,next))return false;
        boolean first=text==null;
        List<Glyph> incoming=split(next);
        int[] matches=matches(glyphs,incoming);
        List<Glyph> result=new ArrayList<>(incoming.size());
        int duration=Math.max(0,durationMs);
        boolean scene=scene(now);
        for(int i=0;i<incoming.size();i++) {
            Glyph item=incoming.get(i),old=matches[i]<0?null:glyphs.get(matches[i]);
            if(scene)result.add(new Glyph(item.value,item.start,item.end,sceneStarted,sceneDuration,false));
            else if(old!=null)result.add(new Glyph(item.value,item.start,item.end,old.started,old.duration,old.rolling));
            else result.add(new Glyph(item.value,item.start,item.end,now,first&&!animateFirst?0:duration,!first||animateFirst));
        }
        text=next;glyphs=result;
        return true;
    }
    void reveal(long now,int durationMs) {
        sceneStarted=now;sceneDuration=Math.max(0,durationMs);
        List<Glyph> result=new ArrayList<>(glyphs.size());
        for(Glyph item:glyphs)result.add(new Glyph(item.value,item.start,item.end,now,sceneDuration,false));
        glyphs=result;
    }
    boolean revealing(long now){return scene(now);}
    private boolean scene(long now){return phase(now,sceneStarted,sceneDuration)<1;}
    void copyFrom(CloudTextTransition previous){
        text=previous.text;glyphs=previous.glyphs;sceneStarted=previous.sceneStarted;sceneDuration=previous.sceneDuration;
    }
    String text(){return text;}
    int count(){return glyphs.size();}
    int start(int index){return glyphs.get(index).start;}
    int end(int index){return glyphs.get(index).end;}
    float sample(int index,long now){return glyphs.get(index).sample(now);}
    float roll(int index,long now){return glyphs.get(index).rolling?1-sample(index,now):0;}
    boolean running(long now){for(Glyph glyph:glyphs)if(glyph.sample(now)<1)return true;return false;}
    void finish(){
        sceneDuration=0;
        List<Glyph> result=new ArrayList<>(glyphs.size());
        for(Glyph item:glyphs)result.add(new Glyph(item.value,item.start,item.end,0,0,false));
        glyphs=result;
    }
    void reset(){glyphs=List.of();text=null;sceneStarted=0;sceneDuration=0;}
    private static float phase(long now,long start,int duration) {
        if(duration<=0)return 1;
        float p=Math.max(0,Math.min(1,(now-start)/(float)duration));
        // Fast arrival with a soft landing, shared by opacity, blur and vertical roll.
        return 1-(1-p)*(1-p)*(1-p);
    }
    private static int[] matches(List<Glyph> before,List<Glyph> after) {
        int n=before.size(),m=after.size();int[] result=new int[m];Arrays.fill(result,-1);
        // Equal-length timers and counters compare positions: 09 -> 10 changes both digits.
        if(n==m){for(int i=0;i<m;i++)if(before.get(i).value.equals(after.get(i).value))result[i]=i;return result;}
        int prefix=0;
        while(prefix<Math.min(n,m)&&before.get(prefix).value.equals(after.get(prefix).value)){result[prefix]=prefix;prefix++;}
        int endA=n,endB=m;
        while(endA>prefix&&endB>prefix&&before.get(endA-1).value.equals(after.get(endB-1).value))result[--endB]=--endA;
        // Bound work on long card paragraphs; common prefix/suffix remain stable even then.
        if((long)(endA-prefix+1)*(endB-prefix+1)>65536)return result;
        int[][] lcs=new int[endA-prefix+1][endB-prefix+1];
        for(int i=endA-1;i>=prefix;i--)for(int j=endB-1;j>=prefix;j--)
            lcs[i-prefix][j-prefix]=before.get(i).value.equals(after.get(j).value)?1+lcs[i-prefix+1][j-prefix+1]:
                Math.max(lcs[i-prefix+1][j-prefix],lcs[i-prefix][j-prefix+1]);
        for(int i=prefix,j=prefix;i<endA&&j<endB;) {
            if(before.get(i).value.equals(after.get(j).value)){result[j++]=i++;}
            else if(lcs[i-prefix+1][j-prefix]>=lcs[i-prefix][j-prefix+1])i++;else j++;
        }
        return result;
    }
    private static List<Glyph> split(String value) {
        List<Glyph> result=new ArrayList<>();
        BreakIterator breaks=BreakIterator.getCharacterInstance(Locale.ROOT);breaks.setText(value);
        int start=breaks.first();
        for(int end=breaks.next();end!=BreakIterator.DONE;end=breaks.next()) {
            if(end<value.length()) {
                int next=value.codePointAt(end),previous=value.codePointBefore(end);
                int type=Character.getType(next);
                if(next==0x200d||previous==0x200d||type==Character.NON_SPACING_MARK||type==Character.COMBINING_SPACING_MARK||
                        next>=0x1f3fb&&next<=0x1f3ff||next>=0xe0020&&next<=0xe007f)continue;
                if(previous>=0x1f1e6&&previous<=0x1f1ff&&next>=0x1f1e6&&next<=0x1f1ff&&value.codePointCount(start,end)%2==1)continue;
            }
            result.add(new Glyph(value.substring(start,end),start,end,0,0,false));start=end;
        }
        return result;
    }
}
