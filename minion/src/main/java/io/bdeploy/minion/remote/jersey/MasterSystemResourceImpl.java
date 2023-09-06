package io.bdeploy.minion.remote.jersey;

import java.util.Map;
import java.util.SortedSet;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.interfaces.configuration.instance.InstanceConfigurationDto;
import io.bdeploy.interfaces.configuration.instance.InstanceUpdateDto;
import io.bdeploy.interfaces.configuration.system.SystemConfiguration;
import io.bdeploy.interfaces.manifest.InstanceManifest;
import io.bdeploy.interfaces.manifest.SystemManifest;
import io.bdeploy.interfaces.remote.MasterSystemResource;
import io.bdeploy.minion.MinionRoot;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response.Status;

public class MasterSystemResourceImpl implements MasterSystemResource {

    private static final Logger log = LoggerFactory.getLogger(MasterSystemResourceImpl.class);

    private final BHive hive;

    @Context
    private ResourceContext rc;

    @Inject
    private MinionRoot root;

    private final String name;

    public MasterSystemResourceImpl(BHive hive, String name) {
        this.hive = hive;
        this.name = name;
    }

    @Override
    public Map<Manifest.Key, SystemConfiguration> list() {
        SortedSet<Key> list = SystemManifest.scan(hive);
        Map<Manifest.Key, SystemConfiguration> result = new TreeMap<>();
        list.forEach(k -> result.put(k, SystemManifest.of(hive, k).getConfiguration()));

        return result;
    }

    @Override
    public Manifest.Key update(SystemConfiguration system) {
        var newKey = new SystemManifest.Builder().setSystemId(system.id).setConfiguration(system).insert(hive);
        var ir = rc.initResource(new MasterNamedResourceImpl(root, hive, name));

        for (var key : InstanceManifest.scan(hive, true)) {
            var im = InstanceManifest.of(hive, key);
            var config = im.getConfiguration();

            if (config.system != null && config.system.getName().equals(newKey.getName())) {
                // update instance reference to system;
                config.system = newKey;
                try {
                    var update = new InstanceUpdateDto(new InstanceConfigurationDto(config, null), null);
                    ir.update(update, key.getTag());
                } catch (Exception e) {
                    // in case a single instance can not be updated, issue a warning.
                    log.warn("Cannot update system {} on instance {}: {}", newKey, config.id, e.toString());
                }
            }
        }

        return newKey;
    }

    @Override
    public void delete(String id) {
        // only delete if no instance has a reference to this system anymore.
        String manifestName = SystemManifest.getManifestName(id);
        SortedSet<Key> instances = InstanceManifest.scan(hive, true);
        for (var key : instances) {
            InstanceManifest imf = InstanceManifest.of(hive, key);
            Manifest.Key sysKey = imf.getConfiguration().system;
            if (sysKey != null && manifestName.equals(sysKey.getName())) {
                throw new WebApplicationException("System " + id + " still in use in instance " + key,
                        Status.PRECONDITION_FAILED);
            }
        }

        SystemManifest.delete(hive, id);
    }

}
