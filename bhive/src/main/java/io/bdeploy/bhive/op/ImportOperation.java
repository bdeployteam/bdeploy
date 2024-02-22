package io.bdeploy.bhive.op;

import static io.bdeploy.common.util.RuntimeAssert.assertNotNull;

import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.audit.AuditParameterExtractor.AuditStrategy;
import io.bdeploy.bhive.audit.AuditParameterExtractor.AuditWith;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.common.ActivityReporter.Activity;

/**
 * Import a {@link Path} recursively into the local hive.
 */
public class ImportOperation extends BHive.TransactedOperation<Manifest.Key> {

    private Path toImport;
    private Manifest.Key manifest;

    @AuditWith(AuditStrategy.COLLECTION_PEEK)
    private final Map<String, String> labels = new TreeMap<>();

    @Override
    public Manifest.Key callTransacted() throws Exception {
        assertNotNull(toImport, "Source path not set");
        assertNotNull(manifest, "Manifest not set");

        try (Activity activity = getActivityReporter().start("Importing", -1)) {
            // we have a manual check up front just to make sure we do not import a lot to later
            // on discover that was not required.
            if (getManifestDatabase().hasManifest(manifest)) {
                throw new IllegalArgumentException("Manifest " + manifest + " already present");
            }

            Manifest.Builder builder = new Manifest.Builder(manifest);

            builder.setRoot(execute(new ImportTreeOperation().setSourcePath(toImport)));
            labels.forEach(builder::addLabel);

            getManifestDatabase().addManifest(builder.build(this), false);
        }
        return manifest;
    }

    /**
     * Set the path to import from. The given directory will be recursively imported
     * into the {@link BHive}.
     */
    public ImportOperation setSourcePath(Path toImport) {
        this.toImport = toImport;
        return this;
    }

    /**
     * Set the to-create {@link Manifest} {@link Key}.
     */
    public ImportOperation setManifest(Manifest.Key manifest) {
        this.manifest = manifest;
        return this;
    }

    /**
     * Add additional meta-data to record in the manifest.
     */
    public ImportOperation addLabel(String key, String value) {
        this.labels.put(key, value);
        return this;
    }

}
