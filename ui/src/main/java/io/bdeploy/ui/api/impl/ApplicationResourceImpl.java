package io.bdeploy.ui.api.impl;

import java.util.List;
import java.util.SortedSet;
import java.util.stream.Collectors;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.interfaces.descriptor.application.ApplicationDescriptor;
import io.bdeploy.interfaces.manifest.ApplicationManifest;
import io.bdeploy.interfaces.manifest.ProductManifest;
import io.bdeploy.ui.api.ApplicationResource;
import io.bdeploy.ui.dto.ApplicationDto;

public class ApplicationResourceImpl implements ApplicationResource {

    private final BHive productBHive;
    private final Manifest.Key productKey;

    public ApplicationResourceImpl(BHive productBHive, Manifest.Key productKey) {
        this.productBHive = productBHive;
        this.productKey = productKey;
    }

    @Override
    public List<ApplicationDto> list() {
        ProductManifest productManifest = ProductManifest.of(productBHive, productKey);
        SortedSet<Key> applications = productManifest.getApplications();
        return applications.stream().map(k -> ApplicationManifest.of(productBHive, k)).map(mf -> {
            ApplicationDto descriptor = new ApplicationDto();
            descriptor.key = mf.getKey();
            descriptor.name = mf.getDescriptor().name;
            descriptor.descriptor = mf.getDescriptor();
            return descriptor;
        }).collect(Collectors.toList());
    }

    @Override
    public ApplicationDescriptor getDescriptor(String name, String tag) {
        ApplicationManifest manifest = ApplicationManifest.of(productBHive, new Manifest.Key(name, tag));
        return manifest.getDescriptor();
    }

}
