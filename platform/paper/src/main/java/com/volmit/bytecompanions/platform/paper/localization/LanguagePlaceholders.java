package com.volmit.bytecompanions.platform.paper.localization;

import java.util.Map;
import java.util.TreeMap;

public final class LanguagePlaceholders {
    private static final Map<String, String> DICTIONARY = build();

    private LanguagePlaceholders() {
    }

    public static Map<String, String> dictionary() {
        return DICTIONARY;
    }

    private static Map<String, String> build() {
        Map<String, String> dictionary = new TreeMap<>();
        dictionary.put("action", "Requested action");
        dictionary.put("actions", "Available actions");
        dictionary.put("after", "Value after the change");
        dictionary.put("amount", "Amount or count");
        dictionary.put("argument", "Unexpected command argument");
        dictionary.put("before", "Value before the change");
        dictionary.put("bonus", "Bonus name");
        dictionary.put("booster", "Booster identifier");
        dictionary.put("category", "Category name");
        dictionary.put("change", "Described change");
        dictionary.put("channel", "Booster channel");
        dictionary.put("choice", "Selected option");
        dictionary.put("command", "Command path");
        dictionary.put("companion", "Companion identifier");
        dictionary.put("companions", "Companion identifiers");
        dictionary.put("count", "Number of items");
        dictionary.put("created", "Creation time");
        dictionary.put("current", "Current value");
        dictionary.put("definition", "Companion definition");
        dictionary.put("detail", "Failure detail");
        dictionary.put("effect", "Particle effect identifier");
        dictionary.put("entry", "Entry label");
        dictionary.put("feature", "Feature name");
        dictionary.put("features", "Enabled features");
        dictionary.put("field", "Studio field name");
        dictionary.put("formation", "Formation name");
        dictionary.put("group", "Message category name");
        dictionary.put("hot_reload", "Hot reload state");
        dictionary.put("index", "Position number");
        dictionary.put("integrations", "Integration states");
        dictionary.put("item", "Item name");
        dictionary.put("items", "Item list");
        dictionary.put("java", "Java major version");
        dictionary.put("key", "Message or setting key");
        dictionary.put("kind", "Transaction kind");
        dictionary.put("language", "Active language");
        dictionary.put("level", "Level number");
        dictionary.put("limit", "Configured limit");
        dictionary.put("line", "Message line number");
        dictionary.put("locale", "Locale identifier");
        dictionary.put("maximum", "Maximum accepted value");
        dictionary.put("mode", "Combining rule");
        dictionary.put("model", "Companion model");
        dictionary.put("money", "Money amount");
        dictionary.put("name", "Display name");
        dictionary.put("new", "Installed rendered value");
        dictionary.put("next", "Next value");
        dictionary.put("old", "Previous rendered value");
        dictionary.put("operation", "Bonus operation");
        dictionary.put("page", "Current page number");
        dictionary.put("pages", "Total page count");
        dictionary.put("parameter", "Parameter name");
        dictionary.put("path", "File path or setting path");
        dictionary.put("percent", "Percentage value");
        dictionary.put("permission", "Required permission");
        dictionary.put("personal", "Personal locale when it differs");
        dictionary.put("player", "Player name");
        dictionary.put("players", "Loaded player count");
        dictionary.put("plugin", "Plugin name");
        dictionary.put("prefix", "Styled plugin name from runtime.prefix");
        dictionary.put("provider", "Payment provider");
        dictionary.put("range", "Supported Minecraft range");
        dictionary.put("reason", "Failure reason");
        dictionary.put("required", "Required amount");
        dictionary.put("scheduler", "Scheduler implementation");
        dictionary.put("seconds", "Duration in seconds");
        dictionary.put("section", "Language editor section");
        dictionary.put("setting", "Setting name");
        dictionary.put("sources", "Bonus sources");
        dictionary.put("status", "Status marker");
        dictionary.put("step", "Adjustment step");
        dictionary.put("target", "Selection target");
        dictionary.put("tier", "Upgrade tier");
        dictionary.put("time", "Duration text");
        dictionary.put("transaction", "Transaction identifier");
        dictionary.put("type", "Parameter type");
        dictionary.put("url", "Public report URL");
        dictionary.put("usage", "Command usage");
        dictionary.put("value", "Current or raw value");
        dictionary.put("variables", "Placeholders valid for the message");
        dictionary.put("variant", "Appearance variant");
        dictionary.put("version", "Plugin version");
        dictionary.put("xp", "Companion experience");
        return Map.copyOf(dictionary);
    }
}
