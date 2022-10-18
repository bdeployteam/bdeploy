package io.bdeploy.interfaces.configuration.template;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.interfaces.descriptor.application.ApplicationDescriptor.ApplicationType;
import io.bdeploy.interfaces.descriptor.template.ApplicationTemplateDescriptor;
import io.bdeploy.interfaces.descriptor.template.InstanceTemplateGroup;
import io.bdeploy.interfaces.descriptor.template.TemplateVariable;

public class FlattenedInstanceTemplateGroupConfiguration {

    private static final Logger log = LoggerFactory.getLogger(FlattenedInstanceTemplateGroupConfiguration.class);

    public String name;

    public String description;

    public ApplicationType type;

    public List<FlattenedApplicationTemplateConfiguration> applications;

    public List<TemplateVariable> groupVariables;

    FlattenedInstanceTemplateGroupConfiguration() {
        // intentionally left empty for deserialization.
    }

    public FlattenedInstanceTemplateGroupConfiguration(InstanceTemplateGroup original, List<ApplicationTemplateDescriptor> appTpl,
            List<TemplateVariable> templateVariables) {
        this.name = original.name;
        this.description = original.description;
        this.type = original.type;

        this.applications = original.applications.stream().map(a -> {
            try {
                return new FlattenedApplicationTemplateConfiguration(a, appTpl, templateVariables);
            } catch (Exception e) {
                log.error("Cannot resolve application template {}: {}", a.name, e.toString());
                if (log.isDebugEnabled()) {
                    log.debug("Exception:", e);
                }
                return null;
            }
        }).filter(Objects::nonNull).toList();

        this.groupVariables = applications.stream().map(a -> a.templateVariables).flatMap(List::stream)
                .filter(distinctByKey(v -> v.name)).toList();
    }

    public static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
        Set<Object> seen = ConcurrentHashMap.newKeySet();
        return t -> seen.add(keyExtractor.apply(t));
    }

}
