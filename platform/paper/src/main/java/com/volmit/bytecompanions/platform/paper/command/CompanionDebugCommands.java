package com.volmit.bytecompanions.platform.paper.command;

import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;
import org.bukkit.command.CommandSender;

@Director(name = "debug", description = "{prefix} diagnostic tools", descriptionKey = "command.description.debug")
public final class CompanionDebugCommands {
    private final CompanionBukkitCommand command;

    public CompanionDebugCommands(CompanionBukkitCommand command) {
        this.command = command;
    }

    @Director(name = "version", sync = true, description = "Show the installed {prefix} version", descriptionKey = "command.description.version")
    public void version(@Param(name = "sender", contextual = true) CommandSender sender) {
        command.version(sender);
    }

    @Director(name = "dump", sync = true, description = "Create a comprehensive {prefix} diagnostic report", descriptionKey = "command.description.debug_dump")
    public void dump(@Param(name = "upload", defaultValue = "true", description = "Upload the report when public uploads are enabled", descriptionKey = "command.parameter.upload") boolean upload,
                     @Param(name = "sender", contextual = true) CommandSender sender) {
        command.dump(sender, upload);
    }
}
