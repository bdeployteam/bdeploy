package io.bdeploy.bhive.op;

import static io.bdeploy.common.util.RuntimeAssert.assertNotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.ReadOnlyOperation;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.objects.ManifestDatabase;

/**
 * Loads the specified {@link Manifest} from its underlying storage in the
 * {@link ManifestDatabase} of the {@link BHive}.
 */
@ReadOnlyOperation
public class ManifestLoadOperation extends BHive.Operation<Manifest> {

    private static final Logger log = LoggerFactory.getLogger(ManifestLoadOperation.class);

    private Manifest.Key manifest;
    private boolean nullOnError = false;

    @Override
    public Manifest call() throws Exception {
        assertNotNull(manifest, "Manifest to load not set");

        try {
            return getManifestDatabase().getManifest(manifest);
        } catch (Exception e) {
            if (nullOnError) {
                if (log.isDebugEnabled()) {
                    log.debug("Failed to load manifest {}", manifest, e);
                }
                return null;
            }

            throw e;
        }
    }

    /**
     * The {@link Manifest} to load.
     */
    public ManifestLoadOperation setManifest(Manifest.Key key) {
        this.manifest = key;
        return this;
    }

    /**
     * Whether errors during manifest loading should bubble, or be ignored (return null on load instead).
     */
    public ManifestLoadOperation setNullOnError(boolean nullOnError) {
        this.nullOnError = nullOnError;
        return this;
    }

}
