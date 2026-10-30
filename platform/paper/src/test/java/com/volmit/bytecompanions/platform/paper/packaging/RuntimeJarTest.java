package com.volmit.bytecompanions.platform.paper.packaging;

import net.kyori.adventure.text.Component;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.io.File;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.HttpURLConnection;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class RuntimeJarTest {
    private static final String PLUGIN_PACKAGE = "com.volmit.bytecompanions";
    private static final String LIBRARY_PACKAGE = "com.volmit.bytecompanions.libs.";
    private static RuntimeLoader loader;

    @TempDir
    Path directory;

    @BeforeAll
    static void openRuntimeLoader() throws Exception {
        Path artifact = Path.of(Objects.requireNonNull(System.getProperty("bytecompanions.runtimeJar")));
        Path libraries = Path.of(Objects.requireNonNull(System.getProperty("bytecompanions.runtimeLibraries")));
        assertTrue(Files.isRegularFile(artifact), artifact.toString());
        assertTrue(Files.isRegularFile(libraries), libraries.toString());
        loader = new RuntimeLoader(new URL[]{artifact.toUri().toURL(), libraries.toUri().toURL()},
                RuntimeJarTest.class.getClassLoader());
    }

    @AfterAll
    static void closeRuntimeLoader() throws Exception {
        if (loader != null) {
            loader.close();
        }
    }

    @Test
    void entrypointInitializesBeforeDownloadedLibrariesAreAvailable() throws Exception {
        Path artifact = Path.of(Objects.requireNonNull(System.getProperty("bytecompanions.runtimeJar")));
        try (RuntimeLoader isolated = new RuntimeLoader(new URL[]{artifact.toUri().toURL()},
                RuntimeJarTest.class.getClassLoader())) {
            Class<?> plugin = Class.forName(PLUGIN_PACKAGE + ".platform.paper.PaperPlugin", true, isolated);
            assertSame(isolated, plugin.getClassLoader());
            assertTrue(JavaPlugin.class.isAssignableFrom(plugin));
        }
    }

    @Test
    void entrypointAndBootstrapLoadFromTheDistribution() throws Exception {
        Class<?> plugin = loader.loadClass(PLUGIN_PACKAGE + ".platform.paper.PaperPlugin");
        assertSame(loader, plugin.getClassLoader());
        assertTrue(JavaPlugin.class.isAssignableFrom(plugin));
        assertNotNull(plugin.getConstructor());
        assertNotNull(plugin.getMethod("onEnable"));
        assertNotNull(plugin.getMethod("onDisable"));
        Class<?> bootstrap = loader.loadClass(LIBRARY_PACKAGE + "slimjar.app.builder.SpigotApplicationBuilder");
        assertSame(loader, bootstrap.getClassLoader());
        Class<?> nestedResources = loader.loadClass(PLUGIN_PACKAGE + ".platform.paper.bootstrap.NestedZipResources");
        assertSame(loader, nestedResources.getClassLoader());
        assertThrows(ClassNotFoundException.class,
                () -> loader.loadClass(LIBRARY_PACKAGE + "volmlib.util.io.NestedZipResources"));
    }

    @Test
    void maintainedBootstrapRetainsItsLicenseAndIsolatedLoaderAgent() throws Exception {
        Path artifact = Path.of(Objects.requireNonNull(System.getProperty("bytecompanions.runtimeJar")));
        try (RuntimeLoader isolated = new RuntimeLoader(new URL[]{artifact.toUri().toURL()},
                ClassLoader.getPlatformClassLoader()); JarFile jar = new JarFile(artifact.toFile())) {
            Class<?> connections = isolated.loadClass(LIBRARY_PACKAGE + "slimjar.util.Connections");
            assertSame(isolated, connections.getClassLoader());
            assertEquals(artifact.toUri().toURL(), connections.getProtectionDomain().getCodeSource().getLocation());
            assertNotNull(connections.getMethod("openConnection", URL.class));
            assertFalse(jar.stream().anyMatch(entry -> entry.getName().startsWith("io/github/slimjar/")));

            String licensePath = "META-INF/licenses/slimjar/LICENSE";
            assertNotNull(jar.getEntry(licensePath));
            String license = new String(jar.getInputStream(jar.getEntry(licensePath)).readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(license.contains("MIT License"));
            assertTrue(license.contains("Copyright (c) 2021 Vaishnav Anil"));
            assertTrue(license.contains("Permission is hereby granted, free of charge"));

            Class<?> modules = isolated.loadClass(LIBRARY_PACKAGE + "slimjar.util.Modules");
            URL agent = (URL) modules.getMethod("findModule", String.class).invoke(null, "loader-agent");
            assertTrue(agent.toExternalForm().startsWith("jar:" + artifact.toUri().toURL() + "!/"), agent.toString());
            try (JarInputStream contents = new JarInputStream(agent.openStream())) {
                boolean foundAgent = false;
                ZipEntry entry;
                while ((entry = contents.getNextEntry()) != null) {
                    if (entry.getName().equals("io/github/slimjar/injector/agent/ClassLoaderAgent.class")) {
                        assertTrue(contents.readAllBytes().length > 0);
                        foundAgent = true;
                    }
                }
                assertTrue(foundAgent);
            }
        }
    }

    @Test
    void packagedBootstrapSetsBothTimeoutsBeforeAnHttpRequest() throws Exception {
        Path artifact = Path.of(Objects.requireNonNull(System.getProperty("bytecompanions.runtimeJar")));
        try (RuntimeLoader isolated = new RuntimeLoader(new URL[]{artifact.toUri().toURL()},
                ClassLoader.getPlatformClassLoader())) {
            TimeoutConnection connection = new TimeoutConnection(new URL("https://bootstrap.invalid/library.jar"));
            URL url = controlledUrl(connection);
            Class<?> connections = isolated.loadClass(LIBRARY_PACKAGE + "slimjar.util.Connections");
            URLConnection opened = (URLConnection) connections.getMethod("createDownloadConnection", URL.class).invoke(null, url);
            assertSame(connection, opened);
            assertEquals(1, connection.responseRequests);
            assertFalse(connection.disconnected);
            assertTrue(connection.getRequestProperty("User-Agent").contains("SlimjarApplication"));
            try (InputStream body = opened.getInputStream()) {
                assertArrayEquals(new byte[]{1, 2, 3}, body.readAllBytes());
            }
            assertEquals(1, connection.bodyRequests);
        }
    }

    @Test
    void packagedBootstrapSetsBothTimeoutsForNonHttpConnections() throws Exception {
        Path artifact = Path.of(Objects.requireNonNull(System.getProperty("bytecompanions.runtimeJar")));
        try (RuntimeLoader isolated = new RuntimeLoader(new URL[]{artifact.toUri().toURL()},
                ClassLoader.getPlatformClassLoader())) {
            URLConnection connection = new URLConnection(new URL("file:/packaged-bootstrap")) {
                @Override
                public void connect() {
                    assertEquals(10_000, getConnectTimeout());
                    assertEquals(30_000, getReadTimeout());
                    connected = true;
                }

                @Override
                public InputStream getInputStream() {
                    connect();
                    return new ByteArrayInputStream(new byte[]{4, 5});
                }
            };
            Class<?> connections = isolated.loadClass(LIBRARY_PACKAGE + "slimjar.util.Connections");
            URLConnection opened = (URLConnection) connections.getMethod("createDownloadConnection", URL.class)
                    .invoke(null, controlledUrl(connection));
            assertSame(connection, opened);
            try (InputStream body = opened.getInputStream()) {
                assertArrayEquals(new byte[]{4, 5}, body.readAllBytes());
            }
        }
    }

    @Test
    void packagedBootstrapPreservesCausesOutsideTheSlimJarHierarchy() throws Exception {
        Path artifact = Path.of(Objects.requireNonNull(System.getProperty("bytecompanions.runtimeJar")));
        try (RuntimeLoader isolated = new RuntimeLoader(new URL[]{artifact.toUri().toURL()},
                ClassLoader.getPlatformClassLoader())) {
            IOException cause = new IOException("packaged bootstrap connection failure");
            IOException cleanup = new IOException("packaged bootstrap cleanup failure");
            cause.addSuppressed(cleanup);
            URL url = new URL(null, "https://bootstrap.invalid/library.jar", new URLStreamHandler() {
                @Override
                protected URLConnection openConnection(URL requested) throws IOException {
                    throw cause;
                }
            });
            Class<?> connections = isolated.loadClass(LIBRARY_PACKAGE + "slimjar.util.Connections");
            InvocationTargetException invocation = assertThrows(InvocationTargetException.class,
                    () -> connections.getMethod("openJarConnection", URL.class).invoke(null, url));
            Throwable failure = invocation.getCause();
            assertEquals(LIBRARY_PACKAGE + "slimjar.exceptions.ModuleExtractorException", failure.getClass().getName());
            assertSame(isolated, failure.getClass().getClassLoader());
            assertSame(cause, failure.getCause());
            assertArrayEquals(new Throwable[]{cleanup}, failure.getCause().getSuppressed());
        }
    }

    private static URL controlledUrl(URLConnection connection) throws Exception {
        return new URL(null, connection.getURL().toExternalForm(), new URLStreamHandler() {
            @Override
            protected URLConnection openConnection(URL requested) {
                assertEquals(connection.getURL().toExternalForm(), requested.toExternalForm());
                return connection;
            }
        });
    }

    private static final class TimeoutConnection extends HttpURLConnection {
        private int responseRequests;
        private int bodyRequests;
        private boolean disconnected;

        private TimeoutConnection(URL url) {
            super(url);
        }

        @Override
        public int getResponseCode() {
            assertTimeouts();
            responseRequests++;
            return HTTP_OK;
        }

        @Override
        public InputStream getInputStream() {
            assertTimeouts();
            bodyRequests++;
            return new ByteArrayInputStream(new byte[]{1, 2, 3});
        }

        @Override
        public void connect() {
            assertTimeouts();
            connected = true;
        }

        @Override
        public void disconnect() {
            disconnected = true;
        }

        @Override
        public boolean usingProxy() {
            return false;
        }

        private void assertTimeouts() {
            assertEquals(10_000, getConnectTimeout());
            assertEquals(30_000, getReadTimeout());
        }
    }

    @Test
    void publicCompanionApiPreservesTheServerAdventureType() throws Exception {
        Class<?> entity = loader.loadClass(PLUGIN_PACKAGE + ".api.companion.entity.CompanionEntity");
        assertSame(loader, entity.getClassLoader());
        assertSame(Component.class, entity.getMethod("customName").getReturnType());
        assertNotNull(entity.getMethod("customName", Component.class));
    }

    @Test
    void h2NestedResourcesPreserveEveryOriginalPayloadUnderTheRelocatedPath() throws Exception {
        Path artifact = Path.of(Objects.requireNonNull(System.getProperty("bytecompanions.runtimeJar")));
        Path relocatedLibrary = relocateH2Library();
        Path h2Library = Path.of(Objects.requireNonNull(System.getProperty("bytecompanions.h2Library")));
        String prefix = LIBRARY_PACKAGE.replace('.', '/') + "h2/";
        String resource = prefix + "util/data.zip";
        try (JarFile original = new JarFile(h2Library.toFile()); JarFile packaged = new JarFile(relocatedLibrary.toFile());
             JarFile plugin = new JarFile(artifact.toFile())) {
            Map<String, byte[]> expected = nestedResources(original.getInputStream(original.getEntry("org/h2/util/data.zip")).readAllBytes());
            byte[] nested = packaged.getInputStream(packaged.getEntry(resource)).readAllBytes();
            Map<String, byte[]> actual = nestedResources(nested);
            assertArrayEquals(Files.readAllBytes(Path.of(System.getProperty("bytecompanions.h2Resources"))), nested);
            assertEquals(97, expected.size());
            assertEquals(expected.size(), actual.size());
            assertTrue(expected.containsKey("META-INF/native-image/resource-config.json"));
            for (Map.Entry<String, byte[]> entry : expected.entrySet()) {
                String relocated = entry.getKey().startsWith("org/h2/")
                        ? prefix + entry.getKey().substring("org/h2/".length()) : entry.getKey();
                assertArrayEquals(entry.getValue(), actual.get(relocated), relocated);
            }
            assertFalse(plugin.stream().anyMatch(entry -> entry.getName().startsWith(prefix)));
        }
    }

    @Test
    void isolatedSlimJarLoaderUsesRelocatedDependencyResourcesForJdbcAndIoDiagnostics() throws Exception {
        Path relocatedLibrary = relocateH2Library();
        Class<?> isolatedType = loader.loadClass(LIBRARY_PACKAGE + "slimjar.injector.loader.IsolatedInjectableClassLoader");
        try (URLClassLoader isolated = (URLClassLoader) isolatedType.getConstructor(URL[].class, Collection.class, ClassLoader.class)
                .newInstance(new URL[]{relocatedLibrary.toUri().toURL()}, List.of(), ClassLoader.getPlatformClassLoader())) {
        Class<?> utils = isolated.loadClass(LIBRARY_PACKAGE + "h2.util.Utils");
        assertSame(isolated, utils.getClassLoader());
        URL selected = utils.getResource("data.zip");
        assertNotNull(selected);
        String artifact = relocatedLibrary.toUri().toURL().toExternalForm();
        assertTrue(selected.toExternalForm().startsWith("jar:" + artifact + "!/"), selected.toString());
        Driver driver = (Driver) isolated.loadClass(LIBRARY_PACKAGE + "h2.Driver").getConstructor().newInstance();
        try (Connection connection = driver.connect("jdbc:h2:mem:packaged-diagnostics", new Properties());
             Statement statement = connection.createStatement()) {
            SQLException failure = assertThrows(SQLException.class, () -> statement.execute("THIS IS NOT SQL"));
            assertTrue(failure.getErrorCode() == 42000 || failure.getErrorCode() == 42001);
            assertTrue(failure.getMessage().contains("Syntax error"), failure.getMessage());
            assertFalse(failure.getMessage().contains("not found"), failure.getMessage());
        }
        IOException cause = new IOException("packaged diagnostic cause");
        Class<?> exception = isolated.loadClass(LIBRARY_PACKAGE + "h2.message.DbException");
        SQLException failure = (SQLException) exception.getMethod("getJdbcSQLException", int.class, Throwable.class, String[].class)
                .invoke(null, 90031, cause, new String[]{"packaged diagnostic cause", "fixture"});
        assertEquals(90031, failure.getErrorCode());
        assertTrue(failure.getMessage().contains("IO Exception"), failure.getMessage());
        assertFalse(failure.getMessage().contains("not found"), failure.getMessage());
        assertSame(cause, failure.getCause());
        }
    }

    private Path relocateH2Library() throws Exception {
        Class<?> relocatorType = loader.loadClass(LIBRARY_PACKAGE + "slimjar.relocation.Relocator");
        Object delegate = Proxy.newProxyInstance(loader, new Class<?>[]{relocatorType}, (proxy, method, arguments) -> {
            if (!method.getName().equals("relocate")) throw new UnsupportedOperationException(method.getName());
            Files.copy(((File) arguments[0]).toPath(), ((File) arguments[1]).toPath(), StandardCopyOption.REPLACE_EXISTING);
            return null;
        });
        Class<?> ruleType = loader.loadClass(LIBRARY_PACKAGE + "slimjar.relocation.RelocationRule");
        Object rule = ruleType.getConstructor(String.class, String.class).newInstance("org.h2", LIBRARY_PACKAGE + "h2");
        Class<?> wrapperType = loader.loadClass(PLUGIN_PACKAGE + ".platform.paper.bootstrap.H2ResourceRelocator");
        Object wrapper = wrapperType.getConstructor(relocatorType, ruleType).newInstance(delegate, rule);
        Path output = directory.resolve("relocated-libraries.jar");
        Path input = Path.of(System.getProperty("bytecompanions.runtimeLibraries"));
        relocatorType.getMethod("relocate", File.class, File.class).invoke(wrapper, input.toFile(), output.toFile());
        return output;
    }

    private static Map<String, byte[]> nestedResources(byte[] bytes) throws IOException {
        Map<String, byte[]> resources = new TreeMap<>();
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                assertFalse(entry.isDirectory());
                assertFalse(resources.containsKey(entry.getName()), entry.getName());
                resources.put(entry.getName(), input.readAllBytes());
            }
        }
        return resources;
    }

    @Test
    void rootConfigurationRetainsReflectiveFeatureAndPerformanceFields() throws Exception {
        Class<?> service = loader.loadClass(PLUGIN_PACKAGE + ".platform.paper.configuration.ConfigService");
        Object prepared = service.getMethod("prepare", String.class).invoke(null, """
                [features]
                attributes = false
                buffs = true
                decorations = false
                effects = true
                particles = false
                progression = true
                boosters = true
                [menus]
                rightClickDashboard = false
                [performance]
                dispatchInteractEvent = true
                [general]
                language = "de_DE"
                [hotReload]
                enabled = false
                [storage.h2]
                directory = "selected-companions"
                [storage.pool]
                maxPoolSize = 4
                """);
        Object configuration = prepared.getClass().getMethod("configuration").invoke(prepared);
        assertSame(loader, configuration.getClass().getClassLoader());
        Object features = configuration.getClass().getMethod("features").invoke(configuration);
        for (String feature : List.of("attributes", "decorations", "particles")) {
            assertEquals(false, features.getClass().getMethod("enabled", String.class).invoke(features, feature), feature);
        }
        for (String feature : List.of("buffs", "effects", "progression", "boosters")) {
            assertEquals(true, features.getClass().getMethod("enabled", String.class).invoke(features, feature), feature);
        }
        assertEquals(true, configuration.getClass().getMethod("dispatchInteractEvent").invoke(configuration));
        assertEquals(false, configuration.getClass().getMethod("openDashboardOnInteract").invoke(configuration));
        Object general = configuration.getClass().getMethod("general").invoke(configuration);
        assertEquals("de_DE", general.getClass().getMethod("language").invoke(general));
        Object hotReload = configuration.getClass().getMethod("hotReload").invoke(configuration);
        assertEquals(false, hotReload.getClass().getMethod("enabled").invoke(hotReload));
        Object storage = configuration.getClass().getMethod("storage").invoke(configuration);
        Object h2 = storage.getClass().getMethod("h2").invoke(storage);
        Object pool = storage.getClass().getMethod("pool").invoke(storage);
        assertEquals("selected-companions", h2.getClass().getMethod("directory").invoke(h2));
        assertEquals(4, pool.getClass().getMethod("maxPoolSize").invoke(pool));
    }

    @Test
    void distributionRetainsMojangManifestAndOnlyTomlDataConfiguration() throws Exception {
        Path artifact = Path.of(Objects.requireNonNull(System.getProperty("bytecompanions.runtimeJar")));
        try (JarFile jar = new JarFile(artifact.toFile())) {
            assertNotNull(jar.getManifest());
            assertEquals("mojang", jar.getManifest().getMainAttributes().getValue("paperweight-mappings-namespace"));
            assertNotNull(jar.getEntry("plugin.yml"));
            assertNotNull(jar.getEntry("config.toml"));
            assertNotNull(jar.getEntry("features/progression/config.toml"));
            assertFalse(jar.stream().anyMatch(entry -> (entry.getName().endsWith(".yml")
                    || entry.getName().endsWith(".yaml")) && !entry.getName().equals("plugin.yml")));
            assertFalse(jar.stream().anyMatch(entry -> entry.getName().startsWith("com/volmit/bytecompanions/platform/paper/extension/")
                    || entry.getName().startsWith("com/volmit/bytecompanions/api/extension/")
                    || entry.getName().startsWith("com/volmit/bytecompanions/api/bukkit/extension/")));
            assertFalse(jar.stream().anyMatch(entry -> entry.getName().equals("config.yml")
                    || entry.getName().equals("messages.yml")
                    || entry.getName().equals("paper-plugin.yml") || entry.getName().equals("paper-libraries.json")));
            Class<?> service = loader.loadClass(PLUGIN_PACKAGE + ".platform.paper.configuration.ConfigService");
            Object prepared = service.getMethod("prepare", String.class).invoke(null, resource("config.toml"));
            assertNotNull(prepared);
        }
    }

    @Test
    void progressionStorageRecordsRoundTripThroughThePackagedSerializer() throws Exception {
        Class<?> gsonType = loader.loadClass(LIBRARY_PACKAGE + "gson.Gson");
        Object gson = gsonType.getConstructor().newInstance();
        String booster = """
                {"activationId":"activation","boosterId":"training","scope":"PERSONAL",
                "percentages":{"companion_xp":50.0},"startsAt":1000,"expiresAt":61000}
                """;
        Map<String, String> records = Map.of(
                "PlayerState", """
                        {"revision":7,"companions":{"bee":{"xp":125,"tiers":{"speed":1},"xpRemainderMicros":250000}},
                        "credits":{"training":2},"boosters":[%s],"checkpoint":2000,"online":true}
                        """.formatted(booster),
                "GlobalState", "{\"boosters\":[" + booster + "]}",
                "PurchaseJournal", """
                        {"transactionId":"purchase","playerId":"619cf279-dacf-38a5-8d2f-e447c526ee3b",
                        "kind":"UPGRADE","companionId":"bee","definitionId":"speed","expectedTier":0,
                        "cost":{"money":25.0,"items":{"EMERALD":2}},"status":"UNKNOWN",
                        "provider":"test","createdAt":1000,"error":"uncertain"}
                        """);
        for (Map.Entry<String, String> entry : records.entrySet()) {
            Class<?> type = loader.loadClass(PLUGIN_PACKAGE + ".platform.paper.progression.ProgressionService$" + entry.getKey());
            Object value = gsonType.getMethod("fromJson", String.class, Class.class).invoke(gson, entry.getValue(), type);
            assertSame(loader, value.getClass().getClassLoader());
            String restored = (String) gsonType.getMethod("toJson", Object.class).invoke(gson, value);
            assertEquals(com.google.gson.JsonParser.parseString(entry.getValue()),
                    com.google.gson.JsonParser.parseString(restored), entry.getKey());
        }
    }

    @Test
    void onlyEnglishShipsInTheJarAndTheManifestNamesTheOtherLocales() throws Exception {
        Path artifact = Path.of(Objects.requireNonNull(System.getProperty("bytecompanions.runtimeJar")));
        Properties manifest = new Properties();
        try (JarFile jar = new JarFile(artifact.toFile())) {
            List<String> catalogs = jar.stream().map(java.util.zip.ZipEntry::getName)
                    .filter(name -> name.startsWith("languages/") && name.endsWith(".toml")).toList();
            assertEquals(List.of("languages/en_US.toml"), catalogs);
            try (InputStream input = jar.getInputStream(jar.getJarEntry("bytecompanions-language-source.properties"))) {
                manifest.load(input);
            }
        }
        Class<?> locales = loader.loadClass(LIBRARY_PACKAGE + "volmlib.util.localization.VolmitLocales");
        List<?> nonEnglish = ((List<?>) locales.getMethod("all").invoke(null)).stream().filter(locale -> !locale.equals("en_US")).toList();
        assertEquals(17, nonEnglish.size());
        assertEquals(String.join(",", nonEnglish.stream().map(Object::toString).toList()), manifest.getProperty("locales"));
        assertFalse(manifest.getProperty("revision").isBlank());
        Map<?, ?> english = parseLanguage(resource("languages/en_US.toml"));
        assertEquals(560, english.size());
    }

    private static Map<?, ?> parseLanguage(String source) throws Exception {
        Class<?> parser = loader.loadClass(LIBRARY_PACKAGE + "volmlib.util.localization.TomlLanguageParser");
        return (Map<?, ?>) parser.getMethod("parseText", String.class).invoke(null, source);
    }

    private static String resource(String path) throws Exception {
        URL resource = loader.findResource(path);
        assertNotNull(resource, path);
        try (InputStream input = resource.openStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void allParticleFactoriesRetainIndependentEffectState() throws Exception {
        Class<?> feature = loader.loadClass(PLUGIN_PACKAGE + ".features.particles.ParticlesFeature");
        Class<?> adapterType = loader.loadClass(PLUGIN_PACKAGE + ".features.particles.adapternms.v26_1.V26_1NMSAdapter");
        Field adapter = feature.getDeclaredField("nmsAdapter");
        adapter.setAccessible(true);
        Object previous = adapter.get(null);
        adapter.set(null, adapterType.getConstructor().newInstance());
        try {
            Class<?> catalog = loader.loadClass(PLUGIN_PACKAGE + ".features.particles.effect.ParticleEffectCatalog");
            Map<?, ?> factories = (Map<?, ?>) catalog.getMethod("factories").invoke(null);
            assertEquals(34, factories.size());
            for (Map.Entry<?, ?> entry : factories.entrySet()) {
                Supplier<?> factory = (Supplier<?>) entry.getValue();
                Object first = factory.get();
                Object second = factory.get();
                assertNotNull(first, entry.getKey().toString());
                assertSame(loader, first.getClass().getClassLoader());
                assertNotSame(first, second, entry.getKey().toString());
            }
        } finally {
            adapter.set(null, previous);
        }
    }

    private Object readConfiguration(String toml, String className) throws Exception {
        Class<?> options = loader.loadClass(LIBRARY_PACKAGE + "configurate.ConfigurationOptions");
        Class<?> content = loader.loadClass(PLUGIN_PACKAGE + ".platform.paper.configuration.ContentSnapshot");
        Object node = content.getMethod("parse", String.class, options)
                .invoke(null, toml, options.getMethod("defaults").invoke(null));
        Class<?> nodeType = loader.loadClass(LIBRARY_PACKAGE + "configurate.ConfigurationNode");
        Class<?> configurationType = loader.loadClass(className);
        Object configuration = nodeType.getMethod("get", Class.class).invoke(node, configurationType);
        assertNotNull(configuration);
        assertSame(loader, configuration.getClass().getClassLoader());
        return configuration;
    }

    private static final class RuntimeLoader extends URLClassLoader {
        private RuntimeLoader(URL[] artifacts, ClassLoader parent) {
            super(artifacts, parent);
        }

        @Override
        public URL getResource(String name) {
            if (name.startsWith("com/volmit/bytecompanions/") || name.startsWith("languages/")
                    || name.startsWith("features/") || name.startsWith("companions/") || name.equals("config.toml")
                    || name.startsWith("modules/") || name.startsWith("META-INF/licenses/slimjar/")) {
                return findResource(name);
            }
            return super.getResource(name);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!name.startsWith(PLUGIN_PACKAGE) && !name.startsWith("com.volmit.bytecompanions.")) {
                return super.loadClass(name, resolve);
            }
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) {
                    loaded = findClass(name);
                }
                if (resolve) {
                    resolveClass(loaded);
                }
                return loaded;
            }
        }
    }
}
