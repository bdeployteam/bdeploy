package io.bdeploy.ui.cli;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;

import org.glassfish.jersey.media.multipart.FormDataMultiPart;

import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.util.StorageHelper;
import io.bdeploy.common.cfg.Configuration.ConfigurationValueMapping;
import io.bdeploy.common.cfg.Configuration.EnvironmentFallback;
import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cfg.Configuration.Validator;
import io.bdeploy.common.cfg.Configuration.ValueMapping;
import io.bdeploy.common.cfg.ExistingPathValidator;
import io.bdeploy.common.cfg.NonExistingOrEmptyDirPathValidator;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.cli.ToolCategory;
import io.bdeploy.common.cli.data.DataResult;
import io.bdeploy.common.cli.data.DataTable;
import io.bdeploy.common.cli.data.DataTableColumn;
import io.bdeploy.common.cli.data.DataTableRowBuilder;
import io.bdeploy.common.cli.data.RenderableResult;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.StreamHelper;
import io.bdeploy.common.util.StringHelper;
import io.bdeploy.common.util.UuidHelper;
import io.bdeploy.interfaces.configuration.VariableConfiguration;
import io.bdeploy.interfaces.configuration.dcu.LinkedValueConfiguration;
import io.bdeploy.interfaces.configuration.instance.ApplicationValidationDto;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration.InstancePurpose;
import io.bdeploy.interfaces.configuration.instance.InstanceConfigurationDto;
import io.bdeploy.interfaces.configuration.instance.InstanceUpdateDto;
import io.bdeploy.interfaces.descriptor.template.InstanceTemplateReferenceDescriptor;
import io.bdeploy.interfaces.descriptor.variable.VariableDescriptor;
import io.bdeploy.interfaces.manifest.managed.ManagedMasterDto;
import io.bdeploy.interfaces.manifest.state.InstanceStateRecord;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.jersey.cli.RemoteServiceTool;
import io.bdeploy.ui.FormDataHelper;
import io.bdeploy.ui.api.BackendInfoResource;
import io.bdeploy.ui.api.InstanceGroupResource;
import io.bdeploy.ui.api.InstanceResource;
import io.bdeploy.ui.api.ManagedServersResource;
import io.bdeploy.ui.api.MinionMode;
import io.bdeploy.ui.api.SystemResource;
import io.bdeploy.ui.cli.RemoteInstanceTool.InstanceConfig;
import io.bdeploy.ui.dto.InstanceDto;
import io.bdeploy.ui.dto.InstanceTemplateReferenceResultDto;
import io.bdeploy.ui.dto.InstanceTemplateReferenceResultDto.InstanceTemplateReferenceStatus;
import io.bdeploy.ui.dto.InstanceVersionDto;
import io.bdeploy.ui.dto.SystemConfigurationDto;
import io.bdeploy.ui.utils.BrowserHelper;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status.Family;

@Help("List, create, import and export instance configurations")
@ToolCategory(TextUIResources.UI_CATEGORY)
@CliName("remote-instance")
public class RemoteInstanceTool extends RemoteServiceTool<InstanceConfig> {

    public @interface InstanceConfig {

        @Help("Name of the instance group for import into or export from")
        @EnvironmentFallback("REMOTE_BHIVE")
        String instanceGroup();

        @Help("Path to a ZIP file containing an export produced with this tool. Requires exactly 1 uuid to be set.")
        @Validator(ExistingPathValidator.class)
        String importFrom();

        @Help("Path to a non-existing ZIP file where to export a given instance configuration. Requires exactly 1 uuid to be set.")
        @Validator(NonExistingOrEmptyDirPathValidator.class)
        String exportTo();

        @Help(value = "IDs of the instances to manipulate")
        String[] uuid();

        @Help(value = "Version of the existing instance. When exporting must exist.")
        String version();

        @Help("Product version to update to")
        String updateTo();

        @Help(value = "List instance versions on the remote. By default will list active versions. If no active version is found for instance, will display its latest installed version. If no installed version is found for instance, will display its latest version. If UUIDs are given, will only display the corresponding instances. Unknown UUIDs will be ignored.",
              arg = false)
        boolean list() default false;

        @Help(value = "List only active instance versions", arg = false)
        boolean active() default false;

        @Help(value = "List only installed instance versions", arg = false)
        boolean installed() default false;

        @Help(value = "List all instance versions", arg = false)
        boolean all() default false;

        @Help("List only a certain amount of versions per instance. Specify zero for no limit.")
        int limit() default 5;

        @Help(value = "Create an instance", arg = false)
        boolean create() default false;

        @Help("When creating the instance, use the provided template YAML")
        @Validator(ExistingPathValidator.class)
        String template();

        @Help(value = "Update the given instances", arg = false)
        boolean update() default false;

        @Help("The name to set for the created/updated instance")
        String name();

        @Help("The description to set for the created/updated instance if using --update or variable if using --setVariable")
        String description();

        @Help("The purpose to set for the created/updated instance")
        @ConfigurationValueMapping(ValueMapping.TO_UPPERCASE)
        InstancePurpose purpose();

        @Help("The system ID to set for the created/updated instance")
        String system();

        @Help(value = "Enable 'Automatic Startup' for the instance", arg = false) //
        boolean enableAutoStart() default false;

        @Help(value = "Disable 'Automatic Startup' for the instance", arg = false) //
        boolean disableAutoStart() default false;

        @Help(value = "Enable 'Automatic Uninstallation' for the instance", arg = false) //
        boolean enableAutoUninstall() default false;

        @Help(value = "Disable 'Automatic Uninstallation' for the instance", arg = false) //
        boolean disableAutoUninstall() default false;

        @Help("The new value for the product version regular expression. Only product versions matching this expression will be presented when updating the product version.")
        String productVersionRegex();

        @Help("The name of the managed server, only used on CENTRAL. When creating instances, this is the target server. When listing instances, it serves as a filter.")
        String server();

        @Help("The name of the product to set for the created instance")
        String product();

        @Help("The version of the product to set for the created instance")
        String productVersion();

        @Help(value = "Delete the given instances. This CANNOT BE UNDONE.", arg = false)
        boolean delete() default false;

        @Help(value = "Use this flag to avoid confirmation prompt when deleting instance.", arg = false)
        boolean yes() default false;

        @Help(value = "Use this flag to open the dashboard of the instance group in the web UI. If an instance uuid is given, it instead opens the dashboard of the specified instance.",
              arg = false)
        boolean open() default false;

        @Help(value = "Show variables for the specified instance", arg = false)
        boolean showVariables() default false;

        @Help("Set the given variable. If there is no variable descriptor with the given ID, a custom variable will be added.")
        String setVariable();

        @Help("The value to set the variable to")
        String value();

        @Help("The type of the variable you are setting. Will default to STRING if not provided.")
        VariableDescriptor.VariableType type() default VariableDescriptor.VariableType.STRING;

        @Help("A potential custom editor ID which must be provided by a plugin to edit a variable with")
        String customEditor();

        @Help("Remove the specified variable")
        String removeVariable();
    }

    public RemoteInstanceTool() {
        super(InstanceConfig.class);
    }

    @Override
    protected RenderableResult run(InstanceConfig config, RemoteService remote) {
        helpAndFailIfMissing(config.instanceGroup(), "--instanceGroup missing");

        if (config.list()) {
            return doList(remote, config);
        }
        if (config.open()) {
            String uuid = parseUuid(config.uuid());
            if (uuid == null) {
                return BrowserHelper.openUrl(remote, "/#/instances/browser/" + config.instanceGroup())//
                        ? createResultWithSuccessMessage("Successfully opened the instance group dashboard")//
                        : createResultWithErrorMessage("Failed to open the instance group dashboard");
            } else {
                return BrowserHelper.openUrl(remote, "/#/instances/dashboard/" + config.instanceGroup() + '/' + uuid)//
                        ? createResultWithSuccessMessage("Successfully opened the instance dashboard")//
                        : createResultWithErrorMessage("Failed to open the instance dashboard");
            }
        }

        InstanceResource ir = ResourceProvider.getVersionedResource(remote, InstanceGroupResource.class, getLocalContext())
                .getInstanceResource(config.instanceGroup());

        if (config.create()) {
            return config.template() != null ? doCreateFromTemplate(remote, ir, config) : doCreate(remote, ir, config);
        }

        helpAndFailIfMissing(config.uuid(), "--uuid missing");

        if (config.update()) {
            return config.template() != null ? doUpdateWithTemplate(remote, ir, config) : doUpdate(remote, ir, config);
        } else if (config.exportTo() != null) {
            return doExport(ir, config);
        } else if (config.importFrom() != null) {
            return doImport(ir, config);
        } else if (config.updateTo() != null) {
            return doUpdateProduct(remote, ir, config);
        } else if (config.delete()) {
            return doDelete(config, ir);
        } else if (config.showVariables()) {
            return doShowVariables(ir, config);
        } else if (config.setVariable() != null) {
            helpAndFailIfMissing(config.value(), "--value is missing");
            return doSetVariable(remote, ir, config);
        } else if (config.removeVariable() != null) {
            return doRemoveVariable(remote, ir, config);
        } else {
            return createNoOp();
        }
    }

    private static String parseUuid(String[] uuidArray) {
        if (uuidArray == null) {
            return null;
        }
        if (uuidArray.length > 1) {
            throw new IllegalArgumentException("At most 1 uuid may be provided for --open.");
        }
        return uuidArray[0];
    }

    private DataResult doUpdate(RemoteService remote, InstanceResource ir, InstanceConfig config) {
        if (config.productVersion() != null) {
            throw new IllegalArgumentException("Please use --updateTo instead of --update to change the product version");
        }

        String name = config.name();
        String description = config.description();
        InstancePurpose purpose = config.purpose();
        String system = config.system();
        Boolean autoStartup = config.disableAutoStart() ? Boolean.FALSE : config.enableAutoStart() ? Boolean.TRUE : null;
        Boolean autoUninstall = config.disableAutoUninstall() ? Boolean.FALSE
                : config.enableAutoUninstall() ? Boolean.TRUE : null;
        String productRegEx = config.productVersionRegex();

        boolean setName = !StringHelper.isNullOrBlank(name);
        boolean setDescription = !StringHelper.isNullOrBlank(description);
        boolean setPurpose = purpose != null;
        boolean setSystem = !StringHelper.isNullOrBlank(system);
        boolean setAutoStartup = autoStartup != null;
        boolean setAutoUninstall = autoUninstall != null;
        boolean setProductRegEx = !StringHelper.isNullOrBlank(productRegEx);

        if (!setName && !setDescription && !setPurpose && !setSystem && !setAutoStartup && !setAutoUninstall
                && !setProductRegEx) {
            helpAndFail(
                    "ERROR: Missing --name, --description, --purpose, --system, any of the --enable/--disable flags or --productVersionRegex");
        }

        Manifest.Key sysKey = null;

        DataResult result = createSuccess();
        if (setName) {
            result.addField("New Name", name);
        }
        if (setDescription) {
            result.addField("New Description", description);
        }
        if (setPurpose) {
            result.addField("New Purpose", purpose);
        }
        if (setSystem) {
            sysKey = ResourceProvider.getVersionedResource(remote, InstanceGroupResource.class, getLocalContext())
                    .getSystemResource(config.instanceGroup()).list().stream().filter(s -> s.config.id.equals(system)).findAny()
                    .orElseThrow(() -> new IllegalArgumentException("Cannot find specified system on server: " + system)).key;
            result.addField("New System", system);
        }
        if (setAutoStartup) {
            result.addField("New value for automatic startup", autoStartup);
        }
        if (setAutoUninstall) {
            result.addField("New value for automatic uninstallation", autoUninstall);
        }
        if (setProductRegEx) {
            result.addField("New product version regular expression", productRegEx);
        }

        BackendInfoResource bir = ResourceProvider.getResource(remote, BackendInfoResource.class, getLocalContext());
        boolean notCentral = bir.getVersion().mode != MinionMode.CENTRAL;

        for (String uuid : new HashSet<>(Arrays.asList(config.uuid()))) {
            String currentTag = identifyCurrentVersion(ir, uuid);

            InstanceConfiguration cfg = ir.readVersion(uuid, currentTag);
            if (setName) {
                cfg.name = name;
            }
            if (setDescription) {
                cfg.description = description;
            }
            if (setPurpose) {
                cfg.purpose = purpose;
            }
            if (setSystem) {
                cfg.system = sysKey;
            }
            if (setAutoStartup) {
                cfg.autoStart = autoStartup;
            }
            if (setAutoUninstall) {
                cfg.autoUninstall = autoUninstall;
            }
            if (setProductRegEx) {
                cfg.productFilterRegex = productRegEx;
            }

            ManagedMasterDto server = notCentral ? null
                    : ResourceProvider.getResource(remote, ManagedServersResource.class, getLocalContext())
                            .getServerForInstance(config.instanceGroup(), uuid, currentTag);
            InstanceConfigurationDto dto = new InstanceConfigurationDto(cfg,
                    ir.getNodeConfigurations(uuid, currentTag).nodeConfigDtos);
            ir.update(uuid, new InstanceUpdateDto(dto, null), server != null ? server.hostName : null, currentTag);
        }

        return result;
    }

    private DataResult doUpdateWithTemplate(RemoteService remote, InstanceResource ir, InstanceConfig config) {
        String[] uuid = config.uuid();
        if (uuid.length != 1) {
            return createResultWithErrorMessage("Exactly 1 uuid must be provided for this command");
        }

        BackendInfoResource bir = ResourceProvider.getVersionedResource(remote, BackendInfoResource.class, getLocalContext());
        if (bir.getVersion().mode == MinionMode.CENTRAL) {
            helpAndFailIfMissing(config.server(), "Missing --server");
        }

        Path template = Paths.get(config.template());
        try (InputStream is = Files.newInputStream(template)) {
            InstanceTemplateReferenceDescriptor desc = StorageHelper.fromYamlStream(is,
                    InstanceTemplateReferenceDescriptor.class);

            // verify that instance template has at least one template group mapping
            if (desc.defaultMappings == null || desc.defaultMappings.isEmpty()) {
                throw new IllegalArgumentException("Instance " + desc.name + " does not map to any nodes.");
            }

            InstanceTemplateReferenceResultDto result = ir.getTemplateResource().updateWithTemplate(desc, config.server(),
                    config.uuid()[0]);

            if (result.status != InstanceTemplateReferenceStatus.ERROR) {
                return createSuccess().setMessage(result.status + ": " + result.message);
            } else {
                return createResultWithErrorMessage(result.status + ": " + result.message);
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot process " + config.template(), e);
        }
    }

    private DataResult doCreate(RemoteService remote, InstanceResource ir, InstanceConfig config) {
        helpAndFailIfMissing(config.name(), "Missing --name");
        helpAndFailIfMissing(config.purpose(), "Missing --purpose");
        helpAndFailIfMissing(config.product(), "Missing --product");
        helpAndFailIfMissing(config.productVersion(), "Missing --productVersion");

        BackendInfoResource bir = ResourceProvider.getVersionedResource(remote, BackendInfoResource.class, getLocalContext());
        if (bir.getVersion().mode == MinionMode.CENTRAL) {
            helpAndFailIfMissing(config.server(), "Missing --server");
        }

        Manifest.Key system = null;
        if (config.system() != null) {
            SystemResource sr = ResourceProvider.getVersionedResource(remote, InstanceGroupResource.class, getLocalContext())
                    .getSystemResource(config.instanceGroup());
            Optional<SystemConfigurationDto> sys = sr.list().stream().filter(s -> s.config.id.equals(config.system())).findAny();
            if (sys.isEmpty()) {
                throw new IllegalArgumentException("Cannot find specified system on server: " + config.system());
            }

            system = sys.get().key;
        }

        InstanceConfiguration cfg = new InstanceConfiguration();
        cfg.id = UuidHelper.randomId();
        cfg.autoUninstall = !config.disableAutoUninstall(); // default = true (true unless disabled)
        cfg.autoStart = config.enableAutoStart(); // default = false (false unless enabled)
        cfg.description = config.description();
        cfg.name = config.name();
        cfg.product = new Manifest.Key(config.product(), config.productVersion());
        cfg.purpose = config.purpose();
        cfg.system = system;

        ir.create(cfg, config.server());

        return createSuccess().addField("Instance ID", cfg.id);
    }

    private RenderableResult doCreateFromTemplate(RemoteService remote, InstanceResource ir, InstanceConfig config) {
        helpAndFailIfMissing(config.purpose(), "Missing --purpose");

        BackendInfoResource bir = ResourceProvider.getVersionedResource(remote, BackendInfoResource.class, getLocalContext());
        if (bir.getVersion().mode == MinionMode.CENTRAL) {
            helpAndFailIfMissing(config.server(), "Missing --server");
        }

        Path template = Paths.get(config.template());
        try (InputStream is = Files.newInputStream(template)) {
            InstanceTemplateReferenceDescriptor desc = StorageHelper.fromYamlStream(is,
                    InstanceTemplateReferenceDescriptor.class);

            // verify that instance template has at least one template group mapping
            if (desc.defaultMappings == null || desc.defaultMappings.isEmpty()) {
                throw new IllegalArgumentException("Instance " + desc.name + " does not map to any nodes.");
            }

            if (config.name() != null && !config.name().isBlank()) {
                desc.name = config.name();
            }

            InstanceTemplateReferenceResultDto result = ir.getTemplateResource().createFromTemplate(desc, config.purpose(),
                    config.server(), config.system());

            if (result.status != InstanceTemplateReferenceStatus.ERROR) {
                return createSuccess().setMessage(result.status + ": " + result.message);
            } else {
                return createResultWithErrorMessage(result.status + ": " + result.message);
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot process " + config.template(), e);
        }
    }

    private DataResult doImport(InstanceResource ir, InstanceConfig config) {
        String[] uuid = config.uuid();
        if (uuid.length != 1) {
            return createResultWithErrorMessage("Exactly 1 uuid must be provided for this command");
        }

        Path input = Paths.get(config.importFrom());
        if (!Files.isRegularFile(input)) {
            helpAndFail("--importFrom is not a regular file");
        }

        try (InputStream is = Files.newInputStream(input);
                FormDataMultiPart fdmp = FormDataHelper.createMultiPartForStream("file", is)) {
            List<Key> keys = ir.importInstance(fdmp, uuid[0]);
            return createSuccess().addField("Created", keys);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot upload instance", e);
        }
    }

    private DataResult doExport(InstanceResource ir, InstanceConfig config) {
        helpAndFailIfMissing(config.version(), "--version missing");

        Path target = Paths.get(config.exportTo());
        if (Files.isRegularFile(target)) {
            helpAndFail("Target file already exists: " + target);
        }

        String[] uuid = config.uuid();
        if (uuid.length != 1) {
            return createResultWithErrorMessage("Exactly 1 uuid must be provided for this command");
        }
        Response resp = ir.exportInstance(uuid[0], config.version());

        if (resp.getStatusInfo().getFamily() != Family.SUCCESSFUL) {
            throw new IllegalStateException(
                    "Export failed: " + resp.getStatusInfo().getStatusCode() + ": " + resp.getStatusInfo().getReasonPhrase());
        }

        try (InputStream zip = resp.readEntity(InputStream.class)) {
            try (OutputStream os = Files.newOutputStream(target)) {
                StreamHelper.copy(zip, os);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot download instance", e);
        }
        return createSuccess().addField("Export File", config.exportTo());
    }

    private RenderableResult doUpdateProduct(RemoteService remote, InstanceResource ir, InstanceConfig config) {
            return updateMultipleInstancesWithValidation(remote, ir, config,
                    (uuid, instanceUpdateDto) -> {
                        String target = config.updateTo();
                        // perform actual product update
                        return Optional.of(ir.updateProductVersion(uuid, target, instanceUpdateDto));
                    });
    }

    private DataTable doList(RemoteService remote, InstanceConfig config) {
        int flagCount = (config.all() ? 1 : 0) + (config.installed() ? 1 : 0) + (config.active() ? 1 : 0);
        if (flagCount > 1) {
            helpAndFail("You can enable only one flag at a time: --all, --installed or --active");
        }
        BackendInfoResource bir = ResourceProvider.getVersionedResource(remote, BackendInfoResource.class, getLocalContext());
        boolean central = false;
        if (bir.getVersion().mode == MinionMode.CENTRAL) {
            central = true;
        }

        ManagedServersResource msr = ResourceProvider.getResource(remote, ManagedServersResource.class, getLocalContext());

        String[] uuid = config.uuid();
        boolean uuidSet = uuid != null && uuid.length > 0;
        String server = config.server();
        boolean serverSet = !StringHelper.isNullOrBlank(server);

        String caption = config.all() ? "All" : config.installed() ? "Installed" : config.active() ? "Active" : "Overview of";
        caption += " instances of " + config.instanceGroup() + " on " + remote.getUri();
        if (serverSet) {
            caption += " for managed server " + server;
        }

        DataTable table = createDataTable();
        table.setCaption(caption);

        table.column(new DataTableColumn.Builder("ID").setMinWidth(13).build());
        table.column(new DataTableColumn.Builder("Name").build());
        table.column(new DataTableColumn.Builder("Ver.").setName("Version").setMinWidth(4).build());
        table.column(new DataTableColumn.Builder("Installed").build());
        table.column(new DataTableColumn.Builder("Active").build());
        table.column(new DataTableColumn.Builder("Auto Start").build());
        table.column(new DataTableColumn.Builder("Auto Uninstall").build());
        table.column(new DataTableColumn.Builder("Purpose").build());
        table.column(new DataTableColumn.Builder("Product").setMinWidth(25).build());
        table.column(new DataTableColumn.Builder("Product Version").setMinWidth(14).build());
        table.column(new DataTableColumn.Builder("Product Ver. Regex").setMinWidth(14).build());
        table.column(new DataTableColumn.Builder("System").build());
        table.column(new DataTableColumn.Builder("Description").setMinWidth(0).build());

        if (central) {
            table.column(new DataTableColumn.Builder("Target Server").setMinWidth(20).build());
        }

        InstanceResource ir = ResourceProvider.getResource(remote, InstanceGroupResource.class, getLocalContext())
                .getInstanceResource(config.instanceGroup());

        Set<String> uuids = uuidSet ? new HashSet<>(Arrays.asList(uuid)) : null;
        for (var instance : ir.list()) {
            if (central && serverSet && !server.equals(instance.managedServer.hostName)) {
                continue;
            }

            if (uuidSet && !uuids.contains(instance.instanceConfiguration.id)) {
                continue;
            }

            var versions = ir.listVersions(instance.instanceConfiguration.id);
            var state = ir.getDeploymentStates(instance.instanceConfiguration.id);
            var limited = sortFilterLimit(versions, state, instance, config, ir);
            for (var version : limited) {
                boolean isActive = version.key.getTag().equals(state.activeTag); // activeTag may be null.
                boolean isInstalled = state.installedTags.contains(version.key.getTag());

                InstanceConfiguration vCfg = ir.readVersion(instance.instanceConfiguration.id, version.key.getTag());

                DataTableRowBuilder row = table.row();

                row.cell(instance.instanceConfiguration.id)
                        .cell(vCfg.name)
                        .cell(version.key.getTag())
                        .cell(isInstalled ? "*" : "")
                        .cell(isActive ? "*" : "")
                        .cell(vCfg.autoStart ? "*" : "")
                        .cell(vCfg.autoUninstall ? "*" : "")
                        .cell(vCfg.purpose.name())
                        .cell(version.product.getName())
                        .cell(version.product.getTag())
                        .cell(vCfg.productFilterRegex)
                        .cell(instance.instanceConfiguration.system != null ? instance.instanceConfiguration.system : "None")
                        .cell(vCfg.description);

                if (central) {
                    row.cell(msr.getServerForInstance(config.instanceGroup(), instance.instanceConfiguration.id,
                            version.key.getTag()).hostName);
                }

                row.build();
            }
        }
        return table;
    }

    private static List<InstanceVersionDto> sortFilterLimit(List<InstanceVersionDto> versions, InstanceStateRecord state,
            InstanceDto instance, InstanceConfig config, InstanceResource ir) {
        // sort versions in descending order (latest first)
        versions.sort((a, b) -> Long.compare(Long.parseLong(b.key.getTag()), Long.parseLong(a.key.getTag())));

        // filter instance versions by --all, --installed, --active flags
        List<InstanceVersionDto> prefiltered = filterByAllInstalledOrActive(versions, state, config);

        // filter instance versions by --version and --purpose
        List<InstanceVersionDto> filtered = filterByVersionAndPurpose(prefiltered, instance, config, ir);

        // return limited list
        if (config.limit() > 0 && config.limit() < filtered.size()) {
            return filtered.subList(0, config.limit());
        } else {
            return filtered;
        }
    }

    private static List<InstanceVersionDto> filterByAllInstalledOrActive(List<InstanceVersionDto> versions,
            InstanceStateRecord state, InstanceConfig config) {
        List<InstanceVersionDto> filtered = new ArrayList<>();
        boolean isDefaultSearch = !config.all() && !config.installed() && !config.active();
        InstanceVersionDto latestInstalled = null;
        for (var version : versions) {
            boolean searchForActive = isDefaultSearch || config.active();
            boolean searchForInstalled = config.installed();
            boolean isActive = version.key.getTag().equals(state.activeTag); // activeTag may be null.
            boolean isInstalled = state.installedTags.contains(version.key.getTag());

            if (isInstalled && latestInstalled == null) {
                latestInstalled = version;
            }

            if (searchForActive && !isActive) {
                continue;
            }

            if (searchForInstalled && !isInstalled) {
                continue;
            }

            filtered.add(version);
        }

        // By default if no active version is found, we add latest installed version (or just latest version if nothing is installed)
        if (filtered.isEmpty() && isDefaultSearch) {
            if (latestInstalled != null) {
                filtered.add(latestInstalled);
            } else if (!versions.isEmpty()) {
                filtered.add(versions.get(0));
            }
        }
        return filtered;
    }

    private static List<InstanceVersionDto> filterByVersionAndPurpose(List<InstanceVersionDto> versions, InstanceDto instance,
            InstanceConfig config, InstanceResource ir) {
        List<InstanceVersionDto> filtered = new ArrayList<>();

        for (var version : versions) {
            if (config.version() != null && !config.version().isBlank() && !version.key.getTag().equals(config.version())) {
                continue;
            }

            InstanceConfiguration vCfg = ir.readVersion(instance.instanceConfiguration.id, version.key.getTag());

            if (config.purpose() != null && config.purpose() != vCfg.purpose) {
                continue;
            }

            filtered.add(version);
        }

        return filtered;

    }

    private RenderableResult doDelete(InstanceConfig config, InstanceResource ir) {
        DataTable result = createDataTable();
        result.setCaption("Success");
        result.column(new DataTableColumn.Builder("Instance").build());
        result.column(new DataTableColumn.Builder("Result").build());
        boolean skipConfirmation = config.yes();
        for (String uuid : new HashSet<>(Arrays.asList(config.uuid()))) {
            DataTableRowBuilder rowBuilder = result.row().cell(uuid);
            try {
                if (skipConfirmation || confirmDelete(uuid, ir)) {
                    ir.delete(uuid);
                    rowBuilder.cell("Deleted");
                } else {
                    rowBuilder.cell("Not deleted - no confirmation");
                }
            } catch (NotFoundException e) {
                rowBuilder.cell("Not deleted - instance does not exist");
            } catch (Exception e) {
                rowBuilder.cell("Not deleted - " + e.getMessage());
            }
            rowBuilder.build();
        }
        return result;
    }

    private static boolean confirmDelete(String uuid, InstanceResource ir) {
        String instanceName = ir.read(uuid).instanceConfiguration.name;
        String confirmation = System.console().readLine("Delete instance %1$s (%2$s)? This CANNOT be undone. (Y/N)? ", uuid,
                instanceName);
        return "Y".equalsIgnoreCase(confirmation);
    }

    private RenderableResult doShowVariables(InstanceResource ir, InstanceConfig config) {
        String[] uuids = config.uuid();
        if (uuids.length != 1) {
            return createResultWithErrorMessage("Exactly 1 uuid must be provided for listing variables");
        }

        String uuid = uuids[0];
        String currentTag = identifyCurrentVersion(ir, uuid);
        InstanceConfiguration cfg = ir.readVersion(uuid, currentTag);

        DataTable table = createDataTable();
        table.setCaption("Variables for " + cfg.name + " (" + uuid + ")");
        table.column(new DataTableColumn.Builder("Id").setMinWidth(10).build());
        table.column(new DataTableColumn.Builder("Description").setMinWidth(20).build());
        table.column(new DataTableColumn.Builder("Type").setMinWidth(11).build());
        table.column(new DataTableColumn.Builder("Custom Editor").setMinWidth(10).build());
        table.column(new DataTableColumn.Builder("Value").setMinWidth(15).build());

        cfg.instanceVariables.stream()
                .forEach(variableConfiguration ->
                        table.row()
                                .cell(variableConfiguration.id)
                                .cell(variableConfiguration.description)
                                .cell(variableConfiguration.type)
                                .cell(variableConfiguration.customEditor)
                                .cell(variableConfiguration.value != null ? variableConfiguration.value.getPreRenderable() : null)
                                .build()
                );

        return table;
    }

    private RenderableResult doSetVariable(RemoteService remote, InstanceResource ir, InstanceConfig cmdParams) {
        String variableId = cmdParams.setVariable();

        return updateMultipleInstancesWithValidation(remote, ir, cmdParams, (uuid, instanceUpdateDto) -> {
            InstanceConfiguration instanceConfiguration = instanceUpdateDto.config.config;
                VariableConfiguration variableToSet = instanceConfiguration.instanceVariables.stream()
                    .filter(variableConfiguration -> variableConfiguration.id.equals(variableId))
                    .findFirst()
                    .orElseGet(() -> {
                        VariableConfiguration variableConfiguration = new VariableConfiguration(variableId, null);
                        instanceConfiguration.instanceVariables.add(variableConfiguration);
                        return variableConfiguration;
                    });

            variableToSet.value = new LinkedValueConfiguration(cmdParams.value());
            variableToSet.description = cmdParams.description();
            variableToSet.type = cmdParams.type();
            variableToSet.customEditor = cmdParams.customEditor();
            return Optional.of(instanceUpdateDto);
        });
    }

    private RenderableResult doRemoveVariable(RemoteService remote, InstanceResource ir, InstanceConfig config) {
        return updateMultipleInstancesWithValidation(remote, ir, config,
                (uuid, instanceUpdateDto) -> {
                    var instanceVariables = instanceUpdateDto.config.config.instanceVariables;
                    Optional<VariableConfiguration> existingVariableConfiguration = instanceVariables.stream().
                            filter(variableConfiguration -> variableConfiguration.id.equals(config.removeVariable()))
                            .findFirst();
                    if(existingVariableConfiguration.isPresent()) {
                        instanceVariables.remove(existingVariableConfiguration.get());
                        return Optional.of(instanceUpdateDto);
                    }

                    return Optional.empty();
                });
    }

    private static String identifyCurrentVersion(InstanceResource ir, String uuid) {
        return Integer.toString(ir.listVersions(uuid).stream()
                .mapToInt(dto -> Integer.parseInt(dto.key.getTag()))
                .max()
                .orElseThrow(() -> new IllegalStateException("Cannot determine current version of instance " + uuid))
        );
    }

    /**
     * Will run the modification function for each instance specified in the uuid list from the cli parameters
     * and log validations or update errors, for each instance in particular.
     *
     * The @param modificationFunction contains the code that modifies an instance by editing the {@link InstanceUpdateDto} that it
     * receives. It should return an {@link Optional} containing the {@link InstanceUpdateDto} that will be sent when executing the
     * update. Or return an empty {@link Optional} if no update needs to be executed.
     *
     * @param remote {@link RemoteService}
     * @param ir the {@link InstanceResource} object with which to execute rest calls
     * @param cmdParams the {@link InstanceConfig} object that encapsulates the parameters received from the user
     * @param modificationFunction the function that modifies a specific {@link InstanceUpdateDto} to then be sent to the server for
     *  validation and update
     * @return the {@link RenderableResult} that should be displayed to the user
     */
    private RenderableResult updateMultipleInstancesWithValidation(RemoteService remote,
            InstanceResource ir,
            InstanceConfig cmdParams,
            BiFunction<String, InstanceUpdateDto, Optional<InstanceUpdateDto>> modificationFunction) {
        DataTable result = createDataTable();
        result.column(new DataTableColumn.Builder("Instance").setMinWidth(10).build());
        result.column(new DataTableColumn.Builder("Process").setMinWidth(10).build());
        result.column(new DataTableColumn.Builder("Type").setMinWidth(5).build());
        result.column(new DataTableColumn.Builder("Message").setMinWidth(5).build());

        BackendInfoResource bir = ResourceProvider.getResource(remote, BackendInfoResource.class, getLocalContext());
        boolean notCentral = bir.getVersion().mode != MinionMode.CENTRAL;

        for (String uuid : new HashSet<>(Arrays.asList(cmdParams.uuid()))) {
            String currentTag = identifyCurrentVersion(ir, uuid);

            ManagedMasterDto server = notCentral ? null
                    : ResourceProvider.getResource(remote, ManagedServersResource.class, getLocalContext())
                            .getServerForInstance(cmdParams.instanceGroup(), uuid, currentTag);

            InstanceConfiguration cfg = ir.readVersion(uuid, currentTag);
            InstanceConfigurationDto dto = new InstanceConfigurationDto(cfg,
                    ir.getNodeConfigurations(uuid, currentTag).nodeConfigDtos);

            // here is where we do custom modifications
            Optional<InstanceUpdateDto> modifiedUpdateDto = modificationFunction.apply(uuid, new InstanceUpdateDto(dto, null));
            if(modifiedUpdateDto.isEmpty()) {
                result.row().cell(uuid).cell("").cell("").cell("Nothing to modify").build();
                continue;
            }

            // perform validation of updateDto
            InstanceUpdateDto updateDto = modifiedUpdateDto.get();
            List<ApplicationValidationDto> issues = ir.validate(uuid, updateDto);

            if (issues.isEmpty()) {
                ir.update(uuid, updateDto, server != null ? server.hostName : null, currentTag);
                if (updateDto.validation != null && !updateDto.validation.isEmpty()) {
                    result.row().cell(uuid).cell("").cell("").cell("Updated with warnings").build();
                    addErrorsToResult(result, updateDto.validation, uuid, "WARN");
                } else {
                    result.row().cell(uuid).cell("").cell("").cell("Updated successfully").build();
                }
            } else {
                result.row().cell(uuid).cell("").cell("").cell("Not modified").build();
                addErrorsToResult(result, issues, uuid, "ERROR");
            }

        }

        return result;
    }

    private static void addErrorsToResult(DataTable result, List<ApplicationValidationDto> validationErrors,
            String uuid, String level) {
        validationErrors.forEach(val -> {
            String pid = val.paramId == null ? "" : (" - " + val.paramId);
            result.row().cell(uuid).cell(val.appId == null ? "" : (val.appId + pid)).cell(level)
                    .cell(val.message).build();
        });
    }
}
