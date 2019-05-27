package io.bdeploy.jersey.cli;

import static io.bdeploy.common.util.RuntimeAssert.assertNotNull;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.ws.rs.core.UriBuilder;

import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.NoThrowAutoCloseable;
import io.bdeploy.common.cfg.Configuration.EnvironmentFallback;
import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cli.ToolBase.ConfiguredCliTool;
import io.bdeploy.common.security.OnDiscKeyStore;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.jersey.JerseyClientFactory;

/**
 * Base class for all CLI tools which need to access a remote service. Figuring
 * out the {@link RemoteService} based on command line arguments is don entirely
 * here.
 */
public abstract class RemoteServiceTool<T extends Annotation> extends ConfiguredCliTool<T> {

    @Help("Configuration for remote access")
    private @interface RemoteConfig {

        @Help("URI of remote BHive. Supports file:, jar:file:, bhive:")
        @EnvironmentFallback("BDEPLOY_REMOTE")
        String remote();

        @Help("Path to keystore containing access token.")
        String keystore();

        @Help("Passphrase for the keystore.")
        String passphrase();

        @Help("Token for the remote access. Can be given alternatively to a keystore.")
        @EnvironmentFallback("BDEPLOY_TOKEN")
        String token();

        @Help("Path to a file containing the access token. Can be given alternatively to a keystore.")
        @EnvironmentFallback("BDEPLOY_TOKENFILE")
        String tokenFile();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    protected @interface RemoteOptional {
    }

    public RemoteServiceTool(Class<T> configClass) {
        super(configClass);
    }

    @Override
    protected Collection<Class<? extends Annotation>> getConfigsForHelp() {
        List<Class<? extends Annotation>> help = new ArrayList<>();
        help.add(RemoteConfig.class);
        help.addAll(super.getConfigsForHelp());
        return help;
    }

    private boolean isOptional() {
        try {
            Method method = getClass().getDeclaredMethod("run", getPrimaryConfigClass(), RemoteService.class);
            return method.getParameters()[1].getAnnotation(RemoteOptional.class) != null;
        } catch (NoSuchMethodException e) {
            out().println("WARN: cannot determine whether remote is optional: " + e.toString());
            return false;
        }
    }

    @Override
    protected final void run(T config) {
        RemoteConfig rc = getConfig(RemoteConfig.class);
        boolean optional = isOptional();

        if (!optional) {
            helpAndFailIfMissing(rc.remote(), "Missing --remote");
        }

        URI r = null;
        if (rc.remote() != null) {
            r = UriBuilder.fromUri(rc.remote()).build();
        }

        RemoteService svc = null;
        if (rc.tokenFile() != null) {
            try {
                String token = new String(Files.readAllBytes(Paths.get(rc.tokenFile())), StandardCharsets.UTF_8);
                svc = new RemoteService(r, token);
            } catch (IOException e) {
                throw new IllegalStateException("Cannot read token from " + rc.tokenFile(), e);
            }
        } else if (rc.token() != null) {
            svc = new RemoteService(r, rc.token());
        } else if (rc.keystore() != null) {
            helpAndFailIfMissing(rc.passphrase(), "Missing --passphrase");

            svc = new RemoteService(r, new OnDiscKeyStore(Paths.get(rc.keystore()), rc.passphrase()));
        } else if (r != null && !r.getScheme().equals("https")) {
            svc = new RemoteService(UriBuilder.fromUri(r).build());
        } else {
            if (!optional) {
                helpAndFail("Need either --tokenFile, --token or --keystore arguments to access remote service");
            }
        }

        if (!optional) {
            assertNotNull(svc, "Cannot determine remote service");
        }

        if (getActivityReporter() instanceof ActivityReporter.Stream) {
            ((ActivityReporter.Stream) getActivityReporter()).setProxyConnector((s, c) -> {
                return JerseyClientFactory.get(s).getEventSource("/activities").register(c);
            });
        }

        try (NoThrowAutoCloseable proxy = getActivityReporter().proxyActivities(svc)) {
            run(config, svc);
        }
    }

    protected abstract void run(T config, RemoteService remote);

}
