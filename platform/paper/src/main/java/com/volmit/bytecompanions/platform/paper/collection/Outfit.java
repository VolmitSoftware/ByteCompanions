package com.volmit.bytecompanions.platform.paper.collection;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public record Outfit(String name, List<String> companionIds, Map<String, CompanionStyle> styles,
                     List<String> formationOrder) {
    public Outfit {
        name = key(name);
        companionIds = List.copyOf(companionIds);
        styles = Map.copyOf(styles);
        formationOrder = List.copyOf(formationOrder);
        if (companionIds.stream().distinct().count() != companionIds.size()) {
            throw new IllegalArgumentException("An outfit cannot contain duplicate companions");
        }
        if (!companionIds.containsAll(styles.keySet()) || !companionIds.containsAll(formationOrder)
                || formationOrder.stream().distinct().count() != formationOrder.size()) {
            throw new IllegalArgumentException("Outfit cosmetics must belong to its selected companions");
        }
    }

    public static String key(String name) {
        if (name == null) throw CollectionFailure.of(CollectionFailure.Reason.OUTFIT_NAME_INVALID);
        String key = name.strip().toLowerCase(Locale.ROOT);
        if (!key.matches("[a-z0-9_-]{1,32}")) {
            throw CollectionFailure.of(CollectionFailure.Reason.OUTFIT_NAME_INVALID);
        }
        return key;
    }
}
