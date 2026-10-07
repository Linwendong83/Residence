package com.bekvon.bukkit.residence.protection;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;

import org.bukkit.entity.Entity;

import com.bekvon.bukkit.residence.Residence;

/** Uses the existing Paper/Moonrise tracker without changing visibility or physics. */
final class PaperArrowResender implements Consumer<Entity> {

    private final Residence plugin;
    private final AtomicBoolean failed = new AtomicBoolean();
    private volatile Bridge bridge;

    PaperArrowResender(Residence plugin) {
        this.plugin = plugin;
    }

    @Override
    public void accept(Entity arrow) {
        if (failed.get())
            return;
        try {
            getBridge().resend(arrow);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            if (failed.compareAndSet(false, true))
                plugin.getLogger().log(Level.WARNING,
                        "Cannot resynchronize protected arrow hits on this server; PvP protection remains active.",
                        exception);
        }
    }

    private synchronized Bridge getBridge() throws ReflectiveOperationException {
        if (bridge == null)
            bridge = new Bridge();
        return bridge;
    }

    // Resolve the optional Mojang-mapped Paper APIs lazily: base protection must still
    // load on older Bukkit/Spigot servers. No NMS classes enter the plugin's signatures.
    private static final class Bridge {
        private final Method getHandle;
        private final Method getTracker;
        private final Field serverEntity;
        private final Field seenBy;
        private final Method getPlayer;
        private final Method sendPairingData;
        private final Method send;
        private final Constructor<?> removePacket;
        private final Constructor<?> bundlePacket;

        Bridge() throws ReflectiveOperationException {
            Class<?> entity = Class.forName("net.minecraft.world.entity.Entity");
            Class<?> tracker = Class.forName("net.minecraft.server.level.ChunkMap$TrackedEntity");
            Class<?> connection = Class.forName("net.minecraft.server.network.ServerPlayerConnection");
            Class<?> player = Class.forName("net.minecraft.server.level.ServerPlayer");
            Class<?> packet = Class.forName("net.minecraft.network.protocol.Packet");
            getHandle = Class.forName("org.bukkit.craftbukkit.entity.CraftEntity").getMethod("getHandle");
            getTracker = entity.getMethod("moonrise$getTrackedEntity");
            serverEntity = tracker.getField("serverEntity");
            seenBy = tracker.getField("seenBy");
            getPlayer = connection.getMethod("getPlayer");
            sendPairingData = Class.forName("net.minecraft.server.level.ServerEntity")
                    .getMethod("sendPairingData", player, Consumer.class);
            send = connection.getMethod("send", packet);
            removePacket = Class.forName("net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket")
                    .getConstructor(int[].class);
            bundlePacket = Class.forName("net.minecraft.network.protocol.game.ClientboundBundlePacket")
                    .getConstructor(Iterable.class);
        }

        void resend(Entity arrow) throws ReflectiveOperationException {
            Object tracker = getTracker.invoke(getHandle.invoke(arrow));
            if (tracker == null)
                return;
            Object trackedEntity = serverEntity.get(tracker);
            Set<?> viewers = (Set<?>) seenBy.get(tracker);
            Object remove = removePacket.newInstance((Object) new int[] { arrow.getEntityId() });
            for (Object viewer : viewers.toArray()) {
                List<Object> packets = new ArrayList<>();
                packets.add(remove);
                // The normal pairing path preserves owner, velocity, item and metadata.
                // Build everything before sending: never remove an entity without its spawn.
                sendPairingData.invoke(trackedEntity, getPlayer.invoke(viewer), (Consumer<Object>) packets::add);
                if (packets.size() > 1 && viewers.contains(viewer))
                    send.invoke(viewer, bundlePacket.newInstance(packets));
            }
            // Only current tracker viewers receive this bundle. Do not hide/show the arrow:
            // that mutates player visibility maps and can undo another plugin's tracking.
        }
    }
}
