package android.widget;
import android.text.*;import java.util.*;
public class TextView extends android.view.View {
 public String text="";public final List<TextWatcher> watchers=new ArrayList<>();
 public CharSequence getText(){return text;}public void setText(String value){text=value;for(TextWatcher w:new ArrayList<>(watchers))w.afterTextChanged(null);}
 public void addTextChangedListener(TextWatcher w){watchers.add(w);}public void removeTextChangedListener(TextWatcher w){watchers.remove(w);}
 protected void onDraw(android.graphics.Canvas canvas){originalOnDraw(canvas);}public void originalOnDraw(android.graphics.Canvas canvas){canvas.events.add("native-text:"+text);}
}
