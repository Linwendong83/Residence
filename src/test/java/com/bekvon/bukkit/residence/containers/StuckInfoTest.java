package com.bekvon.bukkit.residence.containers;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class StuckInfoTest {

    private static final long RESET_AFTER = 10_000L;

    @Test
    public void countsRepeatedDeniedMovesForSameResidence() {
        StuckInfo info = new StuckInfo();

        assertEquals(1, info.registerDeniedMove("home", 1_000L, RESET_AFTER));
        assertEquals(2, info.registerDeniedMove("home", 5_000L, RESET_AFTER));
        assertEquals(3, info.registerDeniedMove("home", 10_000L, RESET_AFTER));
    }

    @Test
    public void resetsAfterTimeout() {
        StuckInfo info = new StuckInfo();

        info.registerDeniedMove("home", 1_000L, RESET_AFTER);
        info.registerDeniedMove("home", 2_000L, RESET_AFTER);

        assertEquals(1, info.registerDeniedMove("home", 12_001L, RESET_AFTER));
    }

    @Test
    public void resetsForDifferentResidence() {
        StuckInfo info = new StuckInfo();

        info.registerDeniedMove("home", 1_000L, RESET_AFTER);
        info.registerDeniedMove("home", 2_000L, RESET_AFTER);

        assertEquals(1, info.registerDeniedMove("shop", 3_000L, RESET_AFTER));
    }

    @Test
    public void explicitResetClearsTheSequence() {
        StuckInfo info = new StuckInfo();

        info.registerDeniedMove("home", 1_000L, RESET_AFTER);
        info.registerDeniedMove("home", 2_000L, RESET_AFTER);
        info.reset();

        assertEquals(0, info.getTimesDenied());
        assertEquals(1, info.registerDeniedMove("home", 3_000L, RESET_AFTER));
    }
}
