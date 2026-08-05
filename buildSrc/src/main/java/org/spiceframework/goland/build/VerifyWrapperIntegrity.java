package org.spiceframework.goland.build;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

/** Validates the executable Gradle wrapper before repository verification. */
public abstract class VerifyWrapperIntegrity extends DefaultTask {
    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getWrapperProperties();

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getWrapperJar();

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getUnixScript();

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getWindowsScript();

    @Input
    public abstract Property<String> getGradleVersion();

    @Input
    public abstract Property<String> getDistributionSha256();

    @Input
    public abstract Property<String> getWrapperSha256();

    @TaskAction
    public final void verify() throws IOException, NoSuchAlgorithmException {
        String properties = Files.readString(
                getWrapperProperties().get().getAsFile().toPath(),
                StandardCharsets.UTF_8
        );
        String distribution =
                "distributionUrl=https\\://services.gradle.org/distributions/gradle-"
                        + getGradleVersion().get()
                        + "-bin.zip";
        if (!properties.contains(distribution)) {
            throw new IllegalStateException(
                    "Gradle wrapper does not select " + getGradleVersion().get()
            );
        }
        String checksum = "distributionSha256Sum=" + getDistributionSha256().get();
        if (!properties.contains(checksum)) {
            throw new IllegalStateException("Gradle distribution checksum is not pinned");
        }
        byte[] wrapper = Files.readAllBytes(
                getWrapperJar().get().getAsFile().toPath()
        );
        String actual = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(wrapper)
        );
        if (!actual.equals(getWrapperSha256().get())) {
            throw new IllegalStateException(
                    "Gradle wrapper JAR checksum is " + actual
            );
        }
    }
}
