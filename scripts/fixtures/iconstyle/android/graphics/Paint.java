package android.graphics;
public class Paint {
 public static final int ANTI_ALIAS_FLAG=1;public enum Style{FILL,STROKE}public enum Cap{ROUND}public enum Join{ROUND}public enum Align{CENTER,LEFT}
 public Style style=Style.FILL;public float textSize=12;public Align align=Align.LEFT;private int color=0xff000000,alpha=255;private ColorFilter filter;
 public Paint(int flags){}public Paint(){}public void setColor(int c){color=c;alpha=c>>>24;}public int getColor(){return color;}
 public void setAlpha(int a){alpha=a;}public int getAlpha(){return alpha;}public void setStyle(Style s){style=s;}public void setStrokeWidth(float w){}
 private Typeface typeface=new Typeface();
 public void setStrokeCap(Cap c){}public void setStrokeJoin(Join j){}public void setTypeface(Typeface t){typeface=t;}public Typeface getTypeface(){return typeface;}public void setTextSize(float s){textSize=s;}public void setTextAlign(Align a){align=a;}public void getTextBounds(String text,int start,int end,Rect bounds){bounds.left=1;bounds.right=1+Math.round((end-start)*textSize*.55f*textScaleX);bounds.top=-Math.round(textSize*.75f);bounds.bottom=0;}
 public static class FontMetricsInt{public int top,bottom,ascent,descent;}
 public FontMetricsInt getFontMetricsInt(){var f=new FontMetricsInt();f.top=-(int)Math.ceil(textSize*.8);f.bottom=(int)Math.ceil(textSize*.2);f.ascent=f.top;f.descent=f.bottom;return f;}
 public void setColorFilter(ColorFilter f){filter=f;}public ColorFilter getColorFilter(){return filter;}
 public float textScaleX=1;public void setTextScaleX(float scale){textScaleX=scale;}public float getTextScaleX(){return textScaleX;}public float measureText(String text){return text.length()*textSize*.6f*textScaleX;}
}
