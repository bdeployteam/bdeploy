package io.bdeploy.pcu;

import java.util.List;
import java.util.function.Function;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.interfaces.configuration.dcu.ApplicationConfiguration;
import io.bdeploy.interfaces.manifest.InstanceNodeManifest;

/**
 * A function that provides the configuration order of applications.
 */
public class InstanceNodeOrderProvider implements Function<String, List<String>> {

    private final BHive hive;
    private final String instanceNodeName;

    public InstanceNodeOrderProvider(BHive hive, String instanceNodeName) {
        this.hive = hive;
        this.instanceNodeName = instanceNodeName;
    }

    @Override
    public List<String> apply(String tag) {
        Manifest.Key inmKey = new Manifest.Key(instanceNodeName, tag);
        InstanceNodeManifest inm = InstanceNodeManifest.of(hive, inmKey);
        List<ApplicationConfiguration> apps = inm.getConfiguration().applications;
        return apps.stream().map(app -> app.id).toList();
    }

}