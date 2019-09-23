package io.bdeploy.ui.api.impl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.SecurityContext;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.objects.view.TreeView;
import io.bdeploy.bhive.objects.view.scanner.TreeVisitor;
import io.bdeploy.bhive.op.ScanOperation;
import io.bdeploy.bhive.op.TreeEntryLoadOperation;
import io.bdeploy.interfaces.configuration.instance.FileStatusDto;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceConfigurationDto;
import io.bdeploy.interfaces.configuration.instance.InstanceUpdateDto;
import io.bdeploy.interfaces.manifest.InstanceManifest;
import io.bdeploy.interfaces.manifest.history.InstanceManifestHistory.Action;
import io.bdeploy.interfaces.remote.MasterNamedResource;
import io.bdeploy.interfaces.remote.MasterRootResource;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.ui.api.ConfigFileResource;

public class ConfigFileResourceImpl implements ConfigFileResource {

    private final BHive hive;
    private final String instanceId;
    private final String groupId;

    @Context
    private SecurityContext context;

    public ConfigFileResourceImpl(BHive hive, String groupId, String instanceId) {
        this.hive = hive;
        this.groupId = groupId;
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

    @Override
    public void updateConfigFiles(List<FileStatusDto> updates, String expectedTag) {
        InstanceManifest oldConfig = InstanceManifest.load(hive, instanceId, null);

        MasterNamedResource master = ResourceProvider.getResource(oldConfig.getConfiguration().target, MasterRootResource.class)
                .getNamedMaster(groupId);

        Manifest.Key rootKey = master.update(new InstanceUpdateDto(
                new InstanceConfigurationDto(oldConfig.getConfiguration(), Collections.emptyList()), updates), expectedTag);

        InstanceManifest.of(hive, rootKey).getHistory(hive).record(Action.CREATE, context.getUserPrincipal().getName(), null);
        UiResources.getInstanceEventManager().create(instanceId, rootKey);
    }

}
