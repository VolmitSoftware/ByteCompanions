package com.volmit.bytecompanions.platform.paper.messenger;

import org.jetbrains.annotations.NotNull;

public enum MessageKey {

    // errors node
    ERROR_NO_PERMISSION("errors", "no-permission"),
    ERROR_PLAYER_NOT_LOADED("errors", "player-not-loaded"),
    ERROR_PERSISTENCE_FAILED("errors", "persistence-failed"),
    ERROR_NO_ACCESS_TO_COMPANION("errors", "no-access-to-companion"),
    ERROR_COMPANION_NOT_FOUND("errors", "companion-not-found"),
    ERROR_NO_COMPANION_EQUIPPED("errors", "no-companion-equipped"),
    ERROR_COMPANION_ALREADY_EQUIPPED("errors", "companion-already-equipped"),
    ERROR_COMPANION_DOES_NOT_HAVE_ANIMATIONS("errors", "companion-does-not-have-animations"),
    ERROR_COMPANION_IS_EXCLUSIVE("errors", "companion-is-exclusive"),
    ERROR_COMPANIONS_LIMIT_REACHED("errors", "companions-limit-reached"),
    ERROR_COMPANIONS_LIMIT_PER_MODEL_REACHED("errors", "companions-limit-per-model-reached"),
    ERROR_PLAYER_HAS_A_EXCLUSIVE_COMPANION("errors", "player-has-a-exclusive-companion"),

    // success node
    SUCCESS_COMPANION_EQUIPPED("success", "companion-equipped"),
    SUCCESS_COMPANION_UNEQUIPPED("success", "companion-unequipped"),

    // general node
    ;

    private final String node;
    private final String key;

    MessageKey(
            final @NotNull String node,
            final @NotNull String key
    ) {
        this.node = node;
        this.key = key;
    }

    public String key() {
        return this.key;
    }

    public String node() {
        return this.node;
    }

    @Override
    public String toString() {
        return "MessageKey{node=" + this.node + ", key=" + this.key + "}";
    }
}
