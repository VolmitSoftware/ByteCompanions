package com.volmit.bytecompanions.platform.paper.compatibility;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class SoundPlaybackTest {
    @Test
    void uiSoundsUseTheUiVolumeCategory() {
        assertEquals(SoundCategory.UI, SoundPlayback.category(Sound.Source.UI));
    }

    @Test
    void playbackPreservesCustomSoundCategoryVolumePitchAndSeed() {
        List<Object[]> calls = new ArrayList<>();
        Location location = new Location(null, 1, 2, 3);
        Player player = Player.class.cast(Proxy.newProxyInstance(Player.class.getClassLoader(),
                new Class<?>[]{Player.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("getLocation")) {
                        return location;
                    }
                    if (method.getName().equals("playSound")) {
                        calls.add(arguments);
                        return null;
                    }
                    throw new UnsupportedOperationException(method.getName());
                }));
        Sound sound = Sound.sound().type(Key.key("companions", "custom"))
                .source(Sound.Source.RECORD).volume(0.75f).pitch(1.25f).seed(123L).build();
        SoundPlayback.play(player, sound);
        Object[] arguments = calls.getFirst();
        assertEquals(1, calls.size());
        assertSame(location, arguments[0]);
        assertEquals("companions:custom", arguments[1]);
        assertEquals(SoundCategory.RECORDS, arguments[2]);
        assertEquals(0.75f, arguments[3]);
        assertEquals(1.25f, arguments[4]);
        assertEquals(123L, arguments[5]);
    }
}
