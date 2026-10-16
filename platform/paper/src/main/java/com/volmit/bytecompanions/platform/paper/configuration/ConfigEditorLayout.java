package com.volmit.bytecompanions.platform.paper.configuration;

import art.arcane.volmlib.util.config.BukkitConfigEditor;
import art.arcane.volmlib.util.config.ConfigDescription;
import art.arcane.volmlib.util.config.ConfigDoc;
import art.arcane.volmlib.util.localization.TextKey;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

public final class ConfigEditorLayout {
    public static final TextKey TITLE = TextKey.of("gui.config.title", "Configuration");
    public static final TextKey LANGUAGES_NAME = TextKey.of("gui.setting.languages.name", "Languages");
    public static final TextKey LANGUAGES_DESCRIPTION = TextKey.of("gui.setting.languages", "Edit server translations and their formatting.");
    private static final Map<String, Material> SECTION_ICONS = Map.ofEntries(
            Map.entry("general", Material.COMMAND_BLOCK),
            Map.entry("metrics", Material.OBSERVER),
            Map.entry("presentation", Material.PAINTING),
            Map.entry("hotReload", Material.COMPARATOR),
            Map.entry("debug", Material.SPYGLASS),
            Map.entry("storage", Material.CHEST),
            Map.entry("performance", Material.CLOCK),
            Map.entry("serverRules", Material.BARRIER),
            Map.entry("menus", Material.ITEM_FRAME),
            Map.entry("collections", Material.ARMOR_STAND),
            Map.entry("features", Material.ENDER_CHEST));
    private static final Map<String, BukkitConfigEditor.NumericControl> NUMERIC_CONTROLS = Map.ofEntries(
            Map.entry("general.companionLimits.maxTotalPerPlayer", new BukkitConfigEditor.NumericControl(1, -1, 64)),
            Map.entry("general.companionLimits.perModel.fullBody", new BukkitConfigEditor.NumericControl(1, -1, 64)),
            Map.entry("general.companionLimits.perModel.headOnly", new BukkitConfigEditor.NumericControl(1, -1, 64)),
            Map.entry("hotReload.pollIntervalMillis", new BukkitConfigEditor.NumericControl(250, 250, 60000)),
            Map.entry("hotReload.cooldownMillis", new BukkitConfigEditor.NumericControl(250, 250, 60000)),
            Map.entry("storage.pool.connectionTimeoutMillis", new BukkitConfigEditor.NumericControl(250, 0, 600000)),
            Map.entry("storage.pool.maxLifetimeMillis", new BukkitConfigEditor.NumericControl(30000, 0, 86400000)),
            Map.entry("storage.pool.maxPoolSize", new BukkitConfigEditor.NumericControl(1, 1, 64)),
            Map.entry("storage.pool.minIdle", new BukkitConfigEditor.NumericControl(1, 0, 64)),
            Map.entry("storage.mysql.port", new BukkitConfigEditor.NumericControl(1, 1, 65535)),
            Map.entry("performance.updateInterval", new BukkitConfigEditor.NumericControl(1, 1, 200)),
            Map.entry("performance.viewerRefreshDelay", new BukkitConfigEditor.NumericControl(1, 1, 200)),
            Map.entry("performance.viewDistance", new BukkitConfigEditor.NumericControl(1, 0, 128)),
            Map.entry("collections.maximumOutfits", new BukkitConfigEditor.NumericControl(1, 1, 32)),
            Map.entry("collections.trialSeconds", new BukkitConfigEditor.NumericControl(5, 5, 120)));
    private static final Map<String, Entry> ENTRIES = scan();

    private ConfigEditorLayout() {
    }

    public static List<TextKey> keys() {
        List<TextKey> keys = new ArrayList<>(ENTRIES.size() * 2 + 3);
        keys.add(TITLE);
        keys.add(LANGUAGES_NAME);
        keys.add(LANGUAGES_DESCRIPTION);
        for (Entry entry : ENTRIES.values()) {
            keys.add(entry.name());
            keys.add(entry.description());
        }
        return List.copyOf(keys);
    }

    public static List<String> paths() {
        return List.copyOf(ENTRIES.keySet());
    }

    public static BukkitConfigEditor.EditorLayout create(Consumer<Player> openLanguagePicker, Consumer<Player> openLanguageEditor) {
        Map<String, BukkitConfigEditor.EntryPresentation> presentations = new LinkedHashMap<>(ENTRIES.size());
        for (Entry entry : ENTRIES.values()) {
            boolean language = entry.path().equals("general.language");
            presentations.put(entry.path(), new BukkitConfigEditor.EntryPresentation(entry.name(), entry.description(),
                    language ? Material.WRITABLE_BOOK : entry.icon(), entry.order(), NUMERIC_CONTROLS.get(entry.path()), null,
                    language ? openLanguagePicker : null));
        }
        BukkitConfigEditor.RootShortcut languages = new BukkitConfigEditor.RootShortcut(LANGUAGES_NAME, LANGUAGES_DESCRIPTION,
                Material.BOOKSHELF, openLanguageEditor);
        return new BukkitConfigEditor.EditorLayout(TITLE, path -> presentations.get(String.join(".", path)), List.of(languages));
    }

    private static Map<String, Entry> scan() {
        Map<String, Entry> entries = new LinkedHashMap<>();
        scan(Configuration.class, "", entries);
        return Map.copyOf(entries);
    }

    private static void scan(Class<?> type, String prefix, Map<String, Entry> entries) {
        int order = 0;
        for (Field field : type.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            String path = prefix.isEmpty() ? field.getName() : prefix + "." + field.getName();
            ConfigDoc doc = field.getAnnotation(ConfigDoc.class);
            boolean section = isSection(field.getType());
            String summary;
            if (doc != null) {
                summary = doc.value();
            } else if (section && field.getType().getAnnotation(ConfigDescription.class) != null) {
                summary = field.getType().getAnnotation(ConfigDescription.class).value();
            } else {
                summary = "Settings for " + field.getName() + ".";
            }
            Material icon = section
                    ? SECTION_ICONS.getOrDefault(path, Material.CHEST)
                    : materialFor(field.getType());
            entries.put(path, new Entry(path, TextKey.of("gui.setting." + path + ".name", title(field.getName())),
                    TextKey.of("gui.setting." + path, summary), icon, order++));
            if (section) {
                scan(field.getType(), path, entries);
            }
        }
    }

    private static boolean isSection(Class<?> type) {
        return type.getPackageName().startsWith("com.volmit.bytecompanions") && !type.isEnum();
    }

    private static Material materialFor(Class<?> type) {
        if (type == boolean.class) {
            return Material.LIME_DYE;
        }
        if (type == int.class || type == long.class || type == double.class) {
            return Material.COMPARATOR;
        }
        if (List.class.isAssignableFrom(type)) {
            return Material.WRITABLE_BOOK;
        }
        return Material.NAME_TAG;
    }

    private static String title(String fieldName) {
        StringBuilder title = new StringBuilder(fieldName.length() + 4);
        for (int index = 0; index < fieldName.length(); index++) {
            char glyph = fieldName.charAt(index);
            if (index == 0) {
                title.append(Character.toUpperCase(glyph));
            } else if (Character.isUpperCase(glyph)) {
                title.append(' ').append(Character.toLowerCase(glyph));
            } else {
                title.append(glyph);
            }
        }
        return title.toString().replace("mysql", "MySQL").replace("H2", "H2").toLowerCase(Locale.ROOT).isEmpty()
                ? fieldName : title.toString();
    }

    private record Entry(String path, TextKey name, TextKey description, Material icon, int order) {
    }
}
