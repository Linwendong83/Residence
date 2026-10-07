package com.bekvon.bukkit.residence.protection;

import static org.mockito.Mockito.mock;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Keyed;
import org.bukkit.Registry;
import org.bukkit.potion.PotionEffectType;
import org.mockito.Answers;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;

/** Test-only registry bootstrap for the Paper API; no running server is required. */
public class PvpTestRegistryAccess implements RegistryAccess {

    private final Map<String, Registry<?>> registries = new HashMap<>();
    private final Map<String, PotionEffectType> effects = new HashMap<>();

    @Override
    @SuppressWarnings("unchecked")
    public <T extends Keyed> Registry<T> getRegistry(RegistryKey<T> key) {
        String name = key.key().value();
        return (Registry<T>) registries.computeIfAbsent(name, ignored -> mock(Registry.class, invocation -> {
            String method = invocation.getMethod().getName();
            if (name.equals("mob_effect") && (method.equals("get") || method.equals("getOrThrow"))) {
                String effect = invocation.getArgument(0).toString();
                return effects.computeIfAbsent(effect, value -> mock(PotionEffectType.class, call -> {
                    if (call.getMethod().getName().equals("getName"))
                        return value.substring(value.indexOf(':') + 1).toUpperCase(Locale.ROOT);
                    return Answers.RETURNS_DEFAULTS.answer(call);
                }));
            }
            return Answers.RETURNS_DEFAULTS.answer(invocation);
        }));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends Keyed> Registry<T> getRegistry(Class<T> type) {
        return (Registry<T>) registries.computeIfAbsent(type.getName(), ignored -> mock(Registry.class));
    }
}
