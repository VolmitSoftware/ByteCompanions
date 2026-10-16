package com.volmit.bytecompanions.platform.paper.bootstrap;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public final class NestedZipResources {
    private NestedZipResources() {
    }

    public record Limits(int entries, int archiveBytes, int entryBytes, long expandedBytes,
                         int libraryEntries, long libraryExpandedBytes) {
        public Limits {
            if (entries <= 0 || archiveBytes <= 0 || archiveBytes == Integer.MAX_VALUE
                    || entryBytes <= 0 || entryBytes == Integer.MAX_VALUE || expandedBytes <= 0
                    || libraryEntries <= 0 || libraryExpandedBytes <= 0) {
                throw new IllegalArgumentException("Nested resource limits must be positive and bounded");
            }
        }
    }

    public static boolean relocate(Path library, String resource, Map<String, String> prefixes, Limits limits)
            throws IOException {
        requireName(resource);
        List<Map.Entry<String, String>> mappings = new ArrayList<>(prefixes.entrySet());
        for (Map.Entry<String, String> mapping : mappings) {
            requirePrefix(mapping.getKey());
            requirePrefix(mapping.getValue());
        }
        mappings.sort(Comparator.comparingInt((Map.Entry<String, String> entry) -> entry.getKey().length()).reversed());
        byte[] replacement;
        try (ZipFile source = new ZipFile(library.toFile())) {
            ZipEntry selected = source.getEntry(resource);
            if (selected == null) {
                return false;
            }
            if (selected.isDirectory()) {
                throw new IOException("Nested resource is a directory: " + resource);
            }
            byte[] compressed;
            try (InputStream input = source.getInputStream(selected)) {
                compressed = readBounded(input, limits.archiveBytes());
            }
            replacement = relocateEntries(compressed, mappings, limits);
        }
        Path absolute = library.toAbsolutePath();
        Path temporary = Files.createTempFile(absolute.getParent(), ".nested-resources-", ".jar");
        try {
            try (ZipFile source = new ZipFile(absolute.toFile());
                 ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(temporary))) {
                Enumeration<? extends ZipEntry> entries = source.entries();
                int count = 0;
                long expanded = 0;
                while (entries.hasMoreElements()) {
                    if (++count > limits.libraryEntries()) {
                        throw new IOException("Library archive exceeds configured entry count");
                    }
                    ZipEntry entry = entries.nextElement();
                    requireName(entry.getName());
                    ZipEntry copied = new ZipEntry(entry.getName());
                    copied.setTime(entry.getTime() < 0 ? 0 : entry.getTime());
                    output.putNextEntry(copied);
                    if (entry.getName().equals(resource)) {
                        expanded += replacement.length;
                        if (expanded > limits.libraryExpandedBytes()) {
                            throw new IOException("Library archive exceeds configured expanded size");
                        }
                        output.write(replacement);
                    } else {
                        try (InputStream input = source.getInputStream(entry)) {
                            byte[] buffer = new byte[8192];
                            int length;
                            while ((length = input.read(buffer)) != -1) {
                                expanded += length;
                                if (expanded > limits.libraryExpandedBytes()) {
                                    throw new IOException("Library archive exceeds configured expanded size");
                                }
                                output.write(buffer, 0, length);
                            }
                        }
                    }
                    output.closeEntry();
                }
            }
            Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static byte[] relocateEntries(byte[] archive, List<Map.Entry<String, String>> mappings, Limits limits)
            throws IOException {
        Map<String, byte[]> resources = new TreeMap<>();
        long total = 0;
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                String name = entry.getName();
                requireName(name);
                for (Map.Entry<String, String> mapping : mappings) {
                    if (name.startsWith(mapping.getKey())) {
                        name = mapping.getValue() + name.substring(mapping.getKey().length());
                        break;
                    }
                }
                requireName(name);
                byte[] content = readBounded(input, limits.entryBytes());
                total += content.length;
                if (total > limits.expandedBytes() || resources.size() >= limits.entries()) {
                    throw new IOException("Nested resource archive exceeds configured limits");
                }
                if (entry.isDirectory() && content.length != 0) {
                    throw new IOException("Nested resource directory has content: " + name);
                }
                if (resources.put(name, content) != null) {
                    throw new IOException("Duplicate relocated nested resource: " + name);
                }
                input.closeEntry();
            }
        }
        if (resources.isEmpty()) {
            throw new IOException("Nested resource archive is empty or invalid");
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream output = new ZipOutputStream(bytes)) {
            output.setLevel(9);
            for (Map.Entry<String, byte[]> resource : resources.entrySet()) {
                ZipEntry entry = new ZipEntry(resource.getKey());
                entry.setTime(0);
                output.putNextEntry(entry);
                output.write(resource.getValue());
                output.closeEntry();
            }
        }
        if (bytes.size() > limits.archiveBytes()) {
            throw new IOException("Relocated resource archive exceeds configured size");
        }
        return bytes.toByteArray();
    }

    private static byte[] readBounded(InputStream input, int maximum) throws IOException {
        byte[] content = input.readNBytes(maximum + 1);
        if (content.length > maximum) {
            throw new IOException("Nested resource exceeds configured size");
        }
        return content;
    }

    private static void requirePrefix(String prefix) throws IOException {
        requireName(prefix);
        if (!prefix.endsWith("/")) {
            throw new IOException("Resource prefix must end with a slash: " + prefix);
        }
    }

    private static void requireName(String name) throws IOException {
        if (name.isEmpty() || name.startsWith("/") || name.contains("\\") || name.contains(":") || name.indexOf('\0') >= 0) {
            throw new IOException("Unsafe ZIP resource name: " + name);
        }
        String path = name.endsWith("/") ? name.substring(0, name.length() - 1) : name;
        for (String segment : path.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw new IOException("Unsafe ZIP resource name: " + name);
            }
        }
    }
}
