package io.bdeploy.ui.cli;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

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
import io.bdeploy.common.cfg.NonExistingPathValidator;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.cli.ToolCategory;
import io.bdeploy.common.cli.data.DataResult;
import io.bdeploy.common.cli.data.DataTable;
import io.bdeploy.common.cli.data.DataTableColumn;
import io.bdeploy.common.cli.data.DataTableRowBuilder;
import io.bdeploy.common.cli.data.RenderableResult;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.StreamHelper;
import io.bdeploy.common.util.UuidHelper;
import io.bdeploy.interfaces.configuration.instance.ApplicationValidationDto;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration.InstancePurpose;
import io.bdeploy.interfaces.configuration.instance.InstanceConfigurationDto;
import io.bdeploy.interfaces.configuration.instance.InstanceUpdateDto;
import io.bdeploy.interfaces.descriptor.template.InstanceTemplateReferenceDescriptor;
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

        @Help("Path to a ZIP file containing an export produced with this command")
        @Validator(ExistingPathValidator.class)
        String importFrom();

        @Help("Path to a non-existing ZIP file where to export a given instance configuration")
        @Validator(NonExistingPathValidator.class)
        String exportTo();

        @Help(value = "ID of the instance. When exporting must exist. When importing may exist (a new version is created). If not given, a random new ID is generated.")
        String uuid();

        @Help(value = "Version of the existing instance. When exporting must exist.")
        String version();

        @Help("Product version to update to")
        String updateTo();

        @Help(value = "List instance versions on the remote. By default will list active versions. If no active version is found for instance, will display its latest installed version. If no installed version is found for instance, will display its latest version",
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

        @Help("Create an instance with the given name")
        String create();

        @Help("When creating the instance, use the provided template YAML")
        @Validator(ExistingPathValidator.class)
        String template();

        @Help("Update the instance with the given ID")
        String update();

        @Help("The name to set for the updated instance")
        String name();

        @Help("The description to set for the created/updated instance")
        String description();

        @Help("The system ID to set for the created/updated instance")
        String system();

        @Help("The purpose to set for the created/updated instance")
        @ConfigurationValueMapping(ValueMapping.TO_UPPERCASE)
        InstancePurpose purpose();

        @Help("The name of the managed server if the instance is created on a target CENTRAL server.")
        String server();

        @Help("The name of the product to set for the created instance")
        String product();

        @Help("The version of the product to set for the created instance")
        String productVersion();

        @Help(value = "Delete the given instance. This CANNOT BE UNDONE.", arg = false)
        boolean delete() default false;

        @Help(value = "Use this flag to avoid confirmation prompt when deleting instance.", arg = false)
        boolean yes() default false;

        @Help(value = "Use this flag to open the dashboard of the current instance in the web UI.", arg = false)
        boolean open() default false;
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

        InstanceResource ir = ResourceProvider.getVersionedResource(remote, InstanceGroupResource.class, getLocalContext())
                .getInstanceResource(config.instanceGroup());

        if (config.create() != null) {
            if (config.template() != null) {
                return doCreateFromTemplate(remote, ir, config);
            } else {
                return doCreate(remote, ir, config);
            }
        } else if (config.update() != null) {
            return doUpdate(remote, ir, config);
        }

        helpAndFailIfMissing(config.uuid(), "--uuid missing");

        if (config.open()) {
            return BrowserHelper.openUrl(remote, "/#/instances/dashboard/" + config.instanceGroup() + '/' + config.uuid())//
                    ? createResultWithSuccessMessage("Successfully opened the dashboard")//
                    : createResultWithErrorMessage("Failed to open the dashboard");
        }

        if (config.exportTo() != null) {
            return doExport(ir, config);
        } else if (config.importFrom() != null) {
            return doImport(ir, config);
        } else if (config.updateTo() != null) {
            return doUpdateProduct(remote, ir, config);
        } else if (config.delete()) {
            return doDelete(config, ir);
        } else {
            return createNoOp();
        }
    }

    private DataResult doUpdate(RemoteService remote, InstanceResource ir, InstanceConfig config) {
        if (config.productVersion() != null) {
            throw new IllegalArgumentException("Please use --updateTo instead of --update to change the product version");
        }

        if (config.name() == null && config.purpose() == null && config.description() == null && config.system() == null) {
            helpAndFail("ERROR: Missing --name, --description, --system or --purpose");
        }

        List<InstanceVersionDto> v = ir.listVersions(config.update());
        Integer max = v.stream().map(d -> Integer.valueOf(d.key.getTag())).sorted(Collections.reverseOrder()).findFirst()
                .orElseThrow(() -> new IllegalStateException("Cannot determine current version of instance " + config.update()));
        String currentTag = max.toString();

        BackendInfoResource bir = ResourceProvider.getResource(remote, BackendInfoResource.class, getLocalContext());
        ManagedMasterDto server = null;
        if (bir.getVersion().mode == MinionMode.CENTRAL) {
            ManagedServersResource msr = ResourceProvider.getResource(remote, ManagedServersResource.class, getLocalContext());

            server = msr.getServerForInstance(config.instanceGroup(), config.update(), currentTag);
        }

        DataResult result = createSuccess();
        InstanceConfiguration cfg = ir.readVersion(config.update(), currentTag);

        if (config.name() != null && !config.name().isBlank()) {
            cfg.name = config.name();
            result.addField("New Name", config.name());
        }
        if (config.description() != null && !config.description().isBlank()) {
            cfg.description = config.description();
            result.addField("New Description", config.description());
        }
        if (config.purpose() != null) {
            cfg.purpose = config.purpose();
            result.addField("New Purpose", config.purpose());
        }
        if (config.system() != null) {
            SystemResource sr = ResourceProvider.getVersionedResource(remote, InstanceGroupResource.class, getLocalContext())
                    .getSystemResource(config.instanceGroup());
            Optional<SystemConfigurationDto> sys = sr.list().stream().filter(s -> s.config.id.equals(config.system())).findAny();
            if (sys.isEmpty()) {
                throw new IllegalArgumentException("Cannot find specified system on server: " + config.system());
            }

            cfg.system = sys.get().key;
        }
        InstanceConfigurationDto dto = new InstanceConfigurationDto(cfg,
                ir.getNodeConfigurations(config.update(), currentTag).nodeConfigDtos);
        ir.update(config.update(), new InstanceUpdateDto(dto, null), server != null ? server.hostName : null, currentTag);

        return result;
    }

    private DataResult doCreate(RemoteService remote, InstanceResource ir, InstanceConfig config) {
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
        cfg.id = config.uuid() == null ? UuidHelper.randomId() : config.uuid();
        cfg.autoUninstall = true;
        cfg.autoStart = false;
        cfg.description = config.description();
        cfg.name = config.create();
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
        Path input = Paths.get(config.importFrom());
        if (!Files.isRegularFile(input)) {
            helpAndFail("--importFrom is not a regular file");
        }

        try (InputStream is = Files.newInputStream(input);
                FormDataMultiPart fdmp = FormDataHelper.createMultiPartForStream("file", is)) {
            List<Key> keys = ir.importInstance(fdmp, config.uuid());
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

        Response resp = ir.exportInstance(config.uuid(), config.version());

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

    private DataResult doUpdateProduct(RemoteService remote, InstanceResource ir, InstanceConfig config) {
        String target = config.updateTo();
        String instance = config.uuid();

        List<InstanceVersionDto> v = ir.listVersions(instance);
        Integer max = v.stream().map(d -> Integer.valueOf(d.key.getTag())).sorted(Collections.reverseOrder()).findFirst()
                .orElseThrow(() -> new IllegalStateException("Cannot determine current version of instance " + instance));
        String currentTag = max.toString();

        BackendInfoResource bir = ResourceProvider.getResource(remote, BackendInfoResource.class, getLocalContext());
        ManagedMasterDto server = null;
        if (bir.getVersion().mode == MinionMode.CENTRAL) {
            ManagedServersResource msr = ResourceProvider.getResource(remote, ManagedServersResource.class, getLocalContext());

            server = msr.getServerForInstance(config.instanceGroup(), instance, currentTag);
        }

        DataResult result = createSuccess();
        InstanceConfiguration cfg = ir.readVersion(instance, currentTag);
        InstanceConfigurationDto dto = new InstanceConfigurationDto(cfg,
                ir.getNodeConfigurations(instance, currentTag).nodeConfigDtos);
        InstanceUpdateDto update = new InstanceUpdateDto(dto, null);

        // perform actual product update
        update = ir.updateProductVersion(instance, target, update);

        // perform validation of update
        List<ApplicationValidationDto> issues = ir.validate(instance, update);

        if (issues.isEmpty()) {
            ir.update(instance, update, server != null ? server.hostName : null, currentTag);

            if (update.validation != null && !update.validation.isEmpty()) {
                result.addField("Update Warnings", "There have been update warnings.");

                update.validation.forEach(val -> {
                    String pid = val.paramId == null ? "" : (" - " + val.paramId);
                    result.addField(" - " + (val.appId == null ? "Global" : (val.appId + pid)), val.message);
                });
            }

            result.addField("Updated To", target);
        } else {
            result.setMessage("Validation Issues");

            issues.forEach(val -> {
                String pid = val.paramId == null ? "" : (" - " + val.paramId);
                result.addField(" - " + (val.appId == null ? "Global" : (val.appId + pid)), val.message);
            });

            result.addField("Update Aborted", "Cannot perform update due to validation issues");
        }

        return result;
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

        DataTable table = createDataTable();
        table.setCaption("Instances of " + config.instanceGroup() + " on " + remote.getUri());

        table.column("ID", 15).column("Name *", 20).column(new DataTableColumn("Version", "Ver.", 4)).column("Installed", 9)
                .column("Active", 6).column("Purpose", 11).column("Product", 25).column("Product Version", 20)
                .column("System", 20).column("Description *", 40);

        if (central) {
            table.column("Target Server", 20);
        }

        InstanceResource ir = ResourceProvider.getResource(remote, InstanceGroupResource.class, getLocalContext())
                .getInstanceResource(config.instanceGroup());
        for (var instance : ir.list()) {
            if (config.uuid() != null && !config.uuid().isBlank() && !instance.instanceConfiguration.id.equals(config.uuid())) {
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

                row.cell(instance.instanceConfiguration.id).cell(vCfg.name).cell(version.key.getTag())
                        .cell(isInstalled ? "*" : "").cell(isActive ? "*" : "").cell(vCfg.purpose.name())
                        .cell(version.product.getName()).cell(version.product.getTag())
                        .cell(instance.instanceConfiguration.system != null ? instance.instanceConfiguration.system : "None")
                        .cell(vCfg.description);

                if (central) {
                    ManagedMasterDto server = msr.getServerForInstance(config.instanceGroup(), instance.instanceConfiguration.id,
                            version.key.getTag());

                    row.cell(server.hostName);
                }

                row.build();
            }
        }
        return table;
    }

    private List<InstanceVersionDto> sortFilterLimit(List<InstanceVersionDto> versions, InstanceStateRecord state,
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

    private List<InstanceVersionDto> filterByAllInstalledOrActive(List<InstanceVersionDto> versions, InstanceStateRecord state,
            InstanceConfig config) {
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

    private List<InstanceVersionDto> filterByVersionAndPurpose(List<InstanceVersionDto> versions, InstanceDto instance,
            InstanceConfig config, InstanceResource ir) {
        List<InstanceVersionDto> filtered = new ArrayList<>();

        for (var version : versions) {
            if (config.version() != null && !config.version().isBlank() && !version.key.getTag().equals(config.version())) {
                continue;
            }

            InstanceConfiguration vCfg = ir.readVersion(instance.instanceConfiguration.id, version.key.getTag());

            if (config.purpose() != null && !config.purpose().equals(vCfg.purpose)) {
                continue;
            }

            filtered.add(version);
        }

        return filtered;

    }

    private DataResult doDelete(InstanceConfig config, InstanceResource ir) {
        if (config.yes() || confirmDelete(config, ir)) {
            ir.delete(config.uuid());
            return createSuccess().addField("Deleted", config.uuid());
        } else {
            return createResultWithErrorMessage("Aborted, no confirmation");
        }
    }

    private boolean confirmDelete(InstanceConfig config, InstanceResource ir) {
        String instanceName = ir.read(config.uuid()).instanceConfiguration.name;
        String confirmation = System.console().readLine("Delete instance %1$s (%2$s)? This CANNOT be undone. (Y/N)? ",
                config.uuid(), instanceName);
        return "Y".equalsIgnoreCase(confirmation);
    }
}
