/*
 * Copyright (c) SSI Schaefer IT Solutions
 */
package io.bdeploy.tea.plugin;

import java.io.File;
import java.io.IOException;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.tea.core.TaskExecutionContext;
import org.eclipse.tea.core.annotations.TaskChainContextInit;
import org.eclipse.tea.core.annotations.TaskChainMenuEntry;
import org.eclipse.tea.core.services.TaskChain;
import org.eclipse.tea.core.services.TaskChain.TaskChainId;
import org.eclipse.tea.core.services.TaskingLog;
import org.eclipse.tea.core.ui.annotations.TaskChainUiInit;
import org.eclipse.tea.library.build.config.BuildDirectories;
import org.eclipse.tea.library.build.menu.BuildLibraryMenu;
import org.osgi.service.component.annotations.Component;

import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.tea.plugin.server.BDeployTargetSpec;

@Component
@TaskChainId(description = "Push BDeploy Product...", alias = "BDeployProductPush")
@TaskChainMenuEntry(path = BuildLibraryMenu.MENU_BUILD, groupingId = "BDeploy", icon = "icons/bdeploy.png")
public class BDeployProductPushTaskChain implements TaskChain {

    private Manifest.Key product;
    private BDeployTargetSpec target;

    @TaskChainUiInit
    public void uiInit(Shell parent, BuildDirectories dirs) throws IOException, CoreException {
        File hive = new File(dirs.getProductDirectory(), "bhive");

        BDeployChooseProductToPushDialog dlg = new BDeployChooseProductToPushDialog(parent, hive);
        dlg.setBlockOnOpen(true);
        if (dlg.open() != Dialog.OK) {
            throw new OperationCanceledException();
        }

        target = dlg.getChosenTarget();
        product = dlg.getChosenProduct();
    }

    @TaskChainContextInit
    public void init(TaskExecutionContext c, TaskingLog log, BDeployConfig cfg, BuildDirectories dirs) throws CoreException {
        if (product == null) {
            product = Manifest.Key.parse(cfg.bdeployProductPushKey);
        }

        File hive = new File(dirs.getProductDirectory(), "bhive");
        c.addTask(new BDeployProductPushTask(hive, () -> product, target));
    }

}
