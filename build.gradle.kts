import org.gradle.api.artifacts.dsl.LockMode
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.spiceframework.goland.build.RepositorySystemProperties
import org.spiceframework.goland.build.VerifyCompatibilityInputs
import org.spiceframework.goland.build.VerifyWrapperIntegrity
import java.util.Properties

plugins {
    java
    kotlin("jvm") version "2.4.10"
    id("org.jetbrains.intellij.platform") version "2.18.1"
}

val compatibility = Properties().apply {
    rootProject.file("compatibility.properties").inputStream().use(::load)
}
fun compatibilityValue(name: String): String =
    requireNotNull(compatibility.getProperty(name)) {
        "compatibility.properties is missing $name"
    }

group = "org.spiceframework.goland"
version = compatibilityValue("pluginVersion")

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

val localGoLandPath = providers.gradleProperty("golandPath")
val spiceCorePath = providers.gradleProperty("spiceCorePath")
    .orElse(providers.environmentVariable("SPICE_CORE_ROOT"))
val spiceToolchainPath = providers.gradleProperty("spiceToolchainPath")
    .orElse(providers.environmentVariable("SPICE_TOOLCHAIN_ROOT"))
val petclinicPath = providers.gradleProperty("petclinicPath")
    .orElse(providers.environmentVariable("SPICE_PETCLINIC_ROOT"))
val spiceCoreDirectory = layout.dir(spiceCorePath.map(::File))
val spiceToolchainDirectory = layout.dir(spiceToolchainPath.map(::File))
val petclinicDirectory = layout.dir(petclinicPath.map(::File))
val spiceCoreInputs = spiceCoreDirectory.map { directory ->
    directory.asFileTree.matching {
        include("**/*.go")
        include("go.mod")
        include("go.sum")
        include("vendor/modules.txt")
        exclude(".tmp/**")
        exclude("bin/**")
        exclude("editors/**")
        exclude("out/**")
    }
}
val spiceToolchainInputs = spiceToolchainDirectory.map { directory ->
    directory.asFileTree.matching {
        include("**/*.go")
        include("go.mod")
        include("go.sum")
        include("vendor/modules.txt")
        exclude(".tmp/**")
        exclude("bin/**")
        exclude("out/**")
    }
}
val petclinicInputs = petclinicDirectory.map { directory ->
    directory.asFileTree.matching {
        include("**/*.css")
        include("**/*.go")
        include("**/*.html")
        include("**/*.json")
        include("**/*.properties")
        include("**/*.sql")
        include("go.mod")
        include("go.sum")
        include("vendor/modules.txt")
        exclude(".git/**")
    }
}

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
            goland(compatibilityValue("golandVersion"))
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
    integrationTestImplementation(sourceSets.test.get().output)
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
        languageVersion = JavaLanguageVersion.of(
            compatibilityValue("javaVersion").toInt(),
        )
    }
}

kotlin {
    jvmToolchain(compatibilityValue("javaVersion").toInt())
    compilerOptions {
        jvmTarget = JvmTarget.JVM_25
        allWarningsAsErrors = true
    }
}

dependencyLocking {
    lockAllConfigurations()
    lockMode = LockMode.STRICT
    // The IntelliJ Platform plugin represents the pinned GoLand distribution
    // as localIde:GO for installed-IDE verification and go:goland for the
    // downloadable CI distribution. Keep both resolution graphs strictly
    // locked without making either environment's coordinate authoritative for
    // the other.
    lockFile = file(
        if (localGoLandPath.isPresent) {
            "gradle-installed-goland.lockfile"
        } else {
            "gradle.lockfile"
        }
    )
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
            url = "https://github.com/spice-framework/goland"
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
        from("LICENSE")
    }

    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release = 25
        options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
    }

    test {
        maxHeapSize = "2g"
        systemProperty("spice.lsp.disabled", "true")
        systemProperty("spice.plugin.root", rootProject.projectDir.canonicalPath)
        inputs.files(spiceCoreInputs, spiceToolchainInputs, petclinicInputs)
        jvmArgumentProviders.add(
            objects.newInstance(RepositorySystemProperties::class.java).apply {
                core.set(spiceCoreDirectory)
                toolchain.set(spiceToolchainDirectory)
                petclinic.set(petclinicDirectory)
            },
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
        systemProperty("spice.plugin.root", rootProject.projectDir.canonicalPath)
        inputs.files(spiceCoreInputs, spiceToolchainInputs, petclinicInputs)
        systemProperty(
            "spice.goland.build",
            compatibilityValue("golandBuild"),
        )
        jvmArgumentProviders.add(
            objects.newInstance(RepositorySystemProperties::class.java).apply {
                core.set(spiceCoreDirectory)
                toolchain.set(spiceToolchainDirectory)
                petclinic.set(petclinicDirectory)
            },
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
        spiceToolchainInputs,
    )
    outputs.file(output)
    workingDir(spiceToolchainDirectory.map { it.dir("cmd/spice") })
    environment("GOWORK", "off")
    environment("GOPROXY", "off")
    environment("GOTOOLCHAIN", "local")
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

val verifyWrapperIntegrity = tasks.register<VerifyWrapperIntegrity>(
    "verifyWrapperIntegrity",
) {
    wrapperProperties.set(layout.projectDirectory.file("gradle/wrapper/gradle-wrapper.properties"))
    wrapperJar.set(layout.projectDirectory.file("gradle/wrapper/gradle-wrapper.jar"))
    unixScript.set(layout.projectDirectory.file("gradlew"))
    windowsScript.set(layout.projectDirectory.file("gradlew.bat"))
    gradleVersion.set(compatibilityValue("gradleVersion"))
    distributionSha256.set(compatibilityValue("gradleDistributionSha256"))
    wrapperSha256.set(compatibilityValue("gradleWrapperSha256"))
}

val verifyCompatibilityInputs = tasks.register<VerifyCompatibilityInputs>(
    "verifyCompatibilityInputs",
) {
    compatibilityFile.set(layout.projectDirectory.file("compatibility.properties"))
    coreGoMod.set(spiceCoreDirectory.map { it.file("go.mod") })
    toolchainGoMod.set(spiceToolchainDirectory.map { it.file("go.mod") })
    petclinicGoMod.set(petclinicDirectory.map { it.file("go.mod") })
    goVersion.set(compatibilityValue("goVersion"))
    spiceCommit.set(compatibilityValue("spiceCommit"))
    toolchainCommit.set(compatibilityValue("toolchainCommit"))
    petclinicCommit.set(compatibilityValue("petclinicCommit"))
}

tasks.named("check") {
    dependsOn(verifyWrapperIntegrity, verifyCompatibilityInputs)
}

tasks.register("verifyRepository") {
    group = "verification"
    description = "Runs unit, packaging, structure, compatibility, and wrapper gates."
    dependsOn(
        verifyWrapperIntegrity,
        verifyCompatibilityInputs,
        "test",
        "buildPlugin",
        "verifyPluginProjectConfiguration",
        "verifyPluginStructure",
        "verifyPlugin",
    )
}

tasks.register("verifyInstalledIde") {
    group = "verification"
    description = "Runs the packaged-plugin installed GoLand interaction suite."
    dependsOn(
        verifyWrapperIntegrity,
        verifyCompatibilityInputs,
        integrationTest,
    )
}
