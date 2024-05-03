package io.bdeploy.api.product.v1;

import java.util.SortedSet;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.common.util.OsHelper.OperatingSystem;

/**
 * Implementors are capable of asserting that a given dependency is present in the given hive.
 */
public interface DependencyFetcher {

    /**
     * @param hive the hive the dependency should end up in
     * @param specs the dependency specs. Each is an "incomplete" manifest key as it lacks the OS name
     * @param os the OS component for the key to resolve.
     * @return all {@link Key} resolved for the given specs.
     */
    public SortedSet<Manifest.Key> fetch(BHive hive, SortedSet<String> specs, OperatingSystem os);
}
