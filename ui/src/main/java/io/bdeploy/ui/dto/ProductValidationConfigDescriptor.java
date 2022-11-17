package io.bdeploy.ui.dto;

import java.util.List;
import java.util.Map;

import io.bdeploy.api.product.v1.ProductDescriptor;
import io.bdeploy.api.validation.v1.dto.ProductValidationDescriptorApi;
import io.bdeploy.interfaces.descriptor.application.ApplicationDescriptor;
import io.bdeploy.interfaces.descriptor.template.ApplicationTemplateDescriptor;
import io.bdeploy.interfaces.descriptor.template.InstanceTemplateDescriptor;
import io.bdeploy.interfaces.descriptor.template.InstanceVariableTemplateDescriptor;
import io.bdeploy.interfaces.descriptor.template.ParameterTemplateDescriptor;

public class ProductValidationConfigDescriptor {

    public ProductValidationDescriptorApi productValidation;

    public ProductDescriptor product;

    public List<ApplicationTemplateDescriptor> applicationTemplates;

    public List<InstanceTemplateDescriptor> instanceTemplates;

    public List<InstanceVariableTemplateDescriptor> instanceVariableTemplates;

    public List<ParameterTemplateDescriptor> parameterTemplates;

    public Map<String, ApplicationDescriptor> applications;

}
