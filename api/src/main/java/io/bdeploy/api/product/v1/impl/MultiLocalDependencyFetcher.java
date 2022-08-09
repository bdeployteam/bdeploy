package io.bdeploy.api.product.v1.impl;

import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import io.bdeploy.api.product.v1.DependencyFetcher;
import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.op.CopyOperation;
import io.bdeploy.bhive.op.ObjectListOperation;
import io.bdeploy.common.util.OsHelper.OperatingSystem;

public class MultiLocalDependencyFetcher implements DependencyFetcher {

    private final List<BHive> sources;

    public MultiLocalDependencyFetcher(List<BHive> sources) {
        this.sources = sources;
    }

    @Override
    public SortedSet<Key> fetch(BHive target, SortedSet<String> specs, OperatingSystem os) {
        SortedSet<Manifest.Key> smks = new TreeSet<>();
        // make sure all dependencies are satisfied
        for (String dep : specs) {
            smks.add(this.resolveDependency(target, dep, os));
        }
        return smks;
    }

    private Key resolveDependency(BHive target, String dep, OperatingSystem os) {
        // check if dependency is in the target
        Key resolved = LocalDependencyFetcher.resolveSingleLocal(target, dep, os);
        if (resolved != null) {
            return resolved;
        }

        // check if dependency is in some source
        for (BHive source : sources) {
            resolved = LocalDependencyFetcher.resolveSingleLocal(source, dep, os);
            if (resolved != null) {
                // copy found dependency into target
                this.copyDependency(source, target, resolved);
                return resolved;
            }
        }

        throw new IllegalStateException("Cannot resolve dependency " + dep + " for " + os);
    }

    private void copyDependency(BHive source, BHive target, Key resolved) {
        Set<ObjectId> objectIds = source.execute(new ObjectListOperation().addManifest(resolved));
        CopyOperation op = new CopyOperation().setDestinationHive(target).addManifest(resolved).addObject(objectIds);
        source.execute(op);
    }
}
