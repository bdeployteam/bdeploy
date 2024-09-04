package io.bdeploy.ui.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

import io.bdeploy.common.cfg.Configuration.EnvironmentFallback;
import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cfg.Configuration.Validator;
import io.bdeploy.common.cfg.ExistingPathValidator;
import io.bdeploy.common.cfg.NonExistingPathValidator;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.cli.ToolCategory;
import io.bdeploy.common.cli.data.DataResult;
import io.bdeploy.common.cli.data.DataTable;
import io.bdeploy.common.cli.data.RenderableResult;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.interfaces.configuration.instance.FileStatusDto;
import io.bdeploy.interfaces.configuration.instance.FileStatusDto.FileStatusType;
import io.bdeploy.interfaces.configuration.instance.InstanceConfigurationDto;
import io.bdeploy.interfaces.configuration.instance.InstanceUpdateDto;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.jersey.cli.RemoteServiceTool;
import io.bdeploy.ui.api.InstanceGroupResource;
import io.bdeploy.ui.api.InstanceResource;
import io.bdeploy.ui.cli.RemoteConfigFilesTool.ConfigFilesConfig;
import io.bdeploy.ui.dto.ConfigFileDto;
import io.bdeploy.ui.dto.InstanceDto;

@Help("List, update and delete configuration files")
@ToolCategory(TextUIResources.UI_CATEGORY)
@CliName("remote-config-files")
public class RemoteConfigFilesTool extends RemoteServiceTool<ConfigFilesConfig> {

    public @interface ConfigFilesConfig {

        @Help("Name of the instance group")
        @EnvironmentFallback("REMOTE_BHIVE")
        String instanceGroup();

        @Help("ID of the instance to manipulate")
        String uuid();

        @Help(value = "List current configuration files", arg = false)
        boolean list() default false;

        @Help(value = "Delete the given file")
        String delete();

        @Help(value = "The name of a file to add to the configuration files. Use --source to specify the source file.")
        String add();

        @Help(value = "The name of a file to replace. Use --source to specify the source file.")
        String update();

        @Help(value = "Path to a source files to add or update from.")
        @Validator(ExistingPathValidator.class)
        String source();

        @Help(value = "The name of the file to export locally.")
        String export();

        @Help(value = "The path to export a file to")
        @Validator(NonExistingPathValidator.class)
        String target();
    }

    public RemoteConfigFilesTool() {
        super(ConfigFilesConfig.class);
    }

    @Override
    protected RenderableResult run(ConfigFilesConfig config, RemoteService remote) {
        helpAndFailIfMissing(config.instanceGroup(), "--instanceGroup is missing");
        helpAndFailIfMissing(config.uuid(), "--uuid is missing");

        InstanceResource ir = ResourceProvider.getVersionedResource(remote, InstanceGroupResource.class, getLocalContext())
                .getInstanceResource(config.instanceGroup());

        InstanceDto instance = ir.list().stream().filter(i -> i.instanceConfiguration.id.equals(config.uuid())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Instance with ID " + config.uuid() + " does not exist."));

        if (config.list()) {
            return doList(config, ir, instance);
        } else if (config.add() != null) {
            helpAndFailIfMissing(config.source(), "--source is missing");
            return doAdd(config, ir, instance);
        } else if (config.update() != null) {
            helpAndFailIfMissing(config.source(), "--source is missing");
            return doUpdate(config, ir, instance);
        } else if (config.export() != null) {
            helpAndFailIfMissing(config.target(), "--target is missing");
            return doExport(config, ir, instance);
        } else if (config.delete() != null) {
            return doDelete(config, ir, instance);
        }
        return createNoOp();
    }

    private DataResult doAdd(ConfigFilesConfig config, InstanceResource ir, InstanceDto instance) {
        var upd = new FileStatusDto();
        upd.file = config.add();
        upd.type = FileStatusType.ADD;
        upd.content = toBase64(config.source());

        return doUpdateInstance(Collections.singletonList(upd), ir, instance);
    }

    private DataResult doUpdate(ConfigFilesConfig config, InstanceResource ir, InstanceDto instance) {
        var upd = new FileStatusDto();
        upd.file = config.update();
        upd.type = FileStatusType.EDIT;
        upd.content = toBase64(config.source());

        return doUpdateInstance(Collections.singletonList(upd), ir, instance);
    }

    private DataResult doDelete(ConfigFilesConfig config, InstanceResource ir, InstanceDto instance) {
        var upd = new FileStatusDto();
        upd.file = config.delete();
        upd.type = FileStatusType.DELETE;

        return doUpdateInstance(Collections.singletonList(upd), ir, instance);
    }

    private DataResult doUpdateInstance(List<FileStatusDto> updates, InstanceResource ir, InstanceDto instance) {
        InstanceUpdateDto update = new InstanceUpdateDto(new InstanceConfigurationDto(instance.instanceConfiguration, null),
                updates);

        ir.update(instance.instanceConfiguration.id, update,
                instance.managedServer != null ? instance.managedServer.hostName : null, instance.instance.getTag());

        return createSuccess();
    }

    private DataResult doExport(ConfigFilesConfig config, InstanceResource ir, InstanceDto instance) {
        String content = ir.getConfigResource(instance.instanceConfiguration.id).loadConfigFile(instance.instance.getTag(),
                config.export());
        byte[] raw = Base64.getDecoder().decode(content);

        try {
            Files.write(Paths.get(config.target()), raw, StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot write " + config.target(), e);
        }

        return createSuccess();
    }

    private DataTable doList(ConfigFilesConfig config, InstanceResource ir, InstanceDto instance) {
        DataTable table = createDataTable();
        table.setCaption("Configuration Files of " + config.uuid());

        table.column("Path", 80).column("Status", 15);

        List<ConfigFileDto> files = ir.getConfigResource(config.uuid()).listConfigFiles(instance.instance.getTag(),
                instance.instanceConfiguration.product.getName(), instance.instanceConfiguration.product.getTag());

        for (var file : files) {
            table.row().cell(file.path).cell(getStatusString(file)).build();
        }

        return table;
    }

    private static String getStatusString(ConfigFileDto file) {
        if (file.instanceId == null) {
            return "ONLY-PRODUCT";
        } else {
            if (file.productId == null) {
                return "ONLY-INSTANCE";
            } else if (file.productId.equals(file.instanceId)) {
                return "SYNC";
            } else {
                return "MODIFIED";
            }
        }
    }

    private static String toBase64(String filepath) {
        try {
            byte[] fileContent = Files.readAllBytes(Paths.get(filepath));
            return Base64.getEncoder().encodeToString(fileContent);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read file " + filepath, e);
        }
    }

}
