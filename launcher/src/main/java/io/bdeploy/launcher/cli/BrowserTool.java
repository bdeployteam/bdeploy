package io.bdeploy.launcher.cli;

import java.nio.file.Path;

import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.cli.ToolBase.ConfiguredCliTool;
import io.bdeploy.common.cli.data.RenderableResult;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.launcher.LauncherPathProvider;
import io.bdeploy.launcher.LauncherPathProvider.SpecialDirectory;
import io.bdeploy.launcher.cli.BrowserTool.BrowserConfig;
import io.bdeploy.launcher.cli.ui.browser.BrowserDialog;

@CliName("browser")
@Help("A tool which shows all locally installed applications")
public class BrowserTool extends ConfiguredCliTool<BrowserConfig> {

    public @interface BrowserConfig {

        @Help("Directory where the launcher stores the hive as well as all applications.")
        String homeDir();

        @Help(value = "Write log output to stdout instead of the log file.", arg = false)
        boolean consoleLog() default false;
    }

    public BrowserTool() {
        super(BrowserConfig.class);
    }

    @Override
    protected RenderableResult run(BrowserConfig config) {
        String homeDirString = config.homeDir();
        if (homeDirString == null) {
            throw new IllegalStateException("Missing --homeDir argument");
        }

        LauncherPathProvider lpp = new LauncherPathProvider(Path.of(homeDirString));
        Path homeDir = lpp.get(SpecialDirectory.HOME);
        Path logsDir = lpp.get(SpecialDirectory.LOGS);

        if (!config.consoleLog()) {
            // Always log into logs directory
            LauncherLoggingContextDataProvider.setLogDir(logsDir.toString());
            LauncherLoggingContextDataProvider.setLogFileBaseName("browser");
        }

        // Try to get an user-area if the home is readonly
        Path userArea = PathHelper.isReadOnly(homeDir) ? ClientPathHelper.getUserAreaOrThrow() : null;

        BrowserDialog dialog = new BrowserDialog(lpp, userArea);
        dialog.setVisible(true);
        dialog.searchApps();
        dialog.waitForExit();

        return null;
    }
}
