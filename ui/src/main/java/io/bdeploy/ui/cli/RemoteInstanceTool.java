package io.bdeploy.ui.cli;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status.Family;

import org.glassfish.jersey.media.multipart.MultiPart;
import org.glassfish.jersey.media.multipart.file.StreamDataBodyPart;

import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
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
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration.InstancePurpose;
import io.bdeploy.interfaces.configuration.instance.InstanceConfigurationDto;
import io.bdeploy.interfaces.manifest.managed.ManagedMasterDto;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.jersey.JerseyClientFactory;
import io.bdeploy.jersey.cli.RemoteServiceTool;
import io.bdeploy.ui.api.BackendInfoResource;
import io.bdeploy.ui.api.InstanceGroupResource;
import io.bdeploy.ui.api.InstanceResource;
import io.bdeploy.ui.api.ManagedServersResource;
import io.bdeploy.ui.api.MinionMode;
import io.bdeploy.ui.cli.RemoteInstanceTool.InstanceConfig;
import io.bdeploy.ui.dto.InstanceVersionDto;

@Help("List, create, import and export  instance configurations")
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

        @Help(value = "UUID of the instance. When exporting must exist. When importing may exist (a new version is created). If not given, a random new UUID is generated.")
        String uuid();

        @Help(value = "When exporting, optional version of the existing instance. Otherwise the latest version is exported.")
        String version();

        @Help("Product version to update to")
        String updateTo();

        @Help(value = "List instance versions on the remote", arg = false)
        boolean list() default false;

        @Help(value = "List only active instance versions", arg = false)
        boolean active() default false;

        @Help("List only a certain amount of versions per instance. Specify zero for no limit.")
        int limit() default 5;

        @Help("Create an instance with the given name")
        String create();

        @Help("Update the instance with the given UUID")
        String update();

        @Help("The name to set for the updated instance")
        String name();

        @Help("The description to set for the created/updated instance")
        String description();

        @Help("The purpose to set for the created/updated instance")
        @ConfigurationValueMapping(ValueMapping.TO_UPPERCASE)
        InstancePurpose purpose();

        @Help("The name of the managed server if the instance is created on a target CENTRAL server.")
        String server();

        @Help("The name of the product to set for the created instance")
        String product();

        @Help("The version of the product to set for the created instance")
        String productVersion();

    }

    public RemoteInstanceTool() {
        super(InstanceConfig.class);
    }

    @Override
    protected RenderableResult run(InstanceConfig config, RemoteService remote) {
        helpAndFailIfMissing(config.instanceGroup(), "--instanceGroup missing");

        if (config.list()) {
            return list(remote, config);
        }

        InstanceResource ir = ResourceProvider.getVersionedResource(remote, InstanceGroupResource.class, getLocalContext())
                .getInstanceResource(config.instanceGroup());

        if (config.create() != null) {
            return doCreate(remote, ir, config);
        } else if (config.update() != null) {
            return doUpdate(remote, ir, config);
        }

        helpAndFailIfMissing(config.uuid(), "--uuid missing");

        if (config.exportTo() != null) {
            return doExport(ir, config);
        } else if (config.importFrom() != null) {
            return doImport(remote, config);
        } else if (config.updateTo() != null) {
            ir.updateTo(config.uuid(), config.updateTo());
            return createSuccess();
        } else {
            return createNoOp();
        }
    }

    private DataResult doUpdate(RemoteService remote, InstanceResource ir, InstanceConfig config) {
        if (config.productVersion() != null) {
            throw new IllegalArgumentException("Please use --updateTo instead of --update to change the product version");
        }

        if (config.name() == null && config.purpose() == null && config.description() == null) {
            helpAndFail("ERROR: Missing --name, --description or --purpose");
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
        InstanceConfigurationDto dto = new InstanceConfigurationDto(cfg,
                ir.getNodeConfigurations(config.update(), currentTag).nodeConfigDtos);
        ir.update(config.update(), dto, server != null ? server.hostName : null, currentTag);

        return result;
    }

    private DataResult doCreate(RemoteService remote, InstanceResource ir, InstanceConfig config) {
        helpAndFailIfMissing(config.description(), "Missing --description");
        helpAndFailIfMissing(config.purpose(), "Missing --purpose");
        helpAndFailIfMissing(config.product(), "Missing --product");
        helpAndFailIfMissing(config.productVersion(), "Missing --productVersion");

        BackendInfoResource bir = ResourceProvider.getVersionedResource(remote, BackendInfoResource.class, getLocalContext());
        if (bir.getVersion().mode == MinionMode.CENTRAL) {
            helpAndFailIfMissing(config.server(), "Missing --server");
        }

        InstanceConfiguration cfg = new InstanceConfiguration();
        cfg.uuid = UuidHelper.randomId();
        cfg.autoUninstall = true;
        cfg.autoStart = false;
        cfg.description = config.description();
        cfg.name = config.create();
        cfg.product = new Manifest.Key(config.product(), config.productVersion());
        cfg.purpose = config.purpose();

        ir.create(cfg, config.server());

        return createSuccess().addField("Instance UUID", cfg.uuid);
    }

    private DataResult doImport(RemoteService svc, InstanceConfig config) {
        Path input = Paths.get(config.importFrom());
        if (!Files.isRegularFile(input)) {
            helpAndFail("--importFrom is not a regular file");
        }

        try (InputStream is = Files.newInputStream(input)) {
            try (MultiPart mp = new MultiPart()) {
                StreamDataBodyPart bp = new StreamDataBodyPart("file", is);
                bp.setFilename("instance.zip");
                bp.setMediaType(MediaType.APPLICATION_OCTET_STREAM_TYPE);
                mp.bodyPart(bp);

                WebTarget target = JerseyClientFactory.get(svc).getBaseTarget()
                        .path("/group/" + config.instanceGroup() + "/instance/" + config.uuid() + "/import");
                Response response = target.request().post(Entity.entity(mp, MediaType.MULTIPART_FORM_DATA_TYPE));

                if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {
                    throw new IllegalStateException("Import failed: " + response.getStatusInfo().getStatusCode() + ": "
                            + response.getStatusInfo().getReasonPhrase());
                }

                return createSuccess().addField("Created", response.readEntity(new GenericType<List<Key>>() {
                }));
            }
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

    private DataTable list(RemoteService remote, InstanceConfig config) {
        BackendInfoResource bir = ResourceProvider.getVersionedResource(remote, BackendInfoResource.class, getLocalContext());
        boolean central = false;
        if (bir.getVersion().mode == MinionMode.CENTRAL) {
            central = true;
        }

        ManagedServersResource msr = ResourceProvider.getResource(remote, ManagedServersResource.class, getLocalContext());

        DataTable table = createDataTable();
        table.setCaption("Instances of " + config.instanceGroup() + " on " + remote.getUri());

        table.column("UUID", 15).column("Name *", 20).column(new DataTableColumn("Version", "Ver.", 4)).column("Installed", 9)
                .column("Active", 6).column("Purpose", 11).column("Product", 25).column("Product Version", 20)
                .column("Description *", 40);

        if (central) {
            table.column("Target Server", 20);
        }

        InstanceResource ir = ResourceProvider.getResource(remote, InstanceGroupResource.class, getLocalContext())
                .getInstanceResource(config.instanceGroup());
        for (var instance : ir.list()) {
            if (config.uuid() != null && !config.uuid().isBlank() && !instance.instanceConfiguration.uuid.equals(config.uuid())) {
                continue;
            }

            var versions = ir.listVersions(instance.instanceConfiguration.uuid);
            var state = ir.getDeploymentStates(instance.instanceConfiguration.uuid);
            versions.sort((a, b) -> Long.compare(Long.parseLong(b.key.getTag()), Long.parseLong(a.key.getTag())));
            var limited = versions;
            if (config.limit() > 0 && config.limit() < versions.size()) {
                limited = versions.subList(0, config.limit());
            }
            for (var version : limited) {
                if (config.version() != null && !config.version().isBlank() && !version.key.getTag().equals(config.version())) {
                    continue;
                }

                boolean isActive = version.key.getTag().equals(state.activeTag); // activeTag may be null.
                boolean isInstalled = state.installedTags.contains(version.key.getTag());

                if (config.active() && !isActive) {
                    continue;
                }

                InstanceConfiguration vCfg = ir.readVersion(instance.instanceConfiguration.uuid, version.key.getTag());
                DataTableRowBuilder row = table.row();

                row.cell(instance.instanceConfiguration.uuid).cell(vCfg.name).cell(version.key.getTag())
                        .cell(isInstalled ? "*" : "").cell(isActive ? "*" : "").cell(vCfg.purpose.name())
                        .cell(version.product.getName()).cell(version.product.getTag()).cell(vCfg.description);

                if (central) {
                    ManagedMasterDto server = msr.getServerForInstance(config.instanceGroup(),
                            instance.instanceConfiguration.uuid, version.key.getTag());

                    row.cell(server.hostName);
                }

                row.build();
            }
        }
        return table;
    }

}
