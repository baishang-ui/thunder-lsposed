package pui.c17.shade;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.lang.reflect.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Ordinary Java reflection only. API 102 forbids calls into the legacy Xposed API. */
final class Reflect {
    private record Key(Class<?> type, String name, java.util.List<Class<?>> args) { }
    private static final Map<Key,Method> methods = new ConcurrentHashMap<>();
    private static final Map<Key,Field> fields = new ConcurrentHashMap<>();
    static Class<?> findClass(String name, ClassLoader loader) {
        try { return Class.forName(name, false, loader); }
        catch (ClassNotFoundException e) { throw new IllegalStateException(name,e); }
    }
    private static Field field(Object object,String name) {
        return fields.computeIfAbsent(new Key(object.getClass(),name,java.util.List.of()),k->{
            for (Class<?> c=k.type(); c!=null; c=c.getSuperclass()) try {
                Field f=c.getDeclaredField(name); f.setAccessible(true); return f;
            } catch (NoSuchFieldException ignored) { }
            throw new IllegalStateException("Missing field: "+k.type().getName()+"."+name);
        });
    }
    static Object getObjectField(Object object,String name) {
        try { return field(object,name).get(object); } catch (IllegalAccessException e) { throw new IllegalStateException(e); }
    }
    static void setObjectField(Object object,String name,Object value) {
        try { field(object,name).set(object,value); } catch (IllegalAccessException e) { throw new IllegalStateException(e); }
    }
    static int getIntField(Object object,String name) { return (Integer)getObjectField(object,name); }
    static void setIntField(Object object,String name,int value) { setObjectField(object,name,value); }
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
    static Object callMethod(Object object,String name,Object... args) {
        Class<?>[] types=new Class<?>[args.length];
        for(int i=0;i<args.length;i++)types[i]=args[i]==null?Void.class:args[i].getClass();
        Method m=methods.computeIfAbsent(new Key(object.getClass(),name,java.util.List.of(types)),k->{
            for(Class<?> c=k.type();c!=null;c=c.getSuperclass()) {
                Method found=null;
                for(Method candidate:c.getDeclaredMethods()) if(candidate.getName().equals(name)&&accepts(candidate.getParameterTypes(),args)) {
                    if(found!=null)throw new IllegalStateException("Ambiguous method: "+c.getName()+"."+name);
                    found=candidate;
                }
                if(found!=null){found.setAccessible(true);return found;}
            }
            throw new IllegalStateException("Missing method: "+k.type().getName()+"."+name);
        });
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
    private static final class IdentityRef extends WeakReference<Object> {
        final int hash;
        IdentityRef(Object value,ReferenceQueue<Object> q){super(value,q);hash=System.identityHashCode(value);}
        @Override public int hashCode(){return hash;}
        @Override public boolean equals(Object other){return this==other||(other instanceof IdentityRef r&&get()!=null&&get()==r.get());}
    }
    private static Map<String,Object> state(Object object,boolean create) {
        for(Object dead;(dead=queue.poll())!=null;)extras.remove(dead);
        IdentityRef ref=new IdentityRef(object,null);Map<String,Object> map=extras.get(ref);
        if(map==null&&create){map=new HashMap<>();extras.put(new IdentityRef(object,queue),map);}return map;
    }
    static synchronized Object getAdditionalInstanceField(Object object,String name){Map<String,Object> m=state(object,false);return m==null?null:m.get(name);}
    static synchronized void setAdditionalInstanceField(Object object,String name,Object value){state(object,true).put(name,value);}
    static synchronized void removeAdditionalInstanceField(Object object,String name){Map<String,Object> m=state(object,false);if(m!=null)m.remove(name);}
}
