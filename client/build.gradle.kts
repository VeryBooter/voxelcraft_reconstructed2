plugins {
    application
}

val lwjglVersion = "3.3.4"
val osName = System.getProperty("os.name").lowercase()
val archName = System.getProperty("os.arch").lowercase()
val isMac = osName.contains("mac")

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
        .filter { it.startsWith("voxelcraft.") || it.startsWith("vc.") }
        .sorted()
        .forEach { key -> systemProperty(key, System.getProperty(key)) }
}

fun JavaExec.configureOptionalDiagnosticsJvmArgs() {
    if (System.getProperty("voxelcraft.gcLog")?.trim()?.lowercase() in setOf("1", "true", "yes", "on")) {
        jvmArgs("-Xlog:gc*,safepoint")
    }
}

fun registerClientRunTask(
    name: String,
    renderMode: String,
    headless: Boolean = false,
    local: Boolean = false
) = tasks.register<JavaExec>(name) {
    val connectAddress = if (local) "127.0.0.1:25565" else providers.gradleProperty("connect").orNull

    group = "application"
    description = if (local) {
        "Runs $renderMode client and connects to local server $connectAddress"
    } else {
        "Runs client in $renderMode mode"
    }
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set(application.mainClass)
    forwardVoxelcraftSystemProperties()
    configureOptionalDiagnosticsJvmArgs()
    if (headless) {
        jvmArgs("-Djava.awt.headless=true")
    } else {
        if (isMac && (renderMode == "gpu" || renderMode == "auto")) {
            jvmArgs("-XstartOnFirstThread")
        }
        if (renderMode == "software") {
            // Hint Java2D to prefer GPU-backed pipelines when available.
            jvmArgs("-Dsun.java2d.opengl=true", "-Dsun.java2d.metal=true")
        }
    }
    args("--render", renderMode)
    if (!connectAddress.isNullOrBlank()) {
        args("--connect", connectAddress)
    }
}

registerClientRunTask("runAuto", "auto")
registerClientRunTask("runSoftware", "software")
registerClientRunTask("runGpu", "gpu")
registerClientRunTask("runHeadless", "software", headless = true)
registerClientRunTask("runAccelerated", "gpu").configure {
    description = "Runs GPU client with vsync disabled for performance testing"
    jvmArgs("-Dvoxelcraft.vsync=0")
}

registerClientRunTask("runSoftwareLocal", "software", local = true)
registerClientRunTask("runGpuLocal", "gpu", local = true).configure {
    description = "Runs GPU client and connects to local server 127.0.0.1:25565"
}

registerClientRunTask("runAcceleratedLocal", "gpu", local = true).configure {
    description = "Runs accelerated GPU client and connects to local server 127.0.0.1:25565"
    jvmArgs("-Dvoxelcraft.vsync=0")
}
