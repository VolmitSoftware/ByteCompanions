package com.volmit.bytecompanions.platform.paper.command;

import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ProgressionCommandAccessTest {
    @Test
    void playerMenusRequireBothMenuAndFeaturePermission() {
        for (String command : Set.of("upgrades", "boosts")) {
            assertFalse(ProgressionCommands.hasPermission(sender(Set.of("bytecompanions.command." + command)), command));
            assertFalse(ProgressionCommands.hasPermission(sender(Set.of("bytecompanions.command.menu")), command));
            assertTrue(ProgressionCommands.hasPermission(sender(Set.of("bytecompanions.command.menu", "bytecompanions.command." + command)), command));
        }
    }

    @Test
    void CreditGrantPermissionDoesNotPermitStartingCancellingOrResolvingPayments() {
        CommandSender grantOnly = sender(Set.of("bytecompanions.command.booster.give"));
        assertTrue(ProgressionCommands.hasPermission(grantOnly, "booster"));
        assertTrue(ProgressionCommands.actionAllowed(grantOnly, "booster", "give"));
        assertFalse(ProgressionCommands.actionAllowed(grantOnly, "booster", "start"));
        assertFalse(ProgressionCommands.actionAllowed(grantOnly, "booster", "cancel"));
        assertFalse(ProgressionCommands.hasPermission(grantOnly, "transactions"));
        CommandSender reviewer = sender(Set.of("bytecompanions.command.transactions.list"));
        assertTrue(ProgressionCommands.hasPermission(reviewer, "transactions"));
        assertFalse(ProgressionCommands.actionAllowed(reviewer, "transactions", "resolve"));
    }

    @Test
    void disabledOptionalFeaturesExposeNoCommandRootsEvenToAdministrators() {
        assertEquals(Set.of(), ProgressionCommands.enabledRoots(false, false));
        assertEquals(Set.of("upgrades", "progression", "transactions"), ProgressionCommands.enabledRoots(true, false));
        assertEquals(Set.of("boosts", "booster", "transactions"), ProgressionCommands.enabledRoots(false, true));
        CommandSender administrator = sender(Set.of("bytecompanions.command.menu", "bytecompanions.command.upgrades",
                "bytecompanions.command.boosts", "bytecompanions.command.progression.give", "bytecompanions.command.booster.give",
                "bytecompanions.command.transactions.resolve"));
        assertEquals(java.util.List.of("collection", "help", "menu", "status"), CompanionBukkitCommand.visibleCommands(administrator,
                ProgressionCommands.enabledRoots(false, false)));
        assertTrue(CompanionBukkitCommand.visibleCommands(administrator, ProgressionCommands.enabledRoots(true, true))
                .containsAll(ProgressionCommands.ROOTS));
    }

    @Test
    void grantsRejectNegativeFractionalOverflowAndOutOfRangeAmountsBeforeMutation() {
        for (String invalid : Set.of("-1", "0", "1.5", "NaN", "Infinity", "1e9", "9223372036854775808", "", " 1")) {
            assertThrows(IllegalArgumentException.class, () -> ProgressionCommands.positiveAmount(invalid, Long.MAX_VALUE), invalid);
        }
        assertEquals(Long.MAX_VALUE, ProgressionCommands.positiveAmount(Long.toString(Long.MAX_VALUE), Long.MAX_VALUE));
        assertEquals(Integer.MAX_VALUE, ProgressionCommands.positiveAmount(Integer.toString(Integer.MAX_VALUE), Integer.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> ProgressionCommands.positiveAmount("2147483648", Integer.MAX_VALUE));
    }

    @Test
    void extraDuplicateAndIrrelevantActionArgumentsAreRejected() {
        for (String[] arguments : java.util.List.of(
                new String[]{"progression", "give", "Alex", "bee", "10", "extra"},
                new String[]{"progression", "give", "Alex", "bee", "10", "xp=20"},
                new String[]{"booster", "start", "qa_server", "player=Alex"},
                new String[]{"booster", "cancel", "activation", "booster=qa_server"},
                new String[]{"transactions", "list", "extra"},
                new String[]{"boosts", "extra"})) {
            assertFalse(ProgressionCommands.validArguments(CompanionBukkitCommand.normalize(arguments)), java.util.Arrays.toString(arguments));
        }
        assertTrue(ProgressionCommands.validArguments(CompanionBukkitCommand.normalize(new String[]{"booster", "start", "qa_server"})));
        assertTrue(ProgressionCommands.validArguments(CompanionBukkitCommand.normalize(new String[]{"booster", "cancel", "activation"})));
        assertTrue(ProgressionCommands.validArguments(CompanionBukkitCommand.normalize(new String[]{"transactions", "resolve", "transaction", "grant"})));
    }

    private static CommandSender sender(Set<String> permissions) {
        return (CommandSender) Proxy.newProxyInstance(CommandSender.class.getClassLoader(), new Class<?>[]{CommandSender.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("hasPermission")) return arguments[0].equals("bytecompanions.command") || permissions.contains(arguments[0]);
                    throw new AssertionError("Unexpected sender access: " + method.getName());
                });
    }
}
