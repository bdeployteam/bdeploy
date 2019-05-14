/*
 * Copyright (c) SSI Schaefer IT Solutions
 */
package io.bdeploy.tea.plugin;

import java.io.File;
import java.net.URI;
import java.util.SortedSet;

import javax.inject.Named;
import javax.ws.rs.core.UriBuilder;

import org.eclipse.e4.core.di.annotations.Execute;
import org.eclipse.tea.core.services.TaskingLog;
import org.eclipse.tea.library.build.config.BuildDirectories;
import org.eclipse.tea.library.build.util.FileUtils;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.op.CopyOperation;
import io.bdeploy.bhive.op.ObjectListOperation;
import io.bdeploy.bhive.op.remote.PushOperation;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.NoThrowAutoCloseable;
import io.bdeploy.common.security.RemoteService;

@Named("BDeploy Product Packaging")
public class BDeployPackageProductTask {

    private final BDeployBuildProductTask build;

    public BDeployPackageProductTask(BDeployBuildProductTask build) {
        this.build = build;
    }

    @Execute
    void packItOrPushIt(BDeployConfig cfg, TaskingLog log, BuildDirectories dirs) {
        ActivityReporter.Stream reporter = new ActivityReporter.Stream(log.info());

        RemoteService svc = cfg.bdeployServer == null ? null
                : new RemoteService(UriBuilder.fromUri(cfg.bdeployServer).build(), cfg.bdeployServerToken);

        try (BHive bhive = new BHive(build.getTarget().toURI(), reporter)) {
            // 1: optionally push
            if (svc != null && cfg.bdeployTargetInstanceGroup != null && !cfg.bdeployTargetInstanceGroup.isEmpty()) {
                log.info("Pushing result to " + svc.getUri() + " | " + cfg.bdeployTargetInstanceGroup);
                try (NoThrowAutoCloseable proxy = reporter.proxyActivities(svc)) {
                    PushOperation pushOp = new PushOperation();
                    bhive.execute(pushOp.addManifest(build.getKey()).setHiveName(cfg.bdeployTargetInstanceGroup).setRemote(svc));
                }
            } else {
                // 2: otherwise export to ZIP.
                File targetFile = new File(dirs.getProductDirectory(), build.getKey().directoryFriendlyName() + ".zip");

                log.info("Creating product ZIP at " + targetFile);
                ObjectListOperation listOp = new ObjectListOperation();
                listOp.addManifest(build.getKey());
                SortedSet<ObjectId> objectIds = bhive.execute(listOp);

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

}
