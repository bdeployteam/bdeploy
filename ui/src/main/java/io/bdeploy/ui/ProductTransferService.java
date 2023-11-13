package io.bdeploy.ui;

import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.jvnet.hk2.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.BHiveTransactions.Transaction;
import io.bdeploy.bhive.op.remote.FetchOperation;
import io.bdeploy.bhive.op.remote.PushOperation;
import io.bdeploy.common.actions.Actions;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.interfaces.manifest.managed.ManagedMasterDto;
import io.bdeploy.interfaces.manifest.managed.ManagedMasters;
import io.bdeploy.jersey.actions.ActionExecution;
import io.bdeploy.jersey.actions.ActionFactory;
import io.bdeploy.jersey.actions.ActionService.ActionHandle;
import io.bdeploy.ui.api.MinionMode;
import io.bdeploy.ui.dto.ProductDto;
import io.bdeploy.ui.dto.ProductTransferDto;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.UriBuilder;

@Service
public class ProductTransferService {

    @Inject
    private ActionFactory af;

    private static final Logger log = LoggerFactory.getLogger(ProductTransferService.class);
    private final SortedMap<String, SortedSet<ProductDto>> inTransfer = new TreeMap<>();
    private final ExecutorService transferExec = Executors.newVirtualThreadPerTaskExecutor();

    public void initTransfer(BHive instanceGroupHive, String groupName, ProductTransferDto data) {
        synchronized (inTransfer) {
            getInTransferFor(groupName).addAll(data.versionsToTransfer);
        }

        // on another thread, need to re-init activity reporting.
        CompletableFuture.runAsync(() -> doTransfer(instanceGroupHive, groupName, data), transferExec).whenComplete((r, e) -> {
            synchronized (inTransfer) {
                getInTransferFor(groupName).removeAll(data.versionsToTransfer);
            }

            if (e != null) {
                log.error("Failed to transfer product versions", e);
            }
        });
    }

    private void doTransfer(BHive instanceGroupHive, String groupName, ProductTransferDto data) {
        ManagedMasters masters = new ManagedMasters(instanceGroupHive);
        FetchOperation fetch = null;
        PushOperation push = null;
        Actions action = null;

        if (data.sourceMode == MinionMode.CENTRAL) {
            // need push to managed server only
            ManagedMasterDto attached = masters.read().getManagedMaster(data.targetServer);
            RemoteService svc = new RemoteService(UriBuilder.fromUri(attached.uri).build(), attached.auth);
            push = new PushOperation().setRemote(svc).setHiveName(groupName);
            action = Actions.TRANSFER_PRODUCT_MANAGED;
        } else {
            ManagedMasterDto srcAttached = masters.read().getManagedMaster(data.sourceServer);
            RemoteService srcSvc = new RemoteService(UriBuilder.fromUri(srcAttached.uri).build(), srcAttached.auth);
            fetch = new FetchOperation().setRemote(srcSvc).setHiveName(groupName);
            action = Actions.TRANSFER_PRODUCT_CENTRAL;

            if (data.targetMode == MinionMode.MANAGED) {
                // need a push after the fetch.
                ManagedMasterDto targetAttached = masters.read().getManagedMaster(data.sourceServer);
                RemoteService targetSvc = new RemoteService(UriBuilder.fromUri(targetAttached.uri).build(), targetAttached.auth);
                push = new PushOperation().setRemote(targetSvc).setHiveName(groupName);
            }
        }

        for (ProductDto x : data.versionsToTransfer) {
            if (push != null) {
                push.addManifest(x.key);
            }
            if (fetch != null) {
                fetch.addManifest(x.key);
            }
        }

        try (ActionHandle h = af.runMultiAs(action, groupName, null,
                data.versionsToTransfer.stream().map(v -> v.key.getName() + ":" + v.key.getTag()).toList(),
                ActionExecution::fromSystem)) {
            // always first fetch, then push
            if (fetch != null) {
                try (Transaction t = instanceGroupHive.getTransactions().begin()) {
                    instanceGroupHive.execute(fetch);
                }
            }
            if (push != null) {
                instanceGroupHive.execute(push);
            }
        }
    }

    private SortedSet<ProductDto> getInTransferFor(String groupName) {
        synchronized (inTransfer) {
            return inTransfer.computeIfAbsent(groupName, k -> new TreeSet<>((a, b) -> a.key.compareTo(b.key)));
        }
    }

    public SortedSet<ProductDto> getActiveTransfers(String groupName) {
        return getInTransferFor(groupName);
    }

}
