package com.volmit.bytecompanions.providers.storage.json;

import art.arcane.volmlib.util.io.AtomicFileIO;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class JsonDocuments {
    public static final String SUFFIX = ".json";
    public static final String LAST_GOOD_SUFFIX = ".last-good";
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private JsonDocuments() {
    }

    public static Path document(Path directory, String id) {
        return directory.resolve(id + SUFFIX);
    }

    public static <T> T read(Path document, Class<T> type, Logger logger) {
        String content;
        try {
            content = readText(document);
        } catch (CharacterCodingException failure) {
            logger.log(Level.WARNING, "Undecodable storage document " + document + "; using its last good copy", failure);
            return readLastGood(document, type);
        }
        if (content == null) {
            return null;
        }
        try {
            T parsed = GSON.fromJson(content, type);
            if (parsed != null) {
                return parsed;
            }
        } catch (JsonParseException failure) {
            logger.log(Level.WARNING, "Unreadable storage document " + document + "; using its last good copy", failure);
        }
        return readLastGood(document, type);
    }

    public static void write(Path document, Object value) {
        writeText(document, GSON.toJson(value));
    }

    public static void writeText(Path document, String content) {
        try {
            String current = readCurrent(document);
            if (content.equals(current)) {
                return;
            }
            if (current != null && isDocument(current)) {
                AtomicFileIO.writeString(lastGood(document), current);
            }
            AtomicFileIO.writeString(document, content);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    public static boolean delete(Path document) {
        try {
            boolean existed = Files.deleteIfExists(document);
            Files.deleteIfExists(lastGood(document));
            return existed;
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    public static List<String> ids(Path directory) {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        List<String> ids = new ArrayList<>();
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(directory, "*" + SUFFIX)) {
            for (Path entry : entries) {
                String name = entry.getFileName().toString();
                ids.add(name.substring(0, name.length() - SUFFIX.length()));
            }
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
        return ids;
    }

    public static void createDirectory(Path directory) {
        try {
            Files.createDirectories(Objects.requireNonNull(directory, "directory"));
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static Path lastGood(Path document) {
        return document.resolveSibling(document.getFileName() + LAST_GOOD_SUFFIX);
    }

    private static <T> T readLastGood(Path document, Class<T> type) {
        String lastGood;
        try {
            lastGood = readText(lastGood(document));
        } catch (CharacterCodingException failure) {
            throw new UncheckedIOException("Storage document and its last good copy are undecodable: " + document, failure);
        }
        if (lastGood == null) {
            throw new UncheckedIOException(new IOException("Storage document is unreadable and has no last good copy: " + document));
        }
        T recovered;
        try {
            recovered = GSON.fromJson(lastGood, type);
        } catch (JsonParseException failure) {
            throw new UncheckedIOException(new IOException("Storage document and its last good copy are unreadable: " + document, failure));
        }
        if (recovered == null) {
            throw new UncheckedIOException(new IOException("Storage document and its last good copy are empty: " + document));
        }
        return recovered;
    }

    private static String readCurrent(Path document) {
        try {
            return readText(document);
        } catch (CharacterCodingException undecodable) {
            return null;
        }
    }

    private static boolean isDocument(String content) {
        try {
            return JsonParser.parseString(content).isJsonObject();
        } catch (JsonParseException failure) {
            return false;
        }
    }

    private static String readText(Path path) throws CharacterCodingException {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (NoSuchFileException missing) {
            return null;
        } catch (CharacterCodingException undecodable) {
            throw undecodable;
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
