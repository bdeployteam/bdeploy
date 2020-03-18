package io.bdeploy.bhive.remote;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.TestHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.op.ImportOperation;
import io.bdeploy.bhive.op.ManifestListOperation;
import io.bdeploy.bhive.op.remote.FetchOperation;
import io.bdeploy.bhive.op.remote.PushOperation;
import io.bdeploy.bhive.remote.jersey.BHiveJacksonModule;
import io.bdeploy.bhive.remote.jersey.BHiveLocatorImpl;
import io.bdeploy.bhive.remote.jersey.BHiveRegistry;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.ContentHelper;
import io.bdeploy.common.TempDirectory;
import io.bdeploy.common.TempDirectory.TempDir;
import io.bdeploy.common.TestActivityReporter;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.jersey.TestServer;

@ExtendWith(TestHive.class)
@ExtendWith(TempDirectory.class)
@ExtendWith(TestActivityReporter.class)
public class TestTwoHivesServer {

    @RegisterExtension
    TestServer ext = new TestServer();

    private BHiveRegistry registry;

    @BeforeEach
    void prepare(@TempDir Path tmp, ActivityReporter r) throws Exception {
        BHive h1 = new BHive(tmp.resolve("1").toUri(), r);
        BHive h2 = new BHive(tmp.resolve("2").toUri(), r);

        registry = new BHiveRegistry(r);

        registry.register("h1", h1);
        registry.register("h2", h2);

        ext.register(registry.binder());
        ext.register(new BHiveJacksonModule().binder());
        ext.register(BHiveLocatorImpl.class);
    }

    @AfterEach
    public void cleanup() {
        if (registry != null) {
            registry.close();
            registry = null;
        }
    }

    @Test
    public void testTwoHives(RemoteService svc, BHive hive, @TempDir Path tmp, ActivityReporter r) throws Exception {
        try (RemoteBHive r1 = RemoteBHive.forService(svc, "h1", r); RemoteBHive r2 = RemoteBHive.forService(svc, "h2", r)) {
            Manifest.Key key = new Manifest.Key("app", "v1");

            Path src = ContentHelper.genSimpleTestTree(tmp, "src");
            hive.execute(new ImportOperation().setSourcePath(src).setManifest(key));

            assertThat(r1.getManifestInventory().size(), is(0));
            assertThat(r2.getManifestInventory().size(), is(0));

            hive.execute(new PushOperation().setRemote(svc).setHiveName("h2").addManifest(key));

            assertThat(r1.getManifestInventory().size(), is(0));
            assertThat(r2.getManifestInventory().size(), is(1));

            hive.execute(new PushOperation().setRemote(svc).setHiveName("h1").addManifest(key));

            assertThat(r1.getManifestInventory().size(), is(1));
            assertThat(r2.getManifestInventory().size(), is(1));

            Path testHiveDir = tmp.resolve("hive");
            try (BHive testHive = new BHive(testHiveDir.toUri(), r)) {
                testHive.execute(new FetchOperation().setRemote(svc).setHiveName("h1").addManifest(key));
                assertThat(testHive.execute(new ManifestListOperation()).size(), is(1));
            }
        }
    }

}
