package com.volmit.bytecompanions.features.effects;

import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntSupplier;
import java.util.function.ToIntFunction;

public final class OwnedPotionEffects {
    private final Map<UUID, Map<PotionEffectType, AppliedEffect>> players = new ConcurrentHashMap<>();
    private final Map<UUID, Map<PotionEffectType, PotionEffect>> replacements = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> mutations = new ConcurrentHashMap<>();
    private final ToIntFunction<Player> ticks;

    public OwnedPotionEffects(IntSupplier ticks) {
        this(player -> ticks.getAsInt());
    }

    public OwnedPotionEffects(ToIntFunction<Player> ticks) {
        this.ticks = ticks;
    }

    public boolean changing(Player player) {
        return this.mutations.containsKey(player.getUniqueId());
    }

    public boolean owns(Player player, PotionEffectType type) {
        Map<PotionEffectType, AppliedEffect> effects = this.players.get(player.getUniqueId());
        AppliedEffect effect = effects == null ? null : effects.get(type);
        return effect != null && effect.owns(player.getPotionEffect(type));
    }

    public boolean externalChange(Player player, PotionEffectType type) {
        Map<PotionEffectType, AppliedEffect> effects = this.players.get(player.getUniqueId());
        if (effects != null) {
            AppliedEffect previous = effects.remove(type);
            if (effects.isEmpty()) {
                this.players.remove(player.getUniqueId());
            }
            return previous != null;
        }
        return false;
    }

    public boolean rememberDisplaced(Player player, PotionEffect external) {
        if (external == null) {
            return false;
        }
        Map<PotionEffectType, AppliedEffect> effects = this.players.get(player.getUniqueId());
        AppliedEffect owned = effects == null ? null : effects.get(external.getType());
        if (owned == null || !owned.owns(player.getPotionEffect(external.getType()))
                || external.getAmplifier() > owned.effect().getAmplifier()
                || external.getAmplifier() == owned.effect().getAmplifier() && external.isInfinite()) {
            return false;
        }
        int tick = this.ticks.applyAsInt(player);
        PotionEffect displaced = owned.displaced();
        if (displaced != null && (displaced.isInfinite() || displaced.getDuration() > tick - owned.tick())) {
            if (displaced.getAmplifier() > external.getAmplifier()) {
                return true;
            }
            if (displaced.getAmplifier() == external.getAmplifier()
                    && (displaced.isInfinite() || !external.isInfinite()
                    && displaced.getDuration() - (tick - owned.tick()) >= external.getDuration())) {
                return true;
            }
        }
        effects.put(external.getType(), new AppliedEffect(owned.effect(), external, tick));
        return true;
    }

    public void discardHiddenEffect(Player player, PotionEffect replacement) {
        Map<PotionEffectType, PotionEffect> pending = this.replacements.get(player.getUniqueId());
        if (pending != null) {
            pending.remove(replacement.getType());
            if (pending.isEmpty()) {
                this.replacements.remove(player.getUniqueId());
            }
        }
        PotionEffect current = player.getPotionEffect(replacement.getType());
        if (current == null || current.getAmplifier() < replacement.getAmplifier()
                || this.players.getOrDefault(player.getUniqueId(), Map.of()).containsKey(replacement.getType())) {
            return;
        }
        this.mutate(player, () -> {
            player.removePotionEffect(current.getType());
            if (player.getPotionEffect(current.getType()) == null) {
                player.addPotionEffect(current);
            }
        });
    }

    public void trackReplacement(Player player, PotionEffect replacement) {
        this.replacements.computeIfAbsent(player.getUniqueId(), key -> new HashMap<>())
                .put(replacement.getType(), replacement);
    }

    public void apply(Player player, PotionEffect desired) {
        PotionEffectType type = desired.getType();
        Map<PotionEffectType, AppliedEffect> effects = this.players.get(player.getUniqueId());
        AppliedEffect previous = effects == null ? null : effects.get(type);
        PotionEffect current = player.getPotionEffect(type);
        if (previous != null && previous.owns(current)) {
            if (current.equals(desired)) {
                return;
            }
            this.remove(player, type);
            current = player.getPotionEffect(type);
        } else if (previous != null) {
            this.externalChange(player, type);
        }
        if (current != null && current.getAmplifier() >= desired.getAmplifier()) {
            return;
        }
        PotionEffect displaced = current;
        this.mutate(player, () -> {
            if (player.addPotionEffect(desired)) {
                this.players.computeIfAbsent(player.getUniqueId(), key -> new HashMap<>())
                        .put(type, new AppliedEffect(desired, displaced, this.ticks.applyAsInt(player)));
            }
        });
    }

    public void remove(Player player, PotionEffectType type) {
        Map<PotionEffectType, AppliedEffect> effects = this.players.get(player.getUniqueId());
        AppliedEffect owned = effects == null ? null : effects.remove(type);
        if (effects != null && effects.isEmpty()) {
            this.players.remove(player.getUniqueId());
        }
        if (owned == null || !owned.owns(player.getPotionEffect(type))) {
            return;
        }
        this.mutate(player, () -> {
            player.removePotionEffect(type);
            if (player.getPotionEffect(type) != null || owned.displaced() == null) {
                return;
            }
            PotionEffect displaced = owned.displaced();
            int duration = displaced.isInfinite() ? PotionEffect.INFINITE_DURATION
                    : displaced.getDuration() - Math.max(0, this.ticks.applyAsInt(player) - owned.tick());
            if (duration > 0 || displaced.isInfinite()) {
                player.addPotionEffect(new PotionEffect(type, duration, displaced.getAmplifier(),
                        displaced.isAmbient(), displaced.hasParticles(), displaced.hasIcon()));
            }
        });
    }

    public void removeAll(Player player) {
        Map<PotionEffectType, PotionEffect> pending = this.replacements.get(player.getUniqueId());
        if (pending != null) {
            for (PotionEffect replacement : pending.values().toArray(PotionEffect[]::new)) {
                this.discardHiddenEffect(player, replacement);
            }
        }
        Map<PotionEffectType, AppliedEffect> effects = this.players.get(player.getUniqueId());
        if (effects != null) {
            for (PotionEffectType type : effects.keySet().toArray(PotionEffectType[]::new)) {
                this.remove(player, type);
            }
        }
    }

    private void mutate(Player player, Runnable mutation) {
        UUID playerId = player.getUniqueId();
        this.mutations.merge(playerId, 1, Integer::sum);
        try {
            mutation.run();
        } finally {
            this.mutations.computeIfPresent(playerId, (key, count) -> count == 1 ? null : count - 1);
        }
    }

    private record AppliedEffect(PotionEffect effect, PotionEffect displaced, int tick) {
        private boolean owns(PotionEffect current) {
            return current != null && current.getType().equals(this.effect.getType())
                    && current.isInfinite() && current.getAmplifier() == this.effect.getAmplifier();
        }
    }
}
