package io.bdeploy.launcher.cli.ui.browser;

import java.nio.file.Path;
import java.util.Collection;

import javax.swing.SwingWorker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.BHive;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.interfaces.configuration.instance.ClientApplicationConfiguration;
import io.bdeploy.interfaces.descriptor.client.ClickAndStartDescriptor;
import io.bdeploy.interfaces.remote.MasterNamedResource;
import io.bdeploy.interfaces.remote.MasterRootResource;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.launcher.cli.ClientApplicationDto;
import io.bdeploy.launcher.cli.ClientSoftwareConfiguration;
import io.bdeploy.launcher.cli.ClientSoftwareManifest;
import io.bdeploy.launcher.cli.LauncherTool;

/**
 * A worker that refreshes the details of the installed applications.
 */
public class AppRefresher extends SwingWorker<Void, Object> {

    private static final Logger log = LoggerFactory.getLogger(LauncherTool.class);

    private final Path rootDir;
    private final Collection<ClientSoftwareConfiguration> apps;

    public AppRefresher(Path rootDir, Collection<ClientSoftwareConfiguration> apps) {
        this.rootDir = rootDir;
        this.apps = apps;
    }

    @Override
    protected Void doInBackground() throws Exception {
        log.info("Fetching configurations...");
        int i = 0;
        try (BHive hive = new BHive(rootDir.resolve("bhive").toUri(), new ActivityReporter.Null())) {
            for (ClientSoftwareConfiguration app : apps) {
                try {
                    log.info("Updating {}", app.clickAndStart.applicationId);
                    setProgress(i++);
                    doUpdate(hive, app);
                } catch (Exception ex) {
                    log.error("Failed to fetch configuration", ex);
                }
            }
        }
        log.info("Fetching done.");
        return null;
    }

    private void doUpdate(BHive hive, ClientSoftwareConfiguration app) {
        ClickAndStartDescriptor clickAndStart = app.clickAndStart;

        MasterRootResource master = ResourceProvider.getVersionedResource(clickAndStart.host, MasterRootResource.class, null);
        MasterNamedResource namedMaster = master.getNamedMaster(clickAndStart.groupId);
        ClientApplicationConfiguration cac = namedMaster.getClientConfiguration(clickAndStart.instanceId,
                clickAndStart.applicationId);

        ClientSoftwareManifest manifest = new ClientSoftwareManifest(hive);
        ClientSoftwareConfiguration csc = manifest.readNewest(clickAndStart.applicationId);
        csc.metadata = ClientApplicationDto.create(clickAndStart, cac);
        manifest.update(clickAndStart.applicationId, csc);
    }

}
