package io.bdeploy.interfaces.configuration.template;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.bdeploy.common.util.OsHelper.OperatingSystem;
import io.bdeploy.common.util.TemplateHelper;
import io.bdeploy.interfaces.descriptor.template.ApplicationTemplateDescriptor;
import io.bdeploy.interfaces.descriptor.template.TemplateApplication;
import io.bdeploy.interfaces.descriptor.template.TemplateParameter;
import io.bdeploy.interfaces.descriptor.template.TemplateVariable;
import io.bdeploy.interfaces.descriptor.template.TemplateVariableFixedValueOverride;

/**
 * Represents a cacheable, flattened version of a {@link ApplicationTemplateDescriptor} or {@link TemplateApplication}.
 */
public class FlattenedApplicationTemplateConfiguration {

    public String name;

    public String processName;

    public String application;

    public String description;

    public String preferredProcessControlGroup;

    public List<OperatingSystem> applyOn = new ArrayList<>();

    public Map<String, Object> processControl;

    public List<TemplateVariable> templateVariables = new ArrayList<>();

    public List<TemplateParameter> startParameters = new ArrayList<>();

    @JsonIgnore
    private List<String> children;

    FlattenedApplicationTemplateConfiguration() {
        // intentionally left empty for deserialization.
    }

    public FlattenedApplicationTemplateConfiguration(TemplateApplication original, List<ApplicationTemplateDescriptor> appTpl,
            List<TemplateVariable> tplVar) {
        this(original, appTpl, tplVar, Collections.emptyList(), Collections.emptyList());
    }

    private FlattenedApplicationTemplateConfiguration(TemplateApplication original, List<ApplicationTemplateDescriptor> appTpl,
            List<TemplateVariable> instTplVar, List<TemplateVariableFixedValueOverride> overrides, List<String> children) {
        this.name = original.name;
        this.processName = original.processName;
        this.application = original.application;
        this.description = original.description;
        this.preferredProcessControlGroup = original.preferredProcessControlGroup;
        this.processControl = original.processControl;
        this.applyOn = new ArrayList<>(original.applyOn);
        this.children = new ArrayList<>(children);

        if (this.name != null) {
            if (this.children.contains(this.name)) {
                throw new IllegalStateException("Circular reference in application templates, while processing: " + this.name);
            }
            this.children.add(this.name);
        }

        resolveRecursive(original, appTpl, instTplVar, resolveOverrides(overrides, original.fixedVariables));
    }

    private static List<TemplateVariableFixedValueOverride> resolveOverrides(List<TemplateVariableFixedValueOverride> fromOutside,
            List<TemplateVariableFixedValueOverride> fromSelf) {

        // the overrides from the outside always take precedence
        List<TemplateVariableFixedValueOverride> result = new ArrayList<>(fromOutside);

        // add our "self" overrides which are not already present.
        fromSelf.stream().filter(notPresentByKey(result, t -> t.id)).forEach(result::add);

        return result;
    }

    private void resolveRecursive(TemplateApplication original, List<ApplicationTemplateDescriptor> appTemplates,
            List<TemplateVariable> instTplVar, List<TemplateVariableFixedValueOverride> overrides) {

        List<TemplateVariable> variables;
        if (original instanceof ApplicationTemplateDescriptor appTplDesc) {
            variables = new ArrayList<>(appTplDesc.templateVariables);
        } else {
            variables = new ArrayList<>();
        }

        if (instTplVar != null && !instTplVar.isEmpty()) {
            variables.addAll(instTplVar);
        }

        List<TemplateParameter> parameters = new ArrayList<>(original.startParameters);

        // in case we have a reference to another template, resolve this first and merge into our own.
        if (original.template != null && !original.template.isBlank()) {
            Optional<ApplicationTemplateDescriptor> optParentTplDesc = appTemplates.stream()
                    .filter(a -> a.id.equals(original.template)).findAny();

            if (optParentTplDesc.isEmpty()) {
                throw new IllegalStateException(
                        "Cannot resolve application template " + original.name + ", missing parent: " + original.template);
            } else {
                ApplicationTemplateDescriptor parentTplDesc = optParentTplDesc.get();
                // recurse to the parent, pass on overrides, and also ids of all templates in the current resolution chain (including self).
                FlattenedApplicationTemplateConfiguration parent = new FlattenedApplicationTemplateConfiguration(parentTplDesc,
                        appTemplates, instTplVar, overrides, children);

                // all 1:1 properties which can be simply merged.
                mergeSimplePropertiesFromParent(parent);

                // merge template variables from parent up to us, so we have the definition of them like it was our own.
                parent.templateVariables.stream().filter(notPresentByKey(variables, t -> t.name)).forEach(variables::add);

                // merge parameters from parent up to us, so we have the definition of them like it was out own.
                parent.startParameters.stream().filter(notPresentByKey(parameters, p -> p.id)).forEach(parameters::add);
            }
        }

        // now that we have a complete set of variables and parameters, go through parameters and process overrides
        // this has the side-effect of "marking" all template variables we need to be able to resolve later.
        // thus we can disregard all variable definitions which we will not need to query from the user.
        TrackingTemplateOverrideResolver res = new TrackingTemplateOverrideResolver(overrides);
        for (TemplateParameter param : parameters) {
            TemplateParameter processed = new TemplateParameter();
            processed.id = param.id;
            processed.value = TemplateHelper.process(param.value, res, res::canResolve);

            this.startParameters.add(processed);
        }

        // name can also use template variables...
        name = TemplateHelper.process(name, res, res::canResolve);

        // now we can filter the variable definitions to only have those left which we *really* need.
        for (TemplateVariable variable : variables) {
            if (!res.getRequestedVariables().contains(variable.id)) {
                // not requested. skip.
                continue;
            }

            this.templateVariables.add(variable);
        }
    }

    public static <T> Predicate<T> notPresentByKey(Collection<T> elements, Function<? super T, ?> keyExtractor) {
        return t -> elements.stream().noneMatch(x -> keyExtractor.apply(x).equals(keyExtractor.apply(t)));
    }

    private void mergeSimplePropertiesFromParent(FlattenedApplicationTemplateConfiguration parent) {
        // update our own properties in case we have nothing set.
        this.name = resolveStringValue(this.name, parent.name);
        this.application = resolveStringValue(this.application, parent.application);
        this.description = resolveStringValue(this.description, parent.description);
        this.preferredProcessControlGroup = resolveStringValue(this.preferredProcessControlGroup,
                parent.preferredProcessControlGroup);

        if (applyOn == null || applyOn.isEmpty()) {
            applyOn = parent.applyOn;
        }

        // merge process control partial object as map
        for (var entry : parent.processControl.entrySet()) {
            if (!this.processControl.containsKey(entry.getKey())) {
                this.processControl.put(entry.getKey(), entry.getValue());
            }
        }
    }

    private static String resolveStringValue(String ours, String theirs) {
        if (ours != null) {
            return ours;
        }
        return theirs;
    }

}
