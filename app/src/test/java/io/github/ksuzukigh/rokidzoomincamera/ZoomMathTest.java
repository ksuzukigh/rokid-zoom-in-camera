package io.github.ksuzukigh.rokidzoomincamera;

import static org.junit.Assert.assertArrayEquals;
import org.junit.Test;

public final class ZoomMathTest {
    @Test public void cropIsCenteredAndEven() {
        int[] crop = ZoomMath.cropEdges(0, 0, 4000, 3000, 2f, 4f);
        assertArrayEquals(new int[]{1000, 750, 3000, 2250}, crop);
    }

    @Test public void cropClampsToCameraMaximum() {
        int[] crop = ZoomMath.cropEdges(10, 20, 4010, 3020, 9f, 4f);
        assertArrayEquals(new int[]{1510, 1145, 2510, 1895}, crop);
    }

    @Test public void cropNeverZoomsOutBelowOne() {
        assertArrayEquals(new int[]{0, 0, 4032, 3024},
                ZoomMath.cropEdges(0, 0, 4032, 3024, 0.5f, 8f));
    }
}
