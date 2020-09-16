package io.bdeploy.minion.remote.jersey;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.inject.Inject;

import io.bdeploy.interfaces.configuration.SettingsConfiguration;
import io.bdeploy.interfaces.remote.MasterSettingsResource;
import io.bdeploy.interfaces.settings.CustomPropertyDescriptor;
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
    public void mergeInstanceGroupPropertyDescriptors(List<CustomPropertyDescriptor> properties) {

        SettingsConfiguration settings = root.getSettings();

        boolean changed = false;
        Map<String, CustomPropertyDescriptor> pMap = settings.instanceGroup.properties.stream()
                .collect(Collectors.toMap(p -> p.name, p -> p));
        for (CustomPropertyDescriptor p : properties) {
            CustomPropertyDescriptor existing = pMap.get(p.name);
            if (!p.equals(existing)) { // != or null
                pMap.put(p.name, p);
                changed = true;
            }
        }
        if (changed) {
            settings.instanceGroup.properties = pMap.values().stream().sorted((a, b) -> a.name.compareTo(b.name))
                    .collect(Collectors.toList());
            root.setSettings(settings);
        }
    }
}
