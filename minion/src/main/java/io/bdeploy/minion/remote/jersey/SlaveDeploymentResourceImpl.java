package io.bdeploy.minion.remote.jersey;

import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response.Status;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.ActivityReporter.Activity;
import io.bdeploy.dcu.InstanceNodeController;
import io.bdeploy.interfaces.configuration.pcu.InstanceNodeStatusDto;
import io.bdeploy.interfaces.manifest.InstanceNodeManifest;
import io.bdeploy.interfaces.remote.SlaveDeploymentResource;
import io.bdeploy.minion.MinionConfigVariableResolver;
import io.bdeploy.minion.MinionRoot;
import io.bdeploy.pcu.InstanceProcessController;
import io.bdeploy.pcu.MinionProcessController;

public class SlaveDeploymentResourceImpl implements SlaveDeploymentResource {

    @Inject
    private MinionRoot root;

    @Inject
    private ActivityReporter reporter;

    @Override
    public void install(Key key) {
        BHive hive = root.getHive();

        Activity deploying = reporter.start("Deploying " + key);
        try {
            InstanceNodeManifest inm = InstanceNodeManifest.of(hive, key);
            InstanceNodeController inc = new InstanceNodeController(hive, root.getDeploymentDir(), inm);
            inc.addAdditionalVariableResolver(new MinionConfigVariableResolver(root));
            inc.install();

            // Notify that there is a new deployment
            MinionProcessController processController = root.getProcessController();
            InstanceProcessController controller = processController.getOrCreate(inm.getUUID());
            controller.addProcessGroup(inc.getDeploymentPathProvider(), inm.getKey().getTag(),
                    inc.getProcessGroupConfiguration());
        } finally {
            deploying.done();
        }
    }

    @Override
    public void activate(Key key) {
        BHive hive = root.getHive();

        InstanceNodeManifest inm = InstanceNodeManifest.of(hive, key);
        InstanceNodeController toActivate = new InstanceNodeController(hive, root.getDeploymentDir(), inm);
        if (!toActivate.isInstalled()) {
            throw new WebApplicationException("Key " + key + " is not deployed", Status.NOT_FOUND);
        }

        // Notify that there is a new active version
        MinionProcessController processController = root.getProcessController();
        InstanceProcessController controller = processController.getOrCreate(inm.getUUID());
        controller.setActiveTag(key.getTag());
        root.modifyState((s) -> s.activeVersions.put(inm.getUUID(), key));
    }

    @Override
    public void remove(Key key) {
        BHive hive = root.getHive();

        InstanceNodeManifest inm = InstanceNodeManifest.of(hive, key);
        InstanceNodeController inc = new InstanceNodeController(hive, root.getDeploymentDir(), inm);

        // check currently active deployment
        MinionProcessController processController = root.getProcessController();
        InstanceProcessController controller = processController.getOrCreate(inm.getUUID());
        InstanceNodeStatusDto status = controller.getStatus();
        if (status.areAppsRunningOrScheduledInVersion(key.getName())) {
            throw new WebApplicationException("Key " + key + " has one ore more applications running.", Status.BAD_REQUEST);
        }

        // Active version cannot be removed
        Key activeVersion = root.getState().activeVersions.get(inm.getUUID());
        if (key.equals(activeVersion)) {
            throw new WebApplicationException("Active version " + key + " cannot be removed.", Status.BAD_REQUEST);
        }

        // cleanup the deployment directory.
        inc.uninstall();
    }

    @Override
    public SortedMap<String, SortedSet<Key>> getAvailableDeployments() {
        SortedSet<Key> scan = InstanceNodeManifest.scan(root.getHive());
        SortedMap<String, SortedSet<Key>> uuidMappedKeys = new TreeMap<>();

        scan.stream()
                .map(k -> new InstanceNodeController(root.getHive(), root.getDeploymentDir(),
                        InstanceNodeManifest.of(root.getHive(), k)))
                .filter(InstanceNodeController::isInstalled).forEach(x -> uuidMappedKeys
                        .computeIfAbsent(x.getManifest().getUUID(), y -> new TreeSet<>()).add(x.getManifest().getKey()));

        return uuidMappedKeys;
    }

    @Override
    public SortedMap<String, Key> getActiveDeployments() {
        return root.getState().activeVersions;
    }

}
