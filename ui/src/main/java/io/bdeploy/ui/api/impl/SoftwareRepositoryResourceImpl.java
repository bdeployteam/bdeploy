package io.bdeploy.ui.api.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response.Status;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.remote.jersey.BHiveRegistry;
import io.bdeploy.interfaces.configuration.instance.SoftwareRepositoryConfiguration;
import io.bdeploy.interfaces.manifest.SoftwareRepositoryManifest;
import io.bdeploy.ui.api.SoftwareRepositoryResource;
import io.bdeploy.ui.api.SoftwareResource;

public class SoftwareRepositoryResourceImpl implements SoftwareRepositoryResource {

    @Context
    private ResourceContext rc;

    @Inject
    private BHiveRegistry registry;

    @Override
    public List<SoftwareRepositoryConfiguration> list() {
        List<SoftwareRepositoryConfiguration> result = new ArrayList<>();
        for (Map.Entry<String, BHive> entry : registry.getAll().entrySet()) {
            SoftwareRepositoryConfiguration cfg = new SoftwareRepositoryManifest(entry.getValue()).read();
            if (cfg != null) {
                result.add(cfg);
            }
        }
        return result;
    }

    @Override
    public SoftwareResource getSoftwareResource(String softwareRepository) {
        return rc.initResource(new SoftwareResourceImpl(getSoftwareRepositoryHive(softwareRepository)));
    }

    private BHive getSoftwareRepositoryHive(String softwareRepository) {
        BHive hive = registry.get(softwareRepository);
        if (hive == null) {
            throw new WebApplicationException(Status.NOT_FOUND);
        }
        return hive;
    }

}
