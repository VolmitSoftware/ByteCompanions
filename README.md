# ByteCompanions

Animated companions with animations, attributes, buffs, decorations, effects, and particles in one plugin.

## Requirements

- Minecraft 26.1 through 26.3 on Spigot, Paper, Purpur, Leaf, Folia, or Canvas.
- Java 25.
- Network access for dependency downloads on the first start.

## Install

1. Stop the server and back up its worlds and plugin data.
2. Place the ByteCompanions distribution jar in `plugins/`, replacing the previous jar.
3. Start the server, check `/bc status`, and open `/bc config` to review the settings.

See the [wiki](https://docs.volmitsoftware.com/bytecompanions), [configuration reference](https://docs.volmitsoftware.com/bytecompanions/03-configuration), and [commands and permissions](https://docs.volmitsoftware.com/bytecompanions/02-commands-permissions).

## Build

Use Java 25. Install the Spigot server artifacts for 26.1.2, 26.2, and 26.3 into the local Maven repository with BuildTools; the Spigot native adapters compile against them. From the ByteCompanions repository:

```powershell
.\gradlew.bat build buildSwiftSwamp
```

The build resolves local VolmLib automatically and stages the verified distribution as `../BUILDS/ByteCompanions.jar`. Override the staging directory with `-PbuildsDirectory=<path>`.
