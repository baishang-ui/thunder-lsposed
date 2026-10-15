package io.github.baishangui.thunder;

import org.junit.Test;
import static org.junit.Assert.*;

public class StatusBarFrameGeometryTest {
    private float[] matrix(float scale, float x, float y) {
        return new float[]{scale, 0, x, 0, scale, y, 0, 0, 1};
    }
    @Test public void acceptsIconToFullscreenPathAndExactFinalPosition() {
        float[] icon = matrix(.08f, 640, 1600);
        assertTrue(StatusBarFrameGeometry.fullscreen(0, 0, 1272, 2800, 1272, 2800, icon));
        float[] end = matrix(1, 0, 0);
        assertTrue(StatusBarFrameGeometry.fullscreen(0, 0, 1272, 2800, 1272, 2800, end));
        // The bar's normal screen-space point transforms back to exactly itself.
        assertEquals(100f, end[0] * 100 + end[1] * 40 + end[2], 0);
        assertEquals(40f, end[3] * 100 + end[4] * 40 + end[5], 0);
    }
    @Test public void rejectsInsetLocalCoordinatesInsteadOfIntroducingFinalOffset() {
        assertFalse(StatusBarFrameGeometry.fullscreen(0, 105, 1272, 2800, 1272, 2800, matrix(1, 0, 105)));
    }
    @Test public void rejectsNonfinitePerspectiveAndRotation() {
        float[] m = matrix(1, 0, Float.NaN);
        assertFalse(StatusBarFrameGeometry.fullscreen(0, 0, 1272, 2800, 1272, 2800, m));
        m = matrix(1, 0, 0); m[6] = .01f;
        assertFalse(StatusBarFrameGeometry.fullscreen(0, 0, 1272, 2800, 1272, 2800, m));
        m = new float[]{0, -1, 2800, 1, 0, 0, 0, 0, 1};
        assertFalse(StatusBarFrameGeometry.fullscreen(0, 0, 1272, 2800, 1272, 2800, m));
    }
}
