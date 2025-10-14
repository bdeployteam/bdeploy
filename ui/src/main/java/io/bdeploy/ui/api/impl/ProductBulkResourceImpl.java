package io.bdeploy.ui.api.impl;

import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.ConcurrentHashMap;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.op.ManifestDeleteOperation;
import io.bdeploy.bhive.op.ManifestListOperation;
import io.bdeploy.common.actions.Actions;
import io.bdeploy.interfaces.manifest.ProductManifest;
import io.bdeploy.interfaces.plugin.PluginManager;
import io.bdeploy.jersey.actions.ActionFactory;
import io.bdeploy.jersey.actions.ActionService.ActionHandle;
import io.bdeploy.ui.RequestScopedParallelOperationsService;
import io.bdeploy.ui.api.ProductBulkResource;
import io.bdeploy.ui.dto.BulkOperationResultDto;
import io.bdeploy.ui.dto.ObjectChangeType;
import io.bdeploy.ui.dto.OperationResult;
import io.bdeploy.ui.dto.OperationResult.OperationResultType;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response.Status;

public class ProductBulkResourceImpl implements ProductBulkResource {

    @Inject
    private PluginManager pm;

    @Inject
    private RequestScopedParallelOperationsService rspos;

    @Inject
    private ChangeEventManager changes;

    @Inject
    private ActionFactory af;

    private final BHive hive;
    private final String group;

    public ProductBulkResourceImpl(BHive hive, String group) {
        this.hive = hive;
        this.group = group;
    }

    @Override
    public BulkOperationResultDto delete(List<Manifest.Key> keys) {
        var result = new BulkOperationResultDto();
        var deleted = new ConcurrentHashMap<Manifest.Key, String>();

        var actions = keys.stream().map(key -> (Runnable) () -> {
            try (ActionHandle h = af.run(Actions.DELETE_PRODUCT, group, null, key.toString())) {
                Set<Key> existing = hive.execute(new ManifestListOperation().setManifestName(key.toString()));
                if (existing.size() != 1) {
                    throw new WebApplicationException("Cannot identify " + key + " to delete", Status.BAD_REQUEST);
                }

                if (!ProductResourceImpl.internalCheckUsedIn(hive, key).isEmpty()) {
                    throw new WebApplicationException("Product version is still in use", Status.BAD_REQUEST);
                }

                // unload any plugins loaded from this version
                pm.unloadProduct(key);

                // This assumes that no single application version is used in multiple products.
                ProductManifest pmf = ProductManifest.of(hive, key);
                SortedSet<Key> apps = pmf.getApplications();

                hive.execute(new ManifestDeleteOperation().setToDelete(key));
                apps.forEach(a -> hive.execute(new ManifestDeleteOperation().setToDelete(a)));
                result.add(new OperationResult(key.toString(), OperationResultType.INFO, "Deleted"));

                deleted.put(key, key.toString());
            } catch (Exception e) {
                result.add(new OperationResult(key.toString(), OperationResultType.ERROR, e.getMessage()));
            }
        }).toList();

        rspos.runAndAwaitAll("Bulk-Delete", actions, hive.getTransactions());

        ProductManifest.invalidateScanCache(hive);
        deleted.keys().asIterator().forEachRemaining(key -> changes.remove(ObjectChangeType.PRODUCT, key));

        return result;
    }

}
