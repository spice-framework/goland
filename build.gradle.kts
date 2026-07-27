import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    java
    kotlin("jvm") version "2.4.10"
    id("org.jetbrains.intellij.platform") version "2.18.1"
}

group = "com.github.stevenbuglione.spice"
version = "0.2.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

val localGoLandPath = providers.gradleProperty("golandPath")
val repositoryRoot = rootProject.projectDir.resolve("../..")
val spiceExecutableName = if (
    System.getProperty("os.name").lowercase().contains("windows")
) {
    "spice.exe"
} else {
    "spice"
}
val integrationSpice = layout.buildDirectory.file(
    "tmp/integrationTest/bin/$spiceExecutableName"
)
val integrationTestSourceSet = sourceSets.create("integrationTest") {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}
val integrationTestImplementation = configurations.getByName(
    "integrationTestImplementation"
) {
    extendsFrom(configurations.testImplementation.get())
}

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
        testFramework(
            TestFrameworkType.Starter,
            configurationName = "integrationTestImplementation"
        )
    }
    testImplementation("junit:junit:4.13.2")
    integrationTestImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
    integrationTestImplementation("org.kodein.di:kodein-di-jvm:7.26.1")
    integrationTestImplementation("org.jetbrains.kotlin:kotlin-stdlib:2.4.10")
    integrationTestImplementation("net.java.dev.jna:jna-platform:5.17.0")
    integrationTestImplementation(
        "org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.10.2"
    )
    add(
        integrationTestSourceSet.runtimeOnlyConfigurationName,
        "org.jetbrains.teamcity:serviceMessages:2024.07"
    )
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        jvmTarget = JvmTarget.JVM_25
        allWarningsAsErrors = true
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
            "spice.repository.root",
            rootProject.projectDir.resolve("../..").canonicalPath
        )
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

val integrationTest = intellijPlatformTesting.testIdeUi.register(
    "integrationTest"
) {
    task {
        dependsOn("buildSpiceForIntegrationTest")
        testClassesDirs = integrationTestSourceSet.output.classesDirs
        classpath = integrationTestSourceSet.runtimeClasspath
        useJUnitPlatform()
        maxHeapSize = "2g"
        systemProperty(
            "spice.repository.root",
            repositoryRoot.canonicalPath
        )
        systemProperty(
            "spice.integration.executable",
            integrationSpice.get().asFile.absolutePath
        )
        systemProperty(
            "spice.installed.visual.output",
            layout.buildDirectory
                .dir("reports/visual")
                .get()
                .asFile
                .absolutePath
        )
        systemProperty(
            "spice.installed.project.output",
            layout.buildDirectory
                .dir("tmp/integrationTest/projects")
                .get()
                .asFile
                .absolutePath
        )
        if (localGoLandPath.isPresent) {
            systemProperty(
                "spice.goland.path",
                file(localGoLandPath.get()).canonicalPath
            )
        }
    }
}

tasks.register<Exec>("buildSpiceForIntegrationTest") {
    mustRunAfter("prepareSandbox")
    val output = integrationSpice.get().asFile
    inputs.files(
        fileTree(repositoryRoot) {
            include("**/*.go")
            include("go.mod")
            include("go.sum")
            exclude("editors/goland/build/**")
            exclude("editors/goland/.intellijPlatform/**")
            exclude("out/**")
        }
    )
    outputs.file(output)
    workingDir(repositoryRoot.resolve("cmd/spice"))
    environment("GOWORK", "off")
    commandLine(
        "go",
        "build",
        "-trimpath",
        "-o",
        output.absolutePath,
        "."
    )
    doFirst {
        output.parentFile.mkdirs()
    }
}
