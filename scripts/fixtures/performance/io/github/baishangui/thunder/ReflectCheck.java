package io.github.baishangui.thunder;

public class ReflectCheck {
    static class Parent { private String value = "parent"; private String inherited() { return value; } }
    static class Child extends Parent {
        private int number = 1000;
        private Integer boxedNumber = 1000;
        private String value = "child";
        private String read() { return value; }
        private String read(int number) { return "int:" + number; }
        private String nullable(String value) { return value; }
        private String ambiguous(String value) { return value; }
        private String ambiguous(Integer value) { return "integer"; }
        private void fail() { throw new UnsupportedOperationException("native failure"); }
    }
    // check_effects marks these forwarding methods as bridge/synthetic after javac,
    // matching the flags of the C17 DEX Roundable forwarders exposed by ART.
    static class BridgeOnlyRow {
        public float getTopRoundness(){return .25f;}
        public String source(String name){return "bridge:"+name;}
    }
    interface Roundable {
        default float getTopRoundness() { return .5f; }
        default String source(String name) { return "round:" + name; }
    }
    interface InheritedRoundable extends Roundable { }
    static class NativeRow implements InheritedRoundable { }
    static class NativeChild extends NativeRow { }
    static class EqualOwner {
        @Override public boolean equals(Object other) { return other instanceof EqualOwner; }
        @Override public int hashCode() { return 1; }
    }
    private static void check(boolean value) { if (!value) throw new AssertionError(); }
    private static void failure(Runnable call, String message) {
        try { call.run(); throw new AssertionError("Expected failure: " + message); }
        catch (IllegalStateException expected) { check(expected.getMessage().contains(message)); }
    }
    public static void main(String[] args) throws Exception {
        Child child = new Child();
        for (int i = 0; i < 100; i++) {
            check(Reflect.getObjectField(child, "value").equals("child"));
            Reflect.setIntField(child, "number", 1000 + i);
            check(Reflect.getIntField(child, "number") == 1000 + i);
            Reflect.setIntField(child,"boxedNumber",2000+i);
            check(Reflect.getIntField(child,"boxedNumber")==2000+i);
            check(Reflect.callMethod(child, "read").equals("child"));
            check(Reflect.callMethod(child, "read", 4).equals("int:4"));
            check(Reflect.callMethod(child, "inherited").equals("parent"));
            check(Reflect.callMethod(child, "nullable", (Object) null) == null);
        }
        check(Reflect.callMethod(new Parent(), "inherited").equals("parent"));
        failure(() -> Reflect.callMethod(child, "ambiguous", (Object) null), "Ambiguous method");
        check(Reflect.callMethod(child,"ambiguous","text").equals("text"));
        check(Reflect.callMethod(child,"ambiguous",3).equals("integer"));
        // A cached non-null overload must not hide ambiguity for a later null argument.
        failure(() -> Reflect.callMethod(child,"ambiguous",(Object)null),"Ambiguous method");
        failure(() -> Reflect.callMethod(child, "absent"), "Missing method");
        failure(() -> Reflect.getObjectField(child, "absent"), "Missing field");
        try { Reflect.callMethod(child, "fail"); throw new AssertionError(); }
        catch (IllegalStateException expected) {
            check(expected.getCause().getCause() instanceof UnsupportedOperationException);
        }
        check(BridgeOnlyRow.class.getDeclaredMethod("getTopRoundness").isBridge());
        for (int i=0;i<2;i++) {
            check(Reflect.callMethod(new BridgeOnlyRow(),"getTopRoundness").equals(.25f));
            check(Reflect.callMethod(new BridgeOnlyRow(),"source","banner").equals("bridge:banner"));
            NativeChild row=new NativeChild();
            check(Reflect.callMethod(row,"getTopRoundness").equals(.5f));
            check(Reflect.callMethod(row,"source","notification").equals("round:notification"));
        }
        EqualOwner first=new EqualOwner(),second=new EqualOwner();
        Reflect.setAdditionalInstanceField(first,"key","first");
        Reflect.setAdditionalInstanceField(second,"key","second");
        check(Reflect.getAdditionalInstanceField(first,"key").equals("first"));
        check(Reflect.getAdditionalInstanceField(second,"key").equals("second"));
        Reflect.removeAdditionalInstanceField(first,"key");
        check(Reflect.getAdditionalInstanceField(first,"key")==null);
        check(Reflect.getAdditionalInstanceField(second,"key").equals("second"));
        var error=new java.util.concurrent.atomic.AtomicReference<Throwable>();
        Thread[] threads=new Thread[6];
        for(int n=0;n<threads.length;n++)threads[n]=new Thread(()->{
            try {
                EqualOwner owner=new EqualOwner();Child local=new Child();
                for(int i=0;i<5000;i++) {
                    Reflect.setAdditionalInstanceField(owner,"key",i);
                    check(Reflect.getAdditionalInstanceField(owner,"key").equals(i));
                    check(Reflect.callMethod(local,"ambiguous",i).equals("integer"));
                    check(Reflect.callMethod(local,"ambiguous","text").equals("text"));
                    Reflect.removeAdditionalInstanceField(owner,"key");
                    check(Reflect.getAdditionalInstanceField(owner,"key")==null);
                }
            } catch(Throwable failure){error.compareAndSet(null,failure);}
        });
        for(Thread thread:threads)thread.start();
        for(Thread thread:threads)thread.join();
        if(error.get()!=null)throw new AssertionError(error.get());
        // Emulate collection deterministically instead of depending on GC timing.
        var extrasField=Reflect.class.getDeclaredField("extras");extrasField.setAccessible(true);
        var extras=(java.util.Map<?,?>)extrasField.get(null);
        java.lang.ref.Reference<?> dead=null;
        for(Object key:extras.keySet())if(((java.lang.ref.Reference<?>)key).get()==second)dead=(java.lang.ref.Reference<?>)key;
        check(dead!=null);dead.clear();dead.enqueue();
        check(Reflect.getAdditionalInstanceField(first,"key")==null);
        check(!extras.containsKey(dead));
        Reflect.setAdditionalInstanceField(first,"key","live");
        check(Reflect.getAdditionalInstanceField(first,"key").equals("live"));
        System.out.println("PASS: reflection preserves inheritance, overloads, nulls, boxed fields, identity and concurrent metadata");
    }
}
