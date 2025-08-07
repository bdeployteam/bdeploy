package io.bdeploy.launcher.cli;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ProcessBuilder.Redirect;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import io.bdeploy.api.product.v1.impl.ScopedManifestKey;
import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.BHiveTransactions.Transaction;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.op.DirectoryLockOperation;
import io.bdeploy.bhive.op.ExportOperation;
import io.bdeploy.bhive.op.PruneOperation;
import io.bdeploy.bhive.op.remote.FetchOperation;
import io.bdeploy.bhive.op.remote.TransferStatistics;
import io.bdeploy.bhive.remote.RemoteBHive;
import io.bdeploy.bhive.util.StorageHelper;
import io.bdeploy.bhive.util.VersionComparator;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.ActivityReporter.Activity;
import io.bdeploy.common.Version;
import io.bdeploy.common.audit.Auditor;
import io.bdeploy.common.cfg.Configuration.EnvironmentFallback;
import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cfg.Configuration.RemainingArguments;
import io.bdeploy.common.cfg.Configuration.Validator;
import io.bdeploy.common.cfg.ExistingPathValidator;
import io.bdeploy.common.cli.ToolBase;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.cli.ToolBase.ConfiguredCliTool;
import io.bdeploy.common.cli.data.RenderableResult;
import io.bdeploy.common.util.ExceptionHelper;
import io.bdeploy.common.util.FormatHelper;
import io.bdeploy.common.util.MdcLogger;
import io.bdeploy.common.util.OsHelper;
import io.bdeploy.common.util.OsHelper.OperatingSystem;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.common.util.StreamHelper;
import io.bdeploy.common.util.TemplateHelper;
import io.bdeploy.common.util.Threads;
import io.bdeploy.common.util.VariableResolver;
import io.bdeploy.common.util.VersionHelper;
import io.bdeploy.common.util.ZipHelper;
import io.bdeploy.interfaces.UpdateHelper;
import io.bdeploy.interfaces.configuration.dcu.ApplicationConfiguration;
import io.bdeploy.interfaces.configuration.instance.ClientApplicationConfiguration;
import io.bdeploy.interfaces.configuration.pcu.ProcessConfiguration;
import io.bdeploy.interfaces.descriptor.application.ApplicationBrandingDescriptor;
import io.bdeploy.interfaces.descriptor.application.ApplicationDescriptor;
import io.bdeploy.interfaces.descriptor.application.ApplicationExitCodeDescriptor;
import io.bdeploy.interfaces.descriptor.application.ProcessControlDescriptor.ApplicationDirectoryMode;
import io.bdeploy.interfaces.descriptor.client.ClickAndStartDescriptor;
import io.bdeploy.interfaces.remote.CommonRootResource;
import io.bdeploy.interfaces.remote.MasterNamedResource;
import io.bdeploy.interfaces.remote.MasterRootResource;
import io.bdeploy.interfaces.remote.MinionStatusResource;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.launcher.ClientPathHelper;
import io.bdeploy.launcher.LauncherPathProvider;
import io.bdeploy.launcher.LauncherPathProvider.SpecialDirectory;
import io.bdeploy.launcher.cli.LauncherTool.LauncherConfig;
import io.bdeploy.launcher.cli.branding.LauncherSplash;
import io.bdeploy.launcher.cli.branding.LauncherSplashReporter;
import io.bdeploy.launcher.cli.scripts.LocalScriptHelper;
import io.bdeploy.launcher.cli.scripts.impl.LocalFileAssocScriptHelper;
import io.bdeploy.launcher.cli.scripts.impl.LocalStartScriptHelper;
import io.bdeploy.launcher.cli.ui.MessageDialogs;
import io.bdeploy.launcher.cli.ui.TextAreaDialog;
import io.bdeploy.logging.audit.RollingFileAuditor;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status.Family;

@CliName("launcher")
@Help("A tool which launches an application described by a '.bdeploy' file")
public class LauncherTool extends ConfiguredCliTool<LauncherConfig> {

    private static final Logger log = LoggerFactory.getLogger(LauncherTool.class);

    /** Lock files should contain the writing PID. In case the process no longer exists, the lock file is invalid. */
    private static final Supplier<String> LOCK_CONTENT = () -> Long.toString(ProcessHandle.current().pid());

    /** Validator will check whether the writing PID of the lock file is still there. */
    private static final Predicate<String> LOCK_VALIDATOR = pid -> ProcessHandle.of(Long.parseLong(pid)).isPresent();

    /** Name of the file containing the ID of the exported configuration version */
    private static final String CONFIG_DIR_CHECK_FILE = ".cfgv";

    /**
     * Environment variable that is set in case that one launcher delegates launching to another (older) one. When this is set,
     * the delegated launcher will not perform any update handling. Sample: User want to launch an application that requires an
     * older launcher. In that case the following processes are involved:
     *
     * <pre>
     *      Native Launcher ---> Java Launcher ---> Native Launcher 2 ---> Java Launcher 2 ---> Application
     * </pre>
     *
     * If the <tt>Application</tt> terminates then the <tt>Java Launcher 2</tt> evaluates the exit code and maps the application
     * specific exit code to our internal update code if required. The <tt>Native Launcher 2</tt> and the <tt>Java Launcher</tt>
     * will just forward the exit code. The <tt>Native Launcher</tt> will perform the update and restart. The <tt>Application</tt>
     * is again started by the responsible launcher.
     */
    private static final String BDEPLOY_DELEGATE = "BDEPLOY_DELEGATE";

    public @interface LauncherConfig {

        @Help("Launch file (*.bdeploy). This can be given directly to the executable as single argument as well.")
        @Validator(ExistingPathValidator.class)
        String launch();

        @Help("Directory where the launcher stores the hive as well as all applications.")
        @EnvironmentFallback("BDEPLOY_INTERNAL_HOMEDIR")
        String homeDir();

        @Help("Set by the launcher script to determine the directory where to put updates for automatic application.")
        @EnvironmentFallback("BDEPLOY_INTERNAL_UPDATEDIR")
        String updateDir();

        @Help(value = "Additional command line arguments for the application. The arguments must be a Base64 encoded JSON list.")
        String appendArgs();

        @RemainingArguments
        String[] remainingArgs();

        @Help(value = "Makes the launcher quit immediately after updating and launching the application.", arg = false)
        boolean dontWait() default false;

        @Help(value = "Opens a dialog that allows to modify the arguments passed to the application.", arg = false)
        boolean customizeArgs() default false;

        @Help(value = "Update the launcher and application and then terminate without launching the application.", arg = false)
        boolean updateOnly() default false;

        @Help(value = "Run the launcher in unattended mode where no splash screen and error dialog is shown.", arg = false)
        boolean unattended() default false;

        @Help(value = "Disables all system changes during application installation")
        boolean noSystemChanges() default false;

        @Help(value = "Run the launcher without showing a splash screen. Error messages however are still shown.", arg = false)
        boolean noSplash() default false;

        @Help(value = "Write log output to stdout instead of the log file.", arg = false)
        boolean consoleLog() default false;
    }

    /** The currently running launcher version */
    private final Version runningVersion = VersionHelper.getVersion();

    /** Configuration passed to the tool */
    private LauncherConfig config;

    /** The home-directory for the hive */
    private Path homeDir;

    /** The path where to store updates */
    private Path updateDir;

    /** Path where the hive is stored */
    private Path bhiveDir;

    /** Path where the launched app is stored */
    private Path appDir;

    /** Path where the pooled products and artifacts are stored */
    private Path poolDir;

    /** Path where the start scripts are stored */
    private Path startScriptsDir;

    /** Path where the file association scripts are stored */
    private Path fileAssocScriptsDir;

    /** The user-directory for the hive */
    private Path userArea;

    /** The {@link LauncherPathProvider} for this {@link LauncherTool} */
    private LauncherPathProvider lpp;

    /** The launch descriptor */
    private ClickAndStartDescriptor clickAndStart;

    /** Configuration about the launched application */
    private ClientApplicationConfiguration clientAppCfg;

    /** Indicates whether or not the home directory is read-only */
    private boolean readOnlyHomeDir;

    public LauncherTool() {
        super(LauncherConfig.class);
    }

    @Override
    protected RenderableResult run(LauncherConfig config) {
        Auditor auditor = null;
        try {
            doInit(config);

            // Show splash and progress of operations
            LauncherSplash splash = new LauncherSplash(appDir);
            if (!config.unattended() && !config.noSplash()) {
                splash.show();
            }

            // Write audit logs to the user area if set
            if (userArea != null) {
                auditor = RollingFileAuditor.getFactory().apply(userArea);
            }

            // Update and launch
            doLaunch(auditor, splash, config.noSystemChanges());
        } catch (CancellationException ex) {
            log.info("Launching has been canceled by the user.", ex);
            doExit(-1);
        } catch (SoftwareUpdateException ex) {
            log.error("Software update could not be installed.", ex);
            if (config.unattended()) {
                helpAndFail(ex.getMessage());
            }
            MessageDialogs.showUpdateRequired(clickAndStart, ex);
        } catch (Exception ex) {
            log.error("Failed to launch application.", ex);
            if (config.unattended()) {
                helpAndFail(ex.getMessage());
            }
            MessageDialogs.showLaunchFailed(clickAndStart, ex);
        } finally {
            if (auditor != null) {
                auditor.close();
            }
        }
        return null;
    }

    /** Initializes all parameters based on the given configuration. */
    private void doInit(LauncherConfig config) {
        if (config.launch() == null) {
            throw new IllegalStateException("Missing --launch argument");
        }
        if (config.homeDir() == null) {
            throw new IllegalStateException("Missing --homeDir argument");
        }
        this.config = config;

        // Check where to put local data.
        homeDir = Paths.get(config.homeDir()).toAbsolutePath();
        updateDir = PathHelper.ofNullableStrig(config.updateDir());
        lpp = new LauncherPathProvider(homeDir);

        // Setup logging into files.
        if (!config.consoleLog()) {
            // Always log into logs directory.
            LauncherLoggingContextDataProvider.setLogDir(lpp.get(SpecialDirectory.LOGS).toString());
            LauncherLoggingContextDataProvider.setLogFileBaseName("launcher");
        }

        // Check for inconsistent file and folder permissions.
        Path versionsFile = lpp.get(SpecialDirectory.LAUNCHER).resolve("version.properties");
        readOnlyHomeDir = PathHelper.isReadOnly(homeDir, versionsFile);

        // Check that the launcher was not moved.
        validateBDeployHome();

        // Try to get an user-area if the home is readonly
        if (readOnlyHomeDir) {
            userArea = ClientPathHelper.getUserAreaOrThrow();
        }

        Path descriptorFile = Paths.get(config.launch());
        try (InputStream is = Files.newInputStream(descriptorFile)) {
            clickAndStart = StorageHelper.fromStream(is, ClickAndStartDescriptor.class);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + config.launch(), e);
        }

        lpp.setApplicationId(clickAndStart.applicationId);
        bhiveDir = lpp.get(SpecialDirectory.BHIVE);
        appDir = lpp.get(SpecialDirectory.APP);
        poolDir = lpp.get(SpecialDirectory.MANIFEST_POOL);
        startScriptsDir = lpp.get(SpecialDirectory.START_SCRIPTS);
        fileAssocScriptsDir = lpp.get(SpecialDirectory.FILE_ASSOC_SCRIPTS);

        // Enrich log messages with the application that is about to be launched
        String pid = String.format("PID: %1$5s", ProcessHandle.current().pid());
        MDC.put(MdcLogger.MDC_NAME, pid + " | App: " + clickAndStart.applicationId);
    }

    /** Validate that homeDir is specified the same way as it was the first time. */
    private void validateBDeployHome() {
        Path bdeployHomePath = homeDir.resolve("bdeploy.home");
        if (bdeployHomePath.toFile().exists()) {
            validateBDeployHome(bdeployHomePath);
        } else if (!readOnlyHomeDir) {
            saveBDeployHome(bdeployHomePath);
        }
    }

    /** Validate content of bdeploy.home file is the same as current value of homeDir. */
    private void validateBDeployHome(Path bdeployHomePath) {
        try {
            Path storedPath = Path.of(Files.readString(bdeployHomePath, StandardCharsets.UTF_8));
            if (!homeDir.equals(storedPath)) {
                throw new IllegalStateException(
                        "BDeploy home directory has changed. Expected: " + storedPath + " Got: " + homeDir);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read from " + bdeployHomePath.toAbsolutePath(), e);
        }
    }

    /** Save homeDir value into bdeploy.home file. */
    private void saveBDeployHome(Path bdeployHomePath) {
        try {
            Files.write(bdeployHomePath, config.homeDir().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to save BDEPLOY_HOME value into file", e);
        }
    }

    /** Launches an application after installing updates. */
    private void doLaunch(Auditor auditor, LauncherSplash splash, boolean noSystemChanges) {
        log.info("Launcher version '{}' started.", VersionHelper.getVersionAsString());
        log.info("Home directory: {}", homeDir);
        if (readOnlyHomeDir) {
            log.info("User directory: {}", userArea);
            log.info("Home directory is readonly. No new applications or updates can be installed.");
        }

        LauncherSplashReporter reporter = new LauncherSplashReporter(splash);
        try (BHive hive = new BHive(bhiveDir.toUri(), auditor != null ? auditor : RollingFileAuditor.getFactory().apply(bhiveDir),
                reporter)) {

            // Provide callback to detect stale locks
            hive.setLockContentSupplier(LOCK_CONTENT);
            hive.setLockContentValidator(LOCK_VALIDATOR);

            // Clean stale transactions once the content supplier and validator are available.
            cleanStaleTransactions(hive);

            // Retrieve the client application configuration.
            ClientSoftwareConfiguration clientSoftwareConfiguration = null;
            boolean offlineMode;
            try (Activity info = reporter.start("Loading meta-data...")) {
                log.info("Fetching client application configuration from server...");
                clientAppCfg = ResourceProvider.getVersionedResource(clickAndStart.host, MasterRootResource.class, null)
                        .getNamedMaster(clickAndStart.groupId)
                        .getClientConfiguration(clickAndStart.instanceId, clickAndStart.applicationId);

                log.info("Successfully fetched client application configuration from server.");
                offlineMode = false;
            } catch (Exception e) {
                Throwable rootCause = ExceptionHelper.getRootCause(e);
                if (!(rootCause instanceof ConnectException) && !(rootCause instanceof UnknownHostException)) {
                    throw e;
                }

                log.info("Failed to fetch client application configuration from server. Reading local configuration...");
                clientSoftwareConfiguration = new ClientSoftwareManifest(hive).readNewest(clickAndStart.applicationId, false);
                if (clientSoftwareConfiguration == null) {
                    throw new IllegalStateException("Failed to read local client software configuration.", e);
                }
                clientAppCfg = clientSoftwareConfiguration.clientAppCfg;
                if (clientAppCfg == null) {
                    throw new IllegalStateException("Failed to read local client application configuration.", e);
                }
                if (!clientAppCfg.appDesc.processControl.offlineStartAllowed) {
                    throw new IllegalStateException("Aborting launch because offline start is not enabled for the application.",
                            e);
                }

                log.info("Successfully read local client application configuration.");
                offlineMode = true;
            }

            // Check for and install launcher updates if necessary.
            Version serverVersion = null;
            if (offlineMode) {
                log.info("Continuing launch in offline mode...");
            } else {
                log.info("Continuing launch in online mode...");
                serverVersion = getServerVersion(clickAndStart);

                doSelfUpdate(hive, reporter, serverVersion); // This call does NOT return if a launcher update is deemed necessary!

                if (serverVersion.compareTo(VersionHelper.parse("4.3.0")) >= 0) {
                    // The source server could have been migrated/converted to node. in this case, display a message.
                    MinionStatusResource msr = ResourceProvider.getVersionedResource(clickAndStart.host,
                            MinionStatusResource.class, null);
                    if (!msr.getStatus().config.master) {
                        log.info("Launch aborted because minion is not a master: {}", clickAndStart.host.getUri());
                        MessageDialogs.showServerIsNode();
                        return;
                    }
                }

                doInstall(hive, reporter, splash, auditor, serverVersion, noSystemChanges);
            }

            // Launch the application
            Process process = null;
            if (!config.updateOnly()) {
                try (Activity info = reporter.start("Launching...")) {
                    process = launchApplication(clientAppCfg, offlineMode, serverVersion);
                }
                log.info("Application successfully launched. PID={}", process.pid());
            }

            reporter.stop();
            splash.dismiss();

            if (!readOnlyHomeDir) {
                log.info("Cleaning unused launchers and applications...");
                doExecuteLocked(hive, reporter, () -> {
                    new ClientCleanup(hive, lpp).run();
                    return null;
                });
            }

            if (config.updateOnly() || config.dontWait()) {
                log.info("Detaching and terminating.");
                return;
            }

            int exitCode = -1;
            if (process != null) {
                try {
                    exitCode = process.waitFor();
                } catch (InterruptedException e) {
                    log.warn("Waiting for application exit interrupted.");
                    Thread.currentThread().interrupt();
                    exitCode = -1;
                }
            } else {
                log.warn("No process handle found after launching.");
            }

            // The delegated launcher launcher has already evaluated the exit code and translated the application specific exit code.
            // We just need to terminate with the given code.
            if (clientAppCfg == null) {
                log.info("Delegated launcher terminated with exit code {}.", exitCode);
                doExit(exitCode);
                return;
            }

            // Application request an update. We will terminate the launcher so that potential launcher updates are also applied.
            ApplicationDescriptor appDesc = clientAppCfg.appDesc;
            ApplicationExitCodeDescriptor exitCodes = appDesc.exitCodes;
            if (exitCodes != null && exitCodes.update != null && exitCodes.update == exitCode) {
                log.info("Application signaled that updates should be installed. Restarting...");
                doExit(UpdateHelper.CODE_RESTART);
            }
            log.info("Application terminated with exit code {}.", exitCode);
            doExit(exitCode);
        }
    }

    /**
     * Cleans up any past transactions which have been aborted for whatever reason.
     * <p>
     * A transaction is stale if the process which created the transaction is no longer alive, but the marker database backing the
     * transaction still exists. This would (worst case) allow damaged objects to exist in the object database without any means
     * to clean them up.
     */
    private void cleanStaleTransactions(BHive hive) {
        // Only possible if the home directory is not read-only.
        if (!readOnlyHomeDir) {
            // Check for stale transactions in the hive.
            log.debug("Checking for stale transactions.");
            long stale = hive.getTransactions().cleanStaleTransactions();
            if (stale > 0) {
                // Stale transactions might leave damaged objects around, force a prune to get rid of them.
                // There is no need to perform a full check of all existing manifests though, a stale transaction cannot cause them to break.
                log.warn("{} stale transactions found, forcing prune of hive", stale);

                SortedMap<ObjectId, Long> result = hive.execute(new PruneOperation());
                long sum = result.values().stream().collect(Collectors.summarizingLong(x -> x)).getSum();
                if (sum > 0) {
                    log.info("Removed {} objects (Size={}).", result.size(), FormatHelper.formatFileSize(sum));
                }
            } else {
                log.debug("No stale transactions found, continue.");
            }
        }
    }

    /**
     * Updates the launcher if there is a new version available. Note that this method <b>does not return</b> if a launcher update
     * is deemed necessary.
     */
    private void doSelfUpdate(BHive hive, LauncherSplashReporter reporter, Version serverVersion) {
        if (VersionHelper.isRunningUndefined()) {
            log.warn("Skipping self update. The local running version is not defined.");
            return;
        }
        log.info("Checking for launcher updates...");
        doExecuteLocked(hive, reporter, () -> {
            long start = System.currentTimeMillis();
            Entry<Version, Key> requiredLauncher = getLatestLauncherVersion(reporter, serverVersion);
            log.info("Took {}ms to calculate the latest launcher version.", System.currentTimeMillis() - start);
            doCheckForLauncherUpdate(hive, reporter, requiredLauncher);
            return requiredLauncher;
        });
    }

    /** Returns the latest available launcher version. */
    private Map.Entry<Version, Key> getLatestLauncherVersion(ActivityReporter reporter, Version serverVersion) {
        // Fetch all versions and filter out the one that corresponds to the server version
        OperatingSystem runningOs = OsHelper.getRunningOs();
        String launcherKey = UpdateHelper.SW_META_PREFIX + UpdateHelper.SW_LAUNCHER;
        NavigableMap<Version, Key> versions = new TreeMap<>(VersionComparator.NEWEST_LAST);
        try (RemoteBHive rbh = RemoteBHive.forService(clickAndStart.host, null, reporter);
                Activity check = reporter.start("Fetching launcher versions....")) {

            boolean serverIsUndefined = VersionHelper.isUndefined(serverVersion);

            SortedMap<Key, ObjectId> launchers = rbh.getManifestInventory(launcherKey);
            for (Key launcher : launchers.keySet()) {
                ScopedManifestKey smk = ScopedManifestKey.parse(launcher);
                if (smk == null || smk.getOperatingSystem() != runningOs) {
                    continue;
                }

                // Filter out all versions that do not match the current server version. We do this only in case that the
                // server provides a valid version. Older servers do not have this API and thus cannot tell us their version.
                Version version = VersionHelper.tryParse(smk.getTag());
                if (!serverIsUndefined && !VersionHelper.equals(serverVersion, version)) {
                    continue;
                }
                versions.put(version, launcher);
            }
        }

        // Last version is the newest one
        if (versions.size() > 0) {
            return versions.lastEntry();
        }

        // If no launcher is installed we simply use the currently running version
        String scopedName = ScopedManifestKey.createScopedName(launcherKey, runningOs);
        versions.put(runningVersion, new Manifest.Key(scopedName, runningVersion.toString()));
        return versions.firstEntry();
    }

    /**
     * Checks for updates and installs them if required. Note that this method <b>does not return</b> if a launcher update is
     * deemed necessary.
     */
    private void doCheckForLauncherUpdate(BHive hive, ActivityReporter reporter, Map.Entry<Version, Key> requiredLauncher) {
        Version latestVersion = requiredLauncher.getKey();
        if (latestVersion.compareTo(runningVersion) <= 0) {
            log.info("No launcher updates are available.");
            return;
        }
        log.info("Launcher updates found. Updating from {} to {}", runningVersion, latestVersion);

        // Check if we have write permissions to install the update
        if (readOnlyHomeDir) {
            throw new SoftwareUpdateException("launcher",
                    "Installed=" + runningVersion.toString() + " Available=" + latestVersion.toString());
        }

        // In case that another launcher has launched us then we do not perform any updates.
        // We just exit with the update code so that the outer launcher can do all required tasks.
        if (System.getenv(BDEPLOY_DELEGATE) != null) {
            log.info("Update of launcher required. Delegating to parent to do this...");
            doExit(UpdateHelper.CODE_RESTART);
            return;
        }

        Key launcher = requiredLauncher.getValue();
        Path updateMarker = updateDir.resolve(".updating");
        try (Activity updating = reporter.start("Updating Launcher")) {
            waitForLauncherUpdates(updateMarker);

            try (Transaction t = hive.getTransactions().begin()) {
                // Found a newer version to install.
                hive.execute(new FetchOperation().addManifest(launcher).setRemote(clickAndStart.host));
            }

            // Write to target directory.
            Path next = UpdateHelper.prepareUpdateDirectory(updateDir);
            hive.execute(new ExportOperation().setManifest(launcher).setTarget(next));

            // Create a marker for others which will gain the lock after we exit for restart.
            Files.createFile(updateMarker);

            // Signal that a new update is available.
            log.info("Restarting...");
            doExit(UpdateHelper.CODE_RESTART);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create update marker", e);
        }
    }

    /** Waits for some time until another launcher has finished installing updates. */
    private static void waitForLauncherUpdates(Path updateMarker) throws IOException {
        // No one is installing updates.
        if (!Files.isRegularFile(updateMarker)) {
            return;
        }

        // Check for a stale update marker
        if (System.currentTimeMillis() - Files.getLastModifiedTime(updateMarker).toMillis() > 60_000) {
            log.warn("Stale update marker found, removing.");
            PathHelper.deleteIfExistsRetry(updateMarker);
            return;
        }

        // Update marker found -> On Windows we just terminate
        // The native launcher cannot install updates while we are running
        if (OsHelper.getRunningOs() == OperatingSystem.WINDOWS) {
            log.info("Found existing update marker. Exiting to allow updates to be installed.");
            doExit(UpdateHelper.CODE_RESTART);
            return;
        }

        // On Linux we wait for some time until the marker disappears
        // The native shell script will remove the marker when finished
        int counter = 0;
        log.info("Waiting while updates are installed.");
        while (counter < 30 && Files.isRegularFile(updateMarker)) {
            Threads.sleep(1000);
            counter++;
        }

        // Terminate and restart in any case
        log.info("Exiting to apply updates.");
        doExit(UpdateHelper.CODE_RESTART);
    }

    private void doInstall(BHive hive, LauncherSplashReporter reporter, LauncherSplash splash, Auditor auditor,
            Version serverVersion, boolean noSystemChanges) {
        // Update splash with the fetched branding information.
        ApplicationBrandingDescriptor branding = clientAppCfg.appDesc.branding;
        if (branding == null) {
            log.info("Client configuration does not contain any branding information.");
        } else {
            if (clientAppCfg.clientImageIcon != null) {
                splash.updateIconImage(clientAppCfg.clientImageIcon);
            }
            if (clientAppCfg.clientSplashData != null) {
                splash.updateSplashImage(clientAppCfg.clientSplashData);
            }
            splash.updateSplashData(branding.splash);
        }

        // Install the application into the pool if necessary.
        doExecuteLocked(hive, reporter, () -> {
            installApplication(hive, splash, reporter, serverVersion, noSystemChanges);
            return null;
        });
    }

    /** Installs the application with all requirements if necessary. */
    private void installApplication(BHive hive, LauncherSplash splash, ActivityReporter reporter, Version serverVersion,
            boolean noSystemChanges) {
        // Update scripts
        if (!noSystemChanges) {
            OperatingSystem os = OsHelper.getRunningOs();
            updateScripts(clientAppCfg, new LocalStartScriptHelper(os, hive, lpp), "start", startScriptsDir);
            updateScripts(clientAppCfg, new LocalFileAssocScriptHelper(os, hive, lpp), "file association", fileAssocScriptsDir);
        }

        // Check if the application has any missing artifacts
        Collection<String> missing = getMissingArtifacts(hive);
        if (missing.isEmpty()) {
            log.info("Application has no missing artifacts.");
            return;
        }

        log.info("Application has missing artifacts: {}", missing);

        // Throw an exception if we do not have write permissions in the directory
        ApplicationConfiguration appCfg = clientAppCfg.appConfig;
        if (readOnlyHomeDir) {
            throw new SoftwareUpdateException(appCfg.name, "Missing parts: " + missing.stream().collect(Collectors.joining(",")));
        }

        // Fetch the application and all the requirements
        try (Activity info = reporter.start("Downloading..."); Transaction t = hive.getTransactions().begin()) {
            log.info("Downloading application...");
            TransferStatistics stats = hive
                    .execute(new FetchOperation().setHiveName(clickAndStart.groupId).setRemote(clickAndStart.host)
                            .addManifest(appCfg.application).addManifest(clientAppCfg.resolvedRequires).setRetryCount(5));
            if (stats.sumManifests == 0) {
                log.info("Local hive already contains all required files.");
            } else {
                log.info("Fetched missing files from server. {}", stats.toLogString());
            }
        }

        // Export the application into the pool
        List<Manifest.Key> applications = new ArrayList<>();
        applications.add(appCfg.application);
        applications.addAll(clientAppCfg.resolvedRequires);

        log.info("Installing application and dependencies into the pool...");
        try (Activity info = reporter.start("Installing...", applications.size())) {
            for (Manifest.Key key : applications) {
                Path target = poolDir.resolve(key.directoryFriendlyName());
                if (Files.isDirectory(target)) {
                    info.worked(1);
                    log.info("{} is already installed.", key);
                    continue;
                }
                log.info("Installing {}", key);
                hive.execute(new ExportOperation().setTarget(target).setManifest(key));
                info.worked(1);
            }
        }

        // Application specific data will be stored in a separate directory.
        PathHelper.mkdirs(appDir);

        // Download and install the current configuration tree if required.
        Path cfgPath = lpp.get(LauncherPathProvider.SpecialDirectory.CONFIG);
        PathHelper.deleteRecursiveRetry(cfgPath); // Get rid of *all* existing configurations

        if (clientAppCfg.configTree != null) {
            downloadAndInstallConfigFiles(clientAppCfg, cfgPath);
        }

        // Store branding information on file-system.
        ApplicationBrandingDescriptor branding = clientAppCfg.appDesc.branding;
        if (branding != null) {
            if (clientAppCfg.clientImageIcon != null) {
                splash.storeIconImage(branding.icon, clientAppCfg.clientImageIcon);
            }
            if (clientAppCfg.clientSplashData != null) {
                splash.storeSplashImage(branding.splash.image, clientAppCfg.clientSplashData);
            }
            splash.storeSplashData(branding);
        }

        // Create the click-and-start file.
        try {
            ClientPathHelper.getOrCreateClickAndStart(lpp, clickAndStart);
        } catch (IOException e) {
            log.error("Initial creation of click-and-start file failed.", e);
        }

        // Protocol the installation.
        ClientSoftwareManifest manifest = new ClientSoftwareManifest(hive);
        ClientSoftwareConfiguration newConfig = new ClientSoftwareConfiguration();
        newConfig.clickAndStart = clickAndStart;
        newConfig.metadata = ClientApplicationDto.create(clickAndStart, clientAppCfg, serverVersion);
        newConfig.clientAppCfg = clientAppCfg;
        newConfig.requiredSoftware.addAll(applications);
        manifest.update(clickAndStart.applicationId, newConfig);

        log.info("Application successfully installed.");
    }

    private void updateScripts(ClientApplicationConfiguration cfg, LocalScriptHelper scriptHelper, String scriptType,
            Path scriptDir) {
        try {
            scriptHelper.createScript(cfg, clickAndStart, false);
        } catch (IOException e) {
            log.error("Failed to create {} script in {}", scriptType, scriptDir, e);
        }
    }

    /**
     * Checks if the application and ALL the required dependencies are already installed. The check is done by verifying that the
     * target directories are existing. No deep verification is done. The returned list indicates which artifacts are missing.
     */
    private Collection<String> getMissingArtifacts(BHive hive) {
        // Application directory must exist
        // NOTE: Directory is created before by the native installer
        Collection<String> missing = new ArrayList<>();
        if (!PathHelper.exists(appDir)) {
            missing.add("Directory: " + clientAppCfg.appConfig.id);
        }

        // The software that we need must be in the pool
        List<Manifest.Key> applications = new ArrayList<>();
        applications.add(clientAppCfg.appConfig.application);
        applications.addAll(clientAppCfg.resolvedRequires);
        for (Manifest.Key app : applications) {
            Path expectedPath = poolDir.resolve(app.directoryFriendlyName());
            if (!PathHelper.exists(expectedPath)) {
                missing.add("Pooled-App: " + app);
            }
        }

        // Configuration files need to be installed in the correct version
        Path cfgPath = lpp.get(LauncherPathProvider.SpecialDirectory.CONFIG);

        if (clientAppCfg.configTree != null) {
            boolean isCurrent = checkForCurrentConfigFiles(clientAppCfg.configTree, cfgPath);

            if (!isCurrent) {
                missing.add("Configuration Files Version: " + clientAppCfg.configTree);
            }
        } else {
            // In case we HAD a configuration directory, we might need to get rid of it.
            boolean hasOldConfig = PathHelper.exists(cfgPath.resolve(CONFIG_DIR_CHECK_FILE));

            if (hasOldConfig) {
                missing.add("Configuration Files must be removed.");
            }
        }

        // Meta-Manifest about the installation must exist and must refer to what the application actually requires.
        ClientSoftwareManifest manifest = new ClientSoftwareManifest(hive);
        ClientSoftwareConfiguration clientConfig = manifest.readNewest(clientAppCfg.appConfig.id, false);

        if (clientConfig == null) {
            missing.add("Meta-Manifest:" + clientAppCfg.appConfig.id);
            return missing;
        }

        // Check that all required applications are listed
        applications.removeAll(clientConfig.requiredSoftware);
        if (!applications.isEmpty()) {
            missing.add("Meta-Manifest-Entry: " + applications);
        }

        if (clientConfig.clientAppCfg == null) {
            if (readOnlyHomeDir) {
                log.warn("Persistent configuration is missing.");
            } else {
                missing.add("Persistent configuration is missing.");
            }
        } else if (!Objects.equals(clientConfig.clientAppCfg.activeTag, clientAppCfg.activeTag)) {
            if (readOnlyHomeDir) {
                log.warn("Persistent configuration is outdated.");
            } else {
                missing.add("Persistent configuration is outdated.");
            }
        }

        return missing;
    }

    private static boolean checkForCurrentConfigFiles(ObjectId configTree, Path cfgPath) {
        Path checkFile = cfgPath.resolve(CONFIG_DIR_CHECK_FILE);
        if (PathHelper.exists(checkFile)) {
            try (InputStream is = Files.newInputStream(checkFile)) {
                List<String> lines = Files.readAllLines(checkFile);

                if (lines.isEmpty()) {
                    return false;
                }

                String check = lines.get(0);
                if (configTree.getId().equals(check)) {
                    return true;
                }
            } catch (Exception e) {
                log.warn("Cannot read check file: " + checkFile, e);
            }
        }
        return false;
    }

    private void downloadAndInstallConfigFiles(ClientApplicationConfiguration clientAppCfg, Path cfgPath) {
        // Contact server, download files and write into configuration directory.
        MasterRootResource master = ResourceProvider.getVersionedResource(clickAndStart.host, MasterRootResource.class, null);
        MasterNamedResource namedMaster = master.getNamedMaster(clickAndStart.groupId);

        Response zipDl = namedMaster.getConfigZipSteam(clickAndStart.instanceId, clickAndStart.applicationId);
        if (zipDl.getStatusInfo().getFamily() != Family.SUCCESSFUL) {
            throw new IllegalStateException("Config File download failed: " + zipDl.getStatusInfo().getStatusCode() + ": "
                    + zipDl.getStatusInfo().getReasonPhrase());
        }

        // Write the download into a temporary file so we can unzip it.
        Path cfgZip = appDir.resolve(clientAppCfg.configTree.getId() + ".zip");
        try (InputStream input = zipDl.readEntity(InputStream.class); OutputStream output = Files.newOutputStream(cfgZip)) {
            StreamHelper.copy(input, output);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Cannot read ZIP response from configuration files request for " + clickAndStart.applicationId, e);
        }

        // Un-zip the downloaded ZIP containing the configuration files.
        ZipHelper.unzip(cfgZip, cfgPath);

        TemplateHelper.processFileTemplates(cfgPath, ResolverHelper.createResolver(lpp, clientAppCfg));

        try {
            // Record the proper ID in the configuration check file.
            Files.write(cfgPath.resolve(CONFIG_DIR_CHECK_FILE), Collections.singletonList(clientAppCfg.configTree.getId()));
        } catch (Exception e) {
            log.warn("Cannot write configuration check file", e);
        }

        // Remove the temporary download file.
        PathHelper.deleteRecursiveRetry(cfgZip);
    }

    /** Launches the client process using the given configuration. */
    private Process launchApplication(ClientApplicationConfiguration clientCfg, boolean offlineMode, Version serverVersion) {
        log.info("Launching application.");
        ApplicationConfiguration appCfg = clientCfg.appConfig;

        if (!offlineMode && VersionHelper.parse("3.6.0").compareTo(serverVersion) < 0) {
            MasterRootResource master = ResourceProvider.getVersionedResource(clickAndStart.host, MasterRootResource.class, null);
            MasterNamedResource namedMaster = master.getNamedMaster(clickAndStart.groupId);
            namedMaster.logClientStart(clickAndStart.instanceId, clickAndStart.applicationId, getHostname("unknown"));
        }

        VariableResolver appSpecificResolvers = ResolverHelper.createResolver(lpp, clientCfg);

        // Create the actual start command and replace all defined variables
        ProcessConfiguration pc = appCfg.renderDescriptor(appSpecificResolvers);
        List<String> command = TemplateHelper.process(pc.start, appSpecificResolvers);

        // Append custom arguments if applicable
        command.addAll(decodeAdditionalArguments(config.appendArgs()));
        if (config.remainingArgs() != null && config.remainingArgs().length > 0) {
            command.addAll(Arrays.asList(config.remainingArgs()));
        }

        // Let the user modify the command-line before launching
        if (config.customizeArgs()) {
            TextAreaDialog dialog = new TextAreaDialog();
            if (!dialog.customize(appCfg.name, command)) {
                throw new CancellationException();
            }
        }
        log.info("Executing {}", command.stream().collect(Collectors.joining(" ")));
        try {
            ProcessBuilder b = new ProcessBuilder(command).redirectError(Redirect.INHERIT).redirectInput(Redirect.INHERIT)
                    .redirectOutput(Redirect.INHERIT);
            if (clientCfg.appDesc.processControl.workingDirectory == ApplicationDirectoryMode.SET) {
                b.directory(appDir.toFile());
            }
            if (pc.startEnv != null) {
                b.environment().putAll(pc.startEnv);
            }
            return b.start();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot start " + appCfg.id, e);
        }
    }

    private static String getHostname(String fallback) {
        String hostname = null;
        try {
            hostname = InetAddress.getLocalHost().getCanonicalHostName();
        } catch (Exception e) {
            // Ignore Exception
        }
        if (hostname == null || "localhost".equalsIgnoreCase(hostname)) {
            hostname = NativeHostnameResolver.getHostname();
        }
        return hostname != null ? hostname : fallback;
    }

    /**
     * Locks the home directory in order to perform the given operation. The lock is not created if the current user does not have
     * the permissions to modify the home directory. If that happens, the operation is executed immediately.
     */
    private <T> T doExecuteLocked(BHive hive, LauncherSplashReporter reporter, Callable<T> runnable) {
        DirectoryLockOperation.LockHandle lockHandle = null;
        if (!readOnlyHomeDir) {
            try (Activity waiting = reporter.start("Waiting for other launchers...")) {
                lockHandle = hive.execute(new DirectoryLockOperation().setDirectory(homeDir)); // This could wait for other launchers.
            }
        }
        log.debug("Entered locked execution mode");
        try {
            return runnable.call();
        } catch (RuntimeException rex) {
            throw rex;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to execute locked operation", ex);
        } finally {
            log.debug("Leaving locked execution mode");
            if (!readOnlyHomeDir && lockHandle != null) {
                lockHandle.unlock();
            }
        }
    }

    /** Decodes the additional arguments that are passed to the application. */
    @SuppressWarnings("unchecked")
    private static Collection<String> decodeAdditionalArguments(String appendArgs) {
        if (appendArgs == null || appendArgs.isBlank()) {
            return Collections.emptyList();
        }
        byte[] decodedBytes = Base64.decodeBase64(appendArgs);
        return StorageHelper.fromRawBytes(decodedBytes, List.class);
    }

    /** Returns the server version or null in case that the version cannot be determined. */
    public static Version getServerVersion(ClickAndStartDescriptor descriptor) {
        try {
            return ResourceProvider.getVersionedResource(descriptor.host, CommonRootResource.class, null).getVersion();
        } catch (Exception ex) {
            if (log.isDebugEnabled()) {
                log.debug("Cannot determine server version.", ex);
            }
            return VersionHelper.UNDEFINED;
        }
    }

    /**
     * Terminates the VM with the given exit code.
     * Careful: in tests will not exit but throw in case of non-zero and <b>continue</b> in case of zero exit code.
     */
    private static void doExit(int exitCode) {
        // if we are in test mode, System.exit will exit the JVM running the test, so we don't do that.
        if (ToolBase.isTestMode()) {
            if (exitCode != 0) {
                throw new IllegalStateException("Non-zero exit in test mode: " + exitCode);
            }
            return; // in test - keep going.
        }

        System.exit(exitCode);
    }
}
