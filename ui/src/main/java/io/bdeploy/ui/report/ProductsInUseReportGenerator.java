package io.bdeploy.ui.report;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import io.bdeploy.bhive.BHive;
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
import io.bdeploy.ui.dto.InstanceUsageDto;
import io.bdeploy.ui.dto.ProductDto;
import io.bdeploy.ui.dto.SystemConfigurationDto;
import io.bdeploy.ui.utils.ProductVersionMatchHelper;

public class ProductsInUseReportGenerator implements ReportGenerator {

    private final BHiveRegistry registry;
    private final InstanceGroupResource igr;

    public ProductsInUseReportGenerator(BHiveRegistry registry, InstanceGroupResource igr) {
        this.registry = registry;
        this.igr = igr;
    }

    @Override
    public ReportResponseDto generateReport(ReportRequestDto request) {
        String productKey = request.params.get(ProductsInUseReportDescriptor.PRODUCT_PARAM_KEY);
        String productVersion = request.params.get(ProductsInUseReportDescriptor.PRODUCT_VERSION_PARAM_KEY);
        boolean regex = Boolean.valueOf(request.params.getOrDefault(ProductsInUseReportDescriptor.REGEX_PARAM_KEY, "false"));
        InstancePurpose purpose = request.params.get(ProductsInUseReportDescriptor.INSTANCE_PURPOSE_PARAM_KEY) == null ? null
                : InstancePurpose.valueOf(request.params.get(ProductsInUseReportDescriptor.INSTANCE_PURPOSE_PARAM_KEY));

        ReportResponseDto resp = new ReportResponseDto();

        for (InstanceGroupConfiguration group : getInstanceGroups(request)) {

            ProductResource pr = igr.getProductResource(group.name);
            InstanceResource ir = igr.getInstanceResource(group.name);
            List<SystemConfigurationDto> systems = igr.getSystemResource(group.name).list();
            List<InstanceDto> instances = igr.getInstanceResource(group.name).list();

            for (ProductDto product : pr.list(productKey)) {
                if (!ProductVersionMatchHelper.matchesVersion(product, productVersion, regex)) {
                    continue;
                }

                for (InstanceUsageDto instanceUsageDto : pr.getProductUsedIn(product.key.getName(), product.key.getTag())) {
                    InstanceConfiguration instanceVersion = ir.readVersion(instanceUsageDto.id, instanceUsageDto.tag);
                    InstanceDto instance = instances.stream().filter(i -> i.instanceConfiguration.id.equals(instanceUsageDto.id))
                            .findAny().orElseThrow();
                    if (purpose != null && purpose != instanceVersion.purpose) {
                        continue;
                    }
                    Map<String, String> row = new HashMap<>();
                    row.put(ProductsInUseReportDescriptor.INSTANCE_GROUP_NAME_COLUMN.key, group.name);
                    row.put(ProductsInUseReportDescriptor.INSTANCE_GROUP_DESCRIPTION_COLUMN.key, group.description);
                    row.put(ProductsInUseReportDescriptor.INSTANCE_UUID_COLUMN.key, instanceUsageDto.id);
                    row.put(ProductsInUseReportDescriptor.INSTANCE_NAME_COLUMN.key, instanceUsageDto.name);
                    row.put(ProductsInUseReportDescriptor.INSTANCE_VERSION_COLUMN.key, instanceUsageDto.tag);
                    row.put(ProductsInUseReportDescriptor.PRODUCT_COLUMN.key, product.key.getName());
                    row.put(ProductsInUseReportDescriptor.PRODUCT_VERSION_COLUMN.key, product.key.getTag());
                    row.put(ProductsInUseReportDescriptor.ACTIVE_VERSION_COLUMN.key,
                            instance.activeVersion == null ? null : instance.activeVersion.toString());
                    row.put(ProductsInUseReportDescriptor.PURPOSE_COLUMN.key, instanceVersion.purpose.name());
                    if (instanceVersion.system != null) {
                        row.put(ProductsInUseReportDescriptor.SYSTEM_COLUMN.key, systems.stream()
                                .filter(s -> s.key.equals(instanceVersion.system)).findAny().orElseThrow().config.name);
                    }
                    if (instance.managedServer != null) {
                        row.put(ProductsInUseReportDescriptor.MANAGED_SERVER_COLUMN.key, instance.managedServer.hostName);
                        String lastComm = Stream.of(instance.managedServer.lastMessageReceived, instance.managedServer.lastSync)
                                .filter(v -> v != null).max(Instant::compareTo).map(v -> v.toString()).orElse(null);
                        row.put(ProductsInUseReportDescriptor.LAST_COMMUNICATION_COLUMN.key, lastComm);
                    }
                    resp.rows.add(row);
                }
            }
        }
        return resp;
    }

    private List<InstanceGroupConfiguration> getInstanceGroups(ReportRequestDto request) {
        String instanceGroup = request.params.get(ProductsInUseReportDescriptor.INSTANCE_GROUP_PARAM_KEY);
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
