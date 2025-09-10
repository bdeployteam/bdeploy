package io.bdeploy.launcher.cli;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.interfaces.configuration.instance.ClientApplicationConfiguration;
import io.bdeploy.interfaces.variables.*;
import io.bdeploy.launcher.LauncherPathProvider;
import io.bdeploy.launcher.LauncherPathProvider.SpecialDirectory;

public class ResolverHelper {

    private ResolverHelper() {
    }

    public static CompositeResolver createResolver(LauncherPathProvider lpp, ClientApplicationConfiguration clientCfg) {
        // General resolvers, but without the path handling used on server(s).
        CompositeResolver instance = Resolvers.forInstancePathIndependent(clientCfg.instanceConfig);

        // Enable resolving of paths - done independently of Resolvers due to LPP instead of DPP
        instance.add(new InstanceVariableResolver(clientCfg.instanceConfig, lpp.get(SpecialDirectory.APP), clientCfg.activeTag));
        instance.add(new DeploymentPathResolver(lpp.toDeploymentPathProvider()));

        // Enable resolving of manifest variables
        Path poolDir = lpp.get(SpecialDirectory.MANIFEST_POOL);
        Key applicationKey = clientCfg.appConfig.application;
        Map<Key, Path> pooledSoftware = new HashMap<>();
        pooledSoftware.put(applicationKey, poolDir.resolve(applicationKey.directoryFriendlyName()));
        for (Manifest.Key key : clientCfg.resolvedRequires) {
            pooledSoftware.put(key, poolDir.resolve(key.directoryFriendlyName()));
        }
        instance.add(new ManifestVariableResolver(new ManifestRefPathProvider(pooledSoftware)));

        // Resolver for local hostname - with client warning enabled.
        instance.add(new LocalHostnameResolver(true));

        return Resolvers.forApplication(instance, clientCfg.instanceConfig, clientCfg.appConfig);
    }
}
