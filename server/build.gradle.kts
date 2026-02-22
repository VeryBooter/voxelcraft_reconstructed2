plugins {
    application
}

dependencies {
    implementation(project(":core"))
}

application {
    mainClass.set("dev.voxelcraft.server.ServerMain")
}

tasks.register<JavaExec>("runLocal") {
    group = "application"
    description = "Runs server with local defaults (port can be overridden via -Pport=25565)"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set(application.mainClass)

    val port = providers.gradleProperty("port").orElse("25565").get()
    args("--port", port)
}
