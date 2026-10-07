package com.bekvon.bukkit.residence.protection;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.metadata.Metadatable;

import com.bekvon.bukkit.residence.Residence;
import com.bekvon.bukkit.residence.containers.Flags;
import com.bekvon.bukkit.residence.protection.FlagPermissions.FlagCombo;

/** Internal PvP checks shared by damage, potion and knockback listeners. */
public final class PvpProtection {

    private static final String LAUNCH_METADATA = "ResidencePvpLaunch";
    // Resolve optional APIs once so the base listeners still load on older Bukkit versions.
    private static final Method DAMAGE_SOURCE = findMethod(EntityDamageEvent.class, "getDamageSource");
    private static final Method VEHICLE_DAMAGE_SOURCE = findMethod(VehicleDamageEvent.class, "getDamageSource");
    private static final Method CAUSING_ENTITY = damageSourceMethod("getCausingEntity");
    private static final Method DIRECT_ENTITY = damageSourceMethod("getDirectEntity");
    private final Residence plugin;

    public PvpProtection(Residence plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabledFor(Entity target) {
        return Flags.pvp.isGlobalyEnabled() && !plugin.isDisabledWorldListener(target);
    }

    public void recordLaunch(Entity projectile, Player shooter) {
        if (shooter.hasMetadata("NPC") || getLaunch(projectile) != null)
            return;
        projectile.setMetadata(LAUNCH_METADATA, new FixedMetadataValue(plugin, capture(shooter)));
    }

    private Launch capture(Player shooter) {
        Location location = shooter.getLocation().clone();
        return new Launch(shooter.getUniqueId(), location, isDeniedAt(location),
                plugin.getResidenceManager().getByLoc(location));
    }

    public void copyLaunch(Metadatable source, Metadatable target) {
        Launch launch = getLaunch(source);
        if (launch != null)
            target.setMetadata(LAUNCH_METADATA, new FixedMetadataValue(plugin, launch));
    }

    public void clearLaunch(Metadatable source) {
        source.removeMetadata(LAUNCH_METADATA, plugin);
    }

    /** Preserve a restricted causal origin, otherwise capture the actual trigger player's position. */
    public void recordTrigger(Metadatable explosive, Entity cause, Player player) {
        clearLaunch(explosive);
        if (player != null && player.hasMetadata("NPC"))
            return;
        Launch inherited = sourceLaunch(cause, 0);
        Launch launch;
        if (inherited != null && inherited.denied) {
            UUID responsible = player != null ? player.getUniqueId() : inherited.shooter;
            launch = new Launch(responsible, inherited.location.clone(), true, inherited.residence.get());
        } else {
            launch = player != null ? capture(player) : inherited;
        }
        if (launch != null)
            explosive.setMetadata(LAUNCH_METADATA, new FixedMetadataValue(plugin, launch));
    }

    public Entity getDamageSource(EntityDamageEvent event) {
        Entity direct = damageEntity(event, DAMAGE_SOURCE, DIRECT_ENTITY);
        if (direct != null)
            return direct;
        if (event instanceof EntityDamageByEntityEvent)
            return ((EntityDamageByEntityEvent) event).getDamager();
        return damageEntity(event, DAMAGE_SOURCE, CAUSING_ENTITY);
    }

    public Player getDamagePlayer(EntityDamageEvent event) {
        Player player = getPlayer(damageEntity(event, DAMAGE_SOURCE, CAUSING_ENTITY));
        return player != null ? player : getPlayer(getDamageSource(event));
    }

    public Entity getDamageSource(VehicleDamageEvent event) {
        Entity direct = damageEntity(event, VEHICLE_DAMAGE_SOURCE, DIRECT_ENTITY);
        return direct != null ? direct : event.getAttacker();
    }

    public Player getDamagePlayer(VehicleDamageEvent event) {
        Player player = getPlayer(damageEntity(event, VEHICLE_DAMAGE_SOURCE, CAUSING_ENTITY));
        return player != null ? player : getPlayer(getDamageSource(event));
    }

    public Player getPlayer(Entity source) {
        return getPlayer(source, 0);
    }

    private Player getPlayer(Entity source, int depth) {
        if (source == null || depth >= 16)
            return null;
        if (source instanceof Player)
            return (Player) source;
        Player player = getPlayer(parentSource(source), depth + 1);
        if (player != null)
            return player;
        Launch launch = getLaunch(source);
        if (launch != null)
            return plugin.getServ().getPlayer(launch.shooter);
        // Fireworks on older server versions do not expose Projectile#getShooter.
        if (source instanceof Firework) {
            for (MetadataValue value : source.getMetadata("CrossbowShooter")) {
                if (value.getOwningPlugin() != plugin)
                    continue;
                try {
                    return plugin.getServ().getPlayer(UUID.fromString(value.asString()));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return null;
    }

    public boolean hasPlayerSource(Entity source, Player player) {
        return player != null || sourceLaunch(source, 0) != null;
    }

    public boolean isDenied(Entity source, Player player, Player target, boolean allowSelf) {
        if (!isEnabledFor(target) || target.hasMetadata("NPC")
                || (player != null && player.hasMetadata("NPC")) || !hasPlayerSource(source, player))
            return false;
        Launch launch = sourceLaunch(source, 0);
        UUID shooter = player != null ? player.getUniqueId() : launch.shooter;
        if (allowSelf && target.getUniqueId().equals(shooter))
            return false;
        return (launch != null && launch.denied) || (player != null && isDeniedAt(player.getLocation()))
                || isDeniedAt(target.getLocation());
    }

    /** A raid may override its own PvP flag, never a launch from another protected area. */
    public boolean isLaunchDeniedOutside(Entity source, ClaimedResidence raidResidence) {
        Launch launch = sourceLaunch(source, 0);
        return launch != null && launch.denied
                && (!raidResidence.equals(launch.residence.get())
                        || !raidResidence.equals(plugin.getResidenceManager().getByLoc(launch.location)));
    }

    private boolean isDeniedAt(Location location) {
        if (!Flags.pvp.isGlobalyEnabled() || location == null || location.getWorld() == null
                || plugin.isDisabledWorldListener(location))
            return false;
        ClaimedResidence residence = plugin.getResidenceManager().getByLoc(location);
        FlagPermissions permissions = residence != null ? residence.getPermissions()
                : plugin.getWorldFlags().getPerms(location.getWorld().getName());
        return permissions.has(Flags.pvp, FlagCombo.OnlyFalse);
    }

    private Launch getLaunch(Metadatable entity) {
        if (entity == null)
            return null;
        for (MetadataValue value : entity.getMetadata(LAUNCH_METADATA)) {
            if (value.getOwningPlugin() == plugin && value.value() instanceof Launch)
                return (Launch) value.value();
        }
        return null;
    }

    private Launch sourceLaunch(Entity source, int depth) {
        if (source == null || depth >= 16)
            return null;
        Launch launch = getLaunch(source);
        return launch != null ? launch : sourceLaunch(parentSource(source), depth + 1);
    }

    private static Entity parentSource(Entity source) {
        if (source instanceof TNTPrimed) {
            try {
                return ((TNTPrimed) source).getSource();
            } catch (NoSuchMethodError ignored) {
                // Older Bukkit APIs do not provide a TNT source.
            }
        }
        if (source instanceof Projectile && ((Projectile) source).getShooter() instanceof Entity)
            return (Entity) ((Projectile) source).getShooter();
        return null;
    }

    private static Method findMethod(Class<?> type, String name) {
        try {
            return type.getMethod(name);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static Method damageSourceMethod(String name) {
        try {
            return findMethod(Class.forName("org.bukkit.damage.DamageSource"), name);
        } catch (ClassNotFoundException ignored) {
            return null;
        }
    }

    private static Entity damageEntity(Object event, Method sourceMethod, Method entityMethod) {
        if (sourceMethod == null || entityMethod == null)
            return null;
        try {
            Object source = sourceMethod.invoke(event);
            return source == null ? null : (Entity) entityMethod.invoke(source);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static final class Launch {
        private final UUID shooter;
        private final Location location;
        private final boolean denied;
        private final WeakReference<ClaimedResidence> residence;

        private Launch(UUID shooter, Location location, boolean denied, ClaimedResidence residence) {
            this.shooter = shooter;
            this.location = location;
            this.denied = denied;
            this.residence = new WeakReference<>(residence);
        }
    }
}
