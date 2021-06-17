package io.bdeploy.ui.api.impl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.codec.binary.Base64;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.objects.view.TreeView;
import io.bdeploy.bhive.objects.view.scanner.TreeVisitor;
import io.bdeploy.bhive.op.ObjectLoadOperation;
import io.bdeploy.bhive.op.ScanOperation;
import io.bdeploy.bhive.op.TreeEntryLoadOperation;
import io.bdeploy.common.util.StreamHelper;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration;
import io.bdeploy.interfaces.manifest.InstanceManifest;
import io.bdeploy.interfaces.manifest.ProductManifest;
import io.bdeploy.interfaces.manifest.managed.MasterProvider;
import io.bdeploy.ui.api.ConfigFileResource;
import io.bdeploy.ui.api.Minion;
import io.bdeploy.ui.dto.ConfigFileDto;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.SecurityContext;

public class ConfigFileResourceImpl implements ConfigFileResource {

    private final BHive hive;
    private final String instanceId;

    @Context
    private SecurityContext context;

    @Context
    private ResourceContext rc;

    @Inject
    private MasterProvider mp;

    @Inject
    private ChangeEventManager changes;

    @Inject
    private Minion minion;

    public ConfigFileResourceImpl(BHive hive, String instanceId) {
        this.hive = hive;
        this.instanceId = instanceId;
    }

    @Override
    public List<ConfigFileDto> listConfigFiles(String tag, String prodName, String prodTag) {
        InstanceManifest manifest = InstanceManifest.load(hive, instanceId, tag);
        InstanceConfiguration configuration = manifest.getConfiguration();
        ObjectId cfgTree = configuration.configTree;

        if (cfgTree == null) {
            return Collections.emptyList();
        }

        List<ConfigFileDto> cfgFilePaths = new ArrayList<>();
        Map<String, ObjectId> productMap = new TreeMap<>();

        // list product config files
        ProductManifest productManifest = ProductManifest.of(hive, new Manifest.Key(prodName, prodTag));
        ObjectId pCfgTree = productManifest.getConfigTemplateTreeId();

        // collect all blobs from the product's config tree
        if (pCfgTree != null) {
            TreeView view = hive.execute(new ScanOperation().setTree(pCfgTree));
            view.visit(new TreeVisitor.Builder().onBlob(b -> {
                productMap.put(b.getPathString(), b.getElementId());
            }).build());
        }

        // collect all blobs from the current config tree
        TreeView view = hive.execute(new ScanOperation().setTree(cfgTree));
        view.visit(new TreeVisitor.Builder().onBlob(b -> {
            var cfg = new ConfigFileDto(b.getPathString(), isTextFile(b.getElementId()), b.getElementId(),
                    productMap.get(b.getPathString()));
            cfgFilePaths.add(cfg);
            productMap.remove(cfg.path);
        }).build());

        for (var prodFile : productMap.entrySet()) {
            cfgFilePaths.add(new ConfigFileDto(prodFile.getKey(), isTextFile(prodFile.getValue()), null, prodFile.getValue()));
        }

        return cfgFilePaths;
    }

    private boolean isTextFile(ObjectId id) {
        try (InputStream is = hive.execute(new ObjectLoadOperation().setObject(id))) {
            return StreamHelper.isTextFile(is);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot determine content type of BLOB: " + id, e);
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
    public String loadProductConfigFile(String prodName, String prodTag, String file) {
        ProductManifest productManifest = ProductManifest.of(hive, new Manifest.Key(prodName, prodTag));
        ObjectId pCfgTree = productManifest.getConfigTemplateTreeId();

        try (InputStream is = hive.execute(new TreeEntryLoadOperation().setRootTree(pCfgTree).setRelativePath(file));
                ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            is.transferTo(baos);
            return Base64.encodeBase64String(baos.toByteArray());
        } catch (IOException e) {
            throw new WebApplicationException("Cannot read configuration file: " + file, e);
        }
    }

}
