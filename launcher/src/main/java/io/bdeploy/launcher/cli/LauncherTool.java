package io.bdeploy.launcher.cli;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ProcessBuilder.Redirect;
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
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.bdeploy.api.product.v1.impl.ScopedManifestKey;
import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.objects.MarkerDatabase;
import io.bdeploy.bhive.op.ExportOperation;
import io.bdeploy.bhive.op.remote.FetchOperation;
import io.bdeploy.bhive.op.remote.TransferStatistics;
import io.bdeploy.bhive.remote.RemoteBHive;
import io.bdeploy.bhive.util.StorageHelper;
import io.bdeploy.bhive.util.VersionComparator;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.ActivityReporter.Activity;
import io.bdeploy.common.Version;
import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cfg.Configuration.Validator;
import io.bdeploy.common.cfg.ExistingPathValidator;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.cli.ToolBase.ConfiguredCliTool;
import io.bdeploy.common.cli.data.RenderableResult;
import io.bdeploy.common.util.DurationHelper;
import io.bdeploy.common.util.OsHelper;
import io.bdeploy.common.util.OsHelper.OperatingSystem;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.common.util.TemplateHelper;
import io.bdeploy.common.util.UnitHelper;
import io.bdeploy.common.util.VersionHelper;
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
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.interfaces.variables.ApplicationParameterProvider;
import io.bdeploy.interfaces.variables.ApplicationParameterValueResolver;
import io.bdeploy.interfaces.variables.ApplicationVariableResolver;
import io.bdeploy.interfaces.variables.CompositeResolver;
import io.bdeploy.interfaces.variables.DelayedVariableResolver;
import io.bdeploy.interfaces.variables.DeploymentPathProvider;
import io.bdeploy.interfaces.variables.DeploymentPathResolver;
import io.bdeploy.interfaces.variables.EnvironmentVariableResolver;
import io.bdeploy.interfaces.variables.InstanceVariableResolver;
import io.bdeploy.interfaces.variables.ManifestRefPathProvider;
import io.bdeploy.interfaces.variables.ManifestSelfResolver;
import io.bdeploy.interfaces.variables.ManifestVariableResolver;
import io.bdeploy.interfaces.variables.OsVariableResolver;
import io.bdeploy.interfaces.variables.ParameterValueResolver;
import io.bdeploy.jersey.audit.Auditor;
import io.bdeploy.jersey.audit.RollingFileAuditor;
import io.bdeploy.launcher.cli.LauncherTool.LauncherConfig;
import io.bdeploy.launcher.cli.branding.LauncherSplash;
import io.bdeploy.launcher.cli.branding.LauncherSplashReporter;
import io.bdeploy.launcher.cli.ui.LauncherErrorDialog;
import io.bdeploy.launcher.cli.ui.LauncherUpdateDialog;

@CliName("launcher")
@Help("A tool which launches an application described by a '.bdeploy' file")
public class LauncherTool extends ConfiguredCliTool<LauncherConfig> {

    private static final Logger log = LoggerFactory.getLogger(LauncherTool.class);

    /** Lock files should contain the writing PID. In case the process no longer exists, the lock file is invalid. */
    private static final Supplier<String> LOCK_CONTENT = () -> Long.toString(ProcessHandle.current().pid());

    /** Validator will check whether the writing PID of the lock file is still there. */
    private static final Predicate<String> LOCK_VALIDATOR = pid -> ProcessHandle.of(Long.parseLong(pid)).isPresent();

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

        @Help(value = "Makes the launcher quit immediately after updating and launching the application.", arg = false)
        boolean dontWait() default false;

        @Help(value = "Terminate the application when an error occurs instead of opening an error dialog.", arg = false)
        boolean exitOnError() default false;
    }

    /** The currently running launcher version */
    private final Version runningVersion = VersionHelper.getVersion();

    /** The home-directory for the hive */
    private Path rootDir;

    /** The path where to store updates */
    private Path updateDir;

    /** Path where the hive is stored */
    private Path bhiveDir;

    /** Path where the launched app is stored */
    private Path appDir;

    /** Path where the pooled products and artifacts are stored */
    private Path poolDir;

    /** The user-directory for the hive */
    private Path userArea;

    /** The launch descriptor */
    private ClickAndStartDescriptor descriptor;

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
            splash.show();

            // Write audit logs to the user area if set
            if (userArea != null) {
                auditor = new RollingFileAuditor(userArea.resolve("logs"));
            }

            // Log details about our current version
            if (VersionHelper.isRunningUndefined()) {
                log.info("Launcher version: Undefined.");
            } else {
                log.info("Launcher version: {}", runningVersion);
            }

            // Log details about the server version
            // NOTE: Not all servers can tell us their version
            Version serverVersion = getServerVersion(descriptor);
            if (VersionHelper.isUndefined(serverVersion)) {
                log.info("Server version: Undefined.");
            } else {
                log.info("Server version: {}", serverVersion);
            }

            // Launch application after installing updates
            LauncherSplashReporter reporter = new LauncherSplashReporter(splash);
            try (BHive hive = new BHive(bhiveDir.toUri(), auditor, reporter)) {
                // Check for and install launcher updates
                // We always try to use the launcher matching the server version
                // If no launcher is installed we simply use the currently running version
                Entry<Version, Key> requiredLauncher = doSelfUpdate(hive, reporter, serverVersion);
                Version requiredVersion = requiredLauncher != null ? requiredLauncher.getKey() : runningVersion;

                // Launch the application or delegate launching
                Process process;
                if (!VersionHelper.equals(runningVersion, requiredVersion) && !VersionHelper.isUndefined(runningVersion)) {
                    log.info("Application requires an older launcher version. Delegating...");
                    doInstallSideBySide(hive, requiredLauncher);
                    process = doDelegateLaunch(requiredVersion, config.launch());
                    log.info("Launcher successfully launched. PID={}", process.pid());
                } else {
                    process = doLaunch(hive, reporter, splash);
                    log.info("Application successfully launched. PID={}", process.pid());
                }

                // Hide progress reporting
                reporter.stop();
                splash.dismiss();

                // Cleanup the installation directory and the hive.
                if (!readOnlyRootDir) {
                    doExecuteLocked(reporter, () -> {
                        ClientCleanup cleanup = new ClientCleanup(hive, rootDir, appDir, poolDir);
                        cleanup.run();
                        return null;
                    });
                }

                // Wait until the process terminates
                if (config.dontWait()) {
                    log.info("Detaching and terminating.");
                    return null;
                }
                int exitCode = doMonitorProcess(process);

                // The delegated launcher launcher has already evaluated the exit
                // code and translated the application specific exit code
                // We just need to terminate with the given code
                if (clientAppCfg == null) {
                    log.info("Delegated launcher terminated with exit code {}.", exitCode);
                    doExit(exitCode);
                    return null;
                }

                // Application request an update. We will terminate the launcher
                // so that potential launcher updates are also applied
                ApplicationDescriptor appDesc = clientAppCfg.appDesc;
                ApplicationExitCodeDescriptor exitCodes = appDesc.exitCodes;
                if (exitCodes != null && exitCodes.update != null && exitCodes.update == exitCode) {
                    log.info("Application signaled that updates should be installed. Restarting...");
                    doExit(UpdateHelper.CODE_UPDATE);
                    return null;
                }
                log.info("Application terminated with exit code {}.", exitCode);
            }
        } catch (SoftwareUpdateException ex) {
            log.error("Software update cannot be installed.", ex);
            if (config.exitOnError()) {
                helpAndFail(ex.getMessage());
            }
            LauncherUpdateDialog dialog = new LauncherUpdateDialog();
            dialog.showUpdateRequired(descriptor, ex);
        } catch (Exception ex) {
            log.error("Failed to launch application.", ex);
            if (config.exitOnError()) {
                helpAndFail(ex.getMessage());
            }
            LauncherErrorDialog dialog = new LauncherErrorDialog();
            dialog.showError(descriptor, ex);
        } finally {
            if (auditor != null) {
                auditor.close();
            }
        }

        return null;
    }

    /** Terminates the VM with the given exit code */
    @SuppressFBWarnings("DM_EXIT")
    private static void doExit(Integer exitCode) {
        System.exit(exitCode);
    }

    /** Updates the launcher if there is a new version available */
    private Entry<Version, Key> doSelfUpdate(BHive hive, LauncherSplashReporter reporter, Version serverVersion) {
        return doExecuteLocked(reporter, () -> {
            Entry<Version, Key> requiredLauncher = getLatestLauncherVersion(reporter, serverVersion);
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

    /** Initializes all parameters based on the given configuration */
    private void doInit(LauncherConfig config) {
        if (config.launch() == null) {
            throw new IllegalStateException("Missing --launch argument");
        }
        if (config.homeDir() == null) {
            throw new IllegalStateException("Missing --homeDir argument");
        }

        // Check where to put local data.
        rootDir = Paths.get(config.homeDir()).toAbsolutePath();
        updateDir = PathHelper.ofNullableStrig(config.updateDir());

        // Try to get a user-area if the root is readonly
        if (PathHelper.isReadOnly(rootDir)) {
            userArea = ClientPathHelper.getUserArea();
            if (userArea == null || PathHelper.isReadOnly(userArea)) {
                throw new IllegalStateException("The user area '" + userArea + "' does not exist or cannot be modified.");
            }
        }

        Path descriptorFile = Paths.get(config.launch());
        try (InputStream is = Files.newInputStream(descriptorFile)) {
            descriptor = StorageHelper.fromStream(is, ClickAndStartDescriptor.class);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + config.launch(), e);
        }
        bhiveDir = rootDir.resolve("bhive");
        Path appsDir = rootDir.resolve("apps");
        poolDir = appsDir.resolve("pool");
        appDir = appsDir.resolve(descriptor.applicationId);
        readOnlyRootDir = PathHelper.isReadOnly(rootDir);

        log.info("Home directory: {}{}", rootDir, readOnlyRootDir ? " (readonly)" : "");
        if (userArea != null) {
            log.info("User-area: {}", userArea);
        }
    }

    private Process doLaunch(BHive hive, LauncherSplashReporter reporter, LauncherSplash splash) {
        log.info("Launching application {}.", descriptor.applicationId);
        MasterRootResource master = ResourceProvider.getVersionedResource(descriptor.host, MasterRootResource.class, null);
        MasterNamedResource namedMaster = master.getNamedMaster(descriptor.groupId);

        // Fetch more information from the remote server.
        try (Activity info = reporter.start("Loading meta-data...")) {
            log.info("Fetching client configuration from server...");
            clientAppCfg = namedMaster.getClientConfiguration(descriptor.instanceId, descriptor.applicationId);
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
        doExecuteLocked(reporter, () -> {
            installApplication(hive, splash, reporter, clientAppCfg);
            return null;
        });

        // Launch the application
        try (Activity info = reporter.start("Launching...")) {
            return launchApplication(clientAppCfg);
        }
    }

    /**
     * Locks the root in order to perform the given operation. The lock is not taken if the current user does
     * not have the permissions to modify the root directory. In that case the operation is directly executed.
     */
    private <T> T doExecuteLocked(LauncherSplashReporter reporter, Callable<T> runnable) {
        if (!readOnlyRootDir) {
            try (Activity waiting = reporter.start("Waiting for other launchers...")) {
                MarkerDatabase.lockRoot(rootDir, LOCK_CONTENT, LOCK_VALIDATOR); // this could wait for other launchers.
            }
        }
        try {
            return runnable.call();
        } catch (RuntimeException rex) {
            throw rex;
        } catch (Exception ex) {
            throw new RuntimeException("Failed to execute locked operation", ex);
        } finally {
            if (!readOnlyRootDir) {
                MarkerDatabase.unlockRoot(rootDir);
            }
        }
    }

    /** Checks for updates and installs them if required */
    private void doCheckForLauncherUpdate(BHive hive, ActivityReporter reporter, Map.Entry<Version, Key> latestLauncher) {
        if (VersionHelper.isRunningUndefined()) {
            log.info("Running version is undefined. Skipping updates...");
            return;
        }
        if (latestLauncher == null) {
            log.warn("Cannot find any launcher version on the server.");
            return;
        }
        log.info("Newest launcher version of server: {}", latestLauncher.getKey());
        Version latestVersion = latestLauncher.getKey();
        if (latestVersion.compareTo(runningVersion) <= 0) {
            log.info("No updates found (running={}, newest={}). Continue...", runningVersion, latestVersion);
            return;
        }
        Key launcher = latestLauncher.getValue();

        // Check if we have write permissions to install the update
        if (PathHelper.isReadOnly(rootDir)) {
            throw new SoftwareUpdateException("launcher",
                    "Installed=" + runningVersion.toString() + " Available=" + latestVersion.toString());
        }

        // In case that another launcher has launched us then we do not perform any updates
        // We just exit with the update code so that the outer launcher can do all required tasks
        if (System.getenv(BDEPLOY_DELEGATE) != null) {
            log.info("Update of launcher required. Delegating to parent to do this...");
            doExit(UpdateHelper.CODE_UPDATE);
            return;
        }

        log.info("Updating launcher from {} to {}", runningVersion, latestVersion);
        Path next = null;
        Path updateMarker = updateDir.resolve(".updating");

        try (Activity updating = reporter.start("Updating Launcher")) {
            if (Files.isRegularFile(updateMarker)) {
                log.warn("Found existing update marker");
                if (System.currentTimeMillis() - Files.getLastModifiedTime(updateMarker).toMillis() > 60_000) {
                    log.warn("Stale update marker found, removing");
                    Files.delete(updateMarker);
                } else {
                    throw new IllegalStateException("Update in progress by another launcher");
                }
            }

            // found a newer version to install.
            hive.execute(new FetchOperation().addManifest(launcher).setRemote(descriptor.host));

            // write to target directory
            next = UpdateHelper.prepareUpdateDirectory(updateDir);
            hive.execute(new ExportOperation().setManifest(launcher).setTarget(next));

            // create a marker for others which will gain the lock after we exit for restart.
            Files.createFile(updateMarker);

            // Signal that a new update is available
            log.info("Restarting...");
            doExit(UpdateHelper.CODE_UPDATE);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create update marker");
        }
    }

    /**
     * Installs the application with all requirements in case it is not already installed
     */
    private void installApplication(BHive hive, LauncherSplash splash, ActivityReporter reporter,
            ClientApplicationConfiguration clientAppCfg) {
        ApplicationConfiguration appCfg = clientAppCfg.appConfig;

        // Check if the application directory is already present
        String appName = appCfg.name;
        Collection<String> missing = Collections.emptyList();
        try {
            missing = getMissingArtifacts(hive, clientAppCfg);
            if (missing.isEmpty()) {
                log.info("Application is already installed. Nothing to install/update.");
                return;
            }
            log.info("Missing artifacts {}", missing);
        } catch (Exception e) {
            log.warn("Cannot verify that application is installed, re-installing", e);
        }

        // Throw an exception if we do not have write permissions in the directory
        if (readOnlyRootDir) {
            throw new SoftwareUpdateException(appName, "Missing artifacts: " + missing.stream().collect(Collectors.joining(",")));
        }

        Key appKey = appCfg.application;
        log.info("Starting installation of application {}", appKey);

        // Fetch the application and all the requirements
        try (Activity info = reporter.start("Downloading...")) {
            log.info("Fetching manifests from server...");
            FetchOperation fetchOp = new FetchOperation().setHiveName(descriptor.groupId).setRemote(descriptor.host);
            fetchOp.addManifest(appKey);
            clientAppCfg.resolvedRequires.forEach(fetchOp::addManifest);

            TransferStatistics stats = hive.execute(fetchOp);
            if (stats.sumManifests == 0) {
                log.info("Local hive already contains all required arfifacts. No manifests where fetched.");
            } else {
                log.info("Fetched {} manifests from server. Total size {}. Duration {}", stats.sumManifests,
                        UnitHelper.formatFileSize(stats.transferSize), DurationHelper.formatDuration(stats.duration));
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
        newConfig.requiredSoftware.addAll(applications);
        manifest.update(descriptor.applicationId, newConfig);

        // Calculate the difference which software is not required anymore
        log.info("Application successfully installed.");
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
        if (!appDir.toFile().exists()) {
            missing.add("Directory: " + clientAppCfg.appConfig.uid);
        }

        // The software that we need must be in the pool
        List<Manifest.Key> applications = new ArrayList<>();
        applications.add(clientAppCfg.appConfig.application);
        applications.addAll(clientAppCfg.resolvedRequires);
        for (Manifest.Key app : applications) {
            Path expectedPath = poolDir.resolve(app.directoryFriendlyName());
            if (!expectedPath.toFile().exists()) {
                missing.add("Pooled-App: " + app);
            }
        }

        // Meta-Manifest about the installation must be there
        // and must refer to what the application actually requires
        ClientSoftwareManifest manifest = new ClientSoftwareManifest(hive);
        ClientSoftwareConfiguration config = manifest.readNewest(clientAppCfg.appConfig.uid);
        if (config == null) {
            missing.add("Meta-Manifest:" + clientAppCfg.appConfig.uid);
        } else {
            // Check that all required apps are listed
            applications.removeAll(config.requiredSoftware);
            if (!applications.isEmpty()) {
                missing.add("Meta-Manifest-Entry: " + applications);
            }
        }
        return missing;
    }

    /**
     * Launches the client process using the given configuration.
     */
    private Process launchApplication(ClientApplicationConfiguration clientCfg) {
        log.info("Attempting to launch application.");
        ApplicationConfiguration appCfg = clientCfg.appConfig;

        // General resolvers
        CompositeResolver resolvers = new CompositeResolver();
        resolvers.add(new ApplicationVariableResolver(appCfg));
        resolvers.add(new DelayedVariableResolver(resolvers));
        resolvers.add(new InstanceVariableResolver(clientCfg.instanceConfig));
        resolvers.add(new OsVariableResolver());
        resolvers.add(new EnvironmentVariableResolver());
        resolvers.add(new ParameterValueResolver(new ApplicationParameterProvider(clientCfg.instanceConfig)));

        // Enable resolving of path variables
        DeploymentPathProvider pathProvider = new DeploymentPathProvider(appDir, "1");
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
     * Installs the launcher required to launch the application side-by-side to this launcher. If the launcher is already
     * installed then nothing will be done.
     */
    private void doInstallSideBySide(BHive hive, Entry<Version, Key> requiredLauncher) {
        // Check if the launcher is already installed
        Version version = requiredLauncher.getKey();
        Path homeDir = ClientPathHelper.getHome(rootDir, version);
        Path nativeLauncher = ClientPathHelper.getNativeLauncher(homeDir);
        if (nativeLauncher.toFile().exists()) {
            return;
        }

        // Install missing launcher
        log.info("Installing required launcher {}...", version);
        if (PathHelper.isReadOnly(homeDir)) {
            throw new SoftwareUpdateException("launcher", "Installed=" + runningVersion.toString() + " Required=" + version);
        }

        // found a newer version to install.
        Key launcher = requiredLauncher.getValue();
        hive.execute(new FetchOperation().addManifest(launcher).setRemote(descriptor.host));

        // write to target directory
        Path launcherHome = homeDir.resolve(ClientPathHelper.LAUNCHER_DIR);
        hive.execute(new ExportOperation().setManifest(launcher).setTarget(launcherHome));

        // Write manifest entry that the launcher needs to be retained
        ClientSoftwareManifest manifest = new ClientSoftwareManifest(hive);
        ClientSoftwareConfiguration newConfig = new ClientSoftwareConfiguration();
        newConfig.launcher = launcher;
        manifest.update(descriptor.applicationId, newConfig);
        log.info("Launcher successfully installed: {}", version);
    }

    /**
     * Delegates launching of the application to the given version of the launcher.
     */
    private Process doDelegateLaunch(Version version, String appDescriptor) {
        Path homeDir = ClientPathHelper.getHome(rootDir, version);
        Path nativeLauncher = ClientPathHelper.getNativeLauncher(homeDir);
        log.info("Launching application {} using launcher version {}", descriptor.applicationId, version);

        List<String> command = new ArrayList<>();
        command.add(nativeLauncher.toFile().getAbsolutePath());
        command.add(appDescriptor);

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
    private Map.Entry<Version, Key> getLatestLauncherVersion(ActivityReporter reporter, Version serverVersion) {
        OperatingSystem runningOs = OsHelper.getRunningOs();

        // Fetch all versions and filter out the one that corresponds to the server version
        boolean serverIsUndefined = VersionHelper.isUndefined(serverVersion);
        NavigableMap<Version, Key> versions = new TreeMap<>(VersionComparator.NEWEST_LAST);
        try (RemoteBHive rbh = RemoteBHive.forService(descriptor.host, null, reporter);
                Activity check = reporter.start("Fetching launcher versions....")) {
            String launcherKey = UpdateHelper.SW_META_PREFIX + UpdateHelper.SW_LAUNCHER;
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
        return null;
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
