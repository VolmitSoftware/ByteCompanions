package com.volmit.bytecompanions.platform.paper.ui.dashboard;

import art.arcane.volmlib.util.inventorygui.UIElement;
import art.arcane.volmlib.util.inventorygui.UIWindow;
import art.arcane.volmlib.util.localization.MessageArgs;
import art.arcane.volmlib.util.plugin.ComponentText;
import com.volmit.bytecompanions.platform.paper.util.scheduling.BukkitOwnerTasks;
import com.volmit.bytecompanions.api.companion.CompanionType;
import com.volmit.bytecompanions.api.user.CompanionPlayer;
import com.volmit.bytecompanions.core.companion.DefaultCompanionType;
import com.volmit.bytecompanions.features.particles.ParticlesFeature;
import com.volmit.bytecompanions.features.particles.registry.ParticleEffectEntry;
import com.volmit.bytecompanions.internal.itemblueprint.context.BlueprintContext;
import com.volmit.bytecompanions.platform.paper.FeaturePermission;
import com.volmit.bytecompanions.platform.paper.PaperPlugin;
import com.volmit.bytecompanions.platform.paper.localization.CompanionMessages;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

public final class CompanionMenus {
    private static final int PAGE_SIZE = 28;
    private final PaperPlugin plugin;
    private final ParticlesFeature particles;
    private final CompanionBonusService bonuses;
    private final CollectionMenus collections;
    private final BooleanSupplier runtimeActive;
    private final Map<UUID, UIWindow> windows = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> collectionPages = new ConcurrentHashMap<>();
    private volatile boolean closed;

    public CompanionMenus(PaperPlugin plugin, ParticlesFeature particles, CompanionBonusService bonuses) {
        this.plugin = plugin;
        this.particles = particles;
        this.bonuses = bonuses;
        this.runtimeActive = plugin.captureRuntimeGuard();
        this.collections = new CollectionMenus(plugin, this);
    }

    public void openHome(Player player) {
        if (!accessible(player)) {
            return;
        }
        CompanionPlayer state = plugin.getCompanionPlayerOrNull(player);
        if (state.companions().size() == 1) {
            openDashboard(player, state.companions().keySet().iterator().next());
        } else {
            openCollection(player, 0);
        }
    }

    public void openUpgrades(Player player, String companionId, int page) {
        new ProgressionMenus(plugin, this).openUpgrades(player, companionId, page);
    }

    public void openBoosters(Player player, int page) {
        new ProgressionMenus(plugin, this).openBoosters(player, page);
    }

    public void openSetup(Player player) {
        new StarterSetupMenus(plugin, this).open(player);
    }

    public void openCollection(Player player, int requestedPage) {
        if (!accessible(player) || !collections.ready(player)) {
            return;
        }
        List<CompanionType> types = collections.filter(player, plugin.typeRegistry().all().stream().toList());
        int page = page(requestedPage, types.size());
        collectionPages.put(player.getUniqueId(), page);
        UIWindow window = window(player, "collection");
        UIElement heading = button(player, "collection", Material.CHEST, "collection-hint");
        heading.addLore("");
        heading.addLore(text(player, "page", "page", Integer.toString(page + 1), "pages", Integer.toString(pages(types.size()))));
        put(window, 4, heading);
        for (int index = page * PAGE_SIZE; index < Math.min(types.size(), (page + 1) * PAGE_SIZE); index++) {
            CompanionType type = types.get(index);
            UIElement element = icon(type.id(), companionIcon(player, type));
            element.addLore(text(player, equipped(player, type.id()) ? "equipped" :
                    player.hasPermission(FeaturePermission.Companions.resolve(type.id())) ? "available" : "locked"));
            element.addLore(text(player, "open-dashboard"));
            if (collections.favorite(player, type.id())) element.addLore(text(player, "favorite-on"));
            element.setEnchanted(equipped(player, type.id()));
            action(window, element, () -> openDashboard(player, type.id()));
            put(window, contentSlot(index % PAGE_SIZE), element);
        }
        if (types.isEmpty()) {
            put(window, 22, button(player, "empty", Material.BARRIER, "empty-hint"));
        }
        navigation(window, page, types.size(), next -> openCollection(player, next));
        collections.filters(window);
        settingsButton(window);
        if (player.hasPermission(FeaturePermission.Command.SETUP)) {
            UIElement setup = button(player, "setup", Material.CRAFTING_TABLE, "setup-hint");
            action(window, setup, () -> openSetup(player));
            put(window, 51, setup);
        }
        show(window);
    }

    public void openOutfits(Player player, int page) { collections.outfits(player, page); }

    public void settings(Player player, String setting, String value) { collections.settings(player, setting, value); }

    public void interact(Player player, String action, String companionId) { collections.interact(player, action, companionId); }

    public void openDashboard(Player player, String companionId) {
        if (!accessible(player)) {
            return;
        }
        CompanionType type = plugin.typeRegistry().getOrNull(companionId);
        if (type == null) {
            openCollection(player, 0);
            return;
        }
        UIWindow window = window(player, "dashboard");
        boolean equipped = equipped(player, companionId);
        UIElement overview = icon(companionId, companionIcon(player, type));
        overview.addLore(text(player, equipped ? "equipped" : "not-equipped"));
        overview.addLore(text(player, "overview"));
        put(window, 13, overview);

        if (plugin.configuration().features().enabled("progression")) {
            UIElement upgrades = button(player, "upgrades", Material.ANVIL, "upgrades-hint");
            action(window, upgrades, () -> openUpgrades(player, companionId, 0));
            put(window, 30, upgrades);
        }
        if (plugin.configuration().features().enabled("boosters")) {
            UIElement boosts = button(player, "boosts", Material.HONEY_BOTTLE, "boosts-hint");
            action(window, boosts, () -> openBoosters(player, 0));
            put(window, 32, boosts);
        }

        boolean mayToggle = player.hasPermission(equipped ? FeaturePermission.Command.DESPAWN : FeaturePermission.Command.SPAWN)
                && (equipped || player.hasPermission(FeaturePermission.Companions.resolve(companionId)));
        UIElement toggle = button(player, equipped ? "unequip" : "equip", equipped ? Material.GRINDSTONE : Material.LIME_DYE,
                mayToggle ? equipped ? "unequip-hint" : "equip-hint" : "locked");
        action(window, toggle, () -> {
            boolean current = equipped(player, companionId);
            if (!permission(player, current ? FeaturePermission.Command.DESPAWN : FeaturePermission.Command.SPAWN)) {
                return;
            }
            if (current) {
                plugin.companionPlayerManager().despawn(player, companionId);
            } else {
                plugin.companionPlayerManager().spawn(player, companionId);
            }
            openDashboard(player, companionId);
        });
        put(window, 22, toggle);

        UIElement particleButton = button(player, "particles", Material.FIREWORK_STAR,
                particles == null ? "disabled" : !player.hasPermission("bytecompanions.command.particles.menu") ? "locked"
                        : !equipped ? "equip-first" : "particles-hint");
        if (particles != null) {
            ParticleEffectEntry selected = selectedParticle(player, companionId);
            String selectedName = textPlain(player, "none");
            if (selected != null) {
                ItemStack selectedIcon = selected.itemBlueprint() == null ? new ItemStack(Material.FIREWORK_STAR)
                        : selected.itemBlueprint().build(context(player, companionId));
                particleButton.setBaseItemStack(selectedIcon);
                selectedName = selectedIcon.hasItemMeta() && selectedIcon.getItemMeta().hasDisplayName()
                        ? ComponentText.section(selectedIcon.getItemMeta().getDisplayName()).plain() : selected.id();
            }
            particleButton.addLore(text(player, "current-particle", "effect", selectedName));
        }
        action(window, particleButton, () -> openParticles(player, companionId, 0));
        put(window, 20, particleButton);
        UIElement bonusButton = button(player, "bonuses", Material.EXPERIENCE_BOTTLE, "bonuses-hint");
        action(window, bonusButton, () -> openBonuses(player, companionId, 0));
        put(window, 24, bonusButton);
        UIElement collection = button(player, "collection", Material.CHEST, "collection-hint");
        action(window, collection, () -> openCollection(player, collectionPages.getOrDefault(player.getUniqueId(), 0)));
        put(window, 46, collection);
        if (player.hasPermission(FeaturePermission.Command.EDIT)) {
            UIElement editor = button(player, "studio", Material.WRITABLE_BOOK, "studio-hint");
            action(window, editor, () -> plugin.companionStudio().open(player, companionId));
            put(window, 53, editor);
        }
        collections.dashboard(window, companionId);
        settingsButton(window);
        show(window);
    }

    public void openParticles(Player player, String companionId, int requestedPage) {
        if (!accessible(player) || !permission(player, "bytecompanions.command.particles.menu")) {
            return;
        }
        if (particles == null) {
            plugin.languageService().send(player, "gui.disabled");
            return;
        }
        if (!equipped(player, companionId)) {
            plugin.languageService().send(player, "gui.equip-first");
            return;
        }
        CompanionType type = plugin.typeRegistry().getOrNull(companionId);
        if (type == null) {
            openCollection(player, 0);
            return;
        }
        List<ParticleEffectEntry> entries = particles.particleEffectRegistry().entries().stream()
                .sorted(Comparator.comparing(ParticleEffectEntry::id)).toList();
        int page = page(requestedPage, entries.size());
        UIWindow window = window(player, "particles");
        UIElement heading = icon(companionId, companionIcon(player, type));
        heading.addLore(text(player, "particles-hint"));
        put(window, 4, heading);
        for (int index = page * PAGE_SIZE; index < Math.min(entries.size(), (page + 1) * PAGE_SIZE); index++) {
            ParticleEffectEntry entry = entries.get(index);
            ItemStack item = entry.itemBlueprint() == null ? new ItemStack(Material.FIREWORK_STAR)
                    : entry.itemBlueprint().build(context(player, companionId));
            UIElement element = icon(entry.id(), item);
            boolean selected = particles.playerEffectManager().hasEquipped(player, companionId, entry.id());
            boolean allowed = player.hasPermission("bytecompanions.command.particles.equip")
                    && player.hasPermission("bytecompanions.particle." + entry.id());
            element.addLore(text(player, selected ? "equipped" : allowed ? "select-particle" : "locked"));
            element.setEnchanted(selected);
            action(window, element, () -> {
                if (!permission(player, "bytecompanions.command.particles.equip")
                        || !permission(player, "bytecompanions.particle." + entry.id()) || !equipped(player, companionId)) {
                    return;
                }
                particles.playerEffectManager().equip(player, companionId, entry);
                openParticles(player, companionId, page);
            });
            put(window, contentSlot(index % PAGE_SIZE), element);
        }
        UIElement remove = button(player, "remove-particle", Material.MILK_BUCKET,
                player.hasPermission("bytecompanions.command.particles.unequip") ? "remove-particle-hint" : "locked");
        action(window, remove, () -> {
            if (permission(player, "bytecompanions.command.particles.unequip")) {
                particles.playerEffectManager().unequip(player, companionId);
                openParticles(player, companionId, page);
            }
        });
        put(window, 48, remove);
        back(window, companionId);
        navigation(window, page, entries.size(), next -> openParticles(player, companionId, next));
        show(window);
    }

    private ParticleEffectEntry selectedParticle(Player player, String companionId) {
        return particles.particleEffectRegistry().entries().stream()
                .filter(entry -> particles.playerEffectManager().hasEquipped(player, companionId, entry.id()))
                .findFirst().orElse(null);
    }

    public void openBonuses(Player player, String companionId, int requestedPage) {
        if (!accessible(player)) {
            return;
        }
        if (plugin.typeRegistry().getOrNull(companionId) == null) {
            openCollection(player, 0);
            return;
        }
        CompanionBonusService.Snapshot snapshot = bonuses.snapshot(player, plugin.getCompanionPlayerOrNull(player), companionId);
        List<UIElement> entries = new ArrayList<>();
        for (CompanionBonusService.AttributeBonus bonus : snapshot.attributes()) {
            UIElement element = bonus(player, "attribute", bonus.attribute().getKey(), Material.IRON_CHESTPLATE, bonus.status());
            element.addLore(text(player, "configured", "value", number(bonus.amount()), "operation", operation(player, bonus.operation().name())));
            if (bonus.currentTotal() != null) {
                element.addLore(text(player, "attribute-total", "value", number(bonus.currentTotal())));
            }
            contributors(player, element, bonus.contributors());
            entries.add(element);
        }
        for (CompanionBonusService.EffectBonus bonus : snapshot.effects()) {
            UIElement element = bonus(player, "effect", bonus.effect().getKey(), Material.POTION, bonus.status());
            element.addLore(text(player, "effect-level", "level", Integer.toString(bonus.amplifier() + 1)));
            element.addLore(text(player, "stack-mode", "mode", textPlain(player, "stack." + bonus.stackMode().name().toLowerCase(Locale.ROOT))));
            if (bonus.resolvedAmplifier() != null) {
                element.addLore(text(player, "resolved-level", "level", Integer.toString(bonus.resolvedAmplifier() + 1)));
            }
            if (bonus.currentAmplifier() != null) {
                element.addLore(text(player, "current-level", "level", Integer.toString(bonus.currentAmplifier() + 1)));
            }
            contributors(player, element, bonus.contributors());
            entries.add(element);
        }
        for (CompanionBonusService.BuffBonus bonus : snapshot.buffs()) {
            if (bonus.status() == CompanionBonusService.Status.DISABLED) {
                continue;
            }
            UIElement element = bonus(player, "buff", bonus.key().name(), Material.EXPERIENCE_BOTTLE, bonus.status());
            element.addLore(text(player, "configured", "value", number(bonus.value()), "operation", operation(player, bonus.operation().name())));
            element.addLore(text(player, "priority", "value", Integer.toString(bonus.priority())));
            if (bonus.boosterPercent() > 0) {
                element.addLore(text(player, "booster-applied", "percent", number(bonus.boosterPercent())));
            }
            if (bonus.winnerId() != null) {
                element.addLore(text(player, "winner", "companion", bonus.winnerId()));
                element.addLore(text(player, "winning-value", "value", number(bonus.winnerValue()), "operation", operation(player, bonus.winnerOperation().name())));
            }
            if (bonus.applyTo() != null) {
                element.addLore(text(player, "applies-to", "target", textPlain(player, "target." + bonus.applyTo().name().toLowerCase(Locale.ROOT))));
            }
            if (bonus.cooldownTicks() > 0) {
                element.addLore(text(player, "cooldown", "seconds", number(bonus.cooldownTicks() / 20.0)));
            }
            if (bonus.remainingCooldownMillis() > 0) {
                element.addLore(text(player, "cooldown-remaining", "seconds", number(bonus.remainingCooldownMillis() / 1000.0)));
            }
            if (!bonus.excludedSources().isEmpty()) {
                element.addLore(text(player, "excluded-sources", "sources", bonus.excludedSources().stream()
                        .map(source -> label(source.name())).sorted().reduce((first, second) -> first + ", " + second).orElse("")));
            }
            entries.add(element);
        }
        for (CompanionBonusService.Feature feature : CompanionBonusService.Feature.values()) {
            if (!snapshot.enabledFeatures().contains(feature)) {
                UIElement disabled = button(player, "feature." + feature.name().toLowerCase(Locale.ROOT), Material.GRAY_DYE, "disabled");
                entries.add(disabled);
            }
        }
        int page = page(requestedPage, entries.size());
        UIWindow window = window(player, "bonuses");
        UIElement heading = icon(companionId, companionIcon(player, plugin.typeRegistry().getOrNull(companionId)));
        heading.addLore(text(player, "bonus-snapshot"));
        put(window, 4, heading);
        if (entries.isEmpty()) {
            put(window, 22, button(player, "no-bonuses", Material.GLASS_BOTTLE, "no-bonuses-hint"));
        }
        for (int index = page * PAGE_SIZE; index < Math.min(entries.size(), (page + 1) * PAGE_SIZE); index++) {
            put(window, contentSlot(index % PAGE_SIZE), entries.get(index));
        }
        UIElement refresh = button(player, "refresh", Material.CLOCK, "refresh-hint");
        action(window, refresh, () -> openBonuses(player, companionId, page));
        put(window, 48, refresh);
        navigation(window, page, entries.size(), next -> openBonuses(player, companionId, next));
        back(window, companionId);
        show(window);
    }

    private UIElement bonus(Player player, String category, String name, Material material, CompanionBonusService.Status status) {
        UIElement element = new UIElement(category + ":" + name).setBaseItemStack(new ItemStack(material))
                .setName(text(player, "bonus-name", "category", textPlain(player, category), "name", label(name)));
        element.addLore(text(player, "status." + status.name().toLowerCase(Locale.ROOT)));
        return element;
    }

    private void contributors(Player player, UIElement element, List<String> contributors) {
        if (!contributors.isEmpty()) {
            element.addLore(text(player, "contributors", "companions", String.join(", ", contributors)));
        }
    }

    private String operation(Player player, String name) {
        return textPlain(player, "operation." + name.toLowerCase(Locale.ROOT));
    }

    private static String number(double number) {
        return java.math.BigDecimal.valueOf(number).stripTrailingZeros().toPlainString();
    }

    private static String label(String identifier) {
        String[] words = identifier.toLowerCase(Locale.ROOT).replace('.', '_').split("_");
        List<String> labels = new ArrayList<>();
        for (String word : words) {
            if (!word.isEmpty()) {
                labels.add(Character.toUpperCase(word.charAt(0)) + word.substring(1));
            }
        }
        return String.join(" ", labels);
    }

    void back(UIWindow window, String companionId) {
        UIElement back = button(window.getViewer(), "back", Material.ARROW, "back-hint");
        action(window, back, () -> openDashboard(window.getViewer(), companionId));
        put(window, 46, back);
    }

    UIWindow window(Player player, String title) {
        UIWindow window = new UIWindow(plugin, player);
        window.setViewportHeight(6);
        window.setTitle(text(player, "title." + title));
        window.setTag("bytecompanions:dashboard");
        for (int slot = 0; slot < 54; slot++) {
            if (slot < 9 || slot >= 45 || slot % 9 == 0 || slot % 9 == 8) {
                Material material = slot < 9 ? Material.ORANGE_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE;
                put(window, slot, new UIElement("border-" + slot).setBaseItemStack(new ItemStack(material)).setName(" "));
            }
        }
        UIElement close = button(player, "close", Material.BARRIER, "close-hint");
        close.onLeftClick(ignored -> close(window));
        close.onRightClick(ignored -> close(window));
        put(window, 49, close);
        window.onClosed(ignored -> {
            if (windows.remove(player.getUniqueId(), window)) {
                collections.navigated(player);
                collectionPages.remove(player.getUniqueId());
            }
        });
        return window;
    }

    void show(UIWindow window) {
        if (!active()) {
            return;
        }
        collections.navigated(window.getViewer());
        windows.put(window.getViewer().getUniqueId(), window);
        window.open();
    }

    void action(UIWindow window, UIElement element, Runnable action) {
        action(window, element, action, FeaturePermission.Command.MENU);
    }

    void action(UIWindow window, UIElement element, Runnable action, String permission) {
        Runnable guarded = () -> {
            if (active() && windows.get(window.getViewer().getUniqueId()) == window && window.isVisible()
                    && accessible(window.getViewer(), permission)) {
                action.run();
            }
        };
        element.onLeftClick(ignored -> guarded.run());
        element.onRightClick(ignored -> guarded.run());
    }

    void navigation(UIWindow window, int page, int count, IntConsumer navigate) {
        Player player = window.getViewer();
        if (page > 0) {
            UIElement previous = button(player, "previous", Material.ARROW, "page", "page", Integer.toString(page), "pages", Integer.toString(pages(count)));
            action(window, previous, () -> navigate.accept(page - 1));
            put(window, 45, previous);
        }
        if ((page + 1) * PAGE_SIZE < count) {
            UIElement next = button(player, "next", Material.ARROW, "page", "page", Integer.toString(page + 2), "pages", Integer.toString(pages(count)));
            action(window, next, () -> navigate.accept(page + 1));
            put(window, 53, next);
        }
    }

    static int contentSlot(int index) {
        return 10 + index / 7 * 9 + index % 7;
    }

    static int pages(int count) {
        return Math.max(1, (count + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    static int page(int requested, int count) {
        return Math.max(0, Math.min(requested, pages(count) - 1));
    }

    static void put(UIWindow window, int slot, UIElement element) {
        window.setElement(slot % 9 - 4, slot / 9, element);
    }

    UIElement button(Player player, String id, Material material, String hint, String... replacements) {
        UIElement element = new UIElement(id).setBaseItemStack(new ItemStack(material)).setName(text(player, id));
        element.addLore(text(player, hint, replacements));
        return element;
    }

    UIElement icon(String id, ItemStack source) {
        ItemStack item = source == null || source.getType().isAir() ? new ItemStack(Material.PLAYER_HEAD) : source.clone();
        ItemMeta meta = item.getItemMeta();
        List<String> description = meta.hasLore() ? meta.getLore() : List.of();
        meta.setLore(List.of());
        item.setItemMeta(meta);
        UIElement element = new UIElement(id).setBaseItemStack(item);
        for (String line : description) {
            element.addLore(line);
        }
        if (!description.isEmpty() && !description.getLast().isBlank()) {
            element.addLore("");
        }
        return element;
    }

    ItemStack companionIcon(Player player, CompanionType type) {
        return type instanceof DefaultCompanionType configured && configured.displayItemBlueprint() != null
                ? configured.displayItemBlueprint().build(context(player, type.id())) : type.displayItem().provide();
    }

    private BlueprintContext context(Player player, String companionId) {
        return BlueprintContext.builder().placeholder("player", player.getName()).placeholder("companion_id", companionId).build();
    }

    private boolean equipped(Player player, String companionId) {
        CompanionPlayer state = plugin.getCompanionPlayerOrNull(player);
        return state != null && state.hasCompanion(companionId);
    }

    boolean accessible(Player player) {
        return accessible(player, FeaturePermission.Command.MENU);
    }

    boolean accessible(Player player, String permission) {
        if (!active() || !player.isOnline() || !permission(player, permission)) {
            return false;
        }
        if (!permission.equals(FeaturePermission.Command.SETTINGS) && plugin.getCompanionPlayerOrNull(player) == null) {
            plugin.languageService().send(player, "gui.loading");
            return false;
        }
        return true;
    }

    private void settingsButton(UIWindow window) {
        Player player = window.getViewer();
        if (!player.hasPermission(FeaturePermission.Command.SETTINGS)) return;
        UIElement settings = button(player, "settings", Material.COMPARATOR, "settings-hint");
        action(window, settings, () -> settings(player, "", ""), FeaturePermission.Command.SETTINGS);
        put(window, 47, settings);
    }

    private boolean permission(Player player, String permission) {
        if (player.hasPermission(permission)) {
            return true;
        }
        plugin.languageService().send(player, "gui.locked");
        return false;
    }

    private boolean active() {
        return !closed && runtimeActive.getAsBoolean();
    }

    boolean current(UIWindow window) {
        return active() && windows.get(window.getViewer().getUniqueId()) == window && window.isVisible();
    }

    CompanionBonusService.Snapshot bonusSnapshot(Player player, String companionId) {
        return bonuses.snapshot(player, plugin.getCompanionPlayerOrNull(player), companionId);
    }

    String text(Player player, String key, String... replacements) {
        MessageArgs.Builder args = MessageArgs.builder();
        for (int index = 0; index < replacements.length; index += 2) {
            args.untrusted(replacements[index], replacements[index + 1]);
        }
        return plugin.languageService().render(player, CompanionMessages.key("gui." + key), args.build()).legacy();
    }

    private String textPlain(Player player, String key) {
        return plugin.languageService().render(player, CompanionMessages.key("gui." + key)).plain();
    }

    public void close(Player player) {
        collections.navigated(player);
        collectionPages.remove(player.getUniqueId());
        UIWindow window = windows.remove(player.getUniqueId());
        if (window != null) {
            window.close();
        }
    }

    private void close(UIWindow window) {
        if (windows.remove(window.getViewer().getUniqueId(), window)) {
            collections.navigated(window.getViewer());
            collectionPages.remove(window.getViewer().getUniqueId());
            window.close();
        }
    }

    public CompletableFuture<Void> closeAsync() {
        closed = true;
        collections.close();
        List<CompletableFuture<Void>> tasks = new ArrayList<>();
        for (UIWindow window : windows.values()) {
            tasks.add(BukkitOwnerTasks.entity(plugin, window.getViewer(), window::close)
                    .whenComplete((ignored, failure) -> window.close()));
        }
        windows.clear();
        collectionPages.clear();
        return CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new));
    }
}
