import io.papermc.paperweight.userdev.PaperweightUserExtension

plugins {
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.21"
}

dependencies {
    paperweight.paperDevBundle(rootProject.extra["paperDevBundle26_1"] as String)
}

extensions.configure<PaperweightUserExtension> {
    javaLauncher.set(extensions.getByType<JavaToolchainService>().launcherFor {
        languageVersion.set(JavaLanguageVersion.of(25))
    })
}
