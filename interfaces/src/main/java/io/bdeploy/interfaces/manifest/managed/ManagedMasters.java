package io.bdeploy.interfaces.manifest.managed;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.meta.MetaManifest;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.op.ManifestDeleteOldByIdOperation;
import io.bdeploy.common.RetryableScope;
import io.bdeploy.interfaces.manifest.InstanceGroupManifest;

/**
 * Encapsulates a {@link MetaManifest} which keeps track of attached servers
 */
public class ManagedMasters {

    private final BHive hive;
    private final MetaManifest<ManagedMastersConfiguration> meta;

    /**
     * @param hive a {@link BHive} which corresponds to an Instance Group.
     */
    public ManagedMasters(BHive hive) {
        InstanceGroupManifest igm = new InstanceGroupManifest(hive);

        this.meta = new MetaManifest<>(igm.getKey(), false, ManagedMastersConfiguration.class);
        this.hive = hive;
    }

    /**
     * @param dto the managed server to attach
     * @param replace whether replacing an existing server is allowed
     */
    public void attach(ManagedMasterDto dto, boolean replace) {
        // This can happen in parallel when synchronizing multiple servers on the same group,
        // or even through actions outside of our JVM thus we must be able to deal with the
        // configuration changing after we read it (i.e. retry).
        RetryableScope.create().withDelay(10).withMaxRetries(10).run(() -> {
            ManagedMastersConfiguration value = read();
            if (value.getManagedMasters().containsKey(dto.hostName) && !replace) {
                throw new IllegalStateException("Managed server " + dto.hostName + " already exists!");
            }
            value.addManagedMaster(dto);
            writeAndClean(value);
        });
    }

    /**
     * @return a record containing information about currently attached managed servers
     */
    public ManagedMastersConfiguration read() {
        ManagedMastersConfiguration value = meta.read(hive);
        if (value == null) {
            value = new ManagedMastersConfiguration();
        }
        return value;
    }

    /**
     * @param serverName the server to detach
     */
    public void detach(String serverName) {
        ManagedMastersConfiguration value = read();
        value.removeManagedMaster(serverName);
        writeAndClean(value);
    }

    private void writeAndClean(ManagedMastersConfiguration value) {
        Manifest.Key key = meta.write(hive, value);

        hive.execute(new ManifestDeleteOldByIdOperation().setAmountToKeep(10).setToDelete(key.getName()));
    }

}
