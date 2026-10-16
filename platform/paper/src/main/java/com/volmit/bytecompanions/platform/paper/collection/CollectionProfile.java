package com.volmit.bytecompanions.platform.paper.collection;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record CollectionProfile(long revision, Set<String> favorites, Map<String, Outfit> outfits,
                                Map<String, CompanionStyle> styles, List<String> formationOrder,
                                ViewerPreferences viewerPreferences) {
    public CollectionProfile {
        if (revision < 0) throw new IllegalArgumentException("Invalid collection revision");
        favorites = Set.copyOf(favorites);
        outfits = Map.copyOf(outfits);
        styles = Map.copyOf(styles);
        formationOrder = List.copyOf(formationOrder);
        viewerPreferences = viewerPreferences == null ? ViewerPreferences.defaults() : viewerPreferences;
        if (formationOrder.stream().distinct().count() != formationOrder.size()) {
            throw new IllegalArgumentException("Duplicate formation companion");
        }
        outfits.forEach((key, outfit) -> {
            if (!key.equals(outfit.name())) throw new IllegalArgumentException("Outfit key does not match its name");
        });
    }

    public CollectionProfile(long revision, Set<String> favorites, Map<String, Outfit> outfits,
                             Map<String, CompanionStyle> styles, List<String> formationOrder) {
        this(revision, favorites, outfits, styles, formationOrder, ViewerPreferences.defaults());
    }

    public static CollectionProfile empty() {
        return new CollectionProfile(0, Set.of(), Map.of(), Map.of(), List.of());
    }

    public CollectionProfile next(Set<String> favorites, Map<String, Outfit> outfits,
                                  Map<String, CompanionStyle> styles, List<String> order) {
        return new CollectionProfile(Math.addExact(revision, 1), favorites, outfits, styles, order, viewerPreferences);
    }

    public CollectionProfile withViewerPreferences(ViewerPreferences preferences) {
        return new CollectionProfile(Math.addExact(revision, 1), favorites, outfits, styles, formationOrder,
                java.util.Objects.requireNonNull(preferences));
    }
}
