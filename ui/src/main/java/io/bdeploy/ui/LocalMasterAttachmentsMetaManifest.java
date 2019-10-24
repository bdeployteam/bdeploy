package io.bdeploy.ui;

import java.util.SortedMap;
import java.util.TreeMap;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.meta.MetaManifest;
import io.bdeploy.interfaces.manifest.InstanceGroupManifest;
import io.bdeploy.ui.dto.AttachIdentDto;

/**
 * {@link MetaManifest} which keeps track of attached servers
 */
public class LocalMasterAttachmentsMetaManifest {

    private final SortedMap<String, AttachIdentDto> attachedLocalServers = new TreeMap<>();

    public SortedMap<String, AttachIdentDto> getAttachedLocalServers() {
        return attachedLocalServers;
    }

    public static void attach(BHive hive, AttachIdentDto dto, boolean replace) {
        InstanceGroupManifest igm = new InstanceGroupManifest(hive);
        MetaManifest<LocalMasterAttachmentsMetaManifest> mm = new MetaManifest<>(igm.getKey(), false,
                LocalMasterAttachmentsMetaManifest.class);

        LocalMasterAttachmentsMetaManifest value = read(hive);
        if (value.attachedLocalServers.containsKey(dto.name) && !replace) {
            throw new IllegalStateException("Local server " + dto.name + " already exists!");
        }
        value.attachedLocalServers.put(dto.name, dto);
        mm.write(hive, value);
    }

    public static LocalMasterAttachmentsMetaManifest read(BHive hive) {
        InstanceGroupManifest igm = new InstanceGroupManifest(hive);
        MetaManifest<LocalMasterAttachmentsMetaManifest> mm = new MetaManifest<>(igm.getKey(), false,
                LocalMasterAttachmentsMetaManifest.class);
        LocalMasterAttachmentsMetaManifest value = mm.read(hive);
        if (value == null) {
            value = new LocalMasterAttachmentsMetaManifest();
        }
        return value;
    }

    public static void detach(BHive hive, String serverName) {
        InstanceGroupManifest igm = new InstanceGroupManifest(hive);
        MetaManifest<LocalMasterAttachmentsMetaManifest> mm = new MetaManifest<>(igm.getKey(), false,
                LocalMasterAttachmentsMetaManifest.class);

        LocalMasterAttachmentsMetaManifest value = read(hive);
        value.attachedLocalServers.remove(serverName);
        mm.write(hive, value);
    }

}
