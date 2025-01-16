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
import java.util.List;

import io.bdeploy.common.cfg.Configuration.EnvironmentFallback;
import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cfg.Configuration.Validator;
import io.bdeploy.common.cfg.ExistingPathValidator;
import io.bdeploy.common.cfg.RemoteValidator;
import io.bdeploy.common.cli.ToolBase;
import io.bdeploy.common.cli.ToolBase.ConfiguredCliTool;
import io.bdeploy.common.cli.data.RenderableResult;
import io.bdeploy.common.security.OnDiscKeyStore;
import io.bdeploy.common.security.RemoteService;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriBuilder;

/**
 * Base class for all CLI tools which need to access a remote service. Figuring
 * out the {@link RemoteService} based on command line arguments is done entirely
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

        @Help("Override which named login session to use for this command.")
        @EnvironmentFallback("BDEPLOY_LOGIN")
        String useLogin();

        @Help("Path to the directory which contains the local login data")
        @EnvironmentFallback("BDEPLOY_LOGIN_STORAGE")
        String loginStorage();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    protected @interface RemoteOptional {
    }

    protected RemoteServiceTool(Class<T> configClass) {
        super(configClass);
    }

    @Override
    protected List<Class<? extends Annotation>> getConfigsForHelp() {
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
    protected final RenderableResult run(T config) {
        RemoteConfig rc = getConfig(RemoteConfig.class);
        boolean optional = isOptional();

        boolean isTestMode = ToolBase.isTestModeLLM();
        LocalLoginManager llm = new LocalLoginManager(rc.loginStorage());

        if (!optional && (!isTestMode && (llm.getCurrent() == null && rc.useLogin() == null))) {
            helpAndFailIfMissing(rc.remote(), "Missing --remote, --useLogin, or current login using `bdeploy login`");
        }

        RemoteService svc = null;
        if (rc.remote() != null) {
            svc = createServiceFromCli(rc, optional);
        } else if (!isTestMode) {
            svc = createServiceFromLLM(rc, optional, llm);
        }

        // TODO: subscribe to actions *triggered by us*. This requires sending a client ID and
        // filtering actions on the server when broadcasting. This in turn has the same challenges
        // as scope service and activities had (?).
        return run(config, svc);
    }

    protected SecurityContext getLocalContext() {
        return null; // Always null for the CLI. The server will infer the context from the token.
    }

    private RemoteService createServiceFromLLM(RemoteConfig rc, boolean optional, LocalLoginManager llm) {
        RemoteService svc;
        if (rc.useLogin() != null) {
            svc = llm.getNamedService(rc.useLogin());
        } else {
            svc = llm.getCurrentService();
        }
        if (!optional && svc == null) {
            helpAndFail(
                    "Need either --tokenFile, --token or --keystore arguments or a current login session to access remote service");
        }
        return svc;
    }

    private RemoteService createServiceFromCli(RemoteConfig rc, boolean optional) {
        URI r = null;
        if (rc.remote() != null) {
            r = UriBuilder.fromUri(rc.remote()).build();
        }

        if (rc.tokenFile() != null && rc.token() != null) {
            out().println(
                    "WARNING: both tokenFile and token are given, preferring tokenFile (Hint: check arguments and environment)");
        }

        return createRemoteService(rc, optional, r);
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
        } else if (r != null && (r.getScheme() == null || !"https".equals(r.getScheme()))) {
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

    protected abstract RenderableResult run(T config, RemoteService remote);

}
