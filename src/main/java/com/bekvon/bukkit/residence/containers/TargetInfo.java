package com.bekvon.bukkit.residence.containers;

import java.util.UUID;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class TargetInfo {

    String targetName = null;
    UUID targetUuid = null;

    public TargetInfo() {
    }

    public TargetInfo defaultIfNotValid(String name) {

        if (isValid())
            return this;

        ResidencePlayer resP = ResidencePlayer.get(name);

        if (resP == null)
            return this;

        targetName = resP.getName();
        targetUuid = resP.getUniqueId();

        return this;
    }

    public boolean isSame(CommandSender sender) {

        if (!isValid())
            return false;

        if (sender instanceof Player)
            return ((Player) sender).getUniqueId().equals(targetUuid);

        return false;
    }

    public TargetInfo defaultIfNotValid(CommandSender sender) {

        if (isValid())
            return this;

        if (sender instanceof Player) {
            ResidencePlayer resP = ResidencePlayer.get((Player) sender);
            targetName = resP.getName();
            targetUuid = resP.getUniqueId();
            return this;
        }

        return this;
    }

    public boolean isValid() {
        return targetName != null && targetUuid != null;
    }

    public boolean isPartiallyValid() {
        return targetName != null || targetUuid != null;
    }

    public UUID getUniqueId() {
        return targetUuid;
    }
}
