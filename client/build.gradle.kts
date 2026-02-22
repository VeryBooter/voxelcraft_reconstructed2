plugins {
    application
}

val lwjglVersion = "3.3.4"
val osName = System.getProperty("os.name").lowercase()
val archName = System.getProperty("os.arch").lowercase()

val lwjglNatives = when {
    osName.contains("mac") && (archName.contains("aarch64") || archName.contains("arm64")) -> "natives-macos-arm64"
    osName.contains("mac") -> "natives-macos"
    osName.contains("win") -> "natives-windows"
    osName.contains("linux") && (archName.contains("aarch64") || archName.contains("arm64")) -> "natives-linux-arm64"
    osName.contains("linux") -> "natives-linux"
    else -> "natives-macos"
}

dependencies {
    implementation(project(":core"))
    implementation(platform("org.lwjgl:lwjgl-bom:$lwjglVersion"))
    implementation("org.lwjgl:lwjgl")
    implementation("org.lwjgl:lwjgl-glfw")
    implementation("org.lwjgl:lwjgl-opengl")

    runtimeOnly("org.lwjgl:lwjgl::$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-glfw::$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-opengl::$lwjglNatives")
}

application {
    mainClass.set("dev.voxelcraft.client.ClientMain")
}

fun JavaExec.forwardVoxelcraftSystemProperties() {
    System.getProperties()
        .stringPropertyNames()
        .asSequence()
        .filter { it.startsWith("voxelcraft.") }
        .sorted()
        .forEach { key -> systemProperty(key, System.getProperty(key)) }
}

fun registerClientRunTask(name: String, renderMode: String, headless: Boolean = false) {
    tasks.register<JavaExec>(name) {
        group = "application"
        description = "Runs client in $renderMode mode"
        classpath = sourceSets.main.get().runtimeClasspath
        mainClass.set(application.mainClass)
        forwardVoxelcraftSystemProperties()
        if (headless) {
            jvmArgs("-Djava.awt.headless=true")
        } else if (renderMode == "software") {
            // Hint Java2D to prefer GPU-backed pipelines when available.
            jvmArgs("-Dsun.java2d.opengl=true", "-Dsun.java2d.metal=true")
        }
        args("--render", renderMode)

        val connectAddress = providers.gradleProperty("connect").orNull
        if (!connectAddress.isNullOrBlank()) {
            args("--connect", connectAddress)
        }
    }
}

registerClientRunTask("runAuto", "auto")
registerClientRunTask("runSoftware", "software")
registerClientRunTask("runGpu", "gpu")
registerClientRunTask("runHeadless", "software", headless = true)
tasks.register<JavaExec>("runAccelerated") {
    group = "application"
    description = "Runs GPU client with vsync disabled for performance testing"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set(application.mainClass)
    forwardVoxelcraftSystemProperties()
    jvmArgs("-Dvoxelcraft.vsync=0")
    args("--render", "gpu")

    val connectAddress = providers.gradleProperty("connect").orNull
    if (!connectAddress.isNullOrBlank()) {
        args("--connect", connectAddress)
    }
}

tasks.register<JavaExec>("runSoftwareLocal") {
    group = "application"
    description = "Runs software client and connects to local server 127.0.0.1:25565"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set(application.mainClass)
    forwardVoxelcraftSystemProperties()
    jvmArgs("-Dsun.java2d.opengl=true", "-Dsun.java2d.metal=true")
    args("--render", "software", "--connect", "127.0.0.1:25565")
}

tasks.register<JavaExec>("runGpuLocal") {
    group = "application"
    description = "Runs GPU client and connects to local server 127.0.0.1:25565"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set(application.mainClass)
    forwardVoxelcraftSystemProperties()
    args("--render", "gpu", "--connect", "127.0.0.1:25565")
}

tasks.register<JavaExec>("runAcceleratedLocal") {
    group = "application"
    description = "Runs accelerated GPU client and connects to local server 127.0.0.1:25565"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set(application.mainClass)
    forwardVoxelcraftSystemProperties()
    jvmArgs("-Dvoxelcraft.vsync=0")
    args("--render", "gpu", "--connect", "127.0.0.1:25565")
}
