package android.text; public class Layout {
 private CharSequence text;private TextPaint paint;public Layout(){}public Layout(CharSequence text,TextPaint paint){this.text=text;this.paint=paint;}
 public static float getDesiredWidth(CharSequence text,TextPaint paint){return paint.measureText(text.toString());}
 public float getPrimaryHorizontal(int offset){return getDesiredWidth(text.subSequence(0,offset),paint);}
}
