repositories {
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
}

dependencies {
    compileOnly(providers.gradleProperty("volmLibCoordinate")
        .orElse("com.github.VolmitSoftware:VolmLib:master-SNAPSHOT").get()) { isTransitive = false }
    compileOnly("org.spigotmc:spigot-api:26.1.2-R0.1-SNAPSHOT")
    compileOnly("net.kyori:adventure-text-minimessage:4.26.1")
    compileOnly("net.kyori:adventure-text-serializer-legacy:4.26.1")
    compileOnly("org.spongepowered:configurate-core:4.2.0")
    compileOnly("org.jetbrains:annotations:26.1.0")
}
