package org.spiceframework.goland.build;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

/** Validates the exact cross-repository compatibility inputs. */
public abstract class VerifyCompatibilityInputs extends DefaultTask {
    private static final Pattern COMMIT = Pattern.compile("[0-9a-f]{40}");

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getCompatibilityFile();

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getCoreGoMod();

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getPetclinicGoMod();

    @Input
    public abstract Property<String> getGoVersion();

    @Input
    public abstract Property<String> getSpiceCommit();

    @Input
    public abstract Property<String> getPetclinicCommit();

    @TaskAction
    public final void verify() throws IOException, InterruptedException {
        requireModule(
                getCoreGoMod(),
                "github.com/spice-framework/spice",
                "spiceCorePath"
        );
        requireModule(
                getPetclinicGoMod(),
                "github.com/spice-framework/petclinic",
                "petclinicPath"
        );
        requireCommit("spiceCommit", getSpiceCommit().get());
        requireCommit("petclinicCommit", getPetclinicCommit().get());
        requireCheckout(
                "spiceCommit",
                getCoreGoMod(),
                getSpiceCommit().get()
        );
        requireCheckout(
                "petclinicCommit",
                getPetclinicGoMod(),
                getPetclinicCommit().get()
        );

        Process process = new ProcessBuilder("go", "version")
                .redirectErrorStream(true)
                .start();
        String output = new String(
                process.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8
        ).trim();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IllegalStateException("go version failed: " + output);
        }
        String required = " go" + getGoVersion().get() + " ";
        if (!output.contains(required)) {
            throw new IllegalStateException(
                    "Go toolchain is " + output + "; require " + required.trim()
            );
        }
    }

    private static void requireModule(
            RegularFileProperty property,
            String module,
            String option
    ) throws IOException {
        List<String> lines = Files.readAllLines(
                property.get().getAsFile().toPath(),
                StandardCharsets.UTF_8
        );
        if (lines.stream().map(String::trim).noneMatch(
                line -> line.equals("module " + module)
        )) {
            throw new IllegalStateException(
                    option + " does not identify the canonical " + module + " module"
            );
        }
    }

    private static void requireCommit(String name, String value) {
        if (!COMMIT.matcher(value).matches()) {
            throw new IllegalStateException(
                    name + " must be a complete lowercase Git object ID"
            );
        }
    }

    private static void requireCheckout(
            String name,
            RegularFileProperty goMod,
            String expected
    ) throws IOException, InterruptedException {
        Path repository = goMod.get().getAsFile().toPath().getParent();
        String actual = runGit(repository, "rev-parse", "HEAD").trim();
        if (!actual.equals(expected)) {
            throw new IllegalStateException(
                    name + " selects " + expected + "; checkout is " + actual
            );
        }
        requireCleanGit(repository, "diff", "--quiet", "--");
        requireCleanGit(repository, "diff", "--cached", "--quiet", "--");
    }

    private static String runGit(Path repository, String... arguments)
            throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder();
        builder.command().add("git");
        builder.command().add("-C");
        builder.command().add(repository.toString());
        builder.command().addAll(List.of(arguments));
        Process process = builder.redirectErrorStream(true).start();
        String output = new String(
                process.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8
        );
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IllegalStateException(
                    "git inspection failed for " + repository + ": " + output.trim()
            );
        }
        return output;
    }

    private static void requireCleanGit(
            Path repository,
            String... arguments
    ) throws IOException, InterruptedException {
        try {
            runGit(repository, arguments);
        } catch (IllegalStateException exception) {
            throw new IllegalStateException(
                    "compatibility checkout has tracked changes: " + repository,
                    exception
            );
        }
    }
}
