package io.bdeploy.ui.api.impl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.SecurityContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.objects.view.TreeView;
import io.bdeploy.bhive.objects.view.scanner.TreeVisitor;
import io.bdeploy.bhive.op.ExportTreeOperation;
import io.bdeploy.bhive.op.ImportTreeOperation;
import io.bdeploy.bhive.op.ManifestNextIdOperation;
import io.bdeploy.bhive.op.ScanOperation;
import io.bdeploy.bhive.op.TreeEntryLoadOperation;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration;
import io.bdeploy.interfaces.manifest.InstanceManifest;
import io.bdeploy.interfaces.manifest.InstanceManifest.Builder;
import io.bdeploy.interfaces.manifest.InstanceNodeManifest;
import io.bdeploy.interfaces.manifest.history.InstanceManifestHistory.Action;
import io.bdeploy.jersey.JerseyWriteLockService.LockingResource;
import io.bdeploy.jersey.JerseyWriteLockService.WriteLock;
import io.bdeploy.ui.api.ConfigFileResource;
import io.bdeploy.ui.api.Minion;
import io.bdeploy.ui.dto.FileStatusDto;

@LockingResource(InstanceResourceImpl.GLOBAL_INSTANCE_LOCK)
public class ConfigFileResourceImpl implements ConfigFileResource {

    private static final Logger log = LoggerFactory.getLogger(ConfigFileResourceImpl.class);

    private final BHive hive;
    private final String instanceId;

    @Context
    private SecurityContext context;

    @Inject
    private Minion minion;

    public ConfigFileResourceImpl(BHive hive, String instanceId) {
        this.hive = hive;
        this.instanceId = instanceId;
    }

    @Override
    public List<String> listConfigFiles(String tag) {
        InstanceManifest manifest = InstanceManifest.load(hive, instanceId, tag);
        InstanceConfiguration configuration = manifest.getConfiguration();
        ObjectId cfgTree = configuration.configTree;

        if (cfgTree == null) {
            return Collections.emptyList();
        }

        List<String> cfgFilePaths = new ArrayList<>();

        // collect all blobs from the current config tree
        TreeView view = hive.execute(new ScanOperation().setTree(cfgTree));
        view.visit(new TreeVisitor.Builder().onBlob(b -> cfgFilePaths.add(b.getPathString())).build());

        return cfgFilePaths;
    }

    @Override
    public String loadConfigFile(String tag, String file) {
        InstanceManifest manifest = InstanceManifest.load(hive, instanceId, tag);
        InstanceConfiguration configuration = manifest.getConfiguration();
        ObjectId cfgTree = configuration.configTree;

        if (cfgTree == null) {
            throw new WebApplicationException("Cannot find file: " + file, Status.NOT_FOUND);
        }

        try (InputStream is = hive.execute(new TreeEntryLoadOperation().setRootTree(cfgTree).setRelativePath(file));
                ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            is.transferTo(baos);
            return baos.toString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new WebApplicationException("Cannot read configuration file: " + file, e);
        }
    }

    @WriteLock
    @Override
    public void updateConfigFiles(List<FileStatusDto> updates, String expectedTag) {
        InstanceManifest oldConfig = InstanceManifest.load(hive, instanceId, null);

        if (!oldConfig.getManifest().getTag().equals(expectedTag)) {
            throw new WebApplicationException("Expected version is not the current one: expected=" + expectedTag + ", current="
                    + oldConfig.getManifest().getTag(), Status.CONFLICT);
        }

        Builder newConfig = new InstanceManifest.Builder();
        InstanceConfiguration config = oldConfig.getConfiguration();

        // calculate target key.
        String rootName = InstanceManifest.getRootName(oldConfig.getConfiguration().uuid);
        String rootTag = hive.execute(new ManifestNextIdOperation().setManifestName(rootName)).toString();
        Manifest.Key rootKey = new Manifest.Key(rootName, rootTag);

        // create new config tree.
        ObjectId newConfigTree = applyConfigUpdates(config.configTree, updates);

        config.configTree = newConfigTree;
        newConfig.setInstanceConfiguration(config);
        for (Map.Entry<String, Manifest.Key> entry : oldConfig.getInstanceNodeManifests().entrySet()) {
            InstanceNodeManifest inm = InstanceNodeManifest.of(hive, entry.getValue());
            Key newInmKey = new Manifest.Key(entry.getValue().getName(), rootTag);

            new InstanceNodeManifest.Builder().setInstanceNodeConfiguration(inm.getConfiguration()).setConfigTreeId(newConfigTree)
                    .setKey(newInmKey).setMinionName(entry.getKey()).insert(hive);

            newConfig.addInstanceNodeManifest(entry.getKey(), newInmKey);
        }

        newConfig.setKey(rootKey).insert(hive);
        InstanceManifest.of(hive, rootKey).getHistory(hive).record(Action.CREATE, context.getUserPrincipal().getName(), null);
        UiResources.getInstanceEventManager().create(instanceId, rootKey);
    }

    private ObjectId applyConfigUpdates(ObjectId configTree, List<FileStatusDto> updates) {
        Path tmpDir = null;
        try {
            tmpDir = Files.createTempDirectory(minion.getTempDir(), "cfgUp-");
            Path cfgDir = tmpDir.resolve("cfg");

            // 1. export current tree to temp directory
            exportConfigTree(configTree, cfgDir);

            // 2. apply updates to files
            applyUpdates(updates, cfgDir);

            // 3. re-import new tree from temp directory
            return hive.execute(new ImportTreeOperation().setSourcePath(cfgDir));
        } catch (IOException e) {
            throw new WebApplicationException("Cannot update configuration files", e);
        } finally {
            if (tmpDir != null) {
                PathHelper.deleteRecursive(tmpDir);
            }
        }
    }

    private void exportConfigTree(ObjectId configTree, Path cfgDir) {
        if (configTree == null) {
            PathHelper.mkdirs(cfgDir);
            return;
        }

        try {
            hive.execute(new ExportTreeOperation().setSourceTree(configTree).setTargetPath(cfgDir));
        } catch (Exception e) {
            // this can happen if the hive was damaged. we allow this case to have a way out
            // if all things break badly.
            log.error("Cannot load existing configuration files", e);
        }
    }

    private void applyUpdates(List<FileStatusDto> updates, Path cfgDir) throws IOException {
        for (FileStatusDto update : updates) {
            Path file = cfgDir.resolve(update.file);
            if (!file.startsWith(cfgDir)) {
                throw new WebApplicationException("Update wants to write to file outside update directory", Status.BAD_REQUEST);
            }

            switch (update.type) {
                case ADD:
                    PathHelper.mkdirs(file.getParent());
                    Files.write(file, update.content.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE_NEW);
                    break;
                case DELETE:
                    Files.delete(file);
                    break;
                case EDIT:
                    Files.write(file, update.content.getBytes(StandardCharsets.UTF_8));
                    break;
            }
        }
    }

}
