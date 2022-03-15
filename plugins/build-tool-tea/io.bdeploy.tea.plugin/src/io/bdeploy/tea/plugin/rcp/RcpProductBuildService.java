/*
 * Copyright (c) SSI Schaefer IT Solutions
 */
package io.bdeploy.tea.plugin.rcp;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.eclipse.tea.core.BackgroundTask;
import org.eclipse.tea.core.TaskExecutionContext;
import org.eclipse.tea.core.services.TaskingLog;
import org.eclipse.tea.library.build.lcdsl.tasks.p2.AbstractProductBuild;
import org.eclipse.tea.library.build.lcdsl.tasks.p2.DynamicProductBuildRegistry;
import org.eclipse.tea.library.build.model.PlatformTriple;
import org.eclipse.tea.library.build.tasks.p2.TaskRunProductExport;
import org.osgi.service.component.annotations.Component;

import io.bdeploy.common.util.OsHelper.OperatingSystem;
import io.bdeploy.tea.plugin.services.BDeployApplicationBuild;
import io.bdeploy.tea.plugin.services.BDeployApplicationDescriptor;
import io.bdeploy.tea.plugin.services.BDeployApplicationDescriptor.BDeployTargetOsArch;
import io.bdeploy.tea.plugin.services.BDeployApplicationService;

@Component
public class RcpProductBuildService implements BDeployApplicationService {

    @Override
    public boolean canHandle(String applicationType) {
        return Objects.equals(applicationType, "RCP_PRODUCT");
    }

    @CreateApplicationTasks
    public List<BDeployApplicationBuild> create(TaskExecutionContext c, BDeployApplicationDescriptor app,
            DynamicProductBuildRegistry registry, TaskingLog log) {
        List<BDeployApplicationBuild> result = new ArrayList<>();

        String prod = (String) app.application.get("product");
        AbstractProductBuild productBuild = registry.findProductBuild(prod);
        if (productBuild != null) {
            productBuild.addUpdateSiteTasks(c, new String[] { productBuild.getOfficialName() });
            TaskRunProductExport task = productBuild.createProductExportTask(productBuild.getOfficialName(), false);
            BackgroundTask bgTask = new BackgroundTask(task);
            c.addTask(bgTask);

            // override platforms to build.
            task.setPlatformsToBuild(app.includeOs.stream().map(o -> o.getMappedTriple()).toArray(PlatformTriple[]::new));

            for (BDeployTargetOsArch os : app.includeOs) {
                BDeployApplicationBuild ad = new BDeployApplicationBuild();
                ad.name = app.name;
                ad.os = OperatingSystem.valueOf(os.name());
                ad.source = () -> getPathForRCPProductInstall(task, os);
                ad.task = bgTask;
                ad.cleanup = () -> Collections.singletonList(getPathForRCPProductInstall(task, os).getParentFile());

                result.add(ad);
            }
        } else {
            log.error("Cannot build product:" + prod + " . Product not found.");
        }

        return result;
    }

    private File getPathForRCPProductInstall(TaskRunProductExport task, BDeployTargetOsArch os) {
        File root = task.getOutput(os.getMappedTriple());
        File[] list = root.listFiles();

        if (list == null || list.length != 1) {
            throw new IllegalStateException("RCP product install should contain a single directory");
        }

        return list[0];
    }

}
