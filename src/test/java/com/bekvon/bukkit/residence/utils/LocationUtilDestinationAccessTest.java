package com.bekvon.bukkit.residence.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.bukkit.entity.Player;
import org.junit.Before;
import org.junit.Test;

import com.bekvon.bukkit.residence.containers.Flags;
import com.bekvon.bukkit.residence.protection.ClaimedResidence;
import com.bekvon.bukkit.residence.protection.FlagPermissions.FlagCombo;
import com.bekvon.bukkit.residence.protection.ResidencePermissions;

public class LocationUtilDestinationAccessTest {

    private Player player;
    private ClaimedResidence target;
    private ResidencePermissions permissions;

    @Before
    public void setUp() {
        player = mock(Player.class);
        target = mock(ClaimedResidence.class);
        permissions = mock(ResidencePermissions.class);
        when(target.getPermissions()).thenReturn(permissions);
    }

    @Test
    public void allowsUnclaimedDestination() {
        assertTrue(canUse(null, null, false, false, false, false));
    }

    @Test
    public void rejectsClaimedDestinationWithoutPlayer() {
        assertFalse(canUse(null, target, false, false, false, false));
    }

    @Test
    public void allowsDestinationWhenMoveAndTeleportAreAllowed() {
        assertTrue(canUse(player, target, false, false, false, false));
    }

    @Test
    public void rejectsDestinationWhenTeleportIsDenied() {
        deny(Flags.tp);
        assertFalse(canUse(player, target, false, false, false, false));
    }

    @Test
    public void rejectsDestinationWhenMovementIsDenied() {
        deny(Flags.move);
        assertFalse(canUse(player, target, false, false, false, false));
    }

    @Test
    public void rejectsDestinationWhenMoveAndTeleportAreDenied() {
        deny(Flags.tp);
        deny(Flags.move);
        assertFalse(canUse(player, target, false, false, false, false));
    }

    @Test
    public void allowsOwnerWhenMoveAndTeleportAreDenied() {
        deny(Flags.tp);
        deny(Flags.move);
        when(target.isOwner(player)).thenReturn(true);
        assertTrue(canUse(player, target, false, false, false, false));
    }

    @Test
    public void allowsResidenceAdminWhenMoveAndTeleportAreDenied() {
        deny(Flags.tp);
        deny(Flags.move);
        assertTrue(canUse(player, target, true, false, false, false));
    }

    @Test
    public void allowsTeleportBypassWhenMoveAndTeleportAreDenied() {
        deny(Flags.tp);
        deny(Flags.move);
        assertTrue(canUse(player, target, false, true, false, false));
    }

    @Test
    public void requiresMatchingAdminPermissionForEachDeniedFlag() {
        deny(Flags.tp);
        deny(Flags.move);

        assertFalse(canUse(player, target, false, false, true, false));
        assertFalse(canUse(player, target, false, false, false, true));
        assertTrue(canUse(player, target, false, false, true, true));
    }

    private void deny(Flags flag) {
        when(permissions.playerHas(player, flag, FlagCombo.OnlyFalse)).thenReturn(true);
    }

    private static boolean canUse(Player player, ClaimedResidence target,
            boolean residenceAdmin, boolean bypassTp, boolean adminTp, boolean adminMove) {
        return LocationUtil.canUseAsOutsideDestination(
                player, target, residenceAdmin, bypassTp, adminTp, adminMove);
    }
}
