package io.bdeploy.ui.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.bdeploy.interfaces.descriptor.template.SystemTemplateDescriptor;
import io.bdeploy.interfaces.minion.MinionStatusDto;

public class SystemTemplateDto {

    public SystemTemplateDescriptor template;

    public List<ProductDto> products = new ArrayList<>();

    public Map<String, MinionStatusDto> nodes;

}
