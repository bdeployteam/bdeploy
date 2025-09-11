package io.bdeploy.interfaces.variables;

import io.bdeploy.common.util.VariableResolver;
import io.bdeploy.interfaces.configuration.dcu.ApplicationConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceNodeConfiguration;
import io.bdeploy.interfaces.configuration.pcu.ProcessConfiguration;
import io.bdeploy.interfaces.manifest.InstanceNodeManifest;

/**
 * Provides common configurations for resolvers which can then be amended use case specific.
 * <p>
 * Some resolvers need to be added manually, e.g. {@link ManifestVariableResolver} and {@link LocalHostnameResolver}
 */
public class Resolvers {

    public static CompositeResolver forInstance(InstanceNodeManifest inm, DeploymentPathProvider paths) {
        return forInstance(inm.getConfiguration(), inm.getKey().getTag(), paths);
    }

    public static CompositeResolver forInstance(InstanceNodeConfiguration config, String tag, DeploymentPathProvider paths) {
        var resolver = new CompositeResolver();

        resolver.add(new InstanceAndSystemVariableResolver(config));
        resolver.add(new ConditionalExpressionResolver(resolver));
        resolver.add(new DelayedVariableResolver(resolver));
        resolver.add(new OsVariableResolver());
        resolver.add(new EnvironmentVariableResolver());
        resolver.add(new ParameterValueResolver(new ApplicationParameterProvider(config)));
        resolver.add(new EscapeJsonCharactersResolver(resolver));
        resolver.add(new EscapeXmlCharactersResolver(resolver));
        resolver.add(new EscapeYamlCharactersResolver(resolver));

        if(paths != null) {
            resolver.add(new InstanceVariableResolver(config, paths.get(DeploymentPathProvider.SpecialDirectory.ROOT), tag));
            resolver.add(new DeploymentPathResolver(paths));
        } else {
            resolver.add(new InstanceVariableResolver(config, null, tag));
        }

        return resolver;
    }

    public static CompositeResolver forInstancePathIndependent(InstanceNodeConfiguration config) {
        // see above, tag is ignored if paths are not set.
        return forInstance(config, null, null);
    }

    public static CompositeResolver forApplication(VariableResolver upstream, InstanceNodeConfiguration instance, ApplicationConfiguration application) {
        var resolver = new CompositeResolver();

        resolver.add(new ApplicationVariableResolver(application));
        resolver.add(new ApplicationParameterValueResolver(application.id, instance));
        resolver.add(new ManifestSelfResolver(application.application, upstream));
        resolver.add(upstream);

        return resolver;
    }

    public static CompositeResolver forProcess(VariableResolver upstream, InstanceNodeConfiguration instance, ProcessConfiguration process) {
        var resolver = new CompositeResolver();

        resolver.add(new ApplicationVariableResolver(process));
        resolver.add(new ApplicationParameterValueResolver(process.id, instance));
        resolver.add(upstream);

        return resolver;
    }

}
