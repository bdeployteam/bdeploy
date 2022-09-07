package io.bdeploy.ui.cli;

import java.util.Collections;
import java.util.Optional;

import io.bdeploy.common.cfg.Configuration.EnvironmentFallback;
import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.cli.ToolCategory;
import io.bdeploy.common.cli.data.DataResult;
import io.bdeploy.common.cli.data.DataTable;
import io.bdeploy.common.cli.data.DataTableRowBuilder;
import io.bdeploy.common.cli.data.RenderableResult;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.UuidHelper;
import io.bdeploy.interfaces.configuration.VariableConfiguration;
import io.bdeploy.interfaces.configuration.system.SystemConfiguration;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.jersey.cli.RemoteServiceTool;
import io.bdeploy.ui.api.BackendInfoResource;
import io.bdeploy.ui.api.InstanceGroupResource;
import io.bdeploy.ui.api.MinionMode;
import io.bdeploy.ui.api.SystemResource;
import io.bdeploy.ui.cli.RemoteSystemTool.SystemConfig;
import io.bdeploy.ui.dto.SystemConfigurationDto;

@Help("List, create and update system configurations")
@ToolCategory(TextUIResources.UI_CATEGORY)
@CliName("remote-system")
public class RemoteSystemTool extends RemoteServiceTool<SystemConfig> {

    public @interface SystemConfig {

        @Help("Name of the instance group")
        @EnvironmentFallback("REMOTE_BHIVE")
        String instanceGroup();

        @Help(value = "UUID of the system. Used when updating existing system configuration.")
        String uuid();

        @Help(value = "List systems on the remote", arg = false)
        boolean list() default false;

        @Help(value = "Show details about a system", arg = false)
        boolean details() default false;

        @Help(value = "Create a system with a new ID", arg = false)
        boolean create() default false;

        @Help(value = "Update the system with the given UUID", arg = false)
        boolean update() default false;

        @Help(value = "Delete the system with the given UUID", arg = false)
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
            return list(remote, sr, config);
        } else if (config.create()) {
            return doCreate(remote, sr, config);
        } else if (config.details()) {
            helpAndFailIfMissing(config.uuid(), "--uuid missing");
            return doShowDetails(remote, sr, config);
        } else if (config.update()) {
            helpAndFailIfMissing(config.uuid(), "--uuid missing");
            return doUpdate(remote, sr, config);
        } else if (config.delete()) {
            helpAndFailIfMissing(config.uuid(), "--uuid missing");
            return doDelete(remote, sr, config);
        }

        return createNoOp();
    }

    private RenderableResult doDelete(RemoteService remote, SystemResource sr, SystemConfig config) {
        sr.delete(config.uuid());
        return createSuccess();
    }

    private RenderableResult doShowDetails(RemoteService remote, SystemResource sr, SystemConfig config) {
        var result = createEmptyResult();
        for (var system : sr.list()) {
            var cfg = system.config;

            if (!cfg.uuid.equals(config.uuid())) {
                continue;
            }

            result.setMessage("Details for System " + cfg.uuid + " - " + cfg.name);
            result.addField("Description", cfg.description);
            result.addField(" -- Config Variables --", "");

            for (var entry : cfg.systemVariables) {
                result.addField(" " + entry.id, entry.value != null ? entry.value.getPreRenderable() : "");
            }

            break;
        }
        return result;
    }

    private DataResult doUpdate(RemoteService remote, SystemResource sr, SystemConfig config) {
        if (config.name() == null && config.description() == null && config.setVariable() == null
                && config.removeVariable() == null) {
            helpAndFail("ERROR: Missing --name, --description, --setKey or --removeKey");
        }

        if (config.setVariable() != null && config.setValue() == null) {
            helpAndFail("ERROR: Got --setKey but missing --setVariable");
        }

        Optional<SystemConfigurationDto> sys = sr.list().stream().filter(s -> s.config.uuid.equals(config.uuid())).findAny();
        if (sys.isEmpty()) {
            throw new IllegalArgumentException("Cannot find system with UUID " + config.uuid());
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

    private DataResult doCreate(RemoteService remote, SystemResource sr, SystemConfig config) {
        helpAndFailIfMissing(config.name(), "Missing --name");

        BackendInfoResource bir = ResourceProvider.getVersionedResource(remote, BackendInfoResource.class, getLocalContext());
        if (bir.getVersion().mode == MinionMode.CENTRAL) {
            helpAndFailIfMissing(config.server(), "Missing --server");
        }

        SystemConfiguration cfg = new SystemConfiguration();

        cfg.uuid = UuidHelper.randomId();
        cfg.name = config.name();
        cfg.description = config.description();

        SystemConfigurationDto dto = new SystemConfigurationDto();
        dto.config = cfg;
        dto.minion = config.server();

        sr.update(dto);

        return createSuccess().addField("System UUID", cfg.uuid);
    }

    private DataTable list(RemoteService remote, SystemResource sr, SystemConfig config) {
        BackendInfoResource bir = ResourceProvider.getVersionedResource(remote, BackendInfoResource.class, getLocalContext());
        boolean central = false;
        if (bir.getVersion().mode == MinionMode.CENTRAL) {
            central = true;
        }

        DataTable table = createDataTable();
        table.setCaption("Systems of " + config.instanceGroup() + " on " + remote.getUri());

        table.column("UUID", 15).column("Name", 20).column("Description", 40);

        if (central) {
            table.column("Target Server", 20);
        }

        for (var system : sr.list()) {
            if (config.uuid() != null && !config.uuid().isBlank() && !system.config.uuid.equals(config.uuid())) {
                continue;
            }

            DataTableRowBuilder row = table.row();

            row.cell(system.config.uuid).cell(system.config.name).cell(system.config.description);

            if (central) {
                row.cell(system.minion);
            }

            row.build();
        }
        return table;
    }

}
