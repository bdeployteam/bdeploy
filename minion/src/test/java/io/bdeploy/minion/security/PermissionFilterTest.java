package io.bdeploy.minion.security;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;

import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.bdeploy.api.remote.v1.dto.CredentialsApi;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.security.ScopedPermission;
import io.bdeploy.common.security.ScopedPermission.Permission;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.jersey.ws.change.msg.ObjectScope;
import io.bdeploy.minion.MinionRoot;
import io.bdeploy.minion.TestMinion;
import io.bdeploy.ui.api.AuthResource;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.core.Response;

class PermissionFilterTest {

    private static final String NO_PERM = "no-perm";
    private static final String GLOBAL_READ = "global-read";
    private static final String GLOBAL_WRITE = "global-write";
    private static final String GLOBAL_ADMIN = "global-admin";
    private static final String LOCAL_READ = "local-read";
    private static final String LOCAL_WRITE = "local-write";
    private static final String LOCAL_ADMIN = "local-admin";
    private static final String PASSWORD = "TheTestPassword";

    private static final String SCOPE = "X";
    private static final String OTHER_SCOPE = "Y";
    private static final ObjectScope EXPECTED_SCOPE = new ObjectScope(SCOPE);

    PermControlSvcImpl control = new PermControlSvcImpl();

    @RegisterExtension
    private final TestMinion ext = new TestMinion();

    @BeforeEach
    void registerResource(MinionRoot root) {
        ext.register(PermSvcImpl.class);
        ext.register(PermSvcDynPermLocatorImpl.class);
        ext.register(PermSvcStaticPermLocatorImpl.class);
        ext.register(new AbstractBinder() {

            @Override
            protected void configure() {
                bind(control).to(PermControlSvcImpl.class);
            }
        });

        root.getUsers().createLocalUser(NO_PERM, PASSWORD, Collections.emptyList());

        root.getUsers().createLocalUser(GLOBAL_READ, PASSWORD, Collections.singletonList(new ScopedPermission(Permission.READ)));
        root.getUsers().createLocalUser(GLOBAL_WRITE, PASSWORD,
                Collections.singletonList(new ScopedPermission(Permission.WRITE)));
        root.getUsers().createLocalUser(GLOBAL_ADMIN, PASSWORD,
                Collections.singletonList(new ScopedPermission(Permission.ADMIN)));

        root.getUsers().createLocalUser(LOCAL_READ, PASSWORD,
                Collections.singletonList(new ScopedPermission(SCOPE, Permission.READ)));
        root.getUsers().createLocalUser(LOCAL_WRITE, PASSWORD,
                Collections.singletonList(new ScopedPermission(SCOPE, Permission.WRITE)));
        root.getUsers().createLocalUser(LOCAL_ADMIN, PASSWORD,
                Collections.singletonList(new ScopedPermission(SCOPE, Permission.ADMIN)));
    }

    private static <T> T getAsUser(RemoteService svc, Class<T> resource, String user) {
        Response resp = ResourceProvider.getResource(svc, AuthResource.class, null)
                .authenticatePacked(new CredentialsApi(user, PASSWORD));
        String token = resp.readEntity(String.class);

        return ResourceProvider.getResource(new RemoteService(svc.getUri(), token), resource, null);
    }

    private static void testSvc(PermSvc service, ObjectScope expected, boolean readAllowed, boolean writeAllowed,
            boolean adminAllowed, boolean adminNoInheritAllowed) {
        if (readAllowed) {
            assertTrue(expected.matches(service.read()));
        } else {
            assertThrows(NotAuthorizedException.class, service::read);
        }

        if (writeAllowed) {
            assertTrue(expected.matches(service.write()));
        } else {
            assertThrows(NotAuthorizedException.class, service::write);
        }

        if (adminAllowed) {
            assertTrue(expected.matches(service.admin()));
        } else {
            assertThrows(NotAuthorizedException.class, service::admin);
        }

        if (adminNoInheritAllowed) {
            assertTrue(expected.matches(service.adminNoInherit()));
        } else {
            assertThrows(NotAuthorizedException.class, service::adminNoInherit);
        }
    }

    @Test
    void testDirectPermission(RemoteService remote) {
        // @formatter:off
        testSvc(getAsUser(remote, PermSvc.class, NO_PERM), ObjectScope.EMPTY, false, false, false, false);
        testSvc(getAsUser(remote, PermSvc.class, GLOBAL_READ), ObjectScope.EMPTY, true, false, false, false);
        testSvc(getAsUser(remote, PermSvc.class, GLOBAL_WRITE), ObjectScope.EMPTY, true, true, false, false);
        testSvc(getAsUser(remote, PermSvc.class, GLOBAL_ADMIN), ObjectScope.EMPTY, true, true, true, true);

        // local scope has NO permissions on direct service.
        testSvc(getAsUser(remote, PermSvc.class, LOCAL_READ), ObjectScope.EMPTY, false, false, false, false);
        testSvc(getAsUser(remote, PermSvc.class, LOCAL_WRITE), ObjectScope.EMPTY, false, false, false, false);
        testSvc(getAsUser(remote, PermSvc.class, LOCAL_ADMIN), ObjectScope.EMPTY, false, false, false, false);
        // @formatter:on
    }

    @Test
    void testDynamicPermissionNoPerm(RemoteService remote) {
        // @formatter:off
        testSvc(getAsUser(remote, PermSvcDynPermLocator.class, NO_PERM).getScopeService(SCOPE), EXPECTED_SCOPE, false, false, false, false);
        testSvc(getAsUser(remote, PermSvcDynPermLocator.class, GLOBAL_READ).getScopeService(SCOPE), EXPECTED_SCOPE, true, false, false, false);
        testSvc(getAsUser(remote, PermSvcDynPermLocator.class, GLOBAL_WRITE).getScopeService(SCOPE), EXPECTED_SCOPE, true, true, false, false);
        testSvc(getAsUser(remote, PermSvcDynPermLocator.class, GLOBAL_ADMIN).getScopeService(SCOPE), EXPECTED_SCOPE, true, true, true, true);

        testSvc(getAsUser(remote, PermSvcDynPermLocator.class, LOCAL_READ).getScopeService(SCOPE), EXPECTED_SCOPE, true, false, false, false);
        testSvc(getAsUser(remote, PermSvcDynPermLocator.class, LOCAL_WRITE).getScopeService(SCOPE), EXPECTED_SCOPE, true, true, false, false);
        testSvc(getAsUser(remote, PermSvcDynPermLocator.class, LOCAL_ADMIN).getScopeService(SCOPE), EXPECTED_SCOPE, true, true, true, false);

        testSvc(getAsUser(remote, PermSvcDynPermLocator.class, LOCAL_READ).getScopeService(OTHER_SCOPE), null, false, false, false, false);
        testSvc(getAsUser(remote, PermSvcDynPermLocator.class, LOCAL_WRITE).getScopeService(OTHER_SCOPE), null, false, false, false, false);
        testSvc(getAsUser(remote, PermSvcDynPermLocator.class, LOCAL_ADMIN).getScopeService(OTHER_SCOPE), null, false, false, false, false);
        // @formatter:on
    }

    @Test
    void testDynamicPermissionWritePerm(RemoteService remote) {
        control.setPerm(Permission.WRITE);

        // @formatter:off
        testSvc(getAsUser(remote, PermSvcDynPermLocator.class, NO_PERM).getScopeService(SCOPE), EXPECTED_SCOPE, false, false, false, false);
        testSvc(getAsUser(remote, PermSvcDynPermLocator.class, GLOBAL_READ).getScopeService(SCOPE), EXPECTED_SCOPE, false, false, false, false);
        testSvc(getAsUser(remote, PermSvcDynPermLocator.class, GLOBAL_WRITE).getScopeService(SCOPE), EXPECTED_SCOPE, true, true, false, false);
        testSvc(getAsUser(remote, PermSvcDynPermLocator.class, GLOBAL_ADMIN).getScopeService(SCOPE), EXPECTED_SCOPE, true, true, true, true);

        testSvc(getAsUser(remote, PermSvcDynPermLocator.class, LOCAL_READ).getScopeService(SCOPE), EXPECTED_SCOPE, false, false, false, false);
        testSvc(getAsUser(remote, PermSvcDynPermLocator.class, LOCAL_WRITE).getScopeService(SCOPE), EXPECTED_SCOPE, true, true, false, false);
        testSvc(getAsUser(remote, PermSvcDynPermLocator.class, LOCAL_ADMIN).getScopeService(SCOPE), EXPECTED_SCOPE, true, true, true, false);

        testSvc(getAsUser(remote, PermSvcDynPermLocator.class, LOCAL_READ).getScopeService(OTHER_SCOPE), null, false, false, false, false);
        testSvc(getAsUser(remote, PermSvcDynPermLocator.class, LOCAL_WRITE).getScopeService(OTHER_SCOPE), null, false, false, false, false);
        testSvc(getAsUser(remote, PermSvcDynPermLocator.class, LOCAL_ADMIN).getScopeService(OTHER_SCOPE), null, false, false, false, false);
        // @formatter:on
    }

    @Test
    void testDynamicPermissionAdminPerm(RemoteService remote) {
        control.setPerm(Permission.ADMIN);

        // @formatter:off
        testSvc(getAsUser(remote, PermSvcDynPermLocator.class, NO_PERM).getScopeService(SCOPE), EXPECTED_SCOPE, false, false, false, false);
        testSvc(getAsUser(remote, PermSvcDynPermLocator.class, GLOBAL_READ).getScopeService(SCOPE), EXPECTED_SCOPE, false, false, false, false);
        testSvc(getAsUser(remote, PermSvcDynPermLocator.class, GLOBAL_WRITE).getScopeService(SCOPE), EXPECTED_SCOPE, false, false, false, false);
        testSvc(getAsUser(remote, PermSvcDynPermLocator.class, GLOBAL_ADMIN).getScopeService(SCOPE), EXPECTED_SCOPE, true, true, true, true);

        testSvc(getAsUser(remote, PermSvcDynPermLocator.class, LOCAL_READ).getScopeService(SCOPE), EXPECTED_SCOPE, false, false, false, false);
        testSvc(getAsUser(remote, PermSvcDynPermLocator.class, LOCAL_WRITE).getScopeService(SCOPE), EXPECTED_SCOPE, false, false, false, false);
        testSvc(getAsUser(remote, PermSvcDynPermLocator.class, LOCAL_ADMIN).getScopeService(SCOPE), EXPECTED_SCOPE, true, true, true, false);

        testSvc(getAsUser(remote, PermSvcDynPermLocator.class, LOCAL_READ).getScopeService(OTHER_SCOPE), null, false, false, false, false);
        testSvc(getAsUser(remote, PermSvcDynPermLocator.class, LOCAL_WRITE).getScopeService(OTHER_SCOPE), null, false, false, false, false);
        testSvc(getAsUser(remote, PermSvcDynPermLocator.class, LOCAL_ADMIN).getScopeService(OTHER_SCOPE), null, false, false, false, false);
        // @formatter:on
    }

    @Test
    void testStaticLocatorNoScopeNoPerm(RemoteService remote) {
        // @formatter:off
        testSvc(getAsUser(remote, PermSvcStaticPermLocator.class, NO_PERM).getServiceNoScopeNoPerm(), ObjectScope.EMPTY, false, false, false, false);
        testSvc(getAsUser(remote, PermSvcStaticPermLocator.class, GLOBAL_READ).getServiceNoScopeNoPerm(), ObjectScope.EMPTY, true, false, false, false);
        testSvc(getAsUser(remote, PermSvcStaticPermLocator.class, GLOBAL_WRITE).getServiceNoScopeNoPerm(), ObjectScope.EMPTY, true, true, false, false);
        testSvc(getAsUser(remote, PermSvcStaticPermLocator.class, GLOBAL_ADMIN).getServiceNoScopeNoPerm(), ObjectScope.EMPTY, true, true, true, true);

        testSvc(getAsUser(remote, PermSvcStaticPermLocator.class, LOCAL_READ).getServiceNoScopeNoPerm(), ObjectScope.EMPTY, false, false, false, false);
        testSvc(getAsUser(remote, PermSvcStaticPermLocator.class, LOCAL_WRITE).getServiceNoScopeNoPerm(), ObjectScope.EMPTY, false, false, false, false);
        testSvc(getAsUser(remote, PermSvcStaticPermLocator.class, LOCAL_ADMIN).getServiceNoScopeNoPerm(), ObjectScope.EMPTY, false, false, false, false);
        // @formatter:on
    }

    @Test
    void testStaticLocatorWritePerm(RemoteService remote) {
        // test with no permission annotation set.

        // @formatter:off
        testSvc(getAsUser(remote, PermSvcStaticPermLocator.class, NO_PERM).getServiceWritePerm(SCOPE), EXPECTED_SCOPE, false, false, false, false);
        testSvc(getAsUser(remote, PermSvcStaticPermLocator.class, GLOBAL_READ).getServiceWritePerm(SCOPE), EXPECTED_SCOPE, false, false, false, false);
        testSvc(getAsUser(remote, PermSvcStaticPermLocator.class, GLOBAL_WRITE).getServiceWritePerm(SCOPE), EXPECTED_SCOPE, true, true, false, false);
        testSvc(getAsUser(remote, PermSvcStaticPermLocator.class, GLOBAL_ADMIN).getServiceWritePerm(SCOPE), EXPECTED_SCOPE, true, true, true, true);

        testSvc(getAsUser(remote, PermSvcStaticPermLocator.class, LOCAL_READ).getServiceWritePerm(SCOPE), EXPECTED_SCOPE, false, false, false, false);
        testSvc(getAsUser(remote, PermSvcStaticPermLocator.class, LOCAL_WRITE).getServiceWritePerm(SCOPE), EXPECTED_SCOPE, true, true, false, false);
        testSvc(getAsUser(remote, PermSvcStaticPermLocator.class, LOCAL_ADMIN).getServiceWritePerm(SCOPE), EXPECTED_SCOPE, true, true, true, false);

        testSvc(getAsUser(remote, PermSvcStaticPermLocator.class, LOCAL_READ).getServiceWritePerm(OTHER_SCOPE), null, false, false, false, false);
        testSvc(getAsUser(remote, PermSvcStaticPermLocator.class, LOCAL_WRITE).getServiceWritePerm(OTHER_SCOPE), null, false, false, false, false);
        testSvc(getAsUser(remote, PermSvcStaticPermLocator.class, LOCAL_ADMIN).getServiceWritePerm(OTHER_SCOPE), null, false, false, false, false);
        // @formatter:on
    }

    @Test
    void testStaticLocatorNoScopeWritePerm(RemoteService remote) {
        // test with no permission annotation set.

        // @formatter:off
        testSvc(getAsUser(remote, PermSvcStaticPermLocator.class, NO_PERM).getServiceNoScopeWritePerm(), ObjectScope.EMPTY, false, false, false, false);
        testSvc(getAsUser(remote, PermSvcStaticPermLocator.class, GLOBAL_READ).getServiceNoScopeWritePerm(), ObjectScope.EMPTY, false, false, false, false);
        testSvc(getAsUser(remote, PermSvcStaticPermLocator.class, GLOBAL_WRITE).getServiceNoScopeWritePerm(), ObjectScope.EMPTY, true, true, false, false);
        testSvc(getAsUser(remote, PermSvcStaticPermLocator.class, GLOBAL_ADMIN).getServiceNoScopeWritePerm(), ObjectScope.EMPTY, true, true, true, true);

        testSvc(getAsUser(remote, PermSvcStaticPermLocator.class, LOCAL_READ).getServiceNoScopeWritePerm(), ObjectScope.EMPTY, false, false, false, false);
        testSvc(getAsUser(remote, PermSvcStaticPermLocator.class, LOCAL_WRITE).getServiceNoScopeWritePerm(), ObjectScope.EMPTY, false, false, false, false);
        testSvc(getAsUser(remote, PermSvcStaticPermLocator.class, LOCAL_ADMIN).getServiceNoScopeWritePerm(), ObjectScope.EMPTY, false, false, false, false);
        // @formatter:on
    }

}
