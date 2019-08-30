package io.bdeploy.interfaces.manifest.state;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps track about installed and active versions of an instance.
 */
public class InstanceStateRecord {

    public String activeTag;
    public List<String> installedTags = new ArrayList<>();

    public InstanceStateRecord setActive(String tag) {
        activeTag = tag;
        return this;
    }

    public InstanceStateRecord setInstalled(String tag) {
        installedTags.add(tag);
        return this;
    }

    public InstanceStateRecord setUninstalled(String tag) {
        if (tag.equals(activeTag)) {
            activeTag = null;
        }
        installedTags.remove(tag);
        return this;
    }

}
