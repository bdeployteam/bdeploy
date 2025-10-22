package io.bdeploy.minion.endpoints;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.SortedMap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.TestHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.op.ManifestLoadOperation;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.interfaces.configuration.dcu.EndpointsConfiguration;
import io.bdeploy.interfaces.descriptor.application.HttpEndpoint;
import io.bdeploy.interfaces.manifest.InstanceManifest;
import io.bdeploy.interfaces.remote.CommonRootResource;
import io.bdeploy.interfaces.remote.MasterRootResource;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.jersey.TestServer;
import io.bdeploy.minion.TestFactory;
import io.bdeploy.minion.TestMinion;
import io.bdeploy.minion.endpoints.HelloEndpoint.HelloResult;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@ExtendWith(TestHive.class)
@ExtendWith(TestMinion.class)
class EndpointsTest {

    private static final Logger log = LoggerFactory.getLogger(EndpointsTest.class);

    @RegisterExtension
    private final TestServer server = new TestServer(false, new Object[] { HelloEndpoint.class });

    @Test
    void testEndpoint(BHive local, MasterRootResource master, CommonRootResource common, RemoteService remote, @TempDir Path tmp)
            throws IOException {
        Manifest.Key instance = TestFactory.createApplicationsAndInstance(local, common, remote, tmp, true, server.getPort());

        String id = local.execute(new ManifestLoadOperation().setManifest(instance)).getLabels()
                .get(InstanceManifest.INSTANCE_LABEL);

        master.getNamedMaster("demo").install(instance);
        assertFalse(master.getNamedMaster("demo").getInstanceState(id).installedTags.isEmpty());

        assertThrows(ClientErrorException.class, () -> common.getInstanceResource("demo").getAllEndpoints(id).isEmpty());

        master.getNamedMaster("demo").activate(instance, false);
        assertEquals(instance.getTag(), master.getNamedMaster("demo").getInstanceState(id).activeTag);

        master.getNamedMaster("demo").start(id, List.of("app"));

        SortedMap<String, EndpointsConfiguration> allEndpoints = common.getInstanceResource("demo").getAllEndpoints(id);
        List<HttpEndpoint> httpEndpoints = allEndpoints.entrySet().stream().flatMap(e -> e.getValue().http.stream()).toList();
        assertEquals(1, httpEndpoints.size());
        assertEquals("test", httpEndpoints.get(0).id);

        // try to actually access the endpoint
        Response response = common.getInstanceResource("demo").getProxyResource(id, "app").get("test");
        log.info("Result: {}", response.getStatusInfo());
        assertEquals(200, response.getStatus());
        assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());

        HelloResult result = response.readEntity(HelloResult.class);

        assertNotNull(result);
        assertEquals("world", result.hello);

        // try to access sub-resource
        response = common.getInstanceResource("demo").getProxyResource(id, "app").get("test/sub");
        log.info("Result: {}", response.getStatusInfo());
        assertEquals(200, response.getStatus());
        assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());

        result = response.readEntity(HelloResult.class);

        assertNotNull(result);
        assertEquals("sub", result.hello);

        // manually construct request to be able to pass additional parameters
        WebTarget wt = ResourceProvider.of(remote).getBaseTarget().path("/master/common/proxy/test")
                .queryParam("BDeploy_group", "demo").queryParam("BDeploy_instance", id).queryParam("BDeploy_application", "app");

        HelloResult input = new HelloResult();
        input.hello = "put";
        input.time = 1;

        response = wt.queryParam("value", "QUERY").request().header("TestHeader", "TestValue")
                .buildPut(Entity.entity(input, MediaType.APPLICATION_JSON_TYPE)).invoke();

        log.info("Result: {}", response.getStatusInfo());
        assertEquals(200, response.getStatus());
        assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());

        result = response.readEntity(HelloResult.class);

        assertNotNull(result);
        assertEquals("put - QUERY - TestValue", result.hello);
    }
}
