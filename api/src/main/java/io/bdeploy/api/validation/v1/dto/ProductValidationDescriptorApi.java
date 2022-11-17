package io.bdeploy.api.validation.v1.dto;

import java.util.HashMap;
import java.util.Map;

/**
 * This descriptor references all files which should be validated during raw product validation.
 * <p>
 * Applications (`app-info.yaml` files) are not directly referenced from `product-info.yaml` files, thus this descriptor creates
 * the required association.
 */
public class ProductValidationDescriptorApi {

    /**
     * The internal file name used when packaging the {@link ProductValidationDescriptorApi} in a ZIP file to send to the server.
     * <p>
     * The input file for validation may have a different name.
     */
    public static final String FILE_NAME = "descriptor.yaml";

    /**
     * Path to the `product-info.yaml` file which defines the root product to be validated.
     */
    public String product;

    /**
     * A map of application names to their respective `app-info.yaml` files. The names should give a human an idea of which
     * application is referenced here, however they need not match the application IDs in the `product-info.yaml`. This is to
     * allow specifying the same application multiple times for different OS in case they have different descriptors per OS.
     */
    public Map<String, String> applications = new HashMap<>();

}
