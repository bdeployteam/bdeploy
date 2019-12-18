package io.bdeploy.launcher.cli;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ProcessBuilder.Redirect;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
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
import io.bdeploy.bhive.op.ManifestDeleteOldByIdOperation;
import io.bdeploy.bhive.op.ManifestDeleteOperation;
import io.bdeploy.bhive.op.ManifestListOperation;
import io.bdeploy.bhive.op.PruneOperation;
import io.bdeploy.bhive.op.remote.FetchOperation;
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
import io.bdeploy.common.util.VersionHelper;
import io.bdeploy.dcu.InstanceNodeController;
import io.bdeploy.interfaces.ScopedManifestKey;
import io.bdeploy.interfaces.UpdateHelper;
import io.bdeploy.interfaces.configuration.instance.ClientApplicationConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceNodeConfiguration;
import io.bdeploy.interfaces.configuration.pcu.ProcessConfiguration;
import io.bdeploy.interfaces.configuration.pcu.ProcessGroupConfiguration;
import io.bdeploy.interfaces.descriptor.application.ApplicationBrandingDescriptor;
import io.bdeploy.interfaces.descriptor.client.ClickAndStartDescriptor;
import io.bdeploy.interfaces.manifest.InstanceNodeManifest;
import io.bdeploy.interfaces.remote.MasterNamedResource;
import io.bdeploy.interfaces.remote.MasterRootResource;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.interfaces.variables.DeploymentPathProvider.SpecialDirectory;
import io.bdeploy.interfaces.variables.LocalHostnameResolver;
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
            if (userArea != null) {
                auditor = new RollingFileAuditor(userArea.resolve("logs"));
            }
            LauncherSplashReporter reporter = new LauncherSplashReporter(splash);
            try (BHive hive = new BHive(bhiveDir.toUri(), auditor, reporter)) {
                doLaunch(hive, reporter, splash, !config.dontWait());
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
        rootDir = Paths.get(System.getProperty("user.home")).resolve(".bdeploy");
        if (config.homeDir() != null && !config.homeDir().isEmpty()) {
            rootDir = Paths.get(config.homeDir());
        } else {
            String override = System.getenv("BDEPLOY_HOME");
            if (override != null && !override.isEmpty()) {
                rootDir = Paths.get(override);
            } else {
                override = System.getenv("LOCALAPPDATA");
                if (override != null && !override.isEmpty()) {
                    rootDir = Paths.get(override).resolve("BDeploy");
                }
            }
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
        appDir = appsDir.resolve(descriptor.applicationId);
        if (!Files.isDirectory(appDir)) {
            PathHelper.mkdirs(appDir);
        }
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
        // Check for launcher updates.
        doCheckForUpdate(hive, reporter);
        log.info("Launching {} / {} / {} from {}", descriptor.groupId, descriptor.instanceId, descriptor.applicationId,
                descriptor.host.getUri());

        MasterRootResource master = ResourceProvider.getResource(descriptor.host, MasterRootResource.class, null);
        MasterNamedResource namedMaster = master.getNamedMaster(descriptor.groupId);

        // Fetch more information from the remote server.
        ClientApplicationConfiguration appCfg;
        try (Activity info = reporter.start("Loading meta-data...")) {
            // fails with 404 or other error, but never null.
            log.info("fetching information from {}", descriptor.host.getUri());
            appCfg = namedMaster.getClientConfiguration(descriptor.instanceId, descriptor.applicationId);
        }

        // Update splash with the fetched branding information
        ApplicationBrandingDescriptor branding = appCfg.clientDesc.branding;
        if (branding != null) {
            if (appCfg.clientImageIcon != null) {
                splash.updateIconImage(appCfg.clientImageIcon);
            }
            if (appCfg.clientSplashData != null) {
                splash.updateSplashImage(appCfg.clientSplashData);
            }
            splash.updateSplashData(branding.splash);
        } else {
            log.warn("Client configuration does not contain any branding information.");
        }

        // this /might/ lead to too much re-installs if unrelated things change, but there is not
        // other/easy way (currently) to detect this...
        Manifest.Key targetClientKey = new Manifest.Key(descriptor.applicationId, appCfg.instanceKey.getTag());

        if (hive.execute(new ManifestListOperation().setManifestName(targetClientKey.toString())).contains(targetClientKey)) {
            // we have it already, no need to re-create.
            log.info("re-using existing manifest: {}", targetClientKey);
        } else {
            // Throw an exception if we do not have write permissions in the root directory
            if (readOnlyRootDir) {
                String installed = getAppVersions(hive);
                String available = appCfg.instanceKey.getTag();
                String name = appCfg.clientDesc.name + "( " + descriptor.applicationId + " )";
                throw new SoftwareUpdateException(name, installed, available);
            }

            log.info("fetching updated applications/configurations for {}", targetClientKey);
            try (Activity info = reporter.start("Loading requirements...")) {
                // fetch the underlying application and it's requirements into local cache (BHive)
                fetchApplicationAndRequirements(hive, appCfg);
            }

            try (Activity info = reporter.start("Preparing configuration...")) {
                // generate a 'fake' instanceNodeManifest from the existing configuration for local caching.
                createInstanceNodeManifest(appCfg, targetClientKey, hive);
            }

            // Store branding information on file-system
            if (appCfg.clientImageIcon != null) {
                splash.storeIconImage(branding.icon, appCfg.clientImageIcon);
            }
            if (appCfg.clientSplashData != null) {
                splash.storeSplashImage(branding.splash.image, appCfg.clientSplashData);
            }
            splash.storeSplashData(branding.splash);
        }

        InstanceNodeController controller;
        try (Activity info = reporter.start("Updating application...")) {
            // 4: install using DCU to the target directory
            controller = installApplication(appsDir, targetClientKey, hive);
        }

        Process p;
        try (Activity info = reporter.start("Launching...")) {
            // 5: launch.
            p = launchProcess(targetClientKey, controller);
        }

        reporter.stop();
        splash.dismiss();

        // 6. Cleanup the installation directory and the hive.
        // Keeps a max. of 2 existing installations per client around.
        if (!readOnlyRootDir) {
            hive.execute(new ManifestDeleteOldByIdOperation().setAmountToKeep(2).setRunGarbageCollector(true)
                    .setToDelete(descriptor.applicationId).setPreDeleteHook(k -> deleteVersion(hive, k)));
        }

        // 7. wait for the process to exit
        if (waitForExit) {
            try {
                p.waitFor();
            } catch (InterruptedException e) {
                log.warn("waiting for application exit interrupted");
                Thread.currentThread().interrupt();
            }
        } else {
            log.info("Detaching...");
        }
    }

    @SuppressFBWarnings("DM_EXIT")
    private void doCheckForUpdate(BHive hive, ActivityReporter reporter) {
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

            doInstallUpdate(descriptor.host, hive, reporter, currentVersion, mostCurrent,
                    byTagForCurrentOs.get(mostCurrent.toString()));
        }
    }

    private void doInstallUpdate(RemoteService remote, BHive hive, ActivityReporter reporter, Version currentVersion,
            Version mostCurrent, Key key) {

        // Check if we have write permissions to install the update
        if (PathHelper.isReadOnly(rootDir)) {
            throw new SoftwareUpdateException("launcher", currentVersion.toString(), mostCurrent.toString());
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
     * Fetch the application and all its requirements from the remote hive.
     */
    private void fetchApplicationAndRequirements(BHive hive, ClientApplicationConfiguration appCfg) {
        FetchOperation fetchOp = new FetchOperation().setHiveName(descriptor.groupId).setRemote(descriptor.host)
                .addManifest(appCfg.clientConfig.application);
        appCfg.resolvedRequires.forEach(fetchOp::addManifest);
        hive.execute(fetchOp);
    }

    /**
     * Creates a new InstanceNodeManifest which is used to "fake" a node (this client). The manifest will have the same tag as the
     * instance, so we can determine whether we have the client for a certain instance tag already.
     */
    private void createInstanceNodeManifest(ClientApplicationConfiguration appCfg, Manifest.Key targetClientKey, BHive hive) {
        InstanceNodeConfiguration fakeInc = new InstanceNodeConfiguration();
        fakeInc.applications.add(appCfg.clientConfig);
        fakeInc.name = appCfg.clientConfig.name;
        fakeInc.uuid = appCfg.clientConfig.uid;
        // FIXME: DCS-396: client config shall not contain server config files.
        //        ObjectId cfg = appCfg.configTreeId;
        ObjectId cfg = null;

        new InstanceNodeManifest.Builder().setInstanceNodeConfiguration(fakeInc).setMinionName("client").setKey(targetClientKey)
                .setConfigTreeId(cfg).insert(hive);
    }

    /**
     * Installs the client application using (and returning) the DCU.
     */
    private InstanceNodeController installApplication(Path rootDir, Manifest.Key targetClientKey, BHive hive) {
        InstanceNodeManifest fakeInmf = InstanceNodeManifest.of(hive, targetClientKey);
        InstanceNodeController controller = new InstanceNodeController(hive, rootDir, fakeInmf);
        controller.addAdditionalVariableResolver(new LocalHostnameResolver());
        if (!controller.isInstalled()) {
            log.info("installing {} to {}", targetClientKey, rootDir);
            controller.install();
        } else {
            log.info("re-using previous installation of {}", targetClientKey);
        }
        return controller;
    }

    /**
     * Removes the given version if installed
     */
    private void deleteVersion(BHive hive, Manifest.Key key) {
        try {
            log.info("Uninstall and delete old version {}", key);
            InstanceNodeManifest mf = InstanceNodeManifest.of(hive, key);
            InstanceNodeController c = new InstanceNodeController(hive, rootDir, mf);
            if (c.isInstalled()) {
                c.uninstall();
            }
        } catch (Exception ex) {
            log.error("Failed to uninstall version", ex);
        }
    }

    /**
     * Launches the client process.
     */
    private static Process launchProcess(Manifest.Key targetClientKey, InstanceNodeController controller) {
        ProcessGroupConfiguration pgc = controller.getProcessGroupConfiguration();
        ProcessConfiguration pc = pgc.applications.get(0);
        List<String> command = TemplateHelper.process(pc.start, controller.getResolver());
        log.info("launching {} using {}", targetClientKey, command);

        // INHERIT causes problems when debugging but is what we actually want in real life, attention.
        ProcessBuilder b = new ProcessBuilder(command).redirectError(Redirect.INHERIT).redirectInput(Redirect.INHERIT)
                .redirectOutput(Redirect.INHERIT)
                .directory(controller.getDeploymentPathProvider().get(SpecialDirectory.RUNTIME).toFile());
        try {
            Process p = b.start();
            log.info("started {}, PID={}", targetClientKey, p.pid());
            return p;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot start " + targetClientKey, e);
        }
    }

    /**
     * Returns the latest version that is installed for the given client
     */
    private String getAppVersions(BHive hive) {
        SortedSet<Manifest.Key> installed = hive.execute(new ManifestListOperation().setManifestName(descriptor.applicationId));
        if (installed.isEmpty()) {
            return "-";
        }
        return installed.stream().map(k -> k.getTag()).collect(Collectors.joining(","));
    }

}
