package com.volmit.bytecompanions.platform.paper.ui.dashboard;

import art.arcane.volmlib.util.inventorygui.UIElement;
import art.arcane.volmlib.util.inventorygui.UIWindow;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import art.arcane.volmlib.util.plugin.ComponentText;
import net.kyori.adventure.text.minimessage.MiniMessage;
import com.volmit.bytecompanions.platform.paper.PaperPlugin;
import com.volmit.bytecompanions.platform.paper.progression.ProgressionDefinitions;
import com.volmit.bytecompanions.platform.paper.progression.ProgressionService;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.logging.Level;

import static com.volmit.bytecompanions.platform.paper.ui.dashboard.CompanionMenus.put;

final class ProgressionMenus {
    private static final int PAGE_SIZE = 28;
    private final PaperPlugin plugin;
    private final CompanionMenus menus;
    private final ProgressionService service;

    ProgressionMenus(PaperPlugin plugin, CompanionMenus menus) {
        this.plugin = plugin;
        this.menus = menus;
        this.service = plugin.progressionService();
    }

    void openUpgrades(Player player, String companionId, int requestedPage) {
        if (!accessible(player, "progression", "bytecompanions.command.upgrades")) return;
        if (plugin.typeRegistry().getOrNull(companionId) == null) {
            plugin.languageService().send(player, "command.feedback.companion-required");
            return;
        }
        ProgressionService.Snapshot snapshot = service.snapshot(player.getUniqueId(), companionId);
        if (!ready(player, snapshot)) return;
        ProgressionDefinitions.CompanionDefinition definition = service.definitions().companions().get(companionId);
        List<Map.Entry<String, ProgressionDefinitions.TrackDefinition>> tracks = definition == null ? List.of()
                : definition.tracks().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList();
        int page = page(requestedPage, tracks.size());
        UIWindow window = menus.window(player, "upgrades");
        UIElement heading = menus.icon(companionId, menus.companionIcon(player, plugin.typeRegistry().getOrNull(companionId)));
        heading.addLore(text(player, "progress-level", "level", snapshot.level()));
        heading.addLore(snapshot.nextThreshold() > 0
                ? text(player, "progress-xp", "xp", snapshot.totalXp(), "required", snapshot.nextThreshold())
                : text(player, "max-level"));
        heading.addLore(text(player, "page", "page", page + 1, "pages", pages(tracks.size())));
        put(window, 4, heading);
        for (int index = page * PAGE_SIZE; index < Math.min(tracks.size(), (page + 1) * PAGE_SIZE); index++) {
            Map.Entry<String, ProgressionDefinitions.TrackDefinition> entry = tracks.get(index);
            int tier = snapshot.tracks().getOrDefault(entry.getKey(), 0);
            UIElement element = track(player, snapshot, entry.getKey(), entry.getValue());
            if (tier < entry.getValue().tiers().size()) {
                menus.action(window, element, () -> confirmUpgrade(player, companionId, entry.getKey(), page));
            }
            put(window, CompanionMenus.contentSlot(index % PAGE_SIZE), element);
        }
        if (tracks.isEmpty()) put(window, 22, menus.button(player, "no-upgrades", Material.BOOK, "no-upgrades-hint"));
        menus.back(window, companionId);
        menus.navigation(window, page, tracks.size(), next -> openUpgrades(player, companionId, next));
        menus.show(window);
    }

    private UIElement track(Player player, ProgressionService.Snapshot snapshot, String id,
                            ProgressionDefinitions.TrackDefinition track) {
        int tier = snapshot.tracks().getOrDefault(id, 0);
        UIElement element = new UIElement(id).setBaseItemStack(new ItemStack(Material.ANVIL)).setName(styled(track.name()));
        element.addLore(text(player, "upgrade-tier", "tier", tier, "maximum", track.tiers().size()));
        element.addLore("");
        if (tier >= track.tiers().size()) {
            element.addLore(text(player, "max-tier"));
            element.setEnchanted(true);
        } else {
            ProgressionDefinitions.TierDefinition next = track.tiers().get(tier);
            for (Map.Entry<String, Double> addition : next.additions().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
                if (!targetEnabled(addition.getKey())) {
                    element.addLore(label(addition.getKey()) + ": " + text(player, "disabled"));
                    continue;
                }
                double current = contribution(player, snapshot.companionId(), addition.getKey());
                element.addLore(text(player, "upgrade-change", "bonus", label(addition.getKey()),
                        "current", number(current), "next", number(current + addition.getValue())));
            }
            element.addLore("");
            element.addLore(text(player, "required-level", "level", next.requiredLevel()));
            cost(player, element, next.cost());
            element.addLore("");
            element.addLore(text(player, snapshot.level() < next.requiredLevel() ? "level-locked" : "upgrade-preview"));
        }
        return element;
    }

    private void confirmUpgrade(Player player, String companionId, String trackId, int returnPage) {
        if (!accessible(player, "progression", "bytecompanions.command.upgrades")) return;
        ProgressionService.Snapshot snapshot = service.snapshot(player.getUniqueId(), companionId);
        if (!ready(player, snapshot)) return;
        ProgressionDefinitions.CompanionDefinition companion = service.definitions().companions().get(companionId);
        ProgressionDefinitions.TrackDefinition track = companion == null ? null : companion.tracks().get(trackId);
        if (track == null) return;
        int tier = snapshot.tracks().getOrDefault(trackId, 0);
        if (tier >= track.tiers().size()) {
            openUpgrades(player, companionId, returnPage);
            return;
        }
        UIWindow window = menus.window(player, "purchase");
        put(window, 13, track(player, snapshot, trackId, track));
        ProgressionDefinitions.TierDefinition next = track.tiers().get(tier);
        boolean enabled = next.additions().keySet().stream().allMatch(this::targetEnabled);
        boolean permitted = player.hasPermission("bytecompanions.companion." + companionId);
        boolean allowed = enabled && permitted && snapshot.level() >= next.requiredLevel();
        UIElement confirm = menus.button(player, "confirm-purchase", allowed ? Material.LIME_DYE : Material.GRAY_DYE,
                allowed ? "confirm-purchase-hint" : !enabled ? "disabled" : !permitted ? "locked" : "level-locked");
        if (allowed) menus.action(window, confirm, () -> complete(window,
                service.purchase(player, companionId, trackId, tier, snapshot.revision()),
                () -> openUpgrades(player, companionId, returnPage)));
        put(window, 22, confirm);
        UIElement back = menus.button(player, "cancel-purchase", Material.ARROW, "cancel-purchase-hint");
        menus.action(window, back, () -> openUpgrades(player, companionId, returnPage));
        put(window, 46, back);
        menus.show(window);
    }

    void openBoosters(Player player, int requestedPage) {
        if (!accessible(player, "boosters", "bytecompanions.command.boosts")) return;
        ProgressionService.Snapshot snapshot = service.snapshot(player.getUniqueId(), "");
        if (!ready(player, snapshot)) return;
        List<Map.Entry<String, ProgressionDefinitions.BoosterDefinition>> definitions = service.definitions().boosters()
                .entrySet().stream().sorted(Map.Entry.comparingByKey()).toList();
        int page = page(requestedPage, definitions.size());
        UIWindow window = menus.window(player, "boosts");
        put(window, 4, menus.button(player, "boosts", Material.HONEY_BOTTLE, "boosts-hint"));
        for (int index = page * PAGE_SIZE; index < Math.min(definitions.size(), (page + 1) * PAGE_SIZE); index++) {
            Map.Entry<String, ProgressionDefinitions.BoosterDefinition> entry = definitions.get(index);
            UIElement element = booster(player, entry.getKey(), entry.getValue(), snapshot);
            element.addLore("");
            element.addLore(text(player, "booster-open"));
            menus.action(window, element, () -> openBooster(player, entry.getKey(), page));
            put(window, CompanionMenus.contentSlot(index % PAGE_SIZE), element);
        }
        if (definitions.isEmpty()) put(window, 22, menus.button(player, "no-boosters", Material.GLASS_BOTTLE, "no-boosters-hint"));
        UIElement active = menus.button(player, "active-boosts", Material.CLOCK, "active-boosts-hint");
        menus.action(window, active, () -> openActiveBoosters(player, 0));
        put(window, 48, active);
        collection(window);
        menus.navigation(window, page, definitions.size(), next -> openBoosters(player, next));
        menus.show(window);
    }

    private void openBooster(Player player, String id, int returnPage) {
        if (!accessible(player, "boosters", "bytecompanions.command.boosts")) return;
        ProgressionService.Snapshot snapshot = service.snapshot(player.getUniqueId(), "");
        if (!ready(player, snapshot)) return;
        ProgressionDefinitions.BoosterDefinition definition = service.definitions().boosters().get(id);
        if (definition == null) return;
        UIWindow window = menus.window(player, "booster");
        UIElement overview = booster(player, id, definition, snapshot);
        cost(player, overview, definition.cost());
        put(window, 13, overview);
        boolean permitted = player.hasPermission("bytecompanions.booster." + id);
        UIElement buy = menus.button(player, "buy-booster", permitted ? Material.EMERALD : Material.GRAY_DYE,
                permitted ? "buy-booster-hint" : "locked");
        if (permitted) menus.action(window, buy, () -> confirmBooster(player, id, returnPage));
        put(window, 20, buy);
        boolean owned = permitted && snapshot.credits().getOrDefault(id, 0) > 0;
        UIElement activate = menus.button(player, "activate-booster", owned ? Material.HONEY_BOTTLE : Material.GLASS_BOTTLE,
                owned ? "activate-booster-hint" : "no-booster-credit");
        if (owned) menus.action(window, activate, () -> complete(window, service.activate(player, id, snapshot.revision()),
                () -> openActiveBoosters(player, 0)));
        put(window, 24, activate);
        boosterBack(window, returnPage);
        menus.show(window);
    }

    private void confirmBooster(Player player, String id, int returnPage) {
        if (!accessible(player, "boosters", "bytecompanions.command.boosts")) return;
        ProgressionService.Snapshot snapshot = service.snapshot(player.getUniqueId(), "");
        if (!ready(player, snapshot)) return;
        ProgressionDefinitions.BoosterDefinition definition = service.definitions().boosters().get(id);
        if (definition == null) return;
        UIWindow window = menus.window(player, "purchase");
        UIElement overview = booster(player, id, definition, snapshot);
        cost(player, overview, definition.cost());
        put(window, 13, overview);
        UIElement confirm = menus.button(player, "confirm-purchase", Material.LIME_DYE, "confirm-purchase-hint");
        menus.action(window, confirm, () -> complete(window, service.purchaseBooster(player, id, snapshot.revision()),
                () -> openBooster(player, id, returnPage)));
        put(window, 22, confirm);
        boosterBack(window, returnPage);
        menus.show(window);
    }

    private UIElement booster(Player player, String id, ProgressionDefinitions.BoosterDefinition definition,
                              ProgressionService.Snapshot snapshot) {
        UIElement element = new UIElement(id).setBaseItemStack(new ItemStack(Material.HONEY_BOTTLE)).setName(styled(definition.name()));
        element.addLore(text(player, "booster-scope." + definition.scope().name().toLowerCase(Locale.ROOT)));
        for (Map.Entry<String, Double> channel : definition.percentages().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            element.addLore(text(player, "booster-channel", "channel", label(channel.getKey()), "percent", number(channel.getValue())));
        }
        element.addLore(text(player, "booster-duration", "time", duration(definition.durationMillis())));
        element.addLore(text(player, "booster-rule." + definition.stacking().name().toLowerCase(Locale.ROOT)));
        element.addLore("");
        element.addLore(text(player, "booster-credits", "count", snapshot.credits().getOrDefault(id, 0)));
        return element;
    }

    private void cost(Player player, UIElement element, ProgressionDefinitions.Cost cost) {
        if (cost.free()) element.addLore(text(player, "cost-free"));
        if (cost.money() > 0) element.addLore(text(player, "cost-money", "amount", number(cost.money())));
        for (Map.Entry<String, Integer> item : cost.items().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            element.addLore(text(player, "cost-item", "amount", item.getValue(), "item", label(item.getKey())));
        }
    }

    private double contribution(Player player, String companionId, String target) {
        CompanionBonusService.Snapshot snapshot = menus.bonusSnapshot(player, companionId);
        if (target.startsWith("attribute:")) {
            return snapshot.attributes().stream().filter(bonus -> ("attribute:" + bonus.attribute()).equals(target))
                    .mapToDouble(CompanionBonusService.AttributeBonus::amount).sum();
        }
        if (target.startsWith("effect:")) {
            return snapshot.effects().stream().filter(bonus -> ("effect:" + bonus.effect()).equals(target))
                    .mapToDouble(bonus -> bonus.amplifier() + 1).max().orElse(0);
        }
        return snapshot.buffs().stream().filter(bonus -> ("buff:" + bonus.key().name().toLowerCase(Locale.ROOT)).equals(target))
                .mapToDouble(CompanionBonusService.BuffBonus::value).findFirst().orElse(0);
    }

    private void openActiveBoosters(Player player, int requestedPage) {
        if (!accessible(player, "boosters", "bytecompanions.command.boosts")) return;
        ProgressionService.Snapshot snapshot = service.snapshot(player.getUniqueId(), "");
        if (!ready(player, snapshot)) return;
        List<ProgressionService.ActiveBooster> active = new ArrayList<>(snapshot.personalBoosters());
        active.addAll(snapshot.serverBoosters());
        active.sort(Comparator.comparing(ProgressionService.ActiveBooster::queued)
                .thenComparingLong(ProgressionService.ActiveBooster::startsAt)
                .thenComparing(ProgressionService.ActiveBooster::activationId));
        int page = page(requestedPage, active.size());
        UIWindow window = menus.window(player, "active-boosts");
        UIElement heading = menus.button(player, "active-boosts", Material.CLOCK, "active-boosts-hint");
        heading.addLore("");
        for (String channel : ProgressionDefinitions.CHANNELS.stream().sorted().toList()) {
            double percent = service.boosterPercent(player.getUniqueId(), channel);
            if (percent > 0) heading.addLore(text(player, "booster-total", "channel", label(channel), "percent", number(percent)));
        }
        heading.addLore(text(player, "booster-cap", "percent", number(service.definitions().maximumBoosterPercent())));
        put(window, 4, heading);
        for (int index = page * PAGE_SIZE; index < Math.min(active.size(), (page + 1) * PAGE_SIZE); index++) {
            ProgressionService.ActiveBooster boost = active.get(index);
            ProgressionDefinitions.BoosterDefinition definition = service.definitions().boosters().get(boost.boosterId());
            UIElement element = new UIElement(boost.activationId()).setBaseItemStack(new ItemStack(boost.queued() ? Material.CLOCK : Material.HONEY_BOTTLE))
                    .setName(styled(definition == null ? boost.boosterId() : definition.name()));
            element.addLore(text(player, "booster-scope." + boost.scope().name().toLowerCase(Locale.ROOT)));
            element.addLore(text(player, boost.queued() ? "booster-queued" : "booster-running"));
            for (Map.Entry<String, Double> channel : boost.percentages().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
                element.addLore(text(player, "booster-channel", "channel", label(channel.getKey()), "percent", number(channel.getValue())));
            }
            element.addLore(text(player, boost.queued() ? "booster-duration" : "booster-remaining", "time", duration(boost.remainingMillis())));
            element.setEnchanted(!boost.queued());
            put(window, CompanionMenus.contentSlot(index % PAGE_SIZE), element);
        }
        if (active.isEmpty()) put(window, 22, menus.button(player, "no-active-boosts", Material.GLASS_BOTTLE, "no-active-boosts-hint"));
        UIElement refresh = menus.button(player, "refresh", Material.CLOCK, "refresh-hint");
        menus.action(window, refresh, () -> openActiveBoosters(player, page));
        put(window, 48, refresh);
        boosterBack(window, 0);
        menus.navigation(window, page, active.size(), next -> openActiveBoosters(player, next));
        menus.show(window);
    }

    private void boosterBack(UIWindow window, int page) {
        UIElement back = menus.button(window.getViewer(), "back-boosts", Material.ARROW, "back-boosts-hint");
        menus.action(window, back, () -> openBoosters(window.getViewer(), page));
        put(window, 46, back);
    }

    private void collection(UIWindow window) {
        UIElement back = menus.button(window.getViewer(), "collection", Material.CHEST, "collection-hint");
        menus.action(window, back, () -> menus.openCollection(window.getViewer(), 0));
        put(window, 46, back);
    }

    private boolean accessible(Player player, String feature, String permission) {
        if (!menus.accessible(player)) return false;
        if (service == null || !plugin.configuration().features().enabled(feature)) {
            plugin.languageService().send(player, "gui.disabled");
            return false;
        }
        if (!player.hasPermission(permission)) {
            plugin.languageService().send(player, "gui.locked");
            return false;
        }
        return true;
    }

    private boolean targetEnabled(String target) {
        return plugin.configuration().features().enabled(target.startsWith("attribute:") ? "attributes"
                : target.startsWith("effect:") ? "effects" : "buffs");
    }

    private boolean ready(Player player, ProgressionService.Snapshot snapshot) {
        if (!snapshot.ready() || snapshot.busy()) {
            plugin.languageService().send(player, snapshot.busy() ? "gui.purchase-busy" : "gui.loading");
            return false;
        }
        return true;
    }

    private void complete(UIWindow window, CompletionStage<ProgressionService.MutationResult> operation, Runnable refresh) {
        Player player = window.getViewer();
        operation.whenComplete((result, failure) -> {
            if (failure != null) {
                plugin.getLogger().log(Level.SEVERE, "Companion progression operation failed for " + player.getUniqueId(), failure);
            }
            if (plugin.progressionService() != service || !service.active()) return;
            FoliaScheduler.runEntity(plugin, player, () -> {
                if (!player.isOnline() || plugin.progressionService() != service || !service.active()) return;
                plugin.languageService().send(player, failure != null ? "gui.purchase-failed"
                        : "progression.result." + result.status().name().toLowerCase(Locale.ROOT));
                if (failure == null && result.transactionId() != null && !result.transactionId().isBlank()) {
                    plugin.languageService().send(player, "command.transactions.reference", "transaction", result.transactionId());
                }
                if (menus.current(window) && menus.accessible(player)) refresh.run();
            });
        });
    }

    private String text(Player player, String key, Object... arguments) {
        String[] values = new String[arguments.length];
        for (int index = 0; index < arguments.length; index++) values[index] = String.valueOf(arguments[index]);
        return menus.text(player, key, values);
    }

    private static String styled(String value) {
        return ComponentText.component(MiniMessage.miniMessage().deserialize(value)).legacy();
    }

    private static String number(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private static String label(String value) {
        String name = value.replace("minecraft:", "").replace(':', ' ').replace('_', ' ');
        return name.isEmpty() ? name : Character.toUpperCase(name.charAt(0)) + name.substring(1).toLowerCase(Locale.ROOT);
    }

    private static String duration(long millis) {
        long seconds = Math.max(0, (millis + 999) / 1000);
        return seconds >= 3600 ? seconds / 3600 + "h " + seconds % 3600 / 60 + "m"
                : seconds >= 60 ? seconds / 60 + "m " + seconds % 60 + "s" : seconds + "s";
    }

    private static int pages(int count) { return Math.max(1, (count + PAGE_SIZE - 1) / PAGE_SIZE); }
    private static int page(int requested, int count) { return Math.max(0, Math.min(requested, pages(count) - 1)); }
}
