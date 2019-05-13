package io.bdeploy.dcu;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.SortedMap;
import java.util.TreeMap;

import io.bdeploy.bhive.BHiveExecution;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Tree.Key;
import io.bdeploy.bhive.objects.ReferenceHandler;
import io.bdeploy.bhive.op.ExportOperation;

public class ApplicationPoolingReferenceHandler implements ReferenceHandler {

    private final BHiveExecution exec;
    private final Path pool;
    private final Path pooledLocation;

    private final SortedMap<Path, Manifest.Key> pooled = new TreeMap<>();

    /**
     * @param exec the {@link BHiveExecution} which is the object source to export from
     * @param pool the pool where to export {@link Manifest}s to.
     * @param pooledLocation the location which to redirect to the pool.
     */
    public ApplicationPoolingReferenceHandler(BHiveExecution exec, Path pool, Path pooledLocation) {
        this.exec = exec;
        this.pool = pool;
        this.pooledLocation = pooledLocation;
    }

    @Override
    public void onReference(Path location, Key key, Manifest referenced) {
        Path target;
        if (location.equals(pooledLocation)) {
            target = pool.resolve(key.getName());
        } else {
            target = location.resolve(key.getName());
        }
        pooled.put(target, referenced.getKey());

        if (!Files.isDirectory(target)) {
            exec.execute(new ExportOperation().setManifest(referenced.getKey()).setTarget(target).setReferenceHandler(this));
        }
    }

    /**
     * @return a mapping of a {@link Path} to the {@link Manifest} exported there.
     */
    public SortedMap<Path, Manifest.Key> getExportedManifests() {
        return pooled;
    }

}
