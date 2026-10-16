package com.volmit.bytecompanions.qa;

import org.bukkit.plugin.Plugin;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

final class H2DiagnosticsFixture {
    private H2DiagnosticsFixture() {
    }

    static String inspect(Plugin fixture) {
        try {
            Path instance = fixture.getDataFolder().toPath().toAbsolutePath().getParent().getParent();
            if (!Files.readString(instance.resolve(".server-source")).matches("(?s).*isolated=true\\s*(?:\\R|$).*")) {
                throw new IllegalStateException("H2 diagnostic fixture requires an isolated instance");
            }
            Plugin plugin = fixture.getServer().getPluginManager().getPlugin("ByteCompanions");
            if (plugin == null || !plugin.isEnabled()) throw new IllegalStateException("ByteCompanions is not enabled");
            ClassLoader loader = plugin.getClass().getClassLoader();
            String prefix = "com.volmit.bytecompanions.libs.h2.";
            Class<?> utility = Class.forName(prefix + "util.Utils", true, loader);
            Class<?> driverType = Class.forName(prefix + "Driver", true, loader);
            URL resource = utility.getResource("data.zip");
            if (resource == null || !(resource.openConnection() instanceof JarURLConnection archive)) {
                throw new IllegalStateException("Expected H2 diagnostic resource inside its relocated JAR: " + resource);
            }
            Path libraries = plugin.getDataFolder().toPath().resolve(".libs").toRealPath();
            Path resourceJar = Path.of(archive.getJarFileURL().toURI()).toRealPath();
            Path utilityJar = Path.of(utility.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();
            Path driverJar = Path.of(driverType.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();
            if (!resourceJar.startsWith(libraries) || !resourceJar.equals(utilityJar) || !resourceJar.equals(driverJar)
                    || !resourceJar.endsWith(Path.of("relocated", "ByteCompanions", "h2-2.4.240.jar"))
                    || utility.getClassLoader() != driverType.getClassLoader()
                    || !archive.getEntryName().equals("com/volmit/bytecompanions/libs/h2/util/data.zip")) {
                throw new IllegalStateException("H2 classes and diagnostics do not share the owned relocated library: " + resource);
            }
            byte[] nested;
            archive.setUseCaches(false);
            try (InputStream input = archive.getInputStream()) {
                nested = input.readNBytes(8 * 1024 * 1024 + 1);
            }
            if (nested.length > 8 * 1024 * 1024) throw new IllegalStateException("H2 diagnostic archive exceeds fixture limit");
            Set<String> names = new HashSet<>();
            try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(nested))) {
                for (ZipEntry entry; (entry = zip.getNextEntry()) != null;) {
                    if (!names.add(entry.getName()) || entry.getName().startsWith("org/h2/")) {
                        throw new IllegalStateException("Duplicate or unrelocated H2 diagnostic entry: " + entry.getName());
                    }
                }
            }
            if (names.size() != 97 || !names.contains("META-INF/native-image/resource-config.json")
                    || !names.contains("com/volmit/bytecompanions/libs/h2/res/_messages_en.prop")) {
                throw new IllegalStateException("Relocated H2 archive is missing expected resources: " + names.size());
            }
            String nestedHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(nested));
            Driver driver = (Driver) driverType.getConstructor().newInstance();
            int syntaxCode;
            try (Connection connection = driver.connect("jdbc:h2:mem:bcqa_diagnostics_" + UUID.randomUUID(), new Properties());
                 Statement statement = connection.createStatement()) {
                try {
                    statement.execute("SELECT FROM");
                    throw new IllegalStateException("Invalid H2 query unexpectedly succeeded");
                } catch (SQLException failure) {
                    syntaxCode = failure.getErrorCode();
                    if ((syntaxCode != 42000 && syntaxCode != 42001) || !failure.getMessage().contains("Syntax error")
                            || failure.getMessage().contains("not found")) {
                        throw new IllegalStateException("H2 syntax diagnostic is missing", failure);
                    }
                }
            }
            IOException cause = new IOException("BCQA diagnostic cause");
            SQLException io = (SQLException) Class.forName(prefix + "message.DbException", true, loader)
                    .getMethod("getJdbcSQLException", int.class, Throwable.class, String[].class)
                    .invoke(null, 90031, cause, new String[]{"BCQA diagnostic cause", "fixture"});
            if (io.getErrorCode() != 90031 || !io.getMessage().contains("IO Exception")
                    || io.getMessage().contains("not found") || io.getCause() != cause) {
                throw new IllegalStateException("H2 I/O diagnostic or cause is missing", io);
            }
            return "BCQA H2_DIAGNOSTICS PASS resource=" + resource + " nestedSha256=" + nestedHash
                    + " entries=" + names.size() + " syntaxCode=" + syntaxCode + " ioCode=90031 cause=true";
        } catch (ReflectiveOperationException | IOException | SQLException | URISyntaxException | NoSuchAlgorithmException failure) {
            throw new IllegalStateException("Could not verify packaged H2 diagnostics", failure);
        }
    }
}
