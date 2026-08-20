package com.bekvon.bukkit.residence.selectionVisuals;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display.Brightness;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.jetbrains.annotations.NotNull;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.bekvon.bukkit.residence.Residence;

import net.Zrips.CMILib.Version.Schedulers.CMIScheduler;
import net.Zrips.CMILib.Version.Schedulers.CMITaskResult;

/**
 * Region-thread-safe wrapper around a selection {@link BlockDisplay}.
 *
 * Entity creation is asynchronous on Folia. State requested before creation is
 * retained and applied once the entity exists. All later entity and player
 * operations are dispatched to their owning schedulers.
 */
public class CMIBlockDisplay {

    private final Object lifecycleLock = new Object();
    private final Residence plugin;
    private final TaskDispatcher dispatcher;
    private final Map<UUID, Player> viewers = new ConcurrentHashMap<>();

    private volatile BlockDisplay display;
    private volatile Location spawnLocation;
    private volatile Material material;
    private volatile VisualState visualState;
    private volatile boolean removed;

    public CMIBlockDisplay(@NotNull Location location, @NotNull Material material) {
        this(location, material, Residence.getInstance());
    }

    private CMIBlockDisplay(Location location, Material material, Residence plugin) {
        this(location, material, plugin, new CmiTaskDispatcher(plugin));
    }

    CMIBlockDisplay(Location location, Material material, Residence plugin, TaskDispatcher dispatcher) {
        if (location == null || location.getWorld() == null)
            throw new IllegalArgumentException("Location must have a world");

        if (material == null)
            throw new IllegalArgumentException("Material cannot be null");

        if (plugin == null)
            throw new IllegalArgumentException("Plugin cannot be null");

        if (dispatcher == null)
            throw new IllegalArgumentException("Task dispatcher cannot be null");

        this.plugin = plugin;
        this.dispatcher = dispatcher;
        this.spawnLocation = location.clone();
        this.material = material;

        Location creationLocation = location.clone();
        dispatcher.runAtLocation(creationLocation, () -> createDisplay(creationLocation), this::retire);
    }

    private void createDisplay(Location location) {
        if (removed)
            return;

        BlockDisplay created;
        try {
            created = location.getWorld().spawn(
                    location,
                    BlockDisplay.class,
                    entity -> {
                        entity.setBlock(material.createBlockData());
                        entity.setVisibleByDefault(false);
                        entity.setPersistent(false);
                        entity.setBrightness(new Brightness(15, 15));
                        entity.setTransformation(new Transformation(
                                new Vector3f(0, 0, 0),
                                new Quaternionf(),
                                new Vector3f(0.05f, 0.05f, 0.05f),
                                new Quaternionf()));
                    });
        } catch (Throwable throwable) {
            removed = true;
            viewers.clear();
            return;
        }

        synchronized (lifecycleLock) {
            if (removed) {
                dispatcher.runAtEntity(created, created::remove, () -> {
                });
                return;
            }
            display = created;
        }

        applyPendingState(created);
        for (Player player : viewers.values())
            showToPlayer(created, player);
    }

    public void setVisual(double locationX, double locationY, double locationZ, float scaleX, float scaleY, float scaleZ) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        visualState = new VisualState(
                locationX + random.nextFloat() / 1000F,
                locationY + random.nextFloat() / 1000F,
                locationZ + random.nextFloat() / 1000F,
                scaleX,
                scaleY,
                scaleZ);
        applyPendingState(display);
    }

    private void applyPendingState(BlockDisplay entity) {
        if (entity == null || removed)
            return;

        dispatcher.runAtEntity(entity, () -> {
            if (removed || display != entity)
                return;

            Material requestedMaterial = material;
            if (requestedMaterial != null)
                entity.setBlock(requestedMaterial.createBlockData());

            VisualState requestedVisual = visualState;
            if (requestedVisual == null)
                return;

            Location entityLocation = entity.getLocation();
            Transformation transformation = entity.getTransformation();
            entity.setTransformation(new Transformation(
                    new Vector3f(
                            (float) (requestedVisual.locationX - entityLocation.getX()),
                            (float) (requestedVisual.locationY - entityLocation.getY()),
                            (float) (requestedVisual.locationZ - entityLocation.getZ())),
                    transformation.getLeftRotation(),
                    new Vector3f(requestedVisual.scaleX, requestedVisual.scaleY, requestedVisual.scaleZ),
                    transformation.getRightRotation()));
        }, this::retire);
    }

    /**
     * May return {@code null} until the region-owned spawn task completes.
     */
    public BlockDisplay getEntity() {
        return display;
    }

    public void setBlock(Material material) {
        if (material == null)
            return;
        this.material = material;
        applyPendingState(display);
    }

    public void setLocation(Location location) {
        if (location == null || location.getWorld() == null || removed)
            return;
        spawnLocation = location.clone();
        mutateEntity(entity -> entity.teleport(location));
    }

    /**
     * Moves the display with client-side interpolation.
     *
     * @param location target location
     * @param duration number of ticks over which the movement occurs
     */
    public void move(Location location, int duration) {
        if (location == null || location.getWorld() == null || removed)
            return;
        spawnLocation = location.clone();
        mutateEntity(entity -> {
            entity.setTeleportDuration(duration);
            entity.teleport(location);
        });
    }

    public Location getLocation() {
        Location location = spawnLocation;
        return location == null ? null : location.clone();
    }

    public void setTransformation(Vector3f translation, Vector3f scale, AxisAngle4f leftRotation, AxisAngle4f rightRotation) {
        mutateEntity(entity -> entity.setTransformation(new Transformation(translation, leftRotation, scale, rightRotation)));
    }

    public void setScale(float x, float y, float z) {
        mutateEntity(entity -> {
            Transformation transformation = entity.getTransformation();
            entity.setTransformation(new Transformation(
                    transformation.getTranslation(),
                    transformation.getLeftRotation(),
                    new Vector3f(x, y, z),
                    transformation.getRightRotation()));
        });
    }

    public void setTranslation(float x, float y, float z) {
        mutateEntity(entity -> {
            Transformation transformation = entity.getTransformation();
            entity.setTransformation(new Transformation(
                    new Vector3f(x, y, z),
                    transformation.getLeftRotation(),
                    transformation.getScale(),
                    transformation.getRightRotation()));
        });
    }

    public void setRotation(float pitch, float yaw, float roll) {
        mutateEntity(entity -> {
            Transformation transformation = entity.getTransformation();
            Quaternionf rotation = new Quaternionf()
                    .rotateY((float) Math.toRadians(-yaw))
                    .rotateX((float) Math.toRadians(pitch))
                    .rotateZ((float) Math.toRadians(roll));
            entity.setTransformation(new Transformation(
                    transformation.getTranslation(),
                    rotation,
                    transformation.getScale(),
                    transformation.getRightRotation()));
        });
    }

    private void mutateEntity(EntityMutation mutation) {
        BlockDisplay entity = display;
        if (entity == null || removed)
            return;
        dispatcher.runAtEntity(entity, () -> {
            if (!removed && display == entity)
                mutation.apply(entity);
        }, this::retire);
    }

    public void show(Player player) {
        if (player == null || removed)
            return;
        viewers.put(player.getUniqueId(), player);
        BlockDisplay entity = display;
        if (entity != null)
            showToPlayer(entity, player);
    }

    private void showToPlayer(BlockDisplay entity, Player player) {
        dispatcher.runAtPlayer(player, () -> {
            if (!removed && display == entity && viewers.get(player.getUniqueId()) == player && player.isOnline())
                player.showEntity(plugin, entity);
        });
    }

    public void hide(Player player) {
        if (player == null)
            return;
        viewers.remove(player.getUniqueId());
        BlockDisplay entity = display;
        if (entity == null)
            return;
        dispatcher.runAtPlayer(player, () -> {
            if (player.isOnline())
                player.hideEntity(plugin, entity);
        });
    }

    public void remove() {
        BlockDisplay entity;
        synchronized (lifecycleLock) {
            if (removed)
                return;
            removed = true;
            viewers.clear();
            entity = display;
        }

        if (entity != null)
            dispatcher.runAtEntity(entity, entity::remove, () -> {
            });
    }

    private void retire() {
        removed = true;
        viewers.clear();
    }

    public boolean isDead() {
        return removed;
    }

    interface TaskDispatcher {
        void runAtLocation(Location location, Runnable task, Runnable rejected);

        void runAtEntity(Entity entity, Runnable task, Runnable retired);

        void runAtPlayer(Player player, Runnable task);
    }

    private static final class CmiTaskDispatcher implements TaskDispatcher {

        private final Residence plugin;

        private CmiTaskDispatcher(Residence plugin) {
            this.plugin = plugin;
        }

        @Override
        public void runAtLocation(Location location, Runnable task, Runnable rejected) {
            try {
                CMIScheduler.runAtLocation(plugin, location, task)
                    .exceptionally(throwable -> {
                        rejected.run();
                        return null;
                    });
            } catch (Throwable throwable) {
                rejected.run();
            }
        }

        @Override
        public void runAtEntity(Entity entity, Runnable task, Runnable retired) {
            try {
                CMIScheduler.runAtEntityWithFallback(plugin, entity, task, retired)
                    .thenAccept(result -> {
                        if (result != CMITaskResult.SUCCESS)
                            retired.run();
                    })
                    .exceptionally(throwable -> {
                        retired.run();
                        return null;
                    });
            } catch (Throwable throwable) {
                retired.run();
            }
        }

        @Override
        public void runAtPlayer(Player player, Runnable task) {
            runAtEntity(player, task, () -> {
            });
        }
    }

    private interface EntityMutation {
        void apply(BlockDisplay entity);
    }

    private static final class VisualState {
        private final double locationX;
        private final double locationY;
        private final double locationZ;
        private final float scaleX;
        private final float scaleY;
        private final float scaleZ;

        private VisualState(double locationX, double locationY, double locationZ, float scaleX, float scaleY, float scaleZ) {
            this.locationX = locationX;
            this.locationY = locationY;
            this.locationZ = locationZ;
            this.scaleX = scaleX;
            this.scaleY = scaleY;
            this.scaleZ = scaleZ;
        }
    }
}
