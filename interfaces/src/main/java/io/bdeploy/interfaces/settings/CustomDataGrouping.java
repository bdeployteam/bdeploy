package io.bdeploy.interfaces.settings;

import java.util.ArrayList;
import java.util.List;

/**
 * Used to store grouping information for global presets
 */
public class CustomDataGrouping {

    /**
     * Name of the instance attribute definition {@link CustomAttributeDescriptor} used for grouping
     */
    public String name;

    /**
     * The selected groups to show
     */
    public List<String> selected = new ArrayList<>();

}
