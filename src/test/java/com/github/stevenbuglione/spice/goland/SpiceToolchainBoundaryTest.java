package com.github.stevenbuglione.spice.goland;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Locks command execution to the standalone toolchain module. */
public final class SpiceToolchainBoundaryTest {
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
                        + "v0.0.0-20260805222344-fd87027fc195"
        ));
        assertTrue(fixture.contains(
                "replace github.com/spice-framework/spice => "
                        + "__SPICE_CORE_ROOT__"
        ));
        assertTrue(fixture.contains(
                "replace github.com/spice-framework/toolchain => "
                        + "__SPICE_TOOLCHAIN_ROOT__"
        ));

        String retired = "github.com/spice-framework/spice" + "/cmd/spice";
        try (var paths = Files.walk(root.resolve("src"))) {
            paths.filter(Files::isRegularFile)
                    .filter(SpiceToolchainBoundaryTest::isSource)
                    .forEach(path -> assertNoRetiredCommandPath(path, retired));
        }
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
