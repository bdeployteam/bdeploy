package io.bdeploy.ui.cli;

import static io.bdeploy.common.util.StringHelper.isNullOrEmpty;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.bdeploy.common.cfg.Configuration.EnvironmentFallback;
import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.cli.ToolCategory;
import io.bdeploy.common.cli.data.DataResult;
import io.bdeploy.common.cli.data.DataTable;
import io.bdeploy.common.cli.data.DataTableColumn;
import io.bdeploy.common.cli.data.RenderableResult;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.FormatHelper;
import io.bdeploy.common.util.Threads;
import io.bdeploy.interfaces.configuration.dcu.ApplicationConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration;
import io.bdeploy.interfaces.configuration.pcu.ProcessDetailDto;
import io.bdeploy.interfaces.configuration.pcu.ProcessHandleDto;
import io.bdeploy.interfaces.configuration.pcu.ProcessProbeResultDto.ProcessProbeType;
import io.bdeploy.interfaces.configuration.pcu.ProcessState;
import io.bdeploy.interfaces.configuration.pcu.ProcessStatusDto;
import io.bdeploy.interfaces.descriptor.application.ProcessControlDescriptor;
import io.bdeploy.interfaces.manifest.state.InstanceStateRecord;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.jersey.cli.RemoteServiceTool;
import io.bdeploy.ui.api.InstanceGroupResource;
import io.bdeploy.ui.api.InstanceResource;
import io.bdeploy.ui.api.ProcessResource;
import io.bdeploy.ui.cli.RemoteProcessTool.RemoteProcessConfig;
import io.bdeploy.ui.dto.InstanceNodeConfigurationListDto;
import io.bdeploy.ui.dto.MappedInstanceProcessStatusDto;
import jakarta.ws.rs.NotFoundException;

@Help("Deploy to a remote master minion")
@ToolCategory(TextUIResources.UI_CATEGORY)
@CliName("remote-process")
public class RemoteProcessTool extends RemoteServiceTool<RemoteProcessConfig> {

    public @interface RemoteProcessConfig {

        @Help("ID of the instance to query/control")
        String uuid();

        @Help("The ID of the application to control, controls all applications of the instance if missing")
        String application();

        @Help("The name of the process control group. Must be specified along with --controlGroupNodeName")
        String controlGroupName();

        @Help("The name of the node process control group belongs to. Must be specified along with --controlGroupName")
        String controlGroupNodeName();

        @Help("The name of the instance group to work on")
        @EnvironmentFallback("REMOTE_BHIVE")
        String instanceGroup();

        @Help(value = "Wait for the process depending on its type. If the process is MANUAL or MANUAL_CONFIRM, wait until it terminated. If the process is type INSTANCE, wait until it reaches the RUNNING or STOPPED state, depending on the given command. Only valid if starting/stopping a single process.",
              arg = false)
        boolean join() default false;

        @Help(value = "List processes on the remote", arg = false)
        boolean list() default false;

        @Help(value = "Start a named process.", arg = false)
        boolean start() default false;

        @Help(value = "Stop a named process.", arg = false)
        boolean stop() default false;

        @Help("The server node on which to run the command on. This is required for operations that must single out a node, for example: if an app is configured on a multi node, you need to specify the node for which you want to see the details for.")
        String nodeName();
    }

    public RemoteProcessTool() {
        super(RemoteProcessConfig.class);
    }

    @Override
    protected RenderableResult run(RemoteProcessConfig config, RemoteService svc) {
        String groupName = config.instanceGroup();
        helpAndFailIfMissing(groupName, "Missing --instanceGroup");

        String instanceId = config.uuid();
        helpAndFailIfMissing(instanceId, "Missing --uuid");

        int flagCount = (config.list() ? 1 : 0) + (config.start() ? 1 : 0) + (config.stop() ? 1 : 0);
        if (flagCount == 0) {
            helpAndFail("Missing --start or --stop or --list");
        }
        if (flagCount > 1) {
            helpAndFail("You can enable only one flag at a time: --start, --stop or --list");
        }

        if (config.join() && (!(config.start() || config.stop()) || isNullOrEmpty(config.application()))) {
            helpAndFail("--join is only possible when starting/stopping a single application");
        }

        boolean hasControlGroupName = !isNullOrEmpty(config.controlGroupName());
        boolean hasControlGroupNodeName = !isNullOrEmpty(config.controlGroupNodeName());
        boolean hasAppId = !isNullOrEmpty(config.application());
        boolean hasNodeName = !isNullOrEmpty(config.nodeName());
        if (hasControlGroupName ^ hasControlGroupNodeName) {
            helpAndFail("--controlGroupName cannot be specified without --controlGroupNodeName and vice versa");
        }

        if (hasNodeName && !hasAppId) {
            helpAndFail("--nodeName requires --application to specify which process to manage");
        }

        if (hasControlGroupName && hasAppId) {
            helpAndFail("specify either only --application or only --controlGroupName");
        }

        InstanceResource ir = ResourceProvider.getResource(svc, InstanceGroupResource.class, getLocalContext())
                .getInstanceResource(groupName);

        InstanceStateRecord deploymentStates = ir.getDeploymentStates(instanceId);

        if (deploymentStates.activeTag == null) {
            return createResultWithErrorMessage("This tool requires an active instance version for the given instance");
        }

        if (config.list()) {
            return doList(config, ir, svc, deploymentStates);
        } else if (config.start() || config.stop()) {
            return doStartStop(config, ir);
        }
        return createNoOp();
    }

    private RenderableResult doList(RemoteProcessConfig config, InstanceResource ir, RemoteService svc,
            InstanceStateRecord deploymentStates) {
        String groupName = config.instanceGroup();
        String instanceId = config.uuid();
        String appId = config.application();
        if (appId == null) {
            return createAllProcessesTable(config, svc, ir, deploymentStates);
        } else {
            MappedInstanceProcessStatusDto allStatus = ir.getProcessResource(instanceId).getMappedStatus();
            ProcessDetailDto appStatus = ir.getProcessResource(instanceId)
                    .getDetails(config.nodeName() != null ? config.nodeName() : allStatus.processToNode.get(appId), appId);
            InstanceNodeConfigurationListDto cfg = ir.getNodeConfigurations(appStatus.status.instanceId,
                    appStatus.status.instanceTag);
            Optional<ApplicationConfiguration> app = findAppConfig(appId, cfg);

            DataResult result = createResultWithSuccessMessage(
                    "Details for " + appId + " of instance " + instanceId + " of instance group " + groupName)
                            .addField("Name", appStatus.status.appName).addField("Application ID", appStatus.status.appId)
                            .addField("Exit Code", appStatus.status.exitCode).addField("Instance ID", appStatus.status.instanceId)
                            .addField("Instance Version", appStatus.status.instanceTag).addField("Main PID", appStatus.status.pid)
                            .addField("State", appStatus.status.processState)
                            .addField("Retries", appStatus.retryCount + "/" + appStatus.maxRetryCount)
                            .addField("Start Type", app.isPresent() ? app.get().processControl.startType : "?")
                            .addField("StdIn attached", (appStatus.hasStdin ? "Yes" : "No"))
                            .addField("Startup Status", getLastProbeStatus(appStatus, ProcessProbeType.STARTUP))
                            .addField("Liveness Status", getLastProbeStatus(appStatus, ProcessProbeType.LIVENESS));

            if (appStatus.handle != null) {
                addProcessDetails(result, appStatus.handle, "");
            }

            return result;
        }
    }

    private RenderableResult doStartStop(RemoteProcessConfig config, InstanceResource ir) {
        if (config.start()) {
            return doActionsOnProcess(config, ir, ProcessResource::startAll, ProcessResource::startProcesses,
                    ProcessResource::startProcesses, true);
        } else if (config.stop()) {
            return doActionsOnProcess(config, ir, ProcessResource::stopAll, ProcessResource::stopProcesses,
                    ProcessResource::stopProcesses, false);
        }

        return createNoOp();
    }

    private static Optional<ApplicationConfiguration> loadAppConfig(RemoteProcessConfig config, InstanceResource ir) {
        var tag = ir.getDeploymentStates(config.uuid()).activeTag;
        var nodes = Optional.of(ir.getNodeConfigurations(config.uuid(), tag));
        return findAppConfig(config.application(), nodes.orElse(null));
    }

    private static void doJoin(long pollIntervalMs, Supplier<Map<String, ProcessStatusDto>> processStatesSupplier,
            Set<ProcessState> targets) {
        if (null == processStatesSupplier.get()) {
            // assuming that there is nothing to do because there isn't any node that we can take the status from
            return;
        }
        while (processStatesSupplier.get().values().stream().anyMatch(state -> !targets.contains(state.processState))) {
            Threads.sleep(pollIntervalMs);
        }
    }

    private static Optional<ApplicationConfiguration> findAppConfig(String appId, InstanceNodeConfigurationListDto nodes) {
        Optional<ApplicationConfiguration> app = Optional.empty();
        if (nodes == null) {
            return app;
        }

        for (var node : nodes.nodeConfigDtos) {
            app = node.nodeConfiguration.applications.stream().filter(a -> a.id.equals(appId)).findFirst();
            if (app.isPresent()) {
                break;
            }
        }
        return app;
    }

    private static void addProcessDetails(DataResult result, ProcessHandleDto pdd, String indent) {
        result.addField("Process PID=" + pdd.pid, String.format("%1$s└[cpu=%2$ds] %3$s %4$s", indent, pdd.totalCpuDuration,
                pdd.command, (pdd.arguments != null && pdd.arguments.length > 0 ? String.join(" ", pdd.arguments) : "")));

        for (ProcessHandleDto child : pdd.children) {
            addProcessDetails(result, child, indent + " ");
        }
    }

    private DataTable createAllProcessesTable(RemoteProcessConfig config, RemoteService svc, InstanceResource ir,
            InstanceStateRecord deploymentStates) {
        DataTable table = createDataTable();
        table.setCaption("Processes for Instance " + config.uuid() + " in Instance Group " + config.instanceGroup() + " on "
                + svc.getUri());
        table.column(new DataTableColumn.Builder("Name").setMinWidth(20).build());
        table.column(new DataTableColumn.Builder("ID").setMinWidth(13).build());
        table.column(new DataTableColumn.Builder("Node").setMinWidth(15).build());
        table.column(new DataTableColumn.Builder("Status").setMinWidth(6).build());
        table.column(new DataTableColumn.Builder("Ver.*").setName("Version").setMinWidth(5).build());
        table.column(new DataTableColumn.Builder("Product Version").setMinWidth(5).build());
        table.column(new DataTableColumn.Builder("Start Type").setMinWidth(5).build());
        table.column(new DataTableColumn.Builder("Started At").setMinWidth(0).build());
        table.column(new DataTableColumn.Builder("OS User").setMinWidth(0).build());
        table.column(new DataTableColumn.Builder("PID").setMinWidth(0).build());
        table.column(new DataTableColumn.Builder("Exit").setName("ExitCode").setMinWidth(4).build());
        table.column(new DataTableColumn.Builder("Startup Status").setMinWidth(7).build());
        table.column(new DataTableColumn.Builder("Liveness Status").setMinWidth(7).build());

        Map<String, Optional<InstanceConfiguration>> instanceInfos = new TreeMap<>();
        Map<String, Optional<InstanceNodeConfigurationListDto>> nodeDtos = new TreeMap<>();
        List<Map.Entry<String, ProcessStatusDto>> processEntries = getOrderedProcessEntries(config, ir,
                deploymentStates.activeTag);

        ProcessResource pr = ir.getProcessResource(config.uuid());
        MappedInstanceProcessStatusDto overall = pr.getMappedStatus();

        for (Map.Entry<String, ProcessStatusDto> processEntry : processEntries) {
            ProcessStatusDto processStatusDto = processEntry.getValue();
            String tag = processStatusDto.instanceTag;
            Optional<InstanceConfiguration> instance = instanceInfos.computeIfAbsent(tag, k -> {
                try {
                    return Optional.of(ir.readVersion(config.uuid(), tag));
                } catch (NotFoundException nf) {
                    // instance version not found - probably not synced to central.
                    out().println("WARNING: Cannot read instance version " + tag
                            + ". This can happen for instance if the central server is not synchronized.");
                    return Optional.ofNullable(null);
                }
            });
            Optional<InstanceNodeConfigurationListDto> nodes = nodeDtos.computeIfAbsent(tag, k -> {
                try {
                    return Optional.of(ir.getNodeConfigurations(config.uuid(), tag));
                } catch (NotFoundException nf) {
                    // instance version not found - probably not synced to central.
                    out().println("WARNING: Cannot read instance nodes for version " + tag
                            + ". This can happen for instance if the central server is not synchronized.");
                    return Optional.ofNullable(null);
                }
            });
            Optional<ApplicationConfiguration> cfg = findAppConfig(processStatusDto.appId, nodes.orElse(null));
            addProcessRows(table, pr, processEntry, instance.orElse(null), deploymentStates, cfg.orElse(null), overall);
        }

        table.addFooter(" * Versions marked with '*' are out-of-sync (not running from the active version)");

        String controlGroupName = config.controlGroupName();
        if (!isNullOrEmpty(controlGroupName)) {
            long count = processEntries.stream().map(e -> e.getValue().processState).distinct().count();
            String state = count == 1 ? processEntries.get(0).getValue().processState.name() : "MIXED";
            table.addFooter(String.format(" * Applications in control group %s are in %s state", controlGroupName, state));
        }

        return table;
    }

    private static List<Map.Entry<String, ProcessStatusDto>> getOrderedProcessEntries(RemoteProcessConfig config,
            InstanceResource ir, String activeTag) {
        String instanceId = config.uuid();
        String controlGroupName = config.controlGroupName();
        String controlGroupNodeName = config.controlGroupNodeName();

        List<String> processOrderList = ir.getNodeConfigurations(instanceId, activeTag).nodeConfigDtos.stream()
                .map(instanceNodeConfigurationDto -> instanceNodeConfigurationDto.nodeConfiguration).filter(Objects::nonNull)
                .filter(nodeConfig -> isNullOrEmpty(controlGroupNodeName) || controlGroupNodeName.equals(nodeConfig.name))
                .map(instanceNodeConfiguration -> instanceNodeConfiguration.controlGroups).filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(controlGroup -> isNullOrEmpty(controlGroupName) || controlGroupName.equals(controlGroup.name))
                .map(controlGroup -> controlGroup.processOrder).flatMap(Collection::stream).toList();

        Comparator<Map.Entry<String, ProcessStatusDto>> comparator = (a, b) -> {
            int result = Integer.compare(processOrderList.indexOf(a.getValue().appId),
                    processOrderList.indexOf(b.getValue().appId));
            return result == 0 ? a.getKey().compareTo(b.getKey()) : result;
        };

        return ir.getProcessResource(instanceId).getMappedStatus().processStates.entrySet().stream()
                .flatMap(configuredNodeEntry -> configuredNodeEntry.getValue().entrySet().stream()
                        .map(runtimeNodeEntry -> Map.entry(runtimeNodeEntry.getKey(), runtimeNodeEntry.getValue())))
                .sorted(comparator).collect(Collectors.toList());
    }

    private static String getLastProbeStatus(ProcessDetailDto appStatus, ProcessProbeType type) {
        if (appStatus.lastProbes == null) {
            return "";
        }
        return appStatus.lastProbes.stream().filter(probe -> probe.type == type).findFirst()
                .map(probe -> String.valueOf(probe.status)).orElse("");
    }

    private static void addProcessRows(DataTable table, ProcessResource pr, Map.Entry<String, ProcessStatusDto> processEntry,
            InstanceConfiguration instance, InstanceStateRecord deploymentStates, ApplicationConfiguration cfg,
            MappedInstanceProcessStatusDto overall) {
        String serverNode = processEntry.getKey();
        ProcessStatusDto process = processEntry.getValue();
        String configuredNode = overall.processToNode.get(process.appId);
        ProcessDetailDto detail = pr.getDetails(serverNode, process.appId);
        ProcessHandleDto handle = detail.handle;

        table.row().cell(process.appName) //
                .cell(process.appId) //
                .cell(getNodeNameDisplay(configuredNode, serverNode)) //
                .cell(process.processState.name()) //
                .cell(process.instanceTag + (process.instanceTag.equals(deploymentStates.activeTag) ? "" : "*")) //
                .cell(instance == null ? "?" : instance.product.getTag()) //
                .cell(cfg == null ? "?" : cfg.processControl.startType) //
                .cell(handle == null ? "-" : FormatHelper.formatTemporal(Instant.ofEpochMilli(handle.startTime))) //
                .cell(handle == null ? "-" : handle.user) //
                .cell(handle == null ? "-" : Long.toString(handle.pid)) //
                .cell(Integer.toString(process.exitCode)) //
                .cell(getLastProbeStatus(detail, ProcessProbeType.STARTUP)) //
                .cell(getLastProbeStatus(detail, ProcessProbeType.LIVENESS)).build(); //
    }

    private static String getNodeNameDisplay(String configuredNode, String serverNode) {
        if (configuredNode.equals(serverNode)) {
            return configuredNode;
        } else {
            return configuredNode + (serverNode != null ? "/" + serverNode : "");
        }
    }

    private DataResult doActionsOnProcess(RemoteProcessConfig config, InstanceResource ir,
            Consumer<ProcessResource> doActionsOnAll, BiConsumer<ProcessResource, List<String>> doActionsOnAppList,
            BiConsumer<ProcessResource, Map<String, List<String>>> doActionsWithNodeMap, boolean waitForStartupWhenJoining) {
        String instanceId = config.uuid();
        boolean doAll = isNullOrEmpty(config.application()) && isNullOrEmpty(config.controlGroupName());
        if (doAll) {
            doActionsOnAll.accept(ir.getProcessResource(instanceId));
        } else {
            String appId = config.application();
            if (!isNullOrEmpty(config.nodeName())) {
                doActionsWithNodeMap.accept(ir.getProcessResource(instanceId), Map.of(config.nodeName(), List.of(appId)));
            } else {
                String activeTag = ir.getDeploymentStates(instanceId).activeTag;
                List<String> appIds = !isNullOrEmpty(appId) ? List.of(appId)
                        : getOrderedProcessEntries(config, ir, activeTag).stream()
                                .map(processEntry -> processEntry.getValue().appId).toList();

                doActionsOnAppList.accept(ir.getProcessResource(instanceId), appIds);
            }

            if (config.join()) {
                Optional<ApplicationConfiguration> cfg = loadAppConfig(config, ir);
                if (cfg.isEmpty()) {
                    return createResultWithErrorMessage("Cannot load configuration for application " + appId);
                }

                var targetStates = waitForStartupWhenJoining
                        && (cfg.get().processControl.startType == ProcessControlDescriptor.ApplicationStartType.INSTANCE)
                                ? Set.of(ProcessState.RUNNING)
                                : Set.of(ProcessState.STOPPED, ProcessState.CRASHED_PERMANENTLY);

                doJoin(2000, () -> ir.getProcessResource(instanceId).getMappedStatus().processStates.get(appId), targetStates);
            }
        }

        return createSuccess();
    }
}
