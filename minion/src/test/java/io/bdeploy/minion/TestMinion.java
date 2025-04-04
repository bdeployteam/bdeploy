package io.bdeploy.minion;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Objects;

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
import io.bdeploy.logging.audit.RollingFileAuditor;
import io.bdeploy.minion.cli.InitTool;
import io.bdeploy.minion.cli.StartTool;
import io.bdeploy.minion.user.UserDatabase;
import io.bdeploy.ui.api.MinionMode;

/**
 * A complete Minion for unit tests.
 */
public class TestMinion extends TestServer {

    private static final Logger log = LoggerFactory.getLogger(TestMinion.class);

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    public @interface AuthPack {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    public @interface SourceMinion {

        MinionMode value();

        String disambiguation() default "1";
    }

    private final MinionMode mode;
    private final String disambiguation;

    /** Compatibility with existing test code: just be able to use TestMinion directly, defaulting to TestMinionStandalone */
    public TestMinion() {
        this(MinionMode.STANDALONE, null);
    }

    public TestMinion(MinionMode mode) {
        this(mode, "1");
    }

    public TestMinion(MinionMode mode, String disambiguation) {
        this.mode = mode;
        this.disambiguation = disambiguation;
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        // Make sure previous registered resources are gone.
        resetRegistrations();

        // handle mode switching on a SINGLE minion, which allows us to re-use a single
        // minion per class and switching modes in between tests, instead of registering
        // (and starting) multiple minions if that is not needed.
        MinionMode newMode = this.mode;
        for (String tag : context.getTags()) {
            if (MinionMode.CENTRAL.name().equals(tag)) {
                newMode = MinionMode.CENTRAL;
            } else if (MinionMode.MANAGED.name().equals(tag)) {
                newMode = MinionMode.MANAGED;
            }
        }

        MinionMode finalMode = newMode;
        log.info("TestMinion mode = {}", finalMode);

        CloseableMinionRoot cmr = getExtensionStore(context).getOrComputeIfAbsent(CloseableMinionRoot.class,
                k -> new CloseableMinionRoot(getServerPort(context), finalMode), CloseableMinionRoot.class);

        MinionState state = cmr.mr.getState();

        String userName = "Test";
        UserDatabase userDb = cmr.mr.getUsers();
        userDb.createLocalUser(userName, "TheTestPassword", Collections.singletonList(ApiAccessToken.ADMIN_PERMISSION));

        serverStore = SecurityHelper.getInstance().loadPrivateKeyStore(state.keystorePath, state.keystorePass);
        storePass = state.keystorePass;

        Builder builder = new ApiAccessToken.Builder().setIssuedTo(userName).addPermission(ApiAccessToken.ADMIN_PERMISSION);
        authPack = SecurityHelper.getInstance().createSignaturePack(builder.build(), serverStore, state.keystorePass);

        setAuditor(RollingFileAuditor.getInstance(cmr.mr.getLogDir()));

        // create the server.
        super.beforeEach(context);

        PluginManager pm = cmr.mr
                .createPluginManager(getExtensionStore(context).get(CloseableServer.class, CloseableServer.class).getServer());
        BHiveRegistry reg = StartTool.registerCommonResources(this, cmr.mr, new ActivityReporter.Null());
        StartTool.registerMasterResources(this, reg, true, cmr.mr, pm, RollingFileAuditor.getFactory(), false);

        getExtensionStore(context).put(BHiveRegistry.class, reg);
    }

    @Override
    public void beforeTestExecution(ExtensionContext context) {
        super.beforeTestExecution(context);

        // now is the time :D run the after startup.
        CloseableMinionRoot cmr = getExtensionStore(context).get(CloseableMinionRoot.class, CloseableMinionRoot.class);
        super.afterStartup().thenRun(() -> cmr.mr.afterStartup(true, false));
    }

    @Override
    protected Object getServerIdentifyingObject() {
        if (mode == MinionMode.NODE) {
            return mode.name() + "-" + disambiguation;
        }
        return mode;
    }

    @Override
    protected Object getParameterIdentifyingObject(ParameterContext context) {
        var x = context.getParameter().getAnnotationsByType(SourceMinion.class);
        if (x.length != 1) {
            return null;
        }

        if (x[0].value() == MinionMode.NODE) {
            return mode.name() + "-" + x[0].disambiguation();
        }

        return x[0].value();
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
            throws ParameterResolutionException {
        // need to re-check this even though the base class does as well, but the order is... difficult.
        if (getServerIdentifyingObject() != null && getParameterIdentifyingObject(parameterContext) != null
                && !Objects.equals(getServerIdentifyingObject(), getParameterIdentifyingObject(parameterContext))) {
            // all is set to distinguish servers, but no match -> nope.
            return false;
        }

        if (parameterContext.getParameter().getType().isAssignableFrom(MinionRoot.class)) {
            return true;
        }

        if (parameterContext.getParameter().getType().isAssignableFrom(BHiveRegistry.class)) {
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

        if (parameterContext.getParameter().getType().isAssignableFrom(BHiveRegistry.class)) {
            return getExtensionStore(extensionContext).get(BHiveRegistry.class, BHiveRegistry.class);
        }

        if (parameterContext.isAnnotated(AuthPack.class)) {
            return authPack;
        }

        return super.resolveParameter(parameterContext, extensionContext);
    }

    private static final class CloseableMinionRoot implements CloseableResource {

        private final Path root;

        final MinionRoot mr;

        CloseableMinionRoot(int port, MinionMode mode) {
            try {
                root = Files.createTempDirectory("mr-");
                mr = new MinionRoot(root, new ActivityReporter.Null());
                InitTool.initMinionRoot(root, mr, "localhost", port, null, mode, true);
                mr.modifyState(s -> s.poolDefaultPath = root.resolve("objpool"));
                mr.onStartup(true);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public void close() {
            mr.close();
            PathHelper.deleteRecursiveRetry(root);
        }

    }

}
