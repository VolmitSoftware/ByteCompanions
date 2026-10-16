package com.volmit.bytecompanions.platform.paper.compatibility;

import net.kyori.adventure.sound.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

public final class SoundPlayback {
    private SoundPlayback() {
    }

    public static void play(Player player, Sound sound) {
        SoundCategory category = category(sound.source());
        if (sound.seed().isPresent()) {
            player.playSound(player.getLocation(), sound.name().asString(), category,
                    sound.volume(), sound.pitch(), sound.seed().getAsLong());
        } else {
            player.playSound(player.getLocation(), sound.name().asString(), category,
                    sound.volume(), sound.pitch());
        }
    }

    static SoundCategory category(Sound.Source source) {
        return switch (source) {
            case MASTER -> SoundCategory.MASTER;
            case MUSIC -> SoundCategory.MUSIC;
            case RECORD -> SoundCategory.RECORDS;
            case WEATHER -> SoundCategory.WEATHER;
            case BLOCK -> SoundCategory.BLOCKS;
            case HOSTILE -> SoundCategory.HOSTILE;
            case NEUTRAL -> SoundCategory.NEUTRAL;
            case PLAYER -> SoundCategory.PLAYERS;
            case AMBIENT -> SoundCategory.AMBIENT;
            case VOICE -> SoundCategory.VOICE;
            case UI -> SoundCategory.UI;
        };
    }
}
