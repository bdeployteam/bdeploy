package io.bdeploy.interfaces.configuration.template;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

import io.bdeploy.common.util.TemplateHelper;
import io.bdeploy.interfaces.configuration.TemplateableVariableConfiguration;
import io.bdeploy.interfaces.configuration.TemplateableVariableDefaultConfiguration;
import io.bdeploy.interfaces.configuration.VariableConfiguration;
import io.bdeploy.interfaces.configuration.dcu.LinkedValueConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceVariableConfiguration;
import io.bdeploy.interfaces.descriptor.template.ApplicationTemplateDescriptor;
import io.bdeploy.interfaces.descriptor.template.InstanceTemplateControlGroup;
import io.bdeploy.interfaces.descriptor.template.InstanceTemplateDescriptor;
import io.bdeploy.interfaces.descriptor.template.InstanceVariableTemplateDescriptor;
import io.bdeploy.interfaces.descriptor.template.TemplateVariable;
import io.bdeploy.interfaces.descriptor.variable.VariableDescriptor;

/**
 * Represents a flattened and cacheable version of an {@link InstanceTemplateDescriptor}.
 */
public class FlattenedInstanceTemplateConfiguration {

    private static final Logger log = LoggerFactory.getLogger(FlattenedInstanceTemplateConfiguration.class);

    public String name;
    public String description;
    public Boolean autoStart;
    public Boolean autoUninstall;
    public List<VariableConfiguration> instanceVariables;
    public List<InstanceVariableConfiguration> instanceVariableValues;
    public List<InstanceTemplateControlGroup> processControlGroups;
    public List<FlattenedInstanceTemplateGroupConfiguration> groups;

    public List<TemplateVariable> directlyUsedTemplateVars = new ArrayList<>();

    FlattenedInstanceTemplateConfiguration() {
        // intentionally left empty for deserialization.
    }

    public FlattenedInstanceTemplateConfiguration(InstanceTemplateDescriptor original,
            List<InstanceVariableTemplateDescriptor> varTpl, List<ApplicationTemplateDescriptor> appTpl,
            List<VariableDescriptor> instanceVariableDescriptors) {
        this.name = original.name;
        this.description = original.description;
        this.autoStart = original.autoStart != null ? original.autoStart : false;
        this.autoUninstall = original.autoUninstall != null ? original.autoUninstall : true;
        this.processControlGroups = original.processControlGroups;
        this.groups = original.groups.stream()
                .map(g -> new FlattenedInstanceTemplateGroupConfiguration(g, appTpl, original.templateVariables)).filter(g -> {
                    if (g.applications.isEmpty()) {
                        log.warn("Ignoring empty instance template group {}", g.name);
                        return false;
                    }
                    return true;
                }).toList();

        resolveInstanceVariablesAndTemplateVariables(varTpl, original.instanceVariables, original.instanceVariableDefaults,
                original.templateVariables, instanceVariableDescriptors, original.instanceVariableValues);
    }

    private void resolveInstanceVariablesAndTemplateVariables(List<InstanceVariableTemplateDescriptor> templates,
            List<TemplateableVariableConfiguration> instanceVariables,
            List<TemplateableVariableDefaultConfiguration> instanceVariableDefaults, List<TemplateVariable> templateVars,
            List<VariableDescriptor> instanceVariableDescriptors, List<InstanceVariableConfiguration> instanceVariableValues) {
        TemplateableVariableConfiguration toReplace = null;
        List<TemplateableVariableConfiguration> vars = new ArrayList<>(instanceVariables);
        do {
            // find element which has a 'template' attribute set
            toReplace = vars.stream().filter(v -> v.template != null).findFirst().orElse(null);
            if (toReplace != null) {
                // replace/expand it. remove the placeholder element.
                int index = vars.indexOf(toReplace);
                vars.remove(index);

                // find replacements by looking up the first template with matching id, and map to its elements
                String templateId = toReplace.template;
                List<TemplateableVariableConfiguration> replacements = templates.stream().filter(t -> t.id.equals(templateId))
                        .map(t -> t.instanceVariables).findFirst().orElse(null);

                if (replacements == null) {
                    log.warn("No instance variable template found for {}", templateId);
                } else {
                    // of the given replacements, insert those whose id is not yet present in the variable list.
                    ImmutableList.copyOf(replacements).reverse().stream()
                            .filter(p -> vars.stream().noneMatch(x -> x.id != null && x.id.equals(p.id)))
                            .forEach(r -> vars.add(index, new TemplateableVariableConfiguration(r)));
                }
            }
        } while (toReplace != null);

        // now that all are resolved, fixup any default value overrides from the instance template.
        if (instanceVariableDefaults != null && !instanceVariableDefaults.isEmpty()) {
            for (var def : instanceVariableDefaults) {
                var variable = vars.stream().filter(x -> x.id.equals(def.id)).findFirst();
                if (variable.isEmpty()) {
                    log.warn("Variable not found while applying override: {}", def.id);
                } else {
                    variable.get().value = def.value;
                }
            }
        }

        // determine which variables are *actually* used, and only provide those in the end.
        TrackingTemplateOverrideResolver res = new TrackingTemplateOverrideResolver(Collections.emptyList()); // no overrides on this level.
        this.instanceVariables = vars.stream().map(v -> new VariableConfiguration(v.id,
                v.value == null ? null
                        : new LinkedValueConfiguration(TemplateHelper.process(v.value.getPreRenderable(), res, res::canResolve)),
                v.description, v.type, v.customEditor)).toList();

        // we allow template variables to be used directly in the template YAML in various places, especially when configuring applications.
        for (var group : this.groups) {
            for (var app : group.applications) {
                TemplateHelper.process(app.name, res, res::canResolve);
                TemplateHelper.process(app.description, res, res::canResolve);
                for (var param : app.startParameters) {
                    TemplateHelper.process(param.value, res, res::canResolve);
                }
            }
        }

        // we allow template variables to be used in instanceVariableValues overrides
        this.instanceVariableValues = new ArrayList<>();
        if (instanceVariableValues != null && instanceVariableDescriptors != null) {
            for (var ivv : instanceVariableValues) {
                var ivd = instanceVariableDescriptors.stream().filter(d -> d.id.equals(ivv.id)).findFirst();
                if (ivd.isEmpty()) {
                    log.warn("Corresponding instance variable descriptor not found. Skipping instance variable value: {}",
                            ivv.id);
                } else {
                    TemplateHelper.process(ivv.value.getPreRenderable(), res, res::canResolve);
                    this.instanceVariableValues.add(ivv);
                }
            }
        }

        this.directlyUsedTemplateVars = templateVars.stream().filter(Objects::nonNull)
                .filter(variable -> res.getRequestedVariables().contains(variable.id)).toList();
    }

}
