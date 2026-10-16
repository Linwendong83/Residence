package com.bekvon.bukkit.residence.utils;

import java.util.Comparator;
import java.util.HashSet;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Visits chunks in order of their minimum horizontal distance to column centers.
 * This is a lower bound for any three-dimensional destination in each chunk.
 */
final class NearestChunkIterator {

    private static final int CHUNK_SIZE = 16;

    private final double sourceX;
    private final double sourceZ;
    private final BlockBounds bounds;
    private final BlockBounds excludedColumns;

    private final PriorityQueue<ChunkCandidate> pending = new PriorityQueue<>(Comparator
            .comparingDouble(ChunkCandidate::getMinimumDistanceSquared)
            .thenComparingInt(ChunkCandidate::getChunkX)
            .thenComparingInt(ChunkCandidate::getChunkZ));
    private final Set<Long> visited = new HashSet<>();

    NearestChunkIterator(double sourceX, double sourceZ, BlockBounds bounds) {
        this(sourceX, sourceZ, bounds, null);
    }

    NearestChunkIterator(double sourceX, double sourceZ, BlockBounds bounds, BlockBounds excludedColumns) {
        this.sourceX = sourceX;
        this.sourceZ = sourceZ;
        this.bounds = bounds;
        this.excludedColumns = excludedColumns;

        if (bounds.minX <= bounds.maxX && bounds.minZ <= bounds.maxZ) {
            int nearestX = clamp(nearestBlock(sourceX), bounds.minX, bounds.maxX);
            int nearestZ = clamp(nearestBlock(sourceZ), bounds.minZ, bounds.maxZ);
            addChunk(Math.floorDiv(nearestX, CHUNK_SIZE), Math.floorDiv(nearestZ, CHUNK_SIZE));
            if (excludedColumns != null) {
                int edgeX = clamp(nearestBlock(sourceX), excludedColumns.minX, excludedColumns.maxX);
                int edgeZ = clamp(nearestBlock(sourceZ), excludedColumns.minZ, excludedColumns.maxZ);
                addBlockSeed(excludedColumns.minX - 1, edgeZ);
                addBlockSeed(excludedColumns.maxX + 1, edgeZ);
                addBlockSeed(edgeX, excludedColumns.minZ - 1);
                addBlockSeed(edgeX, excludedColumns.maxZ + 1);
            }
        }
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

    double distanceSquared(int blockX, int blockZ) {
        double dx = blockX + 0.5D - sourceX;
        double dz = blockZ + 0.5D - sourceZ;
        return dx * dx + dz * dz;
    }

    BlockBounds getBounds() {
        return bounds;
    }

    private void addBlockSeed(int blockX, int blockZ) {
        blockX = clamp(blockX, bounds.minX, bounds.maxX);
        blockZ = clamp(blockZ, bounds.minZ, bounds.maxZ);
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

        if (excludedColumns == null)
            return minimumDistanceSquared(minX, maxX, minZ, maxZ);

        // A full-height residence leaves only the four horizontal outside strips.
        return Math.min(
                Math.min(minimumDistanceSquared(minX, Math.min(maxX, excludedColumns.minX - 1), minZ, maxZ),
                        minimumDistanceSquared(Math.max(minX, excludedColumns.maxX + 1), maxX, minZ, maxZ)),
                Math.min(minimumDistanceSquared(minX, maxX, minZ, Math.min(maxZ, excludedColumns.minZ - 1)),
                        minimumDistanceSquared(minX, maxX, Math.max(minZ, excludedColumns.maxZ + 1), maxZ)));
    }

    private double minimumDistanceSquared(int minX, int maxX, int minZ, int maxZ) {
        if (minX > maxX || minZ > maxZ)
            return Double.POSITIVE_INFINITY;

        int nearestX = clamp(nearestBlock(sourceX), minX, maxX);
        int nearestZ = clamp(nearestBlock(sourceZ), minZ, maxZ);
        return distanceSquared(nearestX, nearestZ);
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
