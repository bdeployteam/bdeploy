package io.bdeploy.ui.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.BHiveTransactions.Transaction;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.op.remote.FetchOperation;
import io.bdeploy.bhive.op.remote.PushOperation;
import io.bdeploy.common.cfg.Configuration.EnvironmentFallback;
import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.cli.ToolCategory;
import io.bdeploy.common.cli.data.RenderableResult;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.jersey.cli.LocalLoginManager;
import io.bdeploy.jersey.cli.RemoteServiceTool;
import io.bdeploy.logging.audit.RollingFileAuditor;
import io.bdeploy.ui.cli.RemoteTransferTool.TransferConfig;
import jakarta.ws.rs.core.UriBuilder;

@Help("Transfer products and software from one server to another")
@ToolCategory(TextUIResources.UI_CATEGORY)
@CliName("remote-transfer")
public class RemoteTransferTool extends RemoteServiceTool<TransferConfig> {

    public @interface TransferConfig {

        @Help("Name of the source instance group or software repository.")
        String sourceGroup();

        @Help("Name of the target instance group or software repository.")
        String targetGroup();

        @Help("The name of the local login to use to contact the target server.")
        String targetLogin();

        @Help("The remote URL of the target server. Preferrably use --targetLogin.")
        String targetRemote();

        @Help("The token used to contact the --targetRemote. Preferrably use --targetLogin.")
        String targetToken();

        @Help("Path to a temporary BHive to use. Can be re-used for subsequent transfers. If not given will be a temporary directory.")
        String transferHive();

        @Help("The Key of the product or external software to transfer, can be found using remote-product or remote-repo-software tools.")
        String key();

        @Help("The version of the product or external software to transfer, can be found using remote-product or remote-repo-software tools.")
        String version();

        @Help("Path to the directory which contains the local login data")
        @EnvironmentFallback("BDEPLOY_LOGIN_STORAGE")
        String loginStorage();
    }

    public RemoteTransferTool() {
        super(TransferConfig.class);
    }

    @Override
    protected RenderableResult run(TransferConfig config, RemoteService source) {
        helpAndFailIfMissing(config.sourceGroup(), "--sourceGroup missing");
        helpAndFailIfMissing(config.targetGroup(), "--targetGroup missing");

        helpAndFailIfMissing(config.key(), "--key missing");
        helpAndFailIfMissing(config.version(), "--version missing");

        RemoteService target = null;

        if (config.targetLogin() != null) {
            target = new LocalLoginManager(config.loginStorage()).getNamedService(config.targetLogin());
        } else if (config.targetRemote() != null && config.targetToken() != null) {
            target = new RemoteService(UriBuilder.fromUri(config.targetRemote()).build(), config.targetToken());
        }

        if (target == null) {
            helpAndFail("No or invalid target server specified");
            return createNoOp();
        }

        Path hivePath = config.transferHive() != null ? Paths.get(config.transferHive()) : null;
        if (hivePath == null) {
            try {
                hivePath = Files.createTempDirectory("bd-tx-");
            } catch (IOException e) {
                throw new IllegalStateException("Cannot create temporary directory", e);
            }
        }

        try (BHive hive = new BHive(hivePath.toUri(), RollingFileAuditor.getFactory().apply(hivePath), getActivityReporter());
                Transaction tx = hive.getTransactions().begin()) {
            Manifest.Key k = new Manifest.Key(config.key(), config.version());

            // 1. fetch from source server
            out().println("Fetching " + k + " from " + config.sourceGroup() + " on " + source.getUri());
            hive.execute(new FetchOperation().setRemote(source).setHiveName(config.sourceGroup()).addManifest(k));

            // 2. push to target server
            out().println("Pushing " + k + " to " + config.targetGroup() + " on " + target.getUri());
            hive.execute(new PushOperation().setRemote(target).setHiveName(config.targetGroup()).addManifest(k));
        } finally {
            if (config.transferHive() == null) {
                PathHelper.deleteRecursiveRetry(hivePath);
            }
        }

        return createSuccess();
    }
}
