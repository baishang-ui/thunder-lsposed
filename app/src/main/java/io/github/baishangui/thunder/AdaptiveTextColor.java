package io.github.baishangui.thunder;

/** Color math shared by the panel and card text. Inputs to foreground() must be opaque. */
final class AdaptiveTextColor {
    static final int BLACK = 0xff000000, WHITE = 0xffffffff;
    static final long TRANSITION_MS = 160;

    static int composite(int overlay, int background) {
        int alpha = overlay >>> 24, inverse = 255 - alpha;
        int red = (((overlay >> 16) & 255) * alpha + ((background >> 16) & 255) * inverse + 127) / 255;
        int green = (((overlay >> 8) & 255) * alpha + ((background >> 8) & 255) * inverse + 127) / 255;
        int blue = ((overlay & 255) * alpha + (background & 255) * inverse + 127) / 255;
        return BLACK | (red << 16) | (green << 8) | blue;
    }

    static double luminance(int color) {
        return .2126 * linear((color >> 16) & 255)
            + .7152 * linear((color >> 8) & 255) + .0722 * linear(color & 255);
    }

    private static double linear(int channel) {
        double value = channel / 255.0;
        return value <= .04045 ? value / 12.92 : Math.pow((value + .055) / 1.055, 2.4);
    }

    static int foreground(int background) {
        double light = luminance(background);
        // Compare WCAG contrast ratios, rather than an arbitrary RGB midpoint.
        return (light + .05) / .05 >= 1.05 / (light + .05) ? BLACK : WHITE;
    }

    /** First draw is immediately legible; subsequent changes only animate along the grayscale axis. */
    static final class Transition {
        private int from, target;
        private long started;
        private boolean initialized;

        int color(int next, long now) {
            if (!initialized) {
                initialized = true;
                from = target = next;
                started = now;
            } else if (next != target) {
                from = value(now);
                target = next;
                started = now;
            }
            return value(now);
        }

        boolean running(long now) { return from != target && now - started < TRANSITION_MS; }

        private int value(long now) {
            float fraction = Math.max(0f, Math.min(1f, (now - started) / (float) TRANSITION_MS));
            int gray = Math.round((from & 255) + ((target & 255) - (from & 255)) * fraction);
            return BLACK | (gray << 16) | (gray << 8) | gray;
        }
    }
}
