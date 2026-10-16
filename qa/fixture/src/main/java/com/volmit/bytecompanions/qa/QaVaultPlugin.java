package com.volmit.bytecompanions.qa;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class QaVaultPlugin extends JavaPlugin {
    private record Account(double balance, int withdrawals, int deposits) { }
    private final Map<UUID, Account> accounts = new ConcurrentHashMap<>();
    private volatile String mode = "success";
    private boolean regionized;

    @Override
    public void onEnable() {
        try {
            if (!Files.readString(Path.of(".server-source")).matches("(?s).*isolated=true(?:\\r?\\n|$).*")) {
                throw new IllegalStateException("QA economy requires an isolated Multiplexor instance");
            }
            try { Class.forName("io.papermc.paper.threadedregions.RegionizedServer"); regionized = true; }
            catch (ClassNotFoundException ignored) { regionized = false; }
            Economy provider = (Economy) Proxy.newProxyInstance(Economy.class.getClassLoader(), new Class<?>[]{Economy.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getName", "toString" -> "ByteCompanions QA Economy";
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        case "isEnabled" -> isEnabled();
                        case "hasBankSupport" -> false;
                        case "fractionalDigits" -> 2;
                        case "format" -> String.format(Locale.ROOT, "%.2f QA", args[0]);
                        case "currencyNamePlural", "currencyNameSingular" -> "QA";
                        case "getBanks" -> List.of();
                        case "hasAccount", "createPlayerAccount" -> true;
                        case "getBalance" -> account(args[0]).balance();
                        case "has" -> account(args[0]).balance() >= ((Number) args[args.length - 1]).doubleValue();
                        case "withdrawPlayer" -> transfer(args, true);
                        case "depositPlayer" -> transfer(args, false);
                        default -> new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Unsupported QA economy operation");
                    });
            getServer().getServicesManager().register(Economy.class, provider, this, ServicePriority.Highest);
        } catch (Exception failure) {
            throw new IllegalStateException("Could not start isolated QA economy", failure);
        }
    }

    @Override
    public void onDisable() { getServer().getServicesManager().unregisterAll(this); accounts.clear(); }

    private UUID id(Object target) {
        if (!(target instanceof OfflinePlayer player)) throw new IllegalArgumentException("QA economy requires an OfflinePlayer overload");
        if (!(player instanceof Player online) || !online.isOnline()
                || regionized && !Bukkit.isOwnedByCurrentRegion(online)) {
            throw new IllegalStateException("QA economy must be called on the connected player's owner");
        }
        return player.getUniqueId();
    }

    private Account account(Object target) { return accounts.getOrDefault(id(target), new Account(0, 0, 0)); }

    private EconomyResponse transfer(Object[] args, boolean withdrawal) {
        UUID player = id(args[0]);
        double amount = ((Number) args[args.length - 1]).doubleValue();
        if (!Double.isFinite(amount) || amount <= 0) throw new IllegalArgumentException("Invalid QA transfer amount");
        Account previous = accounts.getOrDefault(player, new Account(0, 0, 0));
        if (withdrawal && (mode.equals("reject") || previous.balance() < amount)) {
            accounts.put(player, new Account(previous.balance(), previous.withdrawals() + 1, previous.deposits()));
            return new EconomyResponse(0, previous.balance(), EconomyResponse.ResponseType.FAILURE, "BCQA intentional payment rejection");
        }
        double balance = previous.balance() + (withdrawal ? -amount : amount);
        accounts.put(player, new Account(balance, previous.withdrawals() + (withdrawal ? 1 : 0), previous.deposits() + (withdrawal ? 0 : 1)));
        if (withdrawal && mode.equals("unknown")) throw new IllegalStateException("BCQA intentional unknown payment after debit");
        return new EconomyResponse(amount, balance, EconomyResponse.ResponseType.SUCCESS, "");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player) || !player.isOp()) {
            sender.sendMessage("BCQA ECONOMY operator player required");
            return true;
        }
        if (args.length == 2 && args[0].equals("reset")) {
            double amount = Double.parseDouble(args[1]);
            if (!Double.isFinite(amount) || amount < 0 || amount > 1_000_000) throw new IllegalArgumentException("QA balance outside bounds");
            accounts.put(player.getUniqueId(), new Account(amount, 0, 0));
            mode = "success";
        } else if (args.length == 2 && args[0].equals("mode") && List.of("success", "reject", "unknown").contains(args[1])) {
            mode = args[1];
        } else if (args.length != 0 && !(args.length == 1 && args[0].equals("status"))) {
            throw new IllegalArgumentException("Usage: bcqaeconomy reset <balance> | mode success|reject|unknown | status");
        }
        Account state = accounts.getOrDefault(player.getUniqueId(), new Account(0, 0, 0));
        player.sendMessage("BCQA ECONOMY player=" + player.getUniqueId() + " balance=" + state.balance()
                + " withdrawals=" + state.withdrawals() + " deposits=" + state.deposits() + " mode=" + mode);
        return true;
    }
}
