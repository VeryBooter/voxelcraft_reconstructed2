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

    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// The JNI library is packaged with the client, so run/installDist need no manual library path.
if (isMac) {
    val nativeArch = if (archName.contains("aarch64") || archName.contains("arm64")) "arm64" else "x86_64"
    val generatedResources = layout.buildDirectory.dir("generated/metal-resources")
    val nativeLibrary = generatedResources.map { it.file("native/macos-$nativeArch/libvoxelcraft_metal.dylib") }
    val compileMetal by tasks.registering(Exec::class) {
        group = "build"
        description = "Builds the Metal shared-memory JNI bridge (requires Xcode Command Line Tools)"
        inputs.file("src/main/native/metal/MetalBridge.m")
        inputs.property("javaHome", System.getProperty("java.home"))
        outputs.file(nativeLibrary)
        doFirst { nativeLibrary.get().asFile.parentFile.mkdirs() }
        commandLine("xcrun", "clang", "-dynamiclib", "-fobjc-arc", "-fblocks", "-std=gnu11", "-O2",
            "-Wall", "-Wextra", "-Werror", "-Wno-unused-parameter", "-mmacosx-version-min=11.0",
            "-I${System.getProperty("java.home")}/include", "-I${System.getProperty("java.home")}/include/darwin",
            "src/main/native/metal/MetalBridge.m", "-framework", "Cocoa", "-framework", "Metal",
            "-framework", "QuartzCore", "-o", nativeLibrary.get().asFile.absolutePath)
    }
    sourceSets.main { resources.srcDir(generatedResources) }
    tasks.processResources { dependsOn(compileMetal) }
}

tasks.test { useJUnitPlatform { excludeTags("metal-native") } }
tasks.register<Test>("metalTest") {
    group = "verification"
    description = "Runs actual offscreen Metal rendering and shared-buffer lifecycle tests on a unified-memory Mac"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("metal-native") }
    onlyIf { isMac }
    systemProperty("java.awt.headless", "true")
    environment("MTL_DEBUG_LAYER", "1")
    systemProperty("vc.metal.test.output", layout.buildDirectory.dir("metal-test").get().asFile.absolutePath)
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
    if (renderMode == "metal") {
        // AWT is used only for offscreen HUD images; GLFW still creates a native window.
        jvmArgs("-Djava.awt.headless=true")
    }
    if (headless) {
        jvmArgs("-Djava.awt.headless=true")
    } else {
        if (isMac && (renderMode == "gpu" || renderMode == "auto" || renderMode == "metal")) {
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
registerClientRunTask("runMetal", "metal")
registerClientRunTask("runMetalLocal", "metal", local = true)
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

// The browser transports frames/input; it does not implement a second game.
sourceSets.main {
    resources.srcDir("../web/dist")
}
tasks.processResources {
    filesMatching(listOf("index.html", "game.js", "style.css")) {
        path = "browser/$path"
    }
}
tasks.register<JavaExec>("runBrowser") {
    group = "application"
    description = "Runs the existing Java game locally with a browser display and input transport"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("dev.voxelcraft.client.browser.BrowserClientMain")
    forwardVoxelcraftSystemProperties()
    jvmArgs("-Djava.awt.headless=true")
    providers.gradleProperty("connect").orNull?.let { systemProperty("vc.browser.connect", it) }
}
