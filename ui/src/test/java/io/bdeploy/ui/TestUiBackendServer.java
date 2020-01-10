package io.bdeploy.ui;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.annotation.Priority;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.ext.Provider;

import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.junit.jupiter.api.extension.ExtensionContext;

import io.bdeploy.bhive.remote.jersey.BHiveLocatorImpl;
import io.bdeploy.bhive.remote.jersey.BHiveRegistry;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.security.ScopedCapability;
import io.bdeploy.common.util.OsHelper;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.common.util.VersionHelper;
import io.bdeploy.interfaces.UserInfo;
import io.bdeploy.interfaces.minion.MinionConfiguration;
import io.bdeploy.interfaces.minion.MinionDto;
import io.bdeploy.jersey.TestServer;
import io.bdeploy.ui.api.AuthService;
import io.bdeploy.ui.api.MasterProvider;
import io.bdeploy.ui.api.Minion;
import io.bdeploy.ui.api.MinionMode;
import io.bdeploy.ui.api.impl.UiResources;

/**
 * A JUnit5 extension which can be applied to tests for UI backend API endpoints.
 */
public class TestUiBackendServer extends TestServer {

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        resetRegistrations();

        // register all UI backend API endpoints
        UiResources.register(this);

        Path tmp = Files.createTempDirectory("ui-");
        getExtensionStore(context).put("UI_TEMP", tmp);

        // TestServiceBinder binds all mocked services
        register(new TestServiceBinder(tmp, getRemoteService()));
        register(BHiveLocatorImpl.class);
        register(new DummyAuthenticationProvider());

        super.beforeEach(context);
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        Path tmp = getExtensionStore(context).get("UI_TEMP", Path.class);
        if (tmp != null) {
            PathHelper.deleteRecursive(tmp);
        }

        super.afterEach(context);
    }

    /**
     * Provides bindings for services required by the API endpoints.
     */
    private static class TestServiceBinder extends AbstractBinder {

        private final Path tmp;
        private final RemoteService self;

        public TestServiceBinder(Path tmp, RemoteService self) {
            this.tmp = tmp;
            this.self = self;
        }

        @Override
        protected void configure() {
            Path hives = tmp.resolve("hives");
            PathHelper.mkdirs(hives);

            BHiveRegistry reg = new BHiveRegistry(new ActivityReporter.Null());
            reg.scanLocation(hives);

            bind(reg).to(BHiveRegistry.class);
            bind(new EqualsAuthService()).to(AuthService.class);
            bind(new TestUiMinion(tmp.resolve("downloads"), self)).to(Minion.class);
            bind(((MasterProvider) (h, i) -> self)).to(MasterProvider.class);
        }
    }

    private static class EqualsAuthService implements AuthService {

        private final Map<String, List<String>> recently = new TreeMap<>();

        @Override
        public UserInfo authenticate(String user, String pw) {
            if (user.equals(pw)) {
                return new UserInfo(user);
            }
            return null;
        }

        @Override
        public List<String> getRecentlyUsedInstanceGroups(String user) {
            return recently.getOrDefault(user, Collections.emptyList());
        }

        @Override
        public void addRecentlyUsedInstanceGroup(String user, String group) {
            // force order of entries
            recently.computeIfAbsent(user, x -> new ArrayList<>()).remove(group);
            recently.get(user).add(group);
        }

        @Override
        public UserInfo getUser(String name) {
            return new UserInfo(name);
        }

        @Override
        public void updateUserInfo(UserInfo info) {
        }

        @Override
        public void updateLocalPassword(String user, String pw) {
        }

        @Override
        public void createLocalUser(String user, String pw, Collection<ScopedCapability> capabilities) {
        }

        @Override
        public void deleteUser(String name) {
        }

        @Override
        public SortedSet<String> getAllNames() {
            return new TreeSet<>();
        }

        @Override
        public SortedSet<UserInfo> getAll() {
            return new TreeSet<>();
        }

        @Override
        public boolean isAuthorized(String user, ScopedCapability required) {
            return true;
        }

    }

    @Provider
    @Priority(Priorities.AUTHENTICATION)
    public static class DummyAuthenticationProvider implements ContainerRequestFilter {

        @Override
        public void filter(ContainerRequestContext requestContext) throws IOException {
            requestContext.setSecurityContext(new SecurityContext() {

                @Override
                public boolean isUserInRole(String role) {
                    return false;
                }

                @Override
                public boolean isSecure() {
                    return false;
                }

                @Override
                public Principal getUserPrincipal() {
                    return () -> "test";
                }

                @Override
                public String getAuthenticationScheme() {
                    return "DUMMY";
                }
            });
        }

    }

    private static class TestUiMinion implements Minion {

        private final Path tmpDir;
        private final RemoteService self;
        private final MinionConfiguration minions;

        public TestUiMinion(Path tmpDir, RemoteService self) {
            this.tmpDir = tmpDir;
            this.self = self;
            this.minions = createMinion(self);
            PathHelper.mkdirs(tmpDir);
        }

        @Override
        public boolean isMaster() {
            return true;
        }

        @Override
        public Path getDownloadDir() {
            return tmpDir;
        }

        @Override
        public Path getTempDir() {
            return tmpDir;
        }

        @Override
        public MinionConfiguration getMinions() {
            return minions;
        }

        @Override
        public String createToken(String principal, boolean weak) {
            return "token-" + principal;
        }

        @Override
        public MinionMode getMode() {
            return MinionMode.STANDALONE;
        }

        @Override
        public RemoteService getSelf() {
            return self;
        }

        @Override
        public String getHostName() {
            return "Unit_Test";
        }

        @Override
        public MinionDto getMinionConfig() {
            return minions.getMinion("master");
        }

        @Override
        public <T> String getEncryptedPayload(T payload) {
            return null;
        }

        @Override
        public <T> T getDecryptedPayload(String encrypted, Class<T> clazz) {
            return null;
        }

        @Override
        public void signExecutable(File file, String applicationName, String string) {
        }
    }

    private static MinionConfiguration createMinion(RemoteService remote) {
        MinionDto dto = new MinionDto();
        dto.remote = remote;
        dto.os = OsHelper.getRunningOs();
        dto.version = VersionHelper.tryParse(VersionHelper.readVersion());

        MinionConfiguration config = new MinionConfiguration();
        config.addMinion("master", dto);
        return config;
    }

}
