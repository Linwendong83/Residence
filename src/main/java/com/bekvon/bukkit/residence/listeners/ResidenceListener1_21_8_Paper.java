package com.bekvon.bukkit.residence.listeners;

import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.minecart.ExplosiveMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;

import com.bekvon.bukkit.residence.Residence;
import com.bekvon.bukkit.residence.containers.Flags;
import com.bekvon.bukkit.residence.containers.lm;
import com.bekvon.bukkit.residence.protection.FlagPermissions;
import com.bekvon.bukkit.residence.protection.PvpProtection;
import com.bekvon.bukkit.residence.utils.Utils;

import io.papermc.paper.event.entity.EntityPushedByEntityAttackEvent;

import net.Zrips.CMILib.Version.Version;

public class ResidenceListener1_21_8_Paper implements Listener {

    private Residence plugin;

    public ResidenceListener1_21_8_Paper(Residence plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onProjectileKnockback(org.bukkit.event.entity.EntityKnockbackByEntityEvent event) {
        // Paper's later event can report only the player; this event retains the direct source.
        Entity source = event.getSourceEntity();
        if (!(event.getEntity() instanceof Player) || !(source instanceof Projectile || source instanceof TNTPrimed
                || source instanceof EnderCrystal || source instanceof ExplosiveMinecart)
                || plugin.isDisabledWorldListener(event.getEntity()))
            return;
        if (shouldCancelKnockBack(event.getEntity(), event.getSourceEntity()))
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onKnockback(EntityPushedByEntityAttackEvent event) {
        // disabling event on world
        if (plugin.isDisabledWorldListener(event.getEntity())) {
            return;
        }
        if (shouldCancelKnockBack(event.getEntity(), event.getPushedBy()))
            event.setCancelled(true);
    }

    public static boolean shouldCancelKnockBack(Entity target, Entity pushedBy) {

        Player player = Utils.potentialProjectileToPlayer(pushedBy);

        if (target instanceof ArmorStand) {
            return shouldDeny(target, player, Flags.destroy, null);
        }
        if (target instanceof Boat || target instanceof Minecart) {
            return shouldDeny(target, player, Flags.vehicledestroy, null);
        }
        if (target instanceof Player) {
            // Monster-on-player knockback doesn't need to check Flags.pvp
            // Allow players to knock themselves back (e.g., by Wind Charges)
            PvpProtection protection = new PvpProtection(Residence.getInstance());
            player = protection.getPlayer(pushedBy);
            if (protection.isDenied(pushedBy, player, (Player) target, true)) {
                if (player != null)
                    lm.Flag_Deny.sendMessage(player, Flags.pvp);
                return true;
            }
            return false;
        }
        if (Utils.isAnimal(target)) {
            // SulfurCube containing blocks doesn't take damage
            // preferentially uses Flags.push instead on Paper 26.2+
            if (Version.isCurrentEqualOrHigher(Version.v26_2_0) && Version.isPaperBranch()
                    && target instanceof org.bukkit.entity.SulfurCube) {

                EntityEquipment equipment = ((org.bukkit.entity.SulfurCube) target).getEquipment();
                // Check if SulfurCube has a block inside
                if (equipment != null && !equipment.getItem(EquipmentSlot.BODY).isEmpty()) {
                    return shouldDeny(target, player, Flags.push, Flags.animalkilling);
                }
                // SulfurCube without blocks still checks Flags.animalkilling
            }
            return shouldDeny(target, player, Flags.animalkilling, null);
        }
        if (ResidenceEntityListener.isMonster(target)) {
            return shouldDeny(target, player, Flags.mobkilling, null);
        }
        return false;
    }

    private static boolean shouldDeny(Entity target, Player pushedBy, Flags mainFlag, Flags subFlag) {
        if (!mainFlag.isGlobalyEnabled()) {
            return false;
        }
        if (pushedBy != null) {

            return FlagPermissions.shouldDenyAndNotify(pushedBy, target, mainFlag, subFlag);

        } else {
            FlagPermissions perms = FlagPermissions.getPerms(target.getLocation());
            boolean result = (subFlag == null || perms.has(subFlag, true));

            return !perms.has(mainFlag, result);
        }
    }
}
