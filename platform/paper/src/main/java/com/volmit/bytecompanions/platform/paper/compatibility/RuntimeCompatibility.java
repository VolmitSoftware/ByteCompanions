package com.volmit.bytecompanions.platform.paper.compatibility;

import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import org.bukkit.Server;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RuntimeCompatibility {
    private static final int MINIMUM_JAVA = 25;
    private static final int SUPPORTED_MAJOR = 26;
    private static final int MINIMUM_MINOR = 1;
    private static final int MAXIMUM_MINOR = 3;
    private static final Pattern BUKKIT_RELEASE = Pattern.compile(
            "([0-9]+)\\.([0-9]+)(?:\\.([0-9]+))?(?:-R[0-9]+(?:\\.[0-9]+)*-SNAPSHOT|\\.build\\.[0-9]+-(?:alpha|beta|stable|recommended|experimental))?");

    private RuntimeCompatibility() {
    }

    public static void requireSupported(String bukkitVersion, int javaVersion) {
        if (javaVersion < MINIMUM_JAVA || !isSupported(bukkitVersion)) {
            throw new IllegalStateException("ByteCompanions requires Minecraft " + BuildInfo.minecraftRange()
                    + " (including patch releases) on Java " + MINIMUM_JAVA + " or newer; found Minecraft "
                    + bukkitVersion + " on Java " + javaVersion + ".");
        }
    }

    public static Selection require(Server server) {
        Objects.requireNonNull(server, "server");
        boolean paperApi = classAvailable("com.destroystokyo.paper.event.player.PlayerUseUnknownEntityEvent",
                server.getClass().getClassLoader());
        boolean regionized = FoliaScheduler.isRegionizedRuntime(server);
        return select(server.getBukkitVersion(), Runtime.version().feature(), paperApi, regionized);
    }

    public static String minecraftVersion(String bukkitVersion) {
        Matcher release = BUKKIT_RELEASE.matcher(Objects.requireNonNullElse(bukkitVersion, ""));
        if (!release.matches()) {
            throw new IllegalStateException("Unrecognized Bukkit version '" + bukkitVersion + "'");
        }
        return release.group(3) == null ? release.group(1) + "." + release.group(2)
                : release.group(1) + "." + release.group(2) + "." + release.group(3);
    }

    public static Selection select(String bukkitVersion, int javaVersion, boolean paperApi, boolean regionized) {
        requireSupported(bukkitVersion, javaVersion);
        if (regionized && !paperApi) {
            throw new IllegalStateException("ByteCompanions region-threaded support requires the Paper API; found Minecraft "
                    + bukkitVersion + " without it.");
        }
        boolean latestFamily = minor(bukkitVersion) == MAXIMUM_MINOR;
        CompanionAdapter adapter = paperApi
                ? latestFamily ? CompanionAdapter.V26_3 : CompanionAdapter.V26_1
                : latestFamily ? CompanionAdapter.SPIGOT_26_3 : CompanionAdapter.SPIGOT_26_1;
        return new Selection(minecraftVersion(bukkitVersion), adapter);
    }

    private static boolean isSupported(String bukkitVersion) {
        Matcher release = BUKKIT_RELEASE.matcher(Objects.requireNonNullElse(bukkitVersion, ""));
        if (!release.matches() || Integer.parseInt(release.group(1)) != SUPPORTED_MAJOR) {
            return false;
        }
        int minor = Integer.parseInt(release.group(2));
        return minor >= MINIMUM_MINOR && minor <= MAXIMUM_MINOR;
    }

    private static int minor(String bukkitVersion) {
        Matcher release = BUKKIT_RELEASE.matcher(bukkitVersion);
        if (!release.matches()) {
            throw new IllegalStateException("Unrecognized Bukkit version '" + bukkitVersion + "'");
        }
        return Integer.parseInt(release.group(2));
    }

    private static boolean classAvailable(String name, ClassLoader loader) {
        try {
            Class.forName(name, false, loader);
            return true;
        } catch (ClassNotFoundException | LinkageError exception) {
            return false;
        }
    }

    public enum CompanionAdapter {
        V26_1, V26_3, SPIGOT_26_1, SPIGOT_26_3
    }

    public record Selection(String minecraftVersion, CompanionAdapter companionAdapter) {
    }
}
