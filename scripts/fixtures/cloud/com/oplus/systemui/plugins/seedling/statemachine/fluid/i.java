package com.oplus.systemui.plugins.seedling.statemachine.fluid;
/** Width-change scene A consumes e.c.d segments, independently of getWidth(). */
public class i {
    public final WidthUpdate e=new WidthUpdate();
    public i(int... widths){for(int width:widths)e.c.d.add(new Segment(width));}
    public static class WidthUpdate {public final AnimationData c=new AnimationData();}
    public static class AnimationData {public final java.util.List<Segment> d=new java.util.ArrayList<>();}
    public static class Segment {final int end;Segment(int end){this.end=end;}public int c(){return end;}}
}
