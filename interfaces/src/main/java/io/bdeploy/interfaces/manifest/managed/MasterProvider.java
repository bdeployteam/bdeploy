package io.bdeploy.interfaces.manifest.managed;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.interfaces.manifest.InstanceManifest;

@FunctionalInterface
public interface MasterProvider {

    /**
     * Returns the remote service that is capable of communicating with the controlling master of the given instance.
     *
     * @param hive
     *            {@link BHive} hosting the instance
     * @param instanceManifestKey the
     *            {@link Manifest} {@link Key} of the root {@link InstanceManifest}.
     * @return a {@link RemoteService} to communicate with the master.
     */
    public RemoteService getControllingMaster(BHive hive, Manifest.Key instanceManifestKey);

}
