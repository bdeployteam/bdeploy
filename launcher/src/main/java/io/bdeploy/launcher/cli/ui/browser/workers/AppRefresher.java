package io.bdeploy.launcher.cli.ui.browser.workers;

import java.nio.file.Path;
import java.util.Collection;

import javax.swing.SwingWorker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.BHive;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.audit.Auditor;
import io.bdeploy.interfaces.configuration.instance.ClientApplicationConfiguration;
import io.bdeploy.interfaces.descriptor.client.ClickAndStartDescriptor;
import io.bdeploy.interfaces.remote.MasterNamedResource;
import io.bdeploy.interfaces.remote.MasterRootResource;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.launcher.LauncherPathProvider;
import io.bdeploy.launcher.LauncherPathProvider.SpecialDirectory;
import io.bdeploy.launcher.cli.ClientApplicationDto;
import io.bdeploy.launcher.cli.ClientSoftwareConfiguration;
import io.bdeploy.launcher.cli.ClientSoftwareManifest;
import io.bdeploy.logging.audit.RollingFileAuditor;

/**
 * A worker that refreshes the details of the installed applications
 */
public class AppRefresher extends SwingWorker<Void, Object> {

    private static final Logger log = LoggerFactory.getLogger(AppRefresher.class);

    private final LauncherPathProvider lpp;
    private final Auditor auditor;
    private final Collection<ClientSoftwareConfiguration> apps;

    public AppRefresher(LauncherPathProvider lpp, Auditor auditor, Collection<ClientSoftwareConfiguration> apps) {
        this.lpp = lpp;
        this.auditor = auditor;
        this.apps = apps;
    }

    @Override
    protected Void doInBackground() throws Exception {
        log.info("Fetching configurations...");
        int i = 0;
        Path hivePath = lpp.get(SpecialDirectory.BHIVE);
        try (BHive hive = new BHive(hivePath.toUri(), auditor != null ? auditor : RollingFileAuditor.getFactory().apply(hivePath),
                new ActivityReporter.Null())) {
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

    private static void doUpdate(BHive hive, ClientSoftwareConfiguration app) {
        ClickAndStartDescriptor clickAndStart = app.clickAndStart;

        MasterRootResource master = ResourceProvider.getVersionedResource(clickAndStart.host, MasterRootResource.class, null);
        MasterNamedResource namedMaster = master.getNamedMaster(clickAndStart.groupId);
        ClientApplicationConfiguration cac = namedMaster.getClientConfiguration(clickAndStart.instanceId,
                clickAndStart.applicationId);

        ClientSoftwareManifest manifest = new ClientSoftwareManifest(hive);
        ClientSoftwareConfiguration csc = manifest.readNewest(clickAndStart.applicationId, false);
        csc.metadata = ClientApplicationDto.create(clickAndStart, cac);
        csc.clientAppCfg = cac;
        manifest.update(clickAndStart.applicationId, csc);
    }
}
