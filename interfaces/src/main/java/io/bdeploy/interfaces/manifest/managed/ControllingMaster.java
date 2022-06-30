package io.bdeploy.interfaces.manifest.managed;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.meta.MetaManifest;
import io.bdeploy.bhive.model.Manifest;

/**
 * {@link MetaManifest} which keeps track of which attached managed server is responsible for (controlling) a certain asset
 * (instance, system).
 */
public class ControllingMaster {

    private final MetaManifest<ControllingMasterConfiguration> meta;
    private final BHive hive;

    public ControllingMaster(BHive hive, Manifest.Key assetKey) {
        this.meta = new MetaManifest<>(assetKey, false, ControllingMasterConfiguration.class);
        this.hive = hive;
    }

    /**
     * @param master the name of the attached server as known in {@link ManagedMasters}
     */
    public void associate(String master) {
        ControllingMasterConfiguration value = new ControllingMasterConfiguration();
        value.setName(master);
        meta.write(hive, value);
    }

    /**
     * @return the information about the controlling master.
     */
    public ControllingMasterConfiguration read() {
        ControllingMasterConfiguration value = meta.read(hive);
        if (value == null) {
            return new ControllingMasterConfiguration();
        }
        return value;
    }

}
