/*
 * Copyright (c) SSI Schaefer IT Solutions
 */
package io.bdeploy.tea.plugin;

import java.io.File;
import java.util.function.Supplier;

import org.eclipse.e4.core.di.annotations.Execute;
import org.eclipse.tea.core.services.TaskingLog;
import org.eclipse.tea.library.build.config.BuildDirectories;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.op.remote.PushOperation;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.tea.plugin.server.BDeployTargetSpec;
import jakarta.ws.rs.core.UriBuilder;

public class BDeployProductPushTask {

    private final File hive;
    private final Supplier<Key> product;
    private final BDeployTargetSpec target;

    public BDeployProductPushTask(File hive, Supplier<Manifest.Key> product, BDeployTargetSpec target) {
        this.hive = hive;
        this.product = product;
        this.target = target;
    }

    @Execute
    void push(TaskingLog log, BuildDirectories dirs) {
        ActivityReporter.Stream reporter = new ActivityReporter.Stream(log.info());

        RemoteService svc = target.uri == null ? null : new RemoteService(UriBuilder.fromUri(target.uri).build(), target.token);

        if (svc == null || target.instanceGroup == null || target.instanceGroup.isEmpty()) {
            throw new IllegalStateException("Server or instance group not configured, see preferences.");
        }

        try (BHive bhive = new BHive(hive.toURI(), null, reporter)) {
            // 1: optionally push
            log.info("Pushing result to " + svc.getUri() + " | " + target.instanceGroup);
            PushOperation pushOp = new PushOperation();
            bhive.execute(pushOp.addManifest(product.get()).setHiveName(target.instanceGroup).setRemote(svc));
        }
    }

}
