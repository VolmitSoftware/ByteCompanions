package com.volmit.bytecompanions.platform.paper.presentation;

import java.util.List;
import java.util.random.RandomGenerator;

public final class SplashArt {
    public static final int ROWS = 6;
    public static final int ALTERNATE_CHANCE_PERCENT = 10;
    private static final List<String> STANDARD = List.of(
            "██████╗ ██╗   ██╗████████╗███████╗",
            "██╔══██╗╚██╗ ██╔╝╚══██╔══╝██╔════╝",
            "██████╔╝ ╚████╔╝    ██║   █████╗  ",
            "██╔══██╗  ╚██╔╝     ██║   ██╔══╝  ",
            "██████╔╝   ██║      ██║   ███████╗",
            "╚═════╝    ╚═╝      ╚═╝   ╚══════╝");
    private static final List<List<String>> ALTERNATES = List.of(
            List.of(
                    " ____  __   __ _____  _____ ",
                    "| __ ) \\ \\ / /|_   _|| ____|",
                    "|  _ \\  \\ V /   | |  |  _|  ",
                    "| |_) |  | |    | |  | |___ ",
                    "|____/   |_|    |_|  |_____|"),
            List.of(
                    "######  #     # ####### ####### ",
                    "#     #  #   #     #    #       ",
                    "######    # #      #    #####   ",
                    "#     #    #       #    #       ",
                    "#     #    #       #    #       ",
                    "######     #       #    ####### "),
            List.of(
                    "   ██  ██   ",
                    "  ████████  ",
                    " ██ ████ ██ ",
                    " ██████████ ",
                    "   ██  ██   ",
                    "   ██  ██   Follows you everywhere, even to spawn."));

    private SplashArt() {
    }

    public static List<String> standard() {
        return STANDARD;
    }

    public static List<List<String>> alternates() {
        return ALTERNATES;
    }

    public static List<String> pick(RandomGenerator random, boolean alternatesEnabled) {
        if (!alternatesEnabled || random.nextInt(100) >= ALTERNATE_CHANCE_PERCENT) {
            return STANDARD;
        }
        return ALTERNATES.get(random.nextInt(ALTERNATES.size()));
    }
}
