package com.bekvon.bukkit.residence.listeners;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTakeLecternBookEvent;
import org.bukkit.event.raid.RaidTriggerEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;

import com.bekvon.bukkit.residence.Residence;
import com.bekvon.bukkit.residence.containers.Flags;
import com.bekvon.bukkit.residence.containers.lm;
import com.bekvon.bukkit.residence.protection.ClaimedResidence;
import com.bekvon.bukkit.residence.protection.FlagPermissions;
import com.bekvon.bukkit.residence.protection.FlagPermissions.FlagCombo;
import com.bekvon.bukkit.residence.utils.Utils;

import net.Zrips.CMILib.Items.CMIMaterial;
import org.jetbrains.annotations.NotNull;

public class ResidenceListener1_14 implements Listener {

    private Residence plugin;

    public ResidenceListener1_14(Residence plugin) {
        this.plugin = plugin;
    }

    private static final Map<String, Tag<Material>> BLOCK_TAG_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Tag<Material>> ITEM_TAG_CACHE = new ConcurrentHashMap<>();

    // https://minecraft.wiki/w/Block_tag_(Java_Edition)
    public static boolean isBlockTag(Material block, String tagName) {
        if (block == null || tagName == null) {
            return false;
        }
        Tag<Material> tag = BLOCK_TAG_CACHE.computeIfAbsent(tagName, key -> Bukkit.getTag(Tag.REGISTRY_BLOCKS, NamespacedKey.minecraft(key), Material.class));
        return tag != null && tag.isTagged(block);
    }

    // https://minecraft.wiki/w/Item_tag_(Java_Edition)
    public static boolean isItemTag(Material item, String tagName) {
        if (item == null || tagName == null) {
            return false;
        }
        Tag<Material> tag = ITEM_TAG_CACHE.computeIfAbsent(tagName, key -> Bukkit.getTag(Tag.REGISTRY_ITEMS, NamespacedKey.minecraft(key), Material.class));
        return tag != null && tag.isTagged(item);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onLecternBookTake(PlayerTakeLecternBookEvent event) {

        if (FlagPermissions.shouldIgnoreCheck(Flags.container, event.getLectern().getWorld())) {
            return;
        }
        Player player = event.getPlayer();

        if (FlagPermissions.shouldDenyAndNotify(player, event.getLectern().getLocation(), Flags.container, null)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onVehicleDamage(VehicleDamageEvent event) {

        if (FlagPermissions.shouldIgnoreCheck(Flags.vehicledestroy, event.getVehicle())) {
            return;
        }
        Entity attacker = event.getAttacker();

        if (!(attacker instanceof Player)) {
            return;
        }
        Player player = (Player) attacker;

        if (FlagPermissions.shouldDenyAndNotify(player, event.getVehicle(), Flags.vehicledestroy, null)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onProjectileHitBell(ProjectileHitEvent event) {

        Block block = event.getHitBlock();
        if (block == null) {
            return;
        }
        if (FlagPermissions.shouldIgnoreCheck(Flags.use, block)) {
            return;
        }
        if (block.getType() != Material.BELL) {
            return;
        }
        if (shouldDenyProjectileHit(block, event.getEntity(), Flags.use)) {
            event.setCancelled(true);
        }
    }

    public static boolean shouldDenyProjectileHit(@NotNull Block block, @NotNull Projectile projectile, @NotNull Flags flag) {
        Player player = Utils.potentialProjectileToPlayer(projectile);
        if (player != null) {

            return FlagPermissions.shouldDenyAndNotify(player, block, flag, null);

        } else {
            // projectile not player source
            // Check potential block as a shooter which should be allowed if its inside same
            // residence
            if (Utils.isSourceBlockInsideSameResidence(projectile, ClaimedResidence.getByLoc(block.getLocation()))) {
                return false;
            }
            return FlagPermissions.has(block.getLocation(), flag, FlagCombo.OnlyFalse);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerInteractHarvest(PlayerInteractEvent event) {

        Block block = event.getClickedBlock();
        if (block == null)
            return;

        if (FlagPermissions.shouldIgnoreCheck(Flags.harvest, block)) {
            return;
        }

        if (event.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;

        CMIMaterial mat = CMIMaterial.get(block.getType());

        switch (mat) {
        case CAVE_VINES:
        case CAVE_VINES_PLANT:
        case SWEET_BERRY_BUSH:
            break;
        default:
            return;
        }

        Player player = event.getPlayer();

        if (FlagPermissions.shouldDenyAndNotify(player, block, Flags.harvest, null)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCoralDryFade(BlockFadeEvent event) {

        Block block = event.getBlock();

        if (FlagPermissions.shouldIgnoreCheck(Flags.coraldryup, block)) {
            return;
        }
        Material mat = block.getType();

        if (!(isBlockTag(mat, "corals") || isBlockTag(mat, "coral_blocks") || isBlockTag(mat, "wall_corals")))
            return;

        if (FlagPermissions.has(block.getLocation(), Flags.coraldryup, FlagCombo.OnlyFalse))
            event.setCancelled(true);

    }

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onVanillaRaidTrigger(RaidTriggerEvent event) {

        if (FlagPermissions.shouldIgnoreCheck(Flags.raid, event.getWorld())) {
            return;
        }
        if (FlagPermissions.has(event.getRaid().getLocation(), Flags.raid, true)) {
            return;
        }
        lm.Flag_Deny.sendMessage(event.getPlayer(), Flags.raid);
        event.setCancelled(true);

	}
}
