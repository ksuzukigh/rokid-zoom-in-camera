package io.github.ksuzukigh.rokidzoomincamera;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class TouchButtonGuardTest {
    @Test public void ignoresButtonSignalImmediatelyAfterTouch() {
        assertTrue(TouchButtonGuard.shouldIgnore(1_000L, 1_500L, 1_200L));
        assertTrue(TouchButtonGuard.shouldIgnore(1_000L, 2_200L, 1_200L));
    }

    @Test public void acceptsIndependentPhysicalButtonSignal() {
        assertFalse(TouchButtonGuard.shouldIgnore(-1L, 500L, 1_200L));
        assertFalse(TouchButtonGuard.shouldIgnore(1_000L, 2_201L, 1_200L));
        assertFalse(TouchButtonGuard.shouldIgnore(2_000L, 1_999L, 1_200L));
    }
}
