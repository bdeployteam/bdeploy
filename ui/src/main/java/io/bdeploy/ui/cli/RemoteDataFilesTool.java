package io.bdeploy.ui.cli;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.io.FilenameUtils;

import io.bdeploy.common.cfg.Configuration.EnvironmentFallback;
import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cfg.Configuration.Validator;
import io.bdeploy.common.cfg.ExistingPathValidator;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.cli.ToolCategory;
import io.bdeploy.common.cli.data.DataResult;
import io.bdeploy.common.cli.data.DataTable;
import io.bdeploy.common.cli.data.DataTableRowBuilder;
import io.bdeploy.common.cli.data.RenderableResult;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.StreamHelper;
import io.bdeploy.interfaces.directory.RemoteDirectory;
import io.bdeploy.interfaces.directory.RemoteDirectoryEntry;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.jersey.cli.RemoteServiceTool;
import io.bdeploy.ui.api.InstanceGroupResource;
import io.bdeploy.ui.api.InstanceResource;
import io.bdeploy.ui.cli.RemoteDataFilesTool.DataFilesConfig;
import jakarta.ws.rs.core.Response;

@Help("List, export and delete data files")
@ToolCategory(TextUIResources.UI_CATEGORY)
@CliName("remote-data-files")
public class RemoteDataFilesTool extends RemoteServiceTool<DataFilesConfig> {

    public @interface DataFilesConfig {

        @Help("Name of the instance group for import into or export from")
        @EnvironmentFallback("REMOTE_BHIVE")
        String instanceGroup();

        @Help("UUID of the instance. When exporting must exist. When importing may exist (a new version is created). If not given, a random new UUID is generated.")
        String uuid();

        @Help("Filename pattern that will be matched against remote data files. Will match all files if not specified")
        String filter();

        @Help(value = "CLI by default uses simple wildcard (* and ?) pattern matching. If you want to pass regular expression as --filter add --regex to list of arguments",
              arg = false)
        boolean regex() default false;

        @Help(value = "Use this flag if you want to list found files", arg = false)
        boolean list() default false;

        @Help(value = "Use this flag if you want to download found files", arg = false)
        boolean export() default false;

        @Help("Path to an existing folder where ZIP files will be exported to for a given instance configuration")
        @Validator(ExistingPathValidator.class)
        String exportTo();

        @Help(value = "Use this flag if you want to delete found files", arg = false)
        boolean delete() default false;
    }

    public RemoteDataFilesTool() {
        super(DataFilesConfig.class);
    }

    @Override
    protected RenderableResult run(DataFilesConfig config, RemoteService remote) {
        helpAndFailIfMissing(config.instanceGroup(), "--instanceGroup is missing");
        helpAndFailIfMissing(config.uuid(), "--uuid is missing");

        int flagCount = (config.list() ? 1 : 0) + (config.export() ? 1 : 0) + (config.delete() ? 1 : 0);
        if (flagCount == 0) {
            helpAndFail("Please specify what you want to do by enabling one of the flags: --list, --export or --delete");
        }
        if (flagCount > 1) {
            helpAndFail("You can enable only one flag at a time: --list, --export or --delete");
        }

        InstanceResource ir = ResourceProvider.getVersionedResource(remote, InstanceGroupResource.class, getLocalContext())
                .getInstanceResource(config.instanceGroup());

        var matchingFiles = getMatchingFiles(config, ir);

        if (config.list()) {
            return doList(matchingFiles);
        } else if (config.export()) {
            helpAndFailIfMissing(config.exportTo(), "--exportTo is missing");
            return doExport(matchingFiles, config, ir);
        } else if (config.delete()) {
            return doDelete(matchingFiles, config, ir);
        }
        return createNoOp();
    }

    private DataResult doExport(Map<RemoteDirectory, List<RemoteDirectoryEntry>> matchingFiles, DataFilesConfig config,
            InstanceResource ir) {

        if (countFiles(matchingFiles) == 0) {
            helpAndFail("No files found");
        }

        Path exportTo = Paths.get(config.exportTo());

        if (!Files.isDirectory(exportTo)) {
            helpAndFail("exportTo must be a directory: " + exportTo);
        }

        for (var dirSnapshot : matchingFiles.keySet()) {
            List<RemoteDirectoryEntry> entries = matchingFiles.get(dirSnapshot);

            if (entries.isEmpty()) {
                continue;
            }

            String token = ir.getContentMultiZipStreamRequest(config.uuid(), dirSnapshot.minion, entries);
            Response response = ir.getContentMultiZipStream(config.uuid(), token);

            Path target = exportTo.resolve(dirSnapshot.minion + ".zip");
            if (Files.isRegularFile(target)) {
                helpAndFail("Target file already exists: " + target);
            }

            try (OutputStream os = Files.newOutputStream(target); InputStream zip = response.readEntity(InputStream.class)) {
                StreamHelper.copy(zip, os);
            } catch (IOException e) {
                throw new IllegalStateException("Cannot download files from instance", e);
            }
        }
        return createSuccess().addField("Exported files to ", config.exportTo());
    }

    private DataResult doDelete(Map<RemoteDirectory, List<RemoteDirectoryEntry>> matchingFiles, DataFilesConfig config,
            InstanceResource ir) {
        for (var dirSnapshot : matchingFiles.keySet()) {
            for (var entry : matchingFiles.get(dirSnapshot)) {
                ir.deleteDataFile(config.uuid(), dirSnapshot.minion, entry);
            }
        }
        return createSuccess().addField("Delete files", "Successfully deleted " + countFiles(matchingFiles) + " files");
    }

    private DataTable doList(Map<RemoteDirectory, List<RemoteDirectoryEntry>> matchingFiles) {

        DataTable table = createDataTable();
        table.setCaption("Found " + countFiles(matchingFiles) + " data files");

        table.column("Path", 30).column("minion", 30);

        for (var dirSnapshot : matchingFiles.keySet()) {
            for (var entry : matchingFiles.get(dirSnapshot)) {
                DataTableRowBuilder row = table.row();
                row.cell(entry.path);
                row.cell(dirSnapshot.minion);
                row.build();
            }
        }
        return table;
    }

    private Map<RemoteDirectory, List<RemoteDirectoryEntry>> getMatchingFiles(DataFilesConfig config, InstanceResource ir) {
        var processResource = ir.getProcessResource(config.uuid());
        var result = new HashMap<RemoteDirectory, List<RemoteDirectoryEntry>>();
        for (var dirSnapshot : processResource.getDataDirSnapshot()) {
            var list = dirSnapshot.entries.stream().filter(entry -> matches(config.filter(), entry.path, config.regex()))
                    .collect(Collectors.toList());
            if (!list.isEmpty()) {
                result.put(dirSnapshot, list);
            }
        }
        return result;
    }

    private int countFiles(Map<RemoteDirectory, List<RemoteDirectoryEntry>> matchingFiles) {
        return matchingFiles.values().stream().mapToInt(entries -> entries.size()).sum();
    }

    private boolean matches(String filter, String path, boolean isRegex) {
        if (filter == null) {
            return true;
        }
        if (isRegex) {
            return path.matches(filter);
        }
        return FilenameUtils.wildcardMatch(path, filter);
    }

}
