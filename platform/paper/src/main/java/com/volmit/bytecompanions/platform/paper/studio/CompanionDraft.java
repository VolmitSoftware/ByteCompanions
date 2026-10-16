package com.volmit.bytecompanions.platform.paper.studio;

import com.volmit.bytecompanions.platform.paper.configuration.ContentSnapshot;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.BasicConfigurationNode;
import org.spongepowered.configurate.ConfigurationOptions;
import art.arcane.volmlib.util.config.TomlCodec;
import art.arcane.volmlib.util.config.ConfigJson;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Locale;

public final class CompanionDraft {
    private final ContentSnapshot original;
    private final ContentSnapshot edited;
    private final ContentSnapshot resetSnapshot;
    private final String id;
    private final String configurationSource;
    private volatile List<String> changes;

    private CompanionDraft(ContentSnapshot original, ContentSnapshot edited, ContentSnapshot resetSnapshot,
                           String id, String configurationSource) {
        this.original = original;
        this.edited = edited;
        this.resetSnapshot = resetSnapshot;
        this.id = id;
        this.configurationSource = configurationSource;
    }

    public static CompanionDraft open(ContentSnapshot content, String id, String configurationSource) {
        requireIdentifier(id);
        Objects.requireNonNull(content, "content").source(path(id));
        return new CompanionDraft(content, content, content, id, Objects.requireNonNull(configurationSource, "configurationSource"));
    }

    public String id() {
        return id;
    }

    public String configurationSource() {
        return configurationSource;
    }

    public ContentSnapshot snapshot() {
        return edited;
    }

    public ConfigurationNode node(Section section) throws IOException {
        return sectionNode(edited, section, id);
    }

    public CompanionDraft withValue(Section section, List<?> path, Object value) throws IOException {
        Objects.requireNonNull(section, "section");
        Objects.requireNonNull(path, "path");
        ConfigurationNode file = edited.node(section.path(id), ConfigurationOptions.defaults());
        ConfigurationNode target = section == Section.COMPANION ? file : file.node("companions", id);
        target.node(path.toArray()).raw(value);
        Map<String, String> sources = sources(edited);
        sources.put(section.path(id), serialize(file));
        return new CompanionDraft(original, new ContentSnapshot(sources), resetSnapshot, id, configurationSource);
    }

    public CompanionDraft reset() {
        return new CompanionDraft(original, resetSnapshot, resetSnapshot, id, configurationSource);
    }

    public boolean creation() {
        return !original.paths().contains(path(id));
    }

    public CompanionDraft duplicate(String newId) throws IOException {
        if (newId == null || !newId.matches("[a-z0-9_-]{1,64}")) {
            throw new IllegalArgumentException("New companion ID requires 1–64 lowercase letters, digits, underscores, or hyphens");
        }
        requireUnusedIdentifier(original, newId);
        if (edited.paths().stream().anyMatch(existing -> existing.equalsIgnoreCase(path(newId)))) {
            throw new IOException("Companion ID already exists: " + newId);
        }
        Map<String, String> sources = sources(original);
        sources.put(path(newId), edited.source(path(id)));
        for (Section section : Section.values()) {
            if (section == Section.COMPANION) {
                continue;
            }
            ConfigurationNode node = original.node(section.path(newId), ConfigurationOptions.defaults());
            ConfigurationNode copied = sectionNode(edited, section, id);
            if (copied.raw() != null) {
                node.node("companions", newId).from(copied);
                sources.put(section.path(newId), serialize(node));
            }
        }
        ContentSnapshot clone = new ContentSnapshot(sources);
        return new CompanionDraft(original, clone, clone, newId, configurationSource);
    }

    public boolean changed() {
        return !changes().isEmpty();
    }

    public List<String> changes() {
        if (original == edited) {
            return List.of();
        }
        List<String> captured = changes;
        if (captured != null) {
            return captured;
        }
        List<String> changed = new ArrayList<>();
        try {
            for (Section section : Section.values()) {
                if (Objects.equals(optionalSource(original, section.path(id)), optionalSource(edited, section.path(id)))) {
                    continue;
                }
                describeChanges(sectionNode(original, section, id).raw(), sectionNode(edited, section, id).raw(),
                        section == Section.COMPANION ? "Companion" : title(section.name()), changed);
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        captured = List.copyOf(changed);
        changes = captured;
        return captured;
    }

    public PreparedEdit prepare(ContentSnapshot latest) throws IOException {
        if (creation()) {
            requireUnusedIdentifier(latest, id);
        }
        Map<String, String> sources = sources(latest);
        Map<String, String> writes = new LinkedHashMap<>();
        for (Section section : Section.values()) {
            String file = section.path(id);
            if (Objects.equals(optionalSource(original, file), optionalSource(edited, file))
                    || equivalent(sectionNode(original, section, id).raw(), sectionNode(edited, section, id).raw())) {
                continue;
            }
            if (section == Section.COMPANION) {
                if (!Objects.equals(optionalSource(original, file), optionalSource(latest, file))) {
                    throw conflict(file);
                }
                writes.put(file, edited.source(file));
                sources.put(file, edited.source(file));
                continue;
            }
            ConfigurationNode expected = sectionNode(original, section, id);
            ConfigurationNode current = sectionNode(latest, section, id);
            if (!Objects.equals(expected.raw(), current.raw())) {
                throw conflict(file);
            }
            ConfigurationNode replacement = latest.node(file, ConfigurationOptions.defaults());
            replacement.node("companions", id).from(sectionNode(edited, section, id));
            String source = serialize(replacement);
            writes.put(file, source);
            sources.put(file, source);
        }
        return new PreparedEdit(new ContentSnapshot(sources), Map.copyOf(writes));
    }

    private static void requireUnusedIdentifier(ContentSnapshot content, String id) throws IOException {
        if (content.paths().stream().anyMatch(existing -> existing.equalsIgnoreCase(path(id)))) {
            throw new IOException("Companion ID already exists: " + id);
        }
        for (Section section : Section.values()) {
            if (section != Section.COMPANION && sectionNode(content, section, id).raw() != null) {
                throw new IOException("Bonus configuration already uses companion ID: " + id);
            }
        }
        String progression = "features/progression/config.toml";
        if (content.paths().contains(progression) && content.node(progression, ConfigurationOptions.defaults())
                .node("companions", id).raw() != null) {
            throw new IOException("Progression configuration already uses companion ID: " + id);
        }
    }

    private static boolean equivalent(Object left, Object right) {
        if (left instanceof Number first && right instanceof Number second) {
            return new BigDecimal(first.toString()).compareTo(new BigDecimal(second.toString())) == 0;
        }
        if (left instanceof Map<?, ?> first && right instanceof Map<?, ?> second) {
            return first.keySet().equals(second.keySet()) && first.keySet().stream()
                    .allMatch(key -> equivalent(first.get(key), second.get(key)));
        }
        if (left instanceof List<?> first && right instanceof List<?> second) {
            if (first.size() != second.size()) {
                return false;
            }
            for (int index = 0; index < first.size(); index++) {
                if (!equivalent(first.get(index), second.get(index))) {
                    return false;
                }
            }
            return true;
        }
        return Objects.equals(left, right);
    }

    private static void describeChanges(Object before, Object after, String path, List<String> changes) {
        if (equivalent(before, after)) {
            return;
        }
        if ((before == null || before instanceof Map<?, ?>) && (after == null || after instanceof Map<?, ?>)) {
            Map<?, ?> first = before instanceof Map<?, ?> map ? map : Map.of();
            Map<?, ?> second = after instanceof Map<?, ?> map ? map : Map.of();
            LinkedHashSet<Object> keys = new LinkedHashSet<>(first.keySet());
            keys.addAll(second.keySet());
            if (!keys.isEmpty()) {
                for (Object key : keys) {
                    describeChanges(first.get(key), second.get(key), path + " › " + title(key.toString()), changes);
                }
                return;
            }
        }
        if ((before == null || before instanceof List<?>) && (after == null || after instanceof List<?>)) {
            List<?> first = before instanceof List<?> list ? list : List.of();
            List<?> second = after instanceof List<?> list ? list : List.of();
            if (!first.isEmpty() || !second.isEmpty()) {
                for (int index = 0; index < Math.max(first.size(), second.size()); index++) {
                    describeChanges(index < first.size() ? first.get(index) : null,
                            index < second.size() ? second.get(index) : null, path + " › " + (index + 1), changes);
                }
                return;
            }
        }
        changes.add(path + ": " + summarize(before) + " → " + summarize(after));
    }

    private static String summarize(Object value) {
        String text = value == null ? "unset" : String.valueOf(value).replace('\n', ' ').replace('\r', ' ');
        return text.length() > 48 ? text.substring(0, 45) + "…" : text;
    }

    private static String title(String text) {
        String value = text.toLowerCase(Locale.ROOT).replace('-', ' ').replace('_', ' ');
        return value.isEmpty() ? value : Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static IOException conflict(String path) {
        return new IOException("Companion changed while the studio was open; reopen it: " + path);
    }

    private static ConfigurationNode sectionNode(ContentSnapshot content, Section section, String id) throws IOException {
        if (section == Section.COMPANION && !content.paths().contains(section.path(id))) {
            return BasicConfigurationNode.root();
        }
        ConfigurationNode node = content.node(section.path(id), ConfigurationOptions.defaults());
        return section == Section.COMPANION ? node : node.node("companions", id).copy();
    }

    private static Map<String, String> sources(ContentSnapshot snapshot) {
        Map<String, String> sources = new LinkedHashMap<>();
        for (String path : snapshot.paths()) {
            sources.put(path, snapshot.source(path));
        }
        return sources;
    }

    private static String optionalSource(ContentSnapshot content, String path) {
        return content.paths().contains(path) ? content.source(path) : null;
    }

    private static String serialize(ConfigurationNode node) throws IOException {
        String source;
        try {
            source = TomlCodec.toToml(ConfigJson.NORMAL.toJsonTree(node.raw()));
        } catch (IllegalArgumentException exception) {
            throw new IOException("Could not serialize companion TOML", exception);
        }
        if (source.getBytes(StandardCharsets.UTF_8).length > 2 * 1024 * 1024) {
            throw new IOException("Companion content exceeds 2 MiB");
        }
        return source;
    }

    private static String path(String id) {
        return "companions/" + id + ".toml";
    }

    private static void requireIdentifier(String id) {
        if (id == null || id.isBlank() || id.equals(".") || id.equals("..")
                || id.contains("/") || id.contains("\\") || id.contains(":")
                || id.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Companion ID must be a valid filename without a directory");
        }
    }

    public enum Section {
        COMPANION(null), ATTRIBUTES("attributes"), BUFFS("buffs"), EFFECTS("effects");

        private final String feature;

        Section(String feature) {
            this.feature = feature;
        }

        public String path(String id) {
            return feature == null ? CompanionDraft.path(id) : "features/" + feature + "/config.toml";
        }
    }

    public static final class PreparedEdit {
        private final ContentSnapshot snapshot;
        private final Map<String, String> writes;

        private PreparedEdit(ContentSnapshot snapshot, Map<String, String> writes) {
            this.snapshot = snapshot;
            this.writes = writes;
        }

        public ContentSnapshot snapshot() {
            return snapshot;
        }

        public Map<String, String> writes() {
            return writes;
        }
    }
}
