package com.bekvon.bukkit.residence.listeners;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.bukkit.util.Vector;
import org.junit.Test;

public class ResidencePlayerListenerBounceTest {

    private static final double MINIMUM_VELOCITY = 0.35D;

    @Test
    public void suppliesMinimumOutwardVelocityWhenSourceIsStationary() {
        Vector result = ResidencePlayerListener.calculateBounceVelocity(
                new Vector(), new Vector(-1, 0, 0), MINIMUM_VELOCITY);

        assertEquals(-MINIMUM_VELOCITY, result.getX(), 1e-9D);
        assertEquals(0D, result.getY(), 1e-9D);
        assertEquals(0D, result.getZ(), 1e-9D);
    }

    @Test
    public void correctsInwardVelocityAndKeepsTangentialVelocity() {
        Vector result = ResidencePlayerListener.calculateBounceVelocity(
                new Vector(1, 0.5, -0.25), new Vector(-1, 0, 0), MINIMUM_VELOCITY);

        assertEquals(-MINIMUM_VELOCITY, result.getX(), 1e-9D);
        assertEquals(0.5D, result.getY(), 1e-9D);
        assertEquals(-0.25D, result.getZ(), 1e-9D);
    }

    @Test
    public void raisesAnOutwardVelocityBelowTheMinimum() {
        Vector result = ResidencePlayerListener.calculateBounceVelocity(
                new Vector(-0.1, 0, 0), new Vector(-1, 0, 0), MINIMUM_VELOCITY);

        assertEquals(-MINIMUM_VELOCITY, result.getX(), 1e-9D);
    }

    @Test
    public void preservesOutwardVelocityAboveTheMinimum() {
        Vector result = ResidencePlayerListener.calculateBounceVelocity(
                new Vector(-1.25, 0, 0), new Vector(-1, 0, 0), MINIMUM_VELOCITY);

        assertEquals(-1.25D, result.getX(), 1e-9D);
    }

    @Test
    public void appliesTheMinimumAlongEachOutwardAxis() {
        Vector[] normals = { new Vector(-1, 0, 0), new Vector(1, 0, 0),
                new Vector(0, -1, 0), new Vector(0, 1, 0),
                new Vector(0, 0, -1), new Vector(0, 0, 1) };

        for (Vector normal : normals) {
            Vector result = ResidencePlayerListener.calculateBounceVelocity(
                    new Vector(), normal, MINIMUM_VELOCITY);
            assertEquals(MINIMUM_VELOCITY, result.dot(normal), 1e-9D);
        }
    }

    @Test
    public void hardCapsTotalVelocityWhileKeepingOutwardMinimum() {
        Vector result = ResidencePlayerListener.calculateBounceVelocity(
                new Vector(0, 100, 100), new Vector(-1, 0, 0), MINIMUM_VELOCITY);

        assertEquals(-MINIMUM_VELOCITY, result.getX(), 1e-9D);
        assertTrue(result.length() <= ResidencePlayerListener.MAX_BOUNCE_VELOCITY + 1e-9D);
        assertTrue(result.getY() > 0D);
        assertTrue(result.getZ() > 0D);
    }

    @Test
    public void clampsConfiguredMinimumToTheHardCap() {
        Vector result = ResidencePlayerListener.calculateBounceVelocity(
                new Vector(), new Vector(0, 1, 0), ResidencePlayerListener.MAX_BOUNCE_VELOCITY * 2D);

        assertEquals(ResidencePlayerListener.MAX_BOUNCE_VELOCITY, result.getY(), 1e-9D);
        assertEquals(ResidencePlayerListener.MAX_BOUNCE_VELOCITY, result.length(), 1e-9D);
    }
}
