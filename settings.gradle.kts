fun hasVolmLibSettings(directory: File): Boolean =
    directory.resolve("settings.gradle").isFile || directory.resolve("settings.gradle.kts").isFile

fun resolveLocalVolmLibDirectory(): File? {
    val configuredPath = providers.gradleProperty("localVolmLibDirectory")
        .orElse(providers.environmentVariable("VOLMLIB_DIR")).orNull
    if (!configuredPath.isNullOrBlank()) {
        val directory = file(configuredPath)
        require(hasVolmLibSettings(directory)) { "Invalid local VolmLib directory: $directory" }
        return directory
    }
    var directory: File? = settingsDir
    while (directory != null) {
        val candidate = directory.resolve("VolmLib")
        if (hasVolmLibSettings(candidate)) return candidate
        directory = directory.parentFile
    }
    return null
}

if (providers.gradleProperty("useLocalVolmLib").orElse("true").get().toBoolean()) {
    resolveLocalVolmLibDirectory()?.let { directory ->
        includeBuild(directory) {
            dependencySubstitution {
                substitute(module("com.github.VolmitSoftware:VolmLib")).using(project(":shared"))
                substitute(module("com.github.VolmitSoftware.VolmLib:shared")).using(project(":shared"))
                substitute(module("com.github.VolmitSoftware.VolmLib:volmlib-shared")).using(project(":shared"))
                substitute(module("com.github.VolmitSoftware.VolmLib:packaging")).using(project(":packaging"))
            }
        }
    }
}

rootProject.name = "ByteCompanions"

include(
    "api", "spi", "core", "adapter-common", "adapter-v26_1", "adapter-v26_3", "adapter-spigot26_1", "adapter-spigot26_3",
    "library-item-blueprint", "slimjar-runtime",
    "providers:storage", "providers:storage:sql-common", "providers:storage:h2",
    "providers:storage:mysql", "platform:paper", "particle-adapter",
    "particle-adapter:v26_1"
)

project(":particle-adapter").projectDir = file("features/particles/adapter-nms")
if (file("qa/fixture").isDirectory) {
    include("qa-fixture")
    project(":qa-fixture").projectDir = file("qa/fixture")
}
listOf("v26_1").forEach {
    project(":particle-adapter:$it").projectDir = file("features/particles/adapter-nms/$it")
}
