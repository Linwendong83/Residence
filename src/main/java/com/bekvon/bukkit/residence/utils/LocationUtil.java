package com.bekvon.bukkit.residence.utils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockSupport;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import com.bekvon.bukkit.residence.Residence;
import com.bekvon.bukkit.residence.containers.Flags;
import com.bekvon.bukkit.residence.containers.ResAdmin;
import com.bekvon.bukkit.residence.containers.lm;
import com.bekvon.bukkit.residence.permissions.PermissionManager.ResPerm;
import com.bekvon.bukkit.residence.protection.ClaimedResidence;
import com.bekvon.bukkit.residence.protection.CuboidArea;
import com.bekvon.bukkit.residence.protection.FlagPermissions.FlagCombo;
import com.bekvon.bukkit.residence.utils.NearestOutsideChunkIterator.BlockBounds;
import com.bekvon.bukkit.residence.utils.NearestOutsideChunkIterator.ChunkCandidate;

import net.Zrips.CMILib.Container.CMIWorld;
import net.Zrips.CMILib.Items.CMIMC;
import net.Zrips.CMILib.Items.CMIMaterial;
import net.Zrips.CMILib.Version.Version;
import net.Zrips.CMILib.Version.PaperMethods.CMIChunkSnapShot;
import net.Zrips.CMILib.Version.PaperMethods.PaperLib;
import net.Zrips.CMILib.Version.Schedulers.CMIScheduler;

public class LocationUtil {
    static Method getBlockTypeId = null;

    private static CMIMaterial getBlockType(ChunkSnapshot snap, World world, int localX, int localY, int localZ) {
        return getBlockType(snap, new PositionRelativeData(world, localX, localY, localZ));
    }

    private static CMIMaterial getBlockType(ChunkSnapshot snap, Location loc) {
        return getBlockType(snap, new PositionRelativeData(loc));
    }

    private static CMIMaterial getBlockType(ChunkSnapshot snap, PositionRelativeData data) {
        return getBlockType(snap, data.getLocalY(), data);
    }

    @SuppressWarnings("deprecation")
    private static CMIMaterial getBlockType(ChunkSnapshot snap, int localY, PositionRelativeData data) {
        if (localY > data.getMaxWorldY() || localY < data.getMinWorldY())
            return CMIMaterial.AIR;

        if (snap == null)
            return CMIMaterial.AIR;

        if (Version.isCurrentEqualOrHigher(Version.v1_13_R1)) {

            if (localY >= data.getMaxWorldY())
                return CMIMaterial.AIR;

            return CMIMaterial.get(snap.getBlockType(data.getLocalX(), localY, data.getLocalZ()));
        }

        if (snap.getHighestBlockYAt(data.getLocalX(), data.getLocalZ()) < localY)
            return CMIMaterial.AIR;

        if (getBlockTypeId == null)
            try {
                getBlockTypeId = snap.getClass().getMethod("getBlockTypeId", int.class, int.class, int.class);
            } catch (Throwable e) {
                e.printStackTrace();
            }

        try {
            return CMIMaterial.get((int) getBlockTypeId.invoke(snap, data.getLocalX(), localY, data.getLocalZ()));
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return CMIMaterial.AIR;
    }

    private static boolean isEmptyBlock(CMIChunkSnapShot chunk, Location loc) {

        if (chunk == null)
            return false;

        CMIMaterial material = getBlockType(chunk.getSnapshot(), loc);

        return isEmptyBlock(material);
    }

    private static boolean isEmptyBlock(Material material) {
        return isEmptyBlock(CMIMaterial.get(material));
    }

    private static boolean isEmptyBlock(CMIMaterial material) {
        return material.containsCriteria(CMIMC.NOCOLLISIONBOX);
    }

    private static boolean isValidLocation(CMIChunkSnapShot chunk, Location loc) {

        if (chunk == null)
            return false;

        int chunkX = loc.getBlockX() & 0xF;
        int chunkZ = loc.getBlockZ() & 0xF;

        CMIMaterial material = getBlockType(chunk.getSnapshot(), loc.getWorld(), chunkX, loc.getBlockY(), chunkZ);

        if (!isEmptyBlock(material))
            return false;

        CMIMaterial material1 = getBlockType(chunk.getSnapshot(), loc.getWorld(), chunkX, loc.getBlockY() + 1, chunkZ);

        if (!isEmptyBlock(material1))
            return false;

        CMIMaterial material2 = getBlockType(chunk.getSnapshot(), loc.getWorld(), chunkX, loc.getBlockY() - 1, chunkZ);

        if (material2 == CMIMaterial.LAVA)
            return false;

        if (isEmptyBlock(material2))
            return false;

        return true;
    }

    public static CompletableFuture<Location> getOutsideFreeLocASYNC(ClaimedResidence res, Player player, boolean toSpawnOnFail) {
        return getOutsideFreeLocASYNC(res, player.getLocation(), player, toSpawnOnFail);
    }

    public static CompletableFuture<Location> getOutsideFreeLocASYNC(ClaimedResidence res, Location insideLoc, Player player, boolean toSpawnOnFail) {
        return CompletableFuture.supplyAsync(() -> getOutsideFreeLoc(res, insideLoc, player, toSpawnOnFail));
    }

    private static Location getNearestOutsideLocation(Player player, Location source, CuboidArea area) {
        World world = area.getWorld();
        if (world == null || source == null || source.getWorld() == null || !world.equals(source.getWorld()))
            return null;

        Vector low = area.getLowVector();
        Vector high = area.getHighVector();
        BlockBounds bounds = getWorldBorderBounds(world.getWorldBorder());
        NearestOutsideChunkIterator chunks = new NearestOutsideChunkIterator(
                source.getX(), source.getZ(),
                low.getBlockX(), high.getBlockX(), low.getBlockZ(), high.getBlockZ(), bounds);

        SafeColumn best = null;
        while (chunks.hasNext()) {
            ChunkCandidate chunkCandidate = chunks.next();
            if (best != null && chunkCandidate.getMinimumDistanceSquared() >= best.distanceSquared)
                break;

            CMIChunkSnapShot cmiSnapshot = getSnapshot(world, chunkCandidate.getChunkX(), chunkCandidate.getChunkZ(), true, false).join();
            if (cmiSnapshot == null || cmiSnapshot.getSnapshot() == null)
                return null;

            List<SafeColumn> candidates = getSafeColumns(
                    cmiSnapshot.getSnapshot(), world, chunks, chunkCandidate, best == null ? Double.POSITIVE_INFINITY : best.distanceSquared);
            SafeColumn allowed = getFirstAllowedColumn(player, candidates);
            if (allowed != null && (best == null || allowed.distanceSquared < best.distanceSquared))
                best = allowed;
        }

        return best == null ? null : best.location;
    }

    private static List<SafeColumn> getSafeColumns(ChunkSnapshot snapshot, World world,
            NearestOutsideChunkIterator chunks, ChunkCandidate chunkCandidate, double maximumDistanceSquared) {
        List<SafeColumn> candidates = new ArrayList<>();
        BlockBounds bounds = chunks.getBounds();

        int minX = Math.max(bounds.minX, chunkCandidate.getChunkX() * 16);
        int maxX = Math.min(bounds.maxX, chunkCandidate.getChunkX() * 16 + 15);
        int minZ = Math.max(bounds.minZ, chunkCandidate.getChunkZ() * 16);
        int maxZ = Math.min(bounds.maxZ, chunkCandidate.getChunkZ() * 16 + 15);

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (!chunks.isOutsideArea(x, z))
                    continue;

                double distanceSquared = chunks.distanceSquared(x, z);
                if (distanceSquared >= maximumDistanceSquared)
                    continue;

                Location safeLocation = getSpawnLikeLocation(snapshot, world, x, z);
                if (safeLocation != null)
                    candidates.add(new SafeColumn(safeLocation, distanceSquared));
            }
        }

        candidates.sort(Comparator
                .comparingDouble((SafeColumn candidate) -> candidate.distanceSquared)
                .thenComparingInt(candidate -> candidate.location.getBlockX())
                .thenComparingInt(candidate -> candidate.location.getBlockZ()));
        return candidates;
    }

    private static Location getSpawnLikeLocation(ChunkSnapshot snapshot, World world, int blockX, int blockZ) {
        int localX = blockX & 0xF;
        int localZ = blockZ & 0xF;
        int minimumY = CMIWorld.getMinHeight(world);
        int maximumY = world.getMaxHeight();
        int highestY = Math.min(snapshot.getHighestBlockYAt(localX, localZ), maximumY - 1);

        for (int supportY = highestY; supportY >= minimumY; supportY--) {
            Material supportMaterial = snapshot.getBlockType(localX, supportY, localZ);
            BlockData supportData = snapshot.getBlockData(localX, supportY, localZ);

            // Vanilla's overworld spawn lookup abandons a column when it meets fluid.
            if (isLiquid(supportMaterial, supportData))
                return null;
            if (!isSafeSupport(supportMaterial, supportData))
                continue;

            int feetY = supportY + 1;
            if (feetY + 1 >= maximumY)
                continue;
            return new Location(world, blockX + 0.5D, feetY, blockZ + 0.5D);
        }
        return null;
    }

    private static boolean isSafeSupport(Material material, BlockData data) {
        if (isDamaging(material) || isLiquid(material, data))
            return false;
        return data.isFaceSturdy(BlockFace.UP, BlockSupport.FULL);
    }

    private static boolean isLiquid(Material material, BlockData data) {
        return (CMIMaterial.isWater(material) || CMIMaterial.isLava(material)) ||
                data instanceof Waterlogged && ((Waterlogged) data).isWaterlogged();
    }

    private static boolean isDamaging(Material material) {
        return CMIMaterial.get(material).containsCriteria(CMIMC.DAMAGECAUSING);
    }

    private static SafeColumn getFirstAllowedColumn(Player player, List<SafeColumn> candidates) {
        if (candidates.isEmpty())
            return null;

        SafeColumn[] allowed = new SafeColumn[1];
        CMIScheduler.runAtLocation(Residence.getInstance(), candidates.get(0).location, () -> {
            boolean residenceAdmin = player != null && ResAdmin.isResAdmin(player);
            boolean bypassTp = player != null && ResPerm.bypass_tp.hasPermission(player);
            boolean adminTp = player != null && ResPerm.admin_tp.hasPermission(player);
            boolean adminMove = player != null && ResPerm.admin_move.hasPermission(player);

            for (SafeColumn candidate : candidates) {
                if (!isSafeLiveLocation(candidate.location))
                    continue;

                ClaimedResidence target = Residence.getInstance().getResidenceManager().getByLoc(candidate.location);
                if (canUseAsOutsideDestination(player, target, residenceAdmin, bypassTp, adminTp, adminMove)) {
                    allowed[0] = candidate;
                    return;
                }
            }
        }).join();
        return allowed[0];
    }

    static boolean canUseAsOutsideDestination(Player player, ClaimedResidence target,
            boolean residenceAdmin, boolean bypassTp, boolean adminTp, boolean adminMove) {
        if (target == null)
            return true;
        if (player == null)
            return false;
        if (residenceAdmin || bypassTp || target.isOwner(player))
            return true;

        boolean teleportDenied = target.getPermissions().playerHas(player, Flags.tp, FlagCombo.OnlyFalse);
        boolean movementDenied = target.getPermissions().playerHas(player, Flags.move, FlagCombo.OnlyFalse);
        return (!teleportDenied || adminTp) && (!movementDenied || adminMove);
    }

    private static boolean isSafeLiveLocation(Location location) {
        World world = location.getWorld();
        if (world == null)
            return false;
        if (!world.getWorldBorder().isInside(location))
            return false;

        int blockX = location.getBlockX();
        int feetY = location.getBlockY();
        int blockZ = location.getBlockZ();

        Block support = world.getBlockAt(blockX, feetY - 1, blockZ);
        BlockData supportData = support.getBlockData();
        if (!isSafeSupport(support.getType(), supportData))
            return false;

        for (int y = feetY; y <= feetY + 1; y++) {
            Block block = world.getBlockAt(blockX, y, blockZ);
            BlockData data = block.getBlockData();
            if (isLiquid(block.getType(), data) || isDamaging(block.getType()))
                return false;

            BoundingBox localPlayerBox = new BoundingBox(
                    0.2D, feetY - y, 0.2D,
                    0.8D, feetY + 1.8D - y, 0.8D);
            if (block.getCollisionShape().overlaps(localPlayerBox))
                return false;
        }
        return true;
    }

    private static BlockBounds getWorldBorderBounds(WorldBorder border) {
        double halfSize = border.getSize() / 2D;
        double absoluteHalfSize = border.getMaxSize() / 2D;
        double minimumX = Math.max(border.getCenter().getX() - halfSize, -absoluteHalfSize);
        double maximumX = Math.min(border.getCenter().getX() + halfSize, absoluteHalfSize);
        double minimumZ = Math.max(border.getCenter().getZ() - halfSize, -absoluteHalfSize);
        double maximumZ = Math.min(border.getCenter().getZ() + halfSize, absoluteHalfSize);

        return new BlockBounds(
                (int) Math.ceil(minimumX - 0.5D),
                (int) Math.ceil(maximumX - 0.5D) - 1,
                (int) Math.ceil(minimumZ - 0.5D),
                (int) Math.ceil(maximumZ - 0.5D) - 1);
    }

    private static final class SafeColumn {
        private final Location location;
        private final double distanceSquared;

        private SafeColumn(Location location, double distanceSquared) {
            this.location = location;
            this.distanceSquared = distanceSquared;
        }
    }

    private static Location fallBackLocation(ClaimedResidence res, Player player, boolean toSpawnOnFail) {
        if (Residence.getInstance().getConfigManager().getKickLocation() != null)
            return Residence.getInstance().getConfigManager().getKickLocation();

        if (!toSpawnOnFail)
            return null;

        World bw = res.getPermissions().getBukkitWorld();

        if (bw == null)
            return player.getWorld().getSpawnLocation();

        return bw.getSpawnLocation();
    }

    private static Location getOutsideFreeLoc(ClaimedResidence res, Location insideLoc, Player player, boolean toSpawnOnFail) {

        CuboidArea area = res.getAreaByLoc(insideLoc);

        if (area == null)
            area = res.getMainArea();

        if (area == null)
            return fallBackLocation(res, player, toSpawnOnFail);

        Location loc = getNearestOutsideLocation(player, insideLoc, area);

        if (loc == null)
            return fallBackLocation(res, player, toSpawnOnFail);

        if (player != null) {
            loc.setPitch(player.getLocation().getPitch());
            loc.setYaw(player.getLocation().getYaw());
        }

        return loc;
    }

    public static CompletableFuture<Location> getMiddleFreeLocASYNC(ClaimedResidence res, Player player, boolean toSpawnOnFail) {
        return getMiddleFreeLocASYNC(res, player.getLocation(), player, toSpawnOnFail);
    }

    public static CompletableFuture<Location> getMiddleFreeLocASYNC(ClaimedResidence res, Location insideLoc, Player player, boolean toSpawnOnFail) {
        return CompletableFuture.supplyAsync(() -> getMiddleFreeLoc(res, insideLoc, player, toSpawnOnFail));
    }

    private static Location getMiddleFreeLoc(ClaimedResidence res, Location insideLoc, Player player, boolean toSpawnOnFail) {

        if (insideLoc == null)
            return null;

        CuboidArea area = res.getAreaByLoc(insideLoc);
        if (area == null) {
            return insideLoc;
        }

        int y = area.getHighVector().getBlockY();
        int lowY = area.getLowVector().getBlockY();

        int x = area.getLowVector().getBlockX() + area.getXSize() / 2;
        int z = area.getLowVector().getBlockZ() + area.getZSize() / 2;

        Location newLoc = new Location(area.getWorld(), x + 0.5, y, z + 0.5);

        int it = 1;
        int maxIt = y - 2;

        CompletableFuture<CMIChunkSnapShot> cs = PaperLib.getSnapshot(newLoc, false, false);

        CMIChunkSnapShot chunk = cs.join();

        if (chunk == null)
            return null;

        while (it < maxIt) {
            it++;

            if (newLoc.getBlockY() < lowY)
                break;

            newLoc.add(0, -1, 0);

            try {
                if (isValidLocation(chunk, newLoc)) {
                    if (player != null) {
                        newLoc.setPitch(player.getLocation().getPitch());
                        newLoc.setYaw(player.getLocation().getYaw());
                    }
                    return newLoc;
                }
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }

        return getOutsideFreeLoc(res, insideLoc, player, toSpawnOnFail);
    }

    public static CompletableFuture<Location> getTeleportLocationASYNC(ClaimedResidence res, Player player, boolean toSpawnOnFail) {
        return CompletableFuture.supplyAsync(() -> getTeleportLocation(res, player, toSpawnOnFail));
    }

    public static Location getTeleportLocation(ClaimedResidence res, Player player, boolean toSpawnOnFail) {

        if (res.tpLoc == null || res.getMainArea() != null && !res.containsLoc(new Location(res.getMainArea().getWorld(), res.tpLoc.getX(), res.tpLoc.getY(), res.tpLoc.getZ()))) {

            if (res.getMainArea() == null)
                return null;

            Vector low = res.getMainArea().getLowVector();
            Vector high = res.getMainArea().getHighVector();

            Location t = new Location(res.getMainArea().getWorld(), (low.getBlockX() + high.getBlockX()) / 2,
                    (low.getBlockY() + high.getBlockY()) / 2, (low.getBlockZ() + high.getBlockZ()) / 2);

            t = getMiddleFreeLoc(res, t, player, toSpawnOnFail);

            if (t == null)
                return null;

            res.tpLoc = t.toVector();
        }

        if (res.tpLoc == null)
            return null;

        Location loc = res.tpLoc.toLocation(res.getMainArea().getLowLocation().getWorld());
        if (res.PitchYaw != null) {
            loc.setPitch((float) res.PitchYaw.getX());
            loc.setYaw((float) res.PitchYaw.getY());
        }
        return loc;
    }

    public static CompletableFuture<LocationCheck> isSafeTeleportASYNC(ClaimedResidence res, Player player) {
        return CompletableFuture.supplyAsync(() -> isSafeTp(res, player));
    }

    public static LocationCheck isSafeTp(ClaimedResidence res, Player player) {

        LocationCheck validity = new LocationCheck();

        if (player.getAllowFlight())
            return validity;

        if (player.getGameMode() == GameMode.CREATIVE)
            return validity;

        if (Utils.isSpectator(player.getGameMode()))
            return validity;

        if (res.tpLoc == null)
            return validity;

        Location tempLoc = getTeleportLocation(res, player, false);

        if (tempLoc == null)
            return validity;

        CompletableFuture<CMIChunkSnapShot> cs = PaperLib.getSnapshot(tempLoc, false, false);

        CMIChunkSnapShot chunk = cs.join();

        if (chunk == null)
            return validity;

        int fallDistance = 0;
        int minY = CMIWorld.getMinHeight(tempLoc.getWorld());
        for (int i = (int) tempLoc.getY(); i >= minY; i--) {
            if (i <= minY) {
                validity.setValidity(LocationValidity.Void);
                break;
            }

            tempLoc.setY(i);

            int chunkX = tempLoc.getBlockX() & 0xF;
            int chunkZ = tempLoc.getBlockZ() & 0xF;

            CMIMaterial material = getBlockType(chunk.getSnapshot(), tempLoc.getWorld(), chunkX, tempLoc.getBlockY(), chunkZ);

            // In later updates can be changed to check if material contains
            // CMIMC.DAMAGECAUSING
            if (material.isLava()
                    || material.equals(CMIMaterial.MAGMA_BLOCK)
                    || material.equals(CMIMaterial.FIRE)
                    || material.equals(CMIMaterial.SOUL_FIRE)
                    || material.equals(CMIMaterial.SWEET_BERRY_BUSH)
                    || material.equals(CMIMaterial.POINTED_DRIPSTONE)
                    || material.equals(CMIMaterial.CAMPFIRE)
                    || material.equals(CMIMaterial.SOUL_CAMPFIRE)
                    || material.equals(CMIMaterial.LAVA_CAULDRON)) {
                validity.setValidity(LocationValidity.DamageBlock);
                validity.setDamagingMaterial(material);
                break;
            } else {

                if (isEmptyBlock(material)) {
                    fallDistance++;
                } else {
                    break;
                }
            }
        }
        validity.setFallDistance(fallDistance);

        if (validity.getValidity().equals(LocationValidity.Valid) && fallDistance > 3) {
            validity.setValidity(LocationValidity.Fall);
        }

        return validity;
    }

    public static CompletableFuture<CMIChunkSnapShot> getSnapshot(Location loc, boolean generate, boolean biomeData) {
        return getSnapshot(loc.getWorld(), loc.getBlockX() >> 4, loc.getBlockZ() >> 4, generate, biomeData);
    }

    public static CompletableFuture<CMIChunkSnapShot> getSnapshot(World world, int chunkX, int chunkZ, boolean generate, boolean biomeData) {
        if (world == null)
            return CompletableFuture.completedFuture(null);

        CompletableFuture<Chunk> future = null;
        try {
            if (!Version.isPaperBranch()) {
                return CompletableFuture.supplyAsync(() -> {
                    CMIChunkSnapShot cmiChunkSnapshot = new CMIChunkSnapShot(world);
                    try {

                        CompletableFuture<Void> t = CMIScheduler.runAtLocation(Residence.getInstance(), new Location(world, chunkX * 16, 0, chunkZ * 16), () -> cmiChunkSnapshot.setSnapshot(world
                                .getChunkAt(chunkX, chunkZ)
                                .getChunkSnapshot(true, biomeData, false)));

                        t = t.exceptionally(ex -> {
                            lm.consoleMessage("Could not get chunk snapshot for " + world + " " + (chunkX * 16) + ":" + (chunkZ * 16));
                            ex.printStackTrace();
                            return null;
                        });
                        t.get();
                    } catch (Throwable e) {
                        e.printStackTrace();
                        Thread.currentThread().interrupt();
                    }
                    return cmiChunkSnapshot;
                });
            }
            future = PaperLib.getChunkAtAsync(world, chunkX, chunkZ, generate);
        } catch (Throwable e) {
            e.printStackTrace();
        }

        if (future == null)
            return CompletableFuture.completedFuture(null);

        return future.thenComposeAsync(chunk -> CompletableFuture.supplyAsync(() -> {
            CMIChunkSnapShot cmiChunkSnapshot = new CMIChunkSnapShot(world);

            if (chunk == null)
                return cmiChunkSnapshot;

            try {
                CompletableFuture<Void> t = CMIScheduler.runAtLocation(Residence.getInstance(), new Location(world, chunkX * 16, 0, chunkZ * 16),
                        () -> cmiChunkSnapshot.setSnapshot(chunk.getChunkSnapshot(
                                true, biomeData, false)));

                t = t.exceptionally(ex -> {
                    lm.consoleMessage("Unable to get chunk snapshot for " + world + " " + (chunkX * 16) + ":" + (chunkZ * 16));
                    ex.printStackTrace();
                    return null;
                });
                t.get();
            } catch (Throwable e) {
                e.printStackTrace();
                Thread.currentThread().interrupt();
            }
            return cmiChunkSnapshot;
        }));
    }
}
