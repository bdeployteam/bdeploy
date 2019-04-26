package io.bdeploy.interfaces.manifest.dependencies;

import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.op.ManifestExistsOperation;
import io.bdeploy.bhive.op.ManifestLexicalMaxTagOperation;
import io.bdeploy.common.util.OsHelper.OperatingSystem;
import io.bdeploy.interfaces.ScopedManifestKey;

public class LocalDependencyFetcher implements DependencyFetcher {

    @Override
    public SortedSet<Manifest.Key> fetch(BHive hive, SortedSet<String> specs, OperatingSystem os) {
        SortedSet<Manifest.Key> smks = new TreeSet<>();
        // make sure all dependencies are satisfied
        for (String dep : specs) {
            Key resolved = LocalDependencyFetcher.resolveSingleLocal(hive, dep, os);
            if (resolved == null) {
                throw new IllegalStateException("Cannot resolve dependency " + dep + " for " + os);
            }
            smks.add(resolved);
        }
        return smks;
    }

    public static Manifest.Key resolveSingleLocal(BHive hive, String spec, OperatingSystem os) {
        Manifest.Key tmp = null;
        if (spec.contains(":")) {
            tmp = Manifest.Key.parse(spec); // key without OS
        } else {
            // this is currently unused as we don't support tag-less dependencies right now.
            Optional<String> id = hive
                    .execute(new ManifestLexicalMaxTagOperation().setManifestName(ScopedManifestKey.createScopedName(spec, os)));
            if (id.isPresent()) {
                tmp = new Manifest.Key(spec, id.get()); // name without tag and OS
            }
        }
        if (tmp == null) {
            return null;
        }

        // check for platform specific match.
        ScopedManifestKey result = ScopedManifestKey.parse(tmp.toString(), os);
        Boolean exists = hive.execute(new ManifestExistsOperation().setManifest(result.getKey()));
        if (!Boolean.TRUE.equals(exists)) {
            // check if direct match.
            if (Boolean.TRUE.equals(hive.execute(new ManifestExistsOperation().setManifest(tmp)))) {
                return tmp;
            }
            return null;
        }
        return result.getKey();
    }

}
