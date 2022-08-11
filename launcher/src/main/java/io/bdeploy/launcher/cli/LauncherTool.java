package io.bdeploy.launcher.cli;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ProcessBuilder.Redirect;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Set;
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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.bdeploy.api.product.v1.impl.ScopedManifestKey;
import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.BHiveTransactions.Transaction;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.op.CopyOperation;
import io.bdeploy.bhive.op.ExportOperation;
import io.bdeploy.bhive.op.LockDirectoryOperation;
import io.bdeploy.bhive.op.ObjectListOperation;
import io.bdeploy.bhive.op.PruneOperation;
import io.bdeploy.bhive.op.ReleaseDirectoryLockOperation;
import io.bdeploy.bhive.op.remote.FetchOperation;
import io.bdeploy.bhive.op.remote.TransferStatistics;
import io.bdeploy.bhive.remote.RemoteBHive;
import io.bdeploy.bhive.util.StorageHelper;
import io.bdeploy.bhive.util.VersionComparator;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.ActivityReporter.Activity;
import io.bdeploy.common.Version;
import io.bdeploy.common.audit.Auditor;
import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cfg.Configuration.Validator;
import io.bdeploy.common.cfg.ExistingPathValidator;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.cli.ToolBase.ConfiguredCliTool;
import io.bdeploy.common.cli.data.RenderableResult;
import io.bdeploy.common.util.FormatHelper;
import io.bdeploy.common.util.MdcLogger;
import io.bdeploy.common.util.OsHelper;
import io.bdeploy.common.util.OsHelper.OperatingSystem;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.common.util.StreamHelper;
import io.bdeploy.common.util.TemplateHelper;
import io.bdeploy.common.util.Threads;
import io.bdeploy.common.util.VersionHelper;
import io.bdeploy.common.util.ZipHelper;
import io.bdeploy.interfaces.UpdateHelper;
import io.bdeploy.interfaces.configuration.dcu.ApplicationConfiguration;
import io.bdeploy.interfaces.configuration.instance.ClientApplicationConfiguration;
import io.bdeploy.interfaces.configuration.pcu.ProcessConfiguration;
import io.bdeploy.interfaces.descriptor.application.ApplicationBrandingDescriptor;
import io.bdeploy.interfaces.descriptor.application.ApplicationDescriptor;
import io.bdeploy.interfaces.descriptor.application.ApplicationExitCodeDescriptor;
import io.bdeploy.interfaces.descriptor.client.ClickAndStartDescriptor;
import io.bdeploy.interfaces.remote.CommonRootResource;
import io.bdeploy.interfaces.remote.MasterNamedResource;
import io.bdeploy.interfaces.remote.MasterRootResource;
import io.bdeploy.interfaces.remote.MinionStatusResource;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.interfaces.variables.ApplicationParameterProvider;
import io.bdeploy.interfaces.variables.ApplicationParameterValueResolver;
import io.bdeploy.interfaces.variables.ApplicationVariableResolver;
import io.bdeploy.interfaces.variables.CompositeResolver;
import io.bdeploy.interfaces.variables.DelayedVariableResolver;
import io.bdeploy.interfaces.variables.DeploymentPathProvider;
import io.bdeploy.interfaces.variables.DeploymentPathProvider.SpecialDirectory;
import io.bdeploy.interfaces.variables.DeploymentPathResolver;
import io.bdeploy.interfaces.variables.EnvironmentVariableResolver;
import io.bdeploy.interfaces.variables.InstanceAndSystemVariableResolver;
import io.bdeploy.interfaces.variables.InstanceVariableResolver;
import io.bdeploy.interfaces.variables.ManifestRefPathProvider;
import io.bdeploy.interfaces.variables.ManifestSelfResolver;
import io.bdeploy.interfaces.variables.ManifestVariableResolver;
import io.bdeploy.interfaces.variables.OsVariableResolver;
import io.bdeploy.interfaces.variables.ParameterValueResolver;
import io.bdeploy.launcher.cli.LauncherTool.LauncherConfig;
import io.bdeploy.launcher.cli.branding.LauncherSplash;
import io.bdeploy.launcher.cli.branding.LauncherSplashReporter;
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

    /** name of the file containing the ID of the exported configuration version */
    private static final String CONFIG_DIR_CHECK_FILE = ".cfgv";

    /**
     * Environment variable that is set in case that one launcher delegates launching to another (older) one.
     * When this is set, the delegated launcher will not perform any update handling. Sample: User want to launch an application
     * that requires an older launcher. In that case the following processes are involved:
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
        String homeDir();

        @Help("Directory where the launcher stores essential runtime data.")
        String userArea();

        @Help("Set by the launcher script to determine the directory where to put updates for automatic application.")
        String updateDir();

        @Help(value = "Additional command line arguments for the application. The arguments must be a Base64 encoded JSON list.")
        String appendArgs();

        @Help(value = "Makes the launcher quit immediately after updating and launching the application.", arg = false)
        boolean dontWait() default false;

        @Help(value = "Opens a dialog that allows to modify the arguments passed to the application.", arg = false)
        boolean customizeArgs() default false;

        @Help(value = "Update the launcher and application and then terminate without launching the application.", arg = false)
        boolean updateOnly() default false;

        @Help(value = "Run the launcher in unattended mode where no splash screen and error dialog is shown.", arg = false)
        boolean unattended() default false;
    }

    /** The currently running launcher version */
    private final Version runningVersion = VersionHelper.getVersion();

    /** Configuration passed to the tool */
    private LauncherConfig config;

    /** The home-directory for the hive */
    private Path rootDir;

    /** The path where to store updates */
    private Path updateDir;

    /** Path where the hive is stored */
    private Path bhiveDir;

    /** Path where the launched app is stored */
    private Path appDir;

    /** Path where all apps are stored. Each app is located in a folder with its unique ID */
    private Path appsDir;

    /** Path where the pooled products and artifacts are stored */
    private Path poolDir;

    /** The user-directory for the hive */
    private Path userArea;

    /** The launch descriptor */
    private ClickAndStartDescriptor clickAndStart;

    /** Configuration about the launched application */
    private ClientApplicationConfiguration clientAppCfg;

    /** Indicates whether or not the root is read-only */
    private boolean readOnlyRootDir;

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
            if (!config.unattended()) {
                splash.show();
            }

            // Write audit logs to the user area if set
            if (userArea != null) {
                auditor = RollingFileAuditor.getFactory().apply(userArea);
            }

            // Update and launch
            doLaunch(auditor, splash);
        } catch (CancellationException ex) {
            log.info("Launching has been canceled by the user.");
            doExit(-1);
        } catch (SoftwareUpdateException ex) {
            log.error("Software update cannot be installed.", ex);
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

    /**
     * Launch application after installing updates
     */
    private void doLaunch(Auditor auditor, LauncherSplash splash) {
        log.info("Launcher version '{}' started.", VersionHelper.getVersionAsString());
        log.info("Root directory: {}", rootDir);
        if (readOnlyRootDir) {
            log.info("User directory: {}", userArea);
            log.info("Root directory is readonly. No new applications or updates can be installed.");
        }

        LauncherSplashReporter reporter = new LauncherSplashReporter(splash);
        try (BHive hive = new BHive(bhiveDir.toUri(), auditor != null ? auditor : RollingFileAuditor.getFactory().apply(bhiveDir),
                reporter)) {
            // Provide callback to detect stale locks
            hive.setLockContentSupplier(LOCK_CONTENT);
            hive.setLockContentValidator(LOCK_VALIDATOR);

            // Clean stale transactions once the content supplier and validator are available.
            cleanStaleTransactions(hive);

            // Check for and install launcher updates
            // We always try to use the launcher matching the server version
            Entry<Version, Key> requiredLauncher = doSelfUpdate(hive, reporter);
            Version requiredVersion = VersionHelper.UNDEFINED;
            if (requiredLauncher != null) {
                requiredVersion = requiredLauncher.getKey();
            }

            // Launch the application or delegate launching
            Process process = null;
            if (shouldDelegate(runningVersion, requiredVersion)) {
                log.info("Application cannot be started with this launcher: Server is running an older version.");
                log.info("Delegating to launcher {}", requiredVersion);
                doExecuteLocked(hive, reporter, () -> {
                    doInstallLauncherSideBySide(hive, requiredLauncher);
                    doInstallAppSideBySide(hive, reporter, requiredLauncher);
                    return null;
                });
                process = doDelegateLaunch(requiredVersion, config.launch());
                log.info("Launcher successfully launched. PID={}", process.pid());
                log.info("Check logs in {} for more details.", ClientPathHelper.getHome(rootDir, requiredVersion));
            } else {
                // the source server could have been migrated/converted to node. in this case, display a message.
                if (doCheckForMigratedNode()) {
                    log.warn("Migrated node detected, cannot continue");
                    return;
                }
                doInstall(hive, reporter, splash);

                // Launch the application
                if (!config.updateOnly()) {
                    try (Activity info = reporter.start("Launching...")) {
                        process = launchApplication(clientAppCfg);
                    }
                    log.info("Application successfully launched. PID={}", process.pid());
                }
            }

            reporter.stop();
            splash.dismiss();

            if (!readOnlyRootDir) {
                log.info("Cleaning unused launchers and applications ...");
                doExecuteLocked(hive, reporter, () -> {
                    ClientCleanup cleanup = new ClientCleanup(hive, rootDir, appsDir, poolDir);
                    cleanup.run();
                    return null;
                });
            }

            if (config.updateOnly() || config.dontWait()) {
                log.info("Detaching and terminating.");
                return;
            }

            int exitCode = doMonitorProcess(process);

            // The delegated launcher launcher has already evaluated the exit
            // code and translated the application specific exit code
            // We just need to terminate with the given code
            if (clientAppCfg == null) {
                log.info("Delegated launcher terminated with exit code {}.", exitCode);
                doExit(exitCode);
            }

            // Application request an update. We will terminate the launcher
            // so that potential launcher updates are also applied
            ApplicationDescriptor appDesc = clientAppCfg.appDesc;
            ApplicationExitCodeDescriptor exitCodes = appDesc.exitCodes;
            if (exitCodes != null && exitCodes.update != null && exitCodes.update == exitCode) {
                log.info("Application signaled that updates should be installed. Restarting...");
                doExit(UpdateHelper.CODE_RESTART);
            }
            log.info("Application terminated with exit code {}.", exitCode);
        }
    }

    private boolean doCheckForMigratedNode() {
        MinionStatusResource msr = ResourceProvider.getVersionedResource(clickAndStart.host, MinionStatusResource.class, null);

        if (!msr.getStatus().config.master) {
            log.info("Minion is not a master: {}", clickAndStart.host.getUri());

            MessageDialogs.showServerIsNode();

            return true;
        }

        return false;
    }

    /**
     * Cleans up any past transactions which have been aborted for whatever reason.
     * <p>
     * A transaction is stale if the process which created the transaction is no longer alive, but the marker database
     * backing the transaction still exists. This would (worst case) allow damaged objects to exist in the object
     * database without any means to clean them up.
     *
     * @param hive
     */
    private void cleanStaleTransactions(BHive hive) {
        // only possible if not read-only root.
        if (!readOnlyRootDir) {
            // Check for stale transactions in the hive.
            log.debug("Checking for stale transactions.");
            long stale = hive.getTransactions().cleanStaleTransactions();
            if (stale > 0) {
                // Stale transactions might leave damaged objects around, force a prune to get rid of them.
                // There is no need to perform a full check of all existing manifests though, a stale transaction
                // can not cause them to break.
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

    /** Returns whether launching should be delegated to another (older) launcher */
    private boolean shouldDelegate(Version running, Version required) {
        // If the local or the required version is undefined we launch with whatever we have
        if (VersionHelper.isUndefined(running) || VersionHelper.isUndefined(required)) {
            return false;
        }
        // Delegate when they do not match
        return !VersionHelper.equals(running, required);
    }

    /** Terminates the VM with the given exit code */
    @SuppressFBWarnings("DM_EXIT")
    private static void doExit(Integer exitCode) {
        System.exit(exitCode);
    }

    /** Updates the launcher if there is a new version available */
    private Entry<Version, Key> doSelfUpdate(BHive hive, LauncherSplashReporter reporter) {
        if (VersionHelper.isRunningUndefined()) {
            log.warn("Skipping self update. The local running version is not defined.");
            return null;
        }
        log.info("Checking for launcher updates...");
        return doExecuteLocked(hive, reporter, () -> {
            Entry<Version, Key> requiredLauncher = getLatestLauncherVersion(reporter);
            doCheckForLauncherUpdate(hive, reporter, requiredLauncher);
            return requiredLauncher;
        });
    }

    /** Waits until the given process terminates */
    private int doMonitorProcess(Process process) {
        try {
            return process.waitFor();
        } catch (InterruptedException e) {
            log.warn("Waiting for application exit interrupted.");
            Thread.currentThread().interrupt();
            return -1;
        }
    }

    /** Validate that homeDir is specified the same way as it was the first time */
    private void validateBDeployHome() {
        Path bdeployHomePath = rootDir.resolve("bdeploy.home");
        if (bdeployHomePath.toFile().exists()) {
            validateBDeployHome(bdeployHomePath);
        } else if (!readOnlyRootDir) {
            saveBDeployHome(bdeployHomePath);
        }
    }

    /** Validate content of bdeploy.home file is the same as current value of homeDir */
    private void validateBDeployHome(Path bdeployHomePath) {
        try {
            Path storedPath = Path.of(Files.readString(bdeployHomePath, StandardCharsets.UTF_8));
            if (!rootDir.equals(storedPath)) {
                throw new IllegalStateException(
                        "BDeploy home directory has changed. Expected: " + storedPath + " Got: " + rootDir);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read from " + bdeployHomePath.toAbsolutePath());
        }
    }

    /** Save homeDir value into bdeploy.home file */
    private void saveBDeployHome(Path bdeployHomePath) {
        try {
            Files.write(bdeployHomePath, config.homeDir().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to save BDEPLOY_HOME value into file");
        }
    }

    /** Initializes all parameters based on the given configuration */
    private void doInit(LauncherConfig config) {
        if (config.launch() == null) {
            throw new IllegalStateException("Missing --launch argument");
        }
        if (config.homeDir() == null) {
            throw new IllegalStateException("Missing --homeDir argument");
        }
        this.config = config;

        // Check where to put local data.
        rootDir = Paths.get(config.homeDir()).toAbsolutePath();
        updateDir = PathHelper.ofNullableStrig(config.updateDir());

        // Check for inconsistent file and folder permissions
        Path versionsFile = rootDir.resolve(ClientPathHelper.LAUNCHER_DIR).resolve("version.properties");
        readOnlyRootDir = PathHelper.isReadOnly(rootDir, versionsFile);

        // Check that the launcher was not moved
        validateBDeployHome();

        // Try to get a user-area if the root is readonly
        if (readOnlyRootDir) {
            userArea = ClientPathHelper.getUserArea();
            if (userArea == null || PathHelper.isReadOnly(userArea)) {
                throw new IllegalStateException("The user area '" + userArea + "' does not exist or cannot be modified.");
            }
        }

        Path descriptorFile = Paths.get(config.launch());
        try (InputStream is = Files.newInputStream(descriptorFile)) {
            clickAndStart = StorageHelper.fromStream(is, ClickAndStartDescriptor.class);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + config.launch(), e);
        }
        bhiveDir = rootDir.resolve("bhive");
        appsDir = rootDir.resolve("apps");
        poolDir = appsDir.resolve("pool");
        appDir = appsDir.resolve(clickAndStart.applicationId);

        // Enrich log messages with the application that is about to be launched
        String pid = String.format("PID: %1$5s", ProcessHandle.current().pid());
        MDC.put(MdcLogger.MDC_NAME, pid + " | App: " + clickAndStart.applicationId);
    }

    private void doInstall(BHive hive, LauncherSplashReporter reporter, LauncherSplash splash) {
        MasterRootResource master = ResourceProvider.getVersionedResource(clickAndStart.host, MasterRootResource.class, null);
        MasterNamedResource namedMaster = master.getNamedMaster(clickAndStart.groupId);

        // Fetch more information from the remote server.
        try (Activity info = reporter.start("Loading meta-data...")) {
            log.info("Fetching configuration from server...");
            clientAppCfg = namedMaster.getClientConfiguration(clickAndStart.instanceId, clickAndStart.applicationId);
        }

        // Update splash with the fetched branding information
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

        // Install the application into the pool if missing
        doExecuteLocked(hive, reporter, () -> {
            installApplication(hive, splash, reporter, clientAppCfg);
            return null;
        });
    }

    private String getHostname(String fallback) {
        String hostname = null;
        try {
            hostname = InetAddress.getLocalHost().getCanonicalHostName();
        } catch (Exception e) {
            // continue
        }
        if (hostname == null || hostname.equalsIgnoreCase("localhost")) {
            hostname = NativeHostnameResolver.getHostname();
        }
        return hostname != null ? hostname : fallback;
    }

    /**
     * Locks the root in order to perform the given operation. The lock is not taken if the current user does
     * not have the permissions to modify the root directory. In that case the operation is directly executed.
     */
    private <T> T doExecuteLocked(BHive hive, LauncherSplashReporter reporter, Callable<T> runnable) {
        if (!readOnlyRootDir) {
            try (Activity waiting = reporter.start("Waiting for other launchers...")) {
                hive.execute(new LockDirectoryOperation().setDirectory(rootDir)); // this could wait for other launchers.
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
            if (!readOnlyRootDir) {
                hive.execute(new ReleaseDirectoryLockOperation().setDirectory(rootDir));
            }
        }
    }

    /** Checks for updates and installs them if required */
    private void doCheckForLauncherUpdate(BHive hive, ActivityReporter reporter, Map.Entry<Version, Key> requiredLauncher) {
        Version latestVersion = requiredLauncher.getKey();
        if (latestVersion.compareTo(runningVersion) <= 0) {
            log.info("No launcher updates are available.");
            return;
        }
        Key launcher = requiredLauncher.getValue();
        log.info("Launcher updates found. Updating from {} to {}", runningVersion, latestVersion);

        // Check if we have write permissions to install the update
        if (readOnlyRootDir) {
            throw new SoftwareUpdateException("launcher",
                    "Installed=" + runningVersion.toString() + " Available=" + latestVersion.toString());
        }

        // In case that another launcher has launched us then we do not perform any updates
        // We just exit with the update code so that the outer launcher can do all required tasks
        if (System.getenv(BDEPLOY_DELEGATE) != null) {
            log.info("Update of launcher required. Delegating to parent to do this...");
            doExit(UpdateHelper.CODE_RESTART);
            return;
        }

        Path updateMarker = updateDir.resolve(".updating");
        try (Activity updating = reporter.start("Updating Launcher")) {
            waitForLauncherUpdates(updateMarker);

            try (Transaction t = hive.getTransactions().begin()) {
                // found a newer version to install.
                hive.execute(new FetchOperation().addManifest(launcher).setRemote(clickAndStart.host));
            }

            // write to target directory
            Path next = UpdateHelper.prepareUpdateDirectory(updateDir);
            hive.execute(new ExportOperation().setManifest(launcher).setTarget(next));

            // create a marker for others which will gain the lock after we exit for restart.
            Files.createFile(updateMarker);

            // Signal that a new update is available
            log.info("Restarting...");
            doExit(UpdateHelper.CODE_RESTART);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create update marker");
        }
    }

    /**
     * Waits for some time until another launcher has finished installing updates for the launcher.
     */
    private void waitForLauncherUpdates(Path updateMarker) throws IOException {
        // No one is installing updates.
        if (!Files.isRegularFile(updateMarker)) {
            return;
        }

        // Check for a stale update marker
        if (System.currentTimeMillis() - Files.getLastModifiedTime(updateMarker).toMillis() > 60_000) {
            log.warn("Stale update marker found, removing.");
            Files.delete(updateMarker);
            return;
        }

        // Update marker found -> On Windows we just terminate
        // The native launcher cannot install updates while we are running
        if (OsHelper.getRunningOs() == OperatingSystem.WINDOWS) {
            log.info("Found existing update marker. Exiting to allow updates to be installed.");
            doExit(UpdateHelper.CODE_RESTART);
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

    /**
     * Installs the application with all requirements in case it is not already installed
     */
    private void installApplication(BHive hive, LauncherSplash splash, ActivityReporter reporter,
            ClientApplicationConfiguration clientAppCfg) {
        ApplicationConfiguration appCfg = clientAppCfg.appConfig;

        // Check if the application directory is already present
        String appName = appCfg.name;
        Collection<String> missing = getMissingArtifacts(hive, clientAppCfg);
        if (missing.isEmpty()) {
            log.info("Application is already installed. Nothing to install/update.");
            return;
        }
        log.info("Application needs to be installed/updated: {}", missing);

        // Throw an exception if we do not have write permissions in the directory
        if (readOnlyRootDir) {
            throw new SoftwareUpdateException(appName, "Missing parts: " + missing.stream().collect(Collectors.joining(",")));
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

        // Application specific data will be stored in a separate directory
        PathHelper.mkdirs(appDir);

        // Download and install the current configuration tree if required.
        DeploymentPathProvider pathProvider = new DeploymentPathProvider(appDir, "1");
        Path cfgPath = pathProvider.get(SpecialDirectory.CONFIG);
        PathHelper.deleteRecursive(cfgPath); // get rid of *any* existing config

        if (clientAppCfg.configTree != null) {
            downloadAndInstallConfigFiles(clientAppCfg, cfgPath);
        }

        // Store branding information on file-system
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

        // Protocol the installation
        ClientSoftwareManifest manifest = new ClientSoftwareManifest(hive);
        ClientSoftwareConfiguration newConfig = new ClientSoftwareConfiguration();
        newConfig.clickAndStart = clickAndStart;
        newConfig.metadata = ClientApplicationDto.create(clickAndStart, clientAppCfg);
        newConfig.requiredSoftware.addAll(applications);
        manifest.update(clickAndStart.applicationId, newConfig);

        // Calculate the difference which software is not required anymore
        log.info("Application successfully installed.");
    }

    private void downloadAndInstallConfigFiles(ClientApplicationConfiguration clientAppCfg, Path cfgPath) {
        // contact server, download files and write into config directory.
        MasterRootResource master = ResourceProvider.getVersionedResource(clickAndStart.host, MasterRootResource.class, null);
        MasterNamedResource namedMaster = master.getNamedMaster(clickAndStart.groupId);

        Path cfgZip = appDir.resolve(clientAppCfg.configTree.getId() + ".zip");
        Response zipDl = namedMaster.getConfigZipSteam(clickAndStart.instanceId, clickAndStart.applicationId);

        if (zipDl.getStatusInfo().getFamily() != Family.SUCCESSFUL) {
            throw new IllegalStateException("Config File download failed: " + zipDl.getStatusInfo().getStatusCode() + ": "
                    + zipDl.getStatusInfo().getReasonPhrase());
        }

        // write the download into a temporary file so we can unzip it.
        try (InputStream input = zipDl.readEntity(InputStream.class); OutputStream output = Files.newOutputStream(cfgZip)) {
            StreamHelper.copy(input, output);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Cannot read ZIP response from config files request for " + clickAndStart.applicationId);
        }

        // unzip the downloaded ZIP containing the configuration files.
        ZipHelper.unzip(cfgZip, cfgPath);

        try {
            // record the proper ID in the config check file.
            Files.write(cfgPath.resolve(CONFIG_DIR_CHECK_FILE), Collections.singletonList(clientAppCfg.configTree.getId()));
        } catch (Exception e) {
            log.warn("Cannot write config check file", e);
        }

        // remove the temporary download file.
        PathHelper.deleteRecursive(cfgZip);
    }

    /**
     * Checks if the application and ALL the required dependencies are already installed. The check is done
     * by verifying that the target directories are existing. No deep verification is done. The returned list
     * indicates which artifacts are missing.
     */
    private Collection<String> getMissingArtifacts(BHive hive, ClientApplicationConfiguration clientAppCfg) {
        // Application directory must be there
        // NOTE: Directory is create before by the native installer
        Collection<String> missing = new ArrayList<>();
        if (!PathHelper.exists(appDir)) {
            missing.add("Directory: " + clientAppCfg.appConfig.uid);
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

        // configuration files need to be installed in the correct version
        if (clientAppCfg.configTree != null) {
            DeploymentPathProvider pathProvider = new DeploymentPathProvider(appDir, "1");
            Path cfgPath = pathProvider.get(SpecialDirectory.CONFIG);

            boolean isCurrent = checkForCurrentConfigFiles(clientAppCfg.configTree, cfgPath);

            if (!isCurrent) {
                missing.add("Configuration Files Version: " + clientAppCfg.configTree);
            }
        }

        // Meta-Manifest about the installation must be there
        // and must refer to what the application actually requires
        ClientSoftwareManifest manifest = new ClientSoftwareManifest(hive);
        ClientSoftwareConfiguration clientConfig = manifest.readNewest(clientAppCfg.appConfig.uid, false);
        if (clientConfig == null) {
            missing.add("Meta-Manifest:" + clientAppCfg.appConfig.uid);
        } else {
            // Check that all required apps are listed
            applications.removeAll(clientConfig.requiredSoftware);
            if (!applications.isEmpty()) {
                missing.add("Meta-Manifest-Entry: " + applications);
            }

            // Remove reference to the launcher
            if (clientConfig.launcher != null) {
                missing.add("Delete Meta-Manifest: " + clientConfig.launcher);
            }
        }
        return missing;
    }

    private boolean checkForCurrentConfigFiles(ObjectId configTree, Path cfgPath) {
        Path checkFile = cfgPath.resolve(CONFIG_DIR_CHECK_FILE);
        if (PathHelper.exists(checkFile)) {
            try (InputStream is = Files.newInputStream(checkFile)) {
                List<String> lines = Files.readAllLines(checkFile);

                if (lines.isEmpty()) {
                    return false;
                }

                String check = lines.get(0);
                if (clientAppCfg.configTree.getId().equals(check)) {
                    return true;
                }
            } catch (Exception e) {
                log.warn("Cannot read check file: " + checkFile, e);
            }
        }
        return false;
    }

    /**
     * Launches the client process using the given configuration.
     */
    private Process launchApplication(ClientApplicationConfiguration clientCfg) {
        log.info("Launching application.");
        ApplicationConfiguration appCfg = clientCfg.appConfig;
        DeploymentPathProvider pathProvider = new DeploymentPathProvider(appDir, "1");

        MasterRootResource master = ResourceProvider.getVersionedResource(clickAndStart.host, MasterRootResource.class, null);
        MasterNamedResource namedMaster = master.getNamedMaster(clickAndStart.groupId);
        namedMaster.logClientStart(clickAndStart.instanceId, clickAndStart.applicationId, getHostname("unknown"));

        // General resolvers
        CompositeResolver resolvers = new CompositeResolver();
        resolvers.add(new InstanceAndSystemVariableResolver(clientCfg.instanceConfig));
        resolvers.add(new ApplicationVariableResolver(appCfg));
        resolvers.add(new DelayedVariableResolver(resolvers));
        resolvers.add(new InstanceVariableResolver(clientCfg.instanceConfig, pathProvider, clientCfg.activeTag));
        resolvers.add(new OsVariableResolver());
        resolvers.add(new EnvironmentVariableResolver());
        resolvers.add(new ParameterValueResolver(new ApplicationParameterProvider(clientCfg.instanceConfig)));

        // Enable resolving of path variables
        resolvers.add(new DeploymentPathResolver(pathProvider));

        // Enable resolving of manifest variables
        Map<Key, Path> pooledSoftware = new HashMap<>();
        pooledSoftware.put(appCfg.application, poolDir.resolve(appCfg.application.directoryFriendlyName()));
        for (Manifest.Key key : clientCfg.resolvedRequires) {
            pooledSoftware.put(key, poolDir.resolve(key.directoryFriendlyName()));
        }
        resolvers.add(new ManifestVariableResolver(new ManifestRefPathProvider(pathProvider, pooledSoftware)));

        // Resolvers that are using the general ones to actually do the work
        CompositeResolver appSpecificResolvers = new CompositeResolver();
        appSpecificResolvers.add(new ApplicationParameterValueResolver(appCfg.uid, clientCfg.instanceConfig));
        appSpecificResolvers.add(new ManifestSelfResolver(appCfg.application, resolvers));
        appSpecificResolvers.add(resolvers);

        // Create the actual start command and replace all defined variables
        ProcessConfiguration pc = appCfg.renderDescriptor(appSpecificResolvers);
        List<String> command = TemplateHelper.process(pc.start, appSpecificResolvers);

        // Append custom arguments if applicable
        command.addAll(decodeAdditionalArguments(config.appendArgs()));

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
                    .redirectOutput(Redirect.INHERIT).directory(appDir.toFile());
            return b.start();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot start " + appCfg.uid, e);
        }
    }

    /**
     * Installs the launcher side-by-side to this launcher. Does nothing if the launcher is already installed
     */
    private void doInstallLauncherSideBySide(BHive hive, Entry<Version, Key> requiredLauncher) {
        Version version = requiredLauncher.getKey();
        Path homeDir = ClientPathHelper.getHome(rootDir, version);
        Path nativeLauncher = ClientPathHelper.getNativeLauncher(homeDir);

        Key launcher = requiredLauncher.getValue();
        if (PathHelper.exists(nativeLauncher)) {
            log.info("Launcher is already installed.");
            return;
        }
        log.info("Installing required launcher ...");
        if (PathHelper.isReadOnly(homeDir)) {
            throw new SoftwareUpdateException("launcher", "Installed=" + runningVersion.toString() + " Required=" + version);
        }
        // Fetch and write to target directory
        try (Transaction t = hive.getTransactions().begin()) {
            hive.execute(new FetchOperation().addManifest(launcher).setRemote(clickAndStart.host));
        }
        Path launcherHome = homeDir.resolve(ClientPathHelper.LAUNCHER_DIR);
        hive.execute(new ExportOperation().setManifest(launcher).setTarget(launcherHome));
        log.info("Launcher successfully installed.");
    }

    /**
     * Copies software that the application is using from our hive to the side-by-side hive.
     */
    private void doInstallAppSideBySide(BHive hive, LauncherSplashReporter reporter, Entry<Version, Key> requiredLauncher) {
        // Check if the stored configuration references the required launcher
        // If so then we can continue. There is nothing that we need to do
        Version version = requiredLauncher.getKey();
        ClientSoftwareManifest manifest = new ClientSoftwareManifest(hive);
        ClientSoftwareConfiguration cscfg = manifest.readNewest(clickAndStart.applicationId, true);
        Key launcher = requiredLauncher.getValue();
        if (cscfg.launcher != null && cscfg.launcher.equals(launcher)) {
            log.info("Client software manifest is up-2-date.");
            return;
        }

        Path homeDir = ClientPathHelper.getHome(rootDir, version);
        if (PathHelper.isReadOnly(homeDir)) {
            throw new SoftwareUpdateException(clickAndStart.applicationId, "Missing artifacts: Software Manifest");
        }

        // Protocol that this launcher needs to be retained
        // NOTE: We are intentionally not clearing out the required software entry
        // As a result the software will be retained in our hive
        // If the server hosting this application is upgraded the user do not need
        // to download everything again. Thus start times remain fast.
        cscfg.launcher = launcher;
        cscfg.clickAndStart = clickAndStart;
        manifest.update(clickAndStart.applicationId, cscfg);

        // We never started this application so we do not know which software it is using
        if (cscfg.requiredSoftware.isEmpty()) {
            log.info("Skip copying required software: Application has never been started with this launcher.");
            return;
        }

        // Copy the required software from our hive to the target hive
        log.info("Copy required software ...");
        Path hiveDir = homeDir.resolve("bhive");
        try (BHive otherHive = new BHive(hiveDir.toUri(), RollingFileAuditor.getFactory().apply(hiveDir), reporter)) {
            otherHive.setLockContentSupplier(LOCK_CONTENT);
            otherHive.setLockContentValidator(LOCK_VALIDATOR);

            Set<ObjectId> requiredObjects = hive.execute(new ObjectListOperation().addManifest(cscfg.requiredSoftware));
            TransferStatistics stats = hive.execute(new CopyOperation().setDestinationHive(otherHive).addObject(requiredObjects)
                    .addManifest(cscfg.requiredSoftware));
            if (stats.sumManifests == 0) {
                log.info("Hive already contains all required files.");
            } else {
                log.info("Copied missing files from this hive. {}", stats.toLogString());
            }
        } catch (Exception ex) {
            // We just log the error and continue
            // The other launcher will download the required software
            log.warn("Failed to copy required software.", ex);
        }
    }

    /**
     * Delegates launching of the application to the given version of the launcher.
     */
    private Process doDelegateLaunch(Version version, String appDescriptor) {
        Path homeDir = ClientPathHelper.getHome(rootDir, version);
        Path nativeLauncher = ClientPathHelper.getNativeLauncher(homeDir);

        List<String> command = new ArrayList<>();
        command.add(nativeLauncher.toFile().getAbsolutePath());
        command.add(appDescriptor);
        if (config.customizeArgs()) {
            command.add("--customizeArgs");
        }
        if (config.updateOnly()) {
            command.add("--updateOnly");
        }

        Collection<String> appArguments = decodeAdditionalArguments(config.appendArgs());
        if (!appArguments.isEmpty()) {
            command.add("--");
            appArguments.forEach(arg -> command.add("\"" + arg + "\""));
        }

        log.info("Executing {}", command.stream().collect(Collectors.joining(" ")));
        try {
            ProcessBuilder b = new ProcessBuilder(command);
            b.redirectError(Redirect.INHERIT).redirectInput(Redirect.INHERIT).redirectOutput(Redirect.INHERIT);
            b.directory(homeDir.resolve(ClientPathHelper.LAUNCHER_DIR).toFile());

            // Set the home directory for the launcher. Required for older launchers
            // Newer launchers do not use the variable any more
            Map<String, String> env = b.environment();
            env.put("BDEPLOY_HOME", homeDir.toFile().getAbsolutePath());

            // Notify the launcher that he runs in a special mode.
            // In that mode he will forward all exit codes without special handling.
            env.put(BDEPLOY_DELEGATE, "TRUE");
            return b.start();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot start launcher.", e);
        }
    }

    /** Returns the latest available launcher version */
    private Map.Entry<Version, Key> getLatestLauncherVersion(ActivityReporter reporter) {
        // Fetch all versions and filter out the one that corresponds to the server version
        OperatingSystem runningOs = OsHelper.getRunningOs();
        String launcherKey = UpdateHelper.SW_META_PREFIX + UpdateHelper.SW_LAUNCHER;
        NavigableMap<Version, Key> versions = new TreeMap<>(VersionComparator.NEWEST_LAST);
        try (RemoteBHive rbh = RemoteBHive.forService(clickAndStart.host, null, reporter);
                Activity check = reporter.start("Fetching launcher versions....")) {

            Version serverVersion = getServerVersion(clickAndStart);
            boolean serverIsUndefined = VersionHelper.isUndefined(serverVersion);

            SortedMap<Key, ObjectId> launchers = rbh.getManifestInventory(launcherKey);
            for (Key launcher : launchers.keySet()) {
                ScopedManifestKey smk = ScopedManifestKey.parse(launcher);
                if (smk.getOperatingSystem() != runningOs) {
                    continue;
                }

                // Filter out all versions that do not match the current server version
                // We do this only in case that the server provides a valid version. Older
                // servers do not have this API and thus cannot tell us their version
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
     * Decodes the additional arguments that are passed to the application.
     */
    @SuppressWarnings("unchecked")
    private Collection<String> decodeAdditionalArguments(String appendArgs) {
        if (appendArgs == null || appendArgs.isBlank()) {
            return Collections.emptyList();
        }
        byte[] decodedBytes = Base64.decodeBase64(appendArgs);
        return StorageHelper.fromRawBytes(decodedBytes, List.class);
    }

    /**
     * Returns the server version or null in case that the version cannot be determined
     */
    public static Version getServerVersion(ClickAndStartDescriptor descriptor) {
        try {
            CommonRootResource resource = ResourceProvider.getVersionedResource(descriptor.host, CommonRootResource.class, null);
            return resource.getVersion();
        } catch (Exception ex) {
            if (log.isDebugEnabled()) {
                log.debug("Cannot determine server version.", ex);
            }
            return VersionHelper.UNDEFINED;
        }
    }

}
