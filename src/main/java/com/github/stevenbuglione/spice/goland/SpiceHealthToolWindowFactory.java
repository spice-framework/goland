package com.github.stevenbuglione.spice.goland;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import java.awt.BorderLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;

/** Creates the actionable, non-blocking Spice health tool window. */
public final class SpiceHealthToolWindowFactory
        implements ToolWindowFactory, DumbAware {
    @Override
    public void createToolWindowContent(
            @NotNull Project project,
            @NotNull ToolWindow toolWindow
    ) {
        SpicePluginHealthService health =
                SpicePluginHealthService.getInstance(project);
        Disposable contentLifetime = Disposer.newDisposable(
                "Spice health content"
        );
        JBTextArea text = new JBTextArea();
        text.setEditable(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setBackground(JBColor.PanelBackground);

        JButton refresh = new JButton("Refresh");
        Runnable render = () -> {
            text.setText(health.snapshot().render());
            text.setCaretPosition(0);
            refresh.setEnabled(!health.isRefreshing());
            refresh.setText(
                    health.isRefreshing() ? "Refreshing…" : "Refresh"
            );
        };
        refresh.addActionListener(event -> {
            health.refresh();
            render.run();
        });

        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.add(new JBScrollPane(text), BorderLayout.CENTER);
        panel.add(refresh, BorderLayout.SOUTH);
        Content content = ContentFactory.getInstance()
                .createContent(panel, "", false);
        content.setDisposer(contentLifetime);
        toolWindow.getContentManager().addContent(content);
        health.addListener(render, contentLifetime);
        render.run();
        health.refresh();
    }
}
