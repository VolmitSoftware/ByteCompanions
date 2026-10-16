package com.volmit.bytecompanions.platform.paper.collection;

import art.arcane.volmlib.util.localization.MessageArgs;
import com.volmit.bytecompanions.platform.paper.localization.CompanionMessages;
import com.volmit.bytecompanions.platform.paper.localization.LanguageService;
import org.bukkit.command.CommandSender;

import java.util.Map;
import java.util.Objects;

public final class CollectionFailure extends IllegalArgumentException {
    public enum Reason {
        LOADING("gui.loading"),
        NO_PERMISSION("errors.no-permission"),
        COMPANION_UNAVAILABLE("errors.companion-not-found"),
        COMPANION_LOCKED("errors.no-access-to-companion"),
        NO_ANIMATIONS("errors.companion-does-not-have-animations"),
        EXCLUSIVE("errors.companion-is-exclusive"),
        COMPANION_LIMIT("errors.companions-limit-reached"),
        FAILED("command.feedback.failed"),
        EQUIP_FIRST("collection.reason.equip-first"),
        OUTFIT_LIMIT("collection.reason.outfit-limit"),
        OUTFIT_UNAVAILABLE("collection.reason.outfit-unavailable"),
        FORMATION_ORDER("collection.reason.formation-order"),
        MODEL_LIMIT("collection.reason.model-limit"),
        PERSONALIZATION_LOCKED("collection.reason.personalization-locked"),
        PARTICLE_UNAVAILABLE("collection.reason.particle-unavailable"),
        PARTICLE_LOCKED("collection.reason.particle-locked"),
        PARTICLE_REMOVE_DENIED("collection.reason.particle-remove-denied"),
        NICKNAME_INVALID("collection.reason.nickname-invalid"),
        FORMATION_INVALID("collection.reason.formation-invalid"),
        COSMETIC_INVALID("collection.reason.cosmetic-invalid"),
        OUTFIT_NAME_INVALID("collection.reason.outfit-name-invalid"),
        VARIANT_LOCKED("collection.reason.variant-locked"),
        VARIANT_UNAVAILABLE("collection.reason.variant-unavailable"),
        CANCELLED("collection.reason.cancelled"),
        SAVE_FAILED("collection.reason.save-failed"),
        BUSY("collection.reason.busy");

        private final String key;

        Reason(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }
    }

    private final Reason reason;
    private final Map<String, String> arguments;

    public CollectionFailure(Reason reason, Map<String, String> arguments) {
        super(Objects.requireNonNull(reason, "reason").key());
        this.reason = reason;
        this.arguments = Map.copyOf(arguments);
    }

    public static CollectionFailure of(Reason reason) {
        return new CollectionFailure(reason, Map.of());
    }

    public Reason reason() {
        return reason;
    }

    public String render(LanguageService languages, CommandSender sender) {
        MessageArgs.Builder values = MessageArgs.builder();
        arguments.forEach(values::untrusted);
        return languages.render(sender, CompanionMessages.key(reason.key()), values.build()).plain();
    }
}
