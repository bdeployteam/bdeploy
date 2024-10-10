package io.bdeploy.interfaces.report;

import java.util.Collections;
import java.util.List;

public class ProductsInUseReportDescriptor extends ReportDescriptor {

    public static final String INSTANCE_GROUP_PARAM_KEY = "instanceGroup";
    public static final String PRODUCT_PARAM_KEY = "product";
    public static final String PRODUCT_VERSION_PARAM_KEY = "productVersion";
    public static final String REGEX_PARAM_KEY = "regex";
    public static final String INSTANCE_PURPOSE_PARAM_KEY = "purpose";

    public static final ReportColumnDescriptor INSTANCE_GROUP_NAME_COLUMN = new ReportColumnDescriptor("Instance Group",
            "instanceGroupName", false, 10, true);
    public static final ReportColumnDescriptor INSTANCE_GROUP_DESCRIPTION_COLUMN = new ReportColumnDescriptor(
            "Instance Group Description", "instanceGroupDescription", true, 0, false);

    public static final ReportColumnDescriptor INSTANCE_UUID_COLUMN = new ReportColumnDescriptor("Instance UUID", "instanceUuid",
            false, 10, true);
    public static final ReportColumnDescriptor INSTANCE_NAME_COLUMN = new ReportColumnDescriptor("Instance", "instanceName", true,
            10, true);
    public static final ReportColumnDescriptor PURPOSE_COLUMN = new ReportColumnDescriptor("Purpose", "instancePurpose", true, 0,
            false);

    public static final ReportColumnDescriptor PRODUCT_ID_COLUMN = new ReportColumnDescriptor("Product ID", "productId", false, 0,
            true);
    public static final ReportColumnDescriptor PRODUCT_COLUMN = new ReportColumnDescriptor("Product", "product", true, 10, true);
    public static final ReportColumnDescriptor PRODUCT_VERSION_COLUMN = new ReportColumnDescriptor("Product Version",
            "productVersion", true, 10, true);
    public static final ReportColumnDescriptor ACTIVE_VERSION_COLUMN = new ReportColumnDescriptor("Active Version",
            "activeVersion", true, 10, true);

    public static final ReportColumnDescriptor SYSTEM_COLUMN = new ReportColumnDescriptor("System", "system", false, 0, false);
    public static final ReportColumnDescriptor MANAGED_SERVER_COLUMN = new ReportColumnDescriptor("Managed Server",
            "managedServer", false, 0, false);
    public static final ReportColumnDescriptor LAST_COMMUNICATION_COLUMN = new ReportColumnDescriptor("Last Communication",
            "lastCommunication", true, 0, false);

    public ProductsInUseReportDescriptor() {
        super(ReportType.productsInUse, "Products In Use",
                "Shows where products are used, in which version, and for what purpose.",
                List.of(new ReportParameterDescriptor(INSTANCE_GROUP_PARAM_KEY, "Instance Group", "instance group filter",
                        ReportParameterInputType.SELECT, Collections.emptyList(), "instance-groups"),
                        new ReportParameterDescriptor(PRODUCT_PARAM_KEY, "Product", "key part of product manifest",
                                ReportParameterInputType.SELECT, List.of(INSTANCE_GROUP_PARAM_KEY), "products"),
                        new ReportParameterDescriptor(PRODUCT_VERSION_PARAM_KEY, "Product Version", "product version filter",
                                ReportParameterInputType.INPUT, List.of(INSTANCE_GROUP_PARAM_KEY, PRODUCT_PARAM_KEY),
                                "product-versions"),
                        new ReportParameterDescriptor(REGEX_PARAM_KEY, "Regular Expression",
                                "flag marking that supplied productVersion parameter is regular expression",
                                ReportParameterInputType.CHECKBOX),
                        new ReportParameterDescriptor(INSTANCE_PURPOSE_PARAM_KEY, "Instance Purpose", "instance purpose filter",
                                ReportParameterInputType.SELECT, Collections.emptyList(), "instance-purposes")),
                List.of(INSTANCE_GROUP_DESCRIPTION_COLUMN, INSTANCE_NAME_COLUMN, PRODUCT_COLUMN, PRODUCT_VERSION_COLUMN,
                        ACTIVE_VERSION_COLUMN, PURPOSE_COLUMN, LAST_COMMUNICATION_COLUMN, INSTANCE_GROUP_NAME_COLUMN,
                        SYSTEM_COLUMN, PRODUCT_ID_COLUMN, INSTANCE_UUID_COLUMN, MANAGED_SERVER_COLUMN));
    }
}
