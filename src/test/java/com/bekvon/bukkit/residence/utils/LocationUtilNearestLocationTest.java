package com.bekvon.bukkit.residence.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockSupport;
import org.bukkit.block.data.BlockData;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.bekvon.bukkit.residence.protection.CuboidArea;
import com.bekvon.bukkit.residence.utils.LocationUtil.SafeLocation;
import com.bekvon.bukkit.residence.utils.NearestChunkIterator.BlockBounds;

import net.Zrips.CMILib.Items.CMIMaterial;
import net.Zrips.CMILib.Items.ItemManager;

public class LocationUtilNearestLocationTest {

    private final Map<String, Material> blocks = new HashMap<>();
    private Map<Material, CMIMaterial> materials;
    private Map<Material, CMIMaterial> previousMaterials;
    private World world;
    private ChunkSnapshot snapshot;
    private Location source;
    private CuboidArea area;
    private BlockBounds bounds;

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() throws Exception {
        Field materialsField = ItemManager.class.getDeclaredField("byRealMaterial");
        materialsField.setAccessible(true);
        materials = (Map<Material, CMIMaterial>) materialsField.get(null);
        previousMaterials = new HashMap<>(materials);
        materials.put(Material.AIR, CMIMaterial.AIR);
        materials.put(Material.STONE, CMIMaterial.STONE);
        materials.put(Material.WATER, CMIMaterial.WATER);

        world = mock(World.class);
        when(world.getName()).thenReturn("test");
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(128);
        source = new Location(world, 0.5D, 64D, 0.5D);
        area = new CuboidArea(new Location(world, 0, 60, 0), new Location(world, 0, 70, 0));
        bounds = new BlockBounds(0, 5, 0, 0);

        BlockData air = mock(BlockData.class);
        BlockData stone = mock(BlockData.class);
        when(stone.isFaceSturdy(BlockFace.UP, BlockSupport.FULL)).thenReturn(true);
        when(stone.isOccluding()).thenReturn(true);

        snapshot = mock(ChunkSnapshot.class);
        when(snapshot.getHighestBlockYAt(anyInt(), anyInt())).thenReturn(127);
        when(snapshot.getBlockType(anyInt(), anyInt(), anyInt())).thenAnswer(call ->
                blocks.getOrDefault(key(call.getArgument(0), call.getArgument(1), call.getArgument(2)), Material.AIR));
        when(snapshot.getBlockData(anyInt(), anyInt(), anyInt())).thenAnswer(call ->
                blocks.get(key(call.getArgument(0), call.getArgument(1), call.getArgument(2))) == Material.STONE ? stone : air);
    }

    @After
    public void restoreMaterials() {
        if (materials != null && previousMaterials != null) {
            materials.clear();
            materials.putAll(previousMaterials);
        }
    }

    @Test
    public void choosesAFartherHorizontalColumnWhenItsHeightMakesItCloser() {
        floor(1, 100);
        floor(5, 63);

        List<SafeLocation> candidates = candidates(Double.POSITIVE_INFINITY);
        assertEquals(2, candidates.size());
        assertEquals(new Location(world, 5.5D, 64D, 0.5D), candidates.get(0).location);
        assertEquals(25D, candidates.get(0).distanceSquared, 0D);
        assertEquals(1370D, candidates.get(1).distanceSquared, 0D);
    }

    @Test
    public void considersUndergroundFloorsInTheSameColumnAsTheSurface() {
        floor(1, 63);
        floor(1, 95);

        List<SafeLocation> candidates = candidates(Double.POSITIVE_INFINITY);
        assertEquals(2, candidates.size());
        assertEquals(64, candidates.get(0).location.getBlockY());
        assertEquals(96, candidates.get(1).location.getBlockY());
    }

    @Test
    public void considersSafeFloorsDirectlyAboveAndBelowTheResidence() {
        source.setY(69.5D);
        floor(0, 70);
        floor(0, 57);

        List<SafeLocation> candidates = candidates(Double.POSITIVE_INFINITY);
        assertEquals(2, candidates.size());
        assertEquals(new Location(world, 0.5D, 71D, 0.5D), candidates.get(0).location);
        assertEquals(new Location(world, 0.5D, 58D, 0.5D), candidates.get(1).location);
        assertEquals(2.25D, candidates.get(0).distanceSquared, 0D);
    }

    @Test
    public void rejectsFloorsWhoseFeetAreStillInsideTheResidence() {
        floor(0, 63);
        floor(0, 70);

        List<SafeLocation> candidates = candidates(Double.POSITIVE_INFINITY);
        assertEquals(1, candidates.size());
        assertEquals(71, candidates.get(0).location.getBlockY());
        assertFalse(area.containsLoc(candidates.get(0).location));
    }

    @Test
    public void doesNotAbandonAnUndergroundFloorBecauseWaterExistsHigherUp() {
        floor(1, 63);
        blocks.put(key(1, 80, 0), Material.WATER);

        List<SafeLocation> candidates = candidates(Double.POSITIVE_INFINITY);
        assertEquals(1, candidates.size());
        assertEquals(64, candidates.get(0).location.getBlockY());
    }

    @Test
    public void rejectsSolidAndLiquidBodySpaces() {
        floor(1, 63);
        floor(1, 64);
        floor(2, 63);
        blocks.put(key(2, 65, 0), Material.WATER);
        floor(3, 63);

        List<SafeLocation> candidates = candidates(Double.POSITIVE_INFINITY);
        assertFalse(candidates.stream().anyMatch(candidate ->
                candidate.location.getBlockY() == 64 && candidate.location.getBlockX() < 3));
        assertTrue(candidates.stream().anyMatch(candidate ->
                candidate.location.equals(new Location(world, 3.5D, 64D, 0.5D))));
    }

    @Test
    public void includesSafeFloorsAtNegativeWorldHeights() {
        source.setY(-50D);
        area = new CuboidArea(new Location(world, 0, -52, 0), new Location(world, 0, -48, 0));
        floor(1, -55);

        List<SafeLocation> candidates = candidates(Double.POSITIVE_INFINITY);
        assertEquals(1, candidates.size());
        assertEquals(-54, candidates.get(0).location.getBlockY());
        assertEquals(17D, candidates.get(0).distanceSquared, 0D);
    }

    @Test
    public void prunesCandidatesUsingTheirCompleteThreeDimensionalDistance() {
        floor(1, 80);
        floor(3, 64);
        floor(5, 63);

        List<SafeLocation> candidates = candidates(25D);
        assertEquals(1, candidates.size());
        assertEquals(new Location(world, 3.5D, 65D, 0.5D), candidates.get(0).location);
        assertEquals(10D, candidates.get(0).distanceSquared, 0D);
    }

    private List<SafeLocation> candidates(double maximumDistanceSquared) {
        NearestChunkIterator chunks = new NearestChunkIterator(source.getX(), source.getZ(), bounds);
        return LocationUtil.getSafeLocations(snapshot, source, area, chunks, chunks.next(), world.getMinHeight(), maximumDistanceSquared);
    }

    private void floor(int x, int supportY) {
        blocks.put(key(x, supportY, 0), Material.STONE);
    }

    private static String key(int x, int y, int z) {
        return x + ":" + y + ":" + z;
    }
}
