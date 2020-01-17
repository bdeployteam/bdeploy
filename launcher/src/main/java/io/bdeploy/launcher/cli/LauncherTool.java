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
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.op.ExportOperation;
import io.bdeploy.bhive.op.ManifestDeleteOperation;
import io.bdeploy.bhive.op.ManifestListOperation;
import io.bdeploy.bhive.op.PruneOperation;
import io.bdeploy.bhive.op.remote.FetchOperation;
import io.bdeploy.bhive.op.remote.TransferStatistics;
import io.bdeploy.bhive.remote.RemoteBHive;
import io.bdeploy.bhive.util.StorageHelper;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.ActivityReporter.Activity;
import io.bdeploy.common.Version;
import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cfg.Configuration.Validator;
import io.bdeploy.common.cfg.ExistingPathValidator;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.cli.ToolBase.ConfiguredCliTool;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.OsHelper;
import io.bdeploy.common.util.OsHelper.OperatingSystem;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.common.util.TemplateHelper;
import io.bdeploy.common.util.UnitHelper;
import io.bdeploy.common.util.VersionHelper;
import io.bdeploy.interfaces.ScopedManifestKey;
import io.bdeploy.interfaces.UpdateHelper;
import io.bdeploy.interfaces.configuration.dcu.ApplicationConfiguration;
import io.bdeploy.interfaces.configuration.instance.ClientApplicationConfiguration;
import io.bdeploy.interfaces.configuration.pcu.ProcessConfiguration;
import io.bdeploy.interfaces.descriptor.application.ApplicationBrandingDescriptor;
import io.bdeploy.interfaces.descriptor.client.ClickAndStartDescriptor;
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

    public @interface LauncherConfig {

        @Help("Launch file (*.bdeploy). This can be given directly to the executable as single argument as well.")
        @Validator(ExistingPathValidator.class)
        String launch();

        @Help("Directory where the launcher stores the hive as well as all applications. Defaults to home/.bdeploy.")
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

    /** The home-directory for the hive */
    private Path rootDir;

    /** The path where to store updates */
    private Path updateDir;

    /** Path where the hive is stored */
    private Path bhiveDir;

    /** Path where all apps are stored */
    private Path appsDir;

    /** Path where the launched app is stored */
    private Path appDir;

    /** Path where the pooled products and artifacts are stored */
    private Path poolDir;

    /** The user-directory for the hive */
    private Path userArea;

    /** The launch descriptor */
    private ClickAndStartDescriptor descriptor;

    /** Indicates whether or not the root is read-only */
    private boolean readOnlyRootDir;

    public LauncherTool() {
        super(LauncherConfig.class);
    }

    @Override
    protected void run(LauncherConfig config) {
        try {
            doInit(config);

            // Show splash and progress of operations
            LauncherSplash splash = new LauncherSplash(appDir);
            splash.show();

            // Write audit logs to the user area if set
            Auditor auditor = null;
            try {
                if (userArea != null) {
                    auditor = new RollingFileAuditor(userArea.resolve("logs"));
                }
                LauncherSplashReporter reporter = new LauncherSplashReporter(splash);
                try (BHive hive = new BHive(bhiveDir.toUri(), auditor, reporter)) {
                    doCheckForLauncherUpdate(hive, reporter);
                    doLaunch(hive, reporter, splash, !config.dontWait());
                }
            } finally {
                if (auditor != null) {
                    auditor.close();
                }
            }
        } catch (SoftwareUpdateException ex) {
            log.error("Software update cannot be installed.", ex);
            if (config.exitOnError()) {
                helpAndFail(ex.getMessage());
            }
            LauncherUpdateDialog dialog = new LauncherUpdateDialog();
            dialog.showUpdateRequired(ex);
        } catch (Exception ex) {
            log.error("Failed to launch application.", ex);
            if (config.exitOnError()) {
                helpAndFail(ex.getMessage());
            }
            LauncherErrorDialog dialog = new LauncherErrorDialog();
            dialog.showError(ex);
        }
    }

    /** Initializes all parameters based on the given configuration */
    private void doInit(LauncherConfig config) {
        if (config.launch() == null) {
            throw new IllegalStateException("Missing --launch argument");
        }

        // Check where to put local data.
        if (config.homeDir() != null && !config.homeDir().isEmpty()) {
            rootDir = Paths.get(config.homeDir());
        } else {
            rootDir = ClientPathHelper.getBDeployHome();
        }
        rootDir = rootDir.toAbsolutePath();
        updateDir = PathHelper.ofNullableStrig(config.updateDir());

        // Check if a user-specific directory should be used
        userArea = PathHelper.ofNullableStrig(System.getenv("BDEPLOY_USER_AREA"));
        if (userArea != null) {
            userArea = userArea.toAbsolutePath();
        }

        // User-Area must be provided when the root is read-only
        if (PathHelper.isReadOnly(rootDir) && (userArea == null || PathHelper.isReadOnly(userArea))) {
            throw new IllegalStateException("A user area must be provided when the home directory is read-only");
        }

        Path descriptorFile = Paths.get(config.launch());
        try (InputStream is = Files.newInputStream(descriptorFile)) {
            descriptor = StorageHelper.fromStream(is, ClickAndStartDescriptor.class);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + config.launch(), e);
        }
        bhiveDir = rootDir.resolve("bhive");
        appsDir = rootDir.resolve("apps");
        poolDir = appsDir.resolve("pool");
        appDir = appsDir.resolve(descriptor.applicationId);
        readOnlyRootDir = PathHelper.isReadOnly(rootDir);

        log.info("Using home directory {}{}", rootDir, readOnlyRootDir ? " (readonly)" : "");
        if (userArea != null) {
            log.info("Using user-area {}", userArea);
            if (PathHelper.isReadOnly(userArea)) {
                throw new IllegalStateException("The user area '" + userArea + "' must be writable. Check permissions.");
            }
        }
    }

    private void doLaunch(BHive hive, LauncherSplashReporter reporter, LauncherSplash splash, boolean waitForExit) {
        log.info("Launching {} / {} / {} from {}", descriptor.groupId, descriptor.instanceId, descriptor.applicationId,
                descriptor.host.getUri());
        MasterRootResource master = ResourceProvider.getResource(descriptor.host, MasterRootResource.class, null);
        MasterNamedResource namedMaster = master.getNamedMaster(descriptor.groupId);

        // Fetch more information from the remote server.
        ClientApplicationConfiguration clientAppCfg;
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
        boolean updateInstalled = installApplication(hive, splash, reporter, clientAppCfg);

        // Launch the application
        Process p;
        try (Activity info = reporter.start("Launching...")) {
            p = launchApplication(hive, clientAppCfg);
        }
        reporter.stop();
        splash.dismiss();

        // Cleanup the installation directory and the hive.
        if (updateInstalled) {
            ClientAppCleanup cleanup = new ClientAppCleanup(hive, poolDir);
            cleanup.run();
        }

        // Wait for the process to exit
        if (waitForExit) {
            try {
                int exitCode = p.waitFor();
                log.info("Application terminated with exit code {}.", exitCode);
            } catch (InterruptedException e) {
                log.warn("Waiting for application exit interrupted.");
                Thread.currentThread().interrupt();
            }
        } else {
            log.info("Detaching...");
        }
    }

    private void doCheckForLauncherUpdate(BHive hive, ActivityReporter reporter) {
        String running = VersionHelper.readVersion();
        if (VersionHelper.UNKNOWN.equals(running)) {
            log.info("Skipping update, as running version cannot be determined");
            return;
        }

        Version currentVersion = VersionHelper.parse(running);
        try (RemoteBHive rbh = RemoteBHive.forService(descriptor.host, null, reporter)) {
            Version mostCurrent = null;
            Map<String, Key> byTagForCurrentOs = new TreeMap<>();
            try (Activity check = reporter.start("Checking for launcher updates...")) {
                SortedMap<Key, ObjectId> launchers = rbh
                        .getManifestInventory(UpdateHelper.SW_META_PREFIX + UpdateHelper.SW_LAUNCHER);

                OperatingSystem runningOs = OsHelper.getRunningOs();

                // map to ScopedManifestKey, filter by OS, memorize key, map to tag, map to Version, reverse sorted
                List<Version> available = launchers.keySet().stream().map(ScopedManifestKey::parse)
                        .filter(s -> s.getOperatingSystem() == runningOs).peek(s -> byTagForCurrentOs.put(s.getTag(), s.getKey()))
                        .map(ScopedManifestKey::getTag).map(VersionHelper::parse).sorted(Collections.reverseOrder())
                        .collect(Collectors.toList());

                // the first element in the collection is the one with the highest version number.
                if (!available.isEmpty()) {
                    mostCurrent = available.get(0);
                }
            }

            if (mostCurrent == null) {
                log.warn("Cannot find any launcher version on the server");
                return;
            }

            if (mostCurrent.compareTo(currentVersion) <= 0) {
                log.info("No updates found (running={}, newest={}), continue...", currentVersion, mostCurrent);
                return;
            }

            doInstallLauncherUpdate(descriptor.host, hive, reporter, currentVersion, mostCurrent,
                    byTagForCurrentOs.get(mostCurrent.toString()));
        }
    }

    @SuppressFBWarnings("DM_EXIT")
    private void doInstallLauncherUpdate(RemoteService remote, BHive hive, ActivityReporter reporter, Version currentVersion,
            Version mostCurrent, Key key) {

        // Check if we have write permissions to install the update
        if (PathHelper.isReadOnly(rootDir)) {
            throw new SoftwareUpdateException("launcher",
                    "Installed=" + currentVersion.toString() + " Available=" + mostCurrent.toString());
        }

        log.info("New launcher found, updating from {} to {}", currentVersion, mostCurrent);
        try (Activity updating = reporter.start("Updating launcher to " + mostCurrent)) {
            // found a newer version to install.
            hive.execute(new FetchOperation().addManifest(key).setRemote(remote));

            // write to target directory
            Path next = UpdateHelper.prepareUpdateDirectory(updateDir);
            hive.execute(new ExportOperation().setManifest(key).setTarget(next));

            try {
                // cleanup all old versions of the launcher
                SortedSet<Key> allVersions = hive.execute(
                        new ManifestListOperation().setManifestName(UpdateHelper.SW_META_PREFIX + UpdateHelper.SW_LAUNCHER));
                allVersions.stream()
                        .filter(k -> !k.getTag().equals(mostCurrent.toString()) && !k.getTag().equals(currentVersion.toString()))
                        .forEach(k -> hive.execute(new ManifestDeleteOperation().setToDelete(k)));

                hive.execute(new PruneOperation());
            } finally {
                System.exit(UpdateHelper.CODE_UPDATE);
            }
        }
    }

    /**
     * Installs the application with all requirements in case it is not already installed
     */
    private boolean installApplication(BHive hive, LauncherSplash splash, ActivityReporter reporter,
            ClientApplicationConfiguration clientAppCfg) {
        ApplicationConfiguration appCfg = clientAppCfg.appConfig;

        // Check if the application directory is already present
        Collection<String> missing = getMissingArtifacts(hive, clientAppCfg);
        if (missing.isEmpty()) {
            log.info("Application is already installed. Nothing to install/update.");
            return false;
        }
        Key appKey = appCfg.application;
        log.info("Starting installation of application {}", appKey);
        log.info("Missing artifacts {}", missing);

        // Throw an exception if we do not have write permissions in the directory
        String appName = appCfg.name;
        if (readOnlyRootDir) {
            throw new SoftwareUpdateException(appName, "Missing artifacts: " + missing.stream().collect(Collectors.joining(",")));
        }

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
                log.info("Fetched {} manifests from server. Total size {}", stats.sumManifests,
                        UnitHelper.formatFileSize(stats.transferSize));
            }
        }

        // Export the application into the pool
        List<Manifest.Key> applications = new ArrayList<>();
        applications.add(appCfg.application);
        applications.addAll(clientAppCfg.resolvedRequires);

        log.info("Installing application and dependencies into the pool...", appKey);
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
            splash.storeSplashData(branding.splash);
        }

        // Protocol the installation
        ClientSoftwareManifest manifest = new ClientSoftwareManifest(hive);

        ClientSoftwareConfiguration newConfig = new ClientSoftwareConfiguration();
        newConfig.requiredSoftware.addAll(applications);
        manifest.update(appCfg.uid, newConfig);

        // Calculate the difference which software is not required anymore
        log.info("Application successfully installed.");
        return true;
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
    private Process launchApplication(BHive hive, ClientApplicationConfiguration clientCfg) {
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
        DeploymentPathProvider pathProvider = new DeploymentPathProvider(appsDir, "1");
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
            Process p = b.start();
            log.info("Application successfully launched. PID={}", p.pid());
            return p;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot start " + appCfg.uid, e);
        }
    }

}
