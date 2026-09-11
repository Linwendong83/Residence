package com.bekvon.bukkit.residence.listeners;

import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;

import com.bekvon.bukkit.residence.Residence;
import com.bekvon.bukkit.residence.containers.Flags;
import com.bekvon.bukkit.residence.protection.FlagPermissions;

public class ResidenceListener1_10 implements Listener {

    private Residence plugin;

    public ResidenceListener1_10(Residence plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityHotFloorDamage(EntityDamageEvent event) {

        Entity ent = event.getEntity();

        if (FlagPermissions.shouldIgnoreCheck(Flags.hotfloor, event.getEntity())) {
            return;
        }

        if (event.getCause() != DamageCause.HOT_FLOOR)
            return;

        if (!FlagPermissions.has(ent.getLocation(), Flags.hotfloor, true)) {
            event.setCancelled(true);
            return;
        }
    }
}
