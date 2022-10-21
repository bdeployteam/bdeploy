package io.bdeploy.ui.dto;

import java.util.List;
import java.util.Map;

import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration.InstancePurpose;
import io.bdeploy.interfaces.descriptor.template.SystemTemplateDescriptor;

public class SystemTemplateRequestDto {

    public String name;

    public String minion;

    public InstancePurpose purpose;

    public SystemTemplateDescriptor template;

    public List<SystemTemplateGroupMapping> groupMappings;

    public Map<String, String> templateVariableValues;

    public static class SystemTemplateGroupMapping {

        public String instanceName;

        public Manifest.Key productKey;

        public Map<String, String> groupToNode;

        public Map<String, String> templateVariableValues;

    }

}
