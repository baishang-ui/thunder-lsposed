package io.github.baishangui.thunder;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.lang.reflect.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Ordinary Java reflection only. API 102 forbids calls into the legacy Xposed API. */
final class Reflect {
    private static final Object[] NO_ARGS = new Object[0];
    private static final class MethodEntry {
        final Method method;
        final Class<?>[] types;
        final MethodEntry next;
        MethodEntry(Method method,Object[] args,MethodEntry next) {
            this.method=method;this.next=next;types=new Class<?>[args.length];
            for(int i=0;i<args.length;i++)types[i]=args[i]==null?null:args[i].getClass();
        }
        Method find(Object[] args) {
            for(MethodEntry entry=this;entry!=null;entry=entry.next) {
                if(entry.types.length!=args.length)continue;
                int i=0;
                while(i<args.length&&entry.types[i]==(args[i]==null?null:args[i].getClass()))i++;
                if(i==args.length)return entry.method;
            }
            return null;
        }
    }
    private static final class Members {
        final Map<String,Field> fields = new ConcurrentHashMap<>();
        final Map<String,Method> noArgMethods = new ConcurrentHashMap<>();
        final Map<String,MethodEntry> methods = new ConcurrentHashMap<>();
    }
    // Separate class ownership from the lookup key. Hot field/getter calls need no
    // temporary keys, lists or argument-type arrays, and plugin loaders can unload.
    private static final ClassValue<Members> members = new ClassValue<>() {
        @Override protected Members computeValue(Class<?> type) { return new Members(); }
    };
    static Class<?> findClass(String name, ClassLoader loader) {
        try { return Class.forName(name, false, loader); }
        catch (ClassNotFoundException e) { throw new IllegalStateException(name,e); }
    }
    private static Field field(Object object,String name) {
        Class<?> type=object.getClass();
        Map<String,Field> fields=members.get(type).fields;
        Field cached=fields.get(name);
        if(cached!=null)return cached;
        return fields.computeIfAbsent(name,k->{
            for (Class<?> c=type; c!=null; c=c.getSuperclass()) try {
                Field f=c.getDeclaredField(name); f.setAccessible(true); return f;
            } catch (NoSuchFieldException ignored) { }
            throw new IllegalStateException("Missing field: "+type.getName()+"."+name);
        });
    }
    static Object getObjectField(Object object,String name) {
        try { return field(object,name).get(object); } catch (IllegalAccessException e) { throw new IllegalStateException(e); }
    }
    static void setObjectField(Object object,String name,Object value) {
        try { field(object,name).set(object,value); } catch (IllegalAccessException e) { throw new IllegalStateException(e); }
    }
    static int getIntField(Object object,String name) {
        try { Field f=field(object,name);return f.getType()==int.class?f.getInt(object):(Integer)f.get(object); }
        catch(IllegalAccessException e){throw new IllegalStateException(e);}
    }
    static void setIntField(Object object,String name,int value) {
        try { Field f=field(object,name);if(f.getType()==int.class)f.setInt(object,value);else f.set(object,value); }
        catch(IllegalAccessException e){throw new IllegalStateException(e);}
    }
    static boolean getBooleanField(Object object,String name) { return (Boolean)getObjectField(object,name); }
    static void setBooleanField(Object object,String name,boolean value) { setObjectField(object,name,value); }
    private static Class<?> boxed(Class<?> c) {
        if(c==int.class)return Integer.class; if(c==boolean.class)return Boolean.class;
        if(c==float.class)return Float.class; if(c==double.class)return Double.class;
        if(c==long.class)return Long.class; if(c==byte.class)return Byte.class;
        if(c==short.class)return Short.class; if(c==char.class)return Character.class;return c;
    }
    private static boolean accepts(Class<?>[] types,Object[] args) {
        if(types.length!=args.length)return false;
        for(int i=0;i<types.length;i++) if(args[i]==null?types[i].isPrimitive():!boxed(types[i]).isInstance(args[i]))return false;
        return true;
    }
    private static Method resolveMethod(Class<?> type,String name,Object[] args) {
        for(Class<?> c=type;c!=null;c=c.getSuperclass()) {
            Method found=null;
            // Covariant vendor builders expose a compiler bridge with identical arguments.
            // Resolve the implementation, while still rejecting genuinely ambiguous overloads.
            for(Method candidate:c.getDeclaredMethods()) if(!candidate.isBridge()&&candidate.getName().equals(name)&&accepts(candidate.getParameterTypes(),args)) {
                if(found!=null)throw new IllegalStateException("Ambiguous method: "+c.getName()+"."+name);
                found=candidate;
            }
            if(found!=null){found.setAccessible(true);return found;}
        }
        throw new IllegalStateException("Missing method: "+type.getName()+"."+name);
    }
    static Object callMethod(Object object,String name) {
        Class<?> type=object.getClass();
        Map<String,Method> methods=members.get(type).noArgMethods;
        Method method=methods.get(name);
        if(method==null)method=methods.computeIfAbsent(name,k->resolveMethod(type,name,NO_ARGS));
        return invoke(method,object,NO_ARGS);
    }
    static Object callMethod(Object object,String name,Object... args) {
        if(args.length==0)return callMethod(object,name);
        Class<?> type=object.getClass();
        Members cache=members.get(type);
        MethodEntry entry=cache.methods.get(name);
        Method m=entry==null?null:entry.find(args);
        if(m==null)synchronized(cache) {
            entry=cache.methods.get(name);
            m=entry==null?null:entry.find(args);
            if(m==null) {
                m=resolveMethod(type,name,args);
                cache.methods.put(name,new MethodEntry(m,args,entry));
            }
        }
        return invoke(m,object,args);
    }
    private static Object invoke(Method m,Object object,Object[] args) {
        try { return m.invoke(object,args); }
        catch(ReflectiveOperationException e){throw new IllegalStateException(m.toString(),e);}
    }
    static Object newInstance(Class<?> type,Object... args) {
        Constructor<?> found=null;
        for(Constructor<?> c:type.getDeclaredConstructors())if(accepts(c.getParameterTypes(),args)) {
            if(found!=null)throw new IllegalStateException("Ambiguous constructor: "+type.getName());found=c;
        }
        if(found==null)throw new IllegalStateException("Missing constructor: "+type.getName());
        try { found.setAccessible(true);return found.newInstance(args); }
        catch(ReflectiveOperationException e){throw new IllegalStateException(type.getName(),e);}
    }
    // Identity, not data-class equals/hashCode: configs may mutate and compare equal.
    private static final ReferenceQueue<Object> queue=new ReferenceQueue<>();
    private static final Map<IdentityRef,Map<String,Object>> extras=new HashMap<>();
    // All metadata access holds Reflect.class. Reuse only the lookup probe, never a
    // stored key, and clear its strong reference even when lookup fails.
    private static final Lookup lookup=new Lookup();
    private static final class Lookup {
        Object value;
        @Override public int hashCode(){return System.identityHashCode(value);}
        @Override public boolean equals(Object other){return other instanceof IdentityRef r&&value==r.get();}
    }
    private static final class IdentityRef extends WeakReference<Object> {
        final int hash;
        IdentityRef(Object value,ReferenceQueue<Object> q){super(value,q);hash=System.identityHashCode(value);}
        @Override public int hashCode(){return hash;}
        @Override public boolean equals(Object other){return this==other||(other instanceof IdentityRef r&&get()!=null&&get()==r.get());}
    }
    private static Map<String,Object> state(Object object,boolean create) {
        for(Object dead;(dead=queue.poll())!=null;)extras.remove(dead);
        Map<String,Object> map;
        lookup.value=object;
        try {map=extras.get(lookup);}finally {lookup.value=null;}
        if(map==null&&create){map=new HashMap<>();extras.put(new IdentityRef(object,queue),map);}return map;
    }
    static synchronized Object getAdditionalInstanceField(Object object,String name){Map<String,Object> m=state(object,false);return m==null?null:m.get(name);}
    static synchronized void setAdditionalInstanceField(Object object,String name,Object value){state(object,true).put(name,value);}
    static synchronized void removeAdditionalInstanceField(Object object,String name){Map<String,Object> m=state(object,false);if(m!=null)m.remove(name);}
}
