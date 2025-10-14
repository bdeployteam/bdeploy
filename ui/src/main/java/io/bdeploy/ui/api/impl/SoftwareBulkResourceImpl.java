package io.bdeploy.ui.api.impl;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.op.ManifestDeleteOperation;
import io.bdeploy.bhive.op.ManifestListOperation;
import io.bdeploy.common.actions.Actions;
import io.bdeploy.jersey.actions.ActionFactory;
import io.bdeploy.jersey.actions.ActionService.ActionHandle;
import io.bdeploy.ui.RequestScopedParallelOperationsService;
import io.bdeploy.ui.api.SoftwareBulkResource;
import io.bdeploy.ui.dto.BulkOperationResultDto;
import io.bdeploy.ui.dto.ObjectChangeType;
import io.bdeploy.ui.dto.OperationResult;
import io.bdeploy.ui.dto.OperationResult.OperationResultType;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response.Status;

public class SoftwareBulkResourceImpl implements SoftwareBulkResource {

    private static final Logger log = LoggerFactory.getLogger(SoftwareBulkResourceImpl.class);

    @Inject
    private RequestScopedParallelOperationsService rspos;

    @Inject
    private ChangeEventManager changes;

    @Inject
    private ActionFactory af;

    private final BHive hive;
    private final String softwareRepository;

    public SoftwareBulkResourceImpl(BHive hive, String softwareRepository) {
        this.hive = hive;
        this.softwareRepository = softwareRepository;
    }

    @Override
    public BulkOperationResultDto delete(List<Manifest.Key> keys) {

        var result = new BulkOperationResultDto();
        var deleted = new ConcurrentHashMap<Manifest.Key, String>();
        var actions = keys.stream().map(key -> (Runnable) () -> {
            try (ActionHandle h = af.run(Actions.DELETE_SOFTWARE, softwareRepository, null, key.toString())) {
                Set<Key> existing = hive.execute(new ManifestListOperation().setManifestName(key.toString()));
                if (existing.size() != 1) {
                    throw new WebApplicationException("Cannot identify " + key + " to delete", Status.BAD_REQUEST);
                }

                hive.execute(new ManifestDeleteOperation().setToDelete(key));

                result.add(new OperationResult(key.toString(), OperationResultType.INFO, "Deleted"));
                deleted.put(key, key.toString());
            } catch (Exception e) {
                result.add(new OperationResult(key.toString(), OperationResultType.ERROR, e.getMessage()));
                log.error("Failed to delete software repository " + key, e);
            }
        }).toList();

        rspos.runAndAwaitAll("Bulk-Delete", actions, hive.getTransactions());

        deleted.keys().asIterator().forEachRemaining(key -> changes.remove(ObjectChangeType.SOFTWARE_PACKAGE, key));

        return result;
    }
}
