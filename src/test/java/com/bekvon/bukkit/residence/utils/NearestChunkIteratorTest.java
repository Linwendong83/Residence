package com.bekvon.bukkit.residence.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.junit.Test;

import com.bekvon.bukkit.residence.utils.NearestChunkIterator.BlockBounds;
import com.bekvon.bukkit.residence.utils.NearestChunkIterator.ChunkCandidate;

public class NearestChunkIteratorTest {

    @Test
    public void visitsEveryChunkInOrderOfItsHorizontalLowerBound() {
        assertTraversalMatchesBruteForce(1.2D, -2.7D, new BlockBounds(-48, 47, -48, 47));
    }

    @Test
    public void includesTheSourceChunkForDestinationsAboveOrBelowTheResidence() {
        NearestChunkIterator iterator = new NearestChunkIterator(
                8.25D, 8.25D, new BlockBounds(-32, 31, -32, 31));

        ChunkCandidate first = iterator.next();
        assertEquals(0, first.getChunkX());
        assertEquals(0, first.getChunkZ());
        assertEquals(0.125D, first.getMinimumDistanceSquared(), 0.000001D);
    }

    @Test
    public void isEmptyWhenThereAreNoBlockCentersInsideTheWorldBounds() {
        NearestChunkIterator iterator = new NearestChunkIterator(
                0.5D, 0.5D, new BlockBounds(1, 0, -16, 15));

        assertFalse(iterator.hasNext());
    }

    @Test
    public void matchesBruteForceWithClippedBordersAndSourcesOutsideTheBounds() {
        Random random = new Random(923847L);
        for (int iteration = 0; iteration < 100; iteration++) {
            int minX = random.nextInt(32) - 48;
            int minZ = random.nextInt(32) - 48;
            BlockBounds bounds = new BlockBounds(minX, minX + 65, minZ, minZ + 65);
            double sourceX = random.nextDouble() * 200D - 100D;
            double sourceZ = random.nextDouble() * 200D - 100D;
            assertTraversalMatchesBruteForce(sourceX, sourceZ, bounds);
            assertTraversalMatchesBruteForce(sourceX, sourceZ, bounds, new BlockBounds(-20, 20, -18, 18));
        }
    }

    @Test
    public void skipsInteriorChunksWhenTheResidenceCoversEveryStandingHeight() {
        assertTraversalMatchesBruteForce(0.5D, 0.5D,
                new BlockBounds(-48, 47, -48, 47), new BlockBounds(-20, 20, -18, 18));
    }

    @Test
    public void isEmptyWhenAFullHeightResidenceCoversTheWorldBounds() {
        BlockBounds bounds = new BlockBounds(-16, 15, -16, 15);
        NearestChunkIterator iterator = new NearestChunkIterator(0.5D, 0.5D, bounds, bounds);

        assertFalse(iterator.hasNext());
    }

    private static void assertTraversalMatchesBruteForce(double sourceX, double sourceZ, BlockBounds bounds) {
        assertTraversalMatchesBruteForce(sourceX, sourceZ, bounds, null);
    }

    private static void assertTraversalMatchesBruteForce(double sourceX, double sourceZ,
            BlockBounds bounds, BlockBounds excludedColumns) {
        Map<Long, Double> expected = new HashMap<>();
        for (int x = bounds.minX; x <= bounds.maxX; x++) {
            for (int z = bounds.minZ; z <= bounds.maxZ; z++) {
                if (excludedColumns != null && excludedColumns.contains(x, z))
                    continue;
                double dx = x + 0.5D - sourceX;
                double dz = z + 0.5D - sourceZ;
                long key = key(Math.floorDiv(x, 16), Math.floorDiv(z, 16));
                double distance = dx * dx + dz * dz;
                expected.merge(key, distance, Math::min);
            }
        }

        NearestChunkIterator iterator = new NearestChunkIterator(sourceX, sourceZ, bounds, excludedColumns);
        Set<Long> actual = new HashSet<>();
        double previousDistance = -1D;
        while (iterator.hasNext()) {
            ChunkCandidate candidate = iterator.next();
            long key = key(candidate.getChunkX(), candidate.getChunkZ());
            assertTrue(actual.add(key));
            assertTrue(candidate.getMinimumDistanceSquared() >= previousDistance);
            assertEquals(expected.get(key), candidate.getMinimumDistanceSquared(), 0.000001D);
            previousDistance = candidate.getMinimumDistanceSquared();
        }
        assertEquals(expected.keySet(), actual);
    }

    private static long key(int x, int z) {
        return (((long) x) << 32) ^ (z & 0xFFFFFFFFL);
    }
}
