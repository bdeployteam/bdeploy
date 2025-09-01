package io.bdeploy.ui.cli;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

import io.bdeploy.bhive.util.StorageHelper;
import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.cli.ToolCategory;
import io.bdeploy.common.cli.data.DataResult;
import io.bdeploy.common.cli.data.DataTable;
import io.bdeploy.common.cli.data.DataTableColumn;
import io.bdeploy.common.cli.data.RenderableResult;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.interfaces.minion.MinionDto;
import io.bdeploy.interfaces.minion.MinionStatusDto;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.jersey.cli.RemoteServiceTool;
import io.bdeploy.ui.api.MinionMode;
import io.bdeploy.ui.api.NodeManagementResource;
import io.bdeploy.ui.cli.RemoteNodeTool.NodeConfig;
import io.bdeploy.ui.dto.NodeAttachDto;

/**
 * Manages nodes.
 */
@Help("Manage nodes in a remote master")
@ToolCategory(TextUIResources.UI_CATEGORY)
@CliName("remote-node")
public class RemoteNodeTool extends RemoteServiceTool<NodeConfig> {

    public @interface NodeConfig {

        @Help("Adds a node with the given name.")
        String add();

        @Help("The name of the node to remove.")
        String remove();

        @Help("The node remote URL.")
        String node();

        @Help("The node remote authentication token.")
        String nodeToken();

        @Help("The node remote authentication token as file")
        String nodeTokenFile();

        @Help("A node identification file which can be used instead of the --add, --node and --nodeToken parameters")
        String nodeIdentFile();

        @Help(value = "When given, list all known nodes.", arg = false)
        boolean list() default false;
    }

    public RemoteNodeTool() {
        super(NodeConfig.class);
    }

    @Override
    protected RenderableResult run(NodeConfig config, RemoteService svc) {
        if (config.list()) {
            return doListMinions(svc);
        } else if (config.add() != null) {
            if (config.nodeIdentFile() != null) {
                NodeAttachDto nad = StorageHelper.fromPath(Paths.get(config.nodeIdentFile()), NodeAttachDto.class);
                return doAddMinion(svc, config.add(), nad.remote);
            } else {
                helpAndFailIfMissing(config.node(), "Missing --node");
                return doAddMinion(svc, config.add(), createNodeRemote(config));
            }
        } else if (config.remove() != null) {
            return doRemoveMinion(svc, config.remove());
        }
        return createNoOp();
    }

    private RemoteService createNodeRemote(NodeConfig rc) {
        URI r;
        try {
            r = new URI(rc.node());
        } catch (URISyntaxException e) {
            helpAndFail("Invalid Node URI");
            return null;
        }
        if (rc.nodeTokenFile() != null) {
            try {
                String token = new String(Files.readAllBytes(Paths.get(rc.nodeTokenFile())), StandardCharsets.UTF_8);
                return new RemoteService(r, token);
            } catch (IOException e) {
                throw new IllegalStateException("Cannot read token from " + rc.nodeTokenFile(), e);
            }
        } else if (rc.nodeToken() != null) {
            return new RemoteService(r, rc.nodeToken());
        }
        helpAndFail("Either --nodeToken or --nodeTokenFile must be given");
        return null;
    }

    private DataResult doAddMinion(RemoteService svc, String minionName, RemoteService nodeRemote) {
        NodeManagementResource root = ResourceProvider.getResource(svc, NodeManagementResource.class, getLocalContext());

        NodeAttachDto dto = new NodeAttachDto();
        dto.name = minionName;
        dto.remote = nodeRemote;
        dto.sourceMode = MinionMode.NODE;

        root.addServerNode(dto);

        return createSuccess().addField("Node Name", minionName);
    }

    private DataResult doRemoveMinion(RemoteService svc, String minionName) {
        NodeManagementResource root = ResourceProvider.getResource(svc, NodeManagementResource.class, getLocalContext());
        root.removeNode(minionName);

        return createSuccess().addField("Node Name", minionName);
    }

    private RenderableResult doListMinions(RemoteService r) {
        DataTable table = createDataTable();
        table.column(new DataTableColumn.Builder("Name").setMinWidth(5).build());
        table.column(new DataTableColumn.Builder("OS").setMinWidth(7).build());
        table.column(new DataTableColumn.Builder("URI").build());
        table.column(new DataTableColumn.Builder("Online").setMinWidth(6).build());
        table.column(new DataTableColumn.Builder("Status").setMinWidth(6).build());

        NodeManagementResource root = ResourceProvider.getResource(r, NodeManagementResource.class, getLocalContext());

        for (Map.Entry<String, MinionStatusDto> entry : root.getNodes().entrySet()) {
            String name = entry.getKey();
            MinionStatusDto details = entry.getValue();
            MinionDto config = details.config;
            table.row().cell(name).cell(config.os != null ? config.os.name() : "Unknown").cell(config.remote.getUri())
                    .cell(details.offline ? "" : "*").cell(details.infoText).build();
        }
        return table;
    }

}
