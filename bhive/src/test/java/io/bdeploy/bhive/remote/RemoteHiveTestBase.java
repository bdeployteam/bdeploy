package io.bdeploy.bhive.remote;

import java.net.URI;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.remote.jersey.BHiveJacksonModule;
import io.bdeploy.bhive.remote.jersey.BHiveLocatorImpl;
import io.bdeploy.bhive.remote.jersey.BHiveRegistry;
import io.bdeploy.bhive.remote.jersey.JerseyRemoteBHive;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.TestActivityReporter;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.jersey.TestServer;

@ExtendWith(TestActivityReporter.class)
public class RemoteHiveTestBase {

    @RegisterExtension
    TestServer ext = new TestServer();

    private BHiveRegistry registry;
    private RemoteBHive remote;
    private URI uri;

    @BeforeEach
    public void startRemoteServer(RemoteService svc, BHive hive, ActivityReporter r) throws Exception {
        registry = new BHiveRegistry(r);

        // requires TestHive extension on subclass.
        registry.register(JerseyRemoteBHive.DEFAULT_NAME, hive);

        ext.register(registry.binder());
        ext.register(new BHiveJacksonModule().binder());
        ext.register(BHiveLocatorImpl.class);

        ext.disableAuth();

        remote = RemoteBHive.forService(svc, JerseyRemoteBHive.DEFAULT_NAME, r);
    }

    @AfterEach
    public void stopServer() {
        if (remote != null) {
            remote.close();
            remote = null;
        }
        if (registry != null) {
            registry.close();
            registry = null;
        }
    }

    public RemoteBHive getRemote() {
        return remote;
    }

    public URI getUri() {
        return uri;
    }

}
