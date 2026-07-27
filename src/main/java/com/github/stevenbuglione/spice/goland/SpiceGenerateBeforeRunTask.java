package com.github.stevenbuglione.spice.goland;

import com.intellij.execution.BeforeRunTask;

final class SpiceGenerateBeforeRunTask
        extends BeforeRunTask<SpiceGenerateBeforeRunTask> {
    SpiceGenerateBeforeRunTask() {
        super(SpiceGenerateBeforeRunTaskProvider.ID);
    }
}
