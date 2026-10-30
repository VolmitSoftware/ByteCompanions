package com.volmit.bytecompanions.platform.paper.presentation;

import art.arcane.volmlib.util.plugin.ComponentLog;
import art.arcane.volmlib.util.plugin.SplashScreenSupport;
import com.volmit.bytecompanions.platform.paper.compatibility.BuildInfo;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

public final class SplashScreen {
    public static final String NAME = "ByteCompanions";
    public static final String TAGLINE = "Animated companions and particles";
    private static final String FILL = ChatColor.of(ChatMenuStyle.RIGHT).toString();
    private static final String EDGE = ChatColor.of(ChatMenuStyle.LEFT).toString();
    private static final String META = ChatColor.of(ChatMenuStyle.META).toString();
    private static final String ACCENT = ChatColor.of(ChatMenuStyle.ACCENT).toString();
    private static final String RESET = ChatColor.RESET.toString();

    private SplashScreen() {
    }

    public static void print(Plugin plugin, boolean alternatesEnabled) {
        try {
            String splash = render(SplashArt.pick(ThreadLocalRandom.current(), alternatesEnabled),
                    details(plugin.getDescription().getVersion(), SplashScreenSupport.serverVersionWithoutMcSuffix(plugin.getServer().getVersion()),
                            Integer.toString(SplashScreenSupport.javaMajorVersion()), SplashScreenSupport.startupDate()));
            ComponentLog.logLegacy(plugin, plugin.getLogger(), "", Level.INFO, splash, null);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "ByteCompanions splash screen failed to render", exception);
        }
    }

    public static List<String> details(String version, String server, String javaVersion, String date) {
        return List.of(
                "",
                NAME + ", " + TAGLINE,
                "Version: " + version,
                "By: VolmitSoftware (Arcane Arts) | VolmitSoftware.com",
                "Server: " + server + " | MC Support: " + BuildInfo.minecraftRange(),
                "Java: " + javaVersion + " | Date: " + date);
    }

    public static String render(List<String> art, List<String> details) {
        int width = art.stream().mapToInt(String::length).max().orElse(0);
        List<String> rows = new ArrayList<>(SplashArt.ROWS);
        for (int index = 0; index < SplashArt.ROWS; index++) {
            String row = index < art.size() ? art.get(index) : "";
            rows.add(row + " ".repeat(Math.max(0, width - row.length())));
        }
        StringBuilder output = new StringBuilder("\n");
        for (int index = 0; index < rows.size(); index++) {
            output.append(colorizeArt(rows.get(index)));
            String detail = index < details.size() ? details.get(index) : "";
            if (!detail.isEmpty()) {
                output.append(META).append("   ").append(colorizeDetail(detail));
            }
            output.append(RESET).append('\n');
        }
        return output.toString();
    }

    private static String colorizeArt(String row) {
        StringBuilder colored = new StringBuilder(row.length() + 32);
        String active = "";
        boolean text = false;
        for (int index = 0; index < row.length(); index++) {
            char glyph = row.charAt(index);
            String color;
            if (glyph == ' ') {
                color = active;
            } else if (text || Character.isLetter(glyph) || glyph == '.' || glyph == ',') {
                text = true;
                color = ACCENT;
            } else if (glyph == '█') {
                color = FILL;
            } else {
                color = EDGE;
            }
            if (!color.equals(active)) {
                colored.append(color);
                active = color;
            }
            colored.append(glyph);
        }
        return colored.toString();
    }

    private static String colorizeDetail(String detail) {
        String colored = detail;
        if (colored.startsWith(NAME + ", ")) {
            colored = ACCENT + NAME + META + colored.substring(NAME.length());
        }
        return colored.replace(": ", ": " + ACCENT).replace(" | ", META + " | ");
    }
}
