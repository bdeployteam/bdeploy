package io.bdeploy.interfaces.manifest.state;

import java.util.Set;
import java.util.TreeSet;

/**
 * Keeps track about installed and active versions of an instance.
 */
public class InstanceStateRecord {

    public String activeTag;
    public String lastActiveTag;
    public Set<String> installedTags = new TreeSet<>();

    public InstanceStateRecord setActive(String tag) {
        lastActiveTag = activeTag;
        activeTag = tag;
        return this;
    }

    public InstanceStateRecord setInstalled(String tag) {
        installedTags.add(tag);
        return this;
    }

    public InstanceStateRecord setUninstalled(String tag) {
        if (tag.equals(activeTag)) {
            lastActiveTag = activeTag;
            activeTag = null;
        }
        installedTags.remove(tag);
        return this;
    }

}
