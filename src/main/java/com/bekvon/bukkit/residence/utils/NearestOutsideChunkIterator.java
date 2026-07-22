package com.bekvon.bukkit.residence.utils;

import java.util.Comparator;
import java.util.HashSet;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Visits chunks containing columns outside a rectangle in exact nearest-first
 * order. Distances are measured from the source to block-column centers.
 */
final class NearestOutsideChunkIterator {

    private static final int CHUNK_SIZE = 16;

    private final double sourceX;
    private final double sourceZ;
    private final int areaMinX;
    private final int areaMaxX;
    private final int areaMinZ;
    private final int areaMaxZ;
    private final BlockBounds bounds;

    private final PriorityQueue<ChunkCandidate> pending = new PriorityQueue<>(Comparator
            .comparingDouble(ChunkCandidate::getMinimumDistanceSquared)
            .thenComparingInt(ChunkCandidate::getChunkX)
            .thenComparingInt(ChunkCandidate::getChunkZ));
    private final Set<Long> visited = new HashSet<>();

    NearestOutsideChunkIterator(double sourceX, double sourceZ,
            int areaMinX, int areaMaxX, int areaMinZ, int areaMaxZ,
            BlockBounds bounds) {
        this.sourceX = sourceX;
        this.sourceZ = sourceZ;
        this.areaMinX = areaMinX;
        this.areaMaxX = areaMaxX;
        this.areaMinZ = areaMinZ;
        this.areaMaxZ = areaMaxZ;
        this.bounds = bounds;

        int nearestX = clamp(nearestBlock(sourceX), areaMinX, areaMaxX);
        int nearestZ = clamp(nearestBlock(sourceZ), areaMinZ, areaMaxZ);

        addBlockSeed(areaMinX - 1, nearestZ);
        addBlockSeed(areaMaxX + 1, nearestZ);
        addBlockSeed(nearestX, areaMinZ - 1);
        addBlockSeed(nearestX, areaMaxZ + 1);
    }

    boolean hasNext() {
        return !pending.isEmpty();
    }

    ChunkCandidate next() {
        ChunkCandidate current = pending.poll();
        if (current == null)
            return null;

        addChunk(current.chunkX - 1, current.chunkZ);
        addChunk(current.chunkX + 1, current.chunkZ);
        addChunk(current.chunkX, current.chunkZ - 1);
        addChunk(current.chunkX, current.chunkZ + 1);
        return current;
    }

    boolean isOutsideArea(int blockX, int blockZ) {
        return blockX < areaMinX || blockX > areaMaxX || blockZ < areaMinZ || blockZ > areaMaxZ;
    }

    double distanceSquared(int blockX, int blockZ) {
        double dx = blockX + 0.5D - sourceX;
        double dz = blockZ + 0.5D - sourceZ;
        return dx * dx + dz * dz;
    }

    BlockBounds getBounds() {
        return bounds;
    }

    private void addBlockSeed(int blockX, int blockZ) {
        if (!bounds.contains(blockX, blockZ))
            return;
        addChunk(Math.floorDiv(blockX, CHUNK_SIZE), Math.floorDiv(blockZ, CHUNK_SIZE));
    }

    private void addChunk(int chunkX, int chunkZ) {
        long key = (((long) chunkX) << 32) ^ (chunkZ & 0xFFFFFFFFL);
        if (!visited.add(key))
            return;

        double minimumDistanceSquared = minimumDistanceSquared(chunkX, chunkZ);
        if (Double.isFinite(minimumDistanceSquared))
            pending.add(new ChunkCandidate(chunkX, chunkZ, minimumDistanceSquared));
    }

    private double minimumDistanceSquared(int chunkX, int chunkZ) {
        int minX = Math.max(bounds.minX, chunkX * CHUNK_SIZE);
        int maxX = Math.min(bounds.maxX, chunkX * CHUNK_SIZE + CHUNK_SIZE - 1);
        int minZ = Math.max(bounds.minZ, chunkZ * CHUNK_SIZE);
        int maxZ = Math.min(bounds.maxZ, chunkZ * CHUNK_SIZE + CHUNK_SIZE - 1);

        if (minX > maxX || minZ > maxZ)
            return Double.POSITIVE_INFINITY;

        double minimum = Double.POSITIVE_INFINITY;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (!isOutsideArea(x, z))
                    continue;
                minimum = Math.min(minimum, distanceSquared(x, z));
            }
        }
        return minimum;
    }

    private static int nearestBlock(double coordinate) {
        return (int) Math.floor(coordinate);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    static final class BlockBounds {
        final int minX;
        final int maxX;
        final int minZ;
        final int maxZ;

        BlockBounds(int minX, int maxX, int minZ, int maxZ) {
            this.minX = minX;
            this.maxX = maxX;
            this.minZ = minZ;
            this.maxZ = maxZ;
        }

        boolean contains(int blockX, int blockZ) {
            return blockX >= minX && blockX <= maxX && blockZ >= minZ && blockZ <= maxZ;
        }
    }

    static final class ChunkCandidate {
        private final int chunkX;
        private final int chunkZ;
        private final double minimumDistanceSquared;

        ChunkCandidate(int chunkX, int chunkZ, double minimumDistanceSquared) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.minimumDistanceSquared = minimumDistanceSquared;
        }

        int getChunkX() {
            return chunkX;
        }

        int getChunkZ() {
            return chunkZ;
        }

        double getMinimumDistanceSquared() {
            return minimumDistanceSquared;
        }
    }
}
