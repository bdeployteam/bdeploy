package io.bdeploy.ui.api.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.remote.jersey.BHiveRegistry;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration.InstancePurpose;
import io.bdeploy.interfaces.configuration.instance.InstanceGroupConfiguration;
import io.bdeploy.interfaces.manifest.InstanceGroupManifest;
import io.bdeploy.interfaces.manifest.ProductManifest;
import io.bdeploy.interfaces.manifest.SoftwareRepositoryManifest;
import io.bdeploy.interfaces.report.ReportType;
import io.bdeploy.ui.api.ReportParameterOptionResource;
import io.bdeploy.ui.dto.ReportParameterOptionDto;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response.Status;

public class ReportParameterOptionResourceImpl implements ReportParameterOptionResource {

    @Inject
    private BHiveRegistry registry;

    @Context
    private ResourceContext rc;

    private final ReportType report;

    public ReportParameterOptionResourceImpl(ReportType report) {
        this.report = report;
    }

    @Override
    public List<ReportParameterOptionDto> getInstancePurposes() {
        if (report != ReportType.productsInUse) {
            throw new WebApplicationException("Cannot fetch instance purposes for report " + report, Status.BAD_REQUEST);
        }
        return Arrays.stream(InstancePurpose.values()).map(p -> new ReportParameterOptionDto(p.name(), p.name())).toList();
    }

    @Override
    public List<ReportParameterOptionDto> getInstanceGroups() {
        if (report != ReportType.productsInUse) {
            throw new WebApplicationException("Cannot fetch instance groups for report " + report, Status.BAD_REQUEST);
        }
        List<ReportParameterOptionDto> result = new ArrayList<>();
        for (BHive hive : registry.getAll().values()) {
            InstanceGroupConfiguration group = new InstanceGroupManifest(hive).read();
            if (group != null) {
                result.add(new ReportParameterOptionDto(group.name));
            }
        }
        return result;
    }

    @Override
    public List<ReportParameterOptionDto> getProducts(String instanceGroup) {
        if (report != ReportType.productsInUse) {
            throw new WebApplicationException("Cannot fetch products for report " + report, Status.BAD_REQUEST);
        }
        List<ReportParameterOptionDto> result = new ArrayList<>();
        Set<String> products = new HashSet<>();
        List<BHive> hives = instanceGroup == null || instanceGroup.isBlank() ? softwareRepositoryHives()
                : List.of(getInstanceGroupHive(instanceGroup));
        for (BHive hive : hives) {
            SortedSet<Key> scan = ProductManifest.scan(hive);
            for (Key key : scan) {
                if (!products.contains(key.getName())) {
                    products.add(key.getName());
                    ProductManifest manifest = ProductManifest.of(hive, key);
                    result.add(new ReportParameterOptionDto(manifest.getProductDescriptor().name, key.getName()));
                }
            }
        }
        return result;
    }

    @Override
    public List<ReportParameterOptionDto> getProductsVersions(String instanceGroup, String product) {
        if (product == null || product.isBlank()) {
            return Collections.emptyList();
        }
        List<BHive> hives = instanceGroup == null || instanceGroup.isBlank() ? softwareRepositoryHives()
                : List.of(getInstanceGroupHive(instanceGroup));
        Set<String> versions = new HashSet<>();
        for (BHive hive : hives) {
            SortedSet<Key> scan = ProductManifest.scan(hive);
            for (Key key : scan) {
                if (product.equals(key.getName())) {
                    versions.add(key.getTag());
                }
            }
        }
        return versions.stream().map((v) -> new ReportParameterOptionDto(v)).toList();
    }

    private List<BHive> softwareRepositoryHives() {
        return registry.getAll().values().stream().filter(hive -> new SoftwareRepositoryManifest(hive).read() != null).toList();
    }

    private BHive getInstanceGroupHive(String group) {
        BHive hive = registry.get(group);
        if (hive == null || new InstanceGroupManifest(hive).read() == null) {
            throw new WebApplicationException(Status.NOT_FOUND);
        }
        return hive;
    }

}
