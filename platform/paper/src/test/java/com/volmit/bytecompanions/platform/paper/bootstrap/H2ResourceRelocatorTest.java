package com.volmit.bytecompanions.platform.paper.bootstrap;

import io.github.slimjar.exceptions.RelocatorException;
import io.github.slimjar.relocation.RelocationRule;
import io.github.slimjar.relocation.Relocator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class H2ResourceRelocatorTest {
    @TempDir Path directory;

    @Test
    void failedNestedRewritePreservesPreviouslyPublishedLibrary() throws Exception {
        Path output = directory.resolve("cached.jar");
        byte[] previous = new byte[]{1, 2, 3};
        Files.write(output, previous);
        Relocator delegate = (input, target) -> {
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(target.toPath()))) {
                zip.putNextEntry(new ZipEntry("private/h2/util/data.zip"));
                zip.write(new byte[]{0, 1, 2});
                zip.closeEntry();
            } catch (IOException failure) {
                throw new RelocatorException("Fixture failed", failure);
            }
        };
        H2ResourceRelocator relocator = new H2ResourceRelocator(delegate, new RelocationRule("org.h2", "private.h2"));
        assertThrows(RelocatorException.class, () -> relocator.relocate(directory.resolve("input.jar").toFile(), output.toFile()));
        assertArrayEquals(previous, Files.readAllBytes(output));
        assertNoTemporaryFiles();
    }

    @Test
    void failedDelegateCannotLeaveUnpatchedOutputForTheCache() throws Exception {
        Path output = directory.resolve("cached.jar");
        Relocator delegate = (input, target) -> {
            try {
                Files.write(target.toPath(), new byte[]{1, 2, 3});
            } catch (IOException failure) {
                throw new RelocatorException("Fixture failed", failure);
            }
            throw new RelocatorException("Fixture failure after partial write");
        };
        H2ResourceRelocator relocator = new H2ResourceRelocator(delegate, new RelocationRule("org.h2", "private.h2"));
        assertThrows(RelocatorException.class, () -> relocator.relocate(directory.resolve("input.jar").toFile(), output.toFile()));
        assertFalse(Files.exists(output));
        assertNoTemporaryFiles();
    }

    @Test
    void failedCleanupIsSuppressedOnOriginalDelegateFailure() throws Exception {
        Path output = directory.resolve("cached.jar");
        byte[] previous = new byte[]{1, 2, 3};
        Files.write(output, previous);
        RelocatorException original = new RelocatorException("Original relocation failure");
        Relocator delegate = (input, target) -> {
            obstructCleanup(target.toPath());
            throw original;
        };
        H2ResourceRelocator relocator = new H2ResourceRelocator(delegate, new RelocationRule("org.h2", "private.h2"));
        RelocatorException failure = assertThrows(RelocatorException.class,
                () -> relocator.relocate(directory.resolve("input.jar").toFile(), output.toFile()));
        assertSame(original, failure);
        assertEquals(1, failure.getSuppressed().length);
        assertInstanceOf(DirectoryNotEmptyException.class, failure.getSuppressed()[0]);
        assertArrayEquals(previous, Files.readAllBytes(output));
    }

    @Test
    void failedCleanupPreservesUncheckedFailureIdentity() {
        AssertionError original = new AssertionError("Original relocation error");
        Relocator delegate = (input, target) -> {
            obstructCleanup(target.toPath());
            throw original;
        };
        H2ResourceRelocator relocator = new H2ResourceRelocator(delegate, new RelocationRule("org.h2", "private.h2"));
        AssertionError failure = assertThrows(AssertionError.class,
                () -> relocator.relocate(directory.resolve("input.jar").toFile(), directory.resolve("cached.jar").toFile()));
        assertSame(original, failure);
        assertEquals(1, failure.getSuppressed().length);
        assertInstanceOf(DirectoryNotEmptyException.class, failure.getSuppressed()[0]);
        assertFalse(Files.exists(directory.resolve("cached.jar")));
    }

    @Test
    void failedCleanupRetainsNestedRewriteIoCause() throws Exception {
        Path output = directory.resolve("cached.jar");
        byte[] previous = new byte[]{1, 2, 3};
        Files.write(output, previous);
        Relocator delegate = (input, target) -> obstructCleanup(target.toPath());
        H2ResourceRelocator relocator = new H2ResourceRelocator(delegate, new RelocationRule("org.h2", "private.h2"));
        RelocatorException failure = assertThrows(RelocatorException.class,
                () -> relocator.relocate(directory.resolve("input.jar").toFile(), output.toFile()));
        assertInstanceOf(IOException.class, failure.getCause());
        assertEquals(1, failure.getSuppressed().length);
        assertInstanceOf(DirectoryNotEmptyException.class, failure.getSuppressed()[0]);
        assertArrayEquals(previous, Files.readAllBytes(output));
    }

    private void assertNoTemporaryFiles() throws IOException {
        try (Stream<Path> files = Files.list(directory)) {
            assertFalse(files.anyMatch(path -> path.getFileName().toString().startsWith(".relocating-resources-")));
        }
    }

    private void obstructCleanup(Path temporary) {
        try {
            Files.delete(temporary);
            Files.createDirectory(temporary);
            Files.write(temporary.resolve("retained"), new byte[]{1});
        } catch (IOException failure) {
            throw new AssertionError("Could not create cleanup-failure fixture", failure);
        }
    }
}
