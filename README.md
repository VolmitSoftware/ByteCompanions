# ByteCompanions

Cosmetic companions that follow players, built from vanilla blocks and heads with no resource pack, with outfits, particles, buffs, decorations, and progression.

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

Use Java 25. The Spigot adapters need the 26.1.2, 26.2, and 26.3 Spigot artifacts in the local Maven repository, installed with BuildTools. From the ByteCompanions repository:

```powershell
.\gradlew.bat build buildSwiftSwamp
```

The build resolves local VolmLib automatically and stages the verified distribution as `../BUILDS/ByteCompanions.jar`. Override the staging directory with `-PbuildsDirectory=<path>`.
