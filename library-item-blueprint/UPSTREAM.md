# ItemBlueprint source port

Source revision for `item-blueprint`, tag `v1.0.0`, commit `23a036d069bf20acb2179c4e679018119dcf4c77`.

All 27 upstream Java files are retained. The MIT license is preserved in `LICENSE` and packaged as `META-INF/licenses/item-blueprint-MIT.txt`.

The source port keeps the configuration fields, registered bases and components, text serializers, placeholder expansion, typed parse responses, and extension registries. Bukkit registry access replaces Paper registry classes. Skull sources use Bukkit profiles. Shared VolmLib utilities preserve Paper component metadata and provide Spigot text conversion.

Runtime support is determined by ByteCompanions' compatibility gate and test evidence, not by this module's compile API.
