/*
 * Copyright (c) SSI Schaefer IT Solutions
 */
package io.bdeploy.tea.plugin.services;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;

import org.eclipse.e4.core.contexts.IEclipseContext;
import org.eclipse.tea.core.TaskExecutionContext;
import org.eclipse.tea.core.annotations.TaskChainContextInit;

import jakarta.inject.Qualifier;

/**
 * Service which provides logic to enrich a {@link TaskExecutionContext} with logic to build a certain application configured in a
 * product descriptor.
 */
public interface BDeployApplicationService {

    /**
     * A method annotated with this annotation is called to create tasks to build a {@link BDeployApplicationDescriptor}.
     * <p>
     * The method has injector access to the {@link BDeployApplicationDescriptor}, as well as to all things available
     * to {@link TaskChainContextInit}.
     * <p>
     * The method is responsible for adding required Tasks to the {@link TaskExecutionContext} itself.
     * <p>
     * The method <b>must</b> return a {@link List} or {@link BDeployApplicationBuild} containing the resulting future build
     * results for collection into the actual product.
     * <p>
     * The method is called using its own {@link IEclipseContext}. If the method needs to persist data for later applications
     * (e.g. when sharing build tasks between applications), this can be done using the {@link TaskExecutionContext#getContext()}.
     */
    @Documented
    @Qualifier
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface CreateApplicationTasks {
    }

    /**
     * @param applicationType the application type as defined in the product's build.yaml
     * @return whether this service knows how to build this application type
     */
    public boolean canHandle(String applicationType);

}
