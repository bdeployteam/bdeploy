package io.bdeploy.ui;

import java.util.SortedMap;
import java.util.TreeMap;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.meta.MetaManifest;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.op.ManifestDeleteOldByIdOperation;
import io.bdeploy.interfaces.manifest.InstanceGroupManifest;
import io.bdeploy.ui.dto.AttachIdentDto;

/**
 * {@link MetaManifest} which keeps track of attached servers
 */
public class ManagedMasterAttachmentsMetaManifest {

    private final SortedMap<String, AttachIdentDto> attachedManagedServers = new TreeMap<>();

    public SortedMap<String, AttachIdentDto> getAttachedManagedServers() {
        return attachedManagedServers;
    }

    public static void attach(BHive hive, AttachIdentDto dto, boolean replace) {
        InstanceGroupManifest igm = new InstanceGroupManifest(hive);
        MetaManifest<ManagedMasterAttachmentsMetaManifest> mm = new MetaManifest<>(igm.getKey(), false,
                ManagedMasterAttachmentsMetaManifest.class);

        ManagedMasterAttachmentsMetaManifest value = read(hive);
        if (value.attachedManagedServers.containsKey(dto.name) && !replace) {
            throw new IllegalStateException("Managed server " + dto.name + " already exists!");
        }
        value.attachedManagedServers.put(dto.name, dto);
        writeAndClean(hive, mm, value);
    }

    public static ManagedMasterAttachmentsMetaManifest read(BHive hive) {
        InstanceGroupManifest igm = new InstanceGroupManifest(hive);
        MetaManifest<ManagedMasterAttachmentsMetaManifest> mm = new MetaManifest<>(igm.getKey(), false,
                ManagedMasterAttachmentsMetaManifest.class);
        ManagedMasterAttachmentsMetaManifest value = mm.read(hive);
        if (value == null) {
            value = new ManagedMasterAttachmentsMetaManifest();
        }
        return value;
    }

    public static void detach(BHive hive, String serverName) {
        InstanceGroupManifest igm = new InstanceGroupManifest(hive);
        MetaManifest<ManagedMasterAttachmentsMetaManifest> mm = new MetaManifest<>(igm.getKey(), false,
                ManagedMasterAttachmentsMetaManifest.class);

        ManagedMasterAttachmentsMetaManifest value = read(hive);
        value.attachedManagedServers.remove(serverName);
        writeAndClean(hive, mm, value);
    }

    private static void writeAndClean(BHive hive, MetaManifest<ManagedMasterAttachmentsMetaManifest> mm,
            ManagedMasterAttachmentsMetaManifest value) {
        Manifest.Key key = mm.write(hive, value);

        hive.execute(new ManifestDeleteOldByIdOperation().setAmountToKeep(10).setToDelete(key.getName()));
    }

}
