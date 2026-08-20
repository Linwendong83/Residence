package com.bekvon.bukkit.residence.selectionVisuals;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import com.bekvon.bukkit.residence.Residence;

public class CMIBlockDisplayTest {

    private static Field serverField;
    private static Object previousServer;
    private static BlockData blueData;
    private static BlockData greenData;

    private Residence plugin;
    private World world;
    private BlockDisplay entity;
    private Location origin;
    private FakeDispatcher dispatcher;

    @BeforeClass
    public static void installBukkitServer() throws Exception {
        serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        previousServer = serverField.get(null);

        Server server = mock(Server.class);
        blueData = mock(BlockData.class);
        greenData = mock(BlockData.class);
        when(server.createBlockData(Material.BLUE_WOOL)).thenReturn(blueData);
        when(server.createBlockData(Material.GREEN_WOOL)).thenReturn(greenData);
        serverField.set(null, server);
    }

    @AfterClass
    public static void restoreBukkitServer() throws Exception {
        serverField.set(null, previousServer);
    }

    @Before
    public void setUp() {
        plugin = mock(Residence.class);
        world = mock(World.class);
        entity = mock(BlockDisplay.class);
        origin = new Location(world, 10.0, 20.0, 30.0);
        dispatcher = new FakeDispatcher();

        Transformation initial = new Transformation(
                new Vector3f(), new Quaternionf(), new Vector3f(0.05f), new Quaternionf());
        when(entity.getLocation()).thenReturn(origin.clone());
        when(entity.getTransformation()).thenReturn(initial);
        when(world.spawn(any(Location.class), eq(BlockDisplay.class), anyConsumer())).thenAnswer(invocation -> {
            Consumer<BlockDisplay> initializer = invocation.getArgument(2);
            initializer.accept(entity);
            return entity;
        });
    }

    @SuppressWarnings("unchecked")
    private static Consumer<BlockDisplay> anyConsumer() {
        return any(Consumer.class);
    }

    @Test
    public void createsAtLocationAndAppliesPendingVisualAndViewer() {
        Player player = mock(Player.class);
        UUID uuid = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.isOnline()).thenReturn(true);

        CMIBlockDisplay display = new CMIBlockDisplay(origin, Material.BLUE_WOOL, plugin, dispatcher);
        display.setVisual(12.0, 23.0, 34.0, 2.0f, 3.0f, 4.0f);
        display.show(player);

        assertEquals(1, dispatcher.locationTasks.size());
        assertTrue(dispatcher.entityTasks.isEmpty());
        assertTrue(dispatcher.playerTasks.isEmpty());
        verify(world, never()).spawn(any(Location.class), eq(BlockDisplay.class), anyConsumer());

        dispatcher.runNextLocation();
        assertSame(entity, display.getEntity());
        assertEquals(1, dispatcher.entityTasks.size());
        assertEquals(1, dispatcher.playerTasks.size());
        assertSame(entity, dispatcher.entityTasks.peek().entity);
        assertSame(player, dispatcher.playerTasks.peek().player);

        dispatcher.runNextEntity();
        dispatcher.runNextPlayer();

        verify(entity, atLeastOnce()).setBlock(blueData);
        verify(player).showEntity(plugin, entity);

        ArgumentCaptor<Transformation> transformations = ArgumentCaptor.forClass(Transformation.class);
        verify(entity, atLeastOnce()).setTransformation(transformations.capture());
        Transformation applied = transformations.getAllValues().get(transformations.getAllValues().size() - 1);
        assertEquals(2.0f, applied.getScale().x, 0.0001f);
        assertEquals(3.0f, applied.getScale().y, 0.0001f);
        assertEquals(4.0f, applied.getScale().z, 0.0001f);
        assertTrue(applied.getTranslation().x >= 2.0f && applied.getTranslation().x < 2.0011f);
        assertTrue(applied.getTranslation().y >= 3.0f && applied.getTranslation().y < 3.0011f);
        assertTrue(applied.getTranslation().z >= 4.0f && applied.getTranslation().z < 4.0011f);
    }

    @Test
    public void updatesAndRemovesOnOwningSchedulers() {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(true);

        CMIBlockDisplay display = new CMIBlockDisplay(origin, Material.BLUE_WOOL, plugin, dispatcher);
        dispatcher.runNextLocation();
        dispatcher.runNextEntity();

        display.setBlock(Material.GREEN_WOOL);
        assertEquals(1, dispatcher.entityTasks.size());
        dispatcher.runNextEntity();
        verify(entity).setBlock(greenData);

        display.show(player);
        assertEquals(1, dispatcher.playerTasks.size());
        dispatcher.runNextPlayer();
        verify(player).showEntity(plugin, entity);

        display.hide(player);
        assertEquals(1, dispatcher.playerTasks.size());
        dispatcher.runNextPlayer();
        verify(player).hideEntity(plugin, entity);

        display.remove();
        assertTrue(display.isDead());
        assertEquals(1, dispatcher.entityTasks.size());
        dispatcher.runNextEntity();
        verify(entity).remove();

        display.remove();
        assertTrue(dispatcher.entityTasks.isEmpty());
    }

    @Test
    public void removingBeforeCreationNeverSpawnsAnEntity() {
        CMIBlockDisplay display = new CMIBlockDisplay(origin, Material.BLUE_WOOL, plugin, dispatcher);
        display.remove();
        display.remove();
        dispatcher.runNextLocation();

        assertTrue(display.isDead());
        verify(world, never()).spawn(any(Location.class), eq(BlockDisplay.class), anyConsumer());
        assertTrue(dispatcher.entityTasks.isEmpty());
    }

    @Test
    public void offlineViewerIsIgnoredWithoutTouchingPlayerState() {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(false);

        CMIBlockDisplay display = new CMIBlockDisplay(origin, Material.BLUE_WOOL, plugin, dispatcher);
        display.show(player);
        dispatcher.runNextLocation();
        dispatcher.runNextEntity();
        dispatcher.runNextPlayer();

        verify(player, never()).showEntity(plugin, entity);
        display.remove();
        dispatcher.runNextEntity();
    }

    @Test
    public void rejectedAndRetiredSchedulersEndLifecycleSafely() {
        CMIBlockDisplay rejected = new CMIBlockDisplay(origin, Material.BLUE_WOOL, plugin, dispatcher);
        dispatcher.rejectNextLocation();
        assertTrue(rejected.isDead());

        CMIBlockDisplay retired = new CMIBlockDisplay(origin, Material.BLUE_WOOL, plugin, dispatcher);
        dispatcher.runNextLocation();
        assertFalse(retired.isDead());
        dispatcher.retireNextEntity();
        assertTrue(retired.isDead());
    }

    private static final class FakeDispatcher implements CMIBlockDisplay.TaskDispatcher {

        private final Deque<LocationTask> locationTasks = new ArrayDeque<LocationTask>();
        private final Deque<EntityTask> entityTasks = new ArrayDeque<EntityTask>();
        private final Deque<PlayerTask> playerTasks = new ArrayDeque<PlayerTask>();

        @Override
        public void runAtLocation(Location location, Runnable task, Runnable rejected) {
            locationTasks.add(new LocationTask(location, task, rejected));
        }

        @Override
        public void runAtEntity(Entity entity, Runnable task, Runnable retired) {
            entityTasks.add(new EntityTask(entity, task, retired));
        }

        @Override
        public void runAtPlayer(Player player, Runnable task) {
            playerTasks.add(new PlayerTask(player, task));
        }

        private void runNextLocation() {
            locationTasks.remove().task.run();
        }

        private void rejectNextLocation() {
            locationTasks.remove().rejected.run();
        }

        private void runNextEntity() {
            entityTasks.remove().task.run();
        }

        private void retireNextEntity() {
            entityTasks.remove().retired.run();
        }

        private void runNextPlayer() {
            playerTasks.remove().task.run();
        }
    }

    private static final class LocationTask {
        private final Location location;
        private final Runnable task;
        private final Runnable rejected;

        private LocationTask(Location location, Runnable task, Runnable rejected) {
            this.location = location;
            this.task = task;
            this.rejected = rejected;
        }
    }

    private static final class EntityTask {
        private final Entity entity;
        private final Runnable task;
        private final Runnable retired;

        private EntityTask(Entity entity, Runnable task, Runnable retired) {
            this.entity = entity;
            this.task = task;
            this.retired = retired;
        }
    }

    private static final class PlayerTask {
        private final Player player;
        private final Runnable task;

        private PlayerTask(Player player, Runnable task) {
            this.player = player;
            this.task = task;
        }
    }
}
