package io.bdeploy.minion;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Store.CloseableResource;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;

import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.security.SecurityHelper;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.jersey.TestServer;
import io.bdeploy.jersey.audit.RollingFileAuditor;
import io.bdeploy.minion.cli.InitTool;
import io.bdeploy.minion.cli.MasterTool;
import io.bdeploy.ui.api.MinionMode;

public class TestMinion extends TestServer {

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    public @interface AuthPack {
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        // Make sure previous registered resources are gone.
        resetRegistrations();

        CloseableMinionRoot cmr = getExtensionStore(context).getOrComputeIfAbsent(CloseableMinionRoot.class,
                (k) -> new CloseableMinionRoot(getServerPort(context)), CloseableMinionRoot.class);

        authPack = InitTool.initMinionRoot(cmr.root, cmr.mr, "localhost", getServerPort(context), null);
        MinionState state = cmr.mr.getState();

        serverStore = SecurityHelper.getInstance().loadPrivateKeyStore(state.keystorePath, state.keystorePass);
        storePass = state.keystorePass;

        setAuditor(new RollingFileAuditor(cmr.mr.getAuditLogDir()));

        MasterTool.registerMasterResources(this, true, true, cmr.mr, new ActivityReporter.Null());
        super.beforeEach(context);
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

        public CloseableMinionRoot(int port) {
            try {
                root = Files.createTempDirectory("mr-");
                mr = new MinionRoot(root, MinionMode.STANDALONE, new ActivityReporter.Null());
                InitTool.initMinionRoot(root, mr, "localhost", port, null);
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
