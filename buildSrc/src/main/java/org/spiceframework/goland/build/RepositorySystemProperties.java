package org.spiceframework.goland.build;

import java.util.List;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.Internal;
import org.gradle.process.CommandLineArgumentProvider;

/** Supplies explicit external repository roots to forked editor tests. */
public abstract class RepositorySystemProperties
        implements CommandLineArgumentProvider {
    @Internal
    public abstract DirectoryProperty getCore();

    @Internal
    public abstract DirectoryProperty getPetclinic();

    @Override
    public final Iterable<String> asArguments() {
        return List.of(
                "-Dspice.core.root=" + getCore().get().getAsFile(),
                "-Dspice.petclinic.root=" + getPetclinic().get().getAsFile()
        );
    }
}
