package io.bdeploy.ui.api;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.interfaces.manifest.InstanceManifest;
import io.bdeploy.interfaces.remote.MasterRootResource;

@FunctionalInterface
public interface MasterProvider {

    /**
     * @param hive the {@link BHive} hosting the instance to provider a {@link MasterRootResource} for
     * @param imKey the {@link Manifest} {@link Key} of the root {@link InstanceManifest}. Only the name of the {@link Key} is
     *            used, the tag is ignored. This allows to pre-calculate a "future" {@link InstanceManifest} {@link Key}.
     *            Depending on the hosting minion's mode, this information might be ignored.
     * @return a {@link RemoteService} which is capable of communicating with the controlling master of the given instance.
     */
    public RemoteService getControllingMaster(BHive hive, Manifest.Key imKey);

}
