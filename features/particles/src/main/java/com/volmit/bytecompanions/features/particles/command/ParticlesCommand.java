package com.volmit.bytecompanions.features.particles.command;

import art.arcane.volmlib.util.plugin.ComponentMessenger;
import art.arcane.volmlib.util.plugin.ComponentText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import com.volmit.bytecompanions.platform.paper.localization.CompanionMessages;
import com.volmit.bytecompanions.platform.paper.localization.LanguageService;
import org.bukkit.command.defaults.BukkitCommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.api.access.ByteCompanions;
import com.volmit.bytecompanions.api.companion.entity.CompanionEntity;
import com.volmit.bytecompanions.api.user.CompanionPlayer;
import com.volmit.bytecompanions.features.particles.FeaturePermission;
import com.volmit.bytecompanions.features.particles.ParticlesFeature;
import com.volmit.bytecompanions.features.particles.registry.ParticleEffectEntry;
import com.volmit.bytecompanions.platform.paper.PaperPlugin;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public class ParticlesCommand extends BukkitCommand {

    private final ParticlesFeature feature;
    private final ByteCompanions companions;
    private final LanguageService languages;

    public ParticlesCommand(ParticlesFeature feature, ByteCompanions companions, LanguageService languages) {
        super(
                "particles",
                "Manage companion particle effects",
                "/bytecompanions particles <menu|equip|unequip>",
                Collections.emptyList()
        );
        this.feature = feature;
        this.companions = companions;
        this.languages = languages;
    }

    @Override
    public boolean execute(
            final @NotNull CommandSender commandSender,
            final @NotNull String label,
            final @NotNull String @NotNull [] arguments
    ) {

        if (arguments.length == 0 || arguments[0].equalsIgnoreCase("help")) {
            this.sendHelp(commandSender);
            return true;
        }

        String action = arguments[0].toLowerCase(Locale.ROOT);
        if (!hasCommandPermission(commandSender, action)) {
            languages.send(commandSender, "errors.no-permission");
            return true;
        }

        if (!(commandSender instanceof Player player)) {
            languages.send(commandSender, "command.feedback.player-only");
            return true;
        }

        switch (action) {
            case "equip" -> {
                if (arguments.length < 2) {
                    this.feature.messageSender().send(player, "companions.invalid-type-id");
                    return true;
                } else if (arguments.length < 3) {
                    this.feature.messageSender().send(player, "particle-effects.invalid-id");
                    return true;
                }

                final String companionTypeId = arguments[1].toLowerCase(Locale.ROOT);
                final String effectId = arguments[2].toLowerCase(Locale.ROOT);
                final ParticleEffectEntry particleEffectEntry = this.feature.particleEffectRegistry().get(effectId);
                if (particleEffectEntry == null) {
                    this.feature.messageSender().send(player, "particle-effects.not-found", effectId);
                    return true;
                }

                this.feature.playerEffectManager().equip(player, companionTypeId, particleEffectEntry);
            }

            case "menu" -> {
                if (arguments.length < 2) {
                    this.feature.messageSender().send(player, "companions.invalid-type-id");
                    return true;
                }

                final String companionTypeId = arguments[1].toLowerCase(Locale.ROOT);
                final CompanionPlayer companionPlayer = this.companions.getCompanionPlayerOrNull(player);
                if (companionPlayer == null || !companionPlayer.hasCompanion(companionTypeId)) {
                    this.feature.messageSender().send(player, "companions.not-equipped", companionTypeId);
                    return true;
                }

                ((PaperPlugin) this.companions).companionMenus().openParticles(player, companionTypeId, 0);
            }

            case "unequip" -> {
                if (arguments.length < 2) {
                    this.feature.playerEffectManager().unequip(player);
                    return true;
                }

                final String companionTypeId = arguments[1].toLowerCase(Locale.ROOT);
                this.feature.playerEffectManager().unequip(player, companionTypeId);
            }
            default -> this.sendHelp(commandSender);
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        languages.send(sender, "particles.title");
        for (String action : List.of("menu", "equip", "unequip")) {
            if (!hasCommandPermission(sender, action)) {
                continue;
            }
            String usage = switch (action) {
                case "menu" -> "menu <companion>";
                case "equip" -> "equip <companion> <effect>";
                case "unequip" -> "unequip [companion]";
                default -> throw new IllegalArgumentException("Unknown particle action: " + action);
            };
            ComponentMessenger.send(sender, ComponentText.component(Component.text("/bytecompanions particles " + usage + " - "
                    + languages.render(sender, CompanionMessages.key("particles.help." + action)).plain(), NamedTextColor.GRAY)));
        }
    }

    static boolean hasCommandPermission(CommandSender sender, String action) {
        return switch (action) {
            case "menu" -> sender.hasPermission(FeaturePermission.MENU_COMMAND);
            case "equip" -> sender.hasPermission(FeaturePermission.EQUIP_COMMAND);
            case "unequip" -> sender.hasPermission(FeaturePermission.UNEQUIP_COMMAND);
            case "help" -> true;
            default -> false;
        };
    }

    @Override
    public @NotNull List<String> tabComplete(
            final @NotNull CommandSender sender,
            final @NotNull String alias,
            final @NotNull String @NotNull [] arguments
    ) throws IllegalArgumentException {
        if (arguments.length == 1) {
            return Stream.of("help", "menu", "unequip", "equip")
                    .filter(action -> hasCommandPermission(sender, action))
                    .filter(action -> action.startsWith(arguments[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        if (arguments.length == 0 || !hasCommandPermission(sender, arguments[0].toLowerCase(Locale.ROOT)) || !(sender instanceof Player player)) {
            return List.of();
        }

        return switch (arguments.length) {
            case 2 -> switch (arguments[0].toLowerCase(Locale.ROOT)) {
                case "menu", "equip", "unequip" -> this.companions.getCompanionPlayer(player)
                        .map(companionPlayer -> companionPlayer.companions().values())
                        .map(companions -> companions.stream()
                                .filter(CompanionEntity::isSpawned)
                                .map(companion -> companion.type().id())
                                .filter(id -> id.startsWith(arguments[1].toLowerCase(Locale.ROOT)))
                                .toList())
                        .orElse(Collections.emptyList());

                default -> Collections.emptyList();
            };

            case 3 -> {
                if (arguments[0].equalsIgnoreCase("equip")) {
                    yield this.feature.particleEffectRegistry().entries()
                            .stream()
                            .map(ParticleEffectEntry::id)
                            .filter(id -> player.hasPermission(FeaturePermission.resolveEffect(id)))
                            .filter(id -> id.startsWith(arguments[2].toLowerCase(Locale.ROOT)))
                            .toList();
                }

                yield Collections.emptyList();
            }

            default -> Collections.emptyList();
        };

    }
}
