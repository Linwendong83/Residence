package com.bekvon.bukkit.residence.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import org.junit.Test;

import com.bekvon.bukkit.residence.utils.NearestOutsideChunkIterator.BlockBounds;
import com.bekvon.bukkit.residence.utils.NearestOutsideChunkIterator.ChunkCandidate;

public class NearestOutsideChunkIteratorTest {

    @Test
    public void visitsEveryEligibleChunkInNonDecreasingDistanceOrder() {
        BlockBounds bounds = new BlockBounds(-48, 47, -48, 47);
        NearestOutsideChunkIterator iterator = new NearestOutsideChunkIterator(
                1.2D, -2.7D, -20, 20, -18, 18, bounds);

        Set<Long> actual = new HashSet<>();
        double previousDistance = -1D;
        while (iterator.hasNext()) {
            ChunkCandidate candidate = iterator.next();
            assertTrue(candidate.getMinimumDistanceSquared() >= previousDistance);
            previousDistance = candidate.getMinimumDistanceSquared();
            actual.add(key(candidate.getChunkX(), candidate.getChunkZ()));
        }

        Set<Long> expected = new HashSet<>();
        for (int chunkX = -3; chunkX <= 2; chunkX++) {
            for (int chunkZ = -3; chunkZ <= 2; chunkZ++) {
                if (containsOutsideColumn(chunkX, chunkZ, -20, 20, -18, 18))
                    expected.add(key(chunkX, chunkZ));
            }
        }
        assertEquals(expected, actual);
    }

    @Test
    public void startsWithTheChunkContainingTheNearestOutsideColumn() {
        NearestOutsideChunkIterator iterator = new NearestOutsideChunkIterator(
                8.25D, 8.25D, 0, 15, 0, 15, new BlockBounds(-32, 31, -32, 31));

        ChunkCandidate first = iterator.next();
        assertEquals(8.25D * 8.25D + 0.25D * 0.25D,
                first.getMinimumDistanceSquared(), 0.000001D);
    }

    @Test
    public void isEmptyWhenTheAreaCoversTheWorldBounds() {
        NearestOutsideChunkIterator iterator = new NearestOutsideChunkIterator(
                0.5D, 0.5D, -16, 15, -16, 15, new BlockBounds(-16, 15, -16, 15));

        assertFalse(iterator.hasNext());
    }

    @Test
    public void remainsOrderedForVariedNegativeAndPositiveCoordinates() {
        Random random = new Random(923847L);
        BlockBounds bounds = new BlockBounds(-64, 63, -64, 63);

        for (int iteration = 0; iteration < 100; iteration++) {
            int areaMinX = random.nextInt(65) - 32;
            int areaMinZ = random.nextInt(65) - 32;
            int areaMaxX = areaMinX + random.nextInt(24) + 1;
            int areaMaxZ = areaMinZ + random.nextInt(24) + 1;
            double sourceX = areaMinX + random.nextDouble() * (areaMaxX - areaMinX + 1);
            double sourceZ = areaMinZ + random.nextDouble() * (areaMaxZ - areaMinZ + 1);

            NearestOutsideChunkIterator iterator = new NearestOutsideChunkIterator(
                    sourceX, sourceZ, areaMinX, areaMaxX, areaMinZ, areaMaxZ, bounds);
            double previousDistance = -1D;
            int visitedChunks = 0;
            while (iterator.hasNext()) {
                ChunkCandidate candidate = iterator.next();
                assertTrue(candidate.getMinimumDistanceSquared() >= previousDistance);
                previousDistance = candidate.getMinimumDistanceSquared();
                visitedChunks++;
            }

            int expectedChunks = 0;
            for (int chunkX = -4; chunkX <= 3; chunkX++) {
                for (int chunkZ = -4; chunkZ <= 3; chunkZ++) {
                    if (containsOutsideColumn(chunkX, chunkZ, areaMinX, areaMaxX, areaMinZ, areaMaxZ))
                        expectedChunks++;
                }
            }
            assertEquals(expectedChunks, visitedChunks);
        }
    }

    private static boolean containsOutsideColumn(int chunkX, int chunkZ,
            int areaMinX, int areaMaxX, int areaMinZ, int areaMaxZ) {
        for (int x = chunkX * 16; x < chunkX * 16 + 16; x++) {
            for (int z = chunkZ * 16; z < chunkZ * 16 + 16; z++) {
                if (x < areaMinX || x > areaMaxX || z < areaMinZ || z > areaMaxZ)
                    return true;
            }
        }
        return false;
    }

    private static long key(int x, int z) {
        return (((long) x) << 32) ^ (z & 0xFFFFFFFFL);
    }
}
