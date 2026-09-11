package com.bekvon.bukkit.residence.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerSignOpenEvent;

import com.bekvon.bukkit.residence.Residence;
import com.bekvon.bukkit.residence.containers.Flags;
import com.bekvon.bukkit.residence.protection.FlagPermissions;

public class ResidenceListener1_20 implements Listener {

    private Residence plugin;

    public ResidenceListener1_20(Residence plugin) {
        this.plugin = plugin;
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
