package io.bdeploy.jersey.cli;

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
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import javax.ws.rs.core.UriBuilder;

import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.ws.WebSocket;

import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.NoThrowAutoCloseable;
import io.bdeploy.common.cfg.Configuration.EnvironmentFallback;
import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cfg.Configuration.Validator;
import io.bdeploy.common.cfg.ExistingPathValidator;
import io.bdeploy.common.cfg.RemoteValidator;
import io.bdeploy.common.cli.ToolBase;
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

        @Help("URI of remote BHive. Supports file:, jar:file:")
        @EnvironmentFallback("BDEPLOY_REMOTE")
        @Validator(RemoteValidator.class)
        String remote();

        @Help("Path to keystore containing access token.")
        @Validator(ExistingPathValidator.class)
        String keystore();

        @Help("Passphrase for the keystore.")
        String passphrase();

        @Help("Token for the remote access. Can be given alternatively to a keystore.")
        @EnvironmentFallback("BDEPLOY_TOKEN")
        String token();

        @Help("Path to a file containing the access token. Can be given alternatively to a keystore.")
        @EnvironmentFallback("BDEPLOY_TOKENFILE")
        @Validator(ExistingPathValidator.class)
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

        boolean isTestMode = ToolBase.isTestMode();
        LocalLoginManager llm = new LocalLoginManager();

        if (!optional && (!isTestMode && llm.getCurrent() == null)) {
            helpAndFailIfMissing(rc.remote(), "Missing --remote, or login using `bdeploy login`");
        }

        RemoteService svc;
        if (rc.remote() != null) {
            URI r = null;
            if (rc.remote() != null) {
                r = UriBuilder.fromUri(rc.remote()).build();
            }

            if (rc.tokenFile() != null && rc.token() != null) {
                out().println(
                        "WARNING: both tokenFile and token are given, preferring tokenFile (Hint: check arguments and environment)");
            }

            svc = createRemoteService(rc, optional, r);
        } else {
            svc = isTestMode ? null : llm.getCurrentService();
            if (!optional && svc == null) {
                helpAndFail(
                        "Need either --tokenFile, --token or --keystore arguments or a current login session to access remote service");
            }
        }

        if (getActivityReporter() instanceof ActivityReporter.Stream) {
            ((ActivityReporter.Stream) getActivityReporter()).setProxyConnector(this::connectProxy);
        }

        try (NoThrowAutoCloseable proxy = getActivityReporter().proxyActivities(svc)) {
            run(config, svc);
        }
    }

    private NoThrowAutoCloseable connectProxy(RemoteService remote, Consumer<byte[]> onMessage) {
        return new NoThrowAutoCloseable() {

            private AsyncHttpClient client;
            private WebSocket ws;

            {
                try {
                    this.client = JerseyClientFactory.get(remote).getWebSocketClient();
                    this.ws = JerseyClientFactory.get(remote).getAuthenticatedWebSocket(client, "/activities", onMessage, e -> {
                        out().println("WebSocket error: ");
                        e.printStackTrace(out());
                    }, s -> {
                        out().println("Activities WebSocket disconnected");
                    }).get();
                } catch (InterruptedException | ExecutionException e) {
                    out().println("Cannot initialize Acitivities WebSocket");
                    e.printStackTrace(out());
                    Thread.currentThread().interrupt();
                }
            }

            @Override
            public void close() {
                if (ws != null) {
                    ws.close();
                }
                if (client != null) {
                    client.close();
                }
            }
        };
    }

    private RemoteService createRemoteService(RemoteConfig rc, boolean optional, URI r) {
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
        } else if (r != null && (r.getScheme() == null || !r.getScheme().equals("https"))) {
            if (r.getScheme() == null) {
                svc = new RemoteService(UriBuilder.fromUri(r).scheme("file").build());
            } else {
                svc = new RemoteService(UriBuilder.fromUri(r).build());
            }
        } else {
            if (!optional) {
                helpAndFail("Need either --tokenFile, --token or --keystore arguments to access remote service");
            }
        }
        return svc;
    }

    protected abstract void run(T config, RemoteService remote);

}
