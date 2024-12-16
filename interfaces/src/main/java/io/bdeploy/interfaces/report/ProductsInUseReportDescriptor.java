package io.bdeploy.interfaces.report;

import java.util.Collections;
import java.util.List;

public class ProductsInUseReportDescriptor extends ReportDescriptor {

    public static final ReportParameterDescriptor INSTANCE_GROUP_PARAM = new ReportParameterDescriptor("instanceGroup",
            "Instance Group", "instance group filter", ReportParameterInputType.SELECT, Collections.emptyList(),
            "instance-groups");
    public static final ReportParameterDescriptor PRODUCT_PARAM = new ReportParameterDescriptor("product", "Product",
            "key part of product manifest", ReportParameterInputType.SELECT, List.of(INSTANCE_GROUP_PARAM.key), "products");
    public static final ReportParameterDescriptor PRODUCT_VERSION_PARAM = new ReportParameterDescriptor("productVersion",
            "Product Version", "product version filter", ReportParameterInputType.INPUT,
            List.of(INSTANCE_GROUP_PARAM.key, PRODUCT_PARAM.key), "product-versions");
    public static final ReportParameterDescriptor REGEX_PARAM = new ReportParameterDescriptor("regex", "Regular Expression",
            "flag marking that supplied productVersion parameter is regular expression", ReportParameterInputType.CHECKBOX);
    public static final ReportParameterDescriptor INSTANCE_PURPOSE_PARAM = new ReportParameterDescriptor("purpose",
            "Instance Purpose", "instance purpose filter", ReportParameterInputType.SELECT, Collections.emptyList(),
            "instance-purposes");

    public static final ReportColumnDescriptor INSTANCE_GROUP_NAME_COLUMN =//
            new ReportColumnDescriptorBuilder("Instance Group Name", "instanceGroupName").identifier().build();
    public static final ReportColumnDescriptor INSTANCE_GROUP_TITLE_COLUMN =//
            new ReportColumnDescriptorBuilder("Instance Group Title", "instanceGroupTitle").main().build();
    public static final ReportColumnDescriptor INSTANCE_GROUP_DESCRIPTION_COLUMN =//
            new ReportColumnDescriptorBuilder("Instance Group Description", "instanceGroupDescription").build();

    public static final ReportColumnDescriptor INSTANCE_ID_COLUMN =//
            new ReportColumnDescriptorBuilder("Instance ID", "instanceId").identifier().build();
    public static final ReportColumnDescriptor INSTANCE_NAME_COLUMN =//
            new ReportColumnDescriptorBuilder("Instance Name", "instanceName").main().build();
    public static final ReportColumnDescriptor PURPOSE_COLUMN =//
            new ReportColumnDescriptorBuilder("Purpose", "instancePurpose").main().build();

    public static final ReportColumnDescriptor PRODUCT_ID_COLUMN =//
            new ReportColumnDescriptorBuilder("Product ID", "productId").identifier().build();
    public static final ReportColumnDescriptor PRODUCT_NAME_COLUMN =//
            new ReportColumnDescriptorBuilder("Product Name", "productName").main().build();
    public static final ReportColumnDescriptor PRODUCT_VERSION_COLUMN =//
            new ReportColumnDescriptorBuilder("Product Version", "productVersion").main().build();
    public static final ReportColumnDescriptor ACTIVE_VERSION_COLUMN =//
            new ReportColumnDescriptorBuilder("Active Version", "activeVersion").main().build();

    public static final ReportColumnDescriptor SYSTEM_NAME_COLUMN =//
            new ReportColumnDescriptorBuilder("System Name", "systemName").identifier().build();
    public static final ReportColumnDescriptor MANAGED_SERVER_COLUMN =//
            new ReportColumnDescriptorBuilder("Managed Server", "managedServer").identifier().build();
    public static final ReportColumnDescriptor LAST_COMMUNICATION_COLUMN =//
            new ReportColumnDescriptorBuilder("Last Communication", "lastCommunication").main().build();

    public ProductsInUseReportDescriptor() {
        super(ReportType.productsInUse, "Products In Use",
                "Shows where products are used, in which version, and for what purpose.",
                List.of(INSTANCE_GROUP_PARAM, PRODUCT_PARAM, PRODUCT_VERSION_PARAM, REGEX_PARAM, INSTANCE_PURPOSE_PARAM),
                List.of(INSTANCE_GROUP_NAME_COLUMN, INSTANCE_GROUP_TITLE_COLUMN, INSTANCE_GROUP_DESCRIPTION_COLUMN,
                        INSTANCE_ID_COLUMN, INSTANCE_NAME_COLUMN, PRODUCT_ID_COLUMN, PRODUCT_NAME_COLUMN, PRODUCT_VERSION_COLUMN,
                        ACTIVE_VERSION_COLUMN, PURPOSE_COLUMN, SYSTEM_NAME_COLUMN, LAST_COMMUNICATION_COLUMN,
                        MANAGED_SERVER_COLUMN));
    }
}
