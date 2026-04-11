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
    implementation("org.lwjgl:lwjgl-vulkan")
    implementation("org.lwjgl:lwjgl-shaderc")

    runtimeOnly("org.lwjgl:lwjgl::$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-glfw::$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-shaderc::$lwjglNatives")
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

fun JavaExec.configureMacVulkanRuntime(renderMode: String) {
    if (!isMac) {
        return
    }
    val mode = renderMode.trim().lowercase()
    if (mode == "software") {
        return
    }

    val vulkanLoaderPathCandidates = listOf(
        "/opt/homebrew/lib/libvulkan.1.dylib",
        "/usr/local/lib/libvulkan.1.dylib"
    )
    val loaderPath = vulkanLoaderPathCandidates.firstOrNull { project.file(it).exists() }
    if (loaderPath != null) {
        // Tell LWJGL exactly where libvulkan lives on macOS (Homebrew/MoltenVK setup).
        jvmArgs("-Dorg.lwjgl.vulkan.libname=$loaderPath")
    }

    val moltenVkIcdCandidates = listOf(
        "/opt/homebrew/etc/vulkan/icd.d/MoltenVK_icd.json",
        "/usr/local/etc/vulkan/icd.d/MoltenVK_icd.json"
    )
    val icdPath = moltenVkIcdCandidates.firstOrNull { project.file(it).exists() }
    if (icdPath != null) {
        environment("VK_ICD_FILENAMES", icdPath)
    }

    if (mode == "vulkan" && macVulkanHeadlessAwtEnabled()) {
        // Avoid macOS AppKit/IMK deadlocks between GLFW event pumping and Java2D when running Vulkan.
        jvmArgs("-Djava.awt.headless=true")
        systemProperty("vc.vulkan.allowHeadless", "true")
    }
}

fun macVulkanHeadlessAwtEnabled(): Boolean {
    if (!isMac) {
        return false
    }
    val raw = System.getProperty("vc.vulkan.headlessAwt") ?: System.getProperty("voxelcraft.vulkan.headlessAwt")
    if (raw == null) {
        return true
    }
    return when (raw.trim().lowercase()) {
        "1", "true", "yes", "on" -> true
        "0", "false", "no", "off" -> false
        else -> true
    }
}

fun firstThreadEnabledByDefault(renderMode: String): Boolean {
    return isMac && (renderMode == "gpu" || renderMode == "auto" || renderMode == "vulkan")
}

fun macFirstThreadEnabled(renderMode: String): Boolean {
    if (!firstThreadEnabledByDefault(renderMode)) {
        return false
    }
    val raw = System.getProperty("vc.mac.firstThread") ?: System.getProperty("voxelcraft.mac.firstThread")
    if (raw == null) {
        return true
    }
    return when (raw.trim().lowercase()) {
        "1", "true", "yes", "on" -> true
        "0", "false", "no", "off" -> false
        else -> true
    }
}

fun registerClientRunTask(name: String, renderMode: String, headless: Boolean = false) {
    tasks.register<JavaExec>(name) {
        group = "application"
        description = "Runs client in $renderMode mode"
        classpath = sourceSets.main.get().runtimeClasspath
        mainClass.set(application.mainClass)
        forwardVoxelcraftSystemProperties()
        configureOptionalDiagnosticsJvmArgs()
        configureMacVulkanRuntime(renderMode)
        if (headless) {
            jvmArgs("-Djava.awt.headless=true")
        } else {
            if (macFirstThreadEnabled(renderMode)) {
                jvmArgs("-XstartOnFirstThread")
            }
            if (renderMode == "software") {
                // Hint Java2D to prefer Metal pipeline on platforms that support it.
                jvmArgs("-Dsun.java2d.metal=true")
            }
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
registerClientRunTask("runVulkan", "vulkan")
tasks.register<JavaExec>("runGpu") {
    group = "application"
    description = "Compatibility alias: runs Vulkan client (replacing legacy GPU path)"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set(application.mainClass)
    forwardVoxelcraftSystemProperties()
    configureOptionalDiagnosticsJvmArgs()
    configureMacVulkanRuntime("vulkan")
    if (macFirstThreadEnabled("vulkan")) {
        jvmArgs("-XstartOnFirstThread")
    }
    args("--render", "vulkan")

    val connectAddress = providers.gradleProperty("connect").orNull
    if (!connectAddress.isNullOrBlank()) {
        args("--connect", connectAddress)
    }
}
tasks.register<JavaExec>("runVulkanSoftware") {
    group = "application"
    description = "Runs Vulkan client with software-frame upload path forced"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set(application.mainClass)
    forwardVoxelcraftSystemProperties()
    configureOptionalDiagnosticsJvmArgs()
    configureMacVulkanRuntime("vulkan")
    if (macFirstThreadEnabled("vulkan")) {
        jvmArgs("-XstartOnFirstThread")
    }
    systemProperty("vc.vulkan.projectedWorld", "false")
    args("--render", "vulkan")

    val connectAddress = providers.gradleProperty("connect").orNull
    if (!connectAddress.isNullOrBlank()) {
        args("--connect", connectAddress)
    }
}
tasks.register<JavaExec>("runVulkanProjected") {
    group = "application"
    description = "Runs Vulkan client with projected world path enabled"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set(application.mainClass)
    forwardVoxelcraftSystemProperties()
    configureOptionalDiagnosticsJvmArgs()
    configureMacVulkanRuntime("vulkan")
    if (macFirstThreadEnabled("vulkan")) {
        jvmArgs("-XstartOnFirstThread")
    }
    systemProperty("vc.vulkan.projectedWorld", "true")
    args("--render", "vulkan")

    val connectAddress = providers.gradleProperty("connect").orNull
    if (!connectAddress.isNullOrBlank()) {
        args("--connect", connectAddress)
    }
}
registerClientRunTask("runHeadless", "software", headless = true)
tasks.register<JavaExec>("runAccelerated") {
    group = "application"
    description = "Runs Vulkan client with vsync disabled for performance testing (projected path remains optional)"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set(application.mainClass)
    forwardVoxelcraftSystemProperties()
    configureOptionalDiagnosticsJvmArgs()
    configureMacVulkanRuntime("vulkan")
    if (macFirstThreadEnabled("vulkan")) {
        jvmArgs("-XstartOnFirstThread")
    }
    jvmArgs("-Dvoxelcraft.vsync=0")
    args("--render", "vulkan")

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
    configureOptionalDiagnosticsJvmArgs()
    jvmArgs("-Dsun.java2d.metal=true")
    args("--render", "software", "--connect", "127.0.0.1:25565")
}

tasks.register<JavaExec>("runGpuLocal") {
    group = "application"
    description = "Compatibility alias: runs Vulkan client and connects to local server 127.0.0.1:25565"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set(application.mainClass)
    forwardVoxelcraftSystemProperties()
    configureOptionalDiagnosticsJvmArgs()
    configureMacVulkanRuntime("vulkan")
    if (macFirstThreadEnabled("vulkan")) {
        jvmArgs("-XstartOnFirstThread")
    }
    args("--render", "vulkan", "--connect", "127.0.0.1:25565")
}

tasks.register<JavaExec>("runVulkanLocal") {
    group = "application"
    description = "Runs Vulkan client and connects to local server 127.0.0.1:25565"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set(application.mainClass)
    forwardVoxelcraftSystemProperties()
    configureOptionalDiagnosticsJvmArgs()
    configureMacVulkanRuntime("vulkan")
    if (macFirstThreadEnabled("vulkan")) {
        jvmArgs("-XstartOnFirstThread")
    }
    args("--render", "vulkan", "--connect", "127.0.0.1:25565")
}

tasks.register<JavaExec>("runVulkanSoftwareLocal") {
    group = "application"
    description = "Runs Vulkan software-frame-upload client and connects to local server 127.0.0.1:25565"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set(application.mainClass)
    forwardVoxelcraftSystemProperties()
    configureOptionalDiagnosticsJvmArgs()
    configureMacVulkanRuntime("vulkan")
    if (macFirstThreadEnabled("vulkan")) {
        jvmArgs("-XstartOnFirstThread")
    }
    systemProperty("vc.vulkan.projectedWorld", "false")
    args("--render", "vulkan", "--connect", "127.0.0.1:25565")
}

tasks.register<JavaExec>("runVulkanProjectedLocal") {
    group = "application"
    description = "Runs Vulkan projected-world client and connects to local server 127.0.0.1:25565"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set(application.mainClass)
    forwardVoxelcraftSystemProperties()
    configureOptionalDiagnosticsJvmArgs()
    configureMacVulkanRuntime("vulkan")
    if (isMac) {
        jvmArgs("-XstartOnFirstThread")
    }
    systemProperty("vc.vulkan.projectedWorld", "true")
    args("--render", "vulkan", "--connect", "127.0.0.1:25565")
}

tasks.register<JavaExec>("runAcceleratedLocal") {
    group = "application"
    description = "Runs Vulkan client (vsync off) and connects to local server 127.0.0.1:25565"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set(application.mainClass)
    forwardVoxelcraftSystemProperties()
    configureOptionalDiagnosticsJvmArgs()
    configureMacVulkanRuntime("vulkan")
    if (isMac) {
        jvmArgs("-XstartOnFirstThread")
    }
    jvmArgs("-Dvoxelcraft.vsync=0")
    args("--render", "vulkan", "--connect", "127.0.0.1:25565")
}
