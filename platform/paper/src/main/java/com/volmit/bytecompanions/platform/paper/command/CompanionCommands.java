package com.volmit.bytecompanions.platform.paper.command;

import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;

@Director(name = "bytecompanions", aliases = {"companions", "bc"}, description = "{prefix} help and administration", descriptionKey = "command.description.root")
public final class CompanionCommands {
    private final CompanionBukkitCommand command;
    private CompanionDebugCommands debug;

    public CompanionCommands(CompanionBukkitCommand command) {
        this.command = command;
        this.debug = new CompanionDebugCommands(command);
    }

    @Director(name = "version", hidden = true, sync = true, description = "Show the installed {prefix} version", descriptionKey = "command.description.version")
    public void version(@Param(name = "sender", contextual = true) CommandSender sender) {
        debug.version(sender);
    }

    @Director(name = "status", sync = true, description = "Show runtime, feature, and compatibility state", descriptionKey = "command.description.status")
    public void status(@Param(name = "sender", contextual = true) CommandSender sender) {
        command.status(sender);
    }

    @Director(name = "config", sync = true, description = "Open the complete in-game configuration editor", descriptionKey = "command.description.config")
    public void config(@Param(name = "sender", contextual = true) CommandSender sender) {
        command.route(sender, "config");
    }

    @Director(name = "language", sync = true, description = "Select an available {prefix} language", descriptionKey = "command.description.language")
    public void language(@Param(name = "sender", contextual = true) CommandSender sender) {
        command.execute(sender, "bytecompanions", new String[]{"language"});
    }

    @Director(name = "settings", sync = true, description = "Choose companion visibility and particles", descriptionKey = "command.description.settings")
    public void settings(@Param(name = "setting", defaultValue = "", descriptionKey = "command.parameter.setting") String setting,
                         @Param(name = "value", defaultValue = "", descriptionKey = "command.parameter.value") String value,
                         @Param(name = "sender", contextual = true) CommandSender sender) {
        route(sender, "settings", setting, value);
    }

    @Director(name = "collection", sync = true, description = "Browse and filter your collection", descriptionKey = "command.description.collection")
    public void collection(@Param(name = "sender", contextual = true) CommandSender sender) {
        command.route(sender, "collection");
    }

    @Director(name = "outfits", sync = true, description = "Manage saved companion outfits", descriptionKey = "command.description.outfits")
    public void outfits(@Param(name = "sender", contextual = true) CommandSender sender) {
        command.route(sender, "outfits");
    }

    @Director(name = "interact", sync = true, description = "Interact with an equipped companion", descriptionKey = "command.description.interact")
    public void interact(@Param(name = "action", defaultValue = "", descriptionKey = "command.parameter.action") String action,
                         @Param(name = "companion", defaultValue = "", descriptionKey = "command.parameter.companion") String companion,
                         @Param(name = "sender", contextual = true) CommandSender sender) {
        route(sender, "interact", action, companion);
    }

    @Director(name = "edit", sync = true, description = "Edit companion definitions", descriptionKey = "command.description.edit")
    public void edit(@Param(name = "companion", defaultValue = "", descriptionKey = "command.parameter.companion") String companion,
                     @Param(name = "sender", contextual = true) CommandSender sender) {
        route(sender, "edit", companion);
    }

    @Director(name = "setup", sync = true, description = "Set up starter companions", descriptionKey = "command.description.setup")
    public void setup(@Param(name = "sender", contextual = true) CommandSender sender) {
        command.route(sender, "setup");
    }

    @Director(name = "menu", sync = true, description = "Browse companions", descriptionKey = "command.description.menu")
    public void menu(@Param(name = "companion", defaultValue = "", descriptionKey = "command.parameter.companion") String companion,
                     @Param(name = "sender", contextual = true) CommandSender sender) {
        route(sender, "menu", companion);
    }

    @Director(name = "spawn", sync = true, description = "Equip a companion", descriptionKey = "command.description.spawn")
    public void spawn(@Param(name = "companion", defaultValue = "", descriptionKey = "command.parameter.companion") String companion,
                      @Param(name = "sender", contextual = true) CommandSender sender) {
        route(sender, "spawn", companion);
    }

    @Director(name = "despawn", sync = true, description = "Remove companions", descriptionKey = "command.description.despawn")
    public void despawn(@Param(name = "companion", defaultValue = "", descriptionKey = "command.parameter.companion") String companion,
                        @Param(name = "sender", contextual = true) CommandSender sender) {
        route(sender, "despawn", companion);
    }

    @Director(name = "particles", sync = true, description = "Manage particles", descriptionKey = "command.description.particles")
    public void particles(@Param(name = "action", defaultValue = "help", descriptionKey = "command.parameter.action") String action,
                          @Param(name = "companion", defaultValue = "", descriptionKey = "command.parameter.companion") String companion,
                          @Param(name = "effect", defaultValue = "", descriptionKey = "command.parameter.effect") String effect,
                          @Param(name = "sender", contextual = true) CommandSender sender) {
        route(sender, "particles", action, companion, effect);
    }

    @Director(name = "upgrades", sync = true, description = "Browse companion upgrades", descriptionKey = "command.description.upgrades")
    public void upgrades(@Param(name = "companion", defaultValue = "", descriptionKey = "command.parameter.companion") String companion,
                         @Param(name = "sender", contextual = true) CommandSender sender) {
        command.route(sender, "upgrades", companion);
    }

    @Director(name = "boosts", sync = true, description = "Browse boosters", descriptionKey = "command.description.boosts")
    public void boosts(@Param(name = "sender", contextual = true) CommandSender sender) {
        command.route(sender, "boosts");
    }

    @Director(name = "progression", sync = true, description = "Grant companion XP", descriptionKey = "command.description.progression")
    public void progression(@Param(name = "action", defaultValue = "", descriptionKey = "command.parameter.action") String action,
                            @Param(name = "player", defaultValue = "", descriptionKey = "command.parameter.player") String player,
                            @Param(name = "companion", defaultValue = "", descriptionKey = "command.parameter.companion") String companion,
                            @Param(name = "xp", defaultValue = "", descriptionKey = "command.parameter.xp") String xp,
                            @Param(name = "sender", contextual = true) CommandSender sender) {
        command.route(sender, "progression", action, player, companion, xp);
    }

    @Director(name = "booster", sync = true, description = "Manage booster credits and activations", descriptionKey = "command.description.booster")
    public void booster(@Param(name = "action", defaultValue = "", descriptionKey = "command.parameter.action") String action,
                        @Param(name = "player", defaultValue = "", descriptionKey = "command.parameter.player") String player,
                        @Param(name = "booster", defaultValue = "", descriptionKey = "command.parameter.booster") String booster,
                        @Param(name = "count", defaultValue = "", descriptionKey = "command.parameter.count") String count,
                        @Param(name = "activation", defaultValue = "", descriptionKey = "command.parameter.activation") String activation,
                        @Param(name = "sender", contextual = true) CommandSender sender) {
        command.route(sender, "booster", action, player, booster, count, activation);
    }

    @Director(name = "transactions", sync = true, description = "Review unresolved purchases", descriptionKey = "command.description.transactions")
    public void transactions(@Param(name = "action", defaultValue = "", descriptionKey = "command.parameter.action") String action,
                             @Param(name = "transaction", defaultValue = "", descriptionKey = "command.parameter.transaction") String transaction,
                             @Param(name = "resolution", defaultValue = "", descriptionKey = "command.parameter.resolution") String resolution,
                             @Param(name = "sender", contextual = true) CommandSender sender) {
        command.route(sender, "transactions", action, transaction, resolution);
    }

    private void route(CommandSender sender, String... arguments) {
        ArrayList<String> values = new ArrayList<>();
        for (String argument : arguments) {
            if (!argument.isEmpty()) {
                values.add(argument);
            }
        }
        command.route(sender, values.toArray(String[]::new));
    }
}
