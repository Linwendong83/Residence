package com.bekvon.bukkit.residence.protection;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.event.entity.EntityDamageEvent;

import com.bekvon.bukkit.residence.Residence;

import net.Zrips.CMILib.Version.Schedulers.CMIScheduler;

/** Repairs client-side arrow removal after Residence rejects a player hit. */
public final class ArrowHitResync {

    private final BiConsumer<Entity, Runnable> scheduler;
    private final Consumer<Entity> resender;

    public ArrowHitResync(Residence plugin) {
        this((arrow, task) -> CMIScheduler.runAtEntityLater(plugin, arrow, task, 2L),
                new PaperArrowResender(plugin));
    }

    ArrowHitResync(BiConsumer<Entity, Runnable> scheduler, Consumer<Entity> resender) {
        this.scheduler = scheduler;
        this.resender = resender;
    }

    public void afterCancelledDamage(EntityDamageEvent event, Entity source) {
        if (!event.isCancelled() || source == null
                || (!(source instanceof Arrow) && !"SPECTRAL_ARROW".equals(source.getType().name())))
            return;

        // RemotePlayer predicts a successful hit and discards ordinary arrows locally.
        // Wait for the server's rejected-hit bounce and tracker update before recreating
        // that client entity. Follow the arrow's region if it moves on Folia.
        scheduler.accept(source, () -> {
            // A later listener may allow the hit, or the arrow may already be gone.
            if (event.isCancelled() && source.isValid() && !source.isDead())
                resender.accept(source);
        });
    }
}
