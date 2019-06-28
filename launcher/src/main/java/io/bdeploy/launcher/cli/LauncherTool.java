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
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.cli.ToolBase.ConfiguredCliTool;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.OsHelper;
import io.bdeploy.common.util.OsHelper.OperatingSystem;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.common.util.VersionHelper;
import io.bdeploy.dcu.InstanceNodeController;
import io.bdeploy.interfaces.ScopedManifestKey;
import io.bdeploy.interfaces.UpdateHelper;
import io.bdeploy.interfaces.configuration.instance.ClientApplicationConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceNodeConfiguration;
import io.bdeploy.interfaces.configuration.pcu.ProcessConfiguration;
import io.bdeploy.interfaces.configuration.pcu.ProcessGroupConfiguration;
import io.bdeploy.interfaces.descriptor.client.ClickAndStartDescriptor;
import io.bdeploy.interfaces.manifest.InstanceNodeManifest;
import io.bdeploy.interfaces.remote.MasterNamedResource;
import io.bdeploy.interfaces.remote.MasterRootResource;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.interfaces.variables.DeploymentPathProvider.SpecialDirectory;
import io.bdeploy.launcher.cli.LauncherTool.LauncherConfig;
import io.bdeploy.launcher.cli.branding.LauncherSplash;
import io.bdeploy.launcher.cli.branding.LauncherSplashReporter;

@CliName("launcher")
@Help("A tool which launches an application described by a '.bdeploy' file")
public class LauncherTool extends ConfiguredCliTool<LauncherConfig> {

    private static final Logger log = LoggerFactory.getLogger(LauncherTool.class);

    public @interface LauncherConfig {

        @Help("Launch file (*.bdeploy). This can be given directly to the executable as single argument as well.")
        String launch();

        @Help("Local working directory for the launcher, defaults to the user's home/.bdeploy.")
        String cacheDir();

        @Help("Set by the launcher script to determine the directory where to put updates for automatic application.")
        String updateDir();

        @Help(value = "Makes the launcher quit immediately after updating and launching the application.", arg = false)
        boolean dontWait() default false;

    }

    public LauncherTool() {
        super(LauncherConfig.class);
    }

    @Override
    protected void run(LauncherConfig config) {
        helpAndFailIfMissing(config.launch(), "Missing --launch");

        Path cfg = Paths.get(config.launch());
        if (!Files.exists(cfg)) {
            helpAndFail("Given bdeploy file does not exist: " + cfg);
        }

        // 0: check where to put local temporary data.
        Path rootDir = Paths.get(System.getProperty("user.home")).resolve(".bdeploy");
        if (config.cacheDir() != null && !config.cacheDir().isEmpty()) {
            Path check = Paths.get(config.cacheDir());
            if (!Files.isDirectory(check)) {
                PathHelper.mkdirs(check);
            }
            rootDir = check;
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

        // 1: Launch application. Show dialog if launching fails
        log.info("Using cache directory {}", rootDir);
        try {
            doLaunchFromConfig(cfg, rootDir.toAbsolutePath(), config.updateDir(), !config.dontWait());
        } catch (Exception ex) {
            LauncherErrorDialog dialog = new LauncherErrorDialog();
            dialog.showError(ex);
        }
    }

    private static void doLaunchFromConfig(Path cfg, Path rootDir, String updateDir, boolean wait) {
        ClickAndStartDescriptor cd;
        try (InputStream is = Files.newInputStream(cfg)) {
            cd = StorageHelper.fromStream(is, ClickAndStartDescriptor.class);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + cfg, e);
        }

        LauncherSplash splash = new LauncherSplash(cd, rootDir.resolve(".splash"));
        splash.show();

        LauncherSplashReporter reporter = new LauncherSplashReporter(splash);

        try (BHive hive = new BHive(rootDir.resolve("bhive").toUri(), reporter)) {
            // 0: check for launcher updates.
            doCheckForUpdate(cd, hive, reporter, updateDir);

            // yay, we have information
            log.info("Launching {} / {} / {} from {}", cd.groupId, cd.instanceId, cd.applicationId, cd.host.getUri());

            MasterRootResource master = ResourceProvider.getResource(cd.host, MasterRootResource.class);
            MasterNamedResource namedMaster = master.getNamedMaster(cd.groupId);

            // 1: fetch more information from the remote server.
            ClientApplicationConfiguration appCfg;
            try (Activity info = reporter.start("Loading meta-data...")) {
                // fails with 404 or other error, but never null.
                log.info("fetching information from {}", cd.host.getUri());
                appCfg = namedMaster.getClientConfiguration(cd.instanceId, cd.applicationId);
            }

            splash.update(appCfg);

            // this /might/ lead to too much re-installs if unrelated things change, but there is not
            // other/easy way (currently) to detect this...
            Manifest.Key targetClientKey = new Manifest.Key(cd.applicationId, appCfg.instanceKey.getTag());

            if (hive.execute(new ManifestListOperation().setManifestName(targetClientKey.toString())).contains(targetClientKey)) {
                // we have it already, no need to re-create.
                log.info("re-using existing manifest: {}", targetClientKey);
            } else {
                log.info("fetching updated applications/configurations for {}", targetClientKey);
                try (Activity info = reporter.start("Loading requirements...")) {
                    // 2: fetch the underlying application and it's requirements into local cache (BHive)
                    fetchApplicationAndRequirements(hive, cd, appCfg);
                }

                try (Activity info = reporter.start("Preparing configuration...")) {
                    // FIXME: DCS-396: client config shall not contain server config files.
                    // download configuration data.
                    //if (appCfg.configTreeId != null) {
                    //    Path tmpCfg = namedMaster.getClientInstanceConfiguration(appCfg.instanceKey);
                    //    try (BHive tmpHive = new BHive(UriBuilder.fromUri("jar:" + tmpCfg.toUri()).build(), reporter)) {
                    //        CopyOperation copyAll = new CopyOperation().setDestinationHive(hive);
                    //        tmpHive.execute(copyAll);
                    //    }
                    //}

                    // 3: generate a 'fake' instanceNodeManifest from the existing configuration for local caching.
                    createInstanceNodeManifest(appCfg, targetClientKey, hive);
                }
            }

            InstanceNodeController controller;
            try (Activity info = reporter.start("Updating application...")) {
                // 4: install using DCU to the target directory
                controller = installApplication(rootDir, targetClientKey, hive);
            }

            Process p;
            try (Activity info = reporter.start("Launching...")) {
                // 5: launch.
                p = launchProcess(targetClientKey, controller);
            }

            reporter.stop();
            splash.dismiss();

            // 6. cleanup old versions. keep the last 2 versions.
            cleanupOldInstalls(rootDir, cd, hive);

            // 7. wait for the process to exit
            if (wait) {
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
    }

    @SuppressFBWarnings("DM_EXIT")
    private static void doCheckForUpdate(ClickAndStartDescriptor cd, BHive hive, ActivityReporter reporter, String updateDir) {
        String running = VersionHelper.readVersion();
        if (VersionHelper.UNKNOWN.equals(running)) {
            log.info("Skipping update, as running version cannot be determined");
            return;
        }

        Version currentVersion = Version.parse(running);
        try (RemoteBHive rbh = RemoteBHive.forService(cd.host, null, reporter)) {
            Version mostCurrent = null;
            Map<String, Key> byTagForCurrentOs = new TreeMap<>();
            try (Activity check = reporter.start("Checking for launcher updates...")) {
                SortedMap<Key, ObjectId> launchers = rbh
                        .getManifestInventory(UpdateHelper.SW_META_PREFIX + UpdateHelper.SW_LAUNCHER);

                OperatingSystem runningOs = OsHelper.getRunningOs();

                // map to ScopedManifestKey, filter by OS, memorize key, map to tag, map to Version, reverse sorted
                List<Version> available = launchers.keySet().stream().map(ScopedManifestKey::parse)
                        .filter(s -> s.getOperatingSystem() == runningOs).peek(s -> byTagForCurrentOs.put(s.getTag(), s.getKey()))
                        .map(s -> s.getTag()).map(Version::parse).sorted(Collections.reverseOrder()).collect(Collectors.toList());

                // the first element in the collection is the one with the highest version number.
                if (!available.isEmpty()) {
                    mostCurrent = available.get(0);
                }
            }

            if (mostCurrent == null) {
                log.warn("Cannot find any launcher version on the server");
                return;
            }

            if (mostCurrent.compareTo(currentVersion) > 0) {
                log.info("New launcher found, updating from {} to {}", currentVersion, mostCurrent);
                doInstallUpdate(cd.host, hive, reporter, updateDir, currentVersion, mostCurrent,
                        byTagForCurrentOs.get(mostCurrent.toString()));
            } else {
                log.info("No updates found (running={}, newest={}), continue...", currentVersion, mostCurrent);
            }
        }
    }

    private static void doInstallUpdate(RemoteService remote, BHive hive, ActivityReporter reporter, String updateDir,
            Version currentVersion, Version mostCurrent, Key key) {
        try (Activity updating = reporter.start("Updating launcher to " + mostCurrent)) {
            // found a newer version to install.
            hive.execute(new FetchOperation().addManifest(key).setRemote(remote));

            // write to target directory
            Path next = UpdateHelper.prepareUpdateDirectory(Paths.get(updateDir));
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
    private static void fetchApplicationAndRequirements(BHive hive, ClickAndStartDescriptor cd,
            ClientApplicationConfiguration appCfg) {
        FetchOperation fetchOp = new FetchOperation().setHiveName(cd.groupId).setRemote(cd.host)
                .addManifest(appCfg.clientConfig.application);
        appCfg.resolvedRequires.forEach(fetchOp::addManifest);
        hive.execute(fetchOp);
    }

    /**
     * Creates a new InstanceNodeManifest which is used to "fake" a node (this client). The manifest will have the same tag as the
     * instance, so we can determine whether we have the client for a certain instance tag already.
     */
    private static void createInstanceNodeManifest(ClientApplicationConfiguration appCfg, Manifest.Key targetClientKey,
            BHive hive) {
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
    private static InstanceNodeController installApplication(Path rootDir, Manifest.Key targetClientKey, BHive hive) {
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
     * Cleanup the installation directory and the hive. Keeps a max. of 2 existing installations per client around.
     */
    private static void cleanupOldInstalls(Path rootDir, ClickAndStartDescriptor cd, BHive hive) {
        hive.execute(new ManifestDeleteOldByIdOperation().setAmountToKeep(2).setRunGarbageCollector(true)
                .setToDelete(cd.applicationId).setPreDeleteHook(k -> {
                    log.info("uninstall and delete old version {}", k);
                    InstanceNodeController c = new InstanceNodeController(hive, rootDir, InstanceNodeManifest.of(hive, k));
                    if (c.isInstalled()) {
                        c.uninstall();
                    }
                }));
    }

    /**
     * Launches the client process.
     */
    private static Process launchProcess(Manifest.Key targetClientKey, InstanceNodeController controller) {
        log.info("launching {}", targetClientKey);
        ProcessGroupConfiguration pgc = controller.getProcessGroupConfiguration();
        ProcessConfiguration pc = pgc.applications.get(0);

        // INHERIT causes problems when debugging but is what we actually want in real life, attention.
        ProcessBuilder b = new ProcessBuilder(pc.start).redirectError(Redirect.INHERIT).redirectInput(Redirect.INHERIT)
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

}
