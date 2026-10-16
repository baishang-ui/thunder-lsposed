package io.github.baishangui.thunder;

public class ReflectCheck {
    static class Parent { private String value = "parent"; private String inherited() { return value; } }
    static class Child extends Parent {
        private int number = 1000;
        private String value = "child";
        private String read() { return value; }
        private String read(int number) { return "int:" + number; }
        private String nullable(String value) { return value; }
        private String ambiguous(String value) { return value; }
        private String ambiguous(Integer value) { return "integer"; }
        private void fail() { throw new UnsupportedOperationException("native failure"); }
    }
    private static void check(boolean value) { if (!value) throw new AssertionError(); }
    private static void failure(Runnable call, String message) {
        try { call.run(); throw new AssertionError("Expected failure: " + message); }
        catch (IllegalStateException expected) { check(expected.getMessage().contains(message)); }
    }
    public static void main(String[] args) {
        Child child = new Child();
        for (int i = 0; i < 100; i++) {
            check(Reflect.getObjectField(child, "value").equals("child"));
            Reflect.setIntField(child, "number", 1000 + i);
            check(Reflect.getIntField(child, "number") == 1000 + i);
            check(Reflect.callMethod(child, "read").equals("child"));
            check(Reflect.callMethod(child, "read", 4).equals("int:4"));
            check(Reflect.callMethod(child, "inherited").equals("parent"));
            check(Reflect.callMethod(child, "nullable", (Object) null) == null);
        }
        check(Reflect.callMethod(new Parent(), "inherited").equals("parent"));
        failure(() -> Reflect.callMethod(child, "ambiguous", (Object) null), "Ambiguous method");
        failure(() -> Reflect.callMethod(child, "absent"), "Missing method");
        failure(() -> Reflect.getObjectField(child, "absent"), "Missing field");
        try { Reflect.callMethod(child, "fail"); throw new AssertionError(); }
        catch (IllegalStateException expected) {
            check(expected.getCause().getCause() instanceof UnsupportedOperationException);
        }
        System.out.println("PASS: reflection preserves inheritance, overloads, nulls and native exceptions");
    }
}
