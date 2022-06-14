package io.bdeploy.ui.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents the recursive directory tree found in the config tree.
 */
public class ConfigDirDto {

    public String name;
    public List<ConfigDirDto> children = new ArrayList<>();

}
