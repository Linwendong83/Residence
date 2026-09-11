package com.bekvon.bukkit.residence.listenersCache;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.bekvon.bukkit.residence.containers.Flags;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/*
Should be usable in 1.16+ versions
Suppress frequent duplicate Flag-Deny messages
*/
public class DenyMessageCache {

    private static Cache<DenyMessageKey, Boolean> DENY_MESSAGE_CACHE;

    public static void reloadDenyMessageCache(int expireSeconds) {
        DENY_MESSAGE_CACHE = CacheBuilder.newBuilder()
                .expireAfterWrite(expireSeconds, TimeUnit.SECONDS)
                .maximumSize(10_000)
                .build();
    }

    private DenyMessageCache() {
    }

    public static boolean shouldSendDenyMessage(@NotNull Player player, @NotNull Flags flag) {
        Cache<DenyMessageKey, Boolean> cache = DENY_MESSAGE_CACHE;
        if (cache == null) {
            return true;
        }
        DenyMessageKey key = new DenyMessageKey(player, flag);
        if (cache.getIfPresent(key) != null) {
            return false;
        }
        cache.put(key, true);
        return true;
    }

    private static final class DenyMessageKey {

        private final UUID playerUuid;
        private final Flags flag;

        private DenyMessageKey(@NotNull Player player, @NotNull Flags flag) {
            this.playerUuid = player.getUniqueId();
            this.flag = flag;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof DenyMessageKey)) {
                return false;
            }
            DenyMessageKey other = (DenyMessageKey) obj;
            return playerUuid.equals(other.playerUuid) && flag.equals(other.flag);
        }

        @Override
        public int hashCode() {
            int result = playerUuid.hashCode();
            result = 31 * result + flag.hashCode();
            return result;
        }
    }
}
