package com.bekvon.bukkit.residence.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerSignOpenEvent;
import org.bukkit.event.block.TNTPrimeEvent;
import org.bukkit.entity.Entity;

import com.bekvon.bukkit.residence.Residence;
import com.bekvon.bukkit.residence.containers.Flags;
import com.bekvon.bukkit.residence.protection.FlagPermissions;
import com.bekvon.bukkit.residence.protection.PvpProtection;

public class ResidenceListener1_20 implements Listener {

    private Residence plugin;

    public ResidenceListener1_20(Residence plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTntPvpPrime(TNTPrimeEvent event) {
        PvpProtection protection = new PvpProtection(plugin);
        protection.clearLaunch(event.getBlock());
        if (!event.isCancelled()) {
            Entity cause = event.getPrimingEntity();
            protection.recordTrigger(event.getBlock(), cause, protection.getPlayer(cause));
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSignInteract(PlayerSignOpenEvent event) {

        Player player = event.getPlayer();

        if (FlagPermissions.shouldIgnoreCheck(Flags.build, player)) {
            return;
        }
        if (FlagPermissions.shouldDenyAndNotify(player, event.getSign().getLocation(), Flags.build, Flags.use)) {
            event.setCancelled(true);
        }
    }
}
