repositories {
    maven("https://repo.extendedclip.com/releases/")
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
}

val qaVaultApi by configurations.creating

dependencies {
    compileOnly(rootProject.extra["paperApi"] as String)
    compileOnly("net.kyori:adventure-api:4.26.1")
    compileOnly(project(":api"))
    compileOnly("me.clip:placeholderapi:2.12.2") { isTransitive = false }
    compileOnly("beer.devs:itemsadder-api:4.0.17") { isTransitive = false }
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") { isTransitive = false }
    compileOnly("com.google.code.gson:gson:2.11.0")
    qaVaultApi("com.github.MilkBowl:VaultAPI:1.7") { isTransitive = false }
}

tasks.jar {
    archiveFileName.set("ByteCompanionsQA.jar")
    exclude("**/QaVaultPlugin*")
}

tasks.register<Jar>("boosterEconomyJar") {
    dependsOn(tasks.classes)
    archiveFileName.set("ByteCompanionsQAEconomy.jar")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(sourceSets.main.get().output) { include("**/QaVaultPlugin*") }
    from("src/economy/resources")
    from(provider { qaVaultApi.map { zipTree(it) } })
    exclude("META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA")
}
