package io.bdeploy.ui.cli;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.bdeploy.common.cfg.Configuration.EnvironmentFallback;
import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.cli.ToolCategory;
import io.bdeploy.common.cli.data.DataTable;
import io.bdeploy.common.cli.data.DataTableColumn;
import io.bdeploy.common.cli.data.RenderableResult;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.TemplateHelper;
import io.bdeploy.common.util.VariableResolver;
import io.bdeploy.interfaces.configuration.dcu.ApplicationConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceNodeConfigurationDto;
import io.bdeploy.interfaces.configuration.pcu.BulkPortStatesDto;
import io.bdeploy.interfaces.descriptor.variable.VariableDescriptor.VariableType;
import io.bdeploy.interfaces.manifest.state.InstanceStateRecord;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.interfaces.variables.Resolvers;
import io.bdeploy.jersey.cli.RemoteServiceTool;
import io.bdeploy.ui.api.InstanceGroupResource;
import io.bdeploy.ui.api.InstanceResource;
import io.bdeploy.ui.cli.RemotePortsTool.PortsConfig;
import io.bdeploy.ui.dto.InstanceNodeConfigurationListDto;
import io.bdeploy.ui.dto.ports.ApplicationPortStatesDto;
import io.bdeploy.ui.dto.ports.CompositePortStateDto;
import io.bdeploy.ui.dto.ports.InstancePortStatesDto;
import io.bdeploy.ui.dto.ports.PortStateDto;

@Help("List remote ports used by and instance")
@ToolCategory(TextUIResources.UI_CATEGORY)
@CliName("remote-ports")
public class RemotePortsTool extends RemoteServiceTool<PortsConfig> {

    public @interface PortsConfig {

        @Help("Name of the instance group")
        @EnvironmentFallback("REMOTE_BHIVE")
        String instanceGroup();

        @Help(value = "ID of the instance. The active tag of the instance will be queried.")
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
        table.column(new DataTableColumn.Builder("Node").setMinWidth(5).build());
        table.column(new DataTableColumn.Builder("App. ID").setName("ProcessId").setMinWidth(13).build());
        table.column(new DataTableColumn.Builder("Process").setMinWidth(5).build());
        table.column(new DataTableColumn.Builder("P. State").setName("ProcessState").setMinWidth(5).build());
        table.column(new DataTableColumn.Builder("Out of sync").setMinWidth(1).build());
        table.column(new DataTableColumn.Builder("Description").setMinWidth(0).build());
        table.column(new DataTableColumn.Builder("Port").setMinWidth(4).build());
        table.column(new DataTableColumn.Builder("Type").setMinWidth(6).build());
        table.column(new DataTableColumn.Builder("State").setMinWidth(6).build());
        table.column(new DataTableColumn.Builder("Rating").setMinWidth(3).build());

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
        var instancePortStatesDto = collectAndMapPorts(nodeConfigs);

        // updates the existing NodePort objects with state
        fetchAndUpdateWithProcessStatusAndPortCheckResults(config, ir, instancePortStatesDto);

        instancePortStatesDto.appStates.forEach(
                appState -> appState.portStates.stream().sorted(Comparator.comparing(portState -> portState.port))
                        .forEach(portState -> {
                            if (portState.isClientPort() && !config.clients()) {
                                // ignore this row then
                            }

                            if (portState.states.isEmpty()) {
                                // then we do not know anything about this port param
                                table.row().cell(appState.configuredNode).cell(appState.appId).cell(appState.appName).cell("")
                                        .cell("").cell(portState.paramName).cell(portState.port)
                                        .cell(portState.isServerPort() ? "SERVER" : "CLIENT").cell("").cell("").build();
                                return;
                            }

                            portState.states.forEach((checkResult) -> {
                                if (portState.isServerPort()) {
                                    table.row().cell(getNodeNameDisplay(appState, checkResult)).cell(appState.appId)
                                            .cell(appState.appName).cell(checkResult.processState)
                                            .cell(checkResult.isRunning() && !activeTag.equals(checkResult.runningTag) ? "*" : "")

                                            .cell(portState.paramName).cell(portState.port).cell("SERVER")
                                            .cell(checkResult.isUsed ? "open" : "closed")
                                            .cell(checkResult.getRating() ? "OK" : "BAD").build();
                                } else {
                                    table.row().cell(getNodeNameDisplay(appState, checkResult)).cell(appState.appId)
                                            .cell(appState.appName).cell(checkResult.processState).cell("")
                                            .cell(portState.paramName).cell(portState.port).cell("CLIENT").cell("").cell("")
                                            .build();
                                }
                            });

                        }));

        return table;
    }

    /**
     * this method will retrieve data from the nodes and save the status and the port check result in instancePortStates
     */
    private void fetchAndUpdateWithProcessStatusAndPortCheckResults(PortsConfig config, InstanceResource ir,
            InstancePortStatesDto instancePortStates) {
        // load the current process states
        var status = ir.getProcessResource(config.uuid()).getMappedStatus();
        // fetch the states for all the ports at once
        Map<String, List<Integer>> groupedByNode = instancePortStates.getPortsMappedByConfiguredNode();
        BulkPortStatesDto bulkPortStatesDto = ir.getPortStatesBulk(config.uuid(), groupedByNode);

        instancePortStates.appStates.forEach(appState -> {
            var checkResultMappedOnServerNode = bulkPortStatesDto.getNodePortsState(appState.configuredNode);
            appState.portStates.forEach(compositePortState -> {
                checkResultMappedOnServerNode.forEach((serverNode, checkResult) -> {
                    if (checkResult.containsKey(compositePortState.port)) {
                        var portState = new PortStateDto();
                        portState.serverNode = serverNode;
                        portState.isUsed = checkResult.get(compositePortState.port);

                        if (status.processStates.containsKey(appState.appId) && status.processStates.get(appState.appId)
                                .containsKey(serverNode)) {
                            var processStatus = status.processStates.get(appState.appId).get(serverNode);
                            portState.processState = processStatus.processState;
                            portState.runningTag = processStatus.instanceTag;
                        }

                        compositePortState.states.add(portState);
                    } else {
                        throw new IllegalStateException(
                                "Cannot map port " + compositePortState.paramName + " of node " + serverNode + ", port: "
                                        + compositePortState.port);
                    }
                    checkForPortDuplicates(appState);
                });
            });
        });
    }

    /**
     * key = the name of the configured node
     */
    private InstancePortStatesDto collectAndMapPorts(InstanceNodeConfigurationListDto nodeConfigs) {
        var result = new InstancePortStatesDto();
        for (InstanceNodeConfigurationDto node : nodeConfigs.nodeConfigDtos) {
            for (ApplicationConfiguration config : node.nodeConfiguration.applications) {
                result.addApplicationPortState(config.name, config.id, node.nodeName, node.nodeConfiguration.nodeType,
                        getPortsOfApp(nodeConfigs, node, config));
            }
        }

        return result;
    }

    private void checkForPortDuplicates(ApplicationPortStatesDto appState) {
        Map<Integer, List<CompositePortStateDto>> groupedByPortNumber = appState.portStates.stream()
                .collect(Collectors.groupingBy(e -> e.port, Collectors.toList()));

        groupedByPortNumber.entrySet().stream().filter(entry -> entry.getValue().size() > 1).forEach(entry -> {
            out().println(
                    "WARNING: Server port " + entry.getKey() + " assigned to multiple parameters: " + entry.getValue().stream()
                            .map(paramState -> paramState.paramName));
        });
    }

    private List<CompositePortStateDto> getPortsOfApp(InstanceNodeConfigurationListDto nodeConfigs,
            InstanceNodeConfigurationDto node, ApplicationConfiguration config) {
        var desc = nodeConfigs.applications.stream().filter(a -> a.key.getName().equals(config.application.getName())).findFirst()
                .orElse(null);
        if (desc == null) {
            throw new IllegalStateException(
                    "Cannot find application descriptor " + config.application.getName() + " for configuration " + config.name);
        }

        List<CompositePortStateDto> result = new ArrayList<>();
        if (desc.descriptor.startCommand != null) {
            VariableResolver resolver = createResolver(node, config);
            for (var param : config.start.parameters) {
                var paramDesc = desc.descriptor.startCommand.parameters.stream().filter(p -> p.id.equals(param.id)).findFirst()
                        .orElse(null);
                if (paramDesc != null
                        && (paramDesc.type == VariableType.CLIENT_PORT || paramDesc.type == VariableType.SERVER_PORT)) {
                    try {
                        var val = param.value.value;
                        if (param.value.linkExpression != null) {
                            val = TemplateHelper.process(param.value.linkExpression, resolver);
                        }
                        result.add(new CompositePortStateDto(paramDesc.type, param.id, paramDesc.name, Integer.parseInt(val)));
                    } catch (NumberFormatException e) {
                        out().println("Illegal port value configured for " + param.id + " on application " + config.id);
                    }
                }
            }
        }
        return result;
    }

    private static VariableResolver createResolver(InstanceNodeConfigurationDto node, ApplicationConfiguration process) {
        return Resolvers.forApplication(Resolvers.forInstancePathIndependent(node.nodeConfiguration), node.nodeConfiguration,
                process);
    }

    private static String getNodeNameDisplay(ApplicationPortStatesDto appState, PortStateDto portState) {
        return appState.configuredNode + (portState != null && portState.serverNode != null ? "/" + portState.serverNode : "");
    }
}
