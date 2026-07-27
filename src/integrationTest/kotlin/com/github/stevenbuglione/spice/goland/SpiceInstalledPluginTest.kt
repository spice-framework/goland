package com.github.stevenbuglione.spice.goland

import com.intellij.driver.sdk.openFile
import com.intellij.driver.sdk.closeAndDisableAllBalloonNotifications
import com.intellij.driver.sdk.changeTheme
import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.IdeTheme
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import com.intellij.driver.sdk.ui.components.common.JEditorUiComponent
import com.intellij.driver.sdk.ui.components.common.codeEditor
import com.intellij.driver.sdk.ui.components.common.editorTabs
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.waitForIndicators
import com.intellij.driver.model.LockSemantics
import com.intellij.driver.model.OnDispatcher
import com.intellij.ide.starter.driver.engine.runIdeWithDriver
import com.intellij.ide.starter.ide.installer.ExistingIdeInstaller
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.plugins.PluginConfigurator
import com.intellij.ide.starter.project.LocalProjectInfo
import com.intellij.ide.starter.runner.Starter
import com.intellij.tools.ide.starter.product.goland.GoLand
import java.io.File
import java.awt.Rectangle
import java.awt.Robot
import java.awt.image.BufferedImage
import java.awt.event.KeyEvent
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.time.Duration.Companion.minutes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SpiceInstalledPluginTest {
    @Test
    fun packagedPluginOpensRealGoLandProject() {
        val pluginArchive = Path.of(
            requireNotNull(System.getProperty("path.to.build.plugin")) {
                "path.to.build.plugin must identify the packaged Spice archive"
            },
        )
        val repository = Path.of(
            requireNotNull(System.getProperty("spice.repository.root")) {
                "spice.repository.root is required"
            },
        )
        val installedGoLand = System.getProperty("spice.goland.path")
            ?.let(Path::of)
        val spiceExecutable = Path.of(
            requireNotNull(System.getProperty("spice.integration.executable")) {
                "spice.integration.executable must identify the current build"
            },
        )
        assertTrue(Files.isRegularFile(spiceExecutable))
        val fixture = repository.resolve(
            "editors/goland/src/integrationTest/resources/projects/concealment",
        )
        val projectOutput = Path.of(
            requireNotNull(System.getProperty("spice.installed.project.output")) {
                "spice.installed.project.output is required"
            },
        )
        Files.createDirectories(projectOutput)
        val project = Files.createTempDirectory(
            projectOutput,
            "concealment-",
        )
        Files.copy(fixture.resolve("main.go"), project.resolve("main.go"))
        Files.copy(repository.resolve("go.sum"), project.resolve("go.sum"))
        Files.writeString(
            project.resolve("go.mod"),
            Files.readString(fixture.resolve("go.mod")).replace(
                "../../../../../../..",
                repository.toString().replace('\\', '/'),
            ),
        )
        val sourceFile = project.resolve("main.go")
        val sourceBefore = Files.readString(sourceFile)
        val screenshotDirectory = Path.of(
            requireNotNull(System.getProperty("spice.installed.visual.output")) {
                "spice.installed.visual.output is required"
            },
        )
        val pinnedGoLand = if (installedGoLand != null) {
            IdeInfo.GoLand.copy(
                getInstaller = { ExistingIdeInstaller(installedGoLand) },
            )
        } else {
            IdeInfo.GoLand.copy(buildNumber = "262.8665.336")
        }

        Starter.newContext(
            "spice-installed-concealment",
            TestCase(pinnedGoLand, LocalProjectInfo(project)),
        ).apply {
            PluginConfigurator(this).installPluginFromPath(pluginArchive)
            applyVMOptionsPatch {
                val inheritedPath = System.getenv("PATH").orEmpty()
                withEnv(
                    "PATH",
                    spiceExecutable.parent.toString()
                        + File.pathSeparator
                        + inheritedPath,
                )
            }
        }.runIdeWithDriver().useDriverAndCloseIde {
            waitForIndicators(3.minutes)
            openFile("main.go")
            waitForIndicators(1.minutes)
            closeAndDisableAllBalloonNotifications()
            ideFrame {
                val frame: IdeaFrameUI = this
                val localRobot = Robot()
                localRobot.keyPress(KeyEvent.VK_ESCAPE)
                localRobot.keyRelease(KeyEvent.VK_ESCAPE)
                resize(1280, 800)
                ensureFocused()
                codeEditor {
                    assertEquals(sourceBefore, text)

                    val applicationAt = sourceBefore.indexOf("@Application")
                    val namespaceAt = sourceBefore.indexOf(
                        "@observability.Logging",
                    )
                    val declaration = sourceBefore.indexOf("func main")
                    assertTrue(applicationAt >= 0)
                    assertTrue(namespaceAt >= 0)
                    assertTrue(declaration >= 0)

                    val applicationPoint = driver.withContext(
                        OnDispatcher.EDT,
                        LockSemantics.NO_LOCK,
                    ) {
                        editor.offsetToXY(applicationAt)
                    }
                    val namespacePoint = driver.withContext(
                        OnDispatcher.EDT,
                        LockSemantics.NO_LOCK,
                    ) {
                        editor.offsetToXY(namespaceAt)
                    }
                    val declarationPoint = driver.withContext(
                        OnDispatcher.EDT,
                        LockSemantics.NO_LOCK,
                    ) {
                        editor.offsetToXY(declaration)
                    }

                    assertEquals(
                        declarationPoint.x,
                        applicationPoint.x,
                        "folded // prefix must reclaim all horizontal width",
                    )
                    assertEquals(
                        declarationPoint.x,
                        namespacePoint.x,
                        "qualified annotations must reclaim all prefix width",
                    )

                    moveCaretToOffset(declaration)
                    keyboard {
                        enter()
                        typeText("@")
                    }
                    robot.waitForIdle()
                    assertTrue(
                        text.contains("// @\nfunc main"),
                        "typing @ must create physical valid-Go comment syntax",
                    )
                    assertSafeDocument(text)
                    frame.saveAll()
                    awaitSourceContains(sourceFile, "// @")

                    driver.invokeAction("\$Undo")
                    driver.invokeAction("\$Undo")
                    robot.waitForIdle()
                    assertEquals(sourceBefore, text)

                    driver.invokeAction("\$Redo")
                    driver.invokeAction("\$Redo")
                    robot.waitForIdle()
                    assertTrue(text.contains("// @\nfunc main"))
                    assertSafeDocument(text)

                    driver.invokeAction("\$Undo")
                    driver.invokeAction("\$Undo")
                    driver.invokeAction("ReformatCode")
                    robot.waitForIdle()
                    assertEquals(sourceBefore, text)
                }
                frame.saveAll()
                editorTabs {
                    closeTab("main.go")
                }
                driver.openFile("main.go")
                codeEditor {
                    assertEquals(sourceBefore, text)
                    assertSafeDocument(text)
                    moveCaretToOffset(0)
                    robot.waitForIdle()

                    val highlightReport = getAllHighlights()
                        .joinToString("\n") {
                            "${it.getSeverity()}: ${it.getDescription()}"
                        }
                    Files.createDirectories(screenshotDirectory)
                    Files.writeString(
                        screenshotDirectory.resolve(
                            "spice-installed-highlights.txt",
                        ),
                        highlightReport,
                    )
                    assertFalse(
                        highlightReport.lineSequence().any {
                            it.startsWith("ERROR:")
                        },
                        "installed editor contains error highlights:\n"
                            + highlightReport,
                    )
                    listOf(
                        "invalid character U+0040",
                        "expected declaration, found 'ILLEGAL'",
                        "undefined: spiceMain",
                        "gocommand-",
                    ).forEach { formerFailure ->
                        assertFalse(
                            highlightReport.contains(formerFailure),
                            "installed editor repeated former corruption: "
                                + formerFailure,
                        )
                    }

                    driver.changeTheme(IdeTheme.LIGHT)
                    robot.waitForIdle()
                    val light = captureEditor(localRobot, this)
                    driver.changeTheme(IdeTheme.DARK)
                    robot.waitForIdle()
                    val dark = captureEditor(localRobot, this)

                    assertNotBlank(light)
                    assertNotBlank(dark)
                    assertThemeDifference(light, dark)
                    assertTrue(
                        ImageIO.write(
                            light,
                            "png",
                            screenshotDirectory
                                .resolve("spice-installed-light.png")
                                .toFile(),
                        ),
                    )
                    assertTrue(
                        ImageIO.write(
                            dark,
                            "png",
                            screenshotDirectory
                                .resolve("spice-installed-dark.png")
                                .toFile(),
                        ),
                    )
                    assertMatchesGolden(
                        "spice-installed-light.png",
                        light,
                    )
                    assertMatchesGolden(
                        "spice-installed-dark.png",
                        dark,
                    )
                }
            }
        }

        assertEquals(
            sourceBefore,
            Files.readString(sourceFile),
            "installed-plugin presentation must not mutate physical Go source",
        )
    }

    private fun captureEditor(
        robot: Robot,
        editor: JEditorUiComponent,
    ): BufferedImage {
        val component = editor.component
        val location = component.getLocationOnScreen()
        val width = component.width.coerceAtMost(640)
        val height = component.height.coerceAtMost(360)
        assertEquals(640, width, "editor must expose the fixed golden width")
        assertEquals(360, height, "editor must expose the fixed golden height")
        return robot.createScreenCapture(
            Rectangle(
                location.x,
                location.y,
                width,
                height,
            ),
        )
    }

    private fun assertMatchesGolden(
        name: String,
        actual: BufferedImage,
    ) {
        val expected = requireNotNull(
            javaClass.getResourceAsStream("/goldens/$name"),
        ) {
            "missing installed-IDE golden $name"
        }.use(ImageIO::read)
        assertEquals(expected.width, actual.width, "$name width")
        assertEquals(expected.height, actual.height, "$name height")

        var totalDelta = 0L
        var changed = 0L
        val pixels = actual.width.toLong() * actual.height
        for (y in 0 until actual.height) {
            for (x in 0 until actual.width) {
                val expectedPixel = expected.getRGB(x, y)
                val actualPixel = actual.getRGB(x, y)
                val delta = abs(
                    (expectedPixel shr 16 and 0xff)
                        - (actualPixel shr 16 and 0xff),
                ) + abs(
                    (expectedPixel shr 8 and 0xff)
                        - (actualPixel shr 8 and 0xff),
                ) + abs(
                    (expectedPixel and 0xff) - (actualPixel and 0xff),
                )
                totalDelta += delta
                if (delta > 48) {
                    changed++
                }
            }
        }
        val meanChannelDelta = totalDelta.toDouble() / (pixels * 3)
        val changedRatio = changed.toDouble() / pixels
        assertTrue(
            meanChannelDelta <= 5.0,
            "$name mean channel delta $meanChannelDelta exceeds 5.0",
        )
        assertTrue(
            changedRatio <= 0.08,
            "$name changed-pixel ratio $changedRatio exceeds 0.08",
        )
    }

    private fun assertThemeDifference(
        light: BufferedImage,
        dark: BufferedImage,
    ) {
        assertEquals(light.width, dark.width)
        assertEquals(light.height, dark.height)
        var totalDelta = 0L
        var samples = 0L
        val stepX = (light.width / 96).coerceAtLeast(1)
        val stepY = (light.height / 96).coerceAtLeast(1)
        for (y in 0 until light.height step stepY) {
            for (x in 0 until light.width step stepX) {
                val lightPixel = light.getRGB(x, y)
                val darkPixel = dark.getRGB(x, y)
                totalDelta += abs(
                    (lightPixel shr 16 and 0xff)
                        - (darkPixel shr 16 and 0xff),
                )
                totalDelta += abs(
                    (lightPixel shr 8 and 0xff)
                        - (darkPixel shr 8 and 0xff),
                )
                totalDelta += abs(
                    (lightPixel and 0xff) - (darkPixel and 0xff),
                )
                samples += 3
            }
        }
        assertTrue(
            totalDelta / samples > 40,
            "installed GoLand light and dark renders must visibly differ",
        )
    }

    private fun assertSafeDocument(source: String) {
        assertFalse(
            Regex("""(?m)^\s*@""").containsMatchIn(source),
            "editor document must never contain naked annotations",
        )
        listOf(
            "gocommand-",
            "Value: annotationDocumentation",
            "protocolRangeAtOffsets",
            "ControllerHandler(",
        ).forEach { corruption ->
            assertFalse(
                source.contains(corruption),
                "editor document contains protocol/source corruption: $corruption",
            )
        }
    }

    private fun awaitSourceContains(source: Path, expected: String) {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (System.nanoTime() < deadline) {
            if (Files.readString(source).contains(expected)) {
                return
            }
            Thread.sleep(50)
        }
        assertTrue(
            Files.readString(source).contains(expected),
            "saved source did not contain $expected within five seconds",
        )
    }

    private fun assertNotBlank(image: BufferedImage) {
        val first = image.getRGB(0, 0)
        var materiallyDifferent = 0
        val stepX = (image.width / 64).coerceAtLeast(1)
        val stepY = (image.height / 64).coerceAtLeast(1)
        for (y in 0 until image.height step stepY) {
            for (x in 0 until image.width step stepX) {
                val pixel = image.getRGB(x, y)
                val red = abs((pixel shr 16 and 0xff) - (first shr 16 and 0xff))
                val green = abs((pixel shr 8 and 0xff) - (first shr 8 and 0xff))
                val blue = abs((pixel and 0xff) - (first and 0xff))
                if (red + green + blue > 24) {
                    materiallyDifferent++
                }
            }
        }
        assertTrue(
            materiallyDifferent > 64,
            "installed GoLand screenshot must contain rendered editor content",
        )
    }
}
