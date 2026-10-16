package com.volmit.bytecompanions.platform.paper;

import com.volmit.bytecompanions.platform.paper.util.exception.NonInstantiableClassException;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public final class FeaturePermission {
    private static final String NODE = "bytecompanions";

    private FeaturePermission() {
        throw new NonInstantiableClassException();
    }

    public static final class Command {
        private static final String NODE = "command";

        public static final String VIEW = FeaturePermission.node("command");
        public static final String CONFIG = FeaturePermission.node("config");
        public static final String LANGUAGE_SELF = FeaturePermission.node("language.self");
        public static final String DEBUG = FeaturePermission.node("debug");
        public static final String UPDATE = FeaturePermission.node("update");
        public static final String SPAWN = node("spawn");
        public static final String DESPAWN = node("despawn");
        public static final String MENU = node("menu");
        public static final String SETTINGS = node("settings");
        public static final String SETUP = node("setup");
        public static final String EDIT = node("edit");
        public static final String OUTFITS = node("outfits");
        public static final String PERSONALIZE = node("personalize");
        public static final String INTERACT = node("interact");
        public static final String TRIAL = node("trial");

        private Command() {
            throw new NonInstantiableClassException();
        }

        private static @NotNull String node(final @NotNull String command) {
            return FeaturePermission.node(NODE + ".%s").formatted(command);
        }
    }

    public static final class Companions {
        private Companions() {
            throw new NonInstantiableClassException();
        }

        public static @NotNull String resolve(final @NotNull String companion) {
            return "bytecompanions.companion." + companion;
        }
    }

    @Contract(pure = true)
    private static @NotNull String node(final @NotNull String section) {
        return String.format("%s.%s", NODE, section);
    }
}
