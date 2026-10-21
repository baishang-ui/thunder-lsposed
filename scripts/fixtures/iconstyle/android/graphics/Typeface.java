package android.graphics;
public class Typeface {
 public static final int NORMAL=0,BOLD=1;
 public String family="system";public int weight=400;public boolean italic;
 public static Typeface create(Typeface f,int style){return new Typeface();}
 public static Typeface create(String s,int style){return new Typeface();}
 public int getWeight(){return weight;}public boolean isItalic(){return italic;}
 public static Typeface create(Typeface base,int weight,boolean italic){var f=new Typeface();f.family=base==null?"system":base.family;f.weight=weight;f.italic=italic;return f;}
}
