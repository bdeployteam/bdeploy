package io.bdeploy.ui.cli;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

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
import io.bdeploy.common.util.UuidHelper;
import io.bdeploy.interfaces.configuration.VariableConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration.InstancePurpose;
import io.bdeploy.interfaces.configuration.system.SystemConfiguration;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.jersey.cli.RemoteServiceTool;
import io.bdeploy.ui.FormDataHelper;
import io.bdeploy.ui.api.BackendInfoResource;
import io.bdeploy.ui.api.InstanceGroupResource;
import io.bdeploy.ui.api.MinionMode;
import io.bdeploy.ui.api.SystemResource;
import io.bdeploy.ui.cli.RemoteSystemTool.SystemConfig;
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

        @Help(value = "Show details about a system", arg = false)
        boolean details() default false;

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

        SystemResource sr = ResourceProvider.getVersionedResource(remote, InstanceGroupResource.class, getLocalContext())
                .getSystemResource(config.instanceGroup());

        if (config.list()) {
            return doShowList(remote, sr, config);
        } else if (config.details()) {
            helpAndFailIfMissing(config.uuid(), "--uuid missing");
            return doShowDetails(sr, config);
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

    private RenderableResult doShowDetails(SystemResource sr, SystemConfig config) {
        String uuid = config.uuid();
        for (var system : sr.list()) {
            var cfg = system.config;

            if (!cfg.id.equals(uuid)) {
                continue;
            }

            var result = createEmptyResult();
            result.setMessage("Details for System " + cfg.id + " - " + cfg.name);
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
}
