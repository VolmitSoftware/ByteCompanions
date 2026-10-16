package com.volmit.bytecompanions.platform.paper.collection;

import com.volmit.bytecompanions.api.companion.Companion;
import com.volmit.bytecompanions.api.companion.CompanionType;
import com.volmit.bytecompanions.api.companion.CompanionTypeRegistry;
import com.volmit.bytecompanions.api.user.CompanionPlayer;
import com.volmit.bytecompanions.core.user.DefaultCompanionPlayer;
import com.volmit.bytecompanions.platform.paper.PaperPlugin;
import com.volmit.bytecompanions.platform.paper.companion.animations.Formation;
import com.volmit.bytecompanions.platform.paper.companion.animations.defaults.movement.FollowFormationAnimation;
import com.volmit.bytecompanions.platform.paper.companion.animations.defaults.movement.formation.GridFormation;
import com.volmit.bytecompanions.platform.paper.companion.animations.defaults.movement.formation.LineFormation;
import com.volmit.bytecompanions.platform.paper.companion.animations.defaults.movement.formation.OrbitFormation;
import org.bukkit.entity.Player;

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
                        || !(entry.getValue() instanceof Map<?, ?> definition)
                        || definition.size() != 1 || !(definition.get("template") instanceof String template)) {
                    throw new IllegalArgumentException(type.id() + ": each variant requires a lowercase ID and one template field");
                }
                CompanionType source = registry.getOrNull(template);
                if (source == null || source.model() != type.model()) {
                    throw new IllegalArgumentException(type.id() + ": variant " + id + " needs an existing template with the same model");
                }
            }
        }
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
        CompanionType source = plugin.typeRegistry().getOrNull(type.definition().getString("variants." + variant + ".template"));
        if (source == null || source.model() != type.model()) {
            throw new CollectionFailure(CollectionFailure.Reason.VARIANT_UNAVAILABLE, Map.of("variant", variant));
        }
        return source;
    }
}
