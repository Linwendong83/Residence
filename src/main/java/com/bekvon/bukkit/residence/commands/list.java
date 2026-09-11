package com.bekvon.bukkit.residence.commands;

import java.util.Arrays;

import org.bukkit.World;
import org.bukkit.command.CommandSender;

import com.bekvon.bukkit.residence.LocaleManager;
import com.bekvon.bukkit.residence.Residence;
import com.bekvon.bukkit.residence.containers.CommandAnnotation;
import com.bekvon.bukkit.residence.containers.TargetInfo;
import com.bekvon.bukkit.residence.containers.cmd;
import com.bekvon.bukkit.residence.containers.lm;
import com.bekvon.bukkit.residence.permissions.PermissionManager.ResPerm;

import net.Zrips.CMILib.Container.CMIWorld;
import net.Zrips.CMILib.FileHandler.ConfigReader;

public class list implements cmd {

    @Override
    @CommandAnnotation(simple = true, priority = 300)
    public Boolean perform(Residence plugin, CommandSender sender, String[] args, boolean resadmin) {

        int page = 1;
        World world = null;

        TargetInfo info = new TargetInfo();

        for (int i = 0; i < args.length; i++) {

            if (!info.isValid()) {
                info.defaultIfNotValid(args[i]);
                if (info.isValid()) {
                    continue;
                }
            }

            try {
                page = Integer.parseInt(args[i]);
                if (page < 1)
                    page = 1;
                continue;
            } catch (Exception ex) {
            }

            if (world == null) {
                World tempW = CMIWorld.getWorld(args[i]);
                if (tempW != null && tempW.getName().equalsIgnoreCase(args[i])) {
                    world = tempW;
                    continue;
                }
            }
        }

        info.defaultIfNotValid(sender);

        if (!info.isValid()) {
            lm.Invalid_Player.sendMessage(sender);
            return false;
        }

        if (!info.isSame(sender) && !ResPerm.command_$1_others.hasPermission(sender, this.getClass().getSimpleName())) {
            lm.General_NoCmdPermission.sendMessage(sender);
            return true;
        }

        plugin.getResidenceManager().listResidences(sender, info.getUniqueId(), page, false, false, resadmin, world);

        return true;
    }

    @Override
    public void getLocale() {
        ConfigReader c = Residence.getInstance().getLocaleManager().getLocaleConfig();
        c.get("Description", "List Residences");
        c.get("Info", Arrays.asList("&eUsage: &6/res list <player> <page> <worldName>",
                "Lists all the residences a player owns (except hidden ones).",
                "If listing your own residences, shows hidden ones as well.",
                "To list everyones residences, use /res listall."));
        LocaleManager.addTabCompleteMain(this, "[playername]", "[worldname]");
    }
}
