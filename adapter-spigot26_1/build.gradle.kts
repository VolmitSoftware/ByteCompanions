import org.gradle.api.attributes.java.TargetJvmVersion

repositories {
    mavenLocal()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
}

dependencies {
    api(project(":adapter-common"))
    compileOnly("org.spigotmc:spigot:26.1.2-R0.1-SNAPSHOT")
    compileOnly("net.kyori:adventure-text-serializer-gson:4.26.1")
    compileOnly("org.jetbrains:annotations:26.1.0")
}

listOf("compileClasspath", "testCompileClasspath", "testRuntimeClasspath").forEach { name ->
    configurations.named(name) {
        attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 25)
    }
}

val spigot26_2CompileClasspath by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_API))
    attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 25)
}

dependencies {
    spigot26_2CompileClasspath(project(":adapter-common"))
    spigot26_2CompileClasspath("org.spigotmc:spigot:26.2-R0.1-SNAPSHOT")
    spigot26_2CompileClasspath("net.kyori:adventure-text-serializer-gson:4.26.1")
    spigot26_2CompileClasspath("org.jetbrains:annotations:26.1.0")
}

val compileSpigot26_2 = tasks.register<JavaCompile>("compileSpigot26_2") {
    group = "verification"
    description = "Compiles the shared Spigot native adapter against the exact 26.2 API."
    source(sourceSets.main.get().allJava)
    classpath = spigot26_2CompileClasspath
    destinationDirectory.set(layout.buildDirectory.dir("classes/compatibility/spigot26_2"))
}

tasks.named("check") {
    dependsOn(compileSpigot26_2)
}

val spigot26_3CompileClasspath by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_API))
    attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 25)
}

dependencies {
    spigot26_3CompileClasspath(project(":adapter-common"))
    spigot26_3CompileClasspath("org.spigotmc:spigot:26.3-R0.1-SNAPSHOT")
    spigot26_3CompileClasspath("net.kyori:adventure-text-serializer-gson:4.26.1")
    spigot26_3CompileClasspath("org.jetbrains:annotations:26.1.0")
}

val compileSpigot26_3 = tasks.register<JavaCompile>("compileSpigot26_3") {
    group = "verification"
    description = "Compiles the inherited Spigot native hooks against the exact 26.3 API."
    source(sourceSets.main.get().allJava)
    classpath = spigot26_3CompileClasspath
    destinationDirectory.set(layout.buildDirectory.dir("classes/compatibility/spigot26_3"))
}

tasks.named("check") {
    dependsOn(compileSpigot26_3)
}
