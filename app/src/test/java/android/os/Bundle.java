package android.os;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** JVM stand-in for codec tests only. Device IPC is tested separately. */
public class Bundle {
    private final Map<String,Object> values = new HashMap<>();
    public long getLong(String key,long fallback) { return values.containsKey(key)?(Long)values.get(key):fallback; }
    public void putLong(String key,long value) { values.put(key,value); }
    public void putInt(String key, int value) { values.put(key,value); }
    public void putBoolean(String key, boolean value) { values.put(key,value); }
    public int getInt(String key) { return getInt(key,0); }
    public int getInt(String key,int fallback) { return values.containsKey(key)?(Integer)values.get(key):fallback; }
    public boolean getBoolean(String key,boolean fallback) { return values.containsKey(key)?(Boolean)values.get(key):fallback; }
    public boolean containsKey(String key) { return values.containsKey(key); }
    public Set<String> keySet() { return values.keySet(); }
    public void remove(String key) { values.remove(key); }
    public Object get(String key) { return values.get(key); }
}
