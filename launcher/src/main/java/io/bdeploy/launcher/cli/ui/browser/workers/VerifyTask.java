package io.bdeploy.launcher.cli.ui.browser.workers;

import java.io.IOException;
import java.nio.file.Path;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.op.VerifyOperation;
import io.bdeploy.common.audit.Auditor;
import io.bdeploy.interfaces.VerifyOperationResultDto;
import io.bdeploy.interfaces.configuration.instance.ClientApplicationConfiguration;
import io.bdeploy.interfaces.descriptor.client.ClickAndStartDescriptor;
import io.bdeploy.interfaces.remote.MasterNamedResource;
import io.bdeploy.interfaces.remote.MasterRootResource;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.launcher.ClientPathHelper;
import io.bdeploy.launcher.LauncherPathProvider;
import io.bdeploy.launcher.LauncherPathProvider.SpecialDirectory;

/**
 * Executes the Verify operation on the given hive and targetPath.
 */
public class VerifyTask extends HiveTask {

    private final LauncherPathProvider lpp;
    private final ClickAndStartDescriptor clickAndStart;

    public VerifyTask(LauncherPathProvider lpp, Auditor auditor, ClickAndStartDescriptor clickAndStart) throws IOException {
        super(ClientPathHelper.getHives(lpp), auditor);
        this.lpp = lpp;
        this.clickAndStart = clickAndStart;
    }

    @Override
    protected String getTaskName() {
        return "Looking for missing and modified files.";
    }

    @Override
    protected void doExecute(BHive hive) {
        MasterRootResource master = ResourceProvider.getVersionedResource(clickAndStart.host, MasterRootResource.class, null);
        MasterNamedResource namedMaster = master.getNamedMaster(clickAndStart.groupId);
        ClientApplicationConfiguration cac = namedMaster.getClientConfiguration(clickAndStart.instanceId,
                clickAndStart.applicationId);

        Manifest.Key manifest = cac.appConfig.application;

        Path targetPath = lpp.get(SpecialDirectory.MANIFEST_POOL).resolve(manifest.directoryFriendlyName());

        VerifyOperationResultDto result = new VerifyOperationResultDto(
                hive.execute(new VerifyOperation().setTargetPath(targetPath).setManifest(manifest)));
        if (result.missingFiles.isEmpty() && result.modifiedFiles.isEmpty()) {
            builder.append("No errors found.\n");
            return;
        }
        builder.append("\nMissing Files: " + result.missingFiles.size() + "\n");
        for (String file : result.missingFiles) {
            builder.append(file + "\n");
        }
        builder.append("\nModified Files: " + result.modifiedFiles.size() + "\n");
        for (String file : result.modifiedFiles) {
            builder.append(file + "\n");
        }
        builder.append("\nRemaining " + result.unmodifiedFiles.size() + " files are unmodified.\n");
    }
}
