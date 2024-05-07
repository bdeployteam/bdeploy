package io.bdeploy.ui.cli;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.BiFunction;
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
import io.bdeploy.common.cli.data.DataTableRowBuilder;
import io.bdeploy.common.cli.data.ExitCode;
import io.bdeploy.common.cli.data.RenderableResult;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.FormatHelper;
import io.bdeploy.common.util.UuidHelper;
import io.bdeploy.interfaces.configuration.VariableConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration.InstancePurpose;
import io.bdeploy.interfaces.configuration.pcu.ProcessStatusDto;
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
import io.bdeploy.ui.dto.BulkOperationResultDto.OperationResult;
import io.bdeploy.ui.dto.InstanceDto;
import io.bdeploy.ui.dto.InstanceOverallStatusDto;
import io.bdeploy.ui.dto.InstanceTemplateReferenceResultDto.InstanceTemplateReferenceStatus;
import io.bdeploy.ui.dto.SystemConfigurationDto;
import io.bdeploy.ui.dto.SystemTemplateDto;
import io.bdeploy.ui.dto.SystemTemplateRequestDto;
import io.bdeploy.ui.dto.SystemTemplateRequestDto.SystemTemplateGroupMapping;
import io.bdeploy.ui.utils.InstanceTemplateHelper;

@Help("List, create and update system configurations")
@ToolCategory(TextUIResources.UI_CATEGORY)
@CliName("remote-system")
public class RemoteSystemTool extends RemoteServiceTool<SystemConfig> {

    public @interface SystemConfig {

        @Help("Name of the instance group")
        @EnvironmentFallback("REMOTE_BHIVE")
        String instanceGroup();

        @Help(value = "ID of the system. Used when updating existing system configuration.")
        String uuid();

        @Help(value = "List systems on the remote", arg = false)
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

        @Help("The name to set for the updated instance")
        String name();

        @Help("The description to set for the created/updated instance")
        String description();

        @Help("The name of the managed server if the system is created on a target CENTRAL server.")
        String server();

        @Help("The key of a configuration variable to set using --update")
        String setVariable();

        @Help("The value of a configuration variable to set using --update")
        String setValue();

        @Help("The key of a configuration variable to remove using --update")
        String removeVariable();

        @Help("Path to a system template specification. Note that not variable input is supported, the template must apply without user input.")
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
        } else if (config.info()) {
            helpAndFailIfMissing(config.uuid(), "--uuid missing");
            return doShowInfo(sr, config);
        } else if (config.status()) {
            helpAndFailIfMissing(config.uuid(), "--uuid missing");
            return doShowStatus(igr, sr, config);
        } else if (config.start()) {
            helpAndFailIfMissing(config.uuid(), "--uuid missing");
            return doStart(igr, sr, config);
        } else if (config.stop()) {
            helpAndFailIfMissing(config.uuid(), "--uuid missing");
            return doStop(igr, sr, config);
        } else if (config.restart()) {
            helpAndFailIfMissing(config.uuid(), "--uuid missing");
            return doRestart(igr, sr, config);
        } else if (config.create()) {
            return doCreate(remote, sr, config);
        } else if (config.update()) {
            helpAndFailIfMissing(config.uuid(), "--uuid missing");
            return doUpdate(sr, config);
        } else if (config.delete()) {
            helpAndFailIfMissing(config.uuid(), "--uuid missing");
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

        table.column("ID", 15).column("Name", 20).column("Description", 40);

        if (central) {
            table.column("Target Server", 20);
        }

        for (var system : sr.list()) {
            if (config.uuid() != null && !config.uuid().isBlank() && !system.config.id.equals(config.uuid())) {
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
        String uuid = config.uuid();
        for (var system : sr.list()) {
            var cfg = system.config;

            if (!cfg.id.equals(uuid)) {
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
                "Instance group " + config.instanceGroup() + " does not contain a system with the id " + uuid);
    }

    private RenderableResult doShowStatus(InstanceGroupResource igr, SystemResource sr, SystemConfig config) {
        String systemUuid = config.uuid();
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
                .collect(Collectors.toUnmodifiableList());

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
        resultTable.column("Instance Name", 30).column("Instance UUID", 15).column("Process Name", 30).column("Process UUID", 15)
                .column("Status", 20).column("Last Sync", 20).column("Messages", 100);
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
                    .cell(overallStatusDto.status)//
                    .cell(overallStatusDto.timestamp <= 0 ? "Never" : FormatHelper.format(overallStatusDto.timestamp))//
                    .cell(String.join(" | ", overallStatusDto.messages))//
                    .build();

            if (includeDetails) {
                Set<Entry<String, ProcessStatusDto>> processes = ir.getProcessResource(instanceConfig.id)
                        .getStatus().processStates.entrySet();
                if (!processes.isEmpty()) {
                    for (Entry<String, ProcessStatusDto> processEntry : processes) {
                        ProcessStatusDto processStatusDto = processEntry.getValue();
                        resultTable.row()//
                                .cell(instanceConfig.name)//
                                .cell(instanceConfig.id)//
                                .cell(processStatusDto.appName)//
                                .cell(processEntry.getKey())//
                                .cell(processStatusDto.processState)//
                                .cell(null)//
                                .cell(null)//
                                .build();

                    }
                }
                if (i < max) {
                    resultTable.addHorizontalRuler();
                }
                i++;
            }
        }

        return resultTable;
    }

    private RenderableResult doStart(InstanceGroupResource igr, SystemResource sr, SystemConfig config) {
        return performBulkOperation(igr, sr, config, (res, list) -> res.startBulk(list));
    }

    private RenderableResult doStop(InstanceGroupResource igr, SystemResource sr, SystemConfig config) {
        return performBulkOperation(igr, sr, config, (res, list) -> res.stopBulk(list));
    }

    private RenderableResult doRestart(InstanceGroupResource igr, SystemResource sr, SystemConfig config) {
        return performBulkOperation(igr, sr, config, (res, list) -> res.restartBulk(list));
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
        if (config.name() == null && config.description() == null && config.setVariable() == null
                && config.removeVariable() == null) {
            helpAndFail("ERROR: Missing --name, --description, --setKey or --removeKey");
        }

        if (config.setVariable() != null && config.setValue() == null) {
            helpAndFail("ERROR: Got --setKey but missing --setVariable");
        }

        Optional<SystemConfigurationDto> sys = sr.list().stream().filter(s -> s.config.id.equals(config.uuid())).findAny();
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
        sr.delete(config.uuid());
        return createSuccess();
    }

    private RenderableResult performBulkOperation(InstanceGroupResource igr, SystemResource sr, SystemConfig config,
            BiFunction<InstanceBulkResource, List<String>, BulkOperationResultDto> bulkOperation) {
        String systemUuid = config.uuid();
        Optional<SystemConfigurationDto> selectedSystemOpt = sr.list().stream()
                .filter(systemDto -> systemUuid.equals(systemDto.config.id)).findFirst();
        if (selectedSystemOpt.isEmpty()) {
            return createResultWithErrorMessage("Given UUID does not belong to any known system.");
        }

        SystemConfigurationDto systemConfigDto = selectedSystemOpt.get();
        Manifest.Key systemKey = systemConfigDto.key;

        InstanceResource ir = igr.getInstanceResource(config.instanceGroup());

        List<String> instanceIds = ir.list().stream()//
                .filter(instanceDto -> systemKey.equals(instanceDto.instanceConfiguration.system))//
                .map(dto -> dto.instanceConfiguration.id)//
                .collect(Collectors.toUnmodifiableList());

        List<OperationResult> bulkOperationResults = bulkOperation.apply(ir.getBulkResource(), instanceIds).results;
        if (bulkOperationResults.isEmpty()) {
            return createSuccess();
        }

        DataTable resultTable = createDataTable();
        resultTable.column("Target", 15).column("Type", 15).column("Message", 50);
        for (OperationResult operationResult : bulkOperation.apply(ir.getBulkResource(), instanceIds).results) {
            resultTable.row().cell(operationResult.target()).cell(operationResult.type()).cell(operationResult.message()).build();
        }
        return resultTable;
    }
}
