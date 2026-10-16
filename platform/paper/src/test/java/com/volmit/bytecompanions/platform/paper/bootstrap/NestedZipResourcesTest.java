package com.volmit.bytecompanions.platform.paper.bootstrap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NestedZipResourcesTest {
    private static final NestedZipResources.Limits LIMITS = new NestedZipResources.Limits(20, 4096, 1024, 4096, 20, 8192);
    private static final Map<String, String> PREFIXES = Map.of("original/library/", "private/library/");
    @TempDir
    private Path directory;

    @Test
    public void relocatesOnlySelectedArchiveNamesAndPreservesEveryPayload() throws Exception {
        byte[] nested = zip(Map.of("original/library/messages.txt", new byte[]{1, 2, 3},
                "META-INF/config.json", new byte[]{4, 5}));
        Path library = library(nested);
        assertTrue(NestedZipResources.relocate(library, "private/library/data.zip", PREFIXES, LIMITS));
        try (ZipFile output = new ZipFile(library.toFile())) {
            assertArrayEquals(new byte[]{9, 8, 7}, output.getInputStream(output.getEntry("other.class")).readAllBytes());
            Map<String, byte[]> entries = entries(output.getInputStream(output.getEntry("private/library/data.zip")).readAllBytes());
            assertEquals(2, entries.size());
            assertArrayEquals(new byte[]{1, 2, 3}, entries.get("private/library/messages.txt"));
            assertArrayEquals(new byte[]{4, 5}, entries.get("META-INF/config.json"));
        }
    }

    @Test
    public void outputIsDeterministicAndRepeatedRelocationIsStable() throws Exception {
        Map<String, byte[]> original = new LinkedHashMap<>();
        original.put("original/library/z", new byte[]{3});
        original.put("original/library/a", new byte[]{1});
        Path library = library(zip(original));
        NestedZipResources.relocate(library, "private/library/data.zip", PREFIXES, LIMITS);
        byte[] once = Files.readAllBytes(library);
        NestedZipResources.relocate(library, "private/library/data.zip", PREFIXES, LIMITS);
        assertArrayEquals(once, Files.readAllBytes(library));
        try (ZipFile output = new ZipFile(library.toFile());
             ZipInputStream nested = new ZipInputStream(output.getInputStream(output.getEntry("private/library/data.zip")))) {
            ZipEntry first = nested.getNextEntry();
            assertEquals("private/library/a", first.getName());
            assertEquals(0, first.getTime());
        }
    }

    @Test
    public void missingSelectedResourceLeavesLibraryUntouched() throws Exception {
        Path library = library(zip(Map.of("original/library/a", new byte[]{1})));
        byte[] original = Files.readAllBytes(library);
        assertFalse(NestedZipResources.relocate(library, "missing.zip", PREFIXES, LIMITS));
        assertArrayEquals(original, Files.readAllBytes(library));
    }

    @Test
    public void unsafeNamesAndCollisionsFailWithoutChangingLibrary() throws Exception {
        for (String name : new String[]{"../escape", "/absolute", "C:/drive", "a\\b", "a//b", "a/./b"}) {
            rejects(zip(Map.of(name, new byte[]{1})), LIMITS);
        }
        rejects(zip(Map.of("original/library/a", new byte[]{1}, "private/library/a", new byte[]{2})), LIMITS);
        Path library = library(zip(Map.of("original/library/a", new byte[]{1})));
        assertThrows(IOException.class, () -> NestedZipResources.relocate(library, "private/library/data.zip",
                Map.of("original/library/", "../escape/"), LIMITS));
    }

    @Test
    public void boundedExpansionEntryCountAndMalformedArchivesFailWithoutChangingLibrary() throws Exception {
        rejects(new byte[]{0, 1, 2}, LIMITS);
        rejects(zip(Map.of("a", new byte[1025])), LIMITS);
        rejects(zip(Map.of("a", new byte[600], "b", new byte[600])), new NestedZipResources.Limits(20, 4096, 1024, 1000, 20, 8192));
        rejects(zip(Map.of("a", new byte[1], "b", new byte[1])), new NestedZipResources.Limits(1, 4096, 1024, 4096, 20, 8192));
        rejects(zip(Map.of("a", new byte[1])), new NestedZipResources.Limits(20, 10, 1024, 4096, 20, 8192));
        rejects(zip(Map.of("a", new byte[1])), new NestedZipResources.Limits(20, 4096, 1024, 4096, 1, 8192));
        rejects(zip(Map.of("a", new byte[1])), new NestedZipResources.Limits(20, 4096, 1024, 4096, 20, 1));
    }

    private void rejects(byte[] nested, NestedZipResources.Limits limits) throws Exception {
        Path library = library(nested);
        byte[] original = Files.readAllBytes(library);
        assertThrows(IOException.class, () -> NestedZipResources.relocate(library, "private/library/data.zip", PREFIXES, limits));
        assertArrayEquals(original, Files.readAllBytes(library));
        try (Stream<Path> files = Files.list(directory)) {
            assertFalse(files.anyMatch(path -> path.getFileName().toString().startsWith(".nested-resources-")));
        }
    }

    private Path library(byte[] nested) throws IOException {
        Path library = directory.resolve("library.jar");
        Files.write(library, zip(Map.of("other.class", new byte[]{9, 8, 7}, "private/library/data.zip", nested)));
        return library;
    }

    private static byte[] zip(Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream output = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                ZipEntry zipEntry = new ZipEntry(entry.getKey());
                zipEntry.setTime(0);
                output.putNextEntry(zipEntry);
                output.write(entry.getValue());
                output.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private static Map<String, byte[]> entries(byte[] archive) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                entries.put(entry.getName(), input.readAllBytes());
            }
        }
        return entries;
    }
}
