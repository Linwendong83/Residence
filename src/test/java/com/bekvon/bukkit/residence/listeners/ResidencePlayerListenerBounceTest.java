package com.bekvon.bukkit.residence.listeners;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.bukkit.util.Vector;
import org.junit.Test;

public class ResidencePlayerListenerBounceTest {

    private static final double MINIMUM_VELOCITY = 0.35D;
    private static final double MAX_BOUNCE_VELOCITY = 3.55794529641779D;

    @Test
    public void suppliesMinimumOutwardVelocityWhenSourceIsStationary() {
        Vector result = ResidencePlayerListener.calculateBounceVelocity(
                new Vector(), new Vector(-1, 0, 0), MINIMUM_VELOCITY);

        assertEquals(-MINIMUM_VELOCITY, result.getX(), 1e-9D);
        assertEquals(0D, result.getY(), 1e-9D);
        assertEquals(0D, result.getZ(), 1e-9D);
    }

    @Test
    public void reflectsInwardVelocityAndKeepsTangentialVelocity() {
        Vector result = ResidencePlayerListener.calculateBounceVelocity(
                new Vector(1, 0.5, -0.25), new Vector(-1, 0, 0), MINIMUM_VELOCITY);

        assertEquals(-1D, result.getX(), 1e-9D);
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
    public void preservesReflectedVelocityAboveTheMinimum() {
        Vector result = ResidencePlayerListener.calculateBounceVelocity(
                new Vector(1.25, 0, 0), new Vector(-1, 0, 0), MINIMUM_VELOCITY);

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
        assertTrue(result.length() <= MAX_BOUNCE_VELOCITY + 1e-9D);
        assertTrue(result.getY() > 0D);
        assertTrue(result.getZ() > 0D);
    }

    @Test
    public void zeroMinimumDoesNotRaiseAnOutwardVelocity() {
        Vector result = ResidencePlayerListener.calculateBounceVelocity(
                new Vector(0.1, 0, 0), new Vector(-1, 0, 0), 0D);

        assertEquals(-0.1D, result.getX(), 1e-9D);
    }

    @Test
    public void clampsNegativeMinimumToZero() {
        Vector result = ResidencePlayerListener.calculateBounceVelocity(
                new Vector(0.1, 0, 0), new Vector(-1, 0, 0), -1D);

        assertEquals(-0.1D, result.getX(), 1e-9D);
    }

    @Test
    public void clampsConfiguredMinimumToTheHardCap() {
        Vector result = ResidencePlayerListener.calculateBounceVelocity(
                new Vector(), new Vector(0, 1, 0), MAX_BOUNCE_VELOCITY * 2D);

        assertEquals(MAX_BOUNCE_VELOCITY, result.getY(), 1e-9D);
        assertEquals(MAX_BOUNCE_VELOCITY, result.length(), 1e-9D);
    }

    @Test
    public void fallsBackToDefaultForInvalidMinimum() {
        double[] invalidValues = { Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY };
        for (double invalidValue : invalidValues) {
            Vector result = ResidencePlayerListener.calculateBounceVelocity(
                    new Vector(), new Vector(0, 1, 0), invalidValue);

            assertEquals(MINIMUM_VELOCITY, result.getY(), 1e-9D);
        }
    }

    @Test
    public void returnsOriginalVelocityForInvalidNormal() {
        Vector source = new Vector(1.25D, 0.5D, -0.25D);
        Vector result = ResidencePlayerListener.calculateBounceVelocity(source, new Vector(), MINIMUM_VELOCITY);

        assertEquals(source.getX(), result.getX(), 1e-9D);
        assertEquals(source.getY(), result.getY(), 1e-9D);
        assertEquals(source.getZ(), result.getZ(), 1e-9D);
    }
}
