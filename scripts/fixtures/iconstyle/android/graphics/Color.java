package android.graphics;
public class Color {public static final int WHITE=-1,BLACK=0xff000000;public static int alpha(int c){return c>>>24;}
 public static float luminance(int c){return (((c>>16)&255)*.2126f+((c>>8)&255)*.7152f+(c&255)*.0722f)/255;}}
