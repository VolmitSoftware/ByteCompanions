import io.papermc.paperweight.userdev.PaperweightUserExtension

plugins {
    base
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.21" apply false
}

apply(from = "gradle/dependency-integrity.gradle")

val bukkitMinecraftRange: String = providers.gradleProperty("bukkitMinecraftRange").orElse("26.1-26.3").get()
extra["bukkitMinecraftRange"] = bukkitMinecraftRange
extra["paperApi"] = "io.papermc.paper:paper-api:26.1.2.build.74-stable"
extra["paperDevBundle26_1"] = "26.1.2.build.74-stable"
extra["paperDevBundle26_3"] = "26.3.build.190-stable"

subprojects {
    apply(plugin = "java-library")
    group = rootProject.group
    version = rootProject.version

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://jitpack.io")
    }

    dependencies {
        if (name !in setOf("adapter-spigot26_1", "adapter-spigot26_3", "qa-fixture", "slimjar-runtime") && !name.startsWith("library-")) {
            "compileOnly"(rootProject.extra["paperApi"] as String)
        }
        if (!name.startsWith("library-") && name != "slimjar-runtime") {
            "compileOnly"(project(":library-item-blueprint"))
        }
    }

    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    }
    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(25)
        options.compilerArgs.add("-parameters")
    }
    plugins.withId("io.papermc.paperweight.userdev") {
        extensions.configure<PaperweightUserExtension> {
            javaLauncher.set(extensions.getByType<JavaToolchainService>().launcherFor {
                languageVersion.set(JavaLanguageVersion.of(25))
            })
        }
    }
}

val buildSwiftSwamp = tasks.register<Copy>("buildSwiftSwamp") {
    group = "build"
    description = "Verifies and stages the packed distribution as ByteCompanions.jar in the workspace BUILDS directory."
    dependsOn(":platform:paper:check", ":platform:paper:packedJar")
    outputs.upToDateWhen { false }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(provider { project(":platform:paper").tasks.named("packedJar").map { task -> task.property("outputJar") } })
    into(providers.gradleProperty("buildsDirectory").map { directory -> file(directory) }.orElse(file("../BUILDS")))
    rename { "ByteCompanions.jar" }
}

tasks.named("build") {
    dependsOn(":platform:paper:build", buildSwiftSwamp)
}
