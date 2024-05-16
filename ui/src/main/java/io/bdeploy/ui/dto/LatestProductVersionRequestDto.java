package io.bdeploy.ui.dto;

public class LatestProductVersionRequestDto {

    // instance group or repository name. If omitted, latest version will be searched across all repositories
    public String groupOrRepo;

    // (Required) product key (name part for Manifest.Key)
    public String key;

    // (Optional) version filter
    public String version;

    // whether version filter is a regular expression or direct match
    public boolean regex;

}
