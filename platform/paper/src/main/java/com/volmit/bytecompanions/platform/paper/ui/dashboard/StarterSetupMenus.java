package com.volmit.bytecompanions.platform.paper.ui.dashboard;

import art.arcane.volmlib.util.inventorygui.UIElement;
import art.arcane.volmlib.util.inventorygui.UIWindow;
import com.volmit.bytecompanions.platform.paper.FeaturePermission;
import com.volmit.bytecompanions.platform.paper.PaperPlugin;
import com.volmit.bytecompanions.platform.paper.configuration.StarterCollection;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public final class StarterSetupMenus {
    private final PaperPlugin plugin;
    private final CompanionMenus menus;

    public StarterSetupMenus(PaperPlugin plugin, CompanionMenus menus) {
        this.plugin = plugin;
        this.menus = menus;
    }

    public void open(Player player) {
        if (!allowed(player)) {
            return;
        }
        UIWindow window = menus.window(player, "setup");
        UIElement heading = menus.button(player, "setup", Material.CRAFTING_TABLE, "setup-common");
        heading.addLore(menus.text(player, "setup-current", "count", Integer.toString(plugin.typeRegistry().all().size())));
        CompanionMenus.put(window, 4, heading);
        profile(window, StarterCollection.Profile.COSMETIC, 20);
        profile(window, StarterCollection.Profile.SURVIVAL, 24);
        UIElement browse = menus.button(player, "setup-preview", Material.CHEST, "setup-preview-hint");
        action(window, browse, () -> menus.openCollection(player, 0));
        CompanionMenus.put(window, 40, browse);
        menus.show(window);
    }

    private void profile(UIWindow window, StarterCollection.Profile profile, int slot) {
        UIElement element = profileIcon(window.getViewer(), profile);
        element.addLore(menus.text(window.getViewer(), "setup-costs"));
        element.addLore(menus.text(window.getViewer(), "setup-permissions"));
        action(window, element, () -> confirm(window.getViewer(), profile));
        CompanionMenus.put(window, slot, element);
    }

    private UIElement profileIcon(Player player, StarterCollection.Profile profile) {
        String key = profile == StarterCollection.Profile.COSMETIC ? "setup-cosmetic" : "setup-survival";
        Material material = profile == StarterCollection.Profile.COSMETIC ? Material.POPPY : Material.IRON_PICKAXE;
        return menus.button(player, key, material, key + "-hint");
    }

    private void confirm(Player player, StarterCollection.Profile profile) {
        if (!allowed(player)) {
            return;
        }
        String expectedSource = plugin.setupSource();
        UIWindow window = menus.window(player, "setup-confirm");
        UIElement overview = profileIcon(player, profile);
        overview.addLore(menus.text(player, "setup-common"));
        overview.addLore(menus.text(player, "setup-costs"));
        overview.addLore(menus.text(player, "setup-permissions"));
        CompanionMenus.put(window, 13, overview);
        UIElement apply = menus.button(player, "setup-confirm", Material.LIME_DYE, "setup-confirm-hint");
        action(window, apply, () -> {
            menus.close(player);
            plugin.requestStarterSetup(player, profile, expectedSource);
        });
        CompanionMenus.put(window, 22, apply);
        UIElement cancel = menus.button(player, "setup-cancel", Material.ARROW, "setup-cancel-hint");
        action(window, cancel, () -> open(player));
        CompanionMenus.put(window, 46, cancel);
        menus.show(window);
    }

    private void action(UIWindow window, UIElement element, Runnable action) {
        Runnable guarded = () -> {
            if (menus.current(window) && allowed(window.getViewer())) {
                action.run();
            }
        };
        element.onLeftClick(ignored -> guarded.run());
        element.onRightClick(ignored -> guarded.run());
    }

    private boolean allowed(Player player) {
        if (!player.isOnline() || !plugin.captureRuntimeGuard().getAsBoolean()) {
            return false;
        }
        if (!player.hasPermission(FeaturePermission.Command.SETUP)) {
            plugin.languageService().send(player, "errors.no-permission");
            return false;
        }
        return true;
    }
}
