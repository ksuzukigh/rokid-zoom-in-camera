package io.github.ksuzukigh.rokidzoomincamera;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class BatteryDisplayTest {
    @Test public void convertsAndClampsPercentage() {
        assertEquals(83, BatteryDisplay.percentage(83, 100));
        assertEquals(50, BatteryDisplay.percentage(1, 2));
        assertEquals(100, BatteryDisplay.percentage(120, 100));
        assertEquals(-1, BatteryDisplay.percentage(-1, 100));
        assertEquals(-1, BatteryDisplay.percentage(10, 0));
    }

    @Test public void describesBatteryWithoutRelyingOnColor() {
        assertEquals("電池 83%", BatteryDisplay.label(83, false));
        assertEquals("電池少 20%", BatteryDisplay.label(20, false));
        assertEquals("要充電 10%", BatteryDisplay.label(10, false));
        assertEquals("充電中 8%", BatteryDisplay.label(8, true));
        assertEquals("電池 --%", BatteryDisplay.label(-1, false));
    }
}
