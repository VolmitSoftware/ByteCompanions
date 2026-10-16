package com.volmit.bytecompanions.platform.paper.collection;

import java.util.Set;
import java.util.Map;

public record CompanionStyle(String nickname, String variantId, String particleId, String formationId) {
    public CompanionStyle {
        if (nickname != null) {
            nickname = nickname.strip();
            if (nickname.isEmpty()) nickname = null;
            else if (nickname.codePointCount(0, nickname.length()) > 32
                    || nickname.codePoints().anyMatch(Character::isISOControl)
                    || nickname.indexOf('\u00a7') >= 0) {
                throw CollectionFailure.of(CollectionFailure.Reason.NICKNAME_INVALID);
            }
        }
        variantId = optionalId(variantId);
        particleId = optionalId(particleId);
        formationId = optionalId(formationId);
        if (formationId != null && !Set.of("line", "grid", "orbit").contains(formationId)) {
            throw new CollectionFailure(CollectionFailure.Reason.FORMATION_INVALID, Map.of("formation", formationId));
        }
    }

    private static String optionalId(String value) {
        if (value == null || value.isBlank()) return null;
        if (!value.matches("[a-zA-Z0-9_.-]{1,128}")) throw CollectionFailure.of(CollectionFailure.Reason.COSMETIC_INVALID);
        return value;
    }

    public static CompanionStyle defaults() {
        return new CompanionStyle(null, null, null, null);
    }

    public CompanionStyle withParticle(String particle) {
        return new CompanionStyle(nickname, variantId, particle, formationId);
    }
}
