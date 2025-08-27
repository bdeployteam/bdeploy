package io.bdeploy.minion.versioning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.bdeploy.api.remote.v1.PublicInstanceResource;
import io.bdeploy.api.remote.v1.PublicRootResource;
import io.bdeploy.api.remote.v1.dto.CredentialsApi;
import io.bdeploy.api.remote.v1.dto.InstanceGroupConfigurationApi;
import io.bdeploy.api.remote.v1.dto.SoftwareRepositoryConfigurationApi;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.interfaces.remote.versioning.VersionMismatchFilter;
import io.bdeploy.jersey.TestServer;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

class VersioningTest {

    @RegisterExtension
    private final TestServer srv = new TestServer(VersionedImpl.class, UnversionedImpl.class, FakePRR.class);

    @Path("/versioned")
    @Consumes(MediaType.APPLICATION_JSON)
    public interface Versioned {

        @GET
        public String text();
    }

    @Path("/versioned")
    @Consumes(MediaType.APPLICATION_JSON)
    public interface VersionedV2 {

        @GET
        @Path("/v2")
        public String textV2();
    }

    public static class VersionedImpl implements Versioned {

        @Override
        public String text() {
            return "world";
        }
    }

    @Path("/unversioned")
    @Consumes(MediaType.APPLICATION_JSON)
    public interface Unversioned {

        @GET
        public String text();
    }

    @Path("/unversioned")
    @Consumes(MediaType.APPLICATION_JSON)
    public interface UnversionedV2 {

        @GET
        @Path("/v2")
        public String textV2();
    }

    public static class UnversionedImpl implements Unversioned {

        @Override
        public String text() {
            return "hello";
        }

    }

    public static class FakePRR implements PublicRootResource {

        @Override
        public String getVersion() {
            return "1.2.3";
        }

        /** @deprecated Because {@link PublicRootResource#login(String, String, boolean)} is deprecated. */
        @Deprecated(since = "2.3.0")
        @Override
        public Response login(String user, String pass, boolean full) {
            throw new WebApplicationException(Status.NOT_FOUND);
        }

        @Override
        public Response login2(CredentialsApi credentials, boolean full) {
            throw new WebApplicationException(Status.NOT_FOUND);
        }

        @Override
        public List<SoftwareRepositoryConfigurationApi> getSoftwareRepositories() {
            throw new WebApplicationException(Status.NOT_FOUND);
        }

        @Override
        public List<InstanceGroupConfigurationApi> getInstanceGroups() {
            throw new WebApplicationException(Status.NOT_FOUND);
        }

        @Override
        public InstanceGroupConfigurationApi getInstanceGroupByInstanceId(String instanceId) {
            throw new WebApplicationException(Status.NOT_FOUND);
        }

        @Override
        public PublicInstanceResource getInstanceResource(String group) {
            throw new WebApplicationException(Status.NOT_FOUND);
        }

    }

    @Test
    void testVersioning(RemoteService svc, Versioned versioned, Unversioned unversioned) {

        assertEquals("hello", unversioned.text());
        assertEquals("world", versioned.text());

        VersionedV2 versioned2 = ResourceProvider.getVersionedResource(svc, VersionedV2.class, null);
        UnversionedV2 unversioned2 = ResourceProvider.getResource(svc, UnversionedV2.class, null);

        var wea = assertThrows(WebApplicationException.class, versioned2::textV2);
        assertEquals(VersionMismatchFilter.CODE_VERSION_MISMATCH, wea.getResponse().getStatus());

        var wea2 = assertThrows(WebApplicationException.class, unversioned2::textV2);
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), wea2.getResponse().getStatus());
    }
}
