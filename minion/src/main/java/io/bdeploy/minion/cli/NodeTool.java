package io.bdeploy.minion.cli;

import static io.bdeploy.common.util.RuntimeAssert.assertTrue;

import java.nio.file.Paths;
import java.util.Map;

import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.audit.AuditRecord;
import io.bdeploy.common.cfg.Configuration.EnvironmentFallback;
import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cfg.Configuration.Validator;
import io.bdeploy.common.cfg.MinionRootValidator;
import io.bdeploy.common.cfg.PathOwnershipValidator;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.cli.ToolCategory;
import io.bdeploy.common.cli.data.DataResult;
import io.bdeploy.common.cli.data.DataTable;
import io.bdeploy.common.cli.data.RenderableResult;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.interfaces.manifest.MinionManifest;
import io.bdeploy.interfaces.minion.MinionConfiguration;
import io.bdeploy.interfaces.minion.MinionDto;
import io.bdeploy.interfaces.minion.MinionStatusDto;
import io.bdeploy.interfaces.remote.MinionStatusResource;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.jersey.cli.RemoteServiceTool;
import io.bdeploy.minion.MinionRoot;
import io.bdeploy.minion.cli.NodeTool.NodeConfig;

/**
 * Manages nodes.
 */
@Help("Manage nodes in a masters root directory.")
@ToolCategory(MinionServerCli.MGMT_TOOLS)
@CliName("node")
public class NodeTool extends RemoteServiceTool<NodeConfig> {

    public @interface NodeConfig {

        @Help("Root directory for the master minion.")
        @EnvironmentFallback("BDEPLOY_ROOT")
        @Validator({ MinionRootValidator.class, PathOwnershipValidator.class })
        String root();

        @Help("Adds a node with the given name.")
        String add();

        @Help("The name of the node to remove.")
        String remove();

        @Help(value = "When given, list all known nodes.", arg = false)
        boolean list() default false;
    }

    public NodeTool() {
        super(NodeConfig.class);
    }

    @Override
    protected RenderableResult run(NodeConfig config, @RemoteOptional RemoteService svc) {
        helpAndFailIfMissing(config.root(), "Missing --root");
        ActivityReporter.Delegating delegate = new ActivityReporter.Delegating();
        delegate.setDelegate(getActivityReporter());
        try (MinionRoot r = new MinionRoot(Paths.get(config.root()), delegate)) {
            if (config.list()) {
                return doListMinions(r);
            } else if (config.add() != null) {
                r.getAuditor()
                        .audit(AuditRecord.Builder.fromSystem().addParameters(getRawConfiguration()).setWhat("add-node").build());
                return doAddMinion(r, config.add(), svc);
            } else if (config.remove() != null) {
                r.getAuditor().audit(
                        AuditRecord.Builder.fromSystem().addParameters(getRawConfiguration()).setWhat("remove-node").build());
                return doRemoveMinion(r, config.remove());
            }
        }
        return createNoOp();
    }

    private DataResult doAddMinion(MinionRoot root, String minionName, RemoteService nodeRemote) {
        helpAndFailIfMissing(root, "Missing --remote");
        assertTrue(nodeRemote.getUri().getScheme().equalsIgnoreCase("https"), "Only HTTPS nodes supported");
        try {
            // Try to contact the node to get some information
            MinionStatusResource statusResource = ResourceProvider.getResource(nodeRemote, MinionStatusResource.class,
                    getLocalContext());
            MinionStatusDto status = statusResource.getStatus();

            // Store information in our hive
            MinionManifest mf = new MinionManifest(root.getHive());
            MinionConfiguration minionConfig = mf.read();
            minionConfig.addMinion(minionName, status.config);
            mf.update(minionConfig);

            return createSuccess().addField("Node Name", minionName);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot add node. Check if the node is online and try again.", e);
        }
    }

    private DataResult doRemoveMinion(MinionRoot root, String minionName) {
        MinionManifest mf = new MinionManifest(root.getHive());
        MinionConfiguration minionConfig = mf.read();
        minionConfig.removeMinion(minionName);
        mf.update(minionConfig);
        return createSuccess().addField("Node Name", minionName);
    }

    private RenderableResult doListMinions(MinionRoot r) {
        DataTable table = createDataTable();
        table.column("Name", 20).column("OS", 20).column("URI", 50);

        for (Map.Entry<String, MinionDto> entry : r.getMinions().entrySet()) {
            String name = entry.getKey();
            MinionDto details = entry.getValue();
            table.row().cell(name).cell(details.os != null ? details.os.name() : "Unknown").cell(details.remote.getUri()).build();
        }
        return table;
    }

}
