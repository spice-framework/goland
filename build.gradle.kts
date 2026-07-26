import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    java
    id("org.jetbrains.intellij.platform") version "2.18.1"
}

group = "com.github.stevenbuglione.spice"
version = "0.1.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

val localGoLandPath = providers.gradleProperty("golandPath")

dependencies {
    intellijPlatform {
        if (localGoLandPath.isPresent) {
            local(localGoLandPath.get())
        } else {
            goland("2026.2")
        }
        bundledPlugin("org.jetbrains.plugins.go")
        pluginVerifier("1.409")
        testFramework(TestFrameworkType.Platform)
    }
    testImplementation("junit:junit:4.13.2")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

dependencyLocking {
    lockAllConfigurations()
}

intellijPlatform {
    pluginConfiguration {
        name = "Spice"
        version = project.version.toString()
        ideaVersion {
            sinceBuild = "262"
        }
        vendor {
            name = "Spice contributors"
            url = "https://github.com/StevenBuglione/spice"
        }
    }
    pluginVerification {
        ides {
            current()
        }
    }
}

tasks {
    processResources {
        from("../../docs/annotations.md") {
            into("spice")
        }
    }

    jar {
        from("../../LICENSE")
    }

    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release = 25
        options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
    }

    test {
        maxHeapSize = "2g"
        systemProperty("spice.lsp.disabled", "true")
        systemProperty(
            "spice.visual.output",
            layout.buildDirectory
                .file("reports/visual/spice-annotations-light.png")
                .get()
                .asFile
                .absolutePath
        )
    }
}
