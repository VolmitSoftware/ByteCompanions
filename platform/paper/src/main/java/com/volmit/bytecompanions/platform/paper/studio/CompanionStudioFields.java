package com.volmit.bytecompanions.platform.paper.studio;

import com.volmit.bytecompanions.features.particles.effect.ParticleEffectCatalog;
import org.bukkit.Material;
import org.spongepowered.configurate.ConfigurationNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class CompanionStudioFields {
    private static final List<String> SLOTS = List.of("HEAD", "CHEST", "LEGS", "FEET", "HAND", "OFF_HAND");
    private static final List<String> BUFFS = List.of("experience", "experience-block", "experience-mob",
            "experience-furnace", "experience-fishing", "mob-loot", "block-drops", "fishing-loot");
    private static final Set<String> ANIMATION_DECIMALS = Set.of("base-interpolation", "desired-distance", "min-calm-distance",
            "max-follow-distance", "height", "speed", "interpolation", "head-height", "animation-speed", "spacing", "radius",
            "angular-speed", "dead-zone", "max-interpolation", "yaw-amplitude", "pitch-amplitude", "frequency", "smooth-speed",
            "max-pitch", "min-pitch", "dead-zone-vertical", "dead-zone-horizontal", "smoothing", "movement-threshold",
            "max-speed-factor", "base-swing-amplitude", "speed-swing-boost", "walk-blend-smoothing", "stop-damping");

    private CompanionStudioFields() {
    }

    public static List<Field> fields(CompanionDraft draft, Category category) throws IOException {
        List<Field> fields = new ArrayList<>();
        CompanionDraft.Section section = category.section();
        ConfigurationNode root = draft.node(section);
        switch (category) {
            case APPEARANCE -> {
                add(fields, section, root, List.of("model"), "FULL_BODY", List.of("FULL_BODY", "HEAD"));
                add(fields, section, root, List.of("exclusive"), false);
                add(fields, section, root, List.of("display", "item", "material"), "PAPER");
                add(fields, section, root, List.of("display", "item", "name"), draft.id());
                add(fields, section, root, List.of("appearance", "custom-name"), "Example Custom Name");
                add(fields, section, root, List.of("appearance", "scale"), -1.0);
                for (String key : List.of("small", "visible", "show-arms", "show-base-plate")) {
                    add(fields, section, root, List.of("appearance", key), true);
                }
                for (String key : List.of("marker", "custom-name-visible")) {
                    add(fields, section, root, List.of("appearance", key), false);
                }
                flatten(fields, section, root.node("display", "item", "lore"), List.of("display", "item", "lore"));
            }
            case EQUIPMENT -> {
                for (String slot : SLOTS) {
                    add(fields, section, root, List.of("equipment", slot, "material"), "AIR");
                    add(fields, section, root, List.of("equipment", slot, "custom-model-data"), 0);
                    String material = root.node("equipment", slot, "material").getString("");
                    if (material.startsWith("LEATHER_")) {
                        add(fields, section, root, List.of("equipment", slot, "rgb-color"), "160,101,64");
                    }
                    flatten(fields, section, root.node("equipment", slot), List.of("equipment", slot));
                }
            }
            case MOVEMENT -> {
                for (String channel : List.of("movement", "rotation", "pose", "tracking", "full-body", "head-only")) {
                    flatten(fields, section, root.node("animations", channel), List.of("animations", channel));
                }
                List<? extends ConfigurationNode> movement = root.node("animations", "movement").childrenList();
                for (int index = 0; index < movement.size(); index++) {
                    ConfigurationNode animation = movement.get(index);
                    if (!animation.node("id").getString("").equals("follow_formation")) {
                        continue;
                    }
                    String formation = animation.node("formation", "type").getString("grid").toLowerCase(Locale.ROOT);
                    add(fields, section, root, List.of("animations", "movement", index, "formation", "type"),
                            formation, List.of("grid", "line", "orbit"));
                    if (formation.equals("grid") || formation.equals("line")) {
                        add(fields, section, root, List.of("animations", "movement", index, "formation", formation, "spacing"), 1.5);
                    }
                    if (formation.equals("grid")) {
                        add(fields, section, root, List.of("animations", "movement", index, "formation", formation, "columns"), 3);
                    } else if (formation.equals("orbit")) {
                        add(fields, section, root, List.of("animations", "movement", index, "formation", formation, "radius"), 1.0);
                        add(fields, section, root, List.of("animations", "movement", index, "formation", formation, "angular-speed"), 1.0);
                    }
                }
            }
            case PARTICLES -> flatten(fields, section, root.node("animations", "post-tracking"), List.of("animations", "post-tracking"));
            case ATTRIBUTES, EFFECTS -> flatten(fields, section, root, List.of());
            case BUFFS -> {
                add(fields, section, root, List.of("default-priority"), 0);
                for (String buff : BUFFS) {
                    boolean enabled = root.raw() != null;
                    add(fields, section, root, List.of("vanilla", buff, "enabled"), enabled);
                    add(fields, section, root, List.of("vanilla", buff, "operation"), "SUM", List.of("SUM", "MULTIPLY", "ADD_PERCENT"));
                    add(fields, section, root, List.of("vanilla", buff, "value"), 0.0);
                    add(fields, section, root, List.of("vanilla", buff, "cooldown"), 0L);
                }
                flatten(fields, section, root, List.of());
            }
        }
        return List.copyOf(fields);
    }

    public static CompanionDraft apply(CompanionDraft draft, Field field, String input) throws IOException {
        Object value = field.parse(input);
        CompanionDraft candidate = draft;
        if (field.section() == CompanionDraft.Section.BUFFS && draft.node(field.section()).raw() == null) {
            candidate = candidate.withValue(field.section(), List.of(), emptyBuffs());
        }
        return candidate.withValue(field.section(), field.path(), value);
    }

    public static List<String> choices(Category category) {
        return switch (category) {
            case MOVEMENT -> List.of("follow_formation", "hover", "look_at_owner", "walk_swing");
            case PARTICLES -> List.copyOf(ParticleEffectCatalog.factories().keySet());
            case ATTRIBUTES -> List.of("armor", "attack_damage", "movement_speed", "max_health");
            case EFFECTS -> List.of("SPEED", "NIGHT_VISION", "HASTE", "JUMP_BOOST", "WATER_BREATHING", "RESISTANCE");
            case BUFFS -> BUFFS;
            default -> List.of();
        };
    }

    public static CompanionDraft preset(CompanionDraft draft, Category category, String choice) throws IOException {
        if (!choices(category).contains(choice)) {
            throw new IllegalArgumentException("Unknown companion studio preset: " + choice);
        }
        if (category == Category.PARTICLES) {
            return toggleAnimation(draft, "post-tracking", choice, Map.of("id", choice));
        }
        if (category == Category.MOVEMENT) {
            return switch (choice) {
                case "follow_formation" -> toggleAnimation(draft, "movement", choice,
                        Map.of("id", choice, "base-interpolation", 0.15,
                                "formation", Map.of("type", "grid", "grid", Map.of("spacing", 1.5, "columns", 3))));
                case "hover" -> toggleAnimation(draft, "movement", choice,
                        Map.of("id", choice, "height", 0.2, "speed", 0.08, "interpolation", 0.2));
                case "look_at_owner" -> toggleAnimation(draft, "rotation", choice, Map.of("id", choice, "head-height", 1.0));
                case "walk_swing" -> toggleAnimation(draft, "pose", choice, Map.of("id", choice, "animation-speed", 0.25));
                default -> throw new IllegalArgumentException("Unknown movement preset");
            };
        }
        if (category == Category.BUFFS) {
            ConfigurationNode root = draft.node(CompanionDraft.Section.BUFFS);
            CompanionDraft candidate = root.raw() == null ? draft.withValue(CompanionDraft.Section.BUFFS, List.of(), emptyBuffs()) : draft;
            boolean enabled = root.raw() != null && root.node("vanilla", choice, "enabled").getBoolean(true);
            return candidate.withValue(CompanionDraft.Section.BUFFS, List.of("vanilla", choice, "enabled"), !enabled);
        }
        CompanionDraft.Section section = category.section();
        List<Object> entries = new ArrayList<>();
        String key = category == Category.ATTRIBUTES ? "attribute" : "effect";
        boolean removed = false;
        for (ConfigurationNode entry : draft.node(section).childrenList()) {
            if (choice.equalsIgnoreCase(entry.node(key).getString(""))) {
                removed = true;
            } else {
                entries.add(entry.raw());
            }
        }
        if (!removed) {
            if (category == Category.ATTRIBUTES) {
                entries.add(Map.of("attribute", choice, "amount", 0.0, "operation", "ADD_NUMBER"));
            } else {
                entries.add(Map.of("effect", choice, "amplifier", 0, "priority", 0, "stack-mode", "HIGHEST"));
            }
        }
        return draft.withValue(section, List.of(), entries);
    }

    public static boolean selected(CompanionDraft draft, Category category, String choice) throws IOException {
        if (!choices(category).contains(choice)) {
            return false;
        }
        if (category == Category.BUFFS) {
            ConfigurationNode root = draft.node(CompanionDraft.Section.BUFFS);
            return root.raw() != null && root.node("vanilla", choice, "enabled").getBoolean(true);
        }
        if (category == Category.MOVEMENT || category == Category.PARTICLES) {
            String channel = category == Category.PARTICLES ? "post-tracking" : switch (choice) {
                case "look_at_owner" -> "rotation";
                case "walk_swing" -> "pose";
                default -> "movement";
            };
            return draft.node(CompanionDraft.Section.COMPANION).node("animations", channel).childrenList().stream()
                    .anyMatch(node -> choice.equals(node.node("id").getString()));
        }
        String key = category == Category.ATTRIBUTES ? "attribute" : "effect";
        return draft.node(category.section()).childrenList().stream()
                .anyMatch(node -> choice.equalsIgnoreCase(node.node(key).getString("")));
    }

    public static CompanionDraft removeEntry(CompanionDraft draft, Category category, int index) throws IOException {
        if (category != Category.ATTRIBUTES && category != Category.EFFECTS) {
            throw new IllegalArgumentException("Only attribute and potion rows can be removed by index");
        }
        List<Object> entries = new ArrayList<>();
        for (ConfigurationNode entry : draft.node(category.section()).childrenList()) {
            entries.add(entry.raw());
        }
        entries.remove(index);
        return draft.withValue(category.section(), List.of(), entries);
    }

    private static CompanionDraft toggleAnimation(CompanionDraft draft, String channel, String id, Map<String, Object> preset) throws IOException {
        List<Object> entries = new ArrayList<>();
        boolean removed = false;
        for (ConfigurationNode entry : draft.node(CompanionDraft.Section.COMPANION).node("animations", channel).childrenList()) {
            if (id.equals(entry.node("id").getString())) {
                removed = true;
            } else {
                entries.add(entry.raw());
            }
        }
        if (!removed) {
            entries.add(preset);
        }
        return draft.withValue(CompanionDraft.Section.COMPANION, List.of("animations", channel), entries);
    }

    private static Map<String, Object> emptyBuffs() {
        Map<String, Object> vanilla = new LinkedHashMap<>();
        for (String buff : BUFFS) {
            vanilla.put(buff, Map.of("enabled", false));
        }
        return Map.of("default-priority", 0, "vanilla", vanilla);
    }

    private static void flatten(List<Field> fields, CompanionDraft.Section section, ConfigurationNode node, List<Object> path) {
        if (node.isMap()) {
            for (Map.Entry<Object, ? extends ConfigurationNode> entry : node.childrenMap().entrySet()) {
                List<Object> child = new ArrayList<>(path);
                child.add(entry.getKey());
                flatten(fields, section, entry.getValue(), child);
            }
        } else if (node.isList()) {
            List<? extends ConfigurationNode> entries = node.childrenList();
            for (int index = 0; index < entries.size(); index++) {
                List<Object> child = new ArrayList<>(path);
                child.add(index);
                flatten(fields, section, entries.get(index), child);
            }
        } else if (!node.virtual() && !path.isEmpty() && !path.getLast().equals("id")) {
            Object value = node.rawScalar();
            if (value instanceof String || value instanceof Number || value instanceof Boolean) {
                append(fields, new Field(section, path, value, label(path), fieldChoices(section, path)));
            }
        }
    }

    private static List<String> fieldChoices(CompanionDraft.Section section, List<Object> path) {
        String leaf = String.valueOf(path.getLast());
        if (section == CompanionDraft.Section.COMPANION && leaf.equals("type") && path.size() >= 2
                && path.get(path.size() - 2).equals("formation")) {
            return List.of("grid", "line", "orbit");
        }
        if (leaf.equals("operation")) {
            return section == CompanionDraft.Section.ATTRIBUTES
                    ? List.of("ADD_NUMBER", "ADD_SCALAR", "MULTIPLY_SCALAR_1") : List.of("SUM", "MULTIPLY", "ADD_PERCENT");
        }
        return leaf.equals("stack-mode") ? List.of("SUM", "HIGHEST") : List.of();
    }

    private static void add(List<Field> fields, CompanionDraft.Section section, ConfigurationNode root, List<Object> path, Object fallback) {
        add(fields, section, root, path, fallback, List.of());
    }

    private static void add(List<Field> fields, CompanionDraft.Section section, ConfigurationNode root, List<Object> path,
                            Object fallback, List<String> choices) {
        Object value = root.node(path.toArray()).rawScalar();
        append(fields, new Field(section, path, value == null ? fallback : value, label(path), choices));
    }

    private static void append(List<Field> fields, Field field) {
        if (fields.stream().noneMatch(existing -> existing.section() == field.section() && existing.path().equals(field.path()))) {
            fields.add(field);
        }
    }

    private static String label(List<Object> path) {
        return String.join(" › ", path.stream().map(part -> part instanceof Integer index
                ? Integer.toString(index + 1) : part.toString().replace('-', ' ').replace('_', ' ')).toList());
    }

    private static boolean decimalField(CompanionDraft.Section section, List<Object> path) {
        String leaf = String.valueOf(path.getLast());
        return section == CompanionDraft.Section.ATTRIBUTES && leaf.equals("amount")
                || section == CompanionDraft.Section.BUFFS && leaf.equals("value")
                || section == CompanionDraft.Section.COMPANION && (path.equals(List.of("appearance", "scale"))
                || path.getFirst().equals("animations") && ANIMATION_DECIMALS.contains(leaf));
    }

    private static boolean integerField(CompanionDraft.Section section, List<Object> path) {
        String leaf = String.valueOf(path.getLast());
        if (section == CompanionDraft.Section.EFFECTS) {
            return leaf.equals("amplifier") || leaf.equals("priority");
        }
        if (section == CompanionDraft.Section.BUFFS) {
            return leaf.equals("cooldown") || leaf.equals("priority") || leaf.equals("default-priority");
        }
        return section == CompanionDraft.Section.COMPANION && (leaf.equals("custom-model-data")
                || leaf.equals("interval-ticks") || leaf.equals("columns")
                || leaf.equals("amount") && (path.getFirst().equals("equipment") || path.getFirst().equals("display")));
    }

    public enum Category {
        APPEARANCE, EQUIPMENT, MOVEMENT, PARTICLES, ATTRIBUTES, EFFECTS, BUFFS;

        public CompanionDraft.Section section() {
            return switch (this) {
                case ATTRIBUTES -> CompanionDraft.Section.ATTRIBUTES;
                case EFFECTS -> CompanionDraft.Section.EFFECTS;
                case BUFFS -> CompanionDraft.Section.BUFFS;
                default -> CompanionDraft.Section.COMPANION;
            };
        }
    }

    public record Field(CompanionDraft.Section section, List<Object> path, Object value, String label, List<String> choices) {
        public Field {
            Objects.requireNonNull(section, "section");
            path = List.copyOf(path);
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(label, "label");
            choices = List.copyOf(choices);
            if (value instanceof Number number && !path.isEmpty()) {
                if (decimalField(section, path)) {
                    value = number.doubleValue();
                } else if (integerField(section, path) && number.doubleValue() == Math.rint(number.doubleValue())) {
                    if (path.getLast().equals("cooldown")) {
                        value = number.longValue();
                    } else if (number.doubleValue() >= Integer.MIN_VALUE && number.doubleValue() <= Integer.MAX_VALUE) {
                        value = number.intValue();
                    }
                }
            }
        }

        public Object parse(String input) {
            Objects.requireNonNull(input, "input");
            String trimmed = input.trim();
            if (!choices.isEmpty()) {
                return choices.stream().filter(choice -> choice.equalsIgnoreCase(trimmed)).findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("Choose one of " + String.join(", ", choices)));
            }
            if (value instanceof Boolean) {
                if (!trimmed.equalsIgnoreCase("true") && !trimmed.equalsIgnoreCase("false")) {
                    throw new IllegalArgumentException("Enter true or false");
                }
                return Boolean.parseBoolean(trimmed);
            }
            if (value instanceof Number) {
                double number = Double.parseDouble(trimmed);
                if (!Double.isFinite(number)) {
                    throw new IllegalArgumentException("Enter a finite number");
                }
                String leaf = String.valueOf(path.getLast());
                if (leaf.equals("scale") && number != -1 && number <= 0) {
                    throw new IllegalArgumentException("Scale must be -1 or greater than zero");
                }
                if (leaf.equals("cooldown") && number < 0 || leaf.equals("amplifier") && (number < 0 || number > 255)) {
                    throw new IllegalArgumentException("Value is outside the allowed range");
                }
                if ((leaf.equals("columns") || leaf.equals("interval-ticks") || leaf.equals("amount")
                        && section == CompanionDraft.Section.COMPANION) && number < 1) {
                    throw new IllegalArgumentException("Value must be at least one");
                }
                if (integerField(section, path) || value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
                    long integer = Long.parseLong(trimmed);
                    if (!(value instanceof Long) && (integer < Integer.MIN_VALUE || integer > Integer.MAX_VALUE)) {
                        throw new IllegalArgumentException("Integer is outside the allowed range");
                    }
                    if (value instanceof Long) {
                        return integer;
                    }
                    return (int) integer;
                }
                return number;
            }
            String leaf = String.valueOf(path.getLast());
            if (leaf.equals("rgb-color")) {
                String[] channels = trimmed.split(",", -1);
                if (channels.length != 3) {
                    throw new IllegalArgumentException("Enter red,green,blue values from 0 to 255");
                }
                List<String> values = new ArrayList<>();
                for (String channel : channels) {
                    int number = Integer.parseInt(channel.trim());
                    if (number < 0 || number > 255) {
                        throw new IllegalArgumentException("Color channels must be between 0 and 255");
                    }
                    values.add(Integer.toString(number));
                }
                return String.join(",", values);
            }
            if (leaf.equals("material")) {
                String lower = trimmed.toLowerCase(Locale.ROOT);
                if (lower.startsWith("nexo:") || lower.startsWith("itemsadder:")) {
                    if (trimmed.substring(trimmed.indexOf(':') + 1).isBlank()) {
                        throw new IllegalArgumentException("Custom material requires an item ID");
                    }
                    return trimmed;
                }
                Material material = Material.matchMaterial(trimmed);
                if (material == null || !material.isItem() && !material.isAir()) {
                    throw new IllegalArgumentException("Unknown item material");
                }
                return material.name();
            }
            return input;
        }
    }
}
