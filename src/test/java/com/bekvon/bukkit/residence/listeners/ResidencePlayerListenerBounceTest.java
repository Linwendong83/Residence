package com.bekvon.bukkit.residence.listeners;

import static org.junit.Assert.assertEquals;

import org.bukkit.util.Vector;
import org.junit.Test;

public class ResidencePlayerListenerBounceTest {

    @Test
    public void reflectsXAxisAndPreservesTangentialVelocity() {
        Vector result = ResidencePlayerListener.calculateBounceVelocity(
                new Vector(1.25D, 0.5D, -0.25D), 0);

        assertEquals(-1.25D, result.getX(), 1e-9D);
        assertEquals(0.5D, result.getY(), 1e-9D);
        assertEquals(-0.25D, result.getZ(), 1e-9D);
    }

    @Test
    public void reflectsYAxisAndPreservesTangentialVelocity() {
        Vector result = ResidencePlayerListener.calculateBounceVelocity(
                new Vector(0.5D, -1.25D, -0.25D), 1);

        assertEquals(0.5D, result.getX(), 1e-9D);
        assertEquals(1.25D, result.getY(), 1e-9D);
        assertEquals(-0.25D, result.getZ(), 1e-9D);
    }

    @Test
    public void reflectsZAxisAndPreservesTangentialVelocity() {
        Vector result = ResidencePlayerListener.calculateBounceVelocity(
                new Vector(0.5D, -0.25D, 1.25D), 2);

        assertEquals(0.5D, result.getX(), 1e-9D);
        assertEquals(-0.25D, result.getY(), 1e-9D);
        assertEquals(-1.25D, result.getZ(), 1e-9D);
    }

    @Test
    public void stationaryVelocityRemainsStationary() {
        Vector result = ResidencePlayerListener.calculateBounceVelocity(new Vector(), 1);

        assertEquals(0D, result.lengthSquared(), 1e-9D);
    }

    @Test
    public void hardCapsTotalVelocityAfterReflection() {
        Vector result = ResidencePlayerListener.calculateBounceVelocity(
                new Vector(100D, 100D, 100D), 0);

        assertEquals(ResidencePlayerListener.MAX_BOUNCE_VELOCITY, result.length(), 1e-9D);
        assertEquals(-result.getX(), result.getY(), 1e-9D);
        assertEquals(result.getY(), result.getZ(), 1e-9D);
    }
}
