package com.github.stevenbuglione.spice.goland;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Locks command execution to the standalone toolchain module. */
public final class SpiceToolchainBoundaryTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void testCoreRemainsDescriptorOnlyAndToolchainOwnsCommands()
            throws IOException {
        Path root = Path.of(
                System.getProperty("spice.plugin.root", ".")
        ).toAbsolutePath().normalize();
        String build = Files.readString(
                root.resolve("build.gradle.kts"),
                StandardCharsets.UTF_8
        );
        assertTrue(build.contains("spiceToolchainPath"));
        assertTrue(build.contains(
                "workingDir(spiceToolchainDirectory.map { it.dir(\"cmd/spice\") })"
        ));

        String fixture = Files.readString(
                root.resolve(
                        "src/integrationTest/resources/projects/"
                                + "concealment/go.mod"
                ),
                StandardCharsets.UTF_8
        );
        assertTrue(fixture.contains(
                "github.com/spice-framework/toolchain/cmd/spice"
        ));
        assertTrue(fixture.contains(
                "github.com/spice-framework/spice "
                        + "v0.0.0-20260805222830-a2ecd56df246"
        ));
        assertTrue(fixture.contains(
                "github.com/spice-framework/toolchain "
                        + "v0.0.0-20260805230546-150f8ae62c13"
        ));
        assertFalse(fixture.contains("replace "));
        assertFalse(fixture.contains("__SPICE_"));

        String retired = "github.com/spice-framework/spice" + "/cmd/spice";
        try (var paths = Files.walk(root.resolve("src"))) {
            paths.filter(Files::isRegularFile)
                    .filter(SpiceToolchainBoundaryTest::isSource)
                    .forEach(path -> assertNoRetiredCommandPath(path, retired));
        }
    }

    @Test
    public void testEveryTrackedGoModuleParsesWithoutRewriting()
            throws IOException, InterruptedException {
        Path root = pluginRoot();
        var modules = SpiceFixtureGoMod.trackedGoModules(root);
        assertFalse("expected a tracked fixture go.mod", modules.isEmpty());
        for (Path module : modules) {
            byte[] before = Files.readAllBytes(module);
            String metadata = SpiceFixtureGoMod.inspect(module);
            assertTrue(module + " has no module path", metadata.contains(
                    "\"Module\""
            ));
            assertArrayEquals(
                    module + " was changed while parsing",
                    before,
                    Files.readAllBytes(module)
            );
        }
    }

    @Test
    public void testFixtureMaterializationAddsParseableLocalReplacements()
            throws IOException, InterruptedException {
        Path root = pluginRoot();
        Path scratch = temporaryFolder.newFolder("fixture").toPath();
        Path core = Files.createDirectories(scratch.resolve("core"));
        Path toolchain = Files.createDirectories(scratch.resolve("toolchain"));
        Path destination = scratch.resolve("go.mod");
        SpiceFixtureGoMod.materialize(
                root.resolve(
                        "src/integrationTest/resources/projects/"
                                + "concealment/go.mod"
                ),
                destination,
                core,
                toolchain
        );

        String rendered = Files.readString(destination, StandardCharsets.UTF_8);
        assertTrue(rendered.contains(
                "replace " + SpiceFixtureGoMod.CORE_MODULE + " => "
                        + SpiceFixtureGoMod.goPath(core)
        ));
        assertTrue(rendered.contains(
                "replace " + SpiceFixtureGoMod.TOOLCHAIN_MODULE + " => "
                        + SpiceFixtureGoMod.goPath(toolchain)
        ));
        assertFalse(rendered.contains("__SPICE_"));
        String metadata = SpiceFixtureGoMod.inspect(destination);
        assertNotEquals("rendered module metadata is empty", "", metadata);
    }

    private static Path pluginRoot() {
        return Path.of(
                System.getProperty("spice.plugin.root", ".")
        ).toAbsolutePath().normalize();
    }

    private static boolean isSource(Path path) {
        String name = path.getFileName().toString();
        return name.endsWith(".java")
                || name.endsWith(".kt")
                || name.equals("go.mod");
    }

    private static void assertNoRetiredCommandPath(
            Path path,
            String retired
    ) {
        try {
            assertFalse(
                    path + " retains a core-owned command path",
                    Files.readString(path, StandardCharsets.UTF_8)
                            .contains(retired)
            );
        } catch (IOException exception) {
            throw new IllegalStateException("read " + path, exception);
        }
    }
}
