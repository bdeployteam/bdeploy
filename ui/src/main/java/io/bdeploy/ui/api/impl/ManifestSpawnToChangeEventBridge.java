package io.bdeploy.ui.api.impl;

import java.util.Collection;

import org.jvnet.hk2.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.api.product.v1.ProductManifestBuilder;
import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.meta.MetaManifest;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.op.ManifestLoadOperation;
import io.bdeploy.bhive.remote.jersey.BHiveRegistry;
import io.bdeploy.bhive.remote.jersey.BHiveRegistry.MultiManifestSpawnListener;
import io.bdeploy.bhive.remote.jersey.JerseyRemoteBHive;
import io.bdeploy.interfaces.manifest.InstanceGroupManifest;
import io.bdeploy.interfaces.manifest.InstanceManifest;
import io.bdeploy.interfaces.manifest.MinionManifest;
import io.bdeploy.interfaces.manifest.ProductManifest;
import io.bdeploy.interfaces.manifest.SoftwareRepositoryManifest;
import io.bdeploy.interfaces.manifest.SystemManifest;
import io.bdeploy.jersey.ws.change.msg.ObjectScope;
import io.bdeploy.ui.dto.ObjectChangeType;
import jakarta.inject.Inject;

@Service
public class ManifestSpawnToChangeEventBridge implements MultiManifestSpawnListener {

    private static final Logger log = LoggerFactory.getLogger(ManifestSpawnToChangeEventBridge.class);

    @Inject
    private ChangeEventManager events;

    private final BHiveRegistry reg;

    @Inject
    public ManifestSpawnToChangeEventBridge(BHiveRegistry reg) {
        reg.addManifestSpawnListener(this);
        this.reg = reg;
    }

    @Override
    public void spawn(String hiveName, Collection<Key> keys) {
        if (log.isDebugEnabled()) {
            log.debug("BHive {} spawned {} manifests", hiveName, keys.size());
        }

        // most of the things are not interesting in the default hive, only some global meta-data
        if (JerseyRemoteBHive.DEFAULT_NAME.equals(hiveName)) {
            for (Manifest.Key key : keys) {
                if (key.getName().equals(MinionManifest.MANIFEST_NAME)) {
                    if (log.isDebugEnabled()) {
                        log.debug("Node configuration change, new version: {}", key.getTag());
                    }
                    events.change(ObjectChangeType.NODES, key);
                }
            }
            return;
        }

        BHive hive = reg.get(hiveName);

        // key of an instance group/software repo manifest IF it exists in the hive.
        Manifest.Key igmKey = new InstanceGroupManifest(hive).getKey();
        Manifest.Key swrKey = new SoftwareRepositoryManifest(hive).getKey();

        for (Manifest.Key key : keys) {
            if (MetaManifest.isMetaManifest(key)) {
                continue; // not interested.
            }

            processKeySpawn(hiveName, hive, igmKey, swrKey, key);
        }
    }

    private void processKeySpawn(String hiveName, BHive hive, Manifest.Key igmKey, Manifest.Key swrKey, Manifest.Key key) {
        // try to find out what the heck it is. we want to identify: Instance Group, Software Repo, Instance, Product
        if (igmKey != null && key.equals(igmKey)) {
            // update for instance group published.
            if (log.isDebugEnabled()) {
                log.debug("Instance Group update for {}", hiveName);
            }
            events.create(ObjectChangeType.INSTANCE_GROUP, igmKey, new ObjectScope(hiveName));
        } else if (swrKey != null && key.equals(swrKey)) {
            // update for software repo published.
            if (log.isDebugEnabled()) {
                log.debug("Software Repository update for {}", hiveName);
            }
            events.create(ObjectChangeType.SOFTWARE_REPO, swrKey, new ObjectScope(hiveName));
        } else if (key.getName().startsWith(SystemManifest.MANIFEST_PREFIX)) {
            if (log.isDebugEnabled()) {
                log.debug("System update for {}: {}", hiveName, key);
            }
            events.create(ObjectChangeType.SYSTEM, key, new ObjectScope(hiveName));
        } else {
            Manifest mf = hive.execute(new ManifestLoadOperation().setManifest(key));
            if (mf.getLabels().containsKey(ProductManifestBuilder.PRODUCT_LABEL)) {
                // it is a product!
                if (log.isDebugEnabled()) {
                    log.debug("Product update for {}: {}", hiveName, key);
                }
                ProductManifest.invalidateScanCache(hive);
                events.create(ObjectChangeType.PRODUCT, key, new ObjectScope(hiveName));
            } else if (mf.getLabels().containsKey(InstanceManifest.INSTANCE_LABEL)) {
                // it is an instance!
                InstanceManifest im = InstanceManifest.of(hive, key);
                if (log.isDebugEnabled()) {
                    log.debug("Instance update for {}: {}", hiveName, im.getConfiguration().id);
                }
                events.create(ObjectChangeType.INSTANCE, key, new ObjectScope(hiveName, im.getConfiguration().id));
            } else {
                // we have no idea, and we don't care :)
                log.trace("Unknown Manifest spawn in {}: {}", hiveName, key);
            }
        }
    }

}
