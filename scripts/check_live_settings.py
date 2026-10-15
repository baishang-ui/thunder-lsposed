"""Exercise the real config listener with queued delivery and atomic preference snapshots."""
from pathlib import Path
import os
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parent.parent
fixtures = {
'android/content/SharedPreferences.java': '''package android.content;
import java.util.Map;
public interface SharedPreferences {
 Map<String,?> getAll();
 void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener);
 interface OnSharedPreferenceChangeListener {void onSharedPreferenceChanged(SharedPreferences prefs,String key);}
}''',
'android/os/Handler.java': '''package android.os;
import java.util.*;
public class Handler {
 public final List<Runnable> queue=new ArrayList<>();
 public boolean post(Runnable r){queue.add(r);return true;}
 public void removeCallbacks(Runnable r){queue.removeIf(x->x==r);}
 public void drain(){while(!queue.isEmpty())queue.remove(0).run();}
}''',
'io/github/baishangui/thunder/LiveSettingsCheck.java': '''package io.github.baishangui.thunder;
import android.content.SharedPreferences;import android.os.Handler;import java.util.*;
public class LiveSettingsCheck {
 static class Prefs implements SharedPreferences {
  Map<String,Object> data=new HashMap<>();OnSharedPreferenceChangeListener listener;boolean broken;
  public Map<String,?> getAll(){if(broken)throw new IllegalStateException("service unavailable");return new HashMap<>(data);}
  public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l){listener=l;}
  void notifyKey(String key){listener.onSharedPreferenceChanged(this,key);}
 }
 static void check(boolean b,String message){if(!b)throw new AssertionError(message);System.out.println("PASS: "+message);}
 public static void main(String[] args){
  Prefs prefs=new Prefs();Handler worker=new Handler();List<Map<String,?>> loaded=new ArrayList<>();List<Throwable> failures=new ArrayList<>();
  prefs.data.put("revision",1L);prefs.data.put("enabled",true);
  LiveSettings settings=new LiveSettings(prefs,worker,loaded::add,failures::add);settings.start();
  check(loaded.size()==1 && loaded.get(0).get("revision").equals(1L),"startup loads before the target Application exists");
  prefs.data.put("revision",2L);prefs.data.put("enabled",false);
  prefs.notifyKey("enabled");prefs.notifyKey("revision");prefs.notifyKey(null);prefs.notifyKey("revision");
  check(loaded.size()==1 && worker.queue.size()==1,"a multi-key commit queues one refresh without work on the Binder callback");
  worker.drain();
  check(loaded.size()==2 && Boolean.FALSE.equals(loaded.get(1).get("enabled")),"the existing process receives the complete updated snapshot");
  prefs.notifyKey("revision");worker.drain();check(loaded.size()==2,"duplicate revision does not rebuild views or send another report");
  prefs.data.clear();prefs.notifyKey(null);worker.drain();
  check(loaded.size()==2 && failures.size()==1,"an incomplete snapshot preserves the last working configuration");
  prefs.data.put("revision",3L);prefs.broken=true;prefs.notifyKey("revision");worker.drain();
  check(loaded.size()==2 && failures.size()==2,"a settings read failure is contained instead of crashing SystemUI");
  prefs.broken=false;prefs.notifyKey("revision");worker.drain();
  check(loaded.size()==3 && loaded.get(2).get("revision").equals(3L),"the next valid update recovers without a process restart");
 }
}'''
}
with tempfile.TemporaryDirectory(prefix='thunder-live-settings-') as tmp:
    tmp = Path(tmp)
    paths = []
    for name, text in fixtures.items():
        path = tmp / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text)
        paths.append(path)
    paths.append(ROOT / 'app/src/main/java/io/github/baishangui/thunder/LiveSettings.java')
    java = Path(os.environ['JAVA_HOME']) / 'bin'
    subprocess.run([str(java/'javac'), '-d', str(tmp/'classes'), *map(str, paths)], check=True)
    subprocess.run([str(java/'java'), '-cp', str(tmp/'classes'), 'io.github.baishangui.thunder.LiveSettingsCheck'], check=True)
