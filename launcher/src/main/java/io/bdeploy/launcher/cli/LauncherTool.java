package io.bdeploy.launcher.cli;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ProcessBuilder.Redirect;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.op.ManifestDeleteOldByIdOperation;
import io.bdeploy.bhive.op.ManifestListOperation;
import io.bdeploy.bhive.op.remote.FetchOperation;
import io.bdeploy.bhive.util.StorageHelper;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.cli.ToolBase.ConfiguredCliTool;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.dcu.InstanceNodeController;
import io.bdeploy.interfaces.configuration.instance.ClientApplicationConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceNodeConfiguration;
import io.bdeploy.interfaces.configuration.pcu.ProcessConfiguration;
import io.bdeploy.interfaces.configuration.pcu.ProcessGroupConfiguration;
import io.bdeploy.interfaces.descriptor.client.ClientDescriptor;
import io.bdeploy.interfaces.manifest.ApplicationManifest;
import io.bdeploy.interfaces.manifest.InstanceNodeManifest;
import io.bdeploy.interfaces.remote.MasterNamedResource;
import io.bdeploy.interfaces.remote.MasterRootResource;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.interfaces.variables.DeploymentPathProvider.SpecialDirectory;
import io.bdeploy.launcher.cli.LauncherTool.LauncherConfig;

@CliName("launcher")
@Help("A tool which launches an application described by a '.bdeploy' file")
public class LauncherTool extends ConfiguredCliTool<LauncherConfig> {

    private static final Logger log = LoggerFactory.getLogger(LauncherTool.class);

    public @interface LauncherConfig {

        @Help("Launch file (*.bdeploy). This can be given directly to the executable as single argument as well.")
        String launch();

        @Help("Local working directory for the launcher, defaults to the current working directory.")
        String cacheDir();

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
        Path rootDir = Paths.get(""); // current
        if (config.cacheDir() != null && !config.cacheDir().isEmpty()) {
            Path check = Paths.get(config.cacheDir());
            if (!Files.isDirectory(check)) {
                PathHelper.mkdirs(check);
            }
            rootDir = check;
        }

        doLaunchFromConfig(cfg, rootDir.toAbsolutePath().resolve(".bdeploy"), getActivityReporter());
    }

    private static void doLaunchFromConfig(Path cfg, Path rootDir, ActivityReporter reporter) {
        ClientDescriptor cd;
        try (InputStream is = Files.newInputStream(cfg)) {
            cd = StorageHelper.fromStream(is, ClientDescriptor.class);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + cfg, e);
        }

        // yay, we have information
        log.info("Launching " + cd.groupId + " / " + cd.instanceId + " / " + cd.clientId + " from " + cd.host.getUri());

        // 1: fetch more information from the remote server.
        MasterRootResource master = ResourceProvider.getResource(cd.host, MasterRootResource.class);
        MasterNamedResource namedMaster = master.getNamedMaster(cd.groupId);

        // fails with 404 or other error, but never null.
        log.info("fetching information from " + cd.host.getUri());
        ClientApplicationConfiguration appCfg = namedMaster.getClientConfiguration(cd.instanceId, cd.clientId);

        // this /might/ lead to too much re-installs if unrelated things change, but there is not
        // other/easy way (currently) to detect this...
        Manifest.Key targetClientKey = new Manifest.Key(cd.clientId, appCfg.instanceKey.getTag());

        try (BHive hive = new BHive(rootDir.resolve("bhive").toUri(), reporter)) {
            if (hive.execute(new ManifestListOperation().setManifestName(targetClientKey.toString())).contains(targetClientKey)) {
                // we have it already, no need to re-create.
                log.info("re-using existing manifest: " + targetClientKey);
            } else {
                log.info("fetching updated applications/configurations for " + targetClientKey);
                // 2: fetch the underlying application and it's requirements into local cache (BHive)
                fetchApplicationAndRequirements(hive, cd, appCfg);

                // 3: generate a 'fake' instanceNodeManifest from the existing configuration for local caching.
                createInstanceNodeManifest(appCfg, targetClientKey, hive);
            }

            // 4: install using DCU to the target directory
            InstanceNodeController controller = installApplication(rootDir, targetClientKey, hive);

            // 5: launch.
            Process p = launchProcess(targetClientKey, controller);

            // 6. cleanup old versions. keep the last 2 versions.
            cleanupOldInstalls(rootDir, cd, hive);

            // 7. wait for the process to exit
            try {
                p.waitFor();
            } catch (InterruptedException e) {
                log.warn("waiting for application exit interrupted");
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Fetch the application and all its requirements from the remote hive.
     */
    private static void fetchApplicationAndRequirements(BHive hive, ClientDescriptor cd, ClientApplicationConfiguration appCfg) {
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

        Path cfgTmp = null;
        try {
            if (!appCfg.clientDesc.configFiles.isEmpty()) {
                // TODO: not yet properly supported for clients... :|
                try {
                    cfgTmp = Files.createTempDirectory("cfg-");
                    ApplicationManifest amf = ApplicationManifest.of(hive, appCfg.clientConfig.application);
                    amf.exportConfigTemplatesTo(hive, cfgTmp);
                } catch (IOException e) {
                    log.error("Cannot write configuration files", e);
                }
            }

            new InstanceNodeManifest.Builder().setInstanceNodeConfiguration(fakeInc).setMinionName("client")
                    .setKey(targetClientKey).setConfigSource(cfgTmp).insert(hive);
        } finally {
            if (cfgTmp != null) {
                PathHelper.deleteRecursive(cfgTmp);
            }
        }
    }

    /**
     * Installs the client application using (and returning) the DCU.
     */
    private static InstanceNodeController installApplication(Path rootDir, Manifest.Key targetClientKey, BHive hive) {
        InstanceNodeManifest fakeInmf = InstanceNodeManifest.of(hive, targetClientKey);
        InstanceNodeController controller = new InstanceNodeController(hive, rootDir, fakeInmf);
        controller.addAdditionalVariableResolver(new LocalHostnameResolver());
        if (!controller.isInstalled()) {
            log.info("installing " + targetClientKey + " to " + rootDir);
            controller.install();
        } else {
            log.info("re-using previous installation of " + targetClientKey);
        }
        return controller;
    }

    /**
     * Cleanup the installation directory and the hive. Keeps a max. of 2 existing installations per client around.
     */
    private static void cleanupOldInstalls(Path rootDir, ClientDescriptor cd, BHive hive) {
        hive.execute(new ManifestDeleteOldByIdOperation().setAmountToKeep(2).setRunGarbageCollector(true).setToDelete(cd.clientId)
                .setPreDeleteHook(k -> {
                    log.info("uninstall and delete old version " + k);
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
        log.info("launching " + targetClientKey);
        ProcessGroupConfiguration pgc = controller.getProcessGroupConfiguration();
        ProcessConfiguration pc = pgc.applications.get(0);

        // INHERIT causes problems when debugging but is what we actually want in real life, attention.
        ProcessBuilder b = new ProcessBuilder(pc.start).redirectError(Redirect.INHERIT).redirectInput(Redirect.INHERIT)
                .redirectOutput(Redirect.INHERIT)
                .directory(controller.getDeploymentPathProvider().get(SpecialDirectory.RUNTIME).toFile());

        try {
            Process p = b.start();
            log.info("started " + targetClientKey + ", PID=" + p.pid());
            return p;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot start " + targetClientKey, e);
        }
    }

}
