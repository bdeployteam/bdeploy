package io.bdeploy.ui.dto;

import java.util.HashMap;
import java.util.Map;

public class ProductValidationDescriptor {

    public static final String FILE_NAME = "descriptor.yaml";

    public String product;

    public Map<String, String> applications = new HashMap<>();

}
