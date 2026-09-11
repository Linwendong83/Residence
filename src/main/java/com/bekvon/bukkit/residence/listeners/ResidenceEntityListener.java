package com.bekvon.bukkit.residence.listeners;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.entity.Vehicle;
import org.bukkit.entity.Witch;
import org.bukkit.entity.Wither;
import org.bukkit.entity.WitherSkull;
import org.bukkit.entity.minecart.ExplosiveMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityBreakDoorEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingBreakEvent.RemoveCause;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.projectiles.ProjectileSource;

import com.bekvon.bukkit.residence.ConfigManager;
import com.bekvon.bukkit.residence.Residence;
import com.bekvon.bukkit.residence.containers.Flags;
import com.bekvon.bukkit.residence.containers.ResAdmin;
import com.bekvon.bukkit.residence.containers.lm;
import com.bekvon.bukkit.residence.permissions.PermissionManager.ResPerm;
import com.bekvon.bukkit.residence.protection.ClaimedResidence;
import com.bekvon.bukkit.residence.protection.FlagPermissions;
import com.bekvon.bukkit.residence.protection.FlagPermissions.FlagCombo;
import com.bekvon.bukkit.residence.utils.Utils;

import net.Zrips.CMILib.ActionBar.CMIActionBar;
import net.Zrips.CMILib.Entities.CMIEntityType;
import net.Zrips.CMILib.Items.CMIItemStack;
import net.Zrips.CMILib.Items.CMIMC;
import net.Zrips.CMILib.Items.CMIMaterial;
import net.Zrips.CMILib.Version.Version;

public class ResidenceEntityListener implements Listener {

    Residence plugin;

    public ResidenceEntityListener(Residence plugin) {
        this.plugin = plugin;
    }

    private final static String CrossbowShooter = "CrossbowShooter";

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEndermanTeleport(EntityTeleportEvent event) {
        // disabling event on world
        if (plugin.isDisabledWorldListener(event.getTo()))
            return;

        if (event.getEntityType() != EntityType.ENDERMAN)
            return;

        FlagPermissions perms = FlagPermissions.getPerms(event.getTo());
        if (perms.has(Flags.monsters, FlagCombo.OnlyFalse) || perms.has(Flags.nomobs, FlagCombo.OnlyTrue))
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        Block block = event.getBlock();
        // disabling event on world
        if (plugin.isDisabledWorldListener(block)) {
            return;
        }
        Entity entity = event.getEntity();
        boolean shouldDeny = false;

        if (Flags.animalgriefing.isGlobalyEnabled() && Utils.isAnimal(entity)) {
            // Animals are friendly (villagers farming/sheep grazing)
            // When Flags.animalgriefing is None, do not fall back to Flags.destroy
            shouldDeny = FlagPermissions.has(block.getLocation(), Flags.animalgriefing, FlagCombo.OnlyFalse);

        } else if (Flags.witherdestruction.isGlobalyEnabled() && entity instanceof Wither) {
            FlagPermissions perms = FlagPermissions.getPerms(block.getLocation());
            shouldDeny = !perms.has(Flags.witherdestruction, perms.has(Flags.destroy, true));

        } else if (Flags.mobgriefing.isGlobalyEnabled() && isMonster(entity)) {
            FlagPermissions perms = FlagPermissions.getPerms(block.getLocation());
            shouldDeny = !perms.has(Flags.mobgriefing, perms.has(Flags.destroy, true));

        } else if (entity instanceof Player) {
            shouldDeny = shouldDenyPlayerChangeBlock(block, (Player) entity);

        } else if (Flags.destroy.isGlobalyEnabled() && entity instanceof Boat) {
            shouldDeny = shouldDenyBoatBreakLilyPad((Boat) entity, block);

        } else if (Flags.destroy.isGlobalyEnabled() && entity instanceof Projectile) {
            // Projectile-triggered EntityChangeBlockEvent always breaks blocks
            shouldDeny = ResidenceListener1_14.shouldDenyProjectileHit(block, (Projectile) entity, Flags.destroy);

        }

        if (shouldDeny) {
            event.setCancelled(true);
        }
    }

    private boolean shouldDenyPlayerChangeBlock(Block block, Player player) {
        CMIMaterial mat = CMIMaterial.get(block.getType());
        Flags mainFlag;
        Flags subFlag = Flags.build;
        if (Flags.copper.isGlobalyEnabled() && mat.containsCriteria(CMIMC.COPPER)) {
            mainFlag = Flags.copper;

        } else if (Flags.brush.isGlobalyEnabled() && (mat == CMIMaterial.SUSPICIOUS_GRAVEL || mat == CMIMaterial.SUSPICIOUS_SAND)) {
            mainFlag = Flags.brush;

        } else if (Flags.build.isGlobalyEnabled()) {
            // by default, future player-triggered EntityChangeBlockEvent mechanisms check Flags.build
            mainFlag = Flags.build;
            subFlag = null;

        } else {
            return false;
        }
        return FlagPermissions.shouldDenyAndNotify(player, block, mainFlag, subFlag);
    }

    private boolean shouldDenyBoatBreakLilyPad(Boat boat, Block block) {
        if (CMIMaterial.get(block.getType()) != CMIMaterial.LILY_PAD) {
            return false;
        }
        Entity rider = null;
        if (Version.isCurrentLower(Version.v1_11_2)) {
            rider = boat.getPassenger();
        } else {
            List<Entity> passengers = boat.getPassengers();
            if (!passengers.isEmpty()) {
                // first passenger
                rider = passengers.get(0);
            }
        }
        Player riderPlayer = rider instanceof Player ? (Player) rider : null;
        if (riderPlayer != null) {
            if (riderPlayer.hasMetadata("NPC") || ResAdmin.isResAdmin(riderPlayer)) {
                return false;
            }
            return FlagPermissions.has(block.getLocation(), riderPlayer, Flags.destroy, FlagCombo.OnlyFalse);
        } else {
            return FlagPermissions.has(block.getLocation(), Flags.destroy, FlagCombo.OnlyFalse);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntitySpawnEvent(EntitySpawnEvent event) {

        // Disabling listener if flag disabled globally
        if (!Flags.nomobs.isGlobalyEnabled())
            return;

        Entity entity = event.getEntity();
        if (entity == null)
            return;

        if (!(entity instanceof LivingEntity))
            return;

        if (!isMonster(entity))
            return;

        // disabling event on world
        if (plugin.isDisabledWorldListener(entity))
            return;

        FlagPermissions perms = FlagPermissions.getPerms(entity.getLocation());

        if (perms.has(Flags.nomobs, FlagCombo.OnlyTrue)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityInteractEvent(EntityInteractEvent event) {

        Entity entity = event.getEntity();
        // disabling event on world
        if (plugin.isDisabledWorldListener(entity)) {
            return;
        }
        Block block = event.getBlock();
        CMIMaterial mat = CMIMaterial.get(block.getType());
        Flags flag = null;
        // Start getting flags
        switch (mat) {
        case FARMLAND:
            flag = Flags.trample;
            break;

        case TURTLE_EGG:
            if (Utils.isAnimal(entity)) {
                flag = Flags.animalgriefing;
            } else if (isMonster(entity)) {
                flag = Flags.mobgriefing;
            } else {
                // Other entities
                flag = Flags.destroy;
            }
            break;

        default:
            if (mat.containsCriteria(CMIMC.BUTTON)) {
                if (entity instanceof Projectile) {
                    flag = Flags.button;
                }
            } else if (mat.containsCriteria(CMIMC.PRESSUREPLATE)) {
                if (entity instanceof Projectile || entity instanceof Item) {
                    flag = Flags.pressure;
                }
            }
            break;
        }
        if (flag == null || !flag.isGlobalyEnabled()) {
            return;
        }
        FlagPermissions perms;
        // Start checking flags
        switch (flag) {
        // Farmland: Mob StepOn
        case trample:
            perms = FlagPermissions.getPerms(block.getLocation());
            if (perms.has(flag, perms.has(Flags.build, true))) {
                return;
            }
            break;

        // Turtle Egg: Mob StepOn
        case animalgriefing:
        case mobgriefing:
            perms = FlagPermissions.getPerms(block.getLocation());
            if (perms.has(flag, perms.has(Flags.destroy, true))) {
                return;
            }
            break;

        // Turtle Egg: Other-entities StepOn
        case destroy:
            perms = FlagPermissions.getPerms(block.getLocation());
            if (perms.has(flag, true)) {
                return;
            }
            break;

        // Projectile hits button
        case button:
        // Projectile and Item press pressure_plate
        case pressure:
            Player player = Utils.potentialProjectileToPlayer(entity);
            if (player != null) {
                if (ResAdmin.isResAdmin(player)) {
                    return;
                }
                perms = FlagPermissions.getPerms(block.getLocation(), player);
                if (perms.playerHas(player, flag, perms.playerHas(player, Flags.use, true))) {
                    return;
                }
            } else {
                // Check potential block as a shooter which should be allowed if its inside same
                // residence
                if (Utils.isSourceBlockInsideSameResidence(entity, ClaimedResidence.getByLoc(block.getLocation()))) {
                    return;
                }
                perms = FlagPermissions.getPerms(block.getLocation());
                if (perms.has(flag, perms.has(Flags.use, true))) {
                    return;
                }
            }
            break;

        default:
            return;
        }

        event.setCancelled(true);

    }

    public static boolean isMonster(Entity entity) {
        if (entity == null) {
            return false;
        }
        if (Version.isCurrentEqualOrHigher(Version.v1_19_R2)) {
            return entity instanceof org.bukkit.entity.Enemy;
        }
        if (entity instanceof Monster) {
            return true;
        }
        CMIEntityType type = CMIEntityType.get(entity);
        if (type != null) {
            switch (type) {
            case ENDER_DRAGON:
            case GHAST:
            case HOGLIN:
            case PHANTOM:
            case SHULKER:
            case SLIME:
                return true;
            default:
                return false;
            }
        }
        return false;
    }

    private static boolean damageableProjectile(Entity ent) {
        if (ent instanceof Projectile && CMIEntityType.get(ent) == CMIEntityType.SPLASH_POTION) {

            if (((ThrownPotion) ent).getEffects().isEmpty())
                return true;
            for (PotionEffect one : ((ThrownPotion) ent).getEffects()) {
                for (String oneHarm : Residence.getInstance().getConfigManager().getNegativePotionEffects()) {
                    if (oneHarm.equalsIgnoreCase(one.getType().getName()))
                        return true;
                }
            }
        }
        return ent instanceof Projectile;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void AnimalKilling(EntityDamageEvent event) {

        Entity entity = event.getEntity();
        if (entity == null)
            return;

        if (FlagPermissions.shouldIgnoreCheck(Flags.animalkilling, entity)) {
            return;
        }
        if (!Utils.isAnimal(entity))
            return;

        if (event.getCause() == DamageCause.LIGHTNING || event.getCause() == DamageCause.FIRE_TICK) {
            ClaimedResidence res = plugin.getResidenceManager().getByLoc(entity.getLocation());
            if (res != null && res.getPermissions().has(Flags.animalkilling, FlagCombo.OnlyFalse)) {
                event.setCancelled(true);
            }
            return;
        }

        if (!(event instanceof EntityDamageByEntityEvent))
            return;

        EntityDamageByEntityEvent attackevent = (EntityDamageByEntityEvent) event;
        Entity damager = attackevent.getDamager();

        boolean damageable = damageableProjectile(damager);

        if (!damageable && !(damager instanceof Player))
            return;

        if (damageable && !(((Projectile) damager).getShooter() instanceof Player))
            return;

        Player cause = Utils.potentialProjectileToPlayer(damager);

        if (cause == null)
            return;

        if (FlagPermissions.shouldDenyAndNotify(cause, entity, Flags.animalkilling, null)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void AnimalKillingByFlame(EntityCombustByEntityEvent event) {

        if (event.isCancelled())
            return;

        Entity entity = event.getEntity();
        if (entity == null)
            return;

        if (FlagPermissions.shouldIgnoreCheck(Flags.animalkilling, entity)) {
            return;
        }
        if (!Utils.isAnimal(entity))
            return;

        ClaimedResidence res = plugin.getResidenceManager().getByLoc(entity.getLocation());

        if (res == null)
            return;

        Entity damager = event.getCombuster();

        if (!damageableProjectile(damager) && !(damager instanceof Player))
            return;

        if (damageableProjectile(damager) && !(((Projectile) damager).getShooter() instanceof Player))
            return;

        Player cause = Utils.potentialProjectileToPlayer(damager);

        if (cause == null)
            return;

        if (ResAdmin.isResAdmin(cause))
            return;

        if (res.getPermissions().playerHas(cause, Flags.animalkilling, FlagCombo.OnlyFalse)) {
            lm.Residence_FlagDeny.sendMessage(cause, Flags.animalkilling, res.getName());
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void AnimalDamageByMobs(EntityDamageByEntityEvent event) {

        if (event.isCancelled())
            return;

        Entity entity = event.getEntity();
        if (entity == null)
            return;

        if (FlagPermissions.shouldIgnoreCheck(Flags.animalkilling, entity)) {
            return;
        }
        if (!Utils.isAnimal(entity))
            return;

        Entity damager = event.getDamager();

        if (damager instanceof Projectile && ((Projectile) damager).getShooter() instanceof Player || damager instanceof Player)
            return;

        FlagPermissions perms = FlagPermissions.getPerms(entity.getLocation());
        if (!perms.has(Flags.animalkilling, true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void OnEntityDeath(EntityDeathEvent event) {
        // Disabling listener if flag disabled globally
        if (!Flags.mobitemdrop.isGlobalyEnabled() && !Flags.mobexpdrop.isGlobalyEnabled())
            return;
        // disabling event on world
        LivingEntity ent = event.getEntity();
        if (ent == null)
            return;
        if (plugin.isDisabledWorldListener(ent))
            return;
        if (ent instanceof Player)
            return;
        Location loc = ent.getLocation();
        FlagPermissions perms = FlagPermissions.getPerms(loc);
        if (!perms.has(Flags.mobitemdrop, true)) {
            event.getDrops().clear();
        }
        if (!perms.has(Flags.mobexpdrop, true)) {
            event.setDroppedExp(0);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void VehicleDestroy(VehicleDestroyEvent event) {

        Entity damager = event.getAttacker();
        if (damager == null)
            return;

        if (FlagPermissions.shouldIgnoreCheck(Flags.vehicledestroy, damager)) {
            return;
        }
        Vehicle vehicle = event.getVehicle();

        if (shouldBlockVehicleDestroy(damager, vehicle))
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void vehicleCombust(EntityCombustByEntityEvent event) {

        Entity damager = event.getCombuster();
        if (damager == null)
            return;

        if (FlagPermissions.shouldIgnoreCheck(Flags.vehicledestroy, damager)) {
            return;
        }
        if (event.getEntity() instanceof LivingEntity) {
            return;
        }
        if (!(event.getEntity() instanceof Vehicle))
            return;

        Vehicle vehicle = (Vehicle) event.getEntity();

        if (shouldBlockVehicleDestroy(damager, vehicle))
            event.setCancelled(true);
    }

    private boolean shouldBlockVehicleDestroy(Entity damager, Vehicle vehicle) {

        if (vehicle == null)
            return false;

        Player cause = Utils.potentialProjectileToPlayer(damager);

        if (cause != null) {
            return FlagPermissions.shouldDenyAndNotify(cause, vehicle, Flags.vehicledestroy, null);

        } else {
            return FlagPermissions.has(vehicle.getLocation(), Flags.vehicledestroy, FlagCombo.OnlyFalse);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void MonsterKilling(EntityDamageByEntityEvent event) {

        Entity entity = event.getEntity();
        if (entity == null)
            return;

        if (FlagPermissions.shouldIgnoreCheck(Flags.mobkilling, entity)) {
            return;
        }
        if (!isMonster(entity))
            return;

        Entity damager = event.getDamager();

        if (!damageableProjectile(damager) && !(damager instanceof Player))
            return;

        if (damageableProjectile(damager) && !(((Projectile) damager).getShooter() instanceof Player))
            return;

        Player cause = Utils.potentialProjectileToPlayer(damager);

        if (cause == null)
            return;

        if (FlagPermissions.shouldDenyAndNotify(cause, entity, Flags.mobkilling, null)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void PlayerLeashEntityEvent(PlayerLeashEntityEvent event) {

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
    public void onPhantomOrWitherSpawn(CreatureSpawnEvent event) {

        Entity entity = event.getEntity();
        // disabling event on world
        if (plugin.isDisabledWorldListener(entity)) {
            return;
        }
        Flags flag;
        if (Flags.witherspawn.isGlobalyEnabled() && entity instanceof Wither) {
            flag = Flags.witherspawn;

        } else if (Flags.phantomspawn.isGlobalyEnabled() && Utils.isPhantom(entity)) {
            flag = Flags.phantomspawn;

        } else {
            return;
        }
        FlagPermissions perms = FlagPermissions.getPerms(event.getLocation());
        if (perms.has(flag, FlagCombo.OnlyFalse)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        // disabling event on world
        Entity ent = event.getEntity();
        if (ent == null)
            return;
        if (plugin.isDisabledWorldListener(ent))
            return;
        FlagPermissions perms = FlagPermissions.getPerms(event.getLocation());
        if (Utils.isAnimal(ent)) {
            if (!perms.has(Flags.animals, true)) {
                event.setCancelled(true);
                return;
            }
            switch (event.getSpawnReason()) {
            case BUILD_WITHER:
                break;
            case BUILD_IRONGOLEM:
            case BUILD_SNOWMAN:
            case CUSTOM:
            case DEFAULT:
                if (perms.has(Flags.canimals, FlagCombo.OnlyFalse)) {
                    event.setCancelled(true);
                    return;
                }
                break;
            case BREEDING:
            case CHUNK_GEN:
            case CURED:
            case DISPENSE_EGG:
            case EGG:
            case JOCKEY:
            case MOUNT:
            case VILLAGE_INVASION:
            case VILLAGE_DEFENSE:
            case NETHER_PORTAL:
            case OCELOT_BABY:
            case NATURAL:
                if (perms.has(Flags.nanimals, FlagCombo.OnlyFalse)) {
                    event.setCancelled(true);
                    return;
                }
                break;
            case SPAWNER_EGG:
            case SPAWNER:
                if (perms.has(Flags.sanimals, FlagCombo.OnlyFalse)) {
                    event.setCancelled(true);
                    return;
                }
                break;
            default:
                break;
            }
        } else if (isMonster(ent)) {
            if (perms.has(Flags.monsters, FlagCombo.OnlyFalse)) {
                event.setCancelled(true);
                return;
            }
            switch (event.getSpawnReason()) {
            case BUILD_WITHER:
            case CUSTOM:
            case DEFAULT:
                if (perms.has(Flags.cmonsters, FlagCombo.OnlyFalse)) {
                    event.setCancelled(true);
                    return;
                }
                break;
            case CHUNK_GEN:
            case CURED:
            case DISPENSE_EGG:
            case INFECTION:
            case JOCKEY:
            case MOUNT:
            case NETHER_PORTAL:
            case SILVERFISH_BLOCK:
            case SLIME_SPLIT:
            case LIGHTNING:
            case NATURAL:
                if (perms.has(Flags.nmonsters, FlagCombo.OnlyFalse)) {
                    event.setCancelled(true);
                    return;
                }
                break;
            case SPAWNER_EGG:
            case SPAWNER:
                if (perms.has(Flags.smonsters, FlagCombo.OnlyFalse)) {
                    event.setCancelled(true);
                    return;
                }
                break;
            default:
                break;
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {

        Player player = event.getPlayer();
        if (player == null)
            return;

        if (FlagPermissions.shouldIgnoreCheck(Flags.place, player)) {
            return;
        }
        if (FlagPermissions.shouldDenyAndNotify(player, event.getEntity(), Flags.place, Flags.build)) {
            event.setCancelled(true);
            player.updateInventory();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        Projectile projectile = event.getEntity();
        // disabling event on world
        if (plugin.isDisabledWorldListener(projectile)) {
            return;
        }
        Flags flag = Flags.shoot;
        CMIEntityType type = CMIEntityType.get(event.getEntityType());
        if (type != null) {
            switch (type) {
            case EXPERIENCE_BOTTLE:
            case FIREWORK_ROCKET:
                return;
            case ENDER_PEARL:
                flag = Flags.enderpearl;
                break;
            case WIND_CHARGE:
                flag = Flags.windexplode;
                break;
            default:
                break;
            }
        }
        // Disabling listener if flag disabled globally
        if (!flag.isGlobalyEnabled()) {
            return;
        }
        ProjectileSource shooter = projectile.getShooter();
        if (shooter instanceof Player) {

            Player player = (Player) shooter;
            if (FlagPermissions.shouldDenyAndNotify(player, projectile, flag, null)) {
                event.setCancelled(true);
            }

        } else {
            FlagPermissions perms = FlagPermissions.getPerms(projectile.getLocation());
            if (perms.has(flag, FlagCombo.OnlyFalse)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onHangingBreakEventByExplosion(HangingBreakEvent event) {

        Hanging ent = event.getEntity();
        if (ent == null)
            return;

        if (FlagPermissions.shouldIgnoreCheck(Flags.explode, ent)) {
            return;
        }
        if (!event.getCause().equals(RemoveCause.EXPLOSION))
            return;

        FlagPermissions perms = FlagPermissions.getPerms(ent.getLocation());
        if (!perms.has(Flags.explode, perms.has(Flags.destroy, true))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onHangingBreakEvent(HangingBreakEvent event) {

        Hanging ent = event.getEntity();
        if (ent == null)
            return;

        if (FlagPermissions.shouldIgnoreCheck(Flags.destroy, ent)) {
            return;
        }
        // ItemFrame covers item_frame/glow_item_frame
        if (!(ent instanceof ItemFrame)) {
            return;
        }
        if (!event.getCause().equals(RemoveCause.PHYSICS))
            return;

        FlagPermissions perms = FlagPermissions.getPerms(ent.getLocation());
        if (!perms.has(Flags.destroy, perms.has(Flags.build, true))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onHangingBreakByEntity(HangingBreakByEntityEvent event) {

        Hanging ent = event.getEntity();
        if (ent == null)
            return;

        if (FlagPermissions.shouldIgnoreCheck(Flags.destroy, ent)) {
            return;
        }
        if (event.getRemover() instanceof Player) {
            Player player = (Player) event.getRemover();

            if (plugin.getResidenceManager().isOwnerOfLocation(player, ent.getLocation())) {
                return;
            }
            if (FlagPermissions.shouldDenyAndNotify(player, ent, Flags.destroy, Flags.build)) {
                event.setCancelled(true);
            }
        } else {
            if (Utils.isSourceBlockInsideSameResidence(event.getRemover(), ClaimedResidence.getByLoc(event.getEntity().getLocation()))) {
                return;
            }
            FlagPermissions perms = FlagPermissions.getPerms(ent.getLocation());
            if (!perms.has(Flags.destroy, perms.has(Flags.build, true))) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityCombust(EntityCombustEvent event) {

        Entity ent = event.getEntity();
        if (ent == null)
            return;

        if (FlagPermissions.shouldIgnoreCheck(Flags.burn, ent)) {
            return;
        }
        FlagPermissions perms = FlagPermissions.getPerms(ent.getLocation());
        if (!perms.has(Flags.burn, true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onExplosionPrime(ExplosionPrimeEvent event) {
        // disabling event on world
        Entity ent = event.getEntity();
        if (ent == null)
            return;
        if (plugin.isDisabledWorldListener(ent))
            return;

        CMIEntityType type = CMIEntityType.get(event.getEntityType());

        if (type == null)
            return;

        FlagPermissions perms = FlagPermissions.getPerms(ent.getLocation());

        switch (type) {
        case CREEPER:

            // Disabling listener if flag disabled globally
            if (!Flags.creeper.isGlobalyEnabled())
                break;
            if (!perms.has(Flags.creeper, perms.has(Flags.explode, true))) {
                if (plugin.getConfigManager().isCreeperExplodeBelow()) {
                    if (ent.getLocation().getBlockY() >= plugin.getConfigManager().getCreeperExplodeBelowLevel()) {
                        event.setCancelled(true);
                        ent.remove();
                    } else {
                        ClaimedResidence res = plugin.getResidenceManager().getByLoc(ent.getLocation());
                        if (res != null) {
                            event.setCancelled(true);
                            ent.remove();
                        }
                    }
                } else {
                    event.setCancelled(true);
                    ent.remove();
                }
            }
            break;
        case ENDER_CRYSTAL:
            // Disabling listener if flag disabled globally
            if (!Flags.explode.isGlobalyEnabled())
                break;
            if (!perms.has(Flags.explode, perms.has(Flags.destroy, true))) {
                event.setCancelled(true);
                ent.remove();
            }
            break;
        case TNT:
        case TNT_MINECART:

            // Disabling listener if flag disabled globally
            if (!Flags.tnt.isGlobalyEnabled())
                break;

            if (!perms.has(Flags.tnt, perms.has(Flags.explode, true))) {
                if (plugin.getConfigManager().isTNTExplodeBelow()) {
                    if (ent.getLocation().getBlockY() >= plugin.getConfigManager().getTNTExplodeBelowLevel()) {
                        event.setCancelled(true);
                        ent.remove();
                    } else {
                        ClaimedResidence res = plugin.getResidenceManager().getByLoc(ent.getLocation());
                        if (res != null) {
                            event.setCancelled(true);
                            ent.remove();
                        }
                    }
                } else {
                    event.setCancelled(true);
                    ent.remove();
                }
            }
            break;
        case SMALL_FIREBALL:
        case FIREBALL:
            if ((Flags.explode.isGlobalyEnabled() && perms.has(Flags.explode, FlagCombo.OnlyFalse)) ||
                    (Flags.fireball.isGlobalyEnabled() && perms.has(Flags.fireball, FlagCombo.OnlyFalse))) {
                event.setCancelled(true);
                ent.remove();
            }
            break;
        case WITHER_SKULL:
            // Disabling listener if flag disabled globally
            if (!Flags.explode.isGlobalyEnabled())
                break;
            if (!perms.has(Flags.explode, perms.has(Flags.witherdestruction, true))) {
                event.setCancelled(true);
                ent.remove();
            }
            break;
        // These entity explosions are handled by EntityExplodeEvent
        case BREEZE_WIND_CHARGE:
        case WIND_CHARGE:
        case WITHER:
            break;
        default:
            // Disabling listener if flag disabled globally
            if (!Flags.explode.isGlobalyEnabled()) {
                break;
            }
            if (!perms.has(Flags.explode, perms.has(Flags.destroy, true))) {
                event.setCancelled(true);
            }
            break;
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        // ExplosionResult.TRIGGER_BLOCK does not destroy blocks
        // it is triggered by (WindCharge and Wind Charged Effect)
        if (Version.isCurrentEqualOrHigher(Version.v1_21_0)
                && event.getExplosionResult() == org.bukkit.ExplosionResult.TRIGGER_BLOCK) {
            ResidenceListener1_21.onWindExplode(event);
            return;
        }
        // disabling event on world
        Location loc = event.getLocation();
        if (plugin.isDisabledWorldListener(loc))
            return;

        Entity ent = event.getEntity();

        boolean cancel = false;
        boolean remove = true;
        FlagPermissions perms = FlagPermissions.getPerms(loc);

        CMIEntityType ctype = CMIEntityType.get(event.getEntityType());
        ProjectileSource shooter = null;
        // Explosion is prohibited at the source location; cancel the event directly
        if (ent != null && ctype != null) {

            switch (ctype) {
            case CREEPER:
                // Disabling listener if flag disabled globally
                if (!Flags.creeper.isGlobalyEnabled())
                    break;
                if (!perms.has(Flags.creeper, perms.has(Flags.explode, true)))
                    if (plugin.getConfigManager().isCreeperExplodeBelow()) {
                        if (loc.getBlockY() >= plugin.getConfigManager().getCreeperExplodeBelowLevel())
                            cancel = true;
                        else {
                            ClaimedResidence res = plugin.getResidenceManager().getByLoc(loc);
                            if (res != null)
                                cancel = true;
                        }
                    } else
                        cancel = true;
                break;
            case TNT:
            case TNT_MINECART:
                // Disabling listener if flag disabled globally
                if (!Flags.tnt.isGlobalyEnabled())
                    break;
                if (!perms.has(Flags.tnt, perms.has(Flags.explode, true))) {
                    if (plugin.getConfigManager().isTNTExplodeBelow()) {
                        if (loc.getBlockY() >= plugin.getConfigManager().getTNTExplodeBelowLevel())
                            cancel = true;
                        else {
                            ClaimedResidence res = plugin.getResidenceManager().getByLoc(loc);
                            if (res != null)
                                cancel = true;
                        }
                    } else
                        cancel = true;
                }
                break;
            case ENDER_CRYSTAL:
                // Disabling listener if flag disabled globally
                if (!Flags.explode.isGlobalyEnabled())
                    break;
                if (!perms.has(Flags.explode, perms.has(Flags.destroy, true))) {
                    cancel = true;
                }
                break;
            case SMALL_FIREBALL:
            case FIREBALL:
                if ((Flags.explode.isGlobalyEnabled() && perms.has(Flags.explode, FlagCombo.OnlyFalse)) ||
                        (Flags.fireball.isGlobalyEnabled() && perms.has(Flags.fireball, FlagCombo.OnlyFalse))) {
                    cancel = true;
                }
                break;
            case WITHER:
            case WITHER_SKULL:
                // Disabling listener if flag disabled globally
                if (!Flags.explode.isGlobalyEnabled())
                    break;
                if (!perms.has(Flags.explode, perms.has(Flags.witherdestruction, true))) {
                    cancel = true;
                }
                break;
            case ENDER_DRAGON:
                remove = false;
                break;
            default:
                // Disabling listener if flag disabled globally
                if (!Flags.explode.isGlobalyEnabled()) {
                    break;
                }
                if (!perms.has(Flags.explode, perms.has(Flags.destroy, true))) {
                    cancel = true;
                    remove = false;
                }
                break;
            }
        } else if (Flags.explode.isGlobalyEnabled() && !perms.has(Flags.explode, perms.has(Flags.destroy, true))) {
            cancel = true;
        }

        if (cancel) {
            event.setCancelled(true);
            if (ent != null && remove) {
                if (!event.getEntityType().equals(EntityType.WITHER))
                    ent.remove();
            }
            return;
        }
        // Source allows explosion, so check each affected block for destruction
        List<Block> preserve = new ArrayList<Block>();
        for (Block block : event.blockList()) {
            FlagPermissions blockperms = FlagPermissions.getPerms(block.getLocation());

            if (ent != null && ctype != null) {
                switch (ctype) {
                case CREEPER:
                    // Disabling listener if flag disabled globally
                    if (!Flags.creeper.isGlobalyEnabled())
                        continue;
                    if (!blockperms.has(Flags.creeper, blockperms.has(Flags.explode, true)))
                        if (plugin.getConfigManager().isCreeperExplodeBelow()) {
                            if (block.getY() >= plugin.getConfigManager().getCreeperExplodeBelowLevel())
                                preserve.add(block);
                            else {
                                ClaimedResidence res = plugin.getResidenceManager().getByLoc(block.getLocation());
                                if (res != null)
                                    preserve.add(block);
                            }
                        } else
                            preserve.add(block);
                    continue;
                case TNT:
                case TNT_MINECART:
                    // Disabling listener if flag disabled globally
                    if (!Flags.tnt.isGlobalyEnabled())
                        continue;
                    if (!blockperms.has(Flags.tnt, blockperms.has(Flags.explode, true))) {
                        if (plugin.getConfigManager().isTNTExplodeBelow()) {
                            if (block.getY() >= plugin.getConfigManager().getTNTExplodeBelowLevel())
                                preserve.add(block);
                            else {
                                ClaimedResidence res = plugin.getResidenceManager().getByLoc(block.getLocation());
                                if (res != null)
                                    preserve.add(block);
                            }
                        } else
                            preserve.add(block);
                    }
                    continue;
                case ENDER_DRAGON:
                    if (Flags.dragongrief.isGlobalyEnabled() && blockperms.has(Flags.dragongrief, FlagCombo.OnlyFalse)) {
                        preserve.add(block);
                    }
                    continue;
                case ENDER_CRYSTAL:
                    if ((Flags.explode.isGlobalyEnabled() && blockperms.has(Flags.explode, FlagCombo.OnlyFalse)) ||
                            (Flags.destroy.isGlobalyEnabled() && blockperms.has(Flags.destroy, FlagCombo.OnlyFalse))) {
                        preserve.add(block);
                    }
                    continue;
                case SMALL_FIREBALL:
                case FIREBALL:
                    if ((Flags.explode.isGlobalyEnabled() && blockperms.has(Flags.explode, FlagCombo.OnlyFalse)) ||
                            (Flags.fireball.isGlobalyEnabled() && blockperms.has(Flags.fireball, FlagCombo.OnlyFalse))) {
                        preserve.add(block);
                    }
                    continue;
                case WITHER:
                case WITHER_SKULL:
                    if ((Flags.witherdestruction.isGlobalyEnabled() && !blockperms.has(Flags.witherdestruction, blockperms.has(Flags.destroy, true)))
                            || (Flags.explode.isGlobalyEnabled() && blockperms.has(Flags.explode, FlagCombo.OnlyFalse))) {
                        preserve.add(block);
                    }
                    continue;
                default:
                    if ((Flags.destroy.isGlobalyEnabled() && blockperms.has(Flags.destroy, FlagCombo.OnlyFalse)) ||
                            (Flags.explode.isGlobalyEnabled() && blockperms.has(Flags.explode, FlagCombo.OnlyFalse))) {
                        preserve.add(block);
                    }
                    continue;
                }
            } else {
                if ((Flags.destroy.isGlobalyEnabled() && blockperms.has(Flags.destroy, FlagCombo.OnlyFalse)) ||
                        (Flags.explode.isGlobalyEnabled() && blockperms.has(Flags.explode, FlagCombo.OnlyFalse))) {
                    preserve.add(block);
                }
            }
        }

        if (!preserve.isEmpty()) {
            event.blockList().removeAll(preserve);
        }
    }

    // Various zombies break the door
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityBreakDoor(EntityBreakDoorEvent event) {

        Block block = event.getBlock();

        if (FlagPermissions.shouldIgnoreCheck(Flags.mobgriefing, block)) {
            return;
        }
        FlagPermissions perms = FlagPermissions.getPerms(block.getLocation());
        if (perms.has(Flags.mobgriefing, perms.has(Flags.destroy, true))) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSplashPotion(PotionSplashEvent event) {

        if (FlagPermissions.shouldIgnoreCheck(Flags.pvp, event.getEntity())) {
            return;
        }
        ProjectileSource shooter = event.getPotion().getShooter();

        if (shooter instanceof Witch)
            return;

        boolean harmfull = false;

        mein: for (PotionEffect one : event.getPotion().getEffects()) {
            for (String oneHarm : plugin.getConfigManager().getNegativePotionEffects()) {
                if (oneHarm.equalsIgnoreCase(one.getType().getName())) {
                    harmfull = true;
                    break mein;
                }
            }
        }

        if (!harmfull)
            return;

        Entity ent = event.getEntity();
        boolean srcpvp = FlagPermissions.getPerms(ent.getLocation()).has(Flags.pvp, FlagCombo.TrueOrNone);
        boolean animalKilling = FlagPermissions.getPerms(ent.getLocation()).has(Flags.animalkilling, FlagCombo.TrueOrNone);
        Iterator<LivingEntity> it = event.getAffectedEntities().iterator();
        boolean animalDamage = false;
        while (it.hasNext()) {
            LivingEntity target = it.next();

            if (Utils.isAnimal(target)) {
                if (!animalKilling) {
                    event.setIntensity(target, 0);
                    animalDamage = true;
                }
                continue;
            }

            if (target.getType() != EntityType.PLAYER)
                continue;
            Boolean tgtpvp = FlagPermissions.getPerms(target.getLocation()).has(Flags.pvp, FlagCombo.TrueOrNone);
            if (!srcpvp || !tgtpvp) {
                event.setIntensity(target, 0);
                continue;
            }

            ClaimedResidence area = plugin.getResidenceManager().getByLoc(target.getLocation());

            if (target instanceof Player && shooter instanceof Player) {
                Player attacker = (Player) shooter;
                ClaimedResidence srcarea = plugin.getResidenceManager().getByLoc(attacker.getLocation());
                if (srcarea != null && area != null && srcarea.equals(area)
                        && srcarea.getPermissions().playerHas((Player) target, Flags.friendlyfire, FlagCombo.OnlyFalse)
                        && srcarea.getPermissions().playerHas(attacker, Flags.friendlyfire, FlagCombo.OnlyFalse)) {
                    CMIActionBar.send(attacker, plugin.getLM().getMessage(lm.General_NoFriendlyFire));
                    event.setIntensity(target, 0);
                }
            }
        }

        if (!animalKilling && animalDamage && shooter instanceof Player) {
            lm.Flag_Deny.sendMessage((Player) shooter, Flags.animalkilling);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void PlayerKillingByFlame(EntityCombustByEntityEvent event) {

        Entity entity = event.getEntity();

        if (FlagPermissions.shouldIgnoreCheck(Flags.pvp, entity)) {
            return;
        }
        if (!(entity instanceof Player))
            return;

        ClaimedResidence res = plugin.getResidenceManager().getByLoc(entity.getLocation());

        if (res == null)
            return;

        Entity damager = event.getCombuster();

        if (!damageableProjectile(damager) && !(damager instanceof Player))
            return;

        if (damageableProjectile(damager) && !(((Projectile) damager).getShooter() instanceof Player))
            return;

        Player cause = Utils.potentialProjectileToPlayer(damager);

        if (cause == null)
            return;

        boolean srcpvp = FlagPermissions.has(cause.getLocation(), Flags.pvp, FlagCombo.TrueOrNone);
        boolean tgtpvp = FlagPermissions.has(entity.getLocation(), Flags.pvp, FlagCombo.TrueOrNone);
        if (!srcpvp || !tgtpvp)
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityShootBowEvent(EntityShootBowEvent event) {
        // issues https://github.com/Zrips/Residence/issues/466
        // Not sure when Paper stopped needing this, so to be safe, we'll only skip it for Paper 1.21+
        // https://github.com/PaperMC/Paper/pull/10307
        if (Version.isCurrentEqualOrHigher(Version.v1_21_0) && Version.isPaperBranch()) {
            return;
        }
        if (Version.isCurrentEqualOrLower(Version.v1_14_R1))
            return;

        if (event.getBow() == null)
            return;

        if (event.getBow().getType() != Material.CROSSBOW)
            return;

        if (!(event.getEntity() instanceof Player))
            return;

        if (event.getProjectile() instanceof Firework) {
            event.getProjectile().setMetadata(CrossbowShooter, new FixedMetadataValue(plugin, event.getEntity().getUniqueId()));
        }
    }

    private static void process(lm lm, Player attacker, boolean isOnFire, Entity victim, EntityDamageEvent event) {
        if (attacker != null)
            lm.sendMessage(attacker);
        if (isOnFire)
            victim.setFireTicks(0);
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerDamageByPlayer(EntityDamageByEntityEvent event) {
        Entity victim = event.getEntity();
        // disabling event on world
        if (plugin.isDisabledWorldListener(victim)) {
            return;
        }
        if (!(victim instanceof Player) || victim.hasMetadata("NPC")) {
            return;
        }
        Entity attacker = event.getDamager();
        Player attackerPlayer = null;
        boolean isOnFire = false;

        if (attacker instanceof Player) {
            attackerPlayer = (Player) attacker;

            // issues https://github.com/Zrips/Residence/issues/466
            // Not sure when Paper stopped needing this, so to be safe, we'll only skip it for Paper 1.21+
            // https://github.com/PaperMC/Paper/pull/10307
            // In higher versions, Firework also belongs to Projectile, and the shooter can be obtained normall
        } else if (attacker instanceof Firework && (!Version.isPaperBranch() || Version.isCurrentLower(Version.v1_21_0))) {
            List<MetadataValue> meta = attacker.getMetadata(CrossbowShooter);
            if (meta != null && !meta.isEmpty()) {
                try {
                    String uid = meta.get(0).asString();
                    attackerPlayer = Bukkit.getPlayer(UUID.fromString(uid));
                } catch (Throwable e) {
                }
            }

        } else if (attacker instanceof Projectile) {
            Projectile project = (Projectile) attacker;
            ProjectileSource shooter = project.getShooter();
            if (!(shooter instanceof Player)) {
                return;
            }
            attackerPlayer = (Player) shooter;
            if (project.getFireTicks() > 0) {
                isOnFire = true;
            }

        }
        if (attackerPlayer == null || attackerPlayer.hasMetadata("NPC")) {
            return;
        }
        // Now both the attacker and the victim are guaranteed to be players
        ClaimedResidence attackerRes = ClaimedResidence.getByLoc(attacker.getLocation());
        ClaimedResidence victimRes = ClaimedResidence.getByLoc(victim.getLocation());
        // Attacker and victim are in the same Residence
        if (attackerRes != null && victimRes != null && attackerRes.equals(victimRes)) {

            if (ConfigManager.RaidEnabled && victimRes.getRaid().isUnderRaid()) {
                boolean raidSameTeam = victimRes.getRaid().onSameTeam(attackerPlayer, (Player) victim);
                if (raidSameTeam && !ConfigManager.RaidFriendlyFire) {
                    event.setCancelled(true);
                    return;
                }
                if (!raidSameTeam) {
                    return;
                }
            }
            if (attackerRes.getPermissions().has(Flags.pvp, FlagCombo.OnlyFalse)) {
                process(lm.General_NoPVPZone, attackerPlayer, isOnFire, victim, event);
                return;
            }
            if (attackerRes.getPermissions().playerHas((Player) victim, Flags.friendlyfire, FlagCombo.OnlyFalse)
                    && attackerRes.getPermissions().playerHas(attackerPlayer, Flags.friendlyfire, FlagCombo.OnlyFalse)) {
                CMIActionBar.send(attackerPlayer, plugin.getLM().getMessage(lm.General_NoFriendlyFire));
                event.setCancelled(true);
                if (isOnFire) {
                    victim.setFireTicks(0);
                }
            }
            // Attacker and victim are not in the same Residence
        } else {
            // if PVP disabled at attacker location, cancel event
            if (attackerRes != null && attackerRes.getPermissions().has(Flags.pvp, FlagCombo.OnlyFalse)) {
                process(lm.General_NoPVPZone, attackerPlayer, isOnFire, victim, event);
                return;
            }
            // if PVP disabled at victim location, cancel event
            if (victimRes != null && victimRes.getPermissions().has(Flags.pvp, FlagCombo.OnlyFalse)) {
                process(lm.General_NoPVPZone, attackerPlayer, isOnFire, victim, event);
                return;
            }
            // Now attacker and victim are not in Residence
            if (attackerRes == null && victimRes == null) {
                /* World PvP */
                if (plugin.getWorldFlags().getPerms(attackerPlayer.getWorld()).has(Flags.pvp, FlagCombo.OnlyFalse)) {
                    process(lm.General_WorldPVPDisabled, attackerPlayer, isOnFire, victim, event);
                    return;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        Entity victim = event.getEntity();
        // disabling event on world
        if (plugin.isDisabledWorldListener(victim)) {
            return;
        }
        Entity attacker = event.getDamager();
        // Check held Material Blacklist
        if (attacker instanceof Player) {
            Player player = (Player) attacker;
            ItemStack item = CMIItemStack.getItemInMainHand(player);
            if (item != null && !plugin.getItemManager().isAllowed(item.getType(), player) && !ResAdmin.isResAdmin(player)) {
                lm.General_ItemBlacklisted.sendMessage(player);
                event.setCancelled(true);
                return;
            }
        }
        // Decorative entity damage uses separate logic
        if (victim instanceof EnderCrystal || victim instanceof ItemFrame || Utils.isArmorStand(victim)) {
            handleDecorativeEntityDamage(event);
            return;
        }
        Flags mainFlag = null;
        Flags subFlag = null;

        if (Flags.creeper.isGlobalyEnabled() && attacker instanceof Creeper) {
            mainFlag = Flags.creeper;
            subFlag = Flags.explode;

        } else if (Flags.explode.isGlobalyEnabled() && attacker instanceof EnderCrystal) {
            mainFlag = Flags.explode;
            subFlag = Flags.destroy;

        } else if (Flags.fireball.isGlobalyEnabled() && (event.getEntityType() == EntityType.FIREBALL || event.getEntityType() == EntityType.SMALL_FIREBALL)) {
            mainFlag = Flags.fireball;

        } else if (Flags.snowball.isGlobalyEnabled() && attacker instanceof Snowball) {
            mainFlag = Flags.snowball;

        } else if (Flags.tnt.isGlobalyEnabled() && (attacker instanceof TNTPrimed || attacker instanceof ExplosiveMinecart)) {
            mainFlag = Flags.tnt;
            subFlag = Flags.explode;

        } else if (Flags.witherdestruction.isGlobalyEnabled() && (attacker instanceof Wither || attacker instanceof WitherSkull)) {
            mainFlag = Flags.witherdamage;

        }
        if (mainFlag != null) {
            FlagPermissions perms = FlagPermissions.getPerms(victim.getLocation());
            boolean result = (subFlag == null || perms.has(subFlag, true));
            if (perms.has(mainFlag, result)) {
                return;
            }
            event.setCancelled(true);
        }
    }

    private void handleDecorativeEntityDamage(EntityDamageByEntityEvent event) {
        Entity attacker = event.getDamager();
        Entity victim = event.getEntity();
        Player player = Utils.potentialProjectileToPlayer(attacker);
        // if player damages ItemFrame with items inside, check Flags.container
        // this corresponds to taking the item out of the ItemFrame
        if (Flags.container.isGlobalyEnabled() && player != null && victim instanceof ItemFrame
                && ((ItemFrame) victim).getItem() != null && ((ItemFrame) victim).getItem().getType() != Material.AIR) {
            if (ResPerm.bypass_container.hasPermission(player, 10000L)) {
                return;
            }
            if (FlagPermissions.shouldDenyAndNotify(player, victim, Flags.container, Flags.use)) {
                event.setCancelled(true);
            }
            // damage from player or player-fired projectile
        } else if (Flags.destroy.isGlobalyEnabled() && player != null) {
            if (FlagPermissions.shouldDenyAndNotify(player, victim, Flags.destroy, Flags.build)) {
                event.setCancelled(true);
            }
            // damage from non-players or projectiles fired by non-players
        } else if (Flags.destroy.isGlobalyEnabled()) {
            if (attacker instanceof Projectile && Utils.isSourceBlockInsideSameResidence(attacker, ClaimedResidence.getByLoc(victim.getLocation()))) {
                return;
            }
            FlagPermissions perms = FlagPermissions.getPerms(victim.getLocation());
            if (!perms.has(Flags.destroy, perms.has(Flags.build, true))) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityDamageEvent(EntityDamageEvent event) {

        Entity entity = event.getEntity();

        if (plugin.isDisabledWorldListener(entity)) {
            return;
        }
        if (Flags.damage.isGlobalyEnabled() && event.getCause() != DamageCause.VOID
                && (entity instanceof Player || Utils.isTamed(entity))
                && FlagPermissions.has(entity.getLocation(), Flags.damage, FlagCombo.OnlyFalse)) {
            event.setCancelled(true);
            entity.setFireTicks(0);
            return;
        }

        if (Flags.falldamage.isGlobalyEnabled() && event.getCause() == DamageCause.FALL && entity instanceof Player) {
            if (FlagPermissions.has(entity.getLocation(), Flags.falldamage, FlagCombo.OnlyFalse)) {
                event.setCancelled(true);
            }
            return;
        }
        if (Flags.pvp.isGlobalyEnabled() && event.getCause() == DamageCause.LIGHTNING && entity instanceof Player) {
            if (FlagPermissions.has(entity.getLocation(), Flags.pvp, FlagCombo.OnlyFalse)) {
                event.setCancelled(true);
            }
            return;
        }
        if (Flags.destroy.isGlobalyEnabled()
                && (event.getCause() == DamageCause.BLOCK_EXPLOSION || event.getCause() == DamageCause.ENTITY_EXPLOSION || event.getCause() == DamageCause.FIRE_TICK)
                && (entity instanceof Arrow || Utils.isArmorStand(entity))) {
            if (FlagPermissions.has(entity.getLocation(), Flags.destroy, FlagCombo.OnlyFalse)) {
                event.setCancelled(true);
                entity.setFireTicks(0);
            }
            return;
        }

    }
}
