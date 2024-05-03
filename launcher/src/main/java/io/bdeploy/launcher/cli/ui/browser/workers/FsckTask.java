package io.bdeploy.launcher.cli.ui.browser.workers;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.objects.view.DamagedObjectView;
import io.bdeploy.bhive.objects.view.ElementView;
import io.bdeploy.bhive.op.FsckOperation;
import io.bdeploy.common.audit.Auditor;

/**
 * Executes the Fsck operation the given hives
 */
public class FsckTask extends HiveTask {

    public FsckTask(List<Path> hives, Auditor auditor) {
        super(hives, auditor);
    }

    @Override
    protected String getTaskName() {
        return "Checking manifest and object consistency in all hives.";
    }

    @Override
    protected void doExecute(BHive hive) {
        Set<ElementView> result = hive.execute(new FsckOperation().setRepair(true));
        if (result.isEmpty()) {
            builder.append("No errors found.\n");
        } else if (result.size() == 1) {
            builder.append("Found 1 damaged object.\n");
        } else {
            builder.append("Found " + result.size() + " damaged objects.\n");
        }
        for (ElementView ele : result) {
            builder.append(ele.getElementId());
            builder.append(" ");
            if (ele instanceof DamagedObjectView dov) {
                builder.append(dov.getType());
                builder.append(" ");
            }
            builder.append(ele.getPathString());
            builder.append("\n");
        }
    }

}
