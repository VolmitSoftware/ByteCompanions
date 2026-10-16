package com.volmit.bytecompanions.platform.paper.ui.dashboard;

import com.volmit.bytecompanions.platform.paper.util.inventorygui.BukkitTextInput;
import art.arcane.volmlib.util.inventorygui.UIElement;
import art.arcane.volmlib.util.inventorygui.UIWindow;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import com.volmit.bytecompanions.api.companion.CompanionType;
import com.volmit.bytecompanions.platform.paper.FeaturePermission;
import com.volmit.bytecompanions.platform.paper.PaperPlugin;
import com.volmit.bytecompanions.platform.paper.studio.CompanionDraft;
import com.volmit.bytecompanions.platform.paper.studio.CompanionPreviewService;
import com.volmit.bytecompanions.platform.paper.studio.CompanionStudioFields;
import com.volmit.bytecompanions.platform.paper.studio.CompanionStudioFields.Category;
import com.volmit.bytecompanions.platform.paper.studio.CompanionStudioFields.Field;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.Duration;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.logging.Level;

public final class CompanionStudio implements Listener {
    private static final long DRAFT_TIMEOUT = Duration.ofMinutes(15).toNanos();
    private static final int[] REVIEW_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
    private final PaperPlugin plugin;
    private final CompanionMenus menus;
    private final CompanionPreviewService previews;
    private final BukkitTextInput input;
    private final BooleanSupplier runtime;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private volatile boolean closed;

    public CompanionStudio(PaperPlugin plugin, CompanionMenus menus, CompanionPreviewService previews) {
        this.plugin = plugin;
        this.menus = menus;
        this.previews = previews;
        this.input = BukkitTextInput.register(plugin);
        this.runtime = plugin.captureRuntimeGuard();
    }

    public void open(Player player, String id) {
        if (!allowed(player)) {
            return;
        }
        Session current = sessions.get(player.getUniqueId());
        if (current != null) {
            input.cancel(player);
            if (!id.isBlank() && !current.draft.id().equals(id)) {
                plugin.languageService().send(player, "studio.existing", "companion", current.draft.id());
            }
            current.touched = System.nanoTime();
            home(current);
            return;
        }
        if (id.isBlank()) {
            collection(player, 0);
            return;
        }
        try {
            Session session = new Session(player, CompanionDraft.open(plugin.contentSnapshot(), id, plugin.setupSource()));
            sessions.put(player.getUniqueId(), session);
            expireLater(session);
            plugin.languageService().send(player, "studio.retained");
            home(session);
        } catch (Exception failure) {
            failed(player, failure);
        }
    }

    private void collection(Player player, int requestedPage) {
        List<CompanionType> types = plugin.typeRegistry().all().stream().sorted(Comparator.comparing(CompanionType::id)).toList();
        int page = CompanionMenus.page(requestedPage, types.size());
        UIWindow window = menus.window(player, "studio");
        for (int index = page * 28; index < Math.min(types.size(), (page + 1) * 28); index++) {
            CompanionType type = types.get(index);
            UIElement icon = menus.icon("edit-" + type.id(), menus.companionIcon(player, type));
            icon.addLore(menus.text(player, "studio-hint"));
            action(window, icon, () -> open(player, type.id()));
            CompanionMenus.put(window, CompanionMenus.contentSlot(index % 28), icon);
        }
        if (types.isEmpty()) {
            CompanionMenus.put(window, 22, menus.button(player, "empty", Material.BARRIER, "empty-hint"));
        }
        navigate(window, page, types.size(), next -> collection(player, next));
        menus.show(window);
    }

    private void home(Session session) {
        if (!active(session)) {
            return;
        }
        UIWindow window = window(session, "studio");
        Category[] categories = Category.values();
        int[] slots = {19, 20, 21, 23, 29, 30, 32};
        for (int index = 0; index < categories.length; index++) {
            Category category = categories[index];
            UIElement button = menus.button(session.player, key(category), material(category), "studio-category-hint");
            if (featureDisabled(category)) {
                button.addLore(menus.text(session.player, "studio-disabled"));
            }
            sessionAction(window, session, button, () -> fields(session, category, 0));
            CompanionMenus.put(window, slots[index], button);
        }
        button(window, session, 40, "studio-reset", Material.WATER_BUCKET, "studio-reset-hint",
                () -> validate(session, session.draft.reset(), () -> home(session)));
        button(window, session, 38, "studio-duplicate", Material.MAP, "studio-duplicate-hint", () -> duplicate(session));
        menus.show(window);
    }

    private void duplicate(Session session) {
        menus.close(session.player);
        plugin.languageService().send(session.player, "studio.duplicate-prompt");
        boolean requested = input.request(session.player, new BukkitTextInput.Request(64, 1200,
                () -> active(session) && session.player.hasPermission(FeaturePermission.Command.EDIT),
                id -> mutate(session, () -> session.draft.duplicate(id.trim()), () -> home(session)),
                reason -> {
                    if (active(session)) {
                        plugin.languageService().send(session.player, "studio.cancelled");
                        home(session);
                    }
                }));
        if (!requested) {
            plugin.languageService().send(session.player, "studio.cancelled");
            home(session);
        }
    }

    private void fields(Session session, Category category, int requestedPage) {
        if (!active(session)) {
            return;
        }
        if (category == Category.PARTICLES) {
            presets(session, category, requestedPage);
            return;
        }
        try {
            List<Field> fields = CompanionStudioFields.fields(session.draft, category);
            int page = CompanionMenus.page(requestedPage, fields.size());
            UIWindow window = window(session, "studio-fields");
            for (int index = page * 28; index < Math.min(fields.size(), (page + 1) * 28); index++) {
                Field field = fields.get(index);
                UIElement element = fieldIcon(session, field, material(category));
                element.addLore(menus.text(session.player, "studio-edit-hint"));
                sessionAction(window, session, element, () -> field(session, category, page, field));
                CompanionMenus.put(window, CompanionMenus.contentSlot(index % 28), element);
            }
            if (fields.isEmpty()) {
                CompanionMenus.put(window, 22, menus.button(session.player, key(category), material(category), "studio-empty"));
            }
            if (!CompanionStudioFields.choices(category).isEmpty()) {
                button(window, session, 47, "studio-presets", Material.CRAFTING_TABLE, "studio-presets-hint",
                        () -> presets(session, category, 0));
            }
            back(window, session, () -> home(session));
            navigate(window, page, fields.size(), next -> fields(session, category, next));
            menus.show(window);
        } catch (Exception failure) {
            failed(session.player, failure);
        }
    }

    private void field(Session session, Category category, int page, Field field) {
        UIWindow window = window(session, "studio-field");
        CompanionMenus.put(window, 4, fieldIcon(session, field, material(category)));
        if (field.value() instanceof Boolean enabled) {
            button(window, session, 22, "studio-toggle", Material.LEVER, "studio-toggle-hint",
                    () -> edit(session, category, page, field, Boolean.toString(!enabled)));
        } else {
            button(window, session, 22, "studio-type", Material.WRITABLE_BOOK, "studio-type-hint",
                    () -> prompt(session, category, page, field));
            if (field.value() instanceof Number number) {
                double step = number instanceof Float || number instanceof Double ? 0.05 : 1;
                adjustment(window, session, category, page, field, 20, -step);
                adjustment(window, session, category, page, field, 24, step);
            }
            List<String> choices = field.choices();
            for (int index = 0; index < Math.min(choices.size(), 7); index++) {
                String choice = choices.get(index);
                UIElement element = choiceIcon(session, choice, Material.PAPER, "studio-choice-hint");
                sessionAction(window, session, element, () -> edit(session, category, page, field, choice));
                CompanionMenus.put(window, 28 + index, element);
            }
        }
        if ((category == Category.ATTRIBUTES || category == Category.EFFECTS)
                && !field.path().isEmpty() && field.path().getFirst() instanceof Integer index) {
            button(window, session, 40, "studio-remove", Material.RED_DYE, "studio-remove-hint",
                    () -> mutate(session, () -> CompanionStudioFields.removeEntry(session.draft, category, index),
                            () -> fields(session, category, page)));
        }
        back(window, session, () -> fields(session, category, page));
        menus.show(window);
    }

    private void adjustment(UIWindow window, Session session, Category category, int page, Field field, int slot, double step) {
        String key = step > 0 ? "studio-increase" : "studio-decrease";
        UIElement button = menus.button(session.player, key, step > 0 ? Material.LIME_DYE : Material.ORANGE_DYE,
                "studio-adjust-hint", "step", Double.toString(Math.abs(step)));
        sessionAction(window, session, button, () -> {
            Number current = (Number) field.value();
            String value = current instanceof Float || current instanceof Double
                    ? BigDecimal.valueOf(current.doubleValue()).add(BigDecimal.valueOf(step)).toPlainString()
                    : Long.toString(Math.addExact(current.longValue(), (long) step));
            edit(session, category, page, field, value);
        });
        CompanionMenus.put(window, slot, button);
    }

    private void prompt(Session session, Category category, int page, Field field) {
        menus.close(session.player);
        plugin.languageService().send(session.player, "studio.prompt", "field", field.label());
        boolean requested = input.request(session.player, new BukkitTextInput.Request(256, 1200,
                () -> active(session) && session.player.hasPermission(FeaturePermission.Command.EDIT),
                value -> edit(session, category, page, field, value),
                reason -> {
                    if (active(session)) {
                        plugin.languageService().send(session.player, reason == BukkitTextInput.EndReason.TOO_LONG
                                ? "studio.too-long" : "studio.cancelled");
                        fields(session, category, page);
                    }
                }));
        if (!requested) {
            plugin.languageService().send(session.player, "studio.cancelled");
            fields(session, category, page);
        }
    }

    private void edit(Session session, Category category, int page, Field field, String value) {
        mutate(session, () -> CompanionStudioFields.apply(session.draft, field, value),
                () -> fields(session, category, page));
    }

    private void presets(Session session, Category category, int requestedPage) {
        List<String> choices = CompanionStudioFields.choices(category);
        int page = CompanionMenus.page(requestedPage, choices.size());
        UIWindow window = window(session, "studio-presets");
        for (int index = page * 28; index < Math.min(choices.size(), (page + 1) * 28); index++) {
            String choice = choices.get(index);
            UIElement element = choiceIcon(session, choice, material(category),
                    "studio-particle-hint");
            try {
                element.addLore(menus.text(session.player, CompanionStudioFields.selected(session.draft, category, choice)
                        ? "studio-selected" : "studio-unselected"));
            } catch (Exception failure) {
                failed(session.player, failure);
                return;
            }
            sessionAction(window, session, element, () -> mutate(session,
                    () -> CompanionStudioFields.preset(session.draft, category, choice),
                    () -> fields(session, category, 0)));
            CompanionMenus.put(window, CompanionMenus.contentSlot(index % 28), element);
        }
        back(window, session, () -> {
            if (category == Category.PARTICLES) {
                home(session);
            } else {
                fields(session, category, 0);
            }
        });
        navigate(window, page, choices.size(), next -> presets(session, category, next));
        menus.show(window);
    }

    private UIElement choiceIcon(Session session, String choice, Material material, String hint) {
        UIElement element = new UIElement("studio-choice").setBaseItemStack(new ItemStack(material))
                .setName(menus.text(session.player, "studio-choice", "choice", choice.replace('_', ' ').replace('-', ' ')));
        element.addLore(menus.text(session.player, hint));
        return element;
    }

    private UIElement fieldIcon(Session session, Field field, Material material) {
        UIElement element = new UIElement("studio-field").setBaseItemStack(new ItemStack(material))
                .setName(menus.text(session.player, "studio-field", "field", field.label()));
        element.addLore(menus.text(session.player, "studio-value", "value", String.valueOf(field.value())));
        return element;
    }

    private UIWindow window(Session session, String title) {
        UIWindow window = menus.window(session.player, title);
        UIElement heading = new UIElement("studio-heading").setBaseItemStack(new ItemStack(Material.ARMOR_STAND))
                .setName(menus.text(session.player, "studio-heading", "companion", session.draft.id()));
        heading.addLore(menus.text(session.player, session.busy ? "studio-working"
                : session.draft.changed() ? "studio-draft" : "studio-unchanged"));
        CompanionMenus.put(window, 4, heading);
        button(window, session, 48, previews.active(session.player, CompanionPreviewService.Purpose.STUDIO) ? "studio-stop-preview" : "studio-preview",
                Material.SPYGLASS, "studio-preview-hint", () -> preview(session));
        button(window, session, 49, "studio-discard", Material.BARRIER, "studio-discard-hint", () -> discard(session, true));
        button(window, session, 51, "studio-save", Material.LIME_DYE, "studio-save-hint", () -> confirm(session));
        return window;
    }

    private void confirm(Session session) {
        confirm(session, 0);
    }

    private void confirm(Session session, int requestedPage) {
        UIWindow window = window(session, "studio-confirm");
        UIElement apply = menus.button(session.player, "studio-apply", Material.LIME_CONCRETE, "studio-apply-hint");
        apply.addLore(menus.text(session.player, "studio-reload-hint"));
        List<String> changes = session.draft.changes();
        int pages = Math.max(1, (changes.size() + REVIEW_SLOTS.length - 1) / REVIEW_SLOTS.length);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        for (int index = page * REVIEW_SLOTS.length; index < Math.min(changes.size(), (page + 1) * REVIEW_SLOTS.length); index++) {
            UIElement change = new UIElement("studio-review-change").setBaseItemStack(new ItemStack(Material.PAPER))
                    .setName(menus.text(session.player, "studio-review-change", "index", Integer.toString(index + 1)));
            change.addLore(menus.text(session.player, "studio-change", "change", changes.get(index)));
            CompanionMenus.put(window, REVIEW_SLOTS[index % REVIEW_SLOTS.length], change);
        }
        if (page > 0) {
            button(window, session, 45, "previous", Material.ARROW, "studio-back-hint", () -> confirm(session, page - 1));
        }
        if (page + 1 < pages) {
            button(window, session, 53, "next", Material.ARROW, "studio-category-hint", () -> confirm(session, page + 1));
        }
        sessionAction(window, session, apply, () -> save(session));
        CompanionMenus.put(window, 22, apply);
        back(window, session, () -> home(session));
        menus.show(window);
    }

    private void preview(Session session) {
        if (previews.active(session.player, CompanionPreviewService.Purpose.STUDIO)) {
            previews.stop(session.player, CompanionPreviewService.Purpose.STUDIO);
            home(session);
            return;
        }
        validate(session, session.draft, () -> {
            menus.close(session.player);
            plugin.languageService().send(session.player, "studio.preview", "companion", session.draft.id());
        }, true);
    }

    private void mutate(Session session, Mutation mutation, Runnable completed) {
        try {
            validate(session, mutation.apply(), completed);
        } catch (Exception failure) {
            failed(session.player, failure);
            completed.run();
        }
    }

    private void validate(Session session, CompanionDraft candidate, Runnable completed) {
        validate(session, candidate, completed, previews.active(session.player, CompanionPreviewService.Purpose.STUDIO));
    }

    private void validate(Session session, CompanionDraft candidate, Runnable completed, boolean previewRequested) {
        if (!active(session) || session.busy) {
            return;
        }
        session.busy = true;
        UUID previewWorld = session.player.getWorld().getUID();
        long previewEpoch = session.previewEpoch;
        home(session);
        plugin.prepareStudioPreview(candidate).whenComplete((type, failure) -> {
                boolean scheduled = FoliaScheduler.runEntity(plugin, session.player, () -> {
                    if (!active(session)) {
                        return;
                    }
                    session.busy = false;
                    if (!allowed(session.player)) {
                        discard(session, false);
                        return;
                    }
                    if (failure != null) {
                        failed(session.player, failure);
                    } else {
                        session.draft = candidate;
                        if (previewRequested) {
                            if (session.previewEpoch != previewEpoch || !session.player.getWorld().getUID().equals(previewWorld)) {
                                home(session);
                                return;
                            }
                            try {
                                previews.show(session.player, type, CompanionPreviewService.Purpose.STUDIO, CompanionPreviewService.PREVIEW_DURATION);
                            } catch (RuntimeException | LinkageError exception) {
                                plugin.getLogger().log(Level.SEVERE, "Could not show companion preview", exception);
                                failed(session.player, exception);
                                home(session);
                                return;
                            }
                        }
                    }
                    session.touched = System.nanoTime();
                    if (failure == null) {
                        completed.run();
                    } else {
                        home(session);
                    }
                }, 0L, () -> retire(session));
                if (!scheduled) {
                    retire(session);
                }
        });
    }

    private void save(Session session) {
        if (!session.draft.changed()) {
            home(session);
            return;
        }
        session.busy = true;
        input.cancel(session.player);
        menus.close(session.player);
        previews.stop(session.player, CompanionPreviewService.Purpose.STUDIO);
        plugin.languageService().send(session.player, "studio.saving");
        plugin.saveStudioDraft(session.player, session.draft).whenComplete((restored, failure) -> {
                boolean scheduled = FoliaScheduler.runEntity(plugin, session.player, () -> {
                    if (!session.player.isOnline()) {
                        return;
                    }
                    if (failure == null) {
                        plugin.languageService().send(session.player, Boolean.TRUE.equals(restored) ? "studio.saved" : "studio.restore-failed");
                    } else {
                        failed(session.player, failure);
                        if (active(session)) {
                            session.busy = false;
                            home(session);
                        } else if (plugin.companionStudio() != null) {
                            plugin.companionStudio().recover(session.player, session.draft);
                        }
                    }
                }, 0L, () -> retire(session));
                if (!scheduled) {
                    retire(session);
                }
        });
    }

    private void button(UIWindow window, Session session, int slot, String key, Material material, String hint, Runnable action) {
        UIElement element = menus.button(session.player, key, material, hint);
        sessionAction(window, session, element, action);
        CompanionMenus.put(window, slot, element);
    }

    private void back(UIWindow window, Session session, Runnable action) {
        button(window, session, 46, "studio-back", Material.ARROW, "studio-back-hint", action);
    }

    private void navigate(UIWindow window, int page, int count, IntConsumer navigate) {
        if (page > 0) {
            UIElement previous = menus.button(window.getViewer(), "previous", Material.ARROW, "studio-back-hint");
            action(window, previous, () -> navigate.accept(page - 1));
            CompanionMenus.put(window, 45, previous);
        }
        if ((page + 1) * 28 < count) {
            UIElement next = menus.button(window.getViewer(), "next", Material.ARROW, "studio-category-hint");
            action(window, next, () -> navigate.accept(page + 1));
            CompanionMenus.put(window, 53, next);
        }
    }

    private void sessionAction(UIWindow window, Session session, UIElement element, Runnable operation) {
        action(window, element, () -> {
            if (active(session) && !session.busy) {
                session.touched = System.nanoTime();
                operation.run();
            }
        });
    }

    private void action(UIWindow window, UIElement element, Runnable operation) {
        Runnable guarded = () -> {
            if (menus.current(window) && allowed(window.getViewer())) {
                try {
                    operation.run();
                } catch (RuntimeException failure) {
                    plugin.getLogger().log(Level.WARNING, "Companion studio action failed", failure);
                    failed(window.getViewer(), failure);
                }
            }
        };
        element.onLeftClick(ignored -> guarded.run());
        element.onRightClick(ignored -> guarded.run());
    }

    private boolean allowed(Player player) {
        if (closed || !runtime.getAsBoolean() || !player.isOnline()) {
            return false;
        }
        if (!player.hasPermission(FeaturePermission.Command.EDIT)) {
            plugin.languageService().send(player, "errors.no-permission");
            return false;
        }
        return true;
    }

    private boolean active(Session session) {
        return !closed && runtime.getAsBoolean() && sessions.get(session.player.getUniqueId()) == session;
    }

    private void expireLater(Session session) {
        boolean scheduled = FoliaScheduler.runEntity(plugin, session.player, () -> {
            if (!active(session)) {
                return;
            }
            if (!session.busy && System.nanoTime() - session.touched >= DRAFT_TIMEOUT) {
                discard(session, false);
                plugin.languageService().send(session.player, "studio.expired");
            } else {
                expireLater(session);
            }
        }, 1200, () -> retire(session));
        if (!scheduled) {
            retire(session);
        }
    }

    private void discard(Session session, boolean feedback) {
        if (sessions.remove(session.player.getUniqueId(), session)) {
            input.cancel(session.player);
            previews.stop(session.player, CompanionPreviewService.Purpose.STUDIO);
            menus.close(session.player);
            if (feedback) {
                plugin.languageService().send(session.player, "studio.discarded");
            }
        }
    }

    private void retire(Session session) {
        if (sessions.remove(session.player.getUniqueId(), session)) {
            input.cancel(session.player);
            previews.stop(session.player, CompanionPreviewService.Purpose.STUDIO);
        }
    }

    private void recover(Player player, CompanionDraft draft) {
        if (!allowed(player) || sessions.containsKey(player.getUniqueId())) {
            return;
        }
        Session recovered = new Session(player, draft);
        sessions.put(player.getUniqueId(), recovered);
        expireLater(recovered);
        home(recovered);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        sessions.remove(event.getPlayer().getUniqueId());
        input.cancel(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        invalidatePreview(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        invalidatePreview(event.getPlayer());
    }

    private void invalidatePreview(Player player) {
        Session session = sessions.get(player.getUniqueId());
        if (session != null) {
            session.previewEpoch++;
        }
    }

    public CompletableFuture<Void> closeAsync() {
        closed = true;
        input.close();
        sessions.clear();
        return CompletableFuture.completedFuture(null);
    }

    private boolean featureDisabled(Category category) {
        return switch (category) {
            case ATTRIBUTES, EFFECTS, BUFFS, PARTICLES -> !plugin.configuration().features().enabled(category.name().toLowerCase(Locale.ROOT));
            default -> false;
        };
    }

    private static String key(Category category) {
        return "studio-" + category.name().toLowerCase(Locale.ROOT);
    }

    private static Material material(Category category) {
        return switch (category) {
            case APPEARANCE -> Material.NAME_TAG;
            case EQUIPMENT -> Material.LEATHER_CHESTPLATE;
            case MOVEMENT -> Material.FEATHER;
            case PARTICLES -> Material.BLAZE_POWDER;
            case ATTRIBUTES -> Material.IRON_CHESTPLATE;
            case EFFECTS -> Material.POTION;
            case BUFFS -> Material.EXPERIENCE_BOTTLE;
        };
    }

    private void failed(Player player, Throwable failure) {
        Throwable cause = failure;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        plugin.languageService().send(player, "studio.failed", "reason",
                cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage());
    }

    @FunctionalInterface
    private interface Mutation {
        CompanionDraft apply() throws Exception;
    }

    private static final class Session {
        private final Player player;
        private CompanionDraft draft;
        private long touched = System.nanoTime();
        private boolean busy;
        private long previewEpoch;

        private Session(Player player, CompanionDraft draft) {
            this.player = player;
            this.draft = draft;
        }
    }
}
