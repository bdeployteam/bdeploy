package io.bdeploy.ui.report;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.remote.jersey.BHiveRegistry;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration.InstancePurpose;
import io.bdeploy.interfaces.configuration.instance.InstanceGroupConfiguration;
import io.bdeploy.interfaces.manifest.InstanceGroupManifest;
import io.bdeploy.interfaces.report.ProductsInUseReportDescriptor;
import io.bdeploy.interfaces.report.ReportRequestDto;
import io.bdeploy.interfaces.report.ReportResponseDto;
import io.bdeploy.ui.api.InstanceGroupResource;
import io.bdeploy.ui.api.InstanceResource;
import io.bdeploy.ui.api.ProductResource;
import io.bdeploy.ui.dto.InstanceDto;
import io.bdeploy.ui.dto.ProductDto;
import io.bdeploy.ui.dto.SystemConfigurationDto;
import io.bdeploy.ui.utils.ProductVersionMatchHelper;

public class ProductsInUseReportGenerator implements ReportGenerator {

    private static final DateTimeFormatter UTC_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm '(UTC)'")
            .withZone(ZoneId.of("UTC"));

    private final BHiveRegistry registry;
    private final InstanceGroupResource igr;

    public ProductsInUseReportGenerator(BHiveRegistry registry, InstanceGroupResource igr) {
        this.registry = registry;
        this.igr = igr;
    }

    @Override
    public ReportResponseDto generateReport(ReportRequestDto request) {
        String productKey = request.params.get(ProductsInUseReportDescriptor.PRODUCT_PARAM.key);
        String productVersion = request.params.get(ProductsInUseReportDescriptor.PRODUCT_VERSION_PARAM.key);
        boolean regex = Boolean.parseBoolean(request.params.getOrDefault(ProductsInUseReportDescriptor.REGEX_PARAM.key, "false"));
        InstancePurpose purpose = request.params.get(ProductsInUseReportDescriptor.INSTANCE_PURPOSE_PARAM.key) == null ? null
                : InstancePurpose.valueOf(request.params.get(ProductsInUseReportDescriptor.INSTANCE_PURPOSE_PARAM.key));

        ReportResponseDto resp = new ReportResponseDto();

        for (InstanceGroupConfiguration group : getInstanceGroups(request)) {

            ProductResource pr = igr.getProductResource(group.name);
            InstanceResource ir = igr.getInstanceResource(group.name);
            List<SystemConfigurationDto> systems = igr.getSystemResource(group.name).list();

            Map<Manifest.Key, ProductDto> keyToProduct = pr.list(null).stream().collect(Collectors.toMap(p -> p.key, p -> p));

            for (InstanceDto instance : ir.list()) {
                InstanceConfiguration instanceConfiguration = instance.instanceConfiguration;
                if (productKey != null && !productKey.isBlank() && !productKey.equals(instanceConfiguration.product.getName())) {
                    continue;
                }
                ProductDto activeProduct = keyToProduct.get(instance.activeProduct);
                ProductDto currentProduct = keyToProduct.get(instanceConfiguration.product);
                boolean activeProductVersionMatch = activeProduct != null
                        && ProductVersionMatchHelper.matchesVersion(activeProduct, productVersion, regex);
                boolean currentProductVersionMatch = ProductVersionMatchHelper.matchesVersion(currentProduct, productVersion,
                        regex);
                if (!activeProductVersionMatch && !currentProductVersionMatch) {
                    continue;
                }
                if (purpose != null && purpose != instance.instanceConfiguration.purpose) {
                    continue;
                }
                Map<String, String> row = new HashMap<>();
                row.put(ProductsInUseReportDescriptor.INSTANCE_GROUP_NAME_COLUMN.key, group.name);
                row.put(ProductsInUseReportDescriptor.INSTANCE_GROUP_TITLE_COLUMN.key, group.title);
                row.put(ProductsInUseReportDescriptor.INSTANCE_GROUP_DESCRIPTION_COLUMN.key, group.description);

                row.put(ProductsInUseReportDescriptor.INSTANCE_ID_COLUMN.key, instanceConfiguration.id);
                row.put(ProductsInUseReportDescriptor.INSTANCE_NAME_COLUMN.key, instanceConfiguration.name);
                row.put(ProductsInUseReportDescriptor.PURPOSE_COLUMN.key, instanceConfiguration.purpose.name());

                if (currentProduct != null) {
                    row.put(ProductsInUseReportDescriptor.PRODUCT_ID_COLUMN.key, currentProduct.product);
                    row.put(ProductsInUseReportDescriptor.PRODUCT_NAME_COLUMN.key, currentProduct.name);
                    row.put(ProductsInUseReportDescriptor.PRODUCT_VERSION_COLUMN.key, currentProduct.key.getTag());
                }

                if (activeProduct != null) {
                    row.put(ProductsInUseReportDescriptor.ACTIVE_VERSION_COLUMN.key, activeProduct.key.getTag());
                }

                if (instanceConfiguration.system != null) {
                    var system = systems.stream().filter(s -> s.key.equals(instanceConfiguration.system)).findAny().orElseThrow();
                    row.put(ProductsInUseReportDescriptor.SYSTEM_NAME_COLUMN.key, system.config.name);
                }
                if (instance.managedServer != null) {
                    row.put(ProductsInUseReportDescriptor.MANAGED_SERVER_COLUMN.key, instance.managedServer.hostName);

                    // (last sync & last message: take date that is more recent)
                    String lastComm = Stream.of(instance.managedServer.lastMessageReceived, instance.managedServer.lastSync)
                            .filter(v -> v != null).max(Instant::compareTo).map(UTC_FORMATTER::format).orElse(null);
                    row.put(ProductsInUseReportDescriptor.LAST_COMMUNICATION_COLUMN.key, lastComm);
                }
                resp.rows.add(row);
            }
        }

        resp.requestParams = request.params;

        resp.generatedAt = UTC_FORMATTER.format(Instant.now());

        return resp;
    }

    private List<InstanceGroupConfiguration> getInstanceGroups(ReportRequestDto request) {
        String instanceGroup = request.params.get(ProductsInUseReportDescriptor.INSTANCE_GROUP_PARAM.key);
        boolean instanceGroupSet = instanceGroup != null && !instanceGroup.isBlank();

        List<InstanceGroupConfiguration> result = new ArrayList<>();
        for (BHive hive : registry.getAll().values()) {
            InstanceGroupConfiguration group = new InstanceGroupManifest(hive).read();

            if (group == null || (instanceGroupSet && !instanceGroup.equals(group.name))) {
                continue;
            }

            result.add(group);
        }
        return result;
    }

}
