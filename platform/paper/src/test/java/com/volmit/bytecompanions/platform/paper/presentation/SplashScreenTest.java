package com.volmit.bytecompanions.platform.paper.presentation;

import com.volmit.bytecompanions.platform.paper.compatibility.BuildInfo;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SplashScreenTest {
    @Test
    void detailRowsFollowTheSharedLayout() {
        assertEquals(List.of(
                "",
                "ByteCompanions, Animated companions and particles",
                "Version: 3.0.0",
                "By: VolmitSoftware (Arcane Arts) | VolmitSoftware.com",
                "Server: Paper 26.2 | MC Support: " + BuildInfo.minecraftRange(),
                "Java: 25 | Date: 2026-10-15"),
                SplashScreen.details("3.0.0", "Paper 26.2", "25", "2026-10-15"));
        assertEquals("26.1-26.3", BuildInfo.minecraftRange());
    }

    @Test
    void everyArtRendersSixRowsWithConsoleSafeGlyphs() {
        List<String> details = SplashScreen.details("3.0.0", "Paper 26.2", "25", "2026-10-15");
        List<List<String>> arts = new java.util.ArrayList<>(SplashArt.alternates());
        arts.add(SplashArt.standard());
        for (List<String> art : arts) {
            assertTrue(art.size() <= SplashArt.ROWS && !art.isEmpty());
            String rendered = SplashScreen.render(art, details);
            assertTrue(rendered.startsWith("\n"));
            assertEquals(SplashArt.ROWS, rendered.chars().filter(glyph -> glyph == '\n').count() - 1);
            for (String row : art) {
                for (char glyph : row.toCharArray()) {
                    assertTrue(glyph < 0x7f || glyph == '█' || (glyph >= 0x2500 && glyph <= 0x257f)
                            || (glyph >= 0x2800 && glyph <= 0x28ff), "Unsupported glyph " + glyph + " in " + row);
                }
            }
            assertTrue(rendered.contains("MC Support: ") && rendered.contains(BuildInfo.minecraftRange()));
            assertFalse(rendered.contains("\u001b"));
        }
    }

    @Test
    void alternatesAppearOnlyWhenEnabledAndOnlyOneStartInTen() {
        assertSame(SplashArt.standard(), SplashArt.pick(new Random(1), false));
        int alternates = 0;
        Random random = new Random(42);
        for (int start = 0; start < 10_000; start++) {
            if (SplashArt.pick(random, true) != SplashArt.standard()) {
                alternates++;
            }
        }
        assertTrue(alternates > 800 && alternates < 1200, Integer.toString(alternates));
    }
}
