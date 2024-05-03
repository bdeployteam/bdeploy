package io.bdeploy.jersey;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.glassfish.jersey.model.Parameter.Source;
import org.glassfish.jersey.server.ExtendedUriInfo;
import org.glassfish.jersey.server.internal.inject.MultivaluedParameterExtractorProvider;
import org.glassfish.jersey.server.model.Parameter;
import org.glassfish.jersey.server.model.ResourceMethod;

import io.bdeploy.jersey.ws.change.msg.ObjectScope;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.Provider;

@Provider
public class JerseyScopeFilter implements ContainerRequestFilter, ContainerResponseFilter {

    @Inject
    private jakarta.inject.Provider<MultivaluedParameterExtractorProvider> mpep;

    @Inject
    private JerseyScopeService scopeService;

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        // thread may have been pooled. if a task crashed quite hard, current activity could still be set.
        UriInfo plainInfo = requestContext.getUriInfo();
        if (plainInfo instanceof ExtendedUriInfo) {
            List<String> scope = new ArrayList<>();

            ExtendedUriInfo info = (ExtendedUriInfo) plainInfo;

            // locators are LIFO, last matched locator is first element!
            List<ResourceMethod> methods = new ArrayList<>(info.getMatchedResourceLocators());
            Collections.reverse(methods);
            methods.add(info.getMatchedResourceMethod());

            for (ResourceMethod m : methods) {
                scope.addAll(getMethodScope(info, m));
            }

            scopeService.setScope(new ObjectScope(scope));
        }
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) throws IOException {
        // no matter the response, we want to clear out the current scope information.
        scopeService.clear();
    }

    private List<String> getMethodScope(ExtendedUriInfo info, ResourceMethod m) {
        List<String> scopesInOrder = new ArrayList<>();
        for (Parameter param : m.getInvocable().getParameters()) {
            if (param.getAnnotation(Scope.class) != null) {
                // it's a scope parameter
                if (param.getSource() != Source.PATH && param.getSource() != Source.QUERY) {
                    throw new IllegalStateException("Scope can only be annotated on QueryParam or PathParam: " + m);
                }

                MultivaluedMap<String, String> parameters = param.getSource() == Source.QUERY ? info.getQueryParameters()
                        : info.getPathParameters();

                Object value = mpep.get().get(param).extract(parameters);

                if (value != null) {
                    scopesInOrder.add(value.toString());
                }
            }
        }
        return scopesInOrder;
    }

}
