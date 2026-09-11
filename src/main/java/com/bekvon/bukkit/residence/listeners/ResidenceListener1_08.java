package com.bekvon.bukkit.residence.listeners;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerUnleashEntityEvent;

import com.bekvon.bukkit.residence.Residence;
import com.bekvon.bukkit.residence.containers.Flags;
import com.bekvon.bukkit.residence.protection.FlagPermissions;
import com.bekvon.bukkit.residence.protection.FlagPermissions.FlagCombo;

import net.Zrips.CMILib.Version.Version;

public class ResidenceListener1_08 implements Listener {

    private Residence plugin;

    public ResidenceListener1_08(Residence plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerInteractAtArmoStand(PlayerInteractAtEntityEvent event) {

        Player player = event.getPlayer();

        if (FlagPermissions.shouldIgnoreCheck(Flags.container, player)) {
            return;
        }
        Entity ent = event.getRightClicked();

        if (!(ent instanceof ArmorStand)) {
            return;
        }
        if (FlagPermissions.shouldDenyAndNotify(player, ent, Flags.container, Flags.use)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void AnimalUnleash(PlayerUnleashEntityEvent event) {

        Entity entity = event.getEntity();

        if (FlagPermissions.shouldIgnoreCheck(Flags.leash, entity)) {
            return;
        }
        Player player = event.getPlayer();
        if (FlagPermissions.shouldDenyAndNotify(player, entity, Flags.leash, null)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockExplodeEvent(BlockExplodeEvent event) {
        // ExplosionResult.TRIGGER_BLOCK does not destroy blocks
        // it is triggered by (Enchantment: Wind Burst)
        if (Version.isCurrentEqualOrHigher(Version.v1_21_0)
                && event.getExplosionResult() == org.bukkit.ExplosionResult.TRIGGER_BLOCK) {
            ResidenceListener1_21.onWindExplode(event);
            return;
        }
        Block sourceBlock = event.getBlock();
        // disabling event on world
        if (plugin.isDisabledWorldListener(sourceBlock)) {
            return;
        }
        if (Flags.explode.isGlobalyEnabled()) {
            FlagPermissions sourceBlockPerms = FlagPermissions.getPerms(sourceBlock.getLocation());
            // Explosion is prohibited at the source location; cancel the event directly
            if (!sourceBlockPerms.has(Flags.explode, sourceBlockPerms.has(Flags.destroy, true))) {
                event.setCancelled(true);
                return;
            }
        }
        // Source allows explosion, so check each affected block for destruction
        List<Block> preserve = new ArrayList<Block>();
        for (Block block : event.blockList()) {
            FlagPermissions blockPerms = FlagPermissions.getPerms(block.getLocation());
            if ((Flags.explode.isGlobalyEnabled() && blockPerms.has(Flags.explode, FlagCombo.OnlyFalse)) ||
                    (Flags.destroy.isGlobalyEnabled() && blockPerms.has(Flags.destroy, FlagCombo.OnlyFalse))) {
                preserve.add(block);
            }
        }
        if (!preserve.isEmpty()) {
            event.blockList().removeAll(preserve);
        }
    }
}
