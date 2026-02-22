import org.gradle.api.tasks.testing.Test
import org.gradle.api.JavaVersion
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.api.plugins.JavaPluginExtension

allprojects {
    group = "dev.voxelcraft"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")

    val currentJavaMajor = JavaVersion.current().majorVersion.toInt()
    val selectedJavaMajor = maxOf(17, currentJavaMajor)

    extensions.configure(JavaPluginExtension::class.java) {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(selectedJavaMajor))
        }
    }

    tasks.withType(Test::class.java).configureEach {
        useJUnitPlatform()
    }
}
