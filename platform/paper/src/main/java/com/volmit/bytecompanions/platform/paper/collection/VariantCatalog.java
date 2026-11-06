package com.volmit.bytecompanions.platform.paper.collection;

import com.volmit.bytecompanions.api.bukkit.ItemStackProvider;
import com.volmit.bytecompanions.api.companion.Companion;
import com.volmit.bytecompanions.api.companion.CompanionModel;
import com.volmit.bytecompanions.api.companion.CompanionType;
import com.volmit.bytecompanions.api.companion.CompanionTypeRegistry;
import com.volmit.bytecompanions.api.companion.ConfigurationData;
import com.volmit.bytecompanions.api.companion.animation.AnimationChannel;
import com.volmit.bytecompanions.api.companion.animation.AnimationData;
import com.volmit.bytecompanions.api.companion.data.AppearanceData;
import com.volmit.bytecompanions.api.companion.data.OverlayData;
import com.volmit.bytecompanions.api.user.CompanionPlayer;
import com.volmit.bytecompanions.core.user.DefaultCompanionPlayer;
import com.volmit.bytecompanions.platform.paper.PaperPlugin;
import com.volmit.bytecompanions.platform.paper.companion.animations.Formation;
import com.volmit.bytecompanions.platform.paper.companion.animations.defaults.movement.FollowFormationAnimation;
import com.volmit.bytecompanions.platform.paper.companion.animations.defaults.movement.formation.GridFormation;
import com.volmit.bytecompanions.platform.paper.companion.animations.defaults.movement.formation.LineFormation;
import com.volmit.bytecompanions.platform.paper.companion.animations.defaults.movement.formation.OrbitFormation;
import com.volmit.bytecompanions.platform.paper.configuration.NodeConfigurationData;
import com.volmit.bytecompanions.platform.paper.loader.CompanionConfigurationLoader;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class VariantCatalog implements CollectionService.StyleAdapter {
    private final PaperPlugin plugin;

    public VariantCatalog(PaperPlugin plugin) {
        this.plugin = plugin;
    }

    public List<String> variants(String companionId) {
        CompanionType type = plugin.typeRegistry().getOrNull(companionId);
        if (type == null || !(type.definition().get("variants") instanceof Map<?, ?> variants)) {
            return List.of();
        }
        return variants.keySet().stream().map(Object::toString).sorted().toList();
    }

    public String name(String companionId, String variant) {
        CompanionType type = plugin.typeRegistry().getOrNull(companionId);
        return type == null ? null : type.definition().getString("variants." + variant + ".name");
    }

    public String category(CompanionType type) {
        return type.definition().getStringOr("display.category", "general");
    }

    public static void validateDefinitions(CompanionTypeRegistry registry) {
        for (CompanionType type : registry.all()) {
            Object category = type.definition().get("display.category");
            if (category != null && (!(category instanceof String name) || !name.matches("[a-z0-9_-]{1,32}"))) {
                throw new IllegalArgumentException(type.id() + ": display.category must be a lowercase category ID");
            }
            Object raw = type.definition().get("variants");
            if (raw == null) {
                continue;
            }
            if (!(raw instanceof Map<?, ?> variants) || variants.size() > 32) {
                throw new IllegalArgumentException(type.id() + ": variants must be a mapping with at most 32 entries");
            }
            for (Map.Entry<?, ?> entry : variants.entrySet()) {
                if (!(entry.getKey() instanceof String id) || !id.matches("[a-z0-9_-]{1,32}")
                        || !(entry.getValue() instanceof Map<?, ?> definition) || !wellFormed(definition)) {
                    throw new IllegalArgumentException(type.id() + ": each outfit needs a lowercase ID and either one template field"
                            + " or an equipment table with an optional name");
                }
                if (!(definition.get("template") instanceof String template)) {
                    continue;
                }
                CompanionType source = registry.getOrNull(template);
                if (source == null || source.model() != type.model()) {
                    throw new IllegalArgumentException(type.id() + ": variant " + id + " needs an existing template with the same model");
                }
            }
        }
    }

    private static boolean wellFormed(Map<?, ?> definition) {
        if (definition.size() == 1 && definition.get("template") instanceof String) {
            return true;
        }
        if (!(definition.get("equipment") instanceof Map<?, ?> equipment) || equipment.isEmpty()
                || !Set.of("equipment", "name").containsAll(definition.keySet())) {
            return false;
        }
        for (Map.Entry<?, ?> slot : equipment.entrySet()) {
            if (Arrays.stream(EquipmentSlot.values()).noneMatch(value -> value.name().equals(slot.getKey()))
                    || !(slot.getValue() instanceof Map<?, ?> item) || !(item.get("material") instanceof String)) {
                return false;
            }
        }
        Object name = definition.get("name");
        return name == null || name instanceof String text && !text.isBlank()
                && text.codePointCount(0, text.length()) <= 32 && text.codePoints().noneMatch(Character::isISOControl);
    }

    @Override
    public void validate(Player player, String id, CompanionStyle style) {
        CompanionType type = plugin.typeRegistry().getOrNull(id);
        if (type == null) {
            throw new CollectionFailure(CollectionFailure.Reason.COMPANION_UNAVAILABLE, Map.of("companion", id));
        }
        if (style.nickname() != null && (style.nickname().isBlank() || style.nickname().codePointCount(0, style.nickname().length()) > 32
                || style.nickname().codePoints().anyMatch(Character::isISOControl))) {
            throw CollectionFailure.of(CollectionFailure.Reason.NICKNAME_INVALID);
        }
        if (style.formationId() != null && !Set.of("line", "grid", "orbit").contains(style.formationId())) {
            throw new CollectionFailure(CollectionFailure.Reason.FORMATION_INVALID, Map.of("formation", style.formationId()));
        }
        if (style.variantId() != null) {
            template(type, style.variantId());
            if (!player.hasPermission("bytecompanions.variant." + id + "." + style.variantId())) {
                throw new CollectionFailure(CollectionFailure.Reason.VARIANT_LOCKED, Map.of("variant", style.variantId()));
            }
        }
    }

    @Override
    public void apply(Player player, Companion companion, CompanionStyle style) {
        validate(player, companion.type().id(), style);
        plugin.companionFactory().applyCosmetics(player, companion, template(companion.type(), style.variantId()), style.nickname());
        companion.behavior().policy(plugin.personalityPolicy());
        Formation formation = switch (style.formationId() == null ? "default" : style.formationId()) {
            case "line" -> new LineFormation(1.5);
            case "grid" -> new GridFormation(1.5, 3);
            case "orbit" -> new OrbitFormation(1.5, 0.6);
            default -> null;
        };
        companion.behavior().formation(formation == null ? null : new FollowFormationAnimation(formation, 0.15));
    }

    @Override
    public void order(Player player, List<String> companionIds) {
        CompanionPlayer state = plugin.getCompanionPlayerOrNull(player);
        if (state instanceof DefaultCompanionPlayer current) {
            current.formationOrder(companionIds);
        }
    }

    private CompanionType template(CompanionType type, String variant) {
        if (variant == null) {
            return type;
        }
        if (!variants(type.id()).contains(variant)) {
            throw new CollectionFailure(CollectionFailure.Reason.VARIANT_UNAVAILABLE, Map.of("variant", variant));
        }
        String template = type.definition().getString("variants." + variant + ".template");
        if (template == null) {
            return outfit(type, variant);
        }
        CompanionType source = plugin.typeRegistry().getOrNull(template);
        if (source == null || source.model() != type.model()) {
            throw new CollectionFailure(CollectionFailure.Reason.VARIANT_UNAVAILABLE, Map.of("variant", variant));
        }
        return source;
    }

    private static CompanionType outfit(CompanionType type, String variant) {
        if (!(type.definition() instanceof NodeConfigurationData definition)) {
            throw new CollectionFailure(CollectionFailure.Reason.VARIANT_UNAVAILABLE, Map.of("variant", variant));
        }
        Map<EquipmentSlot, ItemStackProvider> equipment = new EnumMap<>(EquipmentSlot.class);
        equipment.putAll(type.equipment());
        try {
            equipment.putAll(CompanionConfigurationLoader.outfitEquipment(definition.node().node("variants", variant)));
        } catch (IOException exception) {
            throw new CollectionFailure(CollectionFailure.Reason.VARIANT_UNAVAILABLE, Map.of("variant", variant));
        }
        return new Outfit(type, Collections.unmodifiableMap(equipment));
    }

    private record Outfit(CompanionType base, Map<EquipmentSlot, ItemStackProvider> equipment) implements CompanionType {
        @Override public String id() { return base.id(); }
        @Override public boolean exclusive() { return base.exclusive(); }
        @Override public ItemStackProvider displayItem() { return base.displayItem(); }
        @Override public AppearanceData appearance() { return base.appearance(); }
        @Override public CompanionModel model() { return base.model(); }
        @Override public ConfigurationData data() { return base.data(); }
        @Override public ConfigurationData definition() { return base.definition(); }
        @Override public Map<AnimationChannel, Map<String, AnimationData>> animations() { return base.animations(); }
        @Override public Map<String, OverlayData> overlays() { return base.overlays(); }
    }
}
