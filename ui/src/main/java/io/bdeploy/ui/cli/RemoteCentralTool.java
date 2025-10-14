package io.bdeploy.ui.cli;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import io.bdeploy.bhive.util.StorageHelper;
import io.bdeploy.common.ActivityReporter.Activity;
import io.bdeploy.common.Version;
import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cfg.Configuration.Validator;
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
import io.bdeploy.common.util.FormatHelper;
import io.bdeploy.common.util.VersionHelper;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration;
import io.bdeploy.interfaces.manifest.managed.ManagedMasterDto;
import io.bdeploy.interfaces.minion.MinionDto;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.jersey.cli.RemoteServiceTool;
import io.bdeploy.ui.api.BackendInfoResource;
import io.bdeploy.ui.api.InstanceGroupResource;
import io.bdeploy.ui.api.ManagedServersResource;
import io.bdeploy.ui.api.MinionMode;
import io.bdeploy.ui.cli.RemoteCentralTool.CentralConfig;
import io.bdeploy.ui.dto.BackendInfoDto;
import io.bdeploy.ui.dto.MinionSyncResultDto;
import jakarta.ws.rs.WebApplicationException;

@Help("Manage attached managed servers on central server")
@ToolCategory(TextUIResources.UI_CATEGORY)
@CliName("remote-central")
public class RemoteCentralTool extends RemoteServiceTool<CentralConfig> {

    public @interface CentralConfig {

        @Help(value = "Download managed server identification information from target MANAGED server.", arg = false)
        boolean managedIdent() default false;

        @Help("The target file to write managed or central identification information to")
        @Validator(NonExistingOrEmptyDirPathValidator.class)
        String output();

        @Help("Attach a managed server with the given server identification information (file) to the target CENTRAL server.")
        String attach();

        @Help("The instance group to query/manipulate managed servers for on the target CENTRAL server.")
        String instanceGroup();

        @Help("Override the name of the target managed server when attaching.")
        String name();

        @Help("Set the description of the managed server being attached")
        String description();

        @Help("Override the URI of the managed server being attached. The URI must be reachable from the central server.")
        String uri();

        @Help(value = "Don't try to automatically attach the managed server from the target CENTRAL server. Rather output a file which can be uploaded to the managed server.",
              arg = false)
        boolean offline() default false;

        @Help("Path to a file output by the central server in offline mode to be used on the target MANAGED server.")
        @Validator(ExistingPathValidator.class)
        String attachCentral();

        @Help(value = "List existing attached managed servers for an instance group on the target CENTRAL server.", arg = false)
        boolean list() default false;

        @Help(value = "Synchronize with the given managed server in the given instance group on the target CENTRAL server.",
              arg = false)
        boolean synchronize() default false;

        @Help(value = "Remove the given managed server in the given instance group on the target CENTRAL server. Also removes all instances associated with the local server from the central (but not from the managed server).",
              arg = false)
        boolean delete() default false;

        @Help(value = "Try to establish a connection to the given managed server and print its version.", arg = false)
        boolean ping() default false;

        @Help("The existing managed server attached to the instance group to query/manipulate")
        String server();

        @Help("Update the specified server. Use the --auth, --uri and --description parameters to update values.")
        String update();

        @Help("When updating a managed server, update the authentication token to the given value. EXPERT only!")
        String auth();

        @Help(value = "Collect and print a report of in-use server versions for a given Instance Group, or all groups if none is given.",
              arg = false)
        boolean versionReport();

        @Help(value = "Instead of grouping by instance group, sort the result table by server version", arg = false)
        boolean sortByVersion() default false;
    }

    public RemoteCentralTool() {
        super(CentralConfig.class);
    }

    @Override
    protected RenderableResult run(CentralConfig config, RemoteService remote) {
        BackendInfoResource bir = ResourceProvider.getVersionedResource(remote, BackendInfoResource.class, getLocalContext());
        BackendInfoDto version = bir.getVersion();

        if (config.managedIdent()) {
            return getManagedServerIdent(config, bir, version);
        }

        ManagedServersResource msr = ResourceProvider.getVersionedResource(remote, ManagedServersResource.class,
                getLocalContext());

        if (config.attachCentral() != null) {
            return attachCentralServer(config, version, msr);
        }

        // the rest of the commands are central only.
        checkMode(version, MinionMode.CENTRAL);

        if (config.list()) {
            return listManagedServers(config, remote, msr);
        } else if (config.attach() != null) {
            return attachManagedServer(config, msr);
        } else if (config.synchronize()) {
            helpAndFailIfMissing(config.instanceGroup(), "Missing --instanceGroup");
            helpAndFailIfMissing(config.server(), "Missing --server");

            MinionSyncResultDto result = msr.synchronize(config.instanceGroup(), config.server());
            return createSuccess().addField("Managed Server", config.server()).addField("Running Version",
                    result.server.update.runningVersion);
        } else if (config.delete()) {
            helpAndFailIfMissing(config.instanceGroup(), "Missing --instanceGroup");
            helpAndFailIfMissing(config.server(), "Missing --server");

            msr.deleteManagedServer(config.instanceGroup(), config.server());

            return createSuccess().addField("Removed Managed Server", config.server());
        } else if (config.ping()) {
            helpAndFailIfMissing(config.instanceGroup(), "Missing --instanceGroup");
            helpAndFailIfMissing(config.server(), "Missing --server");

            try {
                long start = System.currentTimeMillis();
                Version v = msr.pingServer(config.instanceGroup(), config.server());
                return createSuccess().addField("Server Version", v).addField("Full Roundtrip Time",
                        (System.currentTimeMillis() - start) + "ms");
            } catch (WebApplicationException e) {
                return createResultWithErrorMessage("Could not contact server " + config.server()).setException(e);
            }
        } else if (config.update() != null) {
            return updateManagedServer(config, msr);
        } else if (config.versionReport()) {
            return reportVersions(config, remote, msr);
        } else {
            return createNoOp();
        }
    }

    private DataResult updateManagedServer(CentralConfig config, ManagedServersResource msr) {
        helpAndFailIfMissing(config.instanceGroup(), "Missing --instanceGroup");

        String uri = config.uri();
        String desc = config.description();
        String auth = config.auth();

        if (uri == null && desc == null && auth == null) {
            helpAndFail("ERROR: Missing --uri, --description or --auth");
        }

        Optional<ManagedMasterDto> server = msr.getManagedServers(config.instanceGroup()).stream()
                .filter(m -> m.hostName.equals(config.update())).findFirst();
        if (server.isEmpty()) {
            throw new IllegalArgumentException("Cannot find server " + config.update());
        }

        DataResult result = createSuccess();

        ManagedMasterDto mmd = server.get();
        if (desc != null && !desc.isBlank()) {
            mmd.description = desc;
            result.addField("New Description", desc);
        }
        if (uri != null && !uri.isBlank()) {
            mmd.uri = uri;
            result.addField("New URI", uri);
        }
        if (auth != null && !auth.isBlank()) {
            result.addField("New Authentication", auth);
            mmd.auth = auth;
        }

        msr.updateManagedServer(config.instanceGroup(), config.update(), mmd);
        return result;
    }

    private DataResult attachManagedServer(CentralConfig config, ManagedServersResource msr) {
        helpAndFailIfMissing(config.instanceGroup(), "Missing --instanceGroup");
        helpAndFailIfMissing(config.description(), "Missing --description");

        Path source = Paths.get(config.attach());
        ManagedMasterDto mmd;
        try (InputStream is = Files.newInputStream(source)) {
            mmd = StorageHelper.fromStream(is, ManagedMasterDto.class);
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot read managed master information from " + source, e);
        }

        mmd.description = config.description();
        if (config.name() != null) {
            mmd.hostName = config.name();
        }
        if (config.uri() != null) {
            mmd.uri = config.uri();
        }

        if (!config.offline()) {
            msr.tryAutoAttach(config.instanceGroup(), mmd);

            return createResultWithSuccessMessage("Managed Server has been attached")
                    .addField("Instance Group", config.instanceGroup()).addField("Managed Server", mmd.hostName);
        } else {
            helpAndFailIfMissing(config.output(), "Missing --output");

            String ident = msr.getCentralIdent(config.instanceGroup(), mmd);
            msr.manualAttach(config.instanceGroup(), mmd);

            Path target = Paths.get(config.output());
            try {
                Files.writeString(target, ident);
            } catch (IOException e) {
                throw new IllegalArgumentException("Cannot write central identification to " + target, e);
            }

            return createResultWithSuccessMessage(
                    "Server has been manually attached to the Instance Group " + config.instanceGroup()).addField("Hint",
                            "Please use the `remote-central --attachCentral` command with the file " + target
                                    + " on the managed server now to complete offline attach.");
        }
    }

    private DataTable listManagedServers(CentralConfig config, RemoteService remote, ManagedServersResource msr) {
        helpAndFailIfMissing(config.instanceGroup(), "Missing --instanceGroup");

        List<ManagedMasterDto> mmds = msr.getManagedServers(config.instanceGroup());

        DataTable table = createDataTable();
        table.setCaption("Managed servers for " + config.instanceGroup() + " on " + remote.getUri());

        table.column(new DataTableColumn.Builder("Name").setMinWidth(10).build());
        table.column(new DataTableColumn.Builder("URI").setMinWidth(10).build());
        table.column(new DataTableColumn.Builder("Description").setMinWidth(0).build());
        table.column(new DataTableColumn.Builder("Last Sync").setMinWidth(5).build());
        table.column(new DataTableColumn.Builder("# Minions").setName("NumberOfLocalMinions").setMinWidth(9).build());
        table.column(new DataTableColumn.Builder("# Inst.").setName("NumberOfInstances").setMinWidth(7).build());

        for (ManagedMasterDto mmd : mmds) {
            List<InstanceConfiguration> instances = msr.getInstancesControlledBy(config.instanceGroup(), mmd.hostName);

            table.row().cell(mmd.hostName).cell(mmd.uri).cell(mmd.description)
                    .cell(mmd.lastSync != null ? FormatHelper.formatTemporal(mmd.lastSync) : "never").cell(mmd.nodes.nodes.size())
                    .cell(instances.size()).build();
        }
        return table;
    }

    private RenderableResult reportVersions(CentralConfig config, RemoteService remote, ManagedServersResource msr) {
        DataTable table = createDataTable();
        table.setCaption("Server Version Report of " + remote.getUri());

        table.column(new DataTableColumn.Builder("Instance Group").setMinWidth(13).build());
        table.column(new DataTableColumn.Builder("Name").setMinWidth(10).build());
        table.column(new DataTableColumn.Builder("Last Sync").setMinWidth(5).build());
        table.column(new DataTableColumn.Builder("Master Ver.").setMinWidth(11).build());
        table.column(new DataTableColumn.Builder("# Divergent Nodes").setMinWidth(17).build());

        List<String> groups;
        try (Activity fetchGroups = getActivityReporter().start("Fetching instance groups...")) {
            if (config.instanceGroup() != null) {
                groups = Collections.singletonList(config.instanceGroup());
            } else {
                groups = new ArrayList<>(ResourceProvider.getResource(remote, InstanceGroupResource.class, getLocalContext())
                        .list().stream().map(ig -> ig.instanceGroupConfiguration.name).toList());

                Collections.sort(groups);
            }
        }

        Map<Version, List<DataTableRowBuilder>> rowsByVersion = new TreeMap<>();
        try (Activity prepare = getActivityReporter().start("Fetching server information from instance groups...",
                groups.size())) {
            for (var group : groups) {
                if (collectMinionsForGroup(config, msr, group, table, rowsByVersion)) {
                    continue;
                }
                prepare.workAndCancelIfRequested(1);
            }
        }

        if (config.sortByVersion()) {
            // build rows in order.
            for (var entry : rowsByVersion.entrySet()) {
                for (var row : entry.getValue()) {
                    row.build();
                }
            }
        }

        return table;
    }

    private boolean collectMinionsForGroup(CentralConfig config, ManagedServersResource msr, String group, DataTable table,
            Map<Version, List<DataTableRowBuilder>> rowsByVersion) {
        List<ManagedMasterDto> mmds;
        try {
            mmds = msr.getManagedServers(group);
        } catch (Exception e) {
            out().println("Cannot fetch information for " + group + ": " + e.toString());
            if (isVerbose()) {
                e.printStackTrace(out());
            }
            return true;
        }

        for (var mmd : mmds) {
            var row = table.row().cell(group).cell(mmd.hostName)
                    .cell(mmd.lastSync != null ? FormatHelper.formatTemporal(mmd.lastSync) : "never");

            MinionDto master = null;
            Set<Version> versions = new TreeSet<>();
            for (var node : mmd.nodes.nodes.values()) {
                if (node.config.master) {
                    if (master != null) {
                        out().println("Warning: multiple masters found for " + mmd.hostName + " in " + group);
                    }
                    master = node.config;
                }
                if (node.config.version != null) {
                    versions.add(node.config.version);
                }
            }

            if (master == null) {
                out().println("Warning: no master found for " + mmd.hostName + " in " + group);
            }

            if (master == null) {
                row.cell("Unkown").cell(versions.size());
            } else {
                var mver = master.version;
                row.cell(mver.toString()).cell(versions.stream().filter(v -> !mver.equals(v)).count());
            }

            if (config.sortByVersion()) {
                var key = master == null ? VersionHelper.UNDEFINED : master.version;
                rowsByVersion.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
            } else {
                row.build();
            }
        }
        return false;
    }

    private DataResult getManagedServerIdent(CentralConfig config, BackendInfoResource bir, BackendInfoDto version) {
        checkMode(version, MinionMode.MANAGED);

        helpAndFailIfMissing(config.output(), "Missing --output");

        ManagedMasterDto mmd = bir.getManagedMasterIdentification();
        Path target = Paths.get(config.output());
        try (OutputStream os = Files.newOutputStream(target)) {
            os.write(StorageHelper.toRawBytes(mmd));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot write to " + target, e);
        }

        return createSuccess().addField("Ident File", config.output());
    }

    private DataResult attachCentralServer(CentralConfig config, BackendInfoDto version, ManagedServersResource msr) {
        checkMode(version, MinionMode.MANAGED);

        Path source = Paths.get(config.attachCentral());
        String ident;
        try {
            ident = Files.readString(source, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot read content of " + source, e);
        }

        String group = msr.manualAttachCentral(ident);
        return createSuccess().addField("Instance Group", group);
    }

    private static void checkMode(BackendInfoDto version, MinionMode expected) {
        if (version.mode != expected) {
            throw new IllegalArgumentException("Target server has wrong mode for the requested command: " + version.mode);
        }
    }
}
