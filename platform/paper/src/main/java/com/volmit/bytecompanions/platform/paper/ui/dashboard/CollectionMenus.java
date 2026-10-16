package com.volmit.bytecompanions.platform.paper.ui.dashboard;

import com.volmit.bytecompanions.platform.paper.util.inventorygui.BukkitTextInput;
import art.arcane.volmlib.util.inventorygui.UIElement;
import art.arcane.volmlib.util.inventorygui.UIWindow;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import com.volmit.bytecompanions.api.companion.Companion;
import com.volmit.bytecompanions.api.companion.CompanionType;
import com.volmit.bytecompanions.api.companion.behavior.CompanionAction;
import com.volmit.bytecompanions.api.companion.behavior.CompanionActionResult;
import com.volmit.bytecompanions.api.user.CompanionPlayer;
import com.volmit.bytecompanions.platform.paper.FeaturePermission;
import com.volmit.bytecompanions.platform.paper.PaperPlugin;
import com.volmit.bytecompanions.platform.paper.collection.CollectionProfile;
import com.volmit.bytecompanions.platform.paper.collection.CollectionService;
import com.volmit.bytecompanions.platform.paper.collection.CollectionFailure;
import com.volmit.bytecompanions.platform.paper.collection.CompanionStyle;
import com.volmit.bytecompanions.platform.paper.collection.Outfit;
import com.volmit.bytecompanions.platform.paper.collection.ViewerPreferences;
import com.volmit.bytecompanions.platform.paper.studio.CompanionPreviewService;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.logging.Level;

final class CollectionMenus implements Listener {
    private final PaperPlugin plugin;
    private final CompanionMenus menus;
    private final BukkitTextInput input;
    private final BooleanSupplier runtime;
    private final Map<UUID, Browse> browsing = new ConcurrentHashMap<>();
    private final Map<UUID, Object> pending = new ConcurrentHashMap<>();

    CollectionMenus(PaperPlugin plugin, CompanionMenus menus) {
        this.plugin = plugin;
        this.menus = menus;
        this.input = BukkitTextInput.register(plugin);
        this.runtime = plugin.captureRuntimeGuard();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    List<CompanionType> filter(Player player, List<CompanionType> types) {
        CollectionProfile profile = profile(player);
        if (profile == null) return List.of();
        Browse browse = browsing.getOrDefault(player.getUniqueId(), new Browse(false, 0, null));
        return types.stream().filter(type -> !browse.favorites || profile.favorites().contains(type.id()))
                .filter(type -> browse.access == 0 || player.hasPermission(FeaturePermission.Companions.resolve(type.id())) == (browse.access == 1))
                .filter(type -> browse.category == null || browse.category.equals(plugin.collectionVariants().category(type)))
                .sorted(Comparator.comparing(CompanionType::id)).toList();
    }

    boolean ready(Player player) { return profile(player) != null; }

    void settings(Player player, String setting, String value) {
        if (!menus.accessible(player, FeaturePermission.Command.SETTINGS) || profile(player) == null) return;
        if (!setting.isBlank() || !value.isBlank()) {
            try {
                UnaryOperator<ViewerPreferences> mutation = switch (setting.toLowerCase(java.util.Locale.ROOT)) {
                    case "visibility" -> {
                        ViewerPreferences.Visibility visibility = ViewerPreferences.Visibility.valueOf(value.toUpperCase(java.util.Locale.ROOT));
                        yield previous -> new ViewerPreferences(visibility, previous.particleIntensity());
                    }
                    case "particles" -> {
                        ViewerPreferences.ParticleIntensity intensity = ViewerPreferences.ParticleIntensity.valueOf(value.toUpperCase(java.util.Locale.ROOT));
                        yield previous -> new ViewerPreferences(previous.visibility(), intensity);
                    }
                    default -> throw new IllegalArgumentException("Unknown setting");
                };
                saveSettings(player, mutation);
            } catch (IllegalArgumentException invalid) {
                plugin.languageService().send(player, "command.feedback.usage", "usage",
                        "/bytecompanions settings [visibility all|own|none / particles full|reduced|off]");
            }
            return;
        }
        openSettings(player);
    }

    private void saveSettings(Player player, UnaryOperator<ViewerPreferences> mutation) {
        CollectionService service = plugin.collectionService();
        service.updateViewerPreferences(player, mutation).whenComplete((outcome, failure) -> {
            if (failure != null) plugin.getLogger().log(Level.SEVERE, "Could not save viewer settings", failure);
            FoliaScheduler.runEntity(plugin, player, () -> {
                if (!runtime.getAsBoolean() || !player.isOnline() || plugin.collectionService() != service
                        || plugin.getServer().getPlayer(player.getUniqueId()) != player) return;
                if (failure != null) plugin.languageService().send(player, "command.feedback.failed");
                else if (outcome.success()) plugin.languageService().send(player, "collection.saved");
                else plugin.languageService().send(player, "collection.rejected", "detail", outcome.failure().render(plugin.languageService(), player));
            }, 0L, () -> {});
        });
    }

    private void openSettings(Player player) {
        if (!menus.accessible(player, FeaturePermission.Command.SETTINGS)) return;
        CollectionProfile profile = profile(player);
        if (profile == null) return;
        ViewerPreferences preferences = profile.viewerPreferences();
        UIWindow window = menus.window(player, "settings");
        CompanionMenus.put(window, 4, menus.button(player, "settings", Material.COMPARATOR, "settings-hint"));
        CompanionMenus.put(window, 10, button(player, "settings-visibility", Material.SPYGLASS));
        for (ViewerPreferences.Visibility choice : ViewerPreferences.Visibility.values()) {
            String key = "settings-visibility-" + choice.name().toLowerCase(java.util.Locale.ROOT);
            UIElement element = menus.button(player, key, switch (choice) {
                case ALL -> Material.ENDER_EYE;
                case OWN -> Material.PLAYER_HEAD;
                case NONE -> Material.BARRIER;
            }, key + "-hint");
            element.setEnchanted(preferences.visibility() == choice);
            if (preferences.visibility() == choice) element.addLore(menus.text(player, "selected"));
            settingsAction(window, 20 + choice.ordinal() * 2, element,
                    previous -> new ViewerPreferences(choice, previous.particleIntensity()));
        }
        CompanionMenus.put(window, 28, button(player, "settings-particles", Material.FIREWORK_STAR));
        for (ViewerPreferences.ParticleIntensity choice : ViewerPreferences.ParticleIntensity.values()) {
            String key = "settings-particles-" + choice.name().toLowerCase(java.util.Locale.ROOT);
            UIElement element = menus.button(player, key, switch (choice) {
                case FULL -> Material.FIREWORK_ROCKET;
                case REDUCED -> Material.GLOWSTONE_DUST;
                case OFF -> Material.GUNPOWDER;
            }, key + "-hint");
            element.setEnchanted(preferences.particleIntensity() == choice);
            if (preferences.particleIntensity() == choice) element.addLore(menus.text(player, "selected"));
            element.addLore(menus.text(player, "settings-server-limit"));
            settingsAction(window, 38 + choice.ordinal() * 2, element,
                    previous -> new ViewerPreferences(previous.visibility(), choice));
        }
        if (player.hasPermission(FeaturePermission.Command.MENU) && plugin.getCompanionPlayerOrNull(player) != null) {
            action(window, 46, button(player, "collection", Material.CHEST), () -> menus.openHome(player));
        }
        menus.show(window);
    }

    private void settingsAction(UIWindow window, int slot, UIElement element, UnaryOperator<ViewerPreferences> mutation) {
        menus.action(window, element, () -> change(window.getViewer(),
                () -> plugin.collectionService().updateViewerPreferences(window.getViewer(), mutation),
                () -> { if (menus.current(window)) openSettings(window.getViewer()); }), FeaturePermission.Command.SETTINGS);
        CompanionMenus.put(window, slot, element);
    }

    boolean favorite(Player player, String id) {
        CollectionProfile profile = plugin.collectionService().profile(player.getUniqueId()).orElse(null);
        return profile != null && profile.favorites().contains(id);
    }

    void filters(UIWindow window) {
        Player player = window.getViewer();
        Browse browse = browsing.getOrDefault(player.getUniqueId(), new Browse(false, 0, null));
        UIElement favorites = button(player, "favorites-only", Material.HONEYCOMB);
        favorites.setEnchanted(browse.favorites);
        favorites.addLore(menus.text(player, browse.favorites ? "selected" : "filter-all"));
        action(window, 1, favorites, () -> {
            browsing.put(player.getUniqueId(), new Browse(!browse.favorites, browse.access, browse.category));
            menus.openCollection(player, 0);
        });
        action(window, 2, button(player, accessKey(browse.access), Material.HOPPER), () -> {
            browsing.put(player.getUniqueId(), new Browse(browse.favorites, (browse.access + 1) % 3, browse.category));
            menus.openCollection(player, 0);
        });
        UIElement category = button(player, "category", Material.BOOKSHELF);
        category.addLore(browse.category == null ? menus.text(player, "category-all") : menus.text(player, "choice-value", "value", browse.category));
        action(window, 6, category, () -> {
            List<String> categories = plugin.typeRegistry().all().stream().map(type -> plugin.collectionVariants().category(type)).distinct().sorted().toList();
            int next = categories.indexOf(browse.category) + 1;
            browsing.put(player.getUniqueId(), new Browse(browse.favorites, browse.access, next < categories.size() ? categories.get(next) : null));
            menus.openCollection(player, 0);
        });
        if (player.hasPermission(FeaturePermission.Command.OUTFITS)) {
            action(window, 7, button(player, "outfits", Material.LEATHER_CHESTPLATE), () -> outfits(player, 0));
        }
    }

    void dashboard(UIWindow window, String id) {
        Player player = window.getViewer();
        UIElement favorite = button(player, favorite(player, id) ? "favorite-on" : "favorite", Material.HONEYCOMB);
        favorite.setEnchanted(favorite(player, id));
        action(window, 37, favorite, () -> change(player, () -> plugin.collectionService().toggleFavorite(player, id), () -> menus.openDashboard(player, id)));
        if (player.hasPermission(FeaturePermission.Command.OUTFITS)) {
            action(window, 38, button(player, "outfits", Material.LEATHER_CHESTPLATE), () -> outfits(player, 0));
        }
        if (player.hasPermission(FeaturePermission.Command.PERSONALIZE)) {
            action(window, 42, button(player, "personalize", Material.NAME_TAG), () -> personalize(player, id));
        }
        if (plugin.configuration().collections().personalityEnabled() && player.hasPermission(FeaturePermission.Command.INTERACT)) {
            action(window, 40, button(player, "interactions", Material.LEAD), () -> interactions(player, id));
        }
        if (player.hasPermission(FeaturePermission.Command.TRIAL)) {
            boolean active = plugin.companionPreviews().active(player, CompanionPreviewService.Purpose.TRIAL);
            CompanionType trialType = plugin.typeRegistry().getOrNull(id);
            if (!active && (trialType == null || !plugin.companionPreviews().canTrial(player, trialType))) return;
            UIElement trial = button(player, active ? "trial-stop" : "trial", Material.SPYGLASS);
            trial.addLore(menus.text(player, "trial-hint", "seconds", Integer.toString(plugin.configuration().collections().trialSeconds())));
            action(window, 16, trial, () -> {
                if (!allowed(player, FeaturePermission.Command.TRIAL)) return;
                if (plugin.companionPreviews().active(player, CompanionPreviewService.Purpose.TRIAL)) {
                    plugin.companionPreviews().stop(player, CompanionPreviewService.Purpose.TRIAL);
                    menus.openDashboard(player, id);
                    return;
                }
                CompanionType type = plugin.typeRegistry().getOrNull(id);
                if (type == null) return;
                if (!plugin.companionPreviews().canTrial(player, type)) {
                    menus.openDashboard(player, id);
                    return;
                }
                try {
                    plugin.companionPreviews().show(player, type, CompanionPreviewService.Purpose.TRIAL,
                            Duration.ofSeconds(plugin.configuration().collections().trialSeconds()));
                    menus.close(player);
                    plugin.languageService().send(player, "collection.trial-started");
                } catch (RuntimeException | LinkageError failure) {
                    plugin.getLogger().log(Level.SEVERE, "Could not start companion trial " + id, failure);
                    plugin.languageService().send(player, "command.feedback.failed");
                }
            });
        }
    }

    void outfits(Player player, int requestedPage) {
        if (!allowed(player, FeaturePermission.Command.OUTFITS)) return;
        CollectionProfile profile = profile(player);
        if (profile == null) return;
        List<Outfit> outfits = profile.outfits().values().stream().sorted(Comparator.comparing(Outfit::name)).toList();
        int page = CompanionMenus.page(requestedPage, outfits.size());
        UIWindow window = menus.window(player, "outfits");
        CompanionMenus.put(window, 4, button(player, "outfits", Material.LEATHER_CHESTPLATE));
        for (int index = page * 28; index < Math.min(outfits.size(), (page + 1) * 28); index++) {
            Outfit outfit = outfits.get(index);
            UIElement entry = new UIElement(outfit.name()).setBaseItemStack(new ItemStack(Material.LEATHER_CHESTPLATE))
                    .setName(menus.text(player, "choice-value", "value", outfit.name()));
            entry.addLore(menus.text(player, "outfit-count", "count", Integer.toString(outfit.companionIds().size())));
            action(window, CompanionMenus.contentSlot(index % 28), entry, () -> outfit(player, outfit.name(), 0));
        }
        if (outfits.isEmpty()) CompanionMenus.put(window, 22, button(player, "empty", Material.BARRIER));
        action(window, 48, button(player, "outfit-save", Material.WRITABLE_BOOK), () -> prompt(player, FeaturePermission.Command.OUTFITS,
                "collection.outfit-prompt", name -> change(player, () -> plugin.collectionService().saveOutfit(player, name), () -> outfits(player, page)),
                () -> outfits(player, page)));
        action(window, 46, button(player, "collection", Material.CHEST), () -> menus.openCollection(player, 0));
        menus.navigation(window, page, outfits.size(), next -> outfits(player, next));
        menus.show(window);
    }

    private void outfit(Player player, String name, int requestedPage) {
        if (!allowed(player, FeaturePermission.Command.OUTFITS)) return;
        CollectionProfile profile = profile(player);
        if (profile == null) return;
        Outfit outfit = profile.outfits().get(name);
        if (outfit == null) { outfits(player, 0); return; }
        int page = CompanionMenus.page(requestedPage, outfit.companionIds().size());
        UIWindow window = menus.window(player, "outfit");
        UIElement heading = button(player, "outfits", Material.LEATHER_CHESTPLATE);
        heading.addLore(menus.text(player, "choice-value", "value", name));
        CompanionMenus.put(window, 4, heading);
        for (int index = page * 28; index < Math.min(outfit.companionIds().size(), (page + 1) * 28); index++) {
            String id = outfit.companionIds().get(index);
            CompanionType type = plugin.typeRegistry().getOrNull(id);
            UIElement entry = type == null ? button(player, "locked", Material.BARRIER) : menus.icon(id, menus.companionIcon(player, type));
            entry.addLore(menus.text(player, "choice-value", "value", id));
            if (!player.hasPermission(FeaturePermission.Companions.resolve(id))) entry.addLore(menus.text(player, "locked"));
            CompanionStyle style = outfit.styles().getOrDefault(id, CompanionStyle.defaults());
            if (style.nickname() != null) entry.addLore(menus.text(player, "choice-value", "value", style.nickname()));
            if (style.variantId() != null) entry.addLore(menus.text(player, "choice-value", "value", style.variantId()));
            if (style.particleId() != null) entry.addLore(menus.text(player, "choice-value", "value", style.particleId()));
            if (style.formationId() != null) entry.addLore(menus.text(player, "choice-value", "value", style.formationId()));
            CompanionMenus.put(window, CompanionMenus.contentSlot(index % 28), entry);
        }
        action(window, 48, button(player, "outfit-apply", Material.LIME_DYE),
                () -> change(player, () -> plugin.collectionService().applyOutfit(player, name), () -> outfit(player, name, page)));
        action(window, 51, button(player, "outfit-delete", Material.RED_DYE), () -> deleteOutfit(player, name));
        action(window, 46, button(player, "studio-back", Material.ARROW), () -> outfits(player, 0));
        menus.navigation(window, page, outfit.companionIds().size(), next -> outfit(player, name, next));
        menus.show(window);
    }

    private void deleteOutfit(Player player, String name) {
        if (!allowed(player, FeaturePermission.Command.OUTFITS)) return;
        UIWindow window = menus.window(player, "outfit-delete");
        UIElement confirm = button(player, "outfit-delete-confirm", Material.RED_CONCRETE);
        confirm.addLore(menus.text(player, "choice-value", "value", name));
        action(window, 22, confirm, () -> change(player, () -> plugin.collectionService().deleteOutfit(player, name), () -> outfits(player, 0)));
        action(window, 46, button(player, "studio-back", Material.ARROW), () -> outfit(player, name, 0));
        menus.show(window);
    }

    private void personalize(Player player, String id) {
        if (!owned(player, id)) return;
        CollectionProfile profile = profile(player);
        if (profile == null) return;
        CompanionStyle style = plugin.collectionService().style(player, id);
        UIWindow window = menus.window(player, "personalize");
        UIElement name = button(player, "nickname", Material.NAME_TAG);
        name.addLore(style.nickname() == null ? menus.text(player, "default-choice") : menus.text(player, "choice-value", "value", style.nickname()));
        action(window, 20, name, () -> prompt(player, FeaturePermission.Command.PERSONALIZE, "collection.name-prompt",
                value -> updateStyle(player, id, current -> new CompanionStyle(value, current.variantId(), current.particleId(), current.formationId())), () -> personalize(player, id)));
        action(window, 29, button(player, "name-reset", Material.MILK_BUCKET),
                () -> updateStyle(player, id, current -> new CompanionStyle(null, current.variantId(), current.particleId(), current.formationId())));
        action(window, 22, button(player, "variants", Material.LEATHER_CHESTPLATE), () -> variants(player, id, 0));
        action(window, 24, button(player, "formation", Material.COMPASS), () -> formation(player, id));
        action(window, 32, button(player, "formation-earlier", Material.ARROW), () -> moveFormation(player, id, -1));
        action(window, 33, button(player, "formation-later", Material.SPECTRAL_ARROW), () -> moveFormation(player, id, 1));
        action(window, 46, button(player, "studio-back", Material.ARROW), () -> menus.openDashboard(player, id));
        menus.show(window);
    }

    private void variants(Player player, String id, int requestedPage) {
        if (!owned(player, id)) return;
        CollectionProfile profile = profile(player);
        if (profile == null) return;
        CompanionStyle style = plugin.collectionService().style(player, id);
        List<String> choices = plugin.collectionVariants().variants(id);
        int page = CompanionMenus.page(requestedPage, choices.size());
        UIWindow window = menus.window(player, "variants");
        for (int index = page * 28; index < Math.min(choices.size(), (page + 1) * 28); index++) {
            String variant = choices.get(index);
            boolean entitled = player.hasPermission("bytecompanions.variant." + id + "." + variant);
            UIElement entry = new UIElement(variant).setBaseItemStack(new ItemStack(entitled ? Material.LEATHER_CHESTPLATE : Material.GRAY_DYE))
                    .setName(menus.text(player, "choice-value", "value", variant));
            entry.setEnchanted(variant.equals(style.variantId()));
            if (!entitled) entry.addLore(menus.text(player, "locked"));
            action(window, CompanionMenus.contentSlot(index % 28), entry, () -> {
                if (!allowed(player, "bytecompanions.variant." + id + "." + variant)) return;
                updateStyle(player, id, current -> new CompanionStyle(current.nickname(), variant, current.particleId(), current.formationId()));
            });
        }
        if (choices.isEmpty()) CompanionMenus.put(window, 22, button(player, "empty", Material.BARRIER));
        action(window, 48, button(player, "default-choice", Material.MILK_BUCKET),
                () -> updateStyle(player, id, current -> new CompanionStyle(current.nickname(), null, current.particleId(), current.formationId())));
        action(window, 46, button(player, "studio-back", Material.ARROW), () -> personalize(player, id));
        menus.navigation(window, page, choices.size(), next -> variants(player, id, next));
        menus.show(window);
    }

    private void formation(Player player, String id) {
        if (!owned(player, id)) return;
        CollectionProfile profile = profile(player);
        if (profile == null) return;
        CompanionStyle style = plugin.collectionService().style(player, id);
        UIWindow window = menus.window(player, "formation");
        List<String> formations = List.of("default", "line", "grid", "orbit");
        Material[] icons = {Material.MILK_BUCKET, Material.STRING, Material.IRON_BARS, Material.ENDER_PEARL};
        for (int index = 0; index < formations.size(); index++) {
            String choice = formations.get(index);
            String value = choice.equals("default") ? null : choice;
            UIElement entry = button(player, choice.equals("default") ? "default-choice" : "formation-" + choice, icons[index]);
            entry.setEnchanted(Objects.equals(value, style.formationId()));
            action(window, 19 + index * 2, entry,
                    () -> updateStyle(player, id, current -> new CompanionStyle(current.nickname(), current.variantId(), current.particleId(), value)));
        }
        action(window, 46, button(player, "studio-back", Material.ARROW), () -> personalize(player, id));
        menus.show(window);
    }

    private void updateStyle(Player player, String id, UnaryOperator<CompanionStyle> mutation) {
        if (!owned(player, id)) return;
        change(player, () -> plugin.collectionService().updateStyle(player, id,
                mutation.apply(plugin.collectionService().style(player, id))), () -> personalize(player, id));
    }

    private void moveFormation(Player player, String id, int direction) {
        if (!owned(player, id)) return;
        CollectionProfile profile = profile(player);
        if (profile == null) return;
        CompanionPlayer state = plugin.getCompanionPlayerOrNull(player);
        ArrayList<String> order = new ArrayList<>(profile.formationOrder().stream().filter(state::hasCompanion).toList());
        state.companions().keySet().stream().sorted().filter(selected -> !order.contains(selected)).forEach(order::add);
        int index = order.indexOf(id);
        if (index < 0) { plugin.languageService().send(player, "gui.equip-first"); return; }
        int destination = Math.max(0, Math.min(order.size() - 1, index + direction));
        order.remove(index);
        order.add(destination, id);
        change(player, () -> plugin.collectionService().updateFormationOrder(player, order), () -> personalize(player, id));
    }

    private void interactions(Player player, String id) {
        if (!allowed(player, FeaturePermission.Command.INTERACT)) return;
        UIWindow window = menus.window(player, "interactions");
        CompanionAction[] actions = {CompanionAction.PET, CompanionAction.WAVE, CompanionAction.SIT, CompanionAction.STAY, CompanionAction.CALL};
        Material[] icons = {Material.APPLE, Material.FEATHER, Material.OAK_STAIRS, Material.ANVIL, Material.LEAD};
        for (int index = 0; index < actions.length; index++) {
            CompanionAction selected = actions[index];
            action(window, 20 + index, button(player, "action-" + selected.name().toLowerCase(Locale.ROOT), icons[index]),
                    () -> interact(player, selected.name(), id));
        }
        action(window, 46, button(player, "studio-back", Material.ARROW), () -> menus.openDashboard(player, id));
        menus.show(window);
    }

    void interact(Player player, String action, String requestedId) {
        if (!allowed(player, FeaturePermission.Command.INTERACT)) return;
        CompanionPlayer state = plugin.getCompanionPlayerOrNull(player);
        String id = requestedId;
        if (id.isBlank() && state.companions().size() == 1) id = state.companions().keySet().iterator().next();
        Companion companion = state.companions().get(id);
        if (companion == null || !companion.isSpawned()) {
            plugin.languageService().send(player, "collection.choose-equipped");
            return;
        }
        CompanionAction requested;
        try {
            requested = CompanionAction.valueOf(action.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException failure) {
            plugin.languageService().send(player, "command.feedback.usage", "usage", "/bytecompanions interact <pet|wave|sit|stay|call> [companion]");
            return;
        }
        CompanionActionResult result = companion.behavior().act(requested);
        plugin.languageService().send(player, switch (result) {
            case APPLIED -> "collection.action-applied";
            case COOLDOWN -> "collection.cooldown";
            case DISABLED -> "gui.disabled";
        });
    }

    private void prompt(Player player, String permission, String key, Consumer<String> accepted, Runnable cancelled) {
        if (!allowed(player, permission)) return;
        menus.close(player);
        Object token = new Object();
        pending.put(player.getUniqueId(), token);
        plugin.languageService().send(player, key);
        boolean requested = input.request(player, new BukkitTextInput.Request(32, 1200,
                () -> runtime.getAsBoolean() && pending.get(player.getUniqueId()) == token && player.hasPermission(permission),
                value -> {
                    pending.remove(player.getUniqueId(), token);
                    try {
                        accepted.accept(value.trim());
                    } catch (IllegalArgumentException failure) {
                        reject(player, failure);
                        cancelled.run();
                    }
                }, reason -> {
                    if (!pending.remove(player.getUniqueId(), token)) return;
                    plugin.languageService().send(player, switch (reason) {
                        case CANCELLED -> "studio.cancelled";
                        case EXPIRED -> "collection.input-expired";
                        case TOO_LONG -> "collection.too-long";
                    });
                    cancelled.run();
                }));
        if (!requested && pending.remove(player.getUniqueId(), token)) cancelled.run();
    }

    private void reject(Player player, IllegalArgumentException failure) {
        if (failure instanceof CollectionFailure rejected) {
            plugin.languageService().send(player, "collection.rejected", "detail", rejected.render(plugin.languageService(), player));
        } else {
            plugin.getLogger().log(Level.SEVERE, "Could not update companion collection", failure);
            plugin.languageService().send(player, "command.feedback.failed");
        }
    }

    private void change(Player player, Supplier<CompletableFuture<CollectionService.Result>> operation, Runnable completed) {
        if (pending.containsKey(player.getUniqueId())) {
            plugin.languageService().send(player, "collection.working");
            return;
        }
        Object token = new Object();
        pending.put(player.getUniqueId(), token);
        CompletableFuture<CollectionService.Result> result;
        try {
            result = operation.get();
        } catch (IllegalArgumentException failure) {
            pending.remove(player.getUniqueId(), token);
            reject(player, failure);
            completed.run();
            return;
        } catch (RuntimeException failure) {
            pending.remove(player.getUniqueId(), token);
            plugin.getLogger().log(Level.SEVERE, "Could not update companion collection", failure);
            plugin.languageService().send(player, "command.feedback.failed");
            return;
        }
        result.whenComplete((outcome, failure) -> {
            if (failure != null) plugin.getLogger().log(Level.SEVERE, "Could not save companion collection", failure);
            Runnable retired = () -> pending.remove(player.getUniqueId(), token);
            boolean scheduled = FoliaScheduler.runEntity(plugin, player, () -> {
                if (!pending.remove(player.getUniqueId(), token) || !runtime.getAsBoolean() || !player.isOnline()) return;
                if (failure != null) plugin.languageService().send(player, "command.feedback.failed");
                else if (outcome.success()) plugin.languageService().send(player, "collection.saved");
                else plugin.languageService().send(player, "collection.rejected", "detail", outcome.failure().render(plugin.languageService(), player));
                completed.run();
            }, 0L, retired);
            if (!scheduled) retired.run();
        });
    }

    private CollectionProfile profile(Player player) {
        CollectionProfile profile = plugin.collectionService().profile(player.getUniqueId()).orElse(null);
        if (profile == null) plugin.languageService().send(player, "gui.loading");
        return profile;
    }

    private boolean allowed(Player player, String permission) {
        if (!menus.accessible(player)) return false;
        if (player.hasPermission(permission)) return true;
        plugin.languageService().send(player, "gui.locked");
        return false;
    }

    private boolean owned(Player player, String id) {
        return allowed(player, FeaturePermission.Command.PERSONALIZE)
                && allowed(player, FeaturePermission.Companions.resolve(id));
    }

    private UIElement button(Player player, String key, Material material) {
        return new UIElement(key).setBaseItemStack(new ItemStack(material)).setName(menus.text(player, key));
    }

    private void action(UIWindow window, int slot, UIElement element, Runnable action) {
        menus.action(window, element, action);
        CompanionMenus.put(window, slot, element);
    }

    private static String accessKey(int access) {
        return switch (access) { case 1 -> "filter-owned"; case 2 -> "filter-locked"; default -> "filter-all"; };
    }

    void navigated(Player player) {
        pending.remove(player.getUniqueId());
        input.cancel(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onCommand(PlayerCommandPreprocessEvent event) { navigated(event.getPlayer()); }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        navigated(event.getPlayer());
        browsing.remove(event.getPlayer().getUniqueId());
    }

    void close() {
        HandlerList.unregisterAll(this);
        input.close();
        pending.clear();
        browsing.clear();
    }

    private record Browse(boolean favorites, int access, String category) { }
}
