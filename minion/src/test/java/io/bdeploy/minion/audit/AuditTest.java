package io.bdeploy.minion.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import io.bdeploy.jersey.TestServer;
import io.bdeploy.logging.audit.RollingFileAuditor;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response.Status;

class AuditTest {

    @Path("/svc")
    public interface Service {

        @GET
        public String hello(@QueryParam("x") String x);

        @GET
        @Path("/noparam")
        public String helloNoParam();

        @GET
        @Path("/notfound")
        public String lookup();

        @GET
        @Path("/throwSomething")
        public String throwSomething();
    }

    public static class ServiceImpl implements Service {

        @Override
        public String hello(String x) {
            return "Hello " + x;
        }

        @Override
        public String helloNoParam() {
            return "Hello anonymous";
        }

        @Override
        public String lookup() {
            throw new WebApplicationException(Status.NOT_FOUND);
        }

        @Override
        public String throwSomething() {
            throw new RuntimeException("Something happened", new RuntimeException("With a cause"));
        }

    }

    @RegisterExtension
    private final TestServer srv = new TestServer(ServiceImpl.class);

    @BeforeEach
    void setAuditor(@TempDir java.nio.file.Path tmp) {
        srv.setAuditor(RollingFileAuditor.getFactory().apply(tmp));
    }

    @Test
    void testAudit(Service svc) throws IOException {
        svc.hello("Test");
        svc.helloNoParam();
        assertThrows(NotFoundException.class, svc::lookup);
        assertThrows(RuntimeException.class, svc::throwSomething);

        RollingFileAuditor auditor = (RollingFileAuditor) srv.getAuditor();
        List<String> lines = Files.readAllLines(auditor.getLogDir().resolve("audit.log"));
        assertEquals(2, lines.size());
        assertTrue(lines.get(1).contains("Something happened"));
        assertTrue(lines.get(1).contains("With a cause"));
    }

}
