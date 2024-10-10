package io.bdeploy.interfaces.report;

import java.util.List;

public class ProductsInUseReportDescriptor extends ReportDescriptor {

    public static final String INSTANCE_GROUP_PARAM_KEY = "instanceGroup";
    public static final String PRODUCT_PARAM_KEY = "product";
    public static final String PRODUCT_VERSION_PARAM_KEY = "productVersion";
    public static final String REGEX_PARAM_KEY = "regex";
    public static final String INSTANCE_PURPOSE_PARAM_KEY = "purpose";

    public static final ReportColumnDescriptor INSTANCE_GROUP_NAME_COLUMN = new ReportColumnDescriptor("instance group name",
            "instanceGroupName", 10, true);
    public static final ReportColumnDescriptor INSTANCE_GROUP_DESCRIPTION_COLUMN = new ReportColumnDescriptor(
            "instance group description", "instanceGroupDescription", 0, false);
    public static final ReportColumnDescriptor INSTANCE_UUID_COLUMN = new ReportColumnDescriptor("instance uuid", "instanceUuid",
            10, true);
    public static final ReportColumnDescriptor INSTANCE_NAME_COLUMN = new ReportColumnDescriptor("instance name", "instanceName",
            10, true);
    public static final ReportColumnDescriptor INSTANCE_VERSION_COLUMN = new ReportColumnDescriptor("instance version",
            "instanceVersion", 10, true);
    public static final ReportColumnDescriptor PRODUCT_COLUMN = new ReportColumnDescriptor("product", "product", 10, true);
    public static final ReportColumnDescriptor PRODUCT_VERSION_COLUMN = new ReportColumnDescriptor("product version",
            "productVersion", 10, true);
    public static final ReportColumnDescriptor ACTIVE_VERSION_COLUMN = new ReportColumnDescriptor("active version",
            "activeVersion", 10, true);
    public static final ReportColumnDescriptor PURPOSE_COLUMN = new ReportColumnDescriptor("purpose", "instancePurpose", 0,
            false);
    public static final ReportColumnDescriptor SYSTEM_COLUMN = new ReportColumnDescriptor("system", "system", 0, false);
    public static final ReportColumnDescriptor MANAGED_SERVER_COLUMN = new ReportColumnDescriptor("managed server",
            "managedServer", 0, false);
    public static final ReportColumnDescriptor LAST_COMMUNICATION_COLUMN = new ReportColumnDescriptor("last communication",
            "lastCommunication", 0, false); // (last sync & last message: take date that is more recent)

    public ProductsInUseReportDescriptor() {
        super(ReportType.productsInUse, "Products In Use", "Display Products In Use",
                List.of(new ReportParameterDescriptor(INSTANCE_GROUP_PARAM_KEY, "Instance Group", "instance group filter",
                        ReportParameterType.INSTANCE_GROUP, false),
                        new ReportParameterDescriptor(PRODUCT_PARAM_KEY, "Product", "key part of product manifest",
                                ReportParameterType.PRODUCT_KEY, false, INSTANCE_GROUP_PARAM_KEY),
                        new ReportParameterDescriptor(PRODUCT_VERSION_PARAM_KEY, "Product Version", "product version filter",
                                ReportParameterType.STRING, false),
                        new ReportParameterDescriptor(REGEX_PARAM_KEY, "Regular Expression",
                                "flag marking that supplied productVersion parameter is regular expression",
                                ReportParameterType.BOOLEAN, false),
                        new ReportParameterDescriptor(INSTANCE_PURPOSE_PARAM_KEY, "Instance Purpose", "instance purpose filter",
                                ReportParameterType.INSTANCE_PURPOSE, false)),
                List.of(INSTANCE_GROUP_NAME_COLUMN, INSTANCE_GROUP_DESCRIPTION_COLUMN, INSTANCE_UUID_COLUMN, INSTANCE_NAME_COLUMN,
                        INSTANCE_VERSION_COLUMN, PRODUCT_COLUMN, PRODUCT_VERSION_COLUMN, ACTIVE_VERSION_COLUMN, PURPOSE_COLUMN,
                        SYSTEM_COLUMN, MANAGED_SERVER_COLUMN, LAST_COMMUNICATION_COLUMN));
    }
}
