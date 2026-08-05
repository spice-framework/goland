package com.github.stevenbuglione.spice.goland;

import com.goide.execution.GoBuildingRunConfiguration;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.RunManagerEx;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.jdom.Element;

public final class SpiceApplicationRunConfigurationProducerTest
        extends BasePlatformTestCase {
    public void testApplicationUsesWholeDirectoryInsteadOfTemporaryFile()
            throws Exception {
        String source = """
                package main

                import (
                    "os"

                    spiceapp "example.com/app/internal/spicegen/app"
                )

                // @Application
                // @management.Enable(expose=["health"])
                func main() {
                    os.Exit(spiceapp.Main(os.Args[1:]))
                }
                """;
        myFixture.configureByText("main.go", source);
        PsiComment marker = applicationMarker();

        SpiceApplicationConfiguration configuration = configuration();
        Ref<PsiElement> sourceElement = new Ref<>();
        boolean configured = new SpiceApplicationRunConfigurationProducer()
                .setupConfigurationFromContext(
                        configuration,
                        new ConfigurationContext(marker),
                        sourceElement
                );

        assertTrue(configured);
        assertEquals(GoBuildingRunConfiguration.Kind.DIRECTORY, configuration.getKind());
        assertTrue(configuration.getFilePaths().isEmpty());
        assertEquals(".", configuration.getSpicePattern());
        assertEquals("", configuration.getSpiceTarget());
        List<SpiceGenerateBeforeRunTask> generationTasks =
                RunManagerEx.getInstanceEx(getProject()).getBeforeRunTasks(
                        configuration,
                        SpiceGenerateBeforeRunTaskProvider.ID
                );
        assertEquals(1, generationTasks.size());
        assertTrue(generationTasks.getFirst().isEnabled());
        assertEquals(marker, sourceElement.get());
        assertEquals(source, myFixture.getEditor().getDocument().getText());
        assertTrue(myFixture.getEditor().getDocument().getText().contains("// @Application"));
        assertFalse(myFixture.getEditor().getDocument().getText().contains("\n@Application"));
        GeneralCommandLine runCommand =
                SpiceApplicationRunningState.commandLine(configuration);
        assertEquals(
                List.of("spice", "run", "."),
                runCommand.getCommandLineList(null)
        );
        GeneralCommandLine generateCommand =
                SpiceGenerateBeforeRunTaskProvider.generationCommandLine(
                        configuration
                );
        assertEquals(
                List.of("spice", "generate", "."),
                generateCommand.getCommandLineList(null)
        );
        assertNotNull(
                ProgramRunner.getRunner(
                        DefaultRunExecutor.EXECUTOR_ID,
                        configuration
                )
        );
        assertNotNull(
                ProgramRunner.getRunner(
                        DefaultDebugExecutor.EXECUTOR_ID,
                        configuration
                )
        );
    }

    public void testGutterContextPrefersSpiceOverEveryGoApplicationConfiguration() {
        VfsRootAccess.allowRootAccess(
                getTestRootDisposable(),
                Path.of(System.getProperty("spice.repository.root")).toString()
        );
        myFixture.configureByText(
                "main.go",
                """
                        package main

                        // @Application
                        func main() {}
                        """
        );
        PsiNamedElement main = PsiTreeUtil.findChildrenOfType(
                myFixture.getFile(),
                com.goide.psi.GoFunctionDeclaration.class
        ).stream().findFirst().orElseThrow();

        List<ConfigurationFromContext> configurations =
                new ConfigurationContext(main).createConfigurationsFromContext();

        assertFalse("missing gutter configurations", configurations.isEmpty());
        assertTrue(
                "Spice configuration was not preferred: " + configurations,
                configurations.getFirst().getConfiguration()
                        instanceof SpiceApplicationConfiguration
        );
        assertTrue(
                "temporary Go file configuration survived replacement: "
                        + configurations,
                configurations.stream().noneMatch(candidate ->
                        candidate.getConfiguration()
                                instanceof com.goide.execution.application
                                .GoApplicationConfiguration go
                                && !(go instanceof SpiceApplicationConfiguration)
                                && go.getKind()
                                == GoBuildingRunConfiguration.Kind.FILE)
        );
    }

    public void testRejectsApplicationMarkerOnNonMainFunction() {
        myFixture.configureByText(
                "main.go",
                """
                        package main

                        // @Application
                        func helper() {}

                        func main() {}
                        """
        );
        PsiComment marker = applicationMarker();
        SpiceApplicationConfiguration configuration = configuration();

        assertFalse(new SpiceApplicationRunConfigurationProducer()
                .setupConfigurationFromContext(
                        configuration,
                        new ConfigurationContext(marker),
                        new Ref<>()
                ));
    }

    public void testRejectsOrdinaryMainWithoutApplicationMarker() {
        myFixture.configureByText("main.go", "package main\n\nfunc main() {}\n");
        SpiceApplicationConfiguration configuration = configuration();

        assertFalse(new SpiceApplicationRunConfigurationProducer()
                .setupConfigurationFromContext(
                        configuration,
                        new ConfigurationContext(myFixture.getFile()),
                        new Ref<>()
                ));
    }

    public void testConfigurationPersistsSpiceTargetAndPattern()
            throws Exception {
        VfsRootAccess.allowRootAccess(
                getTestRootDisposable(),
                repositoryRoot().toString()
        );
        SpiceApplicationConfiguration original = configuration();
        original.setSpiceTarget("example.com/shop/cmd/server");
        original.setSpicePattern(". ./domain ./presentation");
        Element stored = new Element("configuration");
        original.writeExternal(stored);

        SpiceApplicationConfiguration restored = configuration();
        restored.readExternal(stored);

        assertEquals(
                "example.com/shop/cmd/server",
                restored.getSpiceTarget()
        );
        assertEquals(". ./domain ./presentation", restored.getSpicePattern());
        assertEquals(
                List.of(".", "./domain", "./presentation"),
                SpiceApplicationRunningState.patterns(restored)
        );
        assertEquals(
                List.of(
                        "spice",
                        "run",
                        "--target",
                        "example.com/shop/cmd/server",
                        ".",
                        "./domain",
                        "./presentation"
                ),
                SpiceApplicationRunningState.commandLine(restored)
                        .getCommandLineList(null)
        );
        assertEquals(
                List.of(
                        "spice",
                        "generate",
                        "--target",
                        "example.com/shop/cmd/server",
                        ".",
                        "./domain",
                        "./presentation"
                ),
                SpiceGenerateBeforeRunTaskProvider
                        .generationCommandLine(restored)
                        .getCommandLineList(null)
        );
    }

    public void testRunAndDebugCommandsExecuteFoldedPetclinicPackage()
            throws Exception {
        Path repository = repositoryRoot();
        Path temporary = Files.createTempDirectory("spice-goland-run-");
        try {
            Path executable = temporary.resolve(
                    isWindows() ? "spice-test.exe" : "spice-test"
            );
            assertSuccessful(
                    new GeneralCommandLine(
                            "go",
                            "build",
                            "-trimpath",
                            "-o",
                            executable.toString(),
                            "./cmd/spice"
                    ).withWorkingDirectory(repository),
                    "build Spice test executable"
            );

            String previous = System.getProperty("spice.executable");
            System.setProperty("spice.executable", executable.toString());
            try {
                executePetclinicRunAndDebug(repository, temporary);
            } finally {
                if (previous == null) {
                    System.clearProperty("spice.executable");
                } else {
                    System.setProperty("spice.executable", previous);
                }
            }
        } finally {
            deleteTree(temporary);
        }

        String physicalSource = Files.readString(
                repository.resolve("examples/petclinic/main.go"),
                StandardCharsets.UTF_8
        );
        assertTrue(physicalSource.contains("// @Application"));
        assertFalse(physicalSource.contains("\n@Application"));
    }

    private void executePetclinicRunAndDebug(
            Path repository,
            Path temporary
    ) throws ExecutionException {
        Path petclinic = repository.resolve("examples/petclinic");
        SpiceApplicationConfiguration configuration = configuration();
        configuration.setKind(GoBuildingRunConfiguration.Kind.PACKAGE);
        configuration.setPackage(
                "github.com/spice-framework/spice/examples/petclinic"
        );
        configuration.setWorkingDirectory(petclinic.toString());
        configuration.setSpiceTarget("Petclinic");
        configuration.setSpicePattern(
                ". ./memory ./model ./owner ./presentation ./system ./vet"
        );
        configuration.setParams("-check");

        ProcessOutput run = assertSuccessful(
                SpiceApplicationRunningState.commandLine(configuration),
                "run folded Petclinic application"
        );
        assertTrue(combinedOutput(run).contains("Spice petclinic ready."));

        assertSuccessful(
                SpiceGenerateBeforeRunTaskProvider.generationCommandLine(
                        configuration
                ),
                "generate before debug"
        );
        Path debugBinary = temporary.resolve(
                isWindows() ? "petclinic-debug.exe" : "petclinic-debug"
        );
        assertSuccessful(
                new GeneralCommandLine(
                        "go",
                        "build",
                        "-trimpath",
                        "-gcflags=all=-N -l",
                        "-o",
                        debugBinary.toString(),
                        "."
                ).withWorkingDirectory(petclinic),
                "build complete package for debug"
        );
        ProcessOutput debug = assertSuccessful(
                new GeneralCommandLine(
                        debugBinary.toString(),
                        "-check"
                ).withWorkingDirectory(petclinic),
                "execute debug package"
        );
        assertTrue(combinedOutput(debug).contains("Spice petclinic ready."));
    }

    private static Path repositoryRoot() throws Exception {
        return Path.of(
                System.getProperty("spice.repository.root")
        ).toRealPath();
    }

    private static void deleteTree(Path root) throws Exception {
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private SpiceApplicationConfiguration configuration() {
        return new SpiceApplicationConfiguration(
                getProject(),
                "Spice Application",
                SpiceApplicationRunConfigurationType.getInstance()
        );
    }

    private static ProcessOutput assertSuccessful(
            GeneralCommandLine command,
            String operation
    ) throws ExecutionException {
        ProcessOutput output = new CapturingProcessHandler(command)
                .runProcess(180_000, true);
        if (output.isTimeout()
                || output.isCancelled()
                || output.getExitCode() != 0) {
            fail(
                    operation
                            + " failed: "
                            + command.getCommandLineString()
                            + "\n"
                            + combinedOutput(output)
            );
        }
        return output;
    }

    private static String combinedOutput(ProcessOutput output) {
        return output.getStdout() + "\n" + output.getStderr();
    }

    private static boolean isWindows() {
        return System.getProperty("os.name")
                .toLowerCase(Locale.ROOT)
                .contains("windows");
    }

    private PsiComment applicationMarker() {
        PsiComment marker = PsiTreeUtil.findChildOfType(
                myFixture.getFile(),
                PsiComment.class
        );
        assertNotNull(marker);
        return marker;
    }
}
