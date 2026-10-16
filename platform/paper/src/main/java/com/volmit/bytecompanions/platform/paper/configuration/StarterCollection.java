package com.volmit.bytecompanions.platform.paper.configuration;

import art.arcane.volmlib.util.config.TomlDocumentEditor;
import com.google.gson.JsonPrimitive;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class StarterCollection {
    private static final List<String> IDS = List.of("rainbow", "bee", "myself", "copper", "warrior",
            "lantern", "mushroom", "scout");

    private StarterCollection() {
    }

    public static List<String> ids() {
        return IDS;
    }

    public static Installation installMissing(Path dataFolder) throws IOException {
        return installMissing(dataFolder, path -> StarterCollection.class.getResourceAsStream("/" + path));
    }

    static Installation installMissing(Path dataFolder, Resources resources) throws IOException {
        Path directory = Objects.requireNonNull(dataFolder, "dataFolder").resolve("companions");
        Map<String, byte[]> bundled = new LinkedHashMap<>();
        for (String id : IDS) {
            try (InputStream resource = resources.open("companions/" + id + ".toml")) {
                if (resource == null) {
                    throw new IOException("Missing bundled companion: " + id);
                }
                bundled.put(id, resource.readAllBytes());
            }
        }
        Files.createDirectories(dataFolder);
        boolean createdDirectory = false;
        try {
            Files.createDirectory(directory);
            createdDirectory = true;
        } catch (FileAlreadyExistsException existing) {
            if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Companions must be a directory, not a file or symbolic link", existing);
            }
        }
        Installation installation = new Installation(directory, createdDirectory);
        try {
            for (Map.Entry<String, byte[]> entry : bundled.entrySet()) {
                installation.add(entry.getKey(), entry.getValue());
            }
            return installation;
        } catch (IOException failure) {
            try {
                installation.rollback();
            } catch (IOException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            throw failure;
        }
    }

    public enum Profile {
        COSMETIC,
        SURVIVAL;

        String apply(String source) throws IOException {
            String updated = source;
            for (String feature : List.of("attributes", "buffs", "effects")) {
                updated = TomlDocumentEditor.set(updated, List.of("features", feature),
                        new JsonPrimitive(this == SURVIVAL));
            }
            updated = TomlDocumentEditor.set(updated, List.of("features", "particles"), new JsonPrimitive(true));
            for (String feature : List.of("progression", "boosters")) {
                updated = TomlDocumentEditor.set(updated, List.of("features", feature), new JsonPrimitive(false));
            }
            return updated;
        }
    }

    public static final class Installation {
        private final Path directory;
        private final boolean createdDirectory;
        private final List<CreatedFile> created = new ArrayList<>();

        private Installation(Path directory, boolean createdDirectory) {
            this.directory = directory;
            this.createdDirectory = createdDirectory;
        }

        public void rollback() throws IOException {
            IOException failure = null;
            for (CreatedFile file : created) {
                try {
                    if (Files.isRegularFile(file.path(), LinkOption.NOFOLLOW_LINKS)
                            && Arrays.equals(file.content(), Files.readAllBytes(file.path()))) {
                        Files.deleteIfExists(file.path());
                    }
                } catch (IOException exception) {
                    if (failure == null) {
                        failure = exception;
                    } else {
                        failure.addSuppressed(exception);
                    }
                }
            }
            if (createdDirectory) {
                try {
                    Files.deleteIfExists(directory);
                } catch (DirectoryNotEmptyException ignored) {
                } catch (IOException exception) {
                    if (failure == null) {
                        failure = exception;
                    } else {
                        failure.addSuppressed(exception);
                    }
                }
            }
            if (failure != null) {
                throw failure;
            }
        }

        private void add(String id, byte[] content) throws IOException {
            Path target = directory.resolve(id + ".toml");
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                return;
            }
            Path temporary = Files.createTempFile(directory, ".starter-", ".tmp");
            try {
                Files.write(temporary, content);
                try {
                    Files.move(temporary, target);
                    created.add(new CreatedFile(target, content));
                } catch (FileAlreadyExistsException ignored) {
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
        }
    }

    @FunctionalInterface
    interface Resources {
        InputStream open(String path) throws IOException;
    }

    private record CreatedFile(Path path, byte[] content) {
    }
}
