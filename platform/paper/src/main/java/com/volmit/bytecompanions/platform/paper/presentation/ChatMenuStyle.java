package com.volmit.bytecompanions.platform.paper.presentation;

import art.arcane.volmlib.util.director.help.DirectorMiniMenu;

public final class ChatMenuStyle {
    public static final String LEFT = "#ffcc00";
    public static final String RIGHT = "#ff9900";
    public static final String ACCENT = "#ffdb80";
    public static final String META = "#c9b26b";
    private static final DirectorMiniMenu.Theme THEME = new DirectorMiniMenu.Theme(
            LEFT, RIGHT, "#332600", "#997000", "#fff0bf", "#ff4d6d", ACCENT, "#ad9966");

    private ChatMenuStyle() {
    }

    public static DirectorMiniMenu.Theme theme() {
        return THEME;
    }

    public static String prefix() {
        return "<bold><gradient:" + LEFT + ":" + RIGHT + ">" + SplashScreen.NAME + "</gradient></bold>";
    }

    public static String entry(String text) {
        return "<color:" + THEME.muted() + ">⇀</color> " + text;
    }
}
