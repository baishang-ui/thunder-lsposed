package io.github.baishangui.thunder;

/** A continuous shared clock: the lead moves immediately while followers overlap. */
final class CloudCardTiming {
    static float start(boolean opening,int index,int count) {
        if(count<=1||index==0)return 0;
        float order=(index-1f)/Math.max(1,count-2);
        return opening?.025f+.045f*order:.045f*(1-order);
    }
    static float end(boolean opening,int index,int count) {
        if(count<=1||index==0||opening)return 1;
        return .94f-.06f*(index-1f)/Math.max(1,count-2);
    }
    static float fraction(float time,boolean opening,int index,int count) {
        return fraction(time,start(opening,index,count),end(opening,index,count));
    }
    static float fraction(float time,float start,float end) {
        return Math.max(0,Math.min(1,(time-start)/(end-start)));
    }
    static float opacity(float phase) {
        float t=Math.max(0,Math.min(1,phase));
        return t*t*t*(10+t*(-15+6*t));
    }
    static CloudMorphHook.Frame follower(CloudMorphHook.Frame lead,CloudMorphHook.Frame leadExpanded,CloudMorphHook.Frame expanded,float reveal) {
        float amount=Math.max(0,Math.min(1,reveal));
        if(amount==1&&lead.equals(leadExpanded))return expanded;
        if(amount==0&&lead.progress()==0)return lead;
        // The parked card belongs underneath the current lead, including while
        // that lead is changing from a card into the capsule. A fixed card-sized
        // parking rectangle leaves the followers floating behind after closure.
        float depth=lead.progress(),inset=.02f*depth;
        float width=lead.width()*mix(1-2*inset,expanded.width()/leadExpanded.width(),amount);
        float height=lead.height()*mix(1-2*inset,expanded.height()/leadExpanded.height(),amount);
        float scale=Math.min(lead.width()/leadExpanded.width(),lead.height()/leadExpanded.height());
        return new CloudMorphHook.Frame(
            lead.x()+mix(lead.width()*inset,expanded.x()-leadExpanded.x(),amount),
            lead.y()+mix(8*depth,expanded.y()-leadExpanded.y(),amount),width,height,
            Math.max(0,Math.min(mix(lead.radius(),expanded.radius()*scale,amount),Math.min(width,height)/2)),
            mix(lead.progress(),1,amount));
    }
    private static float mix(float from,float to,float fraction){return from+(to-from)*fraction;}
}
