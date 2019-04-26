package io.bdeploy.jersey.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response.Status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.bdeploy.common.TempDirectory;
import io.bdeploy.common.TempDirectory.TempDir;
import io.bdeploy.jersey.TestServer;
import io.bdeploy.jersey.audit.RollingFileAuditor;

@ExtendWith(TempDirectory.class)
public class AuditTest {

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

    }

    @RegisterExtension
    TestServer srv = new TestServer(ServiceImpl.class);

    @BeforeEach
    void setAuditor(@TempDir java.nio.file.Path tmp) {
        srv.setAuditor(new RollingFileAuditor(tmp));
    }

    @Test
    void testAudit(Service svc) throws IOException {
        svc.hello("Test");
        svc.helloNoParam();
        assertThrows(NotFoundException.class, () -> {
            svc.lookup();
        });

        RollingFileAuditor auditor = (RollingFileAuditor) srv.getAuditor();
        List<String> lines = Files.readAllLines(auditor.getLogFile());
        assertEquals(3, lines.size());
        assertTrue(lines.get(0).contains("q:x=[Test]"));
        assertTrue(lines.get(2).contains("Not Found"));
    }

}
