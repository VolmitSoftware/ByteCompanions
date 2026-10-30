package com.volmit.bytecompanions.platform.paper.command;

import art.arcane.volmlib.util.director.DirectorEngineOptions;
import art.arcane.volmlib.util.director.compat.DirectorEngineFactory;
import art.arcane.volmlib.util.director.context.DirectorContextRegistry;
import art.arcane.volmlib.util.director.help.DirectorMiniMenu;
import art.arcane.volmlib.util.director.runtime.DirectorInvocation;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeEngine;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeNode;
import art.arcane.volmlib.util.director.runtime.DirectorSender;
import art.arcane.volmlib.util.localization.LanguageAudience;
import art.arcane.volmlib.util.localization.MessageArgs;
import art.arcane.volmlib.util.plugin.ComponentMessenger;
import art.arcane.volmlib.util.plugin.ComponentText;
import com.volmit.bytecompanions.api.companion.CompanionType;
import com.volmit.bytecompanions.api.companion.CompanionTypeRegistry;
import com.volmit.bytecompanions.platform.paper.FeaturePermission;
import com.volmit.bytecompanions.platform.paper.PaperPlugin;
import com.volmit.bytecompanions.platform.paper.localization.CompanionMessages;
import com.volmit.bytecompanions.platform.paper.localization.LanguageService;
import com.volmit.bytecompanions.platform.paper.manager.CompanionPlayerManager;
import com.volmit.bytecompanions.platform.paper.messenger.MessageDispatcher;
import com.volmit.bytecompanions.platform.paper.messenger.MessageKey;
import com.volmit.bytecompanions.platform.paper.presentation.ChatMenuStyle;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.command.defaults.BukkitCommand;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;
import java.util.stream.Stream;

public final class CompanionBukkitCommand implements CommandExecutor, TabCompleter {
    public static final String ROOT = "bytecompanions";
    private static final Set<String> FEATURES = Set.of("particles");

    private final PaperPlugin paperPlugin;
    private final BooleanSupplier runtimeActive;
    private final MessageDispatcher messageDispatcher;
    private final CompanionTypeRegistry companionTypeRegistry;
    private final CompanionPlayerManager companionPlayerManager;
    private final DirectorRuntimeEngine director;
    private final ProgressionCommands progression;

    public CompanionBukkitCommand(
            final @NotNull PaperPlugin paperPlugin,
            final @NotNull MessageDispatcher messageDispatcher,
            final @NotNull CompanionPlayerManager companionPlayerManager,
            final @NotNull CompanionTypeRegistry companionTypeRegistry
    ) {
        this.paperPlugin = paperPlugin;
        this.runtimeActive = paperPlugin.captureRuntimeGuard();
        this.messageDispatcher = messageDispatcher;
        this.companionPlayerManager = companionPlayerManager;
        this.companionTypeRegistry = companionTypeRegistry;
        this.progression = new ProgressionCommands(paperPlugin, runtimeActive, companionTypeRegistry);
        DirectorContextRegistry contexts = new DirectorContextRegistry();
        contexts.register(CommandSender.class, (invocation, values) -> ((BukkitSender) invocation.getSender()).sender());
        this.director = DirectorEngineFactory.create(new CompanionCommands(this), DirectorEngineOptions.builder()
                .contexts(contexts).textResolver(paperPlugin.languageService().directorResolver()).build());
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] arguments) {
        return execute(sender, label, arguments);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] arguments) {
        return tabComplete(sender, alias, arguments);
    }

    public boolean execute(CommandSender commandSender, String commandLabel, String[] arguments) {
        return LanguageAudience.call(commandSender instanceof Player player ? player.getUniqueId() : null,
                () -> executeLocalized(commandSender, commandLabel, arguments));
    }

    private boolean executeLocalized(CommandSender sender, String label, String[] arguments) {
        try {
            if (arguments.length == 0 || arguments[0].equalsIgnoreCase("help")
                    || arguments[0].toLowerCase(Locale.ROOT).startsWith("help=")) {
                sendHelp(sender, arguments);
                return true;
            }
            if (arguments[0].equalsIgnoreCase("language")) {
                return paperPlugin.languageSwitcher().command(sender, Arrays.copyOfRange(arguments, 1, arguments.length));
            }
            List<String> normalized = normalize(arguments);
            if (!ProgressionCommands.validArguments(normalized)) {
                paperPlugin.languageService().send(sender, "command.feedback.usage", "usage", "/" + ROOT + " help");
                return true;
            }
            if (!director.execute(new DirectorInvocation(new BukkitSender(sender), label, normalized)).isHandled()) {
                sendHelp(sender, new String[0]);
            }
        } catch (RuntimeException exception) {
            paperPlugin.getLogger().log(Level.SEVERE, "ByteCompanions command failed", exception);
            paperPlugin.languageService().send(sender, "command.feedback.failed");
        }
        return true;
    }

    static List<String> normalize(String[] arguments) {
        ArrayList<String> normalized = new ArrayList<>(List.of(arguments[0]));
        String root = arguments[0].toLowerCase(Locale.ROOT);
        if (root.equals("debug")) {
            for (int index = 1; index < arguments.length; index++) {
                boolean uploadValue = index == 2 && arguments[1].equalsIgnoreCase("dump") && !arguments[index].contains("=");
                normalized.add(uploadValue ? "upload=" + arguments[index] : arguments[index]);
            }
            return normalized;
        }
        String[] names = switch (root) {
            case "menu", "edit", "spawn", "despawn", "upgrades" -> new String[]{"companion"};
            case "particles" -> new String[]{"action", "companion", "effect"};
            case "settings" -> new String[]{"setting", "value"};
            case "interact" -> new String[]{"action", "companion"};
            case "progression" -> new String[]{"action", "player", "companion", "xp"};
            case "booster" -> switch (arguments.length > 1 ? arguments[1].toLowerCase(Locale.ROOT).replace("action=", "") : "") {
                case "start" -> new String[]{"action", "booster"};
                case "cancel" -> new String[]{"action", "activation"};
                default -> new String[]{"action", "player", "booster", "count"};
            };
            case "transactions" -> new String[]{"action", "transaction", "resolution"};
            default -> new String[]{"action"};
        };
        for (int index = 1; index < arguments.length; index++) {
            normalized.add(index <= names.length && !arguments[index].contains("=")
                    ? names[index - 1] + "=" + arguments[index] : arguments[index]);
        }
        return normalized;
    }

    boolean route(CommandSender commandSender, String... arguments) {
        if (!this.runtimeActive.getAsBoolean()) {
            return true;
        }
        if (arguments.length == 0 || arguments[0].equalsIgnoreCase("help")) {
            this.sendHelp(commandSender);
            return true;
        }
        String featureName = arguments[0].toLowerCase(Locale.ROOT);
        if (ProgressionCommands.ROOTS.contains(featureName)) {
            return this.progression.execute(commandSender, arguments);
        }
        BukkitCommand featureCommand = this.paperPlugin.featureCommands().get(featureName);
        if (featureCommand != null) {
            return featureCommand.execute(commandSender, ROOT + " " + featureName,
                    Arrays.copyOfRange(arguments, 1, arguments.length));
        }
        if (FEATURES.contains(featureName)) {
            this.paperPlugin.languageService().send(commandSender, "command.feedback.disabled", "feature", featureName);
            return true;
        }
        if (!(commandSender instanceof Player player)) {
            this.paperPlugin.languageService().send(commandSender, "command.feedback.player-only");
            return true;
        }
        if (!hasCommandPermission(commandSender, featureName)) {
            this.messageDispatcher.send(commandSender, MessageKey.ERROR_NO_PERMISSION);
            return true;
        }
        switch (featureName) {
            case "settings" -> this.paperPlugin.companionMenus().settings(player,
                    arguments.length > 1 ? arguments[1] : "", arguments.length > 2 ? arguments[2] : "");
            case "collection" -> this.paperPlugin.companionMenus().openCollection(player, 0);
            case "outfits" -> this.paperPlugin.companionMenus().openOutfits(player, 0);
            case "interact" -> this.paperPlugin.companionMenus().interact(player,
                    arguments.length > 1 ? arguments[1] : "", arguments.length > 2 ? arguments[2] : "");
            case "edit" -> this.paperPlugin.companionStudio().open(player, arguments.length > 1 ? arguments[1] : "");
            case "setup" -> this.paperPlugin.companionMenus().openSetup(player);
            case "config" -> this.paperPlugin.openConfiguration(player);
            case "menu" -> {
                if (arguments.length > 1) {
                    this.paperPlugin.companionMenus().openDashboard(player, arguments[1]);
                } else {
                    this.paperPlugin.companionMenus().openHome(player);
                }
            }
            case "spawn" -> {
                if (arguments.length < 2) {
                    this.paperPlugin.languageService().send(player, "command.feedback.companion-required");
                    return true;
                }
                this.companionPlayerManager.spawn(player, arguments[1]);
            }
            case "despawn" -> {
                if (arguments.length == 1) {
                    this.companionPlayerManager.despawn(player);
                    return true;
                }
                this.companionPlayerManager.despawn(player, arguments[1]);
            }
            default -> this.sendHelp(commandSender);
        }
        return true;
    }

    void version(CommandSender sender) {
        if (!hasCommandPermission(sender, "version")) {
            this.messageDispatcher.send(sender, MessageKey.ERROR_NO_PERMISSION);
            return;
        }
        this.paperPlugin.languageService().sendVersion(sender, paperPlugin.getDescription().getVersion());
    }

    void dump(CommandSender sender, boolean upload) {
        if (!hasCommandPermission(sender, "debug")) {
            this.messageDispatcher.send(sender, MessageKey.ERROR_NO_PERMISSION);
            return;
        }
        if (!this.runtimeActive.getAsBoolean() || this.paperPlugin.debugDump() == null) {
            this.paperPlugin.languageService().send(sender, "command.feedback.inactive");
            return;
        }
        this.paperPlugin.debugDump().request(sender, upload);
    }

    void status(CommandSender sender) {
        if (!hasCommandPermission(sender, "status")) {
            this.messageDispatcher.send(sender, MessageKey.ERROR_NO_PERMISSION);
            return;
        }
        LanguageService language = this.paperPlugin.languageService();
        List<String> entries = new ArrayList<>(5);
        for (StatusEntry entry : this.paperPlugin.statusEntries()) {
            entries.add(ChatMenuStyle.entry(language.render(sender, CompanionMessages.key(entry.key()), entry.arguments()).miniMessage()));
        }
        String title = language.render(sender, CompanionMessages.key("command.status.header"), MessageArgs.empty()).plain();
        DirectorMiniMenu.ContentMenu menu = new DirectorMiniMenu.ContentMenu(title, "/" + ROOT + " status", "/" + ROOT,
                entries, "", 1, Math.max(1, entries.size()));
        DirectorMiniMenu.deliverContent(sender, menu, ChatMenuStyle.theme(), language.directorResolver());
    }

    private void sendHelp(CommandSender sender) {
        sendHelp(sender, new String[0]);
    }

    private void sendHelp(CommandSender sender, String[] arguments) {
        List<String> visible = visibleCommands(sender, enabledCommands());
        List<DirectorRuntimeNode> entries = director.getRoot().getChildren().stream()
                .filter(node -> !node.getDescriptor().isHidden())
                .filter(node -> visible.contains(node.getDescriptor().getName()))
                .sorted(Comparator.comparing(node -> node.getDescriptor().getName())).toList();
        int requested = 0;
        if (arguments.length > 1 || arguments.length == 1 && arguments[0].contains("=")) {
            try {
                requested = Integer.parseInt(arguments.length > 1 ? arguments[1] : arguments[0].substring(arguments[0].indexOf('=') + 1)) - 1;
            } catch (NumberFormatException ignored) {
                requested = 0;
            }
        }
        DirectorMiniMenu.DirectorHelpPage helpPage = new DirectorMiniMenu.DirectorHelpPage(director.getRoot(), entries,
                requested, DirectorMiniMenu.MAX_ENTRIES_PER_PAGE);
        if (sender instanceof Player) {
            DirectorMiniMenu.deliver(sender, helpPage, ChatMenuStyle.theme(), paperPlugin.languageService().directorResolver());
        } else {
            for (String line : DirectorMiniMenu.render(helpPage, ChatMenuStyle.theme(), paperPlugin.languageService().directorResolver())) {
                ComponentMessenger.sendMarkup(sender, line);
            }
        }
    }

    static List<String> visibleCommands(CommandSender sender, Set<String> enabledFeatures) {
        return Stream.concat(Stream.of("help", "menu", "collection", "outfits", "interact", "edit", "spawn", "despawn", "debug",
                        "config", "setup", "language", "status", "settings"), enabledFeatures.stream())
                .filter(command -> hasCommandPermission(sender, command))
                .sorted()
                .toList();
    }

    static boolean hasCommandPermission(CommandSender sender, String command) {
        return switch (command) {
            case "upgrades", "boosts", "progression", "booster", "transactions" -> ProgressionCommands.hasPermission(sender, command);
            case "menu", "collection" -> sender.hasPermission(FeaturePermission.Command.MENU);
            case "settings" -> sender.hasPermission(FeaturePermission.Command.SETTINGS);
            case "outfits" -> sender.hasPermission(FeaturePermission.Command.MENU) && sender.hasPermission(FeaturePermission.Command.OUTFITS);
            case "interact" -> sender.hasPermission(FeaturePermission.Command.MENU) && sender.hasPermission(FeaturePermission.Command.INTERACT);
            case "setup" -> sender.hasPermission(FeaturePermission.Command.SETUP);
            case "edit" -> sender.hasPermission(FeaturePermission.Command.EDIT);
            case "spawn" -> sender.hasPermission(FeaturePermission.Command.SPAWN);
            case "despawn" -> sender.hasPermission(FeaturePermission.Command.DESPAWN);
            case "debug" -> sender.hasPermission(FeaturePermission.Command.DEBUG);
            case "config" -> sender.hasPermission(FeaturePermission.Command.CONFIG);
            case "version", "status", "help" -> sender.hasPermission(FeaturePermission.Command.VIEW);
            case "language" -> sender.hasPermission(FeaturePermission.Command.CONFIG)
                    || sender.hasPermission("volmit.language.admin")
                    || sender.hasPermission("volmit.language.self") && sender.hasPermission(FeaturePermission.Command.LANGUAGE_SELF);
            case "particles" -> Stream.of("menu", "equip", "unequip")
                    .anyMatch(action -> sender.hasPermission("bytecompanions.command.particles." + action));
            default -> false;
        };
    }

    private Set<String> enabledCommands() {
        HashSet<String> commands = new HashSet<>(paperPlugin.featureCommands().keySet());
        commands.addAll(progression.enabledRoots());
        return commands;
    }

    public List<String> tabComplete(CommandSender commandSender, String alias, String[] arguments) {
        if (arguments.length > 1 && arguments[0].equalsIgnoreCase("language")) {
            return paperPlugin.languageSwitcher().complete(commandSender, Arrays.copyOfRange(arguments, 1, arguments.length));
        }
        if (arguments.length > 1) {
            String featureName = arguments[0].toLowerCase(Locale.ROOT);
            if (ProgressionCommands.ROOTS.contains(featureName)) {
                return progression.complete(commandSender, arguments);
            }
            BukkitCommand featureCommand = this.paperPlugin.featureCommands().get(featureName);
            if (featureCommand != null) {
                if (!hasCommandPermission(commandSender, featureName)) {
                    return List.of();
                }
                return featureCommand.tabComplete(commandSender, alias + " " + featureName,
                        Arrays.copyOfRange(arguments, 1, arguments.length));
            }
        }
        if (arguments.length == 1) {
            return visibleCommands(commandSender, enabledCommands()).stream()
                    .filter(command -> command.startsWith(arguments[0].toLowerCase(Locale.ROOT)))
                    .sorted()
                    .toList();
        }
        if (arguments[0].equalsIgnoreCase("debug")) {
            if (arguments.length == 2) {
                return Stream.of("dump", "version").filter(option -> option.startsWith(arguments[1].toLowerCase(Locale.ROOT))).toList();
            }
            if (arguments.length == 3 && arguments[1].equalsIgnoreCase("dump") && hasCommandPermission(commandSender, "debug")) {
                return Stream.of("upload=true", "upload=false").filter(option -> option.startsWith(arguments[2].toLowerCase(Locale.ROOT))).toList();
            }
            return List.of();
        }
        if (!(commandSender instanceof Player player)) {
            return Collections.emptyList();
        }
        if (arguments[0].equalsIgnoreCase("interact") && hasCommandPermission(player, "interact")) {
            if (arguments.length == 2) {
                return Stream.of("pet", "wave", "sit", "stay", "call")
                        .filter(action -> action.startsWith(arguments[1].toLowerCase(Locale.ROOT))).toList();
            }
            if (arguments.length == 3) {
                return paperPlugin.getCompanionPlayer(player).map(state -> state.companions().keySet()).orElse(Set.of())
                        .stream().filter(id -> id.startsWith(arguments[2].toLowerCase(Locale.ROOT))).sorted().toList();
            }
        }
        if (arguments[0].equalsIgnoreCase("settings") && hasCommandPermission(player, "settings")) {
            List<String> choices = arguments.length == 2 ? List.of("visibility", "particles")
                    : arguments.length == 3 ? switch (arguments[1].toLowerCase(Locale.ROOT)) {
                        case "visibility" -> List.of("all", "own", "none");
                        case "particles" -> List.of("full", "reduced", "off");
                        default -> List.of();
                    } : List.of();
            return choices.stream().filter(choice -> choice.startsWith(arguments[arguments.length - 1].toLowerCase(Locale.ROOT))).toList();
        }
        if (arguments.length != 2) {
            return Collections.emptyList();
        }
        String argumentZero = arguments[0].toLowerCase(Locale.ROOT);
        return switch (argumentZero) {
            case "menu", "edit" -> !hasCommandPermission(player, argumentZero) ? List.of() : this.companionTypeRegistry.all()
                    .stream().map(CompanionType::id)
                    .filter(id -> id.startsWith(arguments[1].toLowerCase(Locale.ROOT))).sorted().toList();
            case "spawn" -> !player.hasPermission(FeaturePermission.Command.SPAWN) ? Collections.emptyList() : this.companionTypeRegistry.all()
                    .stream()
                    .map(CompanionType::id)
                    .filter(id -> player.hasPermission(FeaturePermission.Companions.resolve(id)))
                    .filter(id -> id.startsWith(arguments[1]))
                    .toList();
            case "despawn" -> !hasCommandPermission(player, "despawn") ? List.of() : this.paperPlugin.getCompanionPlayer(player.getUniqueId())
                    .map(companionPlayer -> companionPlayer.companions().keySet())
                    .orElse(Collections.emptySet())
                    .stream()
                    .filter(id -> id.startsWith(arguments[1].toLowerCase(Locale.ROOT)))
                    .toList();
            default -> Collections.emptyList();
        };
    }

    public record StatusEntry(String key, MessageArgs arguments) {
    }

    private record BukkitSender(CommandSender sender) implements DirectorSender {
        @Override
        public String getName() {
            return sender.getName();
        }

        @Override
        public boolean isPlayer() {
            return sender instanceof Player;
        }

        @Override
        public void sendMessage(String message) {
            ComponentMessenger.send(sender, ComponentText.markup(message));
        }
    }
}
