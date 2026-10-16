package com.volmit.bytecompanions.platform.paper.messenger;

import com.volmit.bytecompanions.platform.paper.compatibility.SoundPlayback;
import com.volmit.bytecompanions.platform.paper.localization.LanguageService;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.function.BooleanSupplier;

public final class MessageDispatcher {
    private static final Sound SUCCESS = Sound.sound(Key.key("minecraft", "block.amethyst_cluster.break"), Sound.Source.MASTER, 0.6F, 1.2F);
    private static final Sound FAILURE = Sound.sound(Key.key("minecraft", "block.respawn_anchor.deplete"), Sound.Source.MASTER, 0.6F, 0.8F);

    private final LanguageService languages;
    private final BooleanSupplier commandSounds;

    public MessageDispatcher(LanguageService languages, BooleanSupplier commandSounds) {
        this.languages = Objects.requireNonNull(languages, "languages");
        this.commandSounds = Objects.requireNonNull(commandSounds, "commandSounds");
    }

    public void send(final Audience audience, final MessageKey messageKey, final String... replacements) {
        if (!(audience instanceof CommandSender sender)) {
            throw new IllegalArgumentException("Companion messages require a command sender audience");
        }
        this.languages.send(sender, messageKey.node() + "." + messageKey.key(), replacements);
        if (sender instanceof Player player && this.commandSounds.getAsBoolean()) {
            SoundPlayback.play(player, messageKey.node().equals("success") ? SUCCESS : FAILURE);
        }
    }
}
