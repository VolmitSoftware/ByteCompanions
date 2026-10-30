package com.volmit.bytecompanions.platform.paper.command;

import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import com.volmit.bytecompanions.api.companion.CompanionTypeRegistry;
import com.volmit.bytecompanions.platform.paper.PaperPlugin;
import com.volmit.bytecompanions.platform.paper.progression.ProgressionDefinitions;
import com.volmit.bytecompanions.platform.paper.progression.ProgressionService;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.stream.Stream;

final class ProgressionCommands {
    static final Set<String> ROOTS = Set.of("upgrades", "boosts", "progression", "booster", "transactions");
    private final PaperPlugin plugin;
    private final BooleanSupplier runtimeActive;
    private final CompanionTypeRegistry companions;

    ProgressionCommands(PaperPlugin plugin, BooleanSupplier runtimeActive, CompanionTypeRegistry companions) {
        this.plugin = plugin;
        this.runtimeActive = runtimeActive;
        this.companions = companions;
    }

    Set<String> enabledRoots() {
        ProgressionService service = plugin.progressionService();
        return enabledRoots(service != null && service.active() && service.progressionEnabled(),
                service != null && service.active() && service.boostersEnabled());
    }

    static Set<String> enabledRoots(boolean progression, boolean boosters) {
        java.util.HashSet<String> roots = new java.util.HashSet<>();
        if (progression) roots.addAll(Set.of("upgrades", "progression"));
        if (boosters) roots.addAll(Set.of("boosts", "booster"));
        if (progression || boosters) roots.add("transactions");
        return Set.copyOf(roots);
    }

    static boolean hasPermission(CommandSender sender, String root) {
        return switch (root) {
            case "upgrades", "boosts" -> sender.hasPermission("bytecompanions.command.menu")
                    && sender.hasPermission("bytecompanions.command." + root);
            case "progression" -> actionAllowed(sender, root, "give");
            case "booster" -> Stream.of("give", "start", "cancel").anyMatch(action -> actionAllowed(sender, root, action));
            case "transactions" -> Stream.of("list", "resolve").anyMatch(action -> actionAllowed(sender, root, action));
            default -> false;
        };
    }

    static boolean actionAllowed(CommandSender sender, String root, String action) {
        return sender.hasPermission("bytecompanions.command." + root + "." + action);
    }

    boolean execute(CommandSender sender, String[] args) {
        String root = args[0].toLowerCase(Locale.ROOT);
        if (!hasPermission(sender, root)) {
            send(sender, "errors.no-permission");
            return true;
        }
        if (!enabledRoots().contains(root)) {
            send(sender, "command.feedback.disabled", "feature", root);
            return true;
        }
        ProgressionService service = plugin.progressionService();
        if (root.equals("upgrades") || root.equals("boosts")) {
            if (!(sender instanceof Player player)) {
                send(sender, "command.feedback.player-only");
            } else if (root.equals("boosts")) {
                plugin.companionMenus().openBoosters(player, 0);
            } else if (args.length < 2 || args[1].isBlank()) {
                usage(sender, "/bytecompanions upgrades <companion>");
            } else {
                plugin.companionMenus().openUpgrades(player, args[1], 0);
            }
            return true;
        }
        String action = value(args, 1).toLowerCase(Locale.ROOT);
        if (!actions(root).contains(action)) {
            usage(sender, switch (root) {
                case "progression" -> "/bytecompanions progression give <player> <companion> <xp>";
                case "booster" -> "/bytecompanions booster give <player> <booster> <count> | start <booster> | cancel <activation>";
                default -> "/bytecompanions transactions list | resolve <transaction> grant|cancel";
            });
            return true;
        }
        if (!actionAllowed(sender, root, action)) {
            send(sender, "errors.no-permission");
            return true;
        }
        switch (root) {
            case "progression" -> {
                if (!action.equals("give") || blank(args, 2, 3, 4)) {
                    usage(sender, "/bytecompanions progression give <player> <companion> <xp>");
                    break;
                }
                Player target = target(sender, args[2]);
                if (target == null) break;
                if (companions.getOrNull(args[3]) == null) {
                    send(sender, "errors.companion-not-found", "companion", args[3]);
                    break;
                }
                if (!service.definitions().companions().containsKey(args[3])) {
                    send(sender, "command.progression.not-configured", "companion", args[3]);
                    break;
                }
                String targetName = target.getName();
                Long amount = amount(sender, args[4], Long.MAX_VALUE);
                if (amount != null) complete(sender, service.grantXp(target.getUniqueId(), args[3], amount),
                        ignored -> send(sender, "command.progression.granted", "player", targetName,
                                "companion", args[3], "amount", amount.toString()));
            }
            case "booster" -> booster(sender, args, service, action);
            case "transactions" -> transactions(sender, args, service, action);
            default -> throw new IllegalArgumentException("Unknown progression command");
        }
        return true;
    }

    private void booster(CommandSender sender, String[] args, ProgressionService service, String action) {
        switch (action) {
            case "give" -> {
                if (blank(args, 2, 3, 4) || !value(args, 5).isEmpty()) {
                    usage(sender, "/bytecompanions booster give <player> <booster> <count>");
                    return;
                }
                Player target = target(sender, args[2]);
                if (target == null || !boosterExists(sender, service, args[3])) return;
                String targetName = target.getName();
                Long count = amount(sender, args[4], Integer.MAX_VALUE);
                if (count != null) complete(sender, service.grantCredits(target.getUniqueId(), args[3], count.intValue()),
                        ignored -> send(sender, "command.booster.granted", "player", targetName,
                                "booster", args[3], "amount", count.toString()));
            }
            case "start" -> {
                String id = value(args, 3);
                if (id.isBlank() || !value(args, 2).isEmpty() || !value(args, 4).isEmpty() || !value(args, 5).isEmpty()) {
                    usage(sender, "/bytecompanions booster start <booster>");
                    return;
                }
                if (!boosterExists(sender, service, id)) return;
                if (service.definitions().boosters().get(id).scope() != ProgressionDefinitions.Scope.SERVER) {
                    send(sender, "command.booster.server-only");
                    return;
                }
                complete(sender, service.startGlobal(id), result -> mutation(sender, result));
            }
            case "cancel" -> {
                String id = value(args, 5);
                if (id.isBlank() || !value(args, 2).isEmpty() || !value(args, 3).isEmpty() || !value(args, 4).isEmpty()) {
                    usage(sender, "/bytecompanions booster cancel <activation>");
                    return;
                }
                complete(sender, service.cancelBooster(id), result -> mutation(sender, result));
            }
            default -> usage(sender, "/bytecompanions booster give <player> <booster> <count> | start <booster> | cancel <activation>" );
        }
    }

    private void transactions(CommandSender sender, String[] args, ProgressionService service, String action) {
        if (action.equals("list") && value(args, 2).isEmpty() && value(args, 3).isEmpty()) {
            complete(sender, service.unresolvedPurchases(), rows -> {
                send(sender, "command.transactions.count", "count", Integer.toString(rows.size()));
                for (ProgressionService.PurchaseJournal row : rows) {
                    send(sender, "command.transactions.entry", "transaction", row.transactionId(),
                            "player", row.playerId().toString(), "kind", row.kind(), "companion", row.companionId(),
                            "definition", row.definitionId(), "tier", Integer.toString(row.expectedTier()), "status", row.status(),
                            "money", Double.toString(row.cost().money()), "items", row.cost().items().toString(),
                            "provider", row.provider(), "created", java.time.Instant.ofEpochMilli(row.createdAt()).toString());
                }
            });
        } else if (action.equals("resolve") && transactionId(value(args, 2))
                && Set.of("grant", "cancel").contains(value(args, 3).toLowerCase(Locale.ROOT))) {
            complete(sender, service.resolvePurchase(args[2], args[3].equalsIgnoreCase("grant")), result -> {
                mutation(sender, result);
                if (result.success()) send(sender, "command.transactions.reconciled");
            });
        } else {
            usage(sender, "/bytecompanions transactions list | resolve <transaction> grant|cancel");
        }
    }

    private static boolean transactionId(String value) {
        try {
            return UUID.fromString(value).toString().equals(value);
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    private void mutation(CommandSender sender, ProgressionService.MutationResult result) {
        send(sender, "progression.result." + result.status().name().toLowerCase(Locale.ROOT));
        if (result.transactionId() != null && !result.transactionId().isBlank()) {
            send(sender, "command.transactions.reference", "transaction", result.transactionId());
        }
    }

    private Player target(CommandSender sender, String name) {
        Player player = Bukkit.getPlayerExact(name);
        if (player == null) send(sender, "command.progression.player-unavailable", "player", name);
        else if (!plugin.progressionService().isLoaded(player.getUniqueId())) {
            send(sender, "command.progression.player-loading", "player", name);
            return null;
        }
        return player;
    }

    private boolean boosterExists(CommandSender sender, ProgressionService service, String id) {
        if (service.definitions().boosters().containsKey(id)) return true;
        send(sender, "command.booster.not-found", "booster", id);
        return false;
    }

    static long positiveAmount(String raw, long maximum) {
        if (!raw.matches("[0-9]{1,19}")) throw new IllegalArgumentException("A positive whole number is required");
        long value = Long.parseLong(raw);
        if (value <= 0 || value > maximum) throw new IllegalArgumentException("Amount is outside the supported range");
        return value;
    }

    static boolean validArguments(List<String> normalized) {
        String root = normalized.getFirst().toLowerCase(Locale.ROOT);
        if (!ROOTS.contains(root)) return true;
        java.util.HashMap<String, String> parameters = new java.util.HashMap<>();
        for (String argument : normalized.subList(1, normalized.size())) {
            int separator = argument.indexOf('=');
            if (separator < 1 || parameters.put(argument.substring(0, separator), argument.substring(separator + 1)) != null) return false;
        }
        String action = parameters.getOrDefault("action", "").toLowerCase(Locale.ROOT);
        Set<String> allowed = switch (root) {
            case "upgrades" -> Set.of("companion");
            case "boosts" -> Set.of();
            case "progression" -> Set.of("action", "player", "companion", "xp");
            case "booster" -> switch (action) {
                case "start" -> Set.of("action", "booster");
                case "cancel" -> Set.of("action", "activation");
                default -> Set.of("action", "player", "booster", "count");
            };
            case "transactions" -> action.equals("list") ? Set.of("action") : Set.of("action", "transaction", "resolution");
            default -> Set.of();
        };
        return allowed.containsAll(parameters.keySet());
    }

    private Long amount(CommandSender sender, String raw, long maximum) {
        try {
            return positiveAmount(raw, maximum);
        } catch (IllegalArgumentException invalid) {
            send(sender, "command.progression.invalid-amount", "maximum", Long.toString(maximum));
            return null;
        }
    }

    private <T> void complete(CommandSender sender, CompletableFuture<T> future, Consumer<T> success) {
        future.whenComplete((result, failure) -> {
            if (failure != null) plugin.getLogger().log(Level.WARNING, "Progression command could not be saved", failure);
            if (!runtimeActive.getAsBoolean()) return;
            Runnable feedback = () -> {
                if (!runtimeActive.getAsBoolean() || sender instanceof Player player && !player.isOnline()) return;
                if (failure == null) success.accept(result);
                else send(sender, "command.feedback.failed");
            };
            if (sender instanceof Player player) FoliaScheduler.runEntity(plugin, player, feedback);
            else FoliaScheduler.runGlobal(plugin, feedback);
        });
    }

    List<String> complete(CommandSender sender, String[] args) {
        String root = args[0].toLowerCase(Locale.ROOT);
        if (!runtimeActive.getAsBoolean() || !enabledRoots().contains(root) || !hasPermission(sender, root)) return List.of();
        ProgressionService service = plugin.progressionService();
        Stream<String> candidates = Stream.empty();
        String action = value(args, 1).toLowerCase(Locale.ROOT);
        if (root.equals("upgrades") && args.length == 2) {
            candidates = companions.all().stream().map(type -> type.id())
                    .filter(id -> sender.hasPermission("bytecompanions.companion." + id));
        } else if (args.length == 2) {
            candidates = switch (root) {
                case "progression" -> Stream.of("give");
                case "booster" -> Stream.of("give", "start", "cancel");
                case "transactions" -> Stream.of("list", "resolve");
                default -> Stream.empty();
            };
            candidates = candidates.filter(option -> actionAllowed(sender, root, option));
        } else if (actionAllowed(sender, root, action)) {
            if (action.equals("give") && args.length == 3 && Set.of("progression", "booster").contains(root)) {
                candidates = Bukkit.getOnlinePlayers().stream().map(Player::getName);
            } else if (root.equals("progression") && action.equals("give") && args.length == 4) {
                candidates = service.definitions().companions().keySet().stream();
            } else if (root.equals("booster") && (action.equals("give") && args.length == 4 || action.equals("start") && args.length == 3)) {
                candidates = service.definitions().boosters().entrySet().stream()
                        .filter(entry -> !action.equals("start") || entry.getValue().scope() == ProgressionDefinitions.Scope.SERVER)
                        .map(java.util.Map.Entry::getKey);
            } else if (root.equals("transactions") && action.equals("resolve") && args.length == 4) {
                candidates = Stream.of("grant", "cancel");
            }
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        return candidates.filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted().toList();
    }

    private static String value(String[] args, int index) { return index < args.length ? args[index] : ""; }
    private static List<String> actions(String root) {
        return switch (root) {
            case "progression" -> List.of("give");
            case "booster" -> List.of("give", "start", "cancel");
            case "transactions" -> List.of("list", "resolve");
            default -> List.of();
        };
    }
    private static boolean blank(String[] args, int... indexes) {
        for (int index : indexes) if (value(args, index).isBlank()) return true;
        return false;
    }
    private void usage(CommandSender sender, String usage) { send(sender, "command.feedback.usage", "usage", usage); }
    private void send(CommandSender sender, String key, String... replacements) { plugin.languageService().send(sender, key, replacements); }
}
