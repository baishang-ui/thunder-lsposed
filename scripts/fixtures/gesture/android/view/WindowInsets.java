package android.view;
public final class WindowInsets {
    public static final class Type {
        public static int navigationBars() { return 2; }
        public static int tappableElement() { return 64; }
        public static int systemGestures() { return 16; }
        public static int mandatorySystemGestures() { return 32; }
        public static int ime() { return 8; }
    }
}
