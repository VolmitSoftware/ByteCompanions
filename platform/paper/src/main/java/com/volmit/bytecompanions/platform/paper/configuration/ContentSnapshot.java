package com.volmit.bytecompanions.platform.paper.configuration;

import art.arcane.volmlib.util.config.TomlCodec;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import org.bukkit.configuration.ConfigurationSection;
import org.spongepowered.configurate.BasicConfigurationNode;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.spongepowered.configurate.ConfigurationOptions;
import org.spongepowered.configurate.ConfigurationNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.LinkOption;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

public final class ContentSnapshot {
    private static final List<String> CONFIGURATIONS = List.of("features/attributes/config.toml",
            "features/effects/config.toml", "features/buffs/config.toml", "features/decorations/config.toml",
            "features/particles/config.toml", "features/particles/icons.toml", "features/progression/config.toml");
    private final Map<String, String> sources;

    public ContentSnapshot(Map<String, String> sources) throws IOException {
        this.sources = Map.copyOf(sources);
        for (String path : sources.keySet()) {
            ConfigurationNode node = node(path, ConfigurationOptions.defaults());
            if (!node.isMap() && node.raw() != null) {
                throw new IOException(path + " must contain a TOML table");
            }
            validateNumbers(node, path);
        }
    }

    public static ContentSnapshot capture(JavaPlugin plugin) throws IOException {
        Path root = plugin.getDataFolder().toPath();
        Files.createDirectories(root);
        for (String path : CONFIGURATIONS) {
            if (!Files.exists(root.resolve(path))) {
                plugin.saveResource(path, false);
            }
        }
        Path companions = root.resolve("companions");
        if (!Files.exists(companions)) {
            StarterCollection.installMissing(root);
        }
        return read(root);
    }

    public static ContentSnapshot read(Path root) throws IOException {
        Map<String, String> sources = new LinkedHashMap<>();
        for (String path : watchedPaths(root)) {
            sources.put(path, readSource(root, path));
        }
        return new ContentSnapshot(sources);
    }

    static List<String> watchedPaths(Path root) throws IOException {
        List<String> paths = new ArrayList<>(CONFIGURATIONS);
        Path companions = checkedPath(root, "companions");
        if (Files.exists(companions, LinkOption.NOFOLLOW_LINKS)) {
            try (Stream<Path> files = Files.list(companions)) {
                files.filter(path -> path.getFileName().toString().endsWith(".toml")).sorted()
                        .forEach(path -> paths.add("companions/" + path.getFileName()));
            }
        }
        return paths;
    }

    static Path checkedPath(Path root, String relative) throws IOException {
        Path absolute = root.toAbsolutePath().normalize();
        Path target = absolute.resolve(relative).normalize();
        if (!target.startsWith(absolute) || target.equals(absolute)) {
            throw new IOException("Invalid configuration path: " + relative);
        }
        for (Path current = target; !current.equals(absolute); current = current.getParent()) {
            if (Files.isSymbolicLink(current)) {
                throw new IOException("Configuration cannot use a symbolic link: " + relative);
            }
        }
        return target;
    }

    static String readSource(Path root, String relative) throws IOException {
        Path target = checkedPath(root, relative);
        if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS) || Files.size(target) > 2L * 1024 * 1024) {
            throw new IOException("Configuration is missing, not a regular file, or exceeds 2 MiB: " + relative);
        }
        String source = Files.readString(target, StandardCharsets.UTF_8);
        if (source.getBytes(StandardCharsets.UTF_8).length > 2 * 1024 * 1024) {
            throw new IOException("Configuration exceeds 2 MiB: " + relative);
        }
        return source;
    }

    public Set<String> paths() {
        return sources.keySet();
    }

    public String source(String path) {
        return Objects.requireNonNull(sources.get(path), "Missing captured content: " + path);
    }

    public ConfigurationNode node(String path, ConfigurationOptions options) throws IOException {
        try {
            return parse(source(path), options);
        } catch (IOException exception) {
            throw new IOException("Could not parse " + path, exception);
        }
    }

    public static ConfigurationNode parse(String source, ConfigurationOptions options) throws IOException {
        ConfigurationNode node = BasicConfigurationNode.root(options).raw(value(TomlCodec.toJsonElement(source)));
        validateNumbers(node, "TOML content");
        return node;
    }

    public MemoryConfiguration bukkit(String path) {
        try {
            MemoryConfiguration configuration = new MemoryConfiguration();
            populate(configuration, node(path, ConfigurationOptions.defaults()));
            return configuration;
        } catch (IOException exception) {
            throw new IllegalArgumentException("Could not parse " + path, exception);
        }
    }

    private static void populate(ConfigurationSection section, ConfigurationNode node) {
        for (Map.Entry<Object, ? extends ConfigurationNode> entry : node.childrenMap().entrySet()) {
            String key = entry.getKey().toString();
            ConfigurationNode child = entry.getValue();
            if (child.isMap()) {
                populate(section.createSection(key), child);
            } else {
                section.set(key, child.raw());
            }
        }
    }

    private static Object value(JsonElement element) {
        if (element.isJsonObject()) {
            Map<String, Object> result = new LinkedHashMap<>();
            element.getAsJsonObject().entrySet().forEach(entry -> result.put(entry.getKey(), value(entry.getValue())));
            return result;
        }
        if (element.isJsonArray()) {
            List<Object> result = new ArrayList<>();
            element.getAsJsonArray().forEach(entry -> result.add(value(entry)));
            return result;
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (primitive.isBoolean()) {
            return primitive.getAsBoolean();
        }
        if (primitive.isNumber()) {
            String number = primitive.getAsString();
            if (!number.contains(".") && !number.contains("e") && !number.contains("E")) {
                return Long.parseLong(number);
            }
            return primitive.getAsDouble();
        }
        return primitive.getAsString();
    }

    private static void validateNumbers(ConfigurationNode node, String path) throws IOException {
        if (node.rawScalar() instanceof Number number && !Double.isFinite(number.doubleValue())) {
            throw new IOException(path + " contains a non-finite number at " + node.path());
        }
        for (ConfigurationNode child : node.childrenMap().values()) {
            validateNumbers(child, path);
        }
        for (ConfigurationNode child : node.childrenList()) {
            validateNumbers(child, path);
        }
    }
}
