package io.bdeploy.interfaces.manifest.managed;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.common.security.RemoteService;

public interface MasterProvider {

    /**
     * Returns the remote service that is capable of communicating with the controlling master of the given instance.
     *
     * @param hive
     *            {@link BHive} hosting the instance
     * @param assetKey the
     *            {@link Manifest} {@link Key} of the asset which is controlled by a managed master.
     * @return a {@link RemoteService} to communicate with the master.
     */
    public RemoteService getControllingMaster(BHive hive, Manifest.Key assetKey);

    /**
     * Returns the {@link RemoteService} for a given named managed master on central, or a {@link RemoteService} to self for other
     * scenarios.
     *
     * @param hive the {@link BHive} of the instance group where the server is configured.
     * @param name the name of the server to lookup, may be <code>null</code> for self.
     * @return a {@link RemoteService} for the target server.
     */
    public RemoteService getNamedMasterOrSelf(BHive hive, String name);

}
