/*
 * Copyright (c) SSI Schaefer IT Solutions
 */
package io.bdeploy.tea.plugin;

import java.io.File;

import javax.ws.rs.core.UriBuilder;

import org.eclipse.e4.core.di.annotations.Execute;
import org.eclipse.tea.core.services.TaskingLog;
import org.eclipse.tea.library.build.config.BuildDirectories;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.op.remote.PushOperation;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.NoThrowAutoCloseable;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.StringHelper;

public class BDeployProductPushTask {

    private final File hive;
    private final Key product;

    public BDeployProductPushTask(File hive, Manifest.Key product) {
        this.hive = hive;
        this.product = product;
    }

    @Execute
    void push(BDeployConfig cfg, TaskingLog log, BuildDirectories dirs) {
        ActivityReporter.Stream reporter = new ActivityReporter.Stream(log.info());

        RemoteService svc = cfg.bdeployServer == null ? null
                : new RemoteService(UriBuilder.fromUri(cfg.bdeployServer).build(), cfg.bdeployServerToken);

        if (svc == null || StringHelper.isNullOrEmpty(cfg.bdeployTargetInstanceGroup)) {
            throw new IllegalStateException("Server or instance group not configured, see preferences.");
        }

        try (BHive bhive = new BHive(hive.toURI(), reporter)) {
            // 1: optionally push
            if (svc != null && cfg.bdeployTargetInstanceGroup != null && !cfg.bdeployTargetInstanceGroup.isEmpty()) {
                log.info("Pushing result to " + svc.getUri() + " | " + cfg.bdeployTargetInstanceGroup);
                try (NoThrowAutoCloseable proxy = reporter.proxyActivities(svc)) {
                    PushOperation pushOp = new PushOperation();
                    bhive.execute(pushOp.addManifest(product).setHiveName(cfg.bdeployTargetInstanceGroup).setRemote(svc));
                }
            }
        }
    }

}
