package io.bdeploy.api.product.v1.impl;

import java.util.List;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

import io.bdeploy.api.product.v1.DependencyFetcher;
import io.bdeploy.api.remote.v1.PublicRootResource;
import io.bdeploy.api.remote.v1.dto.SoftwareRepositoryConfigurationApi;
import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.BHiveTransactions.Transaction;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.op.remote.FetchOperation;
import io.bdeploy.bhive.remote.RemoteBHive;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.ActivityReporter.Activity;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.OsHelper.OperatingSystem;
import io.bdeploy.jersey.JerseyClientFactory;

public class RemoteDependencyFetcher implements DependencyFetcher {

    private final RemoteService svc;
    private final String instanceGroup;
    private final ActivityReporter reporter;

    public RemoteDependencyFetcher(RemoteService svc, String instanceGroup, ActivityReporter reporter) {
        this.svc = svc;
        this.instanceGroup = instanceGroup;
        this.reporter = reporter;
    }

    @Override
    public synchronized SortedSet<Manifest.Key> fetch(BHive hive, SortedSet<String> deps, OperatingSystem os) {
        // 1. check which dependency is available locally already
        SortedSet<String> remaining = new TreeSet<>();
        SortedSet<Manifest.Key> passOne = new TreeSet<>();
        for (String dep : deps) {
            Key resolved = LocalDependencyFetcher.resolveSingleLocal(hive, dep, os);
            if (resolved == null) {
                remaining.add(dep);
            } else {
                passOne.add(resolved);
            }
        }

        if (remaining.isEmpty()) {
            return passOne;
        }

        // 2. try to fetch from the own instance group - likely all dependencies are present
        String group = this.instanceGroup;
        if (group != null) {
            try (Activity resolving = reporter
                    .start("Resolving " + remaining.size() + " dependencies from instance group " + group)) {
                remaining = fetchSingleRemote(hive, remaining, os, group);
            }
        }

        // 3. if not all dependencies have been found, fetch list of software repos from master
        if (!remaining.isEmpty()) {
            PublicRootResource root = JerseyClientFactory.get(svc).getProxyClient(PublicRootResource.class);

            // 4. check every software repo for the required dependencies as long as something is missing.
            for (SoftwareRepositoryConfigurationApi repo : root.getSoftwareRepositories()) {
                try (Activity resolving = reporter.start("Resolving " + remaining.size() + " dependencies from repository "
                        + repo.name + " (" + repo.description + ")")) {
                    remaining = fetchSingleRemote(hive, remaining, os, repo.name);
                }

                if (remaining.isEmpty()) {
                    break;
                }
            }
        }

        // 5. finally use the local fetcher to assert that all dependencies have been fetched
        return new LocalDependencyFetcher().fetch(hive, deps, os);
    }

    private SortedSet<String> fetchSingleRemote(BHive hive, SortedSet<String> deps, OperatingSystem os, String group) {
        SortedSet<Manifest.Key> toFetch = new TreeSet<>();
        SortedSet<String> unresolved = new TreeSet<>();
        for (String dep : deps) {
            if (!dep.contains(":")) {
                throw new IllegalStateException("Dependency must have a tag ('name:tag'): " + dep);
            }
            try {
                if (!findOnRemote(os, group, toFetch, dep)) {
                    unresolved.add(dep);
                }
            } catch (RuntimeException e) {
                unresolved.add(dep);
            }
        }
        if (toFetch.isEmpty()) {
            return unresolved;
        }

        try (Transaction t = hive.getTransactions().begin()) {
            hive.execute(new FetchOperation().setRemote(svc).setHiveName(group).addManifest(toFetch));
        }

        return unresolved;
    }

    private boolean findOnRemote(OperatingSystem os, String group, SortedSet<Manifest.Key> toFetch, String dep) {
        Manifest.Key k = Manifest.Key.parse(dep);
        try (RemoteBHive rbh = RemoteBHive.forService(svc, group, reporter)) {
            // find all manifests which match the prefix name part
            SortedMap<Key, ObjectId> rmi = rbh.getManifestInventory(k.getName());
            List<Key> available = rmi.keySet().stream().filter(rk -> rk.getTag().equals(k.getTag())).collect(Collectors.toList());

            // for each of them check whether the OS matches.
            for (Key rk : available) {
                // check for a scoped OS specific match
                ScopedManifestKey smk = ScopedManifestKey.parse(rk);
                if (smk != null && smk.getOperatingSystem() == os) {
                    // found one!
                    toFetch.add(smk.getKey());
                    return true;
                }
            }

            for (Key rk : available) {
                // check a direct unscoped match
                if (rk.equals(k)) {
                    toFetch.add(k);
                    return true;
                }
            }
        }
        return false;
    }

}
