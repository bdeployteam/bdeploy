package io.bdeploy.bhive.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.util.Map;
import java.util.TreeMap;

import org.glassfish.hk2.utilities.binding.AbstractBinder;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.cli.ServeTool.ServeConfig;
import io.bdeploy.bhive.remote.jersey.BHiveJacksonModule;
import io.bdeploy.bhive.remote.jersey.BHiveLocatorImpl;
import io.bdeploy.bhive.remote.jersey.BHiveRegistry;
import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cfg.Configuration.Validator;
import io.bdeploy.common.cfg.ExistingPathValidator;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.cli.ToolBase.ConfiguredCliTool;
import io.bdeploy.common.cli.ToolCategory;
import io.bdeploy.common.cli.data.RenderableResult;
import io.bdeploy.common.security.SecurityHelper;
import io.bdeploy.jersey.JerseyServer;
import io.bdeploy.jersey.JerseySessionConfiguration;
import io.bdeploy.jersey.ws.change.ObjectChangeBroadcaster;
import io.bdeploy.jersey.ws.change.ObjectChangeWebSocket;

/**
 * Starts a HTTP(S) server which serves given {@link BHive}s other the network.
 */
@Help("Serve given BHives over the network.")
@ToolCategory(BHiveCli.SERVER_TOOLS)
@CliName("serve")
public class ServeTool extends ConfiguredCliTool<ServeConfig> {

    public @interface ServeConfig {

        @Help("Mapping from context path to BHive directory, ':' separated. E.g. 'myhive:/path/to/hive' will serve on 'https://host:port/api/hives/myhive'")
        String[] serve();

        @Help("Private KeyStore containing the server certificate and private key")
        @Validator(ExistingPathValidator.class)
        String keystore();

        @Help("Passphrase for the private KeyStore")
        String passphrase();

        @Help("Port to serve on")
        short port() default 7700;
    }

    public ServeTool() {
        super(ServeConfig.class);
    }

    @Override
    protected RenderableResult run(ServeConfig config) {
        helpAndFailIfMissing(config.serve(), "Missing --serve");
        helpAndFailIfMissing(config.keystore(), "Missing --keystore");

        Map<String, BHive> hives = new TreeMap<>();
        try {
            for (String s : config.serve()) {
                int index = s.indexOf(':');
                String path = s.substring(0, index);
                String hive = s.substring(index + 1);

                Path hPath = Paths.get(hive);

                if (!Files.isDirectory(hPath)) {
                    out().println("WARNING: Skipping non-existant: " + hPath);
                    continue;
                }

                hives.put(path, new BHive(hPath.toUri(), getAuditorFactory().apply(hPath), getActivityReporter()));
            }
            Path storePath = Paths.get(config.keystore());

            char[] passphrase = config.passphrase() == null ? null : config.passphrase().toCharArray();
            KeyStore ks = SecurityHelper.getInstance().loadPrivateKeyStore(storePath, passphrase);

            out().println("Serving " + hives.size() + " hive" + (hives.size() == 1 ? "" : "s") + " on port " + config.port());

            runServer(config.port(), hives, ks, passphrase);
        } catch (Exception e) {
            throw new IllegalStateException("Error while preparing or running server", e);
        } finally {
            hives.forEach((k, v) -> v.close());
        }

        return null; // usually not reached.
    }

    private void runServer(short port, Map<String, BHive> hives, KeyStore ks, char[] passphrase) {
        try (JerseyServer server = new JerseyServer(port, ks, null, passphrase, JerseySessionConfiguration.noSessions())) {
            BHiveRegistry reg = new BHiveRegistry(getActivityReporter());

            for (Map.Entry<String, BHive> entry : hives.entrySet()) {
                reg.register(entry.getKey(), entry.getValue());
            }

            // WebSocket activity reporter bridge
            ObjectChangeWebSocket ocws = new ObjectChangeWebSocket(server.getKeyStore());
            server.registerWebsocketApplication(ObjectChangeWebSocket.OCWS_PATH, ocws);

            // locator will create nested resources on demand.
            server.registerResource(reg);
            server.register(reg.binder());
            server.register(new BHiveJacksonModule().binder());
            server.register(BHiveLocatorImpl.class);
            server.register(new AbstractBinder() {

                @Override
                protected void configure() {
                    bind(ocws).to(ObjectChangeBroadcaster.class);
                }
            });

            server.start();
            server.join();
        }
    }

}
