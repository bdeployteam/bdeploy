package io.bdeploy.ui.dto;

public class LatestProductVersionRequestDto {

    // instance group or repository name
    public String groupOrRepo;

    // product key (name part for Manifest.Key)
    public String key;

    // product id (product attribute specified in the product-info.yaml file)
    public String productId;

    // version filter
    public String version;

    // whether version filter is a regular expression or direct match
    public boolean regex;

}
