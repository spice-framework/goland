package com.github.stevenbuglione.spice.goland;

import junit.framework.TestCase;
import java.util.List;

public final class SpicePluginHealthServiceTest extends TestCase {
    public void testParsesOnlyAuthorizedGoToolDirectives() {
        assertEquals(
                List.of(
                        "example.com/acme/cmd/spice-annotations",
                        "github.com/spice-framework/spice/cmd/spice-annotation-core"
                ),
                SpicePluginHealthService.parseToolDirectives(
                        """
                                module example.com/app

                                tool (
                                    github.com/spice-framework/spice/cmd/spice-annotation-core
                                    example.com/acme/cmd/spice-annotations // pinned by require
                                )

                                tool github.com/spice-framework/spice/cmd/spice-annotation-core
                                require example.com/not-a-tool v1.0.0
                                """
                )
        );
    }

    public void testRendersActionableHealthAndFailure() {
        SpicePluginHealthService.Snapshot snapshot =
                new SpicePluginHealthService.Snapshot(
                        "C:\\tools\\spice.exe",
                        "spice v0.2.0",
                        "go version go1.26.5 windows/amd64",
                        "D:\\work\\commerce",
                        "Running",
                        "vendor / offline",
                        List.of("example.com/tools/annotations"),
                        "tool handshake failed"
                );
        String rendered = snapshot.render();
        for (String expected : List.of(
                "C:\\tools\\spice.exe",
                "go1.26.5",
                "D:\\work\\commerce",
                "LSP state: Running",
                "vendor / offline",
                "example.com/tools/annotations",
                "Last failure:",
                "tool handshake failed"
        )) {
            assertTrue(
                    "missing health value: " + expected,
                    rendered.contains(expected)
            );
        }
    }

    public void testHandlesMalformedOrEmptyToolBlocks() {
        assertEquals(
                List.of("example.com/tool"),
                SpicePluginHealthService.parseToolDirectives(
                        """
                                tool (
                                  // comment
                                  example.com/tool extra-ignored
                                )
                                tool (
                                """
                )
        );
        assertTrue(SpicePluginHealthService.parseToolDirectives("").isEmpty());
    }
}
