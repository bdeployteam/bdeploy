package io.bdeploy.minion.migration;

import io.bdeploy.minion.MinionRoot;
import io.bdeploy.ui.api.MinionMode;

public class MinionModeMigration {

    @SuppressWarnings("deprecation")
    public static void run(MinionRoot root) {
        if (root.getMode() == MinionMode.SLAVE) {
            root.modifyState(s -> s.mode = MinionMode.NODE);
        }
    }
}
