package io.bdeploy.ui.api.impl;

import java.util.List;
import java.util.SortedSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.interfaces.descriptor.application.ApplicationDescriptor;
import io.bdeploy.interfaces.manifest.ApplicationManifest;
import io.bdeploy.interfaces.manifest.ProductManifest;
import io.bdeploy.ui.api.ApplicationResource;
import io.bdeploy.ui.dto.ApplicationDto;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response.Status;

public class ApplicationResourceImpl implements ApplicationResource {

    private static final Logger log = LoggerFactory.getLogger(ApplicationResourceImpl.class);

    private final BHive productBHive;
    private final Manifest.Key productKey;

    public ApplicationResourceImpl(BHive productBHive, Manifest.Key productKey) {
        this.productBHive = productBHive;
        this.productKey = productKey;
    }

    @Override
    public List<ApplicationDto> list() {
        try {
            ProductManifest productManifest = ProductManifest.of(productBHive, productKey);
            SortedSet<Key> applications = productManifest.getApplications();
            return applications.stream().map(k -> ApplicationManifest.of(productBHive, k)).map(mf -> {
                ApplicationDto descriptor = new ApplicationDto();
                descriptor.key = mf.getKey();
                descriptor.name = mf.getDescriptor().name;
                descriptor.descriptor = mf.getDescriptor();
                return descriptor;
            }).toList();
        } catch (Exception e) {
            if (log.isDebugEnabled()) {
                log.debug("Cannot load product {}", productKey, e);
            }
            throw new WebApplicationException("Cannot load product " + productKey, Status.NOT_FOUND);
        }
    }

    @Override
    public ApplicationDescriptor getDescriptor(String name, String tag) {
        ApplicationManifest manifest = ApplicationManifest.of(productBHive, new Manifest.Key(name, tag));
        return manifest.getDescriptor();
    }

}
