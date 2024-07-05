package io.bdeploy.launcher.cli;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.interfaces.configuration.instance.ClientApplicationConfiguration;
import io.bdeploy.interfaces.variables.ApplicationParameterProvider;
import io.bdeploy.interfaces.variables.ApplicationParameterValueResolver;
import io.bdeploy.interfaces.variables.ApplicationVariableResolver;
import io.bdeploy.interfaces.variables.CompositeResolver;
import io.bdeploy.interfaces.variables.ConditionalExpressionResolver;
import io.bdeploy.interfaces.variables.DelayedVariableResolver;
import io.bdeploy.interfaces.variables.DeploymentPathResolver;
import io.bdeploy.interfaces.variables.EnvironmentVariableResolver;
import io.bdeploy.interfaces.variables.EscapeJsonCharactersResolver;
import io.bdeploy.interfaces.variables.EscapeXmlCharactersResolver;
import io.bdeploy.interfaces.variables.EscapeYamlCharactersResolver;
import io.bdeploy.interfaces.variables.InstanceAndSystemVariableResolver;
import io.bdeploy.interfaces.variables.InstanceVariableResolver;
import io.bdeploy.interfaces.variables.LocalHostnameResolver;
import io.bdeploy.interfaces.variables.ManifestRefPathProvider;
import io.bdeploy.interfaces.variables.ManifestSelfResolver;
import io.bdeploy.interfaces.variables.ManifestVariableResolver;
import io.bdeploy.interfaces.variables.OsVariableResolver;
import io.bdeploy.interfaces.variables.ParameterValueResolver;
import io.bdeploy.launcher.LauncherPathProvider;
import io.bdeploy.launcher.LauncherPathProvider.SpecialDirectory;

public class ResolverHelper {

    private ResolverHelper() {
    }

    public static CompositeResolver createResolver(LauncherPathProvider lpp, ClientApplicationConfiguration clientCfg) {
        // General resolvers
        CompositeResolver resolvers = new CompositeResolver();
        resolvers.add(new InstanceAndSystemVariableResolver(clientCfg.instanceConfig));
        resolvers.add(new ConditionalExpressionResolver(resolvers));
        resolvers.add(new ApplicationVariableResolver(clientCfg.appConfig));
        resolvers.add(new DelayedVariableResolver(resolvers));
        resolvers.add(new InstanceVariableResolver(clientCfg.instanceConfig, lpp.get(SpecialDirectory.APP), clientCfg.activeTag));
        resolvers.add(new OsVariableResolver());
        resolvers.add(new EnvironmentVariableResolver());
        resolvers.add(new ParameterValueResolver(new ApplicationParameterProvider(clientCfg.instanceConfig)));
        resolvers.add(new EscapeJsonCharactersResolver(resolvers));
        resolvers.add(new EscapeXmlCharactersResolver(resolvers));
        resolvers.add(new EscapeYamlCharactersResolver(resolvers));

        // Enable resolving of path variables
        resolvers.add(new DeploymentPathResolver(lpp.toDeploymentPathProvider()));

        // Enable resolving of manifest variables
        Path poolDir = lpp.get(SpecialDirectory.MANIFEST_POOL);
        Key applicationKey = clientCfg.appConfig.application;
        Map<Key, Path> pooledSoftware = new HashMap<>();
        pooledSoftware.put(applicationKey, poolDir.resolve(applicationKey.directoryFriendlyName()));
        for (Manifest.Key key : clientCfg.resolvedRequires) {
            pooledSoftware.put(key, poolDir.resolve(key.directoryFriendlyName()));
        }
        resolvers.add(new ManifestVariableResolver(new ManifestRefPathProvider(pooledSoftware)));

        // Resolver for local hostname - with client warning enabled.
        resolvers.add(new LocalHostnameResolver(true));

        // Resolvers that are using the general ones to actually do the work
        CompositeResolver appSpecificResolvers = new CompositeResolver();
        appSpecificResolvers.add(new ApplicationParameterValueResolver(clientCfg.appConfig.id, clientCfg.instanceConfig));
        appSpecificResolvers.add(new ManifestSelfResolver(clientCfg.appConfig.application, resolvers));
        appSpecificResolvers.add(resolvers);

        return appSpecificResolvers;
    }
}
