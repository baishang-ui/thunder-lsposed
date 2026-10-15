package io.github.baishangui.thunder;

/** The overlay parent and supported remote app both use display-origin coordinates. */
final class StatusBarFrameGeometry {
    static boolean fullscreen(int left, int top, int right, int bottom, int width, int height, float[] matrix) {
        if (width <= 0 || height <= width || left != 0 || top != 0 || right != width || bottom != height ||
                matrix == null || matrix.length != 9) return false;
        for (float value : matrix) if (!Float.isFinite(value)) return false;
        return matrix[0] > 0 && matrix[4] > 0 && matrix[1] == 0 && matrix[3] == 0 &&
            matrix[6] == 0 && matrix[7] == 0 && matrix[8] == 1;
    }
    private StatusBarFrameGeometry() { }
}
