package com.volmit.bytecompanions.features.particles.messages;

import net.kyori.adventure.audience.Audience;
import org.bukkit.command.CommandSender;
import com.volmit.bytecompanions.platform.paper.localization.LanguageService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class MessageSender {

    private final LanguageService languages;

    public MessageSender(
            final @NotNull LanguageService languages
    ) {
        this.languages = languages;
    }

    public void send(final @NotNull Audience audience, final @NotNull String path) {
        this.send(audience, path, null);
    }

    public void send(final @NotNull Audience audience, final @NotNull String path, final @Nullable String effectId) {
        if (!(audience instanceof CommandSender sender)) {
            throw new IllegalArgumentException("Particle messages require a command sender audience");
        }
        String key = switch (path) {
            case "general.no-permission" -> "errors.no-permission";
            case "general.persistence-failed" -> "errors.persistence-failed";
            case "companion.not-equipped" -> "particles.companions.not-equipped";
            default -> "particles." + path;
        };
        if (effectId == null) {
            languages.send(sender, key);
        } else {
            languages.send(sender, key, "effect", effectId);
        }
    }

}
