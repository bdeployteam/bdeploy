/*
 * Copyright (c) SSI Schaefer IT Solutions
 */
package io.bdeploy.tea.plugin;

import java.io.File;
import java.net.URI;
import java.util.Set;

import org.eclipse.e4.core.di.annotations.Execute;
import org.eclipse.tea.core.services.TaskingLog;
import org.eclipse.tea.library.build.config.BuildDirectories;
import org.eclipse.tea.library.build.util.FileUtils;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.op.CopyOperation;
import io.bdeploy.bhive.op.ObjectListOperation;
import io.bdeploy.common.ActivityReporter;
import jakarta.inject.Named;
import jakarta.ws.rs.core.UriBuilder;

@Named("BDeploy Product Packaging")
public class BDeployPackageProductTask {

    private final BDeployBuildProductTask build;

    public BDeployPackageProductTask(BDeployBuildProductTask build) {
        this.build = build;
    }

    @Execute
    void packItOrPushIt(TaskingLog log, BuildDirectories dirs) {
        ActivityReporter.Stream reporter = new ActivityReporter.Stream(log.info());

        try (BHive bhive = new BHive(build.getTarget().toURI(), reporter)) {
            // export to ZIP.
            File targetFile = new File(dirs.getProductDirectory(), build.getKey().directoryFriendlyName() + ".zip");

            log.info("Creating product ZIP at " + targetFile);
            ObjectListOperation listOp = new ObjectListOperation();
            listOp.addManifest(build.getKey());
            Set<ObjectId> objectIds = bhive.execute(listOp);

            // Copy objects into the target hive
            FileUtils.delete(targetFile);
            URI targetUri = UriBuilder.fromUri("jar:" + targetFile.toURI()).build();
            try (BHive zipHive = new BHive(targetUri, reporter)) {
                CopyOperation op = new CopyOperation().setDestinationHive(zipHive);
                op.addManifest(build.getKey());
                objectIds.forEach(op::addObject);
                bhive.execute(op);
            }
            log.info("Archived Product to: " + targetFile);
        }
    }

}
