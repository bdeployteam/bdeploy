package io.bdeploy.minion.migration;

import java.io.IOException;
import java.security.GeneralSecurityException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.common.security.ApiAccessToken;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.security.ScopedPermission;
import io.bdeploy.common.security.SecurityHelper;
import io.bdeploy.interfaces.manifest.MinionManifest;
import io.bdeploy.interfaces.minion.MinionConfiguration;
import io.bdeploy.interfaces.minion.MinionDto;
import io.bdeploy.minion.MinionRoot;
import io.bdeploy.ui.api.MinionMode;

public class SystemUserMigration {

    private static final Logger log = LoggerFactory.getLogger(SystemUserMigration.class);

    private SystemUserMigration() {
    }

    public static void run(MinionRoot root) throws GeneralSecurityException, IOException {
        MinionManifest manifest = new MinionManifest(root.getHive());
        MinionConfiguration minionConfiguration = manifest.read();
        if (minionConfiguration == null || root.getMode() == MinionMode.NODE) {
            return;
        }
        log.info("Checking for old system token.");

        String myName = root.getState().self;
        MinionDto minion = minionConfiguration.getMinion(myName);
        if (minion != null) {
            String auth = minion.remote.getAuthPack();
            if (auth != null
                    && !SecurityHelper.getInstance().getSelfVerifiedPayloadFromPack(auth, ApiAccessToken.class).isSystem()) {
                // the token was issued /before/ we introduced system tokens and contains a system user name, which is no
                // longer OK on masters... it is OK for nodes, since user names are not verified there, so we only need
                // to handle masters...
                ApiAccessToken aat = new ApiAccessToken.Builder().forSystem().addPermission(ScopedPermission.GLOBAL_ADMIN)
                        .build();
                String pack = SecurityHelper.getInstance().createSignaturePack(aat, root.getState().keystorePath,
                        root.getState().keystorePass);

                RemoteService newSvc = new RemoteService(minion.remote.getUri(), pack);
                minion.remote = newSvc;

                manifest.update(minionConfiguration);

                log.info("Updated system token.");
            }
        }
    }

}
