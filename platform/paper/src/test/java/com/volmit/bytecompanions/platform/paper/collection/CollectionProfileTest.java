package com.volmit.bytecompanions.platform.paper.collection;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CollectionProfileTest {
    @Test
    void capturedProfilesDoNotFollowMutableCallerCollections() {
        Set<String> favorites = new HashSet<>(Set.of("bee"));
        Map<String, CompanionStyle> styles = new HashMap<>(Map.of("bee", CompanionStyle.defaults()));
        List<String> order = new ArrayList<>(List.of("bee"));
        CollectionProfile profile = new CollectionProfile(1, favorites, Map.of(), styles, order);
        favorites.clear();
        styles.clear();
        order.clear();
        assertEquals(Set.of("bee"), profile.favorites());
        assertEquals(Set.of("bee"), profile.styles().keySet());
        assertEquals(List.of("bee"), profile.formationOrder());
    }

    @Test
    void nicknameAndOutfitKeysHaveBoundedPlainTextRules() {
        assertEquals("travel", Outfit.key(" Travel "));
        assertThrows(IllegalArgumentException.class, () -> Outfit.key("../travel"));
        assertThrows(IllegalArgumentException.class, () -> new CompanionStyle("bad\nname", null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new CompanionStyle("\u00a7cred", null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new CompanionStyle("a".repeat(33), null, null, null));
        assertEquals("<red>Bee", new CompanionStyle("<red>Bee", null, null, null).nickname());
        assertThrows(IllegalArgumentException.class, () -> new CompanionStyle(null, null, null, "random"));
    }

    @Test
    void outfitsCannotSmuggleCosmeticsForUnselectedCompanions() {
        assertThrows(IllegalArgumentException.class, () -> new Outfit("test", List.of("bee"),
                Map.of("dragon", CompanionStyle.defaults()), List.of("bee")));
        assertThrows(IllegalArgumentException.class, () -> new Outfit("test", List.of("bee", "bee"), Map.of(), List.of()));
    }
}
