package io.bdeploy.launcher.cli;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.bdeploy.bhive.BHive;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.audit.Auditor;
import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.cli.ToolBase.ConfiguredCliTool;
import io.bdeploy.common.cli.data.DataTable;
import io.bdeploy.common.cli.data.RenderableResult;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.launcher.LauncherPathProvider;
import io.bdeploy.launcher.LauncherPathProvider.SpecialDirectory;
import io.bdeploy.launcher.LocalClientApplicationSettings;
import io.bdeploy.launcher.LocalClientApplicationSettingsManifest;
import io.bdeploy.launcher.cli.AutostartTool.AutostartConfig;
import io.bdeploy.launcher.cli.ui.browser.workers.AppLauncher;
import io.bdeploy.logging.audit.RollingFileAuditor;

@CliName("autostart")
@Help("A tool which starts all applications which are configured for autostart")
public class AutostartTool extends ConfiguredCliTool<AutostartConfig> {

    public @interface AutostartConfig {

        @Help("Directory where the launcher stores the hive as well as all applications.")
        String homeDir();

        @Help(value = "Write log output to stdout instead of the log file.", arg = false)
        boolean consoleLog() default false;
    }

    public AutostartTool() {
        super(AutostartConfig.class);
    }

    @Override
    protected RenderableResult run(AutostartConfig config) {
        String homeDirString = config.homeDir();
        if (homeDirString == null) {
            throw new IllegalStateException("Missing --homeDir argument");
        }

        LauncherPathProvider lpp = new LauncherPathProvider(Path.of(homeDirString));
        Path homeDir = lpp.get(SpecialDirectory.HOME);
        Path logsDir = lpp.get(SpecialDirectory.LOGS);
        Path bhiveDir = lpp.get(SpecialDirectory.BHIVE);

        if (!config.consoleLog()) {
            // Always log into logs directory
            LauncherLoggingContextDataProvider.setLogDir(logsDir.toString());
            LauncherLoggingContextDataProvider.setLogFileBaseName("autostart");
        }

        // Try to get an user-area if the home is readonly
        Path auditorPath = PathHelper.isReadOnly(homeDir) ? ClientPathHelper.getUserAreaOrThrow() : bhiveDir;
        Auditor auditor = RollingFileAuditor.getFactory().apply(auditorPath);

        List<String> emptyArgs = List.of();
        Map<String, String> errors = new HashMap<>();
        try (BHive hive = new BHive(bhiveDir.toUri(), auditor, new ActivityReporter.Null())) {
            new ClientSoftwareManifest(hive).list().parallelStream()//
                    .filter(appConfig -> appConfig.clickAndStart != null)//
                    .filter(appConfig -> {
                        LocalClientApplicationSettings settings = new LocalClientApplicationSettingsManifest(hive).read();
                        Boolean autostartEnabled = settings.getAutostartEnabled(appConfig.clickAndStart);
                        if (autostartEnabled != null) {
                            return autostartEnabled;
                        }
                        return appConfig.metadata.autostart;
                    })//
                    .forEach(appConfig -> {
                        AppLauncher launcher = new AppLauncher(homeDir, appConfig, emptyArgs);
                        try {
                            launcher.execute();
                            launcher.get(15, TimeUnit.SECONDS);
                        } catch (ExecutionException e) {
                            errors.put(appConfig.metadata.appName, e.getMessage());
                        } catch (TimeoutException e) {
                            errors.put(appConfig.metadata.appName, "Timeout");
                        } catch (InterruptedException e) {
                            errors.put(appConfig.metadata.appName, "Interrupted");
                            Thread.currentThread().interrupt();
                        }
                    });
        }

        if (errors.isEmpty()) {
            return createSuccess();
        }

        DataTable resultTable = createDataTable();
        resultTable.setCaption("Errors occurred during autostart");
        resultTable.column("Application", 20).column("Message", 200);

        for (Entry<String, String> entry : errors.entrySet()) {
            resultTable.row().cell(entry.getKey()).cell(entry.getValue()).build();
        }

        return resultTable;
    }
}
