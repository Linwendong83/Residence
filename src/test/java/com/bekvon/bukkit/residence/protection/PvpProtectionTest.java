package com.bekvon.bukkit.residence.protection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.block.Block;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Firework;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.entity.Trident;
import org.bukkit.entity.WindCharge;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.minecart.ExplosiveMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.block.TNTPrimeEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.entity.EntityKnockbackByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.LingeringPotionSplashEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.metadata.Metadatable;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.bekvon.bukkit.residence.ConfigManager;
import com.bekvon.bukkit.residence.Residence;
import com.bekvon.bukkit.residence.listeners.ResidenceEntityListener;
import com.bekvon.bukkit.residence.listeners.ResidenceListener1_09;
import com.bekvon.bukkit.residence.listeners.ResidenceListener1_20;
import com.bekvon.bukkit.residence.listeners.ResidenceListener1_13;
import com.bekvon.bukkit.residence.listeners.ResidenceListener1_21_8_Paper;
import com.bekvon.bukkit.residence.listeners.ResidenceListener1_21_8_Spigot;
import com.bekvon.bukkit.residence.protection.FlagPermissions.FlagCombo;
import com.bekvon.bukkit.residence.containers.Flags;
import com.bekvon.bukkit.residence.raid.ResidenceRaid;
import com.bekvon.bukkit.residence.text.Language;

import io.papermc.paper.event.entity.EntityPushedByEntityAttackEvent;
import net.Zrips.CMILib.CMILib;
import net.Zrips.CMILib.Version.Version;

public class PvpProtectionTest {

    private Residence plugin;
    private ResidenceManager residences;
    private ClaimedResidence home;
    private ClaimedResidence other;
    private ResidencePermissions homePermissions;
    private FlagPermissions homeFlags;
    private FlagPermissions otherFlags;
    private FlagPermissions worldFlags;
    private ConfigManager config;
    private World world;
    private Block tntBlock;
    private Player shooter;
    private Player target;
    private PvpProtection protection;
    private ResidenceEntityListener entities;
    private final List<Runnable> arrowResyncTasks = new ArrayList<>();
    private final List<Entity> resyncedArrows = new ArrayList<>();
    private final Map<UUID, Player> online = new HashMap<>();
    private Object oldPlugin;
    private Object oldVersion;
    private Object oldServer;
    private Object oldCmiLib;
    private boolean oldPvpEnabled;
    private boolean oldRaidEnabled;
    private boolean oldRaidFriendlyFire;

    @Before
    public void setUp() throws Exception {
        oldServer = staticField(Bukkit.class, "server").get(null);
        Server server = mock(Server.class);
        when(server.getBukkitVersion()).thenReturn("26.2-R0.1-SNAPSHOT");
        when(server.getMinecraftVersion()).thenReturn("26.2");
        when(server.getVersion()).thenReturn("Paper 26.2");
        when(server.getName()).thenReturn("Paper");
        when(server.getConsoleSender()).thenReturn(mock(ConsoleCommandSender.class));
        staticField(Bukkit.class, "server").set(null, server);
        oldCmiLib = staticField(CMILib.class, "instance").get(null);
        CMILib cmiLib = mock(CMILib.class, RETURNS_DEEP_STUBS);
        staticField(CMILib.class, "instance").set(null, cmiLib);
        when(cmiLib.getReflectionManager().getServerName()).thenReturn("UnitTest");
        oldPlugin = staticField(Residence.class, "instance").get(null);
        oldVersion = staticField(Version.class, "current").get(null);
        staticField(Version.class, "current").set(null, Version.v26_2_0);
        oldPvpEnabled = Flags.pvp.isGlobalyEnabled();
        oldRaidEnabled = ConfigManager.RaidEnabled;
        oldRaidFriendlyFire = ConfigManager.RaidFriendlyFire;
        Flags.pvp.setGlobalyEnabled(true);
        ConfigManager.RaidEnabled = false;
        ConfigManager.RaidFriendlyFire = false;
        online.clear();

        plugin = mock(Residence.class);
        staticField(Residence.class, "instance").set(null, plugin);
        residences = mock(ResidenceManager.class);
        WorldFlagManager worldManager = mock(WorldFlagManager.class);
        config = mock(ConfigManager.class);
        when(plugin.getResidenceManager()).thenReturn(residences);
        when(plugin.getWorldFlags()).thenReturn(worldManager);
        when(plugin.getServ()).thenReturn(server);
        when(plugin.getConfigManager()).thenReturn(config);
        when(plugin.getLM()).thenReturn(mock(Language.class));
        when(server.getPlayer(any(UUID.class))).thenAnswer(call -> online.get(call.getArgument(0)));
        when(config.getNegativePotionEffects()).thenReturn(Collections.singletonList("poison"));
        when(config.getNegativeLingeringPotionEffects()).thenReturn(Collections.singletonList("poison"));
        world = mock(World.class);
        when(world.getName()).thenReturn("world");
        tntBlock = mock(Block.class);
        metadata(tntBlock);
        when(tntBlock.getLocation()).thenReturn(location(10));
        when(world.getBlockAt(any(Location.class))).thenReturn(tntBlock);

        home = mock(ClaimedResidence.class);
        other = mock(ClaimedResidence.class);
        homeFlags = flags(false);
        otherFlags = flags(true);
        worldFlags = flags(true);
        homePermissions = permissions(home, homeFlags);
        permissions(other, otherFlags);
        when(worldManager.getPerms("world")).thenReturn(worldFlags);
        when(residences.getByLoc(any(Location.class))).thenAnswer(call -> {
            double x = ((Location) call.getArgument(0)).getX();
            return x < 0 ? home : x >= 100 ? other : null;
        });
        shooter = player(-10);
        target = player(10);
        protection = new PvpProtection(plugin);
        entities = new ResidenceEntityListener(plugin);
        arrowResyncTasks.clear();
        resyncedArrows.clear();
        Field resyncField = ResidenceEntityListener.class.getDeclaredField("arrowHitResync");
        resyncField.setAccessible(true);
        resyncField.set(entities, new ArrowHitResync((arrow, task) -> arrowResyncTasks.add(task), resyncedArrows::add));
    }

    @After
    public void tearDown() throws Exception {
        staticField(Residence.class, "instance").set(null, oldPlugin);
        staticField(Version.class, "current").set(null, oldVersion);
        staticField(Bukkit.class, "server").set(null, oldServer);
        staticField(CMILib.class, "instance").set(null, oldCmiLib);
        Flags.pvp.setGlobalyEnabled(oldPvpEnabled);
        ConfigManager.RaidEnabled = oldRaidEnabled;
        ConfigManager.RaidFriendlyFire = oldRaidFriendlyFire;
    }

    @Test
    public void blocksOutgoingArrowEvenThoughImpactIsOutside() {
        assertDamageDenied(projectile(Arrow.class), true);
    }

    @Test
    public void resynchronizesTheArrowAfterDenyingACrossBoundaryHit() {
        Arrow arrow = projectile(Arrow.class);
        when(arrow.isValid()).thenReturn(true);
        EntityDamageByEntityEvent event = statefulArrowDamage(arrow);
        entities.onPlayerDamageByPlayer(event);
        assertTrue(event.isCancelled());
        assertEquals(1, arrowResyncTasks.size());
        assertTrue(resyncedArrows.isEmpty());
        arrowResyncTasks.get(0).run();
        assertSame(arrow, resyncedArrows.get(0));
    }

    @Test
    public void doesNotResynchronizeAllowedPvp() {
        move(shooter, 10);
        EntityDamageByEntityEvent event = statefulArrowDamage(projectile(Arrow.class));
        entities.onPlayerDamageByPlayer(event);
        assertFalse(event.isCancelled());
        assertTrue(arrowResyncTasks.isEmpty());
    }

    @Test
    public void alsoResynchronizesRejectedRaidFriendlyFire() {
        move(target, -5);
        homeFlags.cuboidFlags.put("pvp", true);
        ResidenceRaid raid = raid(home);
        when(raid.onSameTeam(shooter, target)).thenReturn(true);
        Arrow arrow = projectile(Arrow.class);
        when(arrow.isValid()).thenReturn(true);
        EntityDamageByEntityEvent event = statefulArrowDamage(arrow);
        entities.onPlayerDamageByPlayer(event);
        assertTrue(event.isCancelled());
        assertEquals(1, arrowResyncTasks.size());
        arrowResyncTasks.get(0).run();
        assertSame(arrow, resyncedArrows.get(0));
    }

    private EntityDamageByEntityEvent statefulArrowDamage(Arrow arrow) {
        EntityDamageByEntityEvent event = damage(arrow, target, source(arrow, shooter));
        boolean[] cancelled = { false };
        when(event.isCancelled()).thenAnswer(call -> cancelled[0]);
        doAnswer(call -> {
            cancelled[0] = call.getArgument(0);
            return null;
        }).when(event).setCancelled(anyBoolean());
        return event;
    }

    @Test
    public void blocksIncomingArrow() {
        move(shooter, 10);
        move(target, -10);
        assertDamageDenied(projectile(Arrow.class), true);
    }

    @Test
    public void checksBothPlayersAcrossDifferentResidences() {
        move(target, 110);
        assertDamageDenied(projectile(Arrow.class), true);
        homeFlags.cuboidFlags.put("pvp", true);
        otherFlags.cuboidFlags.put("pvp", false);
        assertDamageDenied(projectile(Arrow.class), true);
        otherFlags.cuboidFlags.put("pvp", true);
        assertDamageDenied(projectile(Arrow.class), false);
    }

    @Test
    public void allowsCombatWhenAllLocationsAllowPvp() {
        move(shooter, 10);
        assertDamageDenied(projectile(Arrow.class), false);
    }

    @Test
    public void keepsLaunchRestrictionAfterMovingAndOpeningPvp() {
        Arrow arrow = projectile(Arrow.class);
        record(arrow);
        move(shooter, 10);
        homeFlags.cuboidFlags.put("pvp", true);
        assertDamageDenied(arrow, true);
    }

    @Test
    public void checksCurrentShooterLocationAfterAnAllowedLaunch() {
        move(shooter, 10);
        Arrow arrow = projectile(Arrow.class);
        record(arrow);
        move(shooter, -10);
        assertDamageDenied(arrow, true);
    }

    @Test
    public void keepsLaunchRestrictionAfterShooterDisconnects() {
        Arrow arrow = projectile(Arrow.class);
        record(arrow);
        when(arrow.getShooter()).thenReturn(null);
        online.remove(shooter.getUniqueId());
        assertNull(protection.getPlayer(arrow));
        assertDamageDenied(arrow, true);
    }

    @Test
    public void protectsIncomingTargetAfterShooterDisconnects() {
        move(shooter, 10);
        move(target, -10);
        Arrow arrow = projectile(Arrow.class);
        record(arrow);
        when(arrow.getShooter()).thenReturn(null);
        online.remove(shooter.getUniqueId());
        assertDamageDenied(arrow, true);
    }

    @Test
    public void coversTridentsFireworksAndWindChargeDamage() {
        assertDamageDenied(projectile(Trident.class), true);
        assertDamageDenied(projectile(Firework.class), true);
        assertDamageDenied(projectile(WindCharge.class), true);
    }

    @Test
    public void bowEventRecordsFireworksWithoutExposedShooter() {
        Firework firework = projectile(Firework.class);
        when(firework.getShooter()).thenReturn(null);
        EntityShootBowEvent event = mock(EntityShootBowEvent.class);
        when(event.getEntity()).thenReturn(shooter);
        when(event.getProjectile()).thenReturn(firework);
        entities.onBowPvpLaunch(event);
        move(shooter, 10);
        assertDamageDenied(firework, true);
    }

    @Test
    public void launchRecorderDoesNotCancelShooting() {
        ProjectileLaunchEvent event = mock(ProjectileLaunchEvent.class);
        Arrow arrow = projectile(Arrow.class);
        when(event.getEntity()).thenReturn(arrow);
        entities.onProjectilePvpLaunch(event);
        verify(event, never()).setCancelled(true);
    }

    @Test
    public void usesInheritedSourceFlag() {
        homeFlags.cuboidFlags.clear();
        homeFlags.setParent(flags(false));
        assertDamageDenied(projectile(Arrow.class), true);
    }

    @Test
    public void respectsWorldPvpFlagsOutsideResidences() {
        move(shooter, 10);
        worldFlags.cuboidFlags.put("pvp", false);
        assertDamageDenied(projectile(Arrow.class), true);
    }

    @Test
    public void ignoresForeignPluginLaunchMetadata() {
        move(shooter, 10);
        Arrow arrow = projectile(Arrow.class);
        arrow.setMetadata("ResidencePvpLaunch", new FixedMetadataValue(mock(Residence.class), "foreign"));
        assertDamageDenied(arrow, false);
    }

    @Test
    public void respectsGlobalFlagAndDisabledTargetWorld() {
        Arrow arrow = projectile(Arrow.class);
        record(arrow);
        Flags.pvp.setGlobalyEnabled(false);
        assertDamageDenied(arrow, false);
        Flags.pvp.setGlobalyEnabled(true);
        when(plugin.isDisabledWorldListener(target)).thenReturn(true);
        assertDamageDenied(arrow, false);
    }

    @Test
    public void doesNotCaptureRestrictionInDisabledSourceWorld() {
        when(plugin.isDisabledWorldListener(any(Location.class))).thenReturn(true);
        Arrow arrow = projectile(Arrow.class);
        record(arrow);
        when(plugin.isDisabledWorldListener(any(Location.class))).thenReturn(false);
        move(shooter, 10);
        assertDamageDenied(arrow, false);
    }

    @Test
    public void excludesNpcShootersAndTargets() {
        when(shooter.hasMetadata("NPC")).thenReturn(true);
        assertDamageDenied(projectile(Arrow.class), false);
        when(shooter.hasMetadata("NPC")).thenReturn(false);
        when(target.hasMetadata("NPC")).thenReturn(true);
        assertDamageDenied(projectile(Arrow.class), false);
    }

    @Test
    public void preventsIgnitionOutsideAfterShooterMoves() {
        Arrow arrow = projectile(Arrow.class);
        record(arrow);
        move(shooter, 10);
        EntityCombustByEntityEvent event = mock(EntityCombustByEntityEvent.class);
        when(event.getEntity()).thenReturn(target);
        when(event.getCombuster()).thenReturn(arrow);
        entities.PlayerKillingByFlame(event);
        verify(event).setCancelled(true);
    }

    @Test
    public void clearsFlamesWhenFlamingArrowDamageIsDenied() {
        Arrow arrow = projectile(Arrow.class);
        when(arrow.getFireTicks()).thenReturn(20);
        assertDamageDenied(arrow, true);
        verify(target).setFireTicks(0);
    }

    @Test
    public void retainsFriendlyFireProtection() throws Exception {
        // Use the notification backend that needs no server packet classes in this API-only test.
        staticField(Version.class, "current").set(null, Version.v1_7_R4);
        homeFlags.cuboidFlags.put("pvp", true);
        move(target, -5);
        when(homePermissions.playerHas(shooter, Flags.friendlyfire, FlagCombo.OnlyFalse)).thenReturn(true);
        when(homePermissions.playerHas(target, Flags.friendlyfire, FlagCombo.OnlyFalse)).thenReturn(true);
        assertDamageDenied(projectile(Arrow.class), true);
    }

    @Test
    public void globalPvpSwitchDoesNotDisableFriendlyFireProtection() throws Exception {
        Flags.pvp.setGlobalyEnabled(false);
        retainsFriendlyFireProtection();
    }

    @Test
    public void allowsRaidOpponentsFromTheSameResidence() {
        move(target, -5);
        raid(home);
        Arrow arrow = projectile(Arrow.class);
        record(arrow);
        assertDamageDenied(arrow, false);
    }

    @Test
    public void raidCannotExemptLaunchFromAnotherProtectedResidence() {
        Arrow arrow = projectile(Arrow.class);
        record(arrow);
        // Mutating the player's original Location must not change the saved launch location.
        shooter.getLocation().setX(110);
        move(target, 115);
        otherFlags.cuboidFlags.put("pvp", false);
        raid(other);
        assertDamageDenied(arrow, true);
    }

    @Test
    public void retainsRaidTeamProtection() {
        move(target, -5);
        ResidenceRaid raid = raid(home);
        when(raid.onSameTeam(shooter, target)).thenReturn(true);
        assertDamageDenied(projectile(Arrow.class), true);
    }

    @Test
    public void raidCannotExemptLaunchWhenOriginalResidenceIsReplaced() {
        Arrow arrow = projectile(Arrow.class);
        record(arrow);
        move(shooter, 110);
        move(target, 115);
        raid(other);
        when(residences.getByLoc(any(Location.class))).thenReturn(other);
        assertDamageDenied(arrow, true);
    }

    @Test
    public void paperAndSpigotKnockbackUseLaunchRestriction() throws Exception {
        WindCharge wind = projectile(WindCharge.class);
        record(wind);
        move(shooter, 10);
        EntityKnockbackByEntityEvent paperEvent = knockback(wind);
        new ResidenceListener1_21_8_Paper(plugin).onProjectileKnockback(paperEvent);
        verify(paperEvent).setCancelled(true);
        EntityKnockbackByEntityEvent spigotEvent = knockback(wind);
        new ResidenceListener1_21_8_Spigot(plugin).onKnockback(spigotEvent);
        verify(spigotEvent).setCancelled(true);
        // Paper propagates legacy-event cancellation to this later event.
        assertTrue(ResidenceListener1_21_8_Paper.class.getMethod("onKnockback", EntityPushedByEntityAttackEvent.class)
                .getAnnotation(EventHandler.class).ignoreCancelled());
    }

    @Test
    public void knockbackChecksCurrentSourceAndIncomingTarget() {
        assertTrue(ResidenceListener1_21_8_Paper.shouldCancelKnockBack(target, projectile(WindCharge.class)));
        move(shooter, 10);
        move(target, -10);
        assertTrue(ResidenceListener1_21_8_Paper.shouldCancelKnockBack(target, projectile(WindCharge.class)));
        move(target, 10);
        assertFalse(ResidenceListener1_21_8_Paper.shouldCancelKnockBack(target, projectile(WindCharge.class)));
    }

    @Test
    public void paperShooterOnlyEventChecksCurrentSource() {
        EntityPushedByEntityAttackEvent event = mock(EntityPushedByEntityAttackEvent.class);
        when(event.getEntity()).thenReturn(target);
        when(event.getPushedBy()).thenReturn(shooter);
        new ResidenceListener1_21_8_Paper(plugin).onKnockback(event);
        verify(event).setCancelled(true);
    }

    @Test
    public void knockbackKeepsRestrictionAfterDisconnectAndAllowsOfflineSelf() {
        WindCharge wind = projectile(WindCharge.class);
        record(wind);
        when(wind.getShooter()).thenReturn(null);
        online.remove(shooter.getUniqueId());
        assertTrue(ResidenceListener1_21_8_Paper.shouldCancelKnockBack(target, wind));
        assertFalse(ResidenceListener1_21_8_Paper.shouldCancelKnockBack(shooter, wind));
    }

    @Test
    public void allowsSelfPropulsionAndExcludesNonPlayerWindSources() {
        WindCharge wind = projectile(WindCharge.class);
        record(wind);
        assertFalse(ResidenceListener1_21_8_Paper.shouldCancelKnockBack(shooter, wind));
        WindCharge naturalWind = projectile(WindCharge.class);
        when(naturalWind.getShooter()).thenReturn(null);
        move(target, -5);
        assertFalse(ResidenceListener1_21_8_Paper.shouldCancelKnockBack(target, naturalWind));
    }

    @Test
    public void splashPotionBlocksOutgoingEffectsWithoutFilteringAnimals() {
        ThrownPotion potion = harmfulPotion();
        record(potion);
        move(shooter, 10);
        Cow animal = animal();
        PotionSplashEvent event = splash(potion, target, animal);
        entities.onSplashPotion(event);
        verify(event).setIntensity(target, 0);
        verify(event, never()).setIntensity(animal, 0);
    }

    @Test
    public void splashPotionFiltersOnlyProtectedIncomingPlayers() {
        move(shooter, 10);
        Player protectedPlayer = player(-5);
        Cow animal = animal();
        PotionSplashEvent event = splash(harmfulPotion(), protectedPlayer, target, animal);
        entities.onSplashPotion(event);
        verify(event).setIntensity(protectedPlayer, 0);
        verify(event, never()).setIntensity(target, 0);
        verify(event, never()).setIntensity(animal, 0);
    }

    @Test
    public void lingeringCloudKeepsLaunchRestrictionAfterDisconnect() {
        ThrownPotion potion = harmfulPotion();
        record(potion);
        AreaEffectCloud cloud = cloud();
        LingeringPotionSplashEvent splash = mock(LingeringPotionSplashEvent.class);
        when(splash.getEntity()).thenReturn(potion);
        when(splash.getAreaEffectCloud()).thenReturn(cloud);
        ResidenceListener1_09 listener = new ResidenceListener1_09(plugin);
        listener.onLingeringSplashPotion(splash);
        online.remove(shooter.getUniqueId());
        when(cloud.getSource()).thenReturn(null);
        Cow animal = animal();
        List<LivingEntity> affected = new ArrayList<>(Arrays.asList(target, animal));
        listener.onLingeringEffectApply(apply(cloud, affected));
        assertEquals(Collections.singletonList(animal), affected);
        verify(cloud, never()).remove();
        verify(splash, never()).setCancelled(true);
    }

    @Test
    public void lingeringCloudFiltersProtectedPlayersWithoutDestroyingCloud() {
        move(shooter, 10);
        Player protectedPlayer = player(-5);
        Cow animal = animal();
        AreaEffectCloud cloud = cloud();
        List<LivingEntity> affected = new ArrayList<>(Arrays.asList(protectedPlayer, target, animal));
        new ResidenceListener1_09(plugin).onLingeringEffectApply(apply(cloud, affected));
        assertEquals(Arrays.asList(target, animal), affected);
        verify(cloud, never()).remove();
    }

    @Test
    public void retainsLingeringPotionLandingProtection() {
        ThrownPotion potion = harmfulPotion();
        when(potion.getLocation()).thenReturn(location(-5));
        LingeringPotionSplashEvent event = mock(LingeringPotionSplashEvent.class);
        when(event.getEntity()).thenReturn(potion);
        AreaEffectCloud cloud = cloud();
        when(event.getAreaEffectCloud()).thenReturn(cloud);
        new ResidenceListener1_09(plugin).onLingeringSplashPotion(event);
        verify(event).setCancelled(true);
    }

    @Test
    public void doesNotFilterBeneficialPotions() {
        ThrownPotion potion = harmfulPotion();
        PotionEffect speed = effect(PotionEffectType.SPEED);
        when(potion.getEffects()).thenReturn(Collections.singletonList(speed));
        PotionSplashEvent splash = splash(potion, target);
        entities.onSplashPotion(splash);
        verify(splash, never()).setIntensity(target, 0);
        AreaEffectCloud cloud = cloud();
        when(cloud.getBasePotionType()).thenReturn(PotionType.AWKWARD);
        List<LivingEntity> affected = new ArrayList<>(Collections.singletonList(target));
        new ResidenceListener1_09(plugin).onLingeringEffectApply(apply(cloud, affected));
        assertEquals(Collections.singletonList(target), affected);
    }

    @Test
    public void tracesTntNativeOwnerInBothDirections() {
        TNTPrimed tnt = tnt();
        assertDamageDenied(tnt, true);
        move(shooter, 10);
        move(target, -5);
        assertDamageDenied(tnt, true);
        move(target, 10);
        assertDamageDenied(tnt, false);
    }

    @Test
    public void tntSpawnRetainsIgniterRestrictionAfterMovingAndDisconnecting() {
        TNTPrimed tnt = tnt();
        spawn(tnt, false);
        move(shooter, 10);
        homeFlags.cuboidFlags.put("pvp", true);
        when(tnt.getSource()).thenReturn(null);
        online.remove(shooter.getUniqueId());
        assertDamageDenied(tnt, true);
    }

    @Test
    public void modernDamageSourceIdentifiesCrystalDetonator() {
        EnderCrystal crystal = entity(EnderCrystal.class, EntityType.END_CRYSTAL);
        EntityDamageByEntityEvent damage = damage(crystal, target, source(crystal, shooter));
        entities.onPlayerDamageByPlayer(damage);
        verify(damage).setCancelled(true);
    }

    @Test
    public void crystalTriggerCarriesRestrictedArrowOrigin() {
        EnderCrystal crystal = entity(EnderCrystal.class, EntityType.END_CRYSTAL);
        Arrow arrow = projectile(Arrow.class);
        record(arrow);
        move(shooter, 10);
        entities.onCrystalPvpTrigger(damage(arrow, crystal, source(arrow, shooter)));
        assertDamageDenied(crystal, true);
    }

    @Test
    public void crystalTriggerTracksActualLatestDetonatorRatherThanPreviousPlayer() {
        EnderCrystal crystal = entity(EnderCrystal.class, EntityType.END_CRYSTAL);
        entities.onCrystalPvpTrigger(damage(shooter, crystal, null));
        Player detonator = player(10);
        entities.onCrystalPvpTrigger(damage(detonator, crystal, null));
        assertDamageDenied(crystal, false);
    }

    @Test
    public void inheritedRestrictionKeepsActualDetonatorIdentity() {
        EnderCrystal crystal = entity(EnderCrystal.class, EntityType.END_CRYSTAL);
        Arrow arrow = projectile(Arrow.class);
        record(arrow);
        Player detonator = player(10);
        when(arrow.getShooter()).thenReturn(detonator);
        entities.onCrystalPvpTrigger(damage(arrow, crystal, source(arrow, detonator)));
        assertSame(detonator, protection.getPlayer(crystal));
        online.remove(detonator.getUniqueId());
        assertNull(protection.getPlayer(crystal));
        assertDamageDenied(crystal, true);
    }

    @Test
    public void crystalRemembersDetonatorAfterDisconnect() {
        EnderCrystal crystal = entity(EnderCrystal.class, EntityType.END_CRYSTAL);
        entities.onCrystalPvpTrigger(damage(shooter, crystal, null));
        online.remove(shooter.getUniqueId());
        assertDamageDenied(crystal, true);
    }

    @Test
    public void minecartTriggerCarriesRestrictedArrowOrigin() {
        ExplosiveMinecart cart = entity(ExplosiveMinecart.class, EntityType.TNT_MINECART);
        Arrow arrow = projectile(Arrow.class);
        record(arrow);
        move(shooter, 10);
        DamageSource source = source(arrow, shooter);
        VehicleDamageEvent trigger = new VehicleDamageEvent(cart, source, shooter, 1);
        entities.onMinecartPvpTrigger(trigger);
        assertDamageDenied(cart, true);
    }

    @Test
    public void projectileHitTracksMinecartBeforeImmediateExplosion() {
        ExplosiveMinecart cart = entity(ExplosiveMinecart.class, EntityType.TNT_MINECART);
        Arrow arrow = projectile(Arrow.class);
        record(arrow);
        move(shooter, 10);
        ProjectileHitEvent hit = mock(ProjectileHitEvent.class);
        when(hit.getEntity()).thenReturn(arrow);
        when(hit.getHitEntity()).thenReturn(cart);
        new ResidenceListener1_13(plugin).onExplosiveProjectileHit(hit);
        // Immediate burning-arrow explosions need no VehicleDamageEvent to retain the origin.
        assertDamageDenied(cart, true);
    }

    @Test
    public void nativeCausingPlayerAppliesToOtherIndirectEntities() {
        Entity explosive = entity(Entity.class, EntityType.UNKNOWN);
        EntityDamageByEntityEvent event = damage(explosive, target, source(explosive, shooter));
        entities.onPlayerDamageByPlayer(event);
        verify(event).setCancelled(true);
    }

    @Test
    public void nativeCausingPlayerAppliesWithoutEntityDamageByEntityEvent() {
        EntityDamageEvent event = mock(EntityDamageEvent.class);
        DamageSource source = source(null, shooter);
        when(event.getEntity()).thenReturn(target);
        when(event.getDamageSource()).thenReturn(source);
        entities.onPlayerDamageByIndirectPlayer(event);
        verify(event).setCancelled(true);
    }

    @Test
    public void indirectHandlerDoesNotDoubleProcessEntityDamage() {
        EntityDamageByEntityEvent event = damage(tnt(), target, null);
        entities.onPlayerDamageByIndirectPlayer(event);
        verify(event, never()).setCancelled(true);
        entities.onPlayerDamageByPlayer(event);
        verify(event).setCancelled(true);
    }

    @Test
    public void tntPrimedByRestrictedArrowRetainsRecordAndClearsBlockTransfer() {
        Arrow arrow = projectile(Arrow.class);
        record(arrow);
        move(shooter, 10);
        prime(arrow, false);
        TNTPrimed tnt = tnt();
        spawn(tnt, false);
        assertTrue(tntBlock.getMetadata("ResidencePvpLaunch").isEmpty());
        assertDamageDenied(tnt, true);
    }

    @Test
    public void chainedTntKeepsOriginalIgniterRestriction() {
        TNTPrimed first = tnt();
        spawn(first, false);
        move(shooter, 10);
        prime(first, false);
        TNTPrimed chained = tnt();
        spawn(chained, false);
        assertDamageDenied(chained, true);
    }

    @Test
    public void cancelledPrimingClearsPendingAttribution() {
        prime(shooter, false);
        prime(shooter, true);
        assertTrue(tntBlock.getMetadata("ResidencePvpLaunch").isEmpty());
    }

    @Test
    public void cancelledTntSpawnDoesNotLeaveAttributionForAnotherEntity() {
        prime(shooter, false);
        spawn(tnt(), true);
        assertTrue(tntBlock.getMetadata("ResidencePvpLaunch").isEmpty());
        TNTPrimed unrelated = tnt();
        when(unrelated.getSource()).thenReturn(null);
        spawn(unrelated, false);
        move(target, -5);
        assertDamageDenied(unrelated, false);
    }

    @Test
    public void redstoneAndNaturalExplosionsHaveNoGuessedPlayer() {
        prime(shooter, false);
        prime(null, false);
        TNTPrimed natural = tnt();
        when(natural.getSource()).thenReturn(null);
        spawn(natural, false);
        move(target, -5);
        assertDamageDenied(natural, false);
        assertDamageDenied(entity(EnderCrystal.class, EntityType.END_CRYSTAL), false);
    }

    @Test
    public void unattributedTntStillObeysExistingTntFlag() {
        TNTPrimed natural = tnt();
        when(natural.getSource()).thenReturn(null);
        move(target, -5);
        homeFlags.cuboidFlags.put("tnt", false);
        EntityDamageByEntityEvent event = damage(natural, target, null);
        entities.onPlayerDamageByPlayer(event);
        verify(event, never()).setCancelled(true);
        entities.onEntityDamageByEntity(event);
        verify(event).setCancelled(true);
    }

    @Test
    public void cyclicTntSourceDoesNotOverflowOrInventPlayer() {
        TNTPrimed tnt = tnt();
        when(tnt.getSource()).thenReturn(tnt);
        assertNull(protection.getPlayer(tnt));
        assertDamageDenied(tnt, false);
    }

    @Test
    public void explosiveKnockbackReadsCrystalSnapshotOnPaperAndSpigot() {
        EnderCrystal crystal = entity(EnderCrystal.class, EntityType.END_CRYSTAL);
        entities.onCrystalPvpTrigger(damage(shooter, crystal, null));
        move(shooter, 10);
        EntityKnockbackByEntityEvent paper = knockback(crystal);
        new ResidenceListener1_21_8_Paper(plugin).onProjectileKnockback(paper);
        verify(paper).setCancelled(true);
        EntityKnockbackByEntityEvent spigot = knockback(crystal);
        new ResidenceListener1_21_8_Spigot(plugin).onKnockback(spigot);
        verify(spigot).setCancelled(true);
    }

    private TNTPrimed tnt() {
        TNTPrimed tnt = entity(TNTPrimed.class, EntityType.TNT);
        when(tnt.getSource()).thenReturn(shooter);
        return tnt;
    }

    private void spawn(TNTPrimed tnt, boolean cancelled) {
        EntitySpawnEvent event = mock(EntitySpawnEvent.class);
        when(event.getEntity()).thenReturn(tnt);
        when(event.isCancelled()).thenReturn(cancelled);
        entities.onTntPvpSpawn(event);
    }

    private void prime(Entity source, boolean cancelled) {
        TNTPrimeEvent.PrimeCause cause = source == null ? TNTPrimeEvent.PrimeCause.REDSTONE
                : source instanceof Player ? TNTPrimeEvent.PrimeCause.PLAYER
                : source instanceof Projectile ? TNTPrimeEvent.PrimeCause.PROJECTILE : TNTPrimeEvent.PrimeCause.EXPLOSION;
        TNTPrimeEvent event = new TNTPrimeEvent(tntBlock, cause, source, null);
        event.setCancelled(cancelled);
        new ResidenceListener1_20(plugin).onTntPvpPrime(event);
    }

    private DamageSource source(Entity direct, Entity causing) {
        DamageSource source = mock(DamageSource.class);
        when(source.getDirectEntity()).thenReturn(direct);
        when(source.getCausingEntity()).thenReturn(causing);
        return source;
    }

    private EntityDamageByEntityEvent damage(Entity source, Entity victim, DamageSource nativeSource) {
        EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
        when(event.getDamager()).thenReturn(source);
        when(event.getEntity()).thenReturn(victim);
        when(event.getDamageSource()).thenReturn(nativeSource);
        return event;
    }

    private ResidenceRaid raid(ClaimedResidence residence) {
        ConfigManager.RaidEnabled = true;
        ResidenceRaid raid = mock(ResidenceRaid.class);
        when(residence.getRaid()).thenReturn(raid);
        when(raid.isUnderRaid()).thenReturn(true);
        return raid;
    }

    private ResidencePermissions permissions(ClaimedResidence residence, FlagPermissions flags) {
        ResidencePermissions permissions = mock(ResidencePermissions.class);
        when(residence.getPermissions()).thenReturn(permissions);
        when(permissions.has(any(Flags.class), any(FlagCombo.class)))
                .thenAnswer(call -> flags.has((Flags) call.getArgument(0), (FlagCombo) call.getArgument(1)));
        when(permissions.has(any(Flags.class), anyBoolean()))
                .thenAnswer(call -> flags.has((Flags) call.getArgument(0), (boolean) call.getArgument(1)));
        return permissions;
    }

    private static FlagPermissions flags(boolean pvp) {
        FlagPermissions flags = new FlagPermissions();
        flags.cuboidFlags.put("pvp", pvp);
        return flags;
    }

    private Player player(double x) {
        Player player = entity(Player.class, EntityType.PLAYER);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        move(player, x);
        online.put(player.getUniqueId(), player);
        return player;
    }

    private void move(Player player, double x) {
        when(player.getLocation()).thenReturn(location(x));
    }

    private Location location(double x) {
        return new Location(world, x, 64, 0);
    }

    private <T extends Projectile> T projectile(Class<T> type) {
        T projectile = entity(type, EntityType.ARROW);
        when(projectile.getShooter()).thenReturn(shooter);
        return projectile;
    }

    private <T extends Entity> T entity(Class<T> type, EntityType entityType) {
        T entity = mock(type);
        when(entity.getWorld()).thenReturn(world);
        when(entity.getLocation()).thenReturn(location(10));
        when(entity.getType()).thenReturn(entityType);
        metadata(entity);
        return entity;
    }

    private void metadata(Metadatable entity) {
        Map<String, List<MetadataValue>> metadata = new HashMap<>();
        when(entity.getMetadata(anyString())).thenAnswer(call -> metadata.getOrDefault(call.getArgument(0), Collections.emptyList()));
        doAnswer(call -> {
            String key = call.getArgument(0);
            MetadataValue value = call.getArgument(1);
            List<MetadataValue> values = metadata.computeIfAbsent(key, ignored -> new ArrayList<>());
            values.removeIf(old -> old.getOwningPlugin() == value.getOwningPlugin());
            values.add(value);
            return null;
        }).when(entity).setMetadata(anyString(), any(MetadataValue.class));
        doAnswer(call -> {
            List<MetadataValue> values = metadata.get(call.getArgument(0));
            if (values != null)
                values.removeIf(value -> value.getOwningPlugin() == call.getArgument(1));
            return null;
        }).when(entity).removeMetadata(anyString(), any(Plugin.class));
    }

    private void record(Projectile projectile) {
        ProjectileLaunchEvent launch = mock(ProjectileLaunchEvent.class);
        when(launch.getEntity()).thenReturn(projectile);
        entities.onProjectilePvpLaunch(launch);
    }

    private void assertDamageDenied(Entity source, boolean denied) {
        EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
        when(event.getDamager()).thenReturn(source);
        when(event.getEntity()).thenReturn(target);
        entities.onPlayerDamageByPlayer(event);
        if (denied)
            verify(event).setCancelled(true);
        else
            verify(event, never()).setCancelled(true);
    }

    private EntityKnockbackByEntityEvent knockback(Entity source) {
        EntityKnockbackByEntityEvent event = mock(EntityKnockbackByEntityEvent.class);
        when(event.getEntity()).thenReturn(target);
        when(event.getSourceEntity()).thenReturn(source);
        return event;
    }

    private Cow animal() {
        return entity(Cow.class, EntityType.COW);
    }

    private PotionEffect effect(PotionEffectType type) {
        PotionEffect effect = mock(PotionEffect.class);
        when(effect.getType()).thenReturn(type);
        return effect;
    }

    private ThrownPotion harmfulPotion() {
        ThrownPotion potion = projectile(ThrownPotion.class);
        PotionEffect poison = effect(PotionEffectType.POISON);
        when(potion.getEffects()).thenReturn(Collections.singletonList(poison));
        return potion;
    }

    private PotionSplashEvent splash(ThrownPotion potion, LivingEntity... targets) {
        PotionSplashEvent event = mock(PotionSplashEvent.class);
        when(event.getEntity()).thenReturn(potion);
        when(event.getPotion()).thenReturn(potion);
        when(event.getAffectedEntities()).thenReturn(Arrays.asList(targets));
        return event;
    }

    private AreaEffectCloud cloud() {
        AreaEffectCloud cloud = entity(AreaEffectCloud.class, EntityType.AREA_EFFECT_CLOUD);
        when(cloud.getSource()).thenReturn(shooter);
        when(cloud.getBasePotionType()).thenReturn(PotionType.POISON);
        return cloud;
    }

    private AreaEffectCloudApplyEvent apply(AreaEffectCloud cloud, List<LivingEntity> affected) {
        AreaEffectCloudApplyEvent event = mock(AreaEffectCloudApplyEvent.class);
        when(event.getEntity()).thenReturn(cloud);
        when(event.getAffectedEntities()).thenReturn(affected);
        return event;
    }

    private static Field staticField(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
