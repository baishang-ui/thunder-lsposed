package io.github.baishangui.thunder;
import java.nio.file.*;
import android.graphics.*;
public final class IosCoverageCheck {
 static int checks,failures;
 static void check(boolean v,String s){checks++;if(!v){failures++;System.out.println("FAIL: "+s);}}
 public static void main(String[] args)throws Throwable{
  for(String n:Files.readAllLines(java.nio.file.Path.of("scripts/fixtures/iconstyle/status-resources.txt"))){
   var s=IosStatusIcons.system("com.android.systemui:drawable/"+n);
   check(s!=null,"C17 asset has replacement: "+n);
   if(s==null)continue;
   var icon=new IosIconDrawable(s);icon.setBounds(0,0,120,80);Canvas c=new Canvas();icon.draw(c);
   check(c.depth==0 && (!c.operations.isEmpty() || n.contains("activity_")),"drawable geometry exists: "+n);
  }
  float previousRight=Float.NaN,x=0;
  for(var kind:new IosStatusIcons.Kind[]{IosStatusIcons.Kind.WIFI,IosStatusIcons.Kind.CELLULAR,IosStatusIcons.Kind.BLUETOOTH}) {
   var plain=new IosStatusIcons.Symbol(kind);var original=new IosIconDrawable(plain);original.setBounds(0,0,210,200);
   Canvas before=new Canvas();original.draw(before);
   for(int flag:new int[]{IosStatusIcons.WARNING,IosStatusIcons.ROAMING,IosStatusIcons.NO_VOICE,IosStatusIcons.CONNECTING}) {
    var marked=plain.marked(flag);var changed=new IosIconDrawable(marked);changed.setBounds(0,0,210,200);Canvas after=new Canvas();changed.draw(after);
    check(IosIconDrawable.cellWidth(plain)==IosIconDrawable.cellWidth(marked),"status changes keep cell width: "+kind+"/"+flag);
    check(java.util.Arrays.equals(before.pathBounds.get(0),after.pathBounds.get(0)),"status changes keep base glyph position and size: "+kind+"/"+flag);
   }
  }
  for(var kind:new IosStatusIcons.Kind[]{IosStatusIcons.Kind.CELLULAR,IosStatusIcons.Kind.WIFI,IosStatusIcons.Kind.BATTERY}){
   var symbol=new IosStatusIcons.Symbol(kind);float w=IosIconDrawable.cellWidth(symbol);
   var drawable=new IosIconDrawable(symbol);drawable.setBounds(0,0,Math.round(w*10),200);Canvas canvas=new Canvas();drawable.draw(canvas);
   float l=canvas.pathBounds.stream().map(a->a[0]).min(Float::compare).orElse(0f)/10+x;
   float r=canvas.pathBounds.stream().map(a->a[2]).max(Float::compare).orElse(0f)/10+x;
   if(!Float.isNaN(previousRight))check(Math.abs(l-previousRight-3)<.15f,"signal/Wi-Fi/battery share a 3 dp visible gap");
   previousRight=r;x+=w;
  }
  if(failures>0)throw new AssertionError(failures+"/"+checks+" coverage checks failed");
  System.out.println("Verified "+checks+" firmware artwork checks; native layout is preserved.");
 }
}
