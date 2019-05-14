package io.bdeploy.bhive.op;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.audit.AuditParameterExtractor.AuditStrategy;
import io.bdeploy.bhive.audit.AuditParameterExtractor.AuditWith;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.model.Tree;
import io.bdeploy.bhive.objects.ObjectDatabase;
import io.bdeploy.bhive.objects.view.ElementView;
import io.bdeploy.bhive.objects.view.ManifestRefView;
import io.bdeploy.bhive.objects.view.TreeView;
import io.bdeploy.bhive.objects.view.scanner.TreeVisitor;
import io.bdeploy.common.ActivityReporter.Activity;

/**
 * List {@link ObjectId}s available in the {@link BHive}.
 * <p>
 * Typically used to retrieve a flattened list of {@link ObjectId}s recursively
 * required by one or more {@link Manifest}s.
 * <p>
 * If no {@link Manifest} is set, returns ALL {@link ObjectId}s known in the
 * {@link ObjectDatabase}.
 */
public class ObjectListOperation extends BHive.Operation<SortedSet<ObjectId>> {

    @AuditWith(AuditStrategy.COLLECTION_PEEK)
    private final SortedSet<Manifest.Key> manifests = new TreeSet<>();

    @AuditWith(AuditStrategy.COLLECTION_SIZE)
    private final SortedSet<ObjectId> trees = new TreeSet<>();

    @AuditWith(AuditStrategy.COLLECTION_SIZE)
    private final SortedSet<ObjectId> treeExcludes = new TreeSet<>();

    @Override
    public SortedSet<ObjectId> call() throws Exception {
        try (Activity activity = getActivityReporter().start("Listing objects...", -1)) {
            for (Manifest.Key m : manifests) {
                trees.add(execute(new ManifestLoadOperation().setManifest(m)).getRoot());
            }

            if (trees.isEmpty()) {
                return getObjectManager().db(x -> x.getAllObjects());
            }

            SortedSet<ObjectId> result = new TreeSet<>();
            for (ObjectId tree : trees) {
                List<ElementView> scanned = new ArrayList<>();
                TreeView state = execute(new ScanOperation().setTree(tree));

                state.visit(new TreeVisitor.Builder().onBlob(scanned::add).onTree(t -> {
                    if (treeExcludes.contains(t.getElementId())) {
                        return false;
                    }
                    scanned.add(t);
                    return true;
                }).onManifestRef(scanned::add).build());

                scanned.stream().map(ElementView::getElementId).forEach(result::add);
                scanned.stream().filter(ManifestRefView.class::isInstance).map(x -> ((ManifestRefView) x).getReferenceId())
                        .forEach(result::add);
            }
            return result;
        }
    }

    /**
     * Restrict {@link ObjectId} listing to the given {@link Manifest}(s).
     */
    public ObjectListOperation addManifest(Manifest.Key manifest) {
        manifests.add(manifest);
        return this;
    }

    /**
     * Restrict {@link ObjectId} listing to the given {@link Tree}(s).
     */
    public ObjectListOperation addTree(ObjectId tree) {
        trees.add(tree);
        return this;
    }

    /**
     * When scanning, exclude the given {@link Tree} and all of it's children.
     */
    public ObjectListOperation excludeTree(ObjectId tree) {
        treeExcludes.add(tree);
        return this;
    }

}
