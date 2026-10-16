package com.volmit.bytecompanions.platform.paper.features;

import org.junit.jupiter.api.Test;
import com.volmit.bytecompanions.platform.paper.configuration.StarterCollection;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MergedResourcesTest {

    @Test
    void coreAndFeatureDefaultsRemainIndependentlyAddressable() throws IOException {
        List<String> paths = List.of(
                "config.toml", "languages/en_US.toml",
                "features/attributes/config.toml", "features/buffs/config.toml",
                "features/decorations/config.toml", "features/effects/config.toml",
                "features/particles/config.toml", "features/particles/icons.toml");
        Set<String> contents = new HashSet<>();

        for (String path : paths) {
            List<URL> resources = Collections.list(this.getClass().getClassLoader().getResources(path));
            assertEquals(1, resources.size(), "Expected exactly one resource at " + path);
            String content = resource(path);
            assertFalse(content.isBlank(), "Empty defaults at " + path);
            assertTrue(contents.add(content), "Different feature paths contain the same defaults at " + path);
        }
    }

    @Test
    void allIncludedCompanionDefinitionsSurviveResourceMerging() throws IOException {
        for (String id : StarterCollection.ids()) {
            String definition = resource("companions/" + id + ".toml");
            assertTrue(definition.contains("[animations."), "Missing animation setup for " + id);
            assertTrue(definition.contains("[equipment."), "Missing equipment setup for " + id);
        }
    }

    private static String resource(String path) throws IOException {
        try (InputStream stream = MergedResourcesTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(stream, "Missing resource: " + path);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
