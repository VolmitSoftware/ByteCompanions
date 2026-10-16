package com.volmit.bytecompanions.platform.paper.metrics;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MetricsSourceIntegrityTest {
    private static final String PLUGIN_PACKAGE = "package com.volmit.bytecompanions.platform.paper.metrics;";
    private static final String UPSTREAM_PACKAGE = "package org.bstats.bukkit;";
    private static final String UPSTREAM_SHA256 = "c19484d1de92ae52ea5f80e949a7f5885e4e6819d0e6a57e920ac864ecd5f57a";

    @Test
    void differsFromTheOfficialSingleFileOnlyByPackage() throws IOException, NoSuchAlgorithmException {
        Path project = Path.of(System.getProperty("bytecompanions.projectDir"));
        Path source = project.resolve("src/main/java/com/volmit/bytecompanions/platform/paper/metrics/Metrics.java");
        String current = Files.readString(source, StandardCharsets.UTF_8).replace("\r\n", "\n");

        assertEquals(1, current.split(PLUGIN_PACKAGE, -1).length - 1);
        assertFalse(current.contains(UPSTREAM_PACKAGE));
        String normalized = current.replace(PLUGIN_PACKAGE, UPSTREAM_PACKAGE);
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(StandardCharsets.UTF_8));
        assertTrue(HexFormat.of().formatHex(digest).equals(UPSTREAM_SHA256));
    }
}
