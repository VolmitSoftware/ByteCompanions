package com.volmit.bytecompanions.platform.paper.compatibility;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.Properties;

public final class BuildInfo {
    private static final String RESOURCE = "/bytecompanions-build.properties";
    private static final Properties PROPERTIES = load();

    private BuildInfo() {
    }

    public static String minecraftRange() {
        return Objects.requireNonNull(PROPERTIES.getProperty("minecraftRange"), "minecraftRange");
    }

    public static String version() {
        return Objects.requireNonNull(PROPERTIES.getProperty("version"), "version");
    }

    private static Properties load() {
        Properties properties = new Properties();
        try (InputStream input = BuildInfo.class.getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("Missing build resource " + RESOURCE);
            }
            properties.load(input);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        return properties;
    }
}
