package io.bdeploy.ui.cli;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import io.bdeploy.common.cfg.Configuration.EnvironmentFallback;
import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.cli.ToolCategory;
import io.bdeploy.common.cli.data.DataTable;
import io.bdeploy.common.cli.data.DataTableColumn;
import io.bdeploy.common.cli.data.RenderableResult;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.interfaces.configuration.dcu.ApplicationConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceNodeConfigurationDto;
import io.bdeploy.interfaces.configuration.pcu.ProcessState;
import io.bdeploy.interfaces.configuration.pcu.ProcessStatusDto;
import io.bdeploy.interfaces.descriptor.application.ParameterDescriptor.ParameterType;
import io.bdeploy.interfaces.manifest.InstanceManifest;
import io.bdeploy.interfaces.manifest.state.InstanceStateRecord;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.jersey.cli.RemoteServiceTool;
import io.bdeploy.ui.api.InstanceGroupResource;
import io.bdeploy.ui.api.InstanceResource;
import io.bdeploy.ui.api.ProcessResource;
import io.bdeploy.ui.cli.RemotePortsTool.PortsConfig;
import io.bdeploy.ui.dto.InstanceNodeConfigurationListDto;

@Help("List remote ports used by and instance")
@ToolCategory(TextUIResources.UI_CATEGORY)
@CliName("remote-ports")
public class RemotePortsTool extends RemoteServiceTool<PortsConfig> {

    public @interface PortsConfig {

        @Help("Name of the instance group")
        @EnvironmentFallback("REMOTE_BHIVE")
        String instanceGroup();

        @Help(value = "UUID of the instance. The active tag of the instance will be queried.")
        String uuid();

        @Help(value = "List server ports and their respective states on the individual nodes.", arg = false)
        boolean list() default false;

        @Help(value = "List client ports in addition to server ports.", arg = false)
        boolean clients() default false;

    }

    public RemotePortsTool() {
        super(PortsConfig.class);
    }

    @Override
    protected RenderableResult run(PortsConfig config, RemoteService remote) {
        helpAndFailIfMissing(config.instanceGroup(), "--instanceGroup missing");

        if (config.list()) {
            return list(remote, config);
        }

        return createNoOp();
    }

    private DataTable list(RemoteService remote, PortsConfig config) {
        helpAndFailIfMissing(config.uuid(), "--uuid missing");

        DataTable table = createDataTable();

        table.setCaption("Ports of instance " + config.uuid() + " in instance group " + config.instanceGroup());
        table.column("Node", 20).column(new DataTableColumn("ProcessUuid", "App. ID", 14)).column("Process", 30)
                .column(new DataTableColumn("ProcessState", "P. State", 20)).column("Out of sync", 1).column("Description", 30)
                .column("Port", 5).column("Type", 6).column("State", 6).column("Rating", 3);

        // find the active version of the instance.
        InstanceResource ir = ResourceProvider.getResource(remote, InstanceGroupResource.class, getLocalContext())
                .getInstanceResource(config.instanceGroup());

        InstanceStateRecord state = ir.getDeploymentStates(config.uuid());
        String activeTag = state.activeTag;

        if (activeTag == null) {
            throw new IllegalArgumentException("The given instance has no active version");
        }

        // load the configuration of the active version and collect all ports to check.
        InstanceNodeConfigurationListDto nodeConfigs = ir.getNodeConfigurations(config.uuid(), activeTag);
        var ports = collectPorts(nodeConfigs);

        // updates the existing NodePort objects with state
        fetchAndUpdateStates(config, ir, ports);

        for (var port : ports) {
            if (port.type == ParameterType.CLIENT_PORT && !config.clients()) {
                continue;
            }

            if (port.type == ParameterType.SERVER_PORT) {
                table.row().cell(port.getNodeName()).cell(port.appUid).cell(port.appName).cell(port.processState)
                        .cell(port.processState.isRunning() && !activeTag.equals(port.runningTag) ? "*" : "")
                        .cell(port.description).cell(port.port).cell("SERVER").cell(port.state ? "open" : "closed")
                        .cell(port.getRating() ? "OK" : "BAD").build();
            } else {
                table.row().cell(port.getNodeName()).cell(port.appUid).cell(port.appName).cell(port.processState).cell("")
                        .cell(port.description).cell(port.port).cell("CLIENT").cell("").cell("").build();
            }
        }

        return table;
    }

    private void fetchAndUpdateStates(PortsConfig config, InstanceResource ir, List<NodePort> allPorts) {
        // split by node to be able to fetch individual states per node and filter for server ports.
        var ports = allPorts.stream().filter(np -> np.type == ParameterType.SERVER_PORT)
                .collect(Collectors.groupingBy(NodePort::getNodeName));

        // load the current process states
        ProcessResource pr = ir.getProcessResource(config.uuid());
        var status = pr.getStatus();

        // fetch the states of each node's collected ports
        for (var np : ports.entrySet()) {
            var states = ir.getPortStates(config.uuid(), np.getKey(), np.getValue().stream().map(NodePort::getPort).toList());
            var nodePortsGrouped = np.getValue().stream().collect(Collectors.groupingBy(NodePort::getPort));

            for (var portAndState : states.entrySet()) {
                var matchedPorts = nodePortsGrouped.get(portAndState.getKey());
                if (matchedPorts == null || matchedPorts.isEmpty()) {
                    throw new IllegalStateException(
                            "Cannot map port " + portAndState.getKey() + " of node " + np.getKey() + ", port: " + matchedPorts);
                }

                if (matchedPorts.size() > 1) {
                    out().println("WARNING: Server port " + portAndState.getKey() + " assigned to multiple parameters: "
                            + matchedPorts);
                }

                for (var port : matchedPorts) {
                    port.state = portAndState.getValue();
                    ProcessStatusDto processStatus = status.get(port.appUid);
                    if (processStatus != null) {
                        port.processState = processStatus.processState;
                        port.runningTag = processStatus.instanceTag;
                    }
                }
            }
        }
    }

    private List<NodePort> collectPorts(InstanceNodeConfigurationListDto nodeConfigs) {
        var result = new ArrayList<NodePort>();

        for (InstanceNodeConfigurationDto node : nodeConfigs.nodeConfigDtos) {

            for (ApplicationConfiguration config : node.nodeConfiguration.applications) {

                var desc = nodeConfigs.applications.stream().filter(a -> a.name.equals(config.application.getName())).findFirst()
                        .orElse(null);
                if (desc == null) {
                    throw new IllegalStateException("Cannot find application descriptor " + config.application.getName()
                            + " for configuration " + config.name);
                }

                if (desc.descriptor.startCommand != null) {
                    for (var param : config.start.parameters) {
                        var paramDesc = desc.descriptor.startCommand.parameters.stream().filter(p -> p.uid.equals(param.uid))
                                .findFirst().orElse(null);
                        if (paramDesc != null
                                && (paramDesc.type == ParameterType.CLIENT_PORT || paramDesc.type == ParameterType.SERVER_PORT)) {
                            try {
                                // FIXME: param.value might need processing for linkExpression! this was broken before.
                                result.add(new NodePort(node.nodeName, config.name, config.uid, paramDesc.type, paramDesc.name,
                                        Integer.valueOf(param.value.getPreRenderable())));
                            } catch (NumberFormatException e) {
                                out().println("Illegal port value configured for " + param.uid + " on application " + config.uid);
                            }
                        }
                    }
                }
            }
        }

        // sort by port number.
        Collections.sort(result, Comparator.comparing(NodePort::getPort));

        return result;
    }

    private static class NodePort {

        final String nodeName;

        final String appName;
        final String appUid;

        final ParameterType type;
        final String description;
        final int port;

        boolean state;
        ProcessState processState;
        String runningTag;

        public NodePort(String nodeName, String appName, String appUid, ParameterType type, String description, int port) {
            this.nodeName = nodeName;
            this.appName = appName;
            this.appUid = appUid;
            this.type = type;
            this.description = description;
            this.port = port;
        }

        public int getPort() {
            return port;
        }

        public String getNodeName() {
            if (InstanceManifest.CLIENT_NODE_NAME.equals(nodeName)) {
                return "Client Applications";
            }
            return nodeName;
        }

        private boolean getRating() {
            // if running, it should be open.
            if (processState.isRunning()) {
                return state;
            }

            // otherwise it should not be open.
            return !state;
        }

        @Override
        public String toString() {
            return description + " on " + appName + " = " + port;
        }

    }

}
