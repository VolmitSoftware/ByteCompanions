package com.volmit.bytecompanions.platform.paper.localization;

import art.arcane.volmlib.util.config.BukkitConfigMessages;
import art.arcane.volmlib.util.diagnostics.BukkitDebugMessages;
import art.arcane.volmlib.util.director.DirectorMessages;
import art.arcane.volmlib.util.localization.BukkitLanguageMessages;
import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.TextKey;
import art.arcane.volmlib.util.localization.TomlLanguageParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class CompanionMessages {
    private static final MessageCatalog CATALOG = load();

    private CompanionMessages() {
    }

    public static MessageCatalog catalog() {
        return CATALOG;
    }

    public static TextKey key(String id) {
        return (TextKey) CATALOG.require(id);
    }

    static String resource(String name) throws IOException {
        try (InputStream input = CompanionMessages.class.getResourceAsStream("/languages/" + name)) {
            if (input == null) {
                throw new IOException("Missing bundled language: " + name);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static MessageCatalog load() {
        MessageCatalog.Builder builder = MessageCatalog.builder("en_US")
                .addAll(DirectorMessages.keys())
                .addAll(BukkitLanguageMessages.keys())
                .addAll(BukkitDebugMessages.keys())
                .addAll(BukkitConfigMessages.keys())
                .addAll(BukkitConfigMessages.configuredLayoutKeys());
        builder.addAll(com.volmit.bytecompanions.platform.paper.configuration.ConfigEditorLayout.keys());
        try {
            TomlLanguageParser.parseText(resource("en_US.toml")).forEach((id, value) -> builder.add(TextKey.of(id, value)));
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
        return builder.build();
    }
}
