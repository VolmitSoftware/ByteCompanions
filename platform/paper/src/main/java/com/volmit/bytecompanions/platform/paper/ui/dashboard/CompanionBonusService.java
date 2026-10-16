package com.volmit.bytecompanions.platform.paper.ui.dashboard;

import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import com.volmit.bytecompanions.api.user.CompanionPlayer;
import com.volmit.bytecompanions.features.attributes.AttributesFeature;
import com.volmit.bytecompanions.features.attributes.data.AttributeModifierData;
import com.volmit.bytecompanions.features.buffs.BuffsFeature;
import com.volmit.bytecompanions.features.buffs.ResolvedBuff;
import com.volmit.bytecompanions.features.buffs.configuration.Companions;
import com.volmit.bytecompanions.features.buffs.configuration.Vanilla;
import com.volmit.bytecompanions.features.buffs.configuration.buff.Buff;
import com.volmit.bytecompanions.features.buffs.configuration.buff.MobLoot;
import com.volmit.bytecompanions.features.buffs.util.ApplyTo;
import com.volmit.bytecompanions.features.buffs.util.BuffKey;
import com.volmit.bytecompanions.features.buffs.util.Operation;
import com.volmit.bytecompanions.features.effects.EffectsFeature;
import com.volmit.bytecompanions.features.effects.StackMode;
import com.volmit.bytecompanions.features.effects.data.EffectResolution;
import com.volmit.bytecompanions.features.effects.data.PotionEffectData;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public final class CompanionBonusService {
    private final AttributesFeature attributes;
    private final EffectsFeature effects;
    private final BuffsFeature buffs;
    private final Predicate<Player> ownerThread;

    public CompanionBonusService(AttributesFeature attributes, EffectsFeature effects, BuffsFeature buffs) {
        this(attributes, effects, buffs, FoliaScheduler::isOwnedByCurrentRegion);
    }

    CompanionBonusService(AttributesFeature attributes, EffectsFeature effects, BuffsFeature buffs, Predicate<Player> ownerThread) {
        this.attributes = attributes;
        this.effects = effects;
        this.buffs = buffs;
        this.ownerThread = ownerThread;
    }

    public Snapshot snapshot(Player player, CompanionPlayer state, String companionId) {
        if (!this.ownerThread.test(player)) {
            throw new IllegalStateException("Companion bonuses must be read on the player owner");
        }
        if (!state.playerId().equals(player.getUniqueId())) {
            throw new IllegalArgumentException("Companion state belongs to another player");
        }
        Set<Feature> enabled = EnumSet.noneOf(Feature.class);
        if (this.attributes != null && this.attributes.active()) {
            enabled.add(Feature.ATTRIBUTES);
        }
        if (this.effects != null && this.effects.active()) {
            enabled.add(Feature.EFFECTS);
        }
        if (this.buffs != null && this.buffs.active()) {
            enabled.add(Feature.BUFFS);
        }
        boolean equipped = state.companions().containsKey(companionId);
        return new Snapshot(companionId, equipped, enabled,
                enabled.contains(Feature.ATTRIBUTES) ? this.attributes(player, state, companionId) : List.of(),
                enabled.contains(Feature.EFFECTS) ? this.effects(player, state, companionId) : List.of(),
                enabled.contains(Feature.BUFFS) ? this.buffs(player, state, companionId) : List.of());
    }

    private List<AttributeBonus> attributes(Player player, CompanionPlayer state, String companionId) {
        List<AttributeModifierData> configured = this.attributeDefinitions(player, companionId);
        boolean allowed = this.attributes.bonusesAllowed(player);
        List<AttributeBonus> bonuses = new ArrayList<>();
        for (int index = 0; index < configured.size(); index++) {
            AttributeModifierData entry = configured.get(index);
            AttributeInstance instance = player.getAttribute(entry.attribute());
            AttributeModifier applied = instance == null ? null : modifier(instance, entry.key());
            List<String> contributors = this.attributeContributors(player, state, entry);
            Status status;
            if (!state.companions().containsKey(companionId)) {
                status = Status.NOT_EQUIPPED;
            } else if (!allowed) {
                status = Status.INACTIVE;
            } else if (hasLaterKey(configured, index)) {
                status = Status.OVERRIDDEN;
            } else if (applied == null) {
                status = Status.INACTIVE;
            } else if (!matches(entry, applied)) {
                status = Status.EXTERNAL_OVERRIDE;
            } else {
                status = contributors.size() > 1 ? Status.STACKED : Status.ACTIVE;
            }
            bonuses.add(new AttributeBonus(entry.attribute().getKey(), entry.key(), entry.operation(), entry.amount(),
                    instance == null ? null : instance.getValue(), status, contributors));
        }
        return bonuses;
    }

    private List<AttributeModifierData> attributeDefinitions(Player player, String companionId) {
        return this.attributes.effectiveAttributes(player, companionId);
    }

    private List<String> attributeContributors(Player player, CompanionPlayer state, AttributeModifierData selected) {
        Set<String> contributors = new LinkedHashSet<>();
        AttributeInstance instance = player.getAttribute(selected.attribute());
        if (instance == null) {
            return List.of();
        }
        for (String companionId : state.companions().keySet()) {
            List<AttributeModifierData> entries = this.attributeDefinitions(player, companionId);
            for (int index = 0; index < entries.size(); index++) {
                AttributeModifierData entry = entries.get(index);
                if (entry.attribute().equals(selected.attribute()) && !hasLaterKey(entries, index)
                        && matches(entry, modifier(instance, entry.key()))) {
                    contributors.add(companionId);
                }
            }
        }
        return List.copyOf(contributors);
    }

    private static boolean hasLaterKey(List<AttributeModifierData> entries, int index) {
        AttributeModifierData entry = entries.get(index);
        for (int next = index + 1; next < entries.size(); next++) {
            AttributeModifierData candidate = entries.get(next);
            if (candidate.attribute().equals(entry.attribute()) && candidate.key().equals(entry.key())) {
                return true;
            }
        }
        return false;
    }

    private static AttributeModifier modifier(AttributeInstance instance, NamespacedKey key) {
        return instance.getModifiers().stream().filter(value -> value.getKey().equals(key)).findFirst().orElse(null);
    }

    private static boolean matches(AttributeModifierData entry, AttributeModifier modifier) {
        return modifier != null && Double.compare(entry.amount(), modifier.getAmount()) == 0
                && entry.operation() == modifier.getOperation();
    }

    private List<EffectBonus> effects(Player player, CompanionPlayer state, String companionId) {
        boolean allowed = this.effects.bonusesAllowed(player);
        Map<String, List<PotionEffectData>> definitions = new LinkedHashMap<>();
        for (String id : state.companions().keySet()) {
            definitions.put(id, this.effects.effectiveEffects(player, id));
        }
        definitions.computeIfAbsent(companionId, id -> this.effects.effectiveEffects(player, id));
        Map<PotionEffectType, List<PotionEffectData>> grouped = new LinkedHashMap<>();
        for (String equippedId : state.companions().keySet()) {
            for (PotionEffectData entry : definitions.get(equippedId)) {
                grouped.computeIfAbsent(entry.effectType(), ignored -> new ArrayList<>()).add(entry);
            }
        }
        List<EffectBonus> bonuses = new ArrayList<>();
        for (PotionEffectData entry : definitions.get(companionId)) {
            List<PotionEffectData> contributions = grouped.get(entry.effectType());
            EffectResolution resolution = contributions == null ? null : EffectResolution.resolve(entry.effectType(), contributions);
            PotionEffect current = player.getPotionEffect(entry.effectType());
            List<String> contributors = this.effectContributors(state, definitions, resolution);
            Status status;
            if (!state.companions().containsKey(companionId)) {
                status = Status.NOT_EQUIPPED;
            } else if (!allowed) {
                status = Status.INACTIVE;
            } else if (resolution == null || !resolution.includes(entry)) {
                status = Status.OVERRIDDEN;
            } else if (!this.effects.ownsEffect(player, entry.effectType())) {
                status = current != null && current.getAmplifier() >= resolution.effect().amplifier()
                        ? Status.EXTERNAL_OVERRIDE : Status.INACTIVE;
            } else if (current == null || current.getAmplifier() != resolution.effect().amplifier()) {
                status = Status.INACTIVE;
            } else {
                status = contributors.size() > 1 ? Status.STACKED : Status.ACTIVE;
            }
            bonuses.add(new EffectBonus(entry.effectType().getKey(), entry.amplifier(), entry.stackMode(), entry.priority(),
                    resolution == null ? null : resolution.effect().amplifier(), current == null ? null : current.getAmplifier(),
                    status, contributors));
        }
        return bonuses;
    }

    private List<String> effectContributors(CompanionPlayer state, Map<String, List<PotionEffectData>> definitions, EffectResolution resolution) {
        if (resolution == null) {
            return List.of();
        }
        List<String> contributors = new ArrayList<>();
        for (String companionId : state.companions().keySet()) {
            if (definitions.get(companionId).stream().anyMatch(resolution::includes)) {
                contributors.add(companionId);
            }
        }
        return contributors;
    }

    private List<BuffBonus> buffs(Player player, CompanionPlayer state, String companionId) {
        Companions configured = this.buffs.companions(companionId);
        if (configured == null) {
            return List.of();
        }
        List<BuffBonus> bonuses = new ArrayList<>();
        boolean allowed = this.buffs.buffService().bonusesAllowed(player);
        for (BuffKey key : BuffKey.values()) {
            Buff entry = buff(configured.vanilla(), key);
            ResolvedBuff winner = this.buffs.playerBuffCache().get(player, key).orElse(null);
            long remaining = winner == null ? 0 : this.buffs.cooldownManager()
                    .remainingMillis(player.getEntityId(), key, winner.buff().cooldown());
            Status status;
            if (!entry.enabled()) {
                status = Status.DISABLED;
            } else if (!state.companions().containsKey(companionId)) {
                status = Status.NOT_EQUIPPED;
            } else if (!allowed || winner == null) {
                status = Status.INACTIVE;
            } else if (!winner.companionTypeId().equals(companionId)) {
                status = Status.OVERRIDDEN;
            } else {
                status = remaining > 0 ? Status.COOLDOWN : Status.ACTIVE;
            }
            Set<BuffKey> excludedSources = EnumSet.noneOf(BuffKey.class);
            if (key == BuffKey.EXPERIENCE) {
                for (BuffKey source : List.of(BuffKey.EXPERIENCE_BLOCK, BuffKey.EXPERIENCE_MOB,
                        BuffKey.EXPERIENCE_FISHING, BuffKey.EXPERIENCE_FURNACE)) {
                    if (this.buffs.buffService().isActive(player, source)) {
                        excludedSources.add(source);
                    }
                }
            }
            bonuses.add(new BuffBonus(key, entry.operation(), this.buffs.buffService().value(player, companionId, key, entry),
                    entry.rawPriority() == null ? configured.defaultPriority() : entry.rawPriority(), entry.cooldown(),
                    winner == null ? null : winner.companionTypeId(), winner == null ? null : winner.priority(),
                    winner == null ? null : winner.buff().operation(), winner == null ? null : this.buffs.buffService().value(player, winner.companionTypeId(), key, winner.buff()),
                    remaining, entry instanceof MobLoot loot ? loot.applyTo() : null, excludedSources, status,
                    this.buffs.buffService().boosterPercent(player, key)));
        }
        return bonuses;
    }

    private static Buff buff(Vanilla vanilla, BuffKey key) {
        return switch (key) {
            case EXPERIENCE -> vanilla.experience();
            case EXPERIENCE_MOB -> vanilla.experienceMob();
            case EXPERIENCE_BLOCK -> vanilla.experienceBlock();
            case EXPERIENCE_FURNACE -> vanilla.experienceFurnace();
            case EXPERIENCE_FISHING -> vanilla.experienceFishing();
            case MOB_LOOT -> vanilla.mobLoot();
            case BLOCK_DROPS -> vanilla.blockDrops();
            case FISHING_LOOT -> vanilla.fishingLoot();
        };
    }

    public enum Feature { ATTRIBUTES, EFFECTS, BUFFS }

    public enum Status { ACTIVE, STACKED, OVERRIDDEN, EXTERNAL_OVERRIDE, NOT_EQUIPPED, DISABLED, INACTIVE, COOLDOWN }

    public record Snapshot(String companionId, boolean equipped, Set<Feature> enabledFeatures,
                           List<AttributeBonus> attributes, List<EffectBonus> effects, List<BuffBonus> buffs) {
        public Snapshot {
            enabledFeatures = Set.copyOf(enabledFeatures);
            attributes = List.copyOf(attributes);
            effects = List.copyOf(effects);
            buffs = List.copyOf(buffs);
        }
    }

    public record AttributeBonus(NamespacedKey attribute, NamespacedKey modifier, AttributeModifier.Operation operation,
                                 double amount, Double currentTotal, Status status, List<String> contributors) {
        public AttributeBonus {
            contributors = List.copyOf(contributors);
        }
    }

    public record EffectBonus(NamespacedKey effect, int amplifier, StackMode stackMode, int priority,
                              Integer resolvedAmplifier, Integer currentAmplifier, Status status, List<String> contributors) {
        public EffectBonus {
            contributors = List.copyOf(contributors);
        }
    }

    public record BuffBonus(BuffKey key, Operation operation, double value, int priority, long cooldownTicks,
                            String winnerId, Integer winnerPriority, Operation winnerOperation, Double winnerValue,
                            long remainingCooldownMillis, ApplyTo applyTo, Set<BuffKey> excludedSources, Status status, double boosterPercent) {
        public BuffBonus {
            excludedSources = Set.copyOf(excludedSources);
        }
    }
}
