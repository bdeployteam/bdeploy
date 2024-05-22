package io.bdeploy.ui.utils;

import io.bdeploy.ui.dto.ProductDto;

public class ProductVersionMatchHelper {

    private ProductVersionMatchHelper() {
    }

    public static boolean matchesVersion(ProductDto dto, String version, boolean regex) {
        if (version == null || version.isBlank()) {
            return true;
        }
        if (regex) {
            return dto.key.getTag().matches(version);
        } else {
            return dto.key.getTag().equals(version);
        }
    }

}
