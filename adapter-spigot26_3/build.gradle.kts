import org.gradle.api.attributes.java.TargetJvmVersion

repositories {
    mavenLocal()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
}

dependencies {
    api(project(":adapter-spigot26_1"))
    compileOnly("org.spigotmc:spigot:26.3-R0.1-SNAPSHOT")
    compileOnly("net.kyori:adventure-api:4.26.1")
    compileOnly("org.jetbrains:annotations:26.1.0")
}

listOf("compileClasspath", "testCompileClasspath", "testRuntimeClasspath").forEach { name ->
    configurations.named(name) {
        attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 25)
    }
}
