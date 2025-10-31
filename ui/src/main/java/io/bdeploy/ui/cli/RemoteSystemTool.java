package io.bdeploy.ui.cli;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.common.cfg.Configuration.EnvironmentFallback;
import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cfg.Configuration.Validator;
import io.bdeploy.common.cfg.ExistingPathValidator;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.cli.ToolCategory;
import io.bdeploy.common.cli.data.DataResult;
import io.bdeploy.common.cli.data.DataTable;
import io.bdeploy.common.cli.data.DataTableColumn;
import io.bdeploy.common.cli.data.DataTableRowBuilder;
import io.bdeploy.common.cli.data.ExitCode;
import io.bdeploy.common.cli.data.RenderableResult;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.FormatHelper;
import io.bdeploy.common.util.UuidHelper;
import io.bdeploy.interfaces.configuration.VariableConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration.InstancePurpose;
import io.bdeploy.interfaces.configuration.system.SystemConfiguration;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.jersey.cli.RemoteServiceTool;
import io.bdeploy.ui.FormDataHelper;
import io.bdeploy.ui.api.BackendInfoResource;
import io.bdeploy.ui.api.InstanceBulkResource;
import io.bdeploy.ui.api.InstanceGroupResource;
import io.bdeploy.ui.api.InstanceResource;
import io.bdeploy.ui.api.MinionMode;
import io.bdeploy.ui.api.SystemResource;
import io.bdeploy.ui.cli.RemoteSystemTool.SystemConfig;
import io.bdeploy.ui.dto.BulkOperationResultDto;
import io.bdeploy.ui.dto.InstanceDto;
import io.bdeploy.ui.dto.InstanceOverallStatusDto;
import io.bdeploy.ui.dto.InstanceTemplateReferenceResultDto.InstanceTemplateReferenceStatus;
import io.bdeploy.ui.dto.MappedInstanceProcessStatusDto;
import io.bdeploy.ui.dto.OperationResult;
import io.bdeploy.ui.dto.SystemConfigurationDto;
import io.bdeploy.ui.dto.SystemTemplateDto;
import io.bdeploy.ui.dto.SystemTemplateRequestDto;
import io.bdeploy.ui.dto.SystemTemplateRequestDto.SystemTemplateGroupMapping;
import io.bdeploy.ui.utils.InstanceTemplateHelper;
import jakarta.ws.rs.NotFoundException;

@Help("List, create and update system configurations")
@ToolCategory(TextUIResources.UI_CATEGORY)
@CliName("remote-system")
public class RemoteSystemTool extends RemoteServiceTool<SystemConfig> {

    public @interface SystemConfig {

        @Help("Name of the instance group")
        @EnvironmentFallback("REMOTE_BHIVE")
        String instanceGroup();

        @Help(value = "IDs of one or more systems")
        String[] uuid();

        @Help(value = "Lists systems on the remote. If UUIDs are given, will only display the corresponding systems. Unknown UUIDs will be ignored.",
              arg = false)
        boolean list() default false;

        @Help(value = "Show information about a system", arg = false)
        boolean info() default false;

        @Help(value = "Show the current status of a system", arg = false)
        boolean status() default false;

        @Help(value = "Includes more details in the status-command - this implies --sync", arg = false)
        boolean details() default false;

        @Help(value = "Force a syncronization before displaying the status", arg = false)
        boolean sync() default false;

        @Help(value = "Start all instances of the system", arg = false)
        boolean start() default false;

        @Help(value = "Stop all instances of the system", arg = false)
        boolean stop() default false;

        @Help(value = "Restart all instances of the system", arg = false)
        boolean restart() default false;

        @Help(value = "Create a system with a new ID", arg = false)
        boolean create() default false;

        @Help(value = "Update the system with the given ID", arg = false)
        boolean update() default false;

        @Help(value = "Delete the system with the given ID", arg = false)
        boolean delete() default false;

        @Help("The name to set for the updated system")
        String name();

        @Help("The description to set for the created/updated system")
        String description();

        @Help("The name of the managed server if the system is created on a target CENTRAL server.")
        String server();

        @Help("The key of a configuration variable to set using --update")
        String setVariable();

        @Help("The value of a configuration variable to set using --update")
        String setValue();

        @Help("The key of a configuration variable to remove using --update")
        String removeVariable();

        @Help("Path to a system template specification. Note that no variable input is supported, so the template must apply without user input.")
        @Validator(ExistingPathValidator.class)
        String createFrom();

        @Help("Purpose for all created instances when applying a system template, defaults to 'TEST'")
        InstancePurpose purpose() default InstancePurpose.TEST;
    }

    public RemoteSystemTool() {
        super(SystemConfig.class);
    }

    @Override
    protected RenderableResult run(SystemConfig config, RemoteService remote) {
        helpAndFailIfMissing(config.instanceGroup(), "--instanceGroup missing");

        InstanceGroupResource igr = ResourceProvider.getVersionedResource(remote, InstanceGroupResource.class, getLocalContext());
        SystemResource sr = igr.getSystemResource(config.instanceGroup());

        if (config.list()) {
            return doShowList(remote, sr, config);
        }
        if (config.create()) {
            return doCreate(remote, sr, config);
        }

        helpAndFailIfMissing(config.uuid(), "--uuid missing");

        if (config.info()) {
            return doShowInfo(sr, config);
        }
        if (config.status()) {
            return doShowStatus(igr, sr, config);
        }
        if (config.start()) {
            return doStart(igr, sr, config);
        }
        if (config.restart()) {
            return doRestart(igr, sr, config);
        }
        if (config.stop()) {
            return doStop(igr, sr, config);
        }
        if (config.update()) {
            return doUpdate(sr, config);
        }
        if (config.delete()) {
            return doDelete(sr, config);
        }

        return createNoOp();
    }

    private DataTable doShowList(RemoteService remote, SystemResource sr, SystemConfig config) {
        BackendInfoResource bir = ResourceProvider.getVersionedResource(remote, BackendInfoResource.class, getLocalContext());
        boolean central = false;
        if (bir.getVersion().mode == MinionMode.CENTRAL) {
            central = true;
        }

        DataTable table = createDataTable();
        table.setCaption("Systems of " + config.instanceGroup() + " on " + remote.getUri());

        table.column(new DataTableColumn.Builder("ID").setMinWidth(13).build());
        table.column(new DataTableColumn.Builder("Name").setMinWidth(5).build());
        table.column(new DataTableColumn.Builder("Description").setMinWidth(0).build());

        if (central) {
            table.column(new DataTableColumn.Builder("Target Server").setMinWidth(10).build());
        }

        String[] uuid = config.uuid();
        boolean uuidSet = uuid != null && uuid.length > 0;
        Set<String> uuids = uuidSet ? new HashSet<>(Arrays.asList(uuid)) : null;
        for (var system : sr.list()) {
            if (uuidSet && !uuids.contains(system.config.id)) {
                continue;
            }

            DataTableRowBuilder row = table.row();

            row.cell(system.config.id).cell(system.config.name).cell(system.config.description);

            if (central) {
                row.cell(system.minion);
            }

            row.build();
        }
        return table;
    }

    private RenderableResult doShowInfo(SystemResource sr, SystemConfig config) {
        String[] uuid = config.uuid();
        if (uuid.length != 1) {
            return createResultWithErrorMessage("Exactly 1 uuid must be provided for this command.");
        }
        String systemUuid = uuid[0];

        for (var system : sr.list()) {
            var cfg = system.config;

            if (!cfg.id.equals(systemUuid)) {
                continue;
            }

            var result = createEmptyResult();
            result.setMessage("Info for System " + cfg.id + " - " + cfg.name);
            result.addField("Description", cfg.description);
            result.addField(" -- Config Variables --", "");

            for (var entry : cfg.systemVariables) {
                result.addField(" " + entry.id, entry.value != null ? entry.value.getPreRenderable() : "");
            }

            return result;
        }
        return createResultWithErrorMessage(
                "Instance group " + config.instanceGroup() + " does not contain a system with the id " + systemUuid);
    }

    private RenderableResult doShowStatus(InstanceGroupResource igr, SystemResource sr, SystemConfig config) {
        String[] uuid = config.uuid();
        if (uuid.length != 1) {
            return createResultWithErrorMessage("Exactly 1 uuid must be provided for this command.");
        }
        String systemUuid = uuid[0];

        Optional<SystemConfigurationDto> selectedSystemOpt = sr.list().stream()
                .filter(systemDto -> systemUuid.equals(systemDto.config.id)).findFirst();
        if (selectedSystemOpt.isEmpty()) {
            return createResultWithErrorMessage(
                    "Instance group " + config.instanceGroup() + " does not contain a system with the id " + systemUuid);
        }

        SystemConfigurationDto systemConfigDto = selectedSystemOpt.get();
        Manifest.Key systemKey = systemConfigDto.key;
        SystemConfiguration systemConfig = systemConfigDto.config;

        InstanceResource ir = igr.getInstanceResource(config.instanceGroup());

        List<InstanceDto> instances = ir.list().stream()//
                .filter(instanceDto -> systemKey.equals(instanceDto.instanceConfiguration.system))//
                .toList();

        SortedMap<InstanceDto, InstanceOverallStatusDto> instancesAndOverallStates = new TreeMap<>(
                (a, b) -> a.instance.compareTo(b.instance));

        boolean includeDetails = config.details();
        if (includeDetails || config.sync()) {
            List<InstanceOverallStatusDto> syncedStates = ir.getBulkResource()
                    .syncBulk(instances.stream().map(dto -> dto.instance).collect(Collectors.toSet()));
            for (InstanceDto dto : instances) {
                for (InstanceOverallStatusDto statusDto : syncedStates) {
                    if (dto.instanceConfiguration.id.equals(statusDto.id)) {
                        instancesAndOverallStates.put(dto, statusDto);
                        break;
                    }
                }
            }
        } else {
            for (InstanceDto dto : instances) {
                instancesAndOverallStates.put(dto, new InstanceOverallStatusDto(dto.instanceConfiguration.id, dto.overallState));
            }
        }

        DataTable resultTable = createDataTable();
        resultTable.column(new DataTableColumn.Builder("Instance Name").setMinWidth(13).build());
        resultTable.column(new DataTableColumn.Builder("Instance UUID").setMinWidth(13).build());
        resultTable.column(new DataTableColumn.Builder("Process Name").setMinWidth(13).build());
        resultTable.column(new DataTableColumn.Builder("Process UUID").setMinWidth(13).build());
        resultTable.column(new DataTableColumn.Builder("Node").setMinWidth(15).build());
        resultTable.column(new DataTableColumn.Builder("Status").setMinWidth(7).build());
        resultTable.column(new DataTableColumn.Builder("Last Sync").build());
        resultTable.column(new DataTableColumn.Builder("Messages").build());
        resultTable.setCaption("Status of System " + systemConfig.id + " - " + systemConfig.name);

        Set<Entry<InstanceDto, InstanceOverallStatusDto>> entrySet = instancesAndOverallStates.entrySet();
        int i = 1;
        int max = entrySet.size();

        for (Entry<InstanceDto, InstanceOverallStatusDto> entry : entrySet) {
            InstanceConfiguration instanceConfig = entry.getKey().instanceConfiguration;
            InstanceOverallStatusDto overallStatusDto = entry.getValue();

            resultTable.row()//
                    .cell(instanceConfig.name)//
                    .cell(instanceConfig.id)//
                    .cell(null)//
                    .cell(null)//
                    .cell(null)//
                    .cell(overallStatusDto.status)//
                    .cell(overallStatusDto.timestamp <= 0 ? "Never"
                            : FormatHelper.formatTemporal(Instant.ofEpochMilli(overallStatusDto.timestamp)))//
                    .cell(String.join(" | ", overallStatusDto.messages))//
                    .build();

            if (includeDetails) {
                MappedInstanceProcessStatusDto instanceStatus = ir.getProcessResource(instanceConfig.id).getMappedStatus();
                instanceStatus.processStates.forEach((appId,
                        node2processState) -> node2processState.forEach((serverNode, processStatusDto) -> resultTable.row()//
                                .cell(instanceConfig.name)//
                                .cell(instanceConfig.id)//
                                .cell(processStatusDto.appName)//
                                .cell(appId)//
                                .cell(getNodeNameDisplay(instanceStatus, appId, serverNode))//
                                .cell(processStatusDto.processState)//
                                .cell(null)//
                                .cell(null)//
                                .build()));
                if (i < max) {
                    resultTable.addHorizontalRuler();
                }
                i++;
            }
        }

        return resultTable;
    }

    private RenderableResult doStart(InstanceGroupResource igr, SystemResource sr, SystemConfig config) {
        return performBulkOperation(igr, sr, config, InstanceBulkResource::startBulk);
    }

    private RenderableResult doStop(InstanceGroupResource igr, SystemResource sr, SystemConfig config) {
        return performBulkOperation(igr, sr, config, InstanceBulkResource::stopBulk);
    }

    private RenderableResult doRestart(InstanceGroupResource igr, SystemResource sr, SystemConfig config) {
        return performBulkOperation(igr, sr, config, InstanceBulkResource::restartBulk);
    }

    private DataResult doCreate(RemoteService remote, SystemResource sr, SystemConfig config) {
        helpAndFailIfMissing(config.name(), "Missing --name");

        BackendInfoResource bir = ResourceProvider.getVersionedResource(remote, BackendInfoResource.class, getLocalContext());
        if (bir.getVersion().mode == MinionMode.CENTRAL) {
            helpAndFailIfMissing(config.server(), "Missing --server");
        }

        if (config.createFrom() != null) {
            try (InputStream is = Files.newInputStream(Paths.get(config.createFrom()))) {
                SystemTemplateDto tpl = sr.loadTemplate(FormDataHelper.createMultiPartForStream("stream", is), config.server());
                tpl = sr.importMissingProducts(tpl);

                // check whether variables are all pre-filled...
                List<String> stvs = tpl.template.templateVariables.stream().filter(v -> v.defaultValue == null).map(x -> x.id)
                        .toList();
                if (!stvs.isEmpty()) {
                    throw new IllegalArgumentException("Missing values for system template variables: " + stvs);
                }

                Map<String, String> tplValues = new TreeMap<>();
                for (var v : tpl.template.templateVariables) {
                    tplValues.put(v.id, v.defaultValue);
                }

                List<SystemTemplateGroupMapping> mapping = new ArrayList<>();
                for (var it : tpl.template.instances) {
                    var m = new SystemTemplateGroupMapping();

                    m.instanceName = it.name;
                    m.productKey = InstanceTemplateHelper.findMatchingProductOrFail(it, tpl.products).key;
                    m.groupToNode = new TreeMap<>();
                    m.templateVariableValues = new TreeMap<>();

                    // verify that each instance template has at least on template group mapping.
                    if (it.defaultMappings == null || it.defaultMappings.isEmpty()) {
                        throw new IllegalArgumentException("Instance " + it.name + " does not map to any nodes.");
                    }

                    it.defaultMappings.forEach(x -> m.groupToNode.put(x.group, x.node));

                    // all variables need to be fixed or defaulted.
                    if (it.fixedVariables != null && !it.fixedVariables.isEmpty()) {
                        it.fixedVariables.forEach(v -> m.templateVariableValues.put(v.id, v.value));
                    }

                    mapping.add(m);
                }

                // if *everything* is ok, apply.
                var rq = new SystemTemplateRequestDto();
                rq.name = config.name();
                rq.minion = config.server();
                rq.purpose = config.purpose();
                rq.groupMappings = mapping;
                rq.templateVariableValues = tplValues;
                rq.template = tpl.template;

                var result = sr.applyTemplate(rq);
                DataResult r = createEmptyResult();
                for (var ir : result.results) {
                    r.addField(ir.name, ir.status + ": " + ir.message);

                    if (ir.status == InstanceTemplateReferenceStatus.ERROR) {
                        r.setExitCode(ExitCode.ERROR);
                    }
                }

                return r;
            } catch (IOException e) {
                throw new IllegalStateException("Cannot process system template", e);
            }
        } else {
            SystemConfiguration cfg = new SystemConfiguration();

            cfg.id = UuidHelper.randomId();
            cfg.name = config.name();
            cfg.description = config.description();

            SystemConfigurationDto dto = new SystemConfigurationDto();
            dto.config = cfg;
            dto.minion = config.server();

            sr.update(dto);
            return createSuccess().addField("System ID", cfg.id);
        }
    }

    private DataResult doUpdate(SystemResource sr, SystemConfig config) {
        String[] uuid = config.uuid();
        if (uuid.length != 1) {
            return createResultWithErrorMessage("Exactly 1 uuid must be provided for this command.");
        }
        String systemUuid = uuid[0];

        if (config.name() == null && config.description() == null && config.setVariable() == null
                && config.removeVariable() == null) {
            helpAndFail("ERROR: Missing --name, --description, --setVariable or --removeVariable");
        }

        if (config.setVariable() != null && config.setValue() == null) {
            helpAndFail("ERROR: Got --setVariable but missing --setValue");
        }

        Optional<SystemConfigurationDto> sys = sr.list().stream().filter(s -> s.config.id.equals(systemUuid)).findAny();
        if (sys.isEmpty()) {
            throw new IllegalArgumentException("Cannot find system with ID " + config.uuid());
        }

        DataResult result = createSuccess();
        SystemConfiguration cfg = sys.get().config;

        if (config.name() != null && !config.name().isBlank()) {
            cfg.name = config.name();
            result.addField("New Name", config.name());
        }
        if (config.description() != null && !config.description().isBlank()) {
            cfg.description = config.description();
            result.addField("New Description", config.description());
        }
        if (config.removeVariable() != null) {
            var existing = cfg.systemVariables.stream().filter(v -> v.id.equals(config.setVariable())).findFirst().orElse(null);
            if (existing != null) {
                cfg.systemVariables.remove(existing);
            }
            result.addField("Remove Variable", config.removeVariable());
        }
        if (config.setVariable() != null) {
            var existing = cfg.systemVariables.stream().filter(v -> v.id.equals(config.setVariable())).findFirst().orElse(null);
            if (existing != null) {
                cfg.systemVariables.set(cfg.systemVariables.indexOf(existing),
                        new VariableConfiguration(config.setVariable(), config.setValue()));
            } else {
                cfg.systemVariables.add(new VariableConfiguration(config.setVariable(), config.setValue()));
            }
            Collections.sort(cfg.systemVariables, (v1, v2) -> v1.id.compareTo(v2.id));
            result.addField("Set Variable", config.setVariable());
        }

        sr.update(sys.get());

        return result;
    }

    private RenderableResult doDelete(SystemResource sr, SystemConfig config) {
        DataTable result = createDataTable();
        result.setCaption("Success");
        result.column(new DataTableColumn.Builder("System").build());
        result.column(new DataTableColumn.Builder("Result").build());
        for (String uuid : new HashSet<>(Arrays.asList(config.uuid()))) {
            DataTableRowBuilder rowBuilder = result.row().cell(uuid);
            try {
                sr.delete(uuid);
                rowBuilder.cell("Deleted");
            } catch (NotFoundException e) {
                rowBuilder.cell("Not deleted - system does not exist");
            } catch (Exception e) {
                rowBuilder.cell("Not deleted - " + e.getMessage());
            }
            rowBuilder.build();
        }
        return result;
    }

    private RenderableResult performBulkOperation(InstanceGroupResource igr, SystemResource sr, SystemConfig config,
            BiFunction<InstanceBulkResource, List<String>, BulkOperationResultDto> bulkOperation) {
        DataTable resultTable = createDataTable();
        resultTable.column(new DataTableColumn.Builder("System").setMinWidth(10).build());
        resultTable.column(new DataTableColumn.Builder("Instance").setMinWidth(10).build());
        resultTable.column(new DataTableColumn.Builder("Type").setMinWidth(10).build());
        resultTable.column(new DataTableColumn.Builder("Message").setMinWidth(5).build());

        Map<String, SystemConfigurationDto> systems = sr.list().stream()
                .collect(Collectors.toMap(dto -> dto.config.id, Function.identity()));

        InstanceResource ir = igr.getInstanceResource(config.instanceGroup());
        InstanceBulkResource ibr = ir.getBulkResource();

        for (String uuid : new HashSet<>(Arrays.asList(config.uuid()))) {
            if (!systems.containsKey(uuid)) {
                resultTable.row().cell(uuid).cell(null).cell("ERROR")
                        .cell("UUID " + uuid + " does not belong to any known system.").build();
                continue;
            }

            Manifest.Key systemKey = systems.get(uuid).key;
            List<String> instanceIds = ir.list().stream()//
                    .filter(instanceDto -> systemKey.equals(instanceDto.instanceConfiguration.system))//
                    .map(dto -> dto.instanceConfiguration.id)//
                    .toList();

            List<OperationResult> bulkOperationResults = bulkOperation.apply(ibr, instanceIds).results;

            if (bulkOperationResults.isEmpty()) {
                resultTable.row().cell(uuid).cell(null).cell("INFO").cell("Success").build();
            } else {
                for (OperationResult operationResult : bulkOperationResults) {
                    resultTable.row().cell(uuid).cell(operationResult.target()).cell(operationResult.type())
                            .cell(operationResult.message()).build();
                }
            }
        }

        return resultTable;
    }

    private static String getNodeNameDisplay(MappedInstanceProcessStatusDto instanceStatus, String appId, String serverNode) {
        String configuredNode = instanceStatus.processToNode.get(appId);

        if (configuredNode.equals(serverNode)) {
            return configuredNode;
        } else {
            return configuredNode + (serverNode != null ? "/" + serverNode : "");
        }
    }
}
