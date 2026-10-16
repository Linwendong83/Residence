package com.bekvon.bukkit.residence.protection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.SpectralArrow;
import org.bukkit.event.entity.EntityDamageEvent;
import org.junit.Before;
import org.junit.Test;

public class ArrowHitResyncTest {
    private final List<Entity> scheduledEntities = new ArrayList<>();
    private final List<Runnable> tasks = new ArrayList<>();
    private final List<Entity> resent = new ArrayList<>();
    private ArrowHitResync resync;
    private EntityDamageEvent event;
    private Arrow arrow;

    @Before
    public void setUp() {
        resync = new ArrowHitResync((entity, task) -> {
            scheduledEntities.add(entity);
            tasks.add(task);
        }, resent::add);
        event = mock(EntityDamageEvent.class);
        when(event.isCancelled()).thenReturn(true);
        arrow = mock(Arrow.class);
        when(arrow.isValid()).thenReturn(true);
    }

    @Test
    public void refreshesOnlyAfterTheArrowRegionTaskRuns() {
        resync.afterCancelledDamage(event, arrow);
        assertEquals(1, tasks.size());
        assertSame(arrow, scheduledEntities.get(0));
        assertTrue(resent.isEmpty());
        tasks.get(0).run();
        assertEquals(1, resent.size());
        assertSame(arrow, resent.get(0));
        verify(arrow, never()).remove();
    }

    @Test
    public void leavesSuccessfulHitsToTheNormalTracker() {
        when(event.isCancelled()).thenReturn(false);
        resync.afterCancelledDamage(event, arrow);
        assertTrue(tasks.isEmpty());
    }

    @Test
    public void respectsALaterPluginAllowingTheDamage() {
        resync.afterCancelledDamage(event, arrow);
        when(event.isCancelled()).thenReturn(false);
        tasks.get(0).run();
        assertTrue(resent.isEmpty());
    }

    @Test
    public void doesNotRecreateAnArrowRemovedBeforeTheTaskRuns() {
        resync.afterCancelledDamage(event, arrow);
        when(arrow.isValid()).thenReturn(false);
        tasks.get(0).run();
        assertTrue(resent.isEmpty());
    }

    @Test
    public void doesNotRecreateADeadArrow() {
        resync.afterCancelledDamage(event, arrow);
        when(arrow.isDead()).thenReturn(true);
        tasks.get(0).run();
        assertTrue(resent.isEmpty());
    }

    @Test
    public void alsoRestoresSpectralArrows() {
        SpectralArrow spectral = mock(SpectralArrow.class);
        when(spectral.getType()).thenReturn(EntityType.SPECTRAL_ARROW);
        when(spectral.isValid()).thenReturn(true);
        resync.afterCancelledDamage(event, spectral);
        tasks.get(0).run();
        assertSame(spectral, resent.get(0));
    }

    @Test
    public void ignoresMeleeExplosionsAndOtherProjectiles() {
        for (EntityType type : new EntityType[] { EntityType.PLAYER, EntityType.TNT,
                EntityType.END_CRYSTAL, EntityType.TRIDENT, EntityType.FIREWORK_ROCKET,
                EntityType.WIND_CHARGE }) {
            Entity source = mock(Entity.class);
            when(source.getType()).thenReturn(type);
            resync.afterCancelledDamage(event, source);
        }
        resync.afterCancelledDamage(event, null);
        assertTrue(tasks.isEmpty());
    }
}
