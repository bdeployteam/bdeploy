package io.bdeploy.minion.remote.jersey;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.inject.Inject;

import io.bdeploy.interfaces.configuration.SettingsConfiguration;
import io.bdeploy.interfaces.remote.MasterSettingsResource;
import io.bdeploy.interfaces.settings.CustomAttributeDescriptor;
import io.bdeploy.ui.api.Minion;

public class MasterSettingsResourceImpl implements MasterSettingsResource {

    @Inject
    private Minion root;

    @Override
    public SettingsConfiguration getSettings() {
        return root.getSettings();
    }

    @Override
    public void setSettings(SettingsConfiguration settings) {
        root.setSettings(settings);
    }

    @Override
    public void mergeInstanceGroupAttributesDescriptors(List<CustomAttributeDescriptor> attributes) {

        SettingsConfiguration settings = root.getSettings();

        boolean changed = false;
        Map<String, CustomAttributeDescriptor> pMap = settings.instanceGroup.attributes.stream()
                .collect(Collectors.toMap(p -> p.name, p -> p));
        for (CustomAttributeDescriptor a : attributes) {
            CustomAttributeDescriptor existing = pMap.get(a.name);
            if (!a.equals(existing)) { // != or null
                pMap.put(a.name, a);
                changed = true;
            }
        }
        if (changed) {
            settings.instanceGroup.attributes = pMap.values().stream().sorted((a, b) -> a.name.compareTo(b.name))
                    .collect(Collectors.toList());
            root.setSettings(settings);
        }
    }
}
