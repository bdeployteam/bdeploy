package io.bdeploy.launcher.cli;

import java.nio.file.Path;

import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.cli.ToolBase.ConfiguredCliTool;
import io.bdeploy.common.cli.data.RenderableResult;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.launcher.cli.BrowserTool.BrowserConfig;
import io.bdeploy.launcher.cli.ui.browser.BrowserDialog;

@CliName("browser")
@Help("A tool which shows all locally installed applications")
public class BrowserTool extends ConfiguredCliTool<BrowserConfig> {

    public BrowserTool() {
        super(BrowserConfig.class);
    }

    public @interface BrowserConfig {

        @Help("Directory where the launcher stores the hive as well as all applications.")
        String homeDir();

    }

    @Override
    protected RenderableResult run(BrowserConfig config) {
        Path rootDir = PathHelper.ofNullableStrig(config.homeDir());
        if (rootDir == null) {
            throw new IllegalStateException("Missing --homeDir argument");
        }

        // Try to get a user-area if the root is readonly
        Path userArea = null;
        if (PathHelper.isReadOnly(rootDir)) {
            userArea = ClientPathHelper.getUserArea();
            if (userArea == null || PathHelper.isReadOnly(userArea)) {
                throw new IllegalStateException("The user area '" + userArea + "' does not exist or cannot be modified.");
            }
        }

        BrowserDialog dialog = new BrowserDialog(rootDir, userArea);
        dialog.setVisible(true);
        dialog.searchApps();
        dialog.waitForExit();

        return null;
    }

}
