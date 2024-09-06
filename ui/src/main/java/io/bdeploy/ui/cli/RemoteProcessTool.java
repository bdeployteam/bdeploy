package io.bdeploy.ui.cli;

import static io.bdeploy.common.util.StringHelper.isNullOrEmpty;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Supplier;

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
import io.bdeploy.interfaces.manifest.state.InstanceStateRecord;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.jersey.cli.RemoteServiceTool;
import io.bdeploy.ui.api.InstanceGroupResource;
import io.bdeploy.ui.api.InstanceResource;
import io.bdeploy.ui.api.ProcessResource;
import io.bdeploy.ui.cli.RemoteProcessTool.RemoteProcessConfig;
import io.bdeploy.ui.dto.InstanceNodeConfigurationListDto;
import io.bdeploy.ui.dto.InstanceProcessStatusDto;
import jakarta.ws.rs.NotFoundException;

@Help("Deploy to a remote master minion")
@ToolCategory(TextUIResources.UI_CATEGORY)
@CliName("remote-process")
public class RemoteProcessTool extends RemoteServiceTool<RemoteProcessConfig> {

    public @interface RemoteProcessConfig {

        @Help("ID of the instance to query/control")
        String uuid();

        @Help("The name of the application to control, controls all applications of the instance if missing")
        String application();

        @Help("The name of the process control group. Must be specified along with --controlGroupNodeName")
        String controlGroupName();

        @Help("The name of the node process control group belongs to. Must be specified along with --controlGroupName")
        String controlGroupNodeName();

        @Help("The name of the instance group to work on")
        @EnvironmentFallback("REMOTE_BHIVE")
        String instanceGroup();

        @Help(value = "Wait till termination of a single started process", arg = false)
        boolean join() default false;

        @Help(value = "List processes on the remote", arg = false)
        boolean list() default false;

        @Help(value = "Start a named process.", arg = false)
        boolean start() default false;

        @Help(value = "Stop a named process.", arg = false)
        boolean stop() default false;
    }

    public RemoteProcessTool() {
        super(RemoteProcessConfig.class);
    }

    @Override
    protected RenderableResult run(RemoteProcessConfig config, RemoteService svc) {
        String instanceId = config.uuid();
        helpAndFailIfMissing(instanceId, "Missing --uuid");

        String groupName = config.instanceGroup();
        helpAndFailIfMissing(groupName, "Missing --instanceGroup");

        int flagCount = (config.list() ? 1 : 0) + (config.start() ? 1 : 0) + (config.stop() ? 1 : 0);
        if (flagCount == 0) {
            helpAndFail("Missing --start or --stop or --list");
        }
        if (flagCount > 1) {
            helpAndFail("You can enable only one flag at a time: --start, --stop or --list");
        }

        if (config.join() && (!config.start() || isNullOrEmpty(config.application()))) {
            helpAndFail("--join is only possible when starting a single application");
        }

        boolean hasControlGroupName = !isNullOrEmpty(config.controlGroupName());
        boolean hasControlGroupNodeName = !isNullOrEmpty(config.controlGroupNodeName());
        boolean hasAppId = !isNullOrEmpty(config.application());
        if (hasControlGroupName ^ hasControlGroupNodeName) {
            helpAndFail("--controlGroupName cannot be specified without --controlGroupNodeName and vice versa");
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
            return doStartStop(config, instanceId, ir);
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
            InstanceProcessStatusDto allStatus = ir.getProcessResource(instanceId).getStatus();
            ProcessDetailDto appStatus = ir.getProcessResource(instanceId).getDetails(allStatus.processToNode.get(appId), appId);
            InstanceNodeConfigurationListDto cfg = ir.getNodeConfigurations(appStatus.status.instanceId,
                    appStatus.status.instanceTag);
            Optional<ApplicationConfiguration> app = findAppConfig(appStatus.status, cfg);

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

    private RenderableResult doStartStop(RemoteProcessConfig config, String instanceId, InstanceResource ir) {
        boolean doAll = isNullOrEmpty(config.application()) && isNullOrEmpty(config.controlGroupName());
        if (config.start()) {
            if (doAll) {
                ir.getProcessResource(instanceId).startAll();
            } else {
                ir.getProcessResource(instanceId).startProcesses(getAppIds(config, ir));
                if (config.join()) {
                    doJoin(2000, () -> ir.getProcessResource(instanceId).getStatus().processStates
                            .get(config.application()).processState);
                }
            }
        } else if (config.stop()) {
            if (doAll) {
                ir.getProcessResource(instanceId).stopAll();
            } else {
                ir.getProcessResource(instanceId).stopProcesses(getAppIds(config, ir));
            }
        }
        return createSuccess();
    }

    private static List<String> getAppIds(RemoteProcessConfig config, InstanceResource ir) {
        String appId = config.application();
        if (!isNullOrEmpty(appId)) {
            return List.of(appId);
        }
        String instanceId = config.uuid();
        String activeTag = ir.getDeploymentStates(instanceId).activeTag;
        return getOrderedProcessEntries(config, ir, activeTag).stream().map(processEntry -> processEntry.appId).toList();
    }

    private static void doJoin(long pollIntervalMs, Supplier<ProcessState> stateSupplier) {
        while (!stateSupplier.get().isStopped()) {
            Threads.sleep(pollIntervalMs);
        }
    }

    private static Optional<ApplicationConfiguration> findAppConfig(ProcessStatusDto processStatusDto,
            InstanceNodeConfigurationListDto nodes) {
        Optional<ApplicationConfiguration> app = Optional.empty();
        if (nodes == null) {
            return app;
        }

        for (var node : nodes.nodeConfigDtos) {
            app = node.nodeConfiguration.applications.stream().filter(a -> a.id.equals(processStatusDto.appId)).findFirst();
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
        List<ProcessStatusDto> processEntries = getOrderedProcessEntries(config, ir, deploymentStates.activeTag);

        ProcessResource pr = ir.getProcessResource(config.uuid());
        InstanceProcessStatusDto overall = pr.getStatus();

        for (ProcessStatusDto processEntry : processEntries) {
            String tag = processEntry.instanceTag;
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
            Optional<ApplicationConfiguration> cfg = findAppConfig(processEntry, nodes.orElse(null));
            addProcessRows(table, pr, processEntry, instance.orElse(null), deploymentStates, cfg.orElse(null), overall);
        }

        table.addFooter(" * Versions marked with '*' are out-of-sync (not running from the active version)");

        String controlGroupName = config.controlGroupName();
        if (!isNullOrEmpty(controlGroupName)) {
            long count = processEntries.stream().map(e -> e.processState).distinct().count();
            String state = count == 1 ? processEntries.get(0).processState.name() : "MIXED";
            table.addFooter(String.format(" * Applications in control group %s are in %s state", controlGroupName, state));
        }

        return table;
    }

    private static List<ProcessStatusDto> getOrderedProcessEntries(RemoteProcessConfig config, InstanceResource ir,
            String activeTag) {
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

        Map<String, ProcessStatusDto> status = ir.getProcessResource(instanceId).getStatus().processStates;

        Comparator<ProcessStatusDto> comparator = (a, b) -> Integer.compare(processOrderList.indexOf(a.appId),
                processOrderList.indexOf(b.appId));
        return status.values().stream().sorted(comparator).toList();
    }

    private static String getLastProbeStatus(ProcessDetailDto appStatus, ProcessProbeType type) {
        if (appStatus.lastProbes == null) {
            return "";
        }
        return appStatus.lastProbes.stream().filter(probe -> probe.type == type).findFirst()
                .map(probe -> String.valueOf(probe.status)).orElse("");
    }

    private static void addProcessRows(DataTable table, ProcessResource pr, ProcessStatusDto process,
            InstanceConfiguration instance, InstanceStateRecord deploymentStates, ApplicationConfiguration cfg,
            InstanceProcessStatusDto overall) {

        ProcessDetailDto detail = pr.getDetails(overall.processToNode.get(process.appId), process.appId);
        ProcessHandleDto handle = detail.handle;

        table.row().cell(process.appName) //
                .cell(process.appId) //
                .cell(process.processState.name()) //
                .cell(process.instanceTag + (process.instanceTag.equals(deploymentStates.activeTag) ? "" : "*")) //
                .cell(instance == null ? "?" : instance.product.getTag()) //
                .cell(cfg == null ? "?" : cfg.processControl.startType) //
                .cell(handle == null ? "-" : FormatHelper.formatInstant(Instant.ofEpochMilli(handle.startTime))) //
                .cell(handle == null ? "-" : handle.user) //
                .cell(handle == null ? "-" : Long.toString(handle.pid)) //
                .cell(Integer.toString(process.exitCode)) //
                .cell(getLastProbeStatus(detail, ProcessProbeType.STARTUP)) //
                .cell(getLastProbeStatus(detail, ProcessProbeType.LIVENESS)).build(); //
    }

}
