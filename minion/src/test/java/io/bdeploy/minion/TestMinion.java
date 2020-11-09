package io.bdeploy.minion;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Store.CloseableResource;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.remote.jersey.BHiveRegistry;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.security.ApiAccessToken;
import io.bdeploy.common.security.ApiAccessToken.Builder;
import io.bdeploy.common.security.SecurityHelper;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.interfaces.plugin.PluginManager;
import io.bdeploy.jersey.TestServer;
import io.bdeploy.jersey.audit.RollingFileAuditor;
import io.bdeploy.minion.cli.InitTool;
import io.bdeploy.minion.cli.StartTool;
import io.bdeploy.minion.user.UserDatabase;
import io.bdeploy.ui.api.MinionMode;

/**
 * A complete Minion for unit tests.
 * <p>
 * You can provide a {@link Tag} to set the minion's mode, e.g. <code>@Tag("CENTRAL")</code>.
 */
public class TestMinion extends TestServer {

    private static final Logger log = LoggerFactory.getLogger(TestMinion.class);

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    public @interface AuthPack {
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        // Make sure previous registered resources are gone.
        resetRegistrations();

        MinionMode mode = MinionMode.STANDALONE;
        for (String tag : context.getTags()) {
            if (MinionMode.CENTRAL.name().equals(tag)) {
                mode = MinionMode.CENTRAL;
            } else if (MinionMode.MANAGED.name().equals(tag)) {
                mode = MinionMode.MANAGED;
            }
        }

        MinionMode finalMode = mode;
        log.info("TestMinion mode = " + finalMode);

        CloseableMinionRoot cmr = getExtensionStore(context).getOrComputeIfAbsent(CloseableMinionRoot.class,
                (k) -> new CloseableMinionRoot(getServerPort(context), finalMode), CloseableMinionRoot.class);

        InitTool.initMinionRoot(cmr.root, cmr.mr, "localhost", getServerPort(context), null, finalMode);
        MinionState state = cmr.mr.getState();

        String userName = "Test";
        UserDatabase userDb = cmr.mr.getUsers();
        userDb.createLocalUser(userName, userName, Collections.singletonList(ApiAccessToken.ADMIN_PERMISSION));

        serverStore = SecurityHelper.getInstance().loadPrivateKeyStore(state.keystorePath, state.keystorePass);
        storePass = state.keystorePass;

        Builder builder = new ApiAccessToken.Builder().setIssuedTo(userName).addPermission(ApiAccessToken.ADMIN_PERMISSION);
        authPack = SecurityHelper.getInstance().createSignaturePack(builder.build(), serverStore, state.keystorePass);

        setAuditor(new RollingFileAuditor(cmr.mr.getAuditLogDir()));

        // create the server.
        super.beforeEach(context);

        PluginManager pm = cmr.mr
                .createPluginManager(getExtensionStore(context).get(CloseableServer.class, CloseableServer.class).getServer());
        BHiveRegistry reg = StartTool.registerCommonResources(this, cmr.mr, new ActivityReporter.Null());
        StartTool.registerMasterResources(this, reg, true, cmr.mr, new ActivityReporter.Null(), pm);
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
            throws ParameterResolutionException {
        if (parameterContext.getParameter().getType().isAssignableFrom(MinionRoot.class)) {
            return true;
        }

        if (parameterContext.isAnnotated(AuthPack.class)) {
            return true;
        }

        return super.supportsParameter(parameterContext, extensionContext);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
            throws ParameterResolutionException {
        if (parameterContext.getParameter().getType().isAssignableFrom(MinionRoot.class)) {
            return getExtensionStore(extensionContext).get(CloseableMinionRoot.class, CloseableMinionRoot.class).mr;
        }

        if (parameterContext.isAnnotated(AuthPack.class)) {
            return authPack;
        }

        return super.resolveParameter(parameterContext, extensionContext);
    }

    private static final class CloseableMinionRoot implements CloseableResource {

        private final Path root;

        final MinionRoot mr;

        public CloseableMinionRoot(int port, MinionMode mode) {
            try {
                root = Files.createTempDirectory("mr-");
                mr = new MinionRoot(root, new ActivityReporter.Null());
                InitTool.initMinionRoot(root, mr, "localhost", port, null, mode);
                mr.onStartup();
                mr.setupServerTasks(mr.getMode());
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public void close() throws Throwable {
            mr.close();
            PathHelper.deleteRecursive(root);
        }

    }

}
