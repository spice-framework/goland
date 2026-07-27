package com.github.stevenbuglione.spice.goland;

final class SpiceExecutable {
    private static final String EXECUTABLE_PROPERTY = "spice.executable";
    private static final String EXECUTABLE_ENVIRONMENT = "SPICE_EXECUTABLE";

    private SpiceExecutable() {}

    static String resolve() {
        String configured = System.getProperty(EXECUTABLE_PROPERTY);
        if (configured == null || configured.isBlank()) {
            configured = System.getenv(EXECUTABLE_ENVIRONMENT);
        }
        return configured == null || configured.isBlank()
                ? "spice"
                : configured.strip();
    }
}
