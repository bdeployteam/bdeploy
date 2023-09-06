package io.bdeploy.ui.api.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.common.actions.Actions;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.interfaces.configuration.instance.InstanceConfigurationDto;
import io.bdeploy.interfaces.configuration.instance.InstanceNodeConfigurationDto;
import io.bdeploy.interfaces.configuration.instance.InstanceUpdateDto;
import io.bdeploy.interfaces.configuration.system.SystemConfiguration;
import io.bdeploy.interfaces.manifest.ApplicationManifest;
import io.bdeploy.interfaces.manifest.InstanceManifest;
import io.bdeploy.interfaces.manifest.InstanceNodeManifest;
import io.bdeploy.interfaces.manifest.ProductManifest;
import io.bdeploy.interfaces.manifest.SystemManifest;
import io.bdeploy.interfaces.manifest.managed.ManagedMasterDto;
import io.bdeploy.interfaces.manifest.managed.MasterProvider;
import io.bdeploy.interfaces.manifest.state.InstanceOverallState;
import io.bdeploy.interfaces.remote.MasterNamedResource;
import io.bdeploy.interfaces.remote.MasterRootResource;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.jersey.actions.ActionFactory;
import io.bdeploy.jersey.actions.ActionService.ActionHandle;
import io.bdeploy.jersey.ws.change.msg.ObjectScope;
import io.bdeploy.ui.ProductUpdateService;
import io.bdeploy.ui.RequestScopedParallelOperationsService;
import io.bdeploy.ui.api.InstanceBulkResource;
import io.bdeploy.ui.api.InstanceResource;
import io.bdeploy.ui.api.ManagedServersResource;
import io.bdeploy.ui.api.Minion;
import io.bdeploy.ui.api.MinionMode;
import io.bdeploy.ui.dto.BulkOperationResultDto;
import io.bdeploy.ui.dto.BulkOperationResultDto.OperationResult;
import io.bdeploy.ui.dto.BulkOperationResultDto.OperationResultType;
import io.bdeploy.ui.dto.InstanceDto;
import io.bdeploy.ui.dto.InstanceOverallStatusDto;
import io.bdeploy.ui.dto.ObjectChangeDetails;
import io.bdeploy.ui.dto.ObjectChangeHint;
import io.bdeploy.ui.dto.ObjectChangeType;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.SecurityContext;

/**
 * Provides bulk operations for instances.
 */
public class InstanceBulkResourceImpl implements InstanceBulkResource {

    private static final Logger log = LoggerFactory.getLogger(InstanceBulkResourceImpl.class);

    @Inject
    private Minion minion;

    @Context
    private ResourceContext rc;

    @Context
    private SecurityContext context;

    @Inject
    private ProductUpdateService pus;

    @Inject
    private RequestScopedParallelOperationsService rspos;

    @Inject
    private MasterProvider mp;

    @Inject
    private ChangeEventManager changes;

    @Inject
    private ActionFactory af;

    private final BHive hive;
    private final String group;

    public InstanceBulkResourceImpl(BHive hive, String group) {
        this.hive = hive;
        this.group = group;
    }

    private InstanceResource getInstanceResource() {
        return rc.initResource(new InstanceGroupResourceImpl()).getInstanceResource(group);
    }

    @Override
    public BulkOperationResultDto updateBulk(List<Manifest.Key> instances, String productTag) {
        BulkOperationResultDto result = new BulkOperationResultDto();

        if (instances == null || instances.isEmpty()) {
            return result;
        }

        // 1) read instances per key.
        List<InstanceUpdateDto> updates = new ArrayList<>();
        Map<String, Manifest.Key> instanceKeys = new TreeMap<>();

        // read all instance manifests, and all associated node manifests. then mash them into an update DTO which has no config file changes.
        instances.stream().map(i -> InstanceManifest.of(hive, i)).map(im -> {
            var icd = new InstanceConfigurationDto(im.getConfiguration(),
                    im.getInstanceNodeManifests().entrySet().stream().map(e -> new InstanceNodeConfigurationDto(e.getKey(),
                            InstanceNodeManifest.of(hive, e.getValue()).getConfiguration())).toList());

            instanceKeys.put(icd.config.id, im.getManifest());
            return new InstanceUpdateDto(icd, null);
        }).forEach(dto -> updates.add(dto));

        try (ActionHandle h = af.runMulti(Actions.UPDATE_PRODUCT_VERSION, group, instanceKeys.keySet())) {
            // 2) validate all are using the same product (name, not version).
            var refProd = updates.get(0).config.config.product.getName();

            new ArrayList<>(updates).stream().forEach(i -> {
                if (!refProd.equals(i.config.config.product.getName())) {
                    updates.remove(i);
                    result.add(new OperationResult(i.config.config.id, OperationResultType.ERROR,
                            "All instances must be based on the same product."));
                }
            });

            // 3) read source product versions (may differ!) and all associated applications.
            Map<String, ProductManifest> currentProds = new TreeMap<>();
            Map<String, List<ApplicationManifest>> currentApps = new TreeMap<>();

            for (var e : updates) {
                Key productKey = e.config.config.product;

                var pm = currentProds.computeIfAbsent(productKey.getTag(), v -> {
                    try {
                        return ProductManifest.of(hive, productKey);
                    } catch (Exception ex) {
                        // this *might* be ok :)
                        log.info("Missing product {} while updating {}", productKey, e.config.config.id);
                        return null;
                    }
                });

                if (pm != null) {
                    currentApps.computeIfAbsent(productKey.getTag(), v -> {
                        return pm.getApplications().stream().map(a -> ApplicationManifest.of(hive, a, pm)).toList();
                    });
                }
            }

            // 4) read target product version (same for all).
            ProductManifest targetProd = ProductManifest.of(hive, new Manifest.Key(refProd, productTag));
            List<ApplicationManifest> targetApps = targetProd.getApplications().stream()
                    .map(a -> ApplicationManifest.of(hive, a, targetProd)).toList();

            // 5) read all systems we need for validation.
            Map<Manifest.Key, SystemConfiguration> systems = new TreeMap<>();
            for (var update : updates) {
                var sysKey = update.config.config.system;
                if (sysKey != null) {
                    systems.computeIfAbsent(sysKey, k -> SystemManifest.of(hive, sysKey).getConfiguration());
                }
            }

            // 5) prepare call to pus.update() and pus.validate() for each instance. then save the result if possible.
            Set<Manifest.Key> toSync = new ConcurrentSkipListSet<>();
            List<Runnable> updateRuns = new ArrayList<>();
            List<InstanceUpdateDto> updated = new ArrayList<>();
            for (var update : updates) {
                String sourceTag = update.config.config.product.getTag();

                if (sourceTag.equals(productTag)) {
                    // we can skip this, it's already there!
                    result.add(new OperationResult(update.config.config.id, OperationResultType.INFO,
                            "Skipped, already on " + productTag));
                    continue;
                }

                updateRuns.add(() -> {
                    try {
                        var system = update.config.config.system != null ? systems.get(update.config.config.system) : null;
                        var upd = pus.update(update, targetProd, currentProds.get(sourceTag), targetApps,
                                currentApps.get(sourceTag));
                        var issues = pus.validate(upd, targetApps, system);

                        if (issues.isEmpty()) {
                            updated.add(upd);
                        } else {
                            result.add(new OperationResult(upd.config.config.id, OperationResultType.WARNING,
                                    issues.size() + " Validation issues after update, skipping."));
                            return;
                        }

                        var key = instanceKeys.get(update.config.config.id);
                        RemoteService svc = mp.getControllingMaster(hive, key);
                        MasterRootResource root = ResourceProvider.getVersionedResource(svc, MasterRootResource.class, context);
                        MasterNamedResource mnr = root.getNamedMaster(group);

                        var resultKey = mnr.update(update, key.getTag());
                        result.add(new OperationResult(update.config.config.id, OperationResultType.INFO,
                                "Created instance version " + resultKey.getTag()));
                        toSync.add(key); // sync the *old* key, as the new one does not exist for us on central.
                    } catch (Exception e) {
                        log.warn("Error while updating {}", update.config.config.id, e);
                        result.add(new OperationResult(update.config.config.id, OperationResultType.ERROR, e.getMessage()));
                    }
                });
            }

            // 6) run all prepared tasks.
            rspos.runAndAwaitAll("Bulk-Update", updateRuns, hive.getTransactions());

            // 7) sync!
            syncBulk(toSync);
        }

        return result;
    }

    @Override
    public BulkOperationResultDto startBulk(List<String> instances) {
        var result = new BulkOperationResultDto();
        var ir = getInstanceResource();
        var sync = new ConcurrentHashMap<Manifest.Key, String>();
        var actions = instances.stream().map(i -> (Runnable) () -> {
            var im = InstanceManifest.load(hive, i, null);
            try {
                var pr = ir.getProcessResource(i);
                pr.startAll();

                sync.put(im.getManifest(), im.getConfiguration().id);
                result.add(new OperationResult(i, OperationResultType.INFO, "Started"));
            } catch (Exception e) {
                log.warn("Error while starting {}", i, e);
                result.add(new OperationResult(i, OperationResultType.ERROR, e.getMessage()));
            }
        }).toList();

        rspos.runAndAwaitAll("Bulk-Start", actions, hive.getTransactions());

        syncBulk(sync.keySet());

        sync.entrySet()
                .forEach(e -> changes.change(ObjectChangeType.INSTANCE, e.getKey(),
                        new ObjectScope(group, e.getValue(), e.getKey().getTag()),
                        Map.of(ObjectChangeDetails.CHANGE_HINT, ObjectChangeHint.STATE)));

        return result;
    }

    @Override
    public BulkOperationResultDto stopBulk(List<String> instances) {
        var result = new BulkOperationResultDto();
        var ir = getInstanceResource();
        var sync = new ConcurrentHashMap<Manifest.Key, String>();
        var actions = instances.stream().map(i -> (Runnable) () -> {
            var im = InstanceManifest.load(hive, i, null);
            try {
                var pr = ir.getProcessResource(i);
                pr.stopAll();

                sync.put(im.getManifest(), im.getConfiguration().id);
                result.add(new OperationResult(i, OperationResultType.INFO, "Stopped"));
            } catch (Exception e) {
                log.warn("Error while starting {}", i, e);
                result.add(new OperationResult(i, OperationResultType.ERROR, e.getMessage()));
            }
        }).toList();

        rspos.runAndAwaitAll("Bulk-Stop", actions, hive.getTransactions());

        syncBulk(sync.keySet());

        sync.entrySet()
                .forEach(e -> changes.change(ObjectChangeType.INSTANCE, e.getKey(),
                        new ObjectScope(group, e.getValue(), e.getKey().getTag()),
                        Map.of(ObjectChangeDetails.CHANGE_HINT, ObjectChangeHint.STATE)));

        return result;
    }

    @Override
    public BulkOperationResultDto deleteBulk(List<String> instances) {
        var result = new BulkOperationResultDto();
        var sync = new ConcurrentHashMap<Manifest.Key, String>();

        var actions = instances.stream().map(i -> (Runnable) () -> {
            var im = InstanceManifest.load(hive, i, null);
            var master = mp.getControllingMaster(hive, im.getManifest());
            try {
                var root = ResourceProvider.getVersionedResource(master, MasterRootResource.class, context);
                root.getNamedMaster(group).delete(i);

                sync.put(im.getManifest(), im.getConfiguration().id);
                result.add(new OperationResult(i, OperationResultType.INFO, "Deleted"));
            } catch (Exception e) {
                log.warn("Error while deleting {}", i, e);
                result.add(new OperationResult(i, OperationResultType.ERROR, e.getMessage()));
            }
        }).toList();

        rspos.runAndAwaitAll("Bulk-Delete", actions, hive.getTransactions());

        // now sync and fire update for all manipulated instances.
        syncBulk(sync.keySet());

        // TODO: check who actually fires change events. should be the master directly (?) and the sync in
        // case of central (only!). no resources in the UI should ever fire?
        sync.entrySet().forEach(e -> changes.remove(ObjectChangeType.INSTANCE, e.getKey(),
                new ObjectScope(group, e.getValue(), e.getKey().getTag())));

        return result;
    }

    @Override
    public BulkOperationResultDto installLatestBulk(List<String> instances) {
        var result = new BulkOperationResultDto();
        var sync = new ConcurrentHashMap<Manifest.Key, String>();

        var actions = instances.stream().map(i -> (Runnable) () -> {
            var im = InstanceManifest.load(hive, i, null);
            var state = im.getState(hive).read();

            if (state.installedTags.contains(im.getManifest().getTag())) {
                result.add(new OperationResult(i, OperationResultType.INFO, "Already installed: " + im.getManifest().getTag()));
                return;
            }

            var master = mp.getControllingMaster(hive, im.getManifest());
            try {
                var root = ResourceProvider.getVersionedResource(master, MasterRootResource.class, context);
                root.getNamedMaster(group).install(im.getManifest());

                sync.put(im.getManifest(), im.getConfiguration().id); // only on success.
                result.add(new OperationResult(i, OperationResultType.INFO, "Installed"));
            } catch (Exception e) {
                log.warn("Error while deleting {}", i, e);
                result.add(new OperationResult(i, OperationResultType.ERROR, e.getMessage()));
            }
        }).toList();

        rspos.runAndAwaitAll("Bulk-Install-Latest", actions, hive.getTransactions());

        // now sync and fire update for all manipulated instances.
        syncBulk(sync.keySet());

        // TODO: same check as above regarding events.
        sync.entrySet()
                .forEach(e -> changes.change(ObjectChangeType.INSTANCE, e.getKey(),
                        new ObjectScope(group, e.getValue(), e.getKey().getTag()),
                        Map.of(ObjectChangeDetails.CHANGE_HINT, ObjectChangeHint.STATE)));

        return result;
    }

    @Override
    public BulkOperationResultDto activateLatestBulk(List<String> instances) {
        var result = new BulkOperationResultDto();
        var sync = new ConcurrentHashMap<Manifest.Key, String>();

        var actions = instances.stream().map(i -> (Runnable) () -> {
            var im = InstanceManifest.load(hive, i, null);
            var state = im.getState(hive).read();

            if (state.activeTag != null && state.activeTag.equals(im.getManifest().getTag())) {
                result.add(new OperationResult(i, OperationResultType.INFO, "Already active: " + state.activeTag));
                return;
            }

            var master = mp.getControllingMaster(hive, im.getManifest());
            try {
                var root = ResourceProvider.getVersionedResource(master, MasterRootResource.class, context);
                root.getNamedMaster(group).activate(im.getManifest());

                sync.put(im.getManifest(), im.getConfiguration().id); // only on success.
                result.add(new OperationResult(i, OperationResultType.INFO, "Activated"));
            } catch (Exception e) {
                log.warn("Error while deleting {}", i, e);
                result.add(new OperationResult(i, OperationResultType.ERROR, e.getMessage()));
            }
        }).toList();

        rspos.runAndAwaitAll("Bulk-Activate-Latest", actions, hive.getTransactions());

        // now sync and fire update for all manipulated instances.
        syncBulk(sync.keySet());

        // TODO: same check as above regarding events.
        sync.entrySet()
                .forEach(e -> changes.change(ObjectChangeType.INSTANCE, e.getKey(),
                        new ObjectScope(group, e.getValue(), e.getKey().getTag()),
                        Map.of(ObjectChangeDetails.CHANGE_HINT, ObjectChangeHint.STATE)));

        return result;
    }

    @Override
    public List<InstanceOverallStatusDto> syncBulk(Set<Key> given) {
        // in case no instance IDs are given, sync and get all.
        List<InstanceDto> list = rc.initResource(new InstanceResourceImpl(group, hive)).list();
        Collection<Manifest.Key> instances = (given == null || given.isEmpty()) ? list.stream().map(d -> d.instance).toList()
                : given;
        Set<String> errors = new TreeSet<>();

        // on CENTRAL only, synchronize managed servers. only after that we know all instances.
        if (minion.getMode() == MinionMode.CENTRAL) {
            List<ManagedMasterDto> toSync = list.stream().filter(i -> instances.contains(i.instance)).map(i -> i.managedServer)
                    .toList();

            log.info("Mass-synchronize {} server(s).", toSync.size());

            ManagedServersResource rs = rc.initResource(new ManagedServersResourceImpl());
            List<Runnable> syncTasks = new ArrayList<>();
            for (ManagedMasterDto host : toSync) {
                syncTasks.add(() -> {
                    try {
                        if (log.isDebugEnabled()) {
                            log.debug("Synchronize {}", host.hostName);
                        }
                        rs.synchronize(group, host.hostName);
                    } catch (Exception e) {
                        errors.add(host.hostName);
                        log.warn("Could not synchronize managed server: {}: {}", host.hostName, e.toString());
                        if (log.isDebugEnabled()) {
                            log.debug("Exception:", e);
                        }
                    }
                });
            }

            rspos.runAndAwaitAll("Mass-Synchronizer", syncTasks, hive.getTransactions());
        } else {
            // update the local stored state.
            ResourceProvider.getResource(minion.getSelf(), MasterRootResource.class, context).getNamedMaster(group)
                    .updateOverallStatus();
        }

        // for each instance, read the meta-manifest, and provide the recorded data.
        var result = new ArrayList<InstanceOverallStatusDto>();
        for (var inst : list.stream().filter(i -> instances.contains(i.instance)).toList()) {
            if (inst.managedServer != null && inst.managedServer.hostName != null
                    && errors.contains(inst.managedServer.hostName)) {
                continue; // no state as we could not sync.
            }
            result.add(new InstanceOverallStatusDto(inst.instanceConfiguration.id,
                    new InstanceOverallState(inst.instance, hive).read()));
        }

        return result;
    }

}
