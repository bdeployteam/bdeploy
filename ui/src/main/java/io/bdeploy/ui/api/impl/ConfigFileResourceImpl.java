package io.bdeploy.ui.api.impl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.SecurityContext;

import org.apache.commons.codec.binary.Base64;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.objects.view.BlobView;
import io.bdeploy.bhive.objects.view.TreeView;
import io.bdeploy.bhive.objects.view.scanner.TreeVisitor;
import io.bdeploy.bhive.op.ObjectLoadOperation;
import io.bdeploy.bhive.op.ScanOperation;
import io.bdeploy.bhive.op.TreeEntryLoadOperation;
import io.bdeploy.common.util.StreamHelper;
import io.bdeploy.interfaces.configuration.instance.FileStatusDto;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceConfigurationDto;
import io.bdeploy.interfaces.configuration.instance.InstanceUpdateDto;
import io.bdeploy.interfaces.manifest.InstanceManifest;
import io.bdeploy.interfaces.manifest.ProductManifest;
import io.bdeploy.interfaces.manifest.managed.MasterProvider;
import io.bdeploy.interfaces.remote.MasterNamedResource;
import io.bdeploy.interfaces.remote.MasterRootResource;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.ui.api.ConfigFileResource;
import io.bdeploy.ui.api.Minion;
import io.bdeploy.ui.dto.ConfigFileDto;

public class ConfigFileResourceImpl implements ConfigFileResource {

    private final BHive hive;
    private final String instanceId;
    private final String groupId;

    @Context
    private SecurityContext context;

    @Context
    private ResourceContext rc;

    @Inject
    private MasterProvider mp;

    @Inject
    private InstanceEventManager iem;

    @Inject
    private Minion minion;

    public ConfigFileResourceImpl(BHive hive, String groupId, String instanceId) {
        this.hive = hive;
        this.groupId = groupId;
        this.instanceId = instanceId;
    }

    @Override
    public List<ConfigFileDto> listConfigFiles(String tag) {
        InstanceManifest manifest = InstanceManifest.load(hive, instanceId, tag);
        InstanceConfiguration configuration = manifest.getConfiguration();
        ObjectId cfgTree = configuration.configTree;

        if (cfgTree == null) {
            return Collections.emptyList();
        }

        List<ConfigFileDto> cfgFilePaths = new ArrayList<>();

        // collect all blobs from the current config tree
        TreeView view = hive.execute(new ScanOperation().setTree(cfgTree));
        view.visit(new TreeVisitor.Builder().onBlob(b -> cfgFilePaths.add(new ConfigFileDto(b.getPathString(), isTextFile(b))))
                .build());

        return cfgFilePaths;
    }

    private boolean isTextFile(BlobView blob) {
        try (InputStream is = hive.execute(new ObjectLoadOperation().setObject(blob.getElementId()))) {
            return StreamHelper.isTextFile(is);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot determine content type of BLOB: " + blob, e);
        }
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
            return Base64.encodeBase64String(baos.toByteArray());
        } catch (IOException e) {
            throw new WebApplicationException("Cannot read configuration file: " + file, e);
        }
    }

    @Override
    public void updateConfigFiles(List<FileStatusDto> updates, String expectedTag) {
        InstanceManifest oldConfig = InstanceManifest.load(hive, instanceId, null);

        MasterRootResource root = ResourceProvider.getVersionedResource(mp.getControllingMaster(hive, oldConfig.getManifest()),
                MasterRootResource.class, context);
        MasterNamedResource master = root.getNamedMaster(groupId);

        Manifest.Key rootKey = master.update(new InstanceUpdateDto(
                new InstanceConfigurationDto(oldConfig.getConfiguration(), Collections.emptyList()), updates), expectedTag);

        InstanceResourceImpl.syncInstance(minion, rc, groupId, instanceId);

        iem.create(rootKey);
    }

    @Override
    public List<ConfigFileDto> syncConfigFiles(String iTag, String pName, String pTag) {
        // list instance config files
        InstanceManifest instanceManifest = InstanceManifest.load(hive, instanceId, iTag);
        InstanceConfiguration instanceConfiguration = instanceManifest.getConfiguration();
        ObjectId iCfgTree = instanceConfiguration.configTree;

        // build a map path->ObjectId
        Map<String, ObjectId> iFilesMap = new HashMap<>();
        if (iCfgTree != null) {
            TreeView view = hive.execute(new ScanOperation().setTree(iCfgTree));
            view.visit(new TreeVisitor.Builder().onBlob(b -> iFilesMap.put(b.getPathString(), b.getElementId())).build());
        }

        // list product config files
        Manifest.Key key = new Manifest.Key(pName, pTag);
        ProductManifest productManifest = ProductManifest.of(hive, key);
        ObjectId pCfgTree = productManifest.getConfigTemplateTreeId();

        List<ConfigFileDto> cfgFilePaths = new ArrayList<>();
        // collect all blobs from the product's config tree
        if (pCfgTree != null) {
            TreeView view = hive.execute(new ScanOperation().setTree(pCfgTree));
            view.visit(new TreeVisitor.Builder().onBlob(b -> {
                ObjectId iId = iFilesMap.get(b.getPathString());
                if (iId == null || !iId.equals(b.getElementId())) {
                    cfgFilePaths.add(new ConfigFileDto(b.getPathString(), isTextFile(b)));
                }
            }).build());
        }

        return cfgFilePaths;
    }

}
