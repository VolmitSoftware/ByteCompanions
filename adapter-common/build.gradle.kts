import io.papermc.paperweight.userdev.PaperweightUserExtension
import org.gradle.api.attributes.java.TargetJvmVersion

plugins {
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.21"
}

dependencies {
    paperweight.paperDevBundle(rootProject.extra["paperDevBundle26_1"] as String)
    compileOnly(platform("net.kyori:adventure-bom:4.26.1"))
    api(project(":core"))
}

listOf("compileClasspath", "testCompileClasspath", "testRuntimeClasspath").forEach { name ->
    configurations.named(name) {
        attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 25)
    }
}

extensions.configure<PaperweightUserExtension> {
    javaLauncher.set(extensions.getByType<JavaToolchainService>().launcherFor {
        languageVersion.set(JavaLanguageVersion.of(25))
    })
}
