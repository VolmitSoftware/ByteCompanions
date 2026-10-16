package io.github.slimjar;

import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipFile;

import static org.junit.Assert.*;

public class RuntimeProvenanceTest {
    @Test
    public void unchangedSourcesMatchPinnedArchive() throws Exception {
        Path sources = Path.of(System.getProperty("slimjar.sourceDirectory"));
        Set<String> changed = new HashSet<>(Files.readAllLines(Path.of(System.getProperty("slimjar.changedSources"))));
        String manifest = Files.readString(Path.of(System.getProperty("slimjar.upstreamManifest")));
        Matcher entries = Pattern.compile("\"([^\"]+\\.java)\"\\s*:\\s*\"([a-f0-9]{64})\"").matcher(manifest);
        int count = 0;
        while (entries.find()) {
            String name = entries.group(1);
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(sources.resolve(name))));
            if (changed.remove(name)) assertNotEquals(name, entries.group(2), hash);
            else assertEquals(name, entries.group(2), hash);
            count++;
        }
        assertEquals(125, count);
        assertTrue(changed.toString(), changed.isEmpty());
        assertEquals("2.1.9", BuildConstants.VERSION);
        assertEquals("9.8", BuildConstants.ASM);
    }

    @Test
    public void loaderAgentAndLicenseArePackagedResources() throws Exception {
        boolean found = false;
        try (InputStream resource = getClass().getClassLoader().getResourceAsStream("modules/loader-agent.isolated-jar")) {
            assertNotNull(resource);
            try (ZipInputStream zip = new ZipInputStream(resource)) {
                for (ZipEntry entry; (entry = zip.getNextEntry()) != null;) {
                    if (entry.getName().equals("io/github/slimjar/injector/agent/ClassLoaderAgent.class")) {
                        byte[] bytes = zip.readAllBytes();
                        assertTrue(bytes.length > 100);
                        assertEquals(69, ((bytes[6] & 255) << 8) | (bytes[7] & 255));
                        found = true;
                    }
                }
            }
        }
        assertTrue(found);
        try (InputStream resource = getClass().getClassLoader().getResourceAsStream("META-INF/licenses/slimjar/LICENSE")) {
            assertNotNull(resource);
            String notice = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(notice.contains("Copyright (c) 2021 Vaishnav Anil"));
            assertTrue(notice.contains("Permission is hereby granted"));
        }
    }

    @Test
    public void publishedSourcesIncludeLoaderAgentAndProvenance() throws Exception {
        try (ZipFile archive = new ZipFile(System.getProperty("slimjar.sourcesArchive"))) {
            assertEquals(125, archive.stream().filter(entry -> entry.getName().startsWith("io/github/slimjar/")
                    && entry.getName().endsWith(".java")).count());
            assertNotNull(archive.getEntry("loader-agent/io/github/slimjar/injector/agent/ClassLoaderAgent.java"));
            for (String name : Set.of("UPSTREAM.md", "upstream-sources.json", "upstream-changes.txt", "LICENSE")) {
                assertNotNull(name, archive.getEntry(name));
            }
            try (InputStream resource = archive.getInputStream(archive.getEntry("upstream-sources.json"))) {
                assertEquals(Files.readString(Path.of(System.getProperty("slimjar.upstreamManifest"))),
                        new String(resource.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
    }
}
