import art.arcane.volmit.packaging.PackagingMode
import art.arcane.volmit.packaging.PluginPackagingExtension
import art.arcane.volmit.packaging.VerifyNativeBoundary
import art.arcane.volmit.packaging.VerifyNativeClassBoundary
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import io.github.slimjar.SlimJarExtension
import io.github.slimjar.resolver.data.Dependency
import io.github.slimjar.resolver.reader.dependency.DependencyReader
import io.github.slimjar.resolver.reader.resolution.PreResolutionDataReader
import io.github.slimjar.task.SlimJarTask
import io.github.slimjar.util.Serialization.writeList
import io.papermc.paperweight.userdev.PaperweightUserExtension
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

buildscript {
    repositories {
        mavenCentral()
        maven("https://jitpack.io")
    }
    dependencies {
        classpath("com.github.VolmitSoftware.VolmLib:packaging:master-SNAPSHOT")
    }
}

plugins {
    id("com.gradleup.shadow") version "9.2.2"
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.21"
    id("de.crazydev22.slimjar") version "2.1.9"
}

apply(plugin = "art.arcane.volmit-packaging")
tasks.named("slimJar").get()

val bukkitMinecraftRange = rootProject.extra["bukkitMinecraftRange"] as String
val pluginVersion = rootProject.version.toString()
val mainClass = "com.volmit.bytecompanions.platform.paper.PaperPlugin"
val apiVersion = "26.1"
val distributionName = "bytecompanions-$pluginVersion-$bukkitMinecraftRange.jar"

tasks.named<SlimJarTask>("slimJar") {
    doLast {
        val metadata = outputDirectory.resolve("slimjar.dat").inputStream().use(DependencyReader.DEFAULT::read)
        val dependencies = mutableSetOf<String>()
        fun collect(dependency: Dependency) {
            dependencies.add(dependency.toString())
            dependency.transitive().forEach(::collect)
        }
        metadata.dependencies().forEach(::collect)
        val resolutionFile = outputDirectory.resolve("slimjar-resolutions.dat")
        if (resolutionFile.isFile) {
            val resolutions = resolutionFile.inputStream().use(PreResolutionDataReader.DEFAULT::read)
            val retained = resolutions.filterKeys { it in dependencies }
            if (retained.size != resolutions.size) {
                DataOutputStream(resolutionFile.outputStream().buffered()).use { output ->
                    writeList(retained.entries, output) { entry, destination ->
                        destination.writeUTF(entry.key)
                        entry.value.write(destination)
                    }
                }
            }
        }
    }
}

repositories {
    maven("https://maven.enginehub.org/repo")
    maven("https://repo.nexomc.com/releases")
    maven("https://repo.extendedclip.com/releases/")
}

val features = listOf("animations", "attributes", "buffs", "decorations", "effects", "particles")
val bundledLocales = listOf(
    "en_US", "de_DE", "es_ES", "fi_FI", "fr_FR", "he_IL", "it_IT", "ja-JP", "ko_KR",
    "lt_LT", "nl_NL", "pl_PL", "pt_PT", "ru_RU", "tr_TR", "vi_VI", "zh_CN", "zh_TW"
)
val languageSourceReference: String = providers.gradleProperty("languageSourceReference").orElse("master").get()
val languageResources = listOf("languages/en_US.toml")
val languageSourceManifest = tasks.register<WriteProperties>("generateLanguageSourceManifest") {
    destinationFile.set(layout.buildDirectory.file("generated/language-source/bytecompanions-language-source.properties"))
    property("revision", languageSourceReference)
    property("locales", bundledLocales.filter { it != "en_US" }.joinToString(","))
}
sourceSets.main {
    features.forEach { java.srcDir(rootProject.file("features/$it/src/main/java")) }
}

configurations.named("testRuntimeOnly") {
    extendsFrom(configurations.getByName("slimApi"))
}

val runtimeLibraryTest = configurations.create("runtimeLibraryTest") {
    isCanBeConsumed = false
    isCanBeResolved = true
    extendsFrom(configurations.getByName("slimApi"))
}

dependencies {
    paperweight.paperDevBundle(rootProject.extra["paperDevBundle26_1"] as String)
    api(project(":core"))
    implementation(project(":adapter-v26_1"))
    implementation(project(":adapter-v26_3"))
    implementation(project(":adapter-spigot26_1"))
    implementation(project(":adapter-spigot26_3"))
    implementation(project(":library-item-blueprint"))
    implementation(project(":providers:storage:h2"))
    implementation(project(":providers:storage:mysql"))
    implementation(project(":particle-adapter"))
    implementation(project(":particle-adapter:v26_1"))
    implementation(providers.gradleProperty("volmLibCoordinate")
        .orElse("com.github.VolmitSoftware:VolmLib:master-SNAPSHOT").get()) {
        isChanging = true
        isTransitive = false
    }
    implementation("de.crazydev22.slimjar.helper:spigot:2.1.9") {
        exclude(group = "de.crazydev22.slimjar", module = "runtime")
    }
    implementation(project(":slimjar-runtime"))
    "slimApi"("org.spongepowered:configurate-core:4.2.0")
    "slimApi"("com.zaxxer:HikariCP:7.0.2")
    "slimApi"("com.h2database:h2:2.4.240")
    "slimApi"("com.mysql:mysql-connector-j:9.5.0")
    "slimApi"("org.mariadb.jdbc:mariadb-java-client:3.5.8")
    listOf("adventure-api", "adventure-text-minimessage", "adventure-text-serializer-legacy",
        "adventure-text-serializer-plain", "adventure-text-serializer-gson", "adventure-text-serializer-json").forEach {
        "slimApi"("net.kyori:$it:4.26.1")
    }
    "slimApi"("com.google.code.gson:gson:2.10.1")
    "slimApi"("it.unimi.dsi:fastutil:8.5.15")
    "slimApi"("com.moandjiezana.toml:toml4j:0.7.2")
    compileOnly("com.nexomc:nexo:1.15.0")
    listOf("com.sk89q.worldguard:worldguard-bukkit:7.0.16", "com.sk89q.worldedit:worldedit-bukkit:7.4.0").forEach {
        compileOnly(it)
        testImplementation(it)
    }
    compileOnly("beer.devs:itemsadder-api:4.0.17") { isTransitive = false }
    compileOnly("me.clip:placeholderapi:2.12.2")
    testImplementation("me.clip:placeholderapi:2.12.2") { isTransitive = false }
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") { isTransitive = false }
    testImplementation("com.github.MilkBowl:VaultAPI:1.7") { isTransitive = false }
    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
    testImplementation("org.mockito:mockito-core:5.14.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

extensions.configure<PaperweightUserExtension> {
    javaLauncher.set(extensions.getByType<JavaToolchainService>().launcherFor {
        languageVersion.set(JavaLanguageVersion.of(25))
    })
}

tasks.processResources {
    dependsOn(languageSourceManifest)
    exclude { element -> element.relativePath.pathString.startsWith("languages/") && element.relativePath.pathString != "languages/en_US.toml" }
    from(languageSourceManifest)
    val pluginProperties = mapOf(
        "name" to rootProject.name,
        "version" to pluginVersion,
        "main" to mainClass,
        "apiVersion" to apiVersion,
        "minecraftRange" to bukkitMinecraftRange
    )
    inputs.properties(pluginProperties)
    filesMatching(listOf("plugin.yml", "bytecompanions-build.properties")) { expand(pluginProperties) }
    features.forEach { feature ->
        from(rootProject.file("features/$feature/src/main/resources")) {
            into("features/$feature")
        }
    }
    duplicatesStrategy = DuplicatesStrategy.FAIL
}
tasks.jar { enabled = false }
tasks.test {
    useJUnitPlatform()
    systemProperty("net.bytebuddy.experimental", "true")
    systemProperty("bytecompanions.projectDir", projectDir.absolutePath)
    exclude("**/packaging/RuntimeJarTest.class")
}

tasks.named("check") {
    dependsOn(":slimjar-runtime:test")
}

val libraryPrefix = "com.volmit.bytecompanions.libs"
val runtimeRelocations = linkedMapOf(
    "org.spongepowered.configurate" to "$libraryPrefix.configurate",
    "io.leangen.geantyref" to "$libraryPrefix.geantyref",
    "com.zaxxer.hikari" to "$libraryPrefix.hikari",
    "org.h2" to "$libraryPrefix.h2",
    "com.mysql" to "$libraryPrefix.mysql",
    "org.mariadb" to "$libraryPrefix.mariadb",
    "com.google.protobuf" to "$libraryPrefix.protobuf",
    "com.sun.jna" to "$libraryPrefix.jna",
    "com.volmit.bytecompanions.internal.itemblueprint" to "$libraryPrefix.itemblueprint",
    "com.google.gson" to "$libraryPrefix.gson",
    "com.moandjiezana.toml" to "$libraryPrefix.toml"
)

val h2ResourcePath = "${runtimeRelocations.getValue("org.h2").replace('.', '/')}/util/data.zip"
val h2Library = provider {
    runtimeLibraryTest.resolvedConfiguration.resolvedArtifacts.single {
        it.moduleVersion.id.group == "com.h2database" && it.name == "h2"
    }.file
}
val h2ResourcesDirectory = layout.buildDirectory.dir("generated/h2-resources")
val relocateH2Resources = tasks.register("relocateH2Resources") {
    inputs.file(h2Library)
    inputs.property("relocation", runtimeRelocations.getValue("org.h2"))
    outputs.dir(h2ResourcesDirectory)
    doLast {
        val resources = sortedMapOf<String, ByteArray>()
        ZipFile(h2Library.get()).use { library ->
            val nested = checkNotNull(library.getEntry("org/h2/util/data.zip")) { "H2 resource archive missing" }
            ZipInputStream(library.getInputStream(nested)).use { input ->
                while (true) {
                    val entry = input.nextEntry ?: break
                    check(!entry.isDirectory && !entry.name.contains('\\')
                            && entry.name.split('/').none { it.isEmpty() || it == "." || it == ".." }) {
                        "Unsafe H2 resource entry: ${entry.name}"
                    }
                    val relocated = if (entry.name.startsWith("org/h2/")) {
                        runtimeRelocations.getValue("org.h2").replace('.', '/') + entry.name.removePrefix("org/h2")
                    } else {
                        entry.name
                    }
                    check(resources.put(relocated, input.readBytes()) == null) { "Duplicate H2 resource: $relocated" }
                    input.closeEntry()
                }
            }
        }
        check(resources.isNotEmpty()) { "H2 resource archive is empty" }
        val target = h2ResourcesDirectory.get().file(h2ResourcePath).asFile
        target.parentFile.mkdirs()
        ZipOutputStream(target.outputStream()).use { output ->
            output.setLevel(9)
            resources.forEach { (name, bytes) ->
                val entry = ZipEntry(name)
                entry.time = 0L
                output.putNextEntry(entry)
                output.write(bytes)
                output.closeEntry()
            }
        }
    }
}
extensions.configure<SlimJarExtension> {
    runtimeRelocations.forEach { (source, destination) -> relocate(source, destination) }
}

tasks.shadowJar {
    archiveFileName.set(distributionName)
    mergeServiceFiles()
    minimize {
        exclude(project(":library-item-blueprint"))
        exclude(project(":slimjar-runtime"))
        exclude(dependency("de.crazydev22.slimjar.helper:.*:.*"))
        exclude(dependency("com.volmit:.*:.*"))
    }
    relocate("io.github.slimjar", "$libraryPrefix.slimjar")
    relocate("art.arcane.volmlib", "$libraryPrefix.volmlib")
    runtimeRelocations.forEach { (source, destination) -> relocate(source, destination) }
    manifest.attributes["paperweight-mappings-namespace"] = "mojang"
}

val runtimeLibraryTestJar = tasks.register<ShadowJar>("runtimeLibraryTestJar") {
    archiveFileName.set("runtime-libraries.jar")
    destinationDirectory.set(layout.buildDirectory.dir("test-libraries"))
    configurations = listOf(runtimeLibraryTest)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    exclude("META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA", "META-INF/*.EC")
    mergeServiceFiles()
    runtimeRelocations.forEach { (source, destination) -> relocate(source, destination) }
}

val packagingTest = tasks.register<Test>("packagingTest") {
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    include("**/packaging/RuntimeJarTest.class")
    dependsOn(tasks.shadowJar, runtimeLibraryTestJar, relocateH2Resources)
    useJUnitPlatform()
    inputs.file(tasks.shadowJar.flatMap { it.archiveFile })
    inputs.file(runtimeLibraryTestJar.flatMap { it.archiveFile })
    inputs.file(h2Library)
    inputs.file(h2ResourcesDirectory.map { it.file(h2ResourcePath) })
    doFirst {
        systemProperty("bytecompanions.runtimeJar", tasks.shadowJar.get().archiveFile.get().asFile.absolutePath)
        systemProperty("bytecompanions.runtimeLibraries", runtimeLibraryTestJar.get().archiveFile.get().asFile.absolutePath)
        systemProperty("bytecompanions.h2Library", h2Library.get().absolutePath)
        systemProperty("bytecompanions.h2Resources", h2ResourcesDirectory.get().file(h2ResourcePath).asFile.absolutePath)
    }
}

extensions.configure<PluginPackagingExtension> {
    nativeImplementationPackages.addAll(
        "com.volmit.bytecompanions.adaptercommon",
        "com.volmit.bytecompanions.adapter_v26_1",
        "com.volmit.bytecompanions.adapter_v26_3",
        "com.volmit.bytecompanions.adapter_spigot26_1",
        "com.volmit.bytecompanions.adapter_spigot26_3",
        "com.volmit.bytecompanions.features.particles.adapternms.v26_1"
    )
    artifacts.create("distribution") {
        taskName = "shadowJar"
        isPacked = true
        isStripDirectories = true
        isStripLocalVariables = true
        isReleaseCompression = providers.gradleProperty("compactRelease").map { it.toBoolean() }
            .getOrElse(!PackagingMode.resolve(providers).development())
        maximumBytes = 2_100_000
        prunePrefixes = listOf("com/volmit/bytecompanions/libs/volmlib/")
        requiredEntries = listOf(
            "plugin.yml", "bytecompanions-build.properties", "bytecompanions-language-source.properties",
            "slimjar.dat", "slimjar-resolutions.dat",
            "com/volmit/bytecompanions/platform/paper/PaperPlugin.class",
            "com/volmit/bytecompanions/platform/paper/configuration/ConfigService.class",
            "com/volmit/bytecompanions/platform/paper/localization/LanguageService.class",
            "com/volmit/bytecompanions/platform/paper/metrics/Metrics.class",
            "META-INF/LICENSE-bStats.txt",
            "com/volmit/bytecompanions/libs/slimjar/app/builder/SpigotApplicationBuilder.class",
            "com/volmit/bytecompanions/libs/volmlib/util/diagnostics/BukkitDebugDump.class",
            "com/volmit/bytecompanions/libs/volmlib/util/bukkit/BukkitComponents.class",
            "com/volmit/bytecompanions/libs/volmlib/util/bukkit/BukkitProfiles.class",
            "com/volmit/bytecompanions/libs/volmlib/integration/ReloadAware.class",
            "com/volmit/bytecompanions/libs/volmlib/integration/ReloadAware\$PreUnloadReason.class",
            "com/volmit/bytecompanions/libs/volmlib/integration/ReloadPreparation.class",
            "com/volmit/bytecompanions/libs/volmlib/integration/IntegrationServiceContract.class"
        ) + languageResources
        forbiddenPrefixes = listOf(
            "org/bukkit/", "io/papermc/", "net/minecraft/", "org/gradle/", "org/bstats/",
            "art/arcane/volmit/packaging/", "art/arcane/volmlib/", "io/github/slimjar/",
            "net/kyori/", "it/unimi/dsi/fastutil/", "com/sk89q/", "org/enginehub/",
            "com/volmit/bytecompanions/internal/itemblueprint/"
        ) + runtimeRelocations.filterKeys { it !in setOf("com.volmit.bytecompanions.internal.itemblueprint") }.flatMap { (source, destination) ->
            listOf(source.replace('.', '/') + "/", destination.replace('.', '/') + "/")
        }
        shrinkRules = listOf(rootProject.file("packaging/shrink.pro"))
        shrinkLibraries.from(provider { project(":adapter-v26_3").configurations.getByName("compileClasspath") })
        shrinkLibraries.from(provider { project(":adapter-spigot26_1").configurations.getByName("compileClasspath") })
        shrinkLibraries.from(provider { project(":adapter-spigot26_3").configurations.getByName("compileClasspath") })
        shrinkDontwarn = listOf(
            "net.minecraft.core.Rotations",
            "net.minecraft.core.particles.DustParticleOptions",
            "net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket",
            "net.minecraft.network.protocol.game.ServerboundInteractPacket",
            "net.minecraft.network.protocol.game.ClientboundMoveEntityPacket\$PosRot",
            "net.minecraft.server.MinecraftServer",
            "net.minecraft.world.item.ItemStack",
            "net.minecraft.core.particles.ColorParticleOption",
            "org.bukkit.craftbukkit.inventory.CraftItemStack"
        )
        setShrinkRelocations(runtimeRelocations)
    }
}

tasks.named<VerifyNativeBoundary>("verifyNativeBoundary") {
    sources.from(provider {
        rootProject.subprojects.map { it.extensions.getByType<JavaPluginExtension>().sourceSets.getByName("main").allJava }
    })
}
tasks.named<VerifyNativeClassBoundary>("verifyNativeClassBoundary") {
    classes.from(provider {
        rootProject.subprojects.map { it.extensions.getByType<JavaPluginExtension>().sourceSets.getByName("main").output.classesDirs }
    })
}

val verifyJava25Artifact = tasks.register("verifyJava25Artifact") {
    dependsOn(tasks.shadowJar)
    inputs.file(tasks.shadowJar.flatMap { it.archiveFile })
    inputs.property("version", pluginVersion)
    inputs.property("range", bukkitMinecraftRange)
    doLast {
        val artifact = tasks.shadowJar.get().archiveFile.get().asFile
        check(artifact.name == distributionName) { "Distribution must be named $distributionName, found ${artifact.name}" }
        ZipFile(artifact).use { jar ->
            val entries = jar.entries().asSequence().map { it.name }.toSet()
            val required = listOf(
                "plugin.yml", "bytecompanions-build.properties", "slimjar.dat", "slimjar-resolutions.dat",
                "features/particles/icons.toml",
                "features/progression/config.toml",
                "com/volmit/bytecompanions/platform/paper/ui/dashboard/CompanionMenus.class",
                "com/volmit/bytecompanions/platform/paper/PaperPlugin.class",
                "com/volmit/bytecompanions/features/animations/AnimationsFeature.class",
                "com/volmit/bytecompanions/features/attributes/AttributesFeature.class",
                "com/volmit/bytecompanions/features/buffs/BuffsFeature.class",
                "com/volmit/bytecompanions/features/decorations/DecorationsFeature.class",
                "com/volmit/bytecompanions/features/effects/EffectsFeature.class",
                "com/volmit/bytecompanions/features/particles/ParticlesFeature.class",
                "com/volmit/bytecompanions/platform/paper/metrics/Metrics.class",
                "META-INF/LICENSE-bStats.txt",
                "META-INF/licenses/item-blueprint-MIT.txt"
            ) + features.filter { it != "animations" }.map { "features/$it/config.toml" } + languageResources
            check(entries.containsAll(required)) { "Merged artifact is missing: ${required - entries}" }
            check(entries.count { it == "plugin.yml" || it.endsWith("/plugin.yml") || it.endsWith("paper-plugin.yml") } == 1) {
                "Distribution must contain exactly one plugin descriptor"
            }
            val descriptor = jar.getInputStream(jar.getEntry("plugin.yml")).use { it.readBytes().toString(Charsets.UTF_8) }
            check(descriptor.lines().contains("api-version: '$apiVersion'")) { "Distribution must declare API $apiVersion" }
            check(descriptor.lines().contains("version: $pluginVersion")) { "Distribution must declare version $pluginVersion" }
            val buildInfo = Properties().also { properties ->
                jar.getInputStream(jar.getEntry("bytecompanions-build.properties")).use { properties.load(it) }
            }
            check(buildInfo.getProperty("minecraftRange") == bukkitMinecraftRange && buildInfo.getProperty("version") == pluginVersion) {
                "Build properties do not match the build: $buildInfo"
            }
            check(entries.none { it.startsWith("org/bukkit/") || it.startsWith("net/minecraft/") || it.startsWith("io/papermc/") }) {
                "Server classes leaked into the distribution"
            }
            check(entries.none { it.startsWith("art/arcane/volmlib/") }) { "Unrelocated VolmLib class in the distribution" }
            check(entries.none { it.startsWith("org/bstats/") }) { "Unrelocated bStats class in the distribution" }
            check(entries.none { it.startsWith("com/google/gson/") || it.startsWith("com/moandjiezana/toml/") || it.startsWith("net/kyori/") }) {
                "Runtime-downloaded library bundled in the distribution"
            }
            check("paper-libraries.json" !in entries) { "Distribution contains multiple dependency bootstrap contracts" }
            check(entries.none { (it.endsWith(".yml") || it.endsWith(".yaml")) && it != "plugin.yml" }) {
                "Distribution contains a retired YAML data resource"
            }
            check(entries.none { it.startsWith("com/volmit/bytecompanions/platform/paper/extension/")
                    || it.startsWith("com/volmit/bytecompanions/api/extension/")
                    || it.startsWith("com/volmit/bytecompanions/api/bukkit/extension/") }) {
                "Distribution contains the retired external extension loader or API"
            }
            check(entries.filter { it.startsWith("languages/") && it.endsWith(".toml") }.toSet() == languageResources.toSet()) {
                "Distribution must contain only the English catalog"
            }
            val languageManifest = Properties().also { properties ->
                jar.getInputStream(jar.getEntry("bytecompanions-language-source.properties")).use { properties.load(it) }
            }
            check(languageManifest.getProperty("revision") == languageSourceReference
                    && languageManifest.getProperty("locales") == bundledLocales.filter { it != "en_US" }.joinToString(",")) {
                "Language source manifest does not match the build source set"
            }
            for (entry in entries.filter { it.endsWith(".class") }) {
                DataInputStream(jar.getInputStream(jar.getEntry(entry))).use { input ->
                    check(input.readInt() == 0xCAFEBABE.toInt()) { "Invalid class header: $entry" }
                    input.readUnsignedShort()
                    val major = input.readUnsignedShort()
                    check(major <= 69) { "Packaged class exceeds Java 25: $entry ($major)" }
                    check(!entry.startsWith("com/volmit/bytecompanions/") || entry.startsWith("com/volmit/bytecompanions/libs/") || major == 69) {
                        "ByteCompanions class must target Java 25: $entry ($major)"
                    }
                }
            }
            val libraryMetadata = jar.getInputStream(jar.getEntry("slimjar.dat")).use {
                DependencyReader.DEFAULT.read(it)
            }
            val allDependencies = mutableListOf<Dependency>()
            fun collect(dependency: Dependency) {
                allDependencies.add(dependency)
                dependency.transitive().forEach(::collect)
            }
            libraryMetadata.dependencies().forEach(::collect)
            val resolutionMetadata = jar.getInputStream(jar.getEntry("slimjar-resolutions.dat")).use {
                PreResolutionDataReader.DEFAULT.read(it)
            }
            val dependencyKeys = allDependencies.map { it.toString() }.toSet()
            check(dependencyKeys.containsAll(resolutionMetadata.keys)) {
                "Dependency resolution metadata contains libraries outside the runtime dependency graph"
            }
            val runtimeLibraries = allDependencies.map { "${it.groupId()}:${it.artifactId()}:${it.version()}" }
            check(runtimeLibraries.contains("org.spongepowered:configurate-core:4.2.0")) {
                "Runtime metadata is missing typed configuration mapping"
            }
            check(runtimeLibraries.none { it.startsWith("org.spongepowered:configurate-yaml:")
                    || it.startsWith("org.yaml:snakeyaml:") }) {
                "Runtime metadata includes a retired YAML parser"
            }
            check(runtimeLibraries.contains("net.kyori:adventure-api:4.26.1")) {
                "Runtime metadata is missing the public Adventure API for Spigot"
            }
            check(runtimeLibraries.contains("com.moandjiezana.toml:toml4j:0.7.2")) {
                "Runtime metadata is missing the typed TOML parser"
            }
            for (dependency in runtimeLibraries) {
                val dependencyVersion = dependency.split(':')[2]
                check(!dependencyVersion.endsWith("-SNAPSHOT", ignoreCase = true)
                        && !dependencyVersion.startsWith("latest.", ignoreCase = true)
                        && dependencyVersion.none { it in "+[]()," }) {
                    "Runtime metadata contains a changing dependency: $dependency"
                }
            }
        }
    }
}
tasks.check { dependsOn(verifyJava25Artifact, packagingTest,
    ":adapter-spigot26_1:compileSpigot26_2", ":adapter-spigot26_1:compileSpigot26_3") }
tasks.build { dependsOn(tasks.shadowJar) }
