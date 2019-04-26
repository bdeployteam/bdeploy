package io.bdeploy.jersey;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.ext.Provider;

import org.glassfish.jersey.server.ExtendedUriInfo;
import org.glassfish.jersey.server.internal.inject.MultivaluedParameterExtractorProvider;
import org.glassfish.jersey.server.model.Parameter;
import org.glassfish.jersey.server.model.Parameter.Source;
import org.glassfish.jersey.server.model.ResourceMethod;

@Provider
public class JerseySseActivityScopeFilter implements ContainerRequestFilter {

    private static final String SCOPE_PROPERTY = "ActivityScopeProperty";

    @Inject
    private javax.inject.Provider<MultivaluedParameterExtractorProvider> mpep;

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        UriInfo plainInfo = requestContext.getUriInfo();
        if (plainInfo instanceof ExtendedUriInfo) {
            List<String> scope = new ArrayList<>();

            String proxyScope = requestContext.getHeaderString(JerseySseActivityProxyClientFilter.PROXY_SCOPE_HEADER);
            if (proxyScope != null) {
                scope.add(proxyScope);
            }

            ExtendedUriInfo info = (ExtendedUriInfo) plainInfo;

            // locators are LIFO, last matched locator is first element!
            List<ResourceMethod> methods = new ArrayList<>(info.getMatchedResourceLocators());
            Collections.reverse(methods);
            methods.add(info.getMatchedResourceMethod());

            for (ResourceMethod m : methods) {
                scope.addAll(getMethodScope(info, m));
            }

            requestContext.setProperty(SCOPE_PROPERTY, scope);
        }
    }

    private List<String> getMethodScope(ExtendedUriInfo info, ResourceMethod m) {
        List<String> scopesInOrder = new ArrayList<>();
        for (Parameter param : m.getInvocable().getParameters()) {
            if (param.getAnnotation(ActivityScope.class) != null) {
                // it's a scope parameter
                if (param.getSource() != Source.PATH && param.getSource() != Source.QUERY) {
                    throw new IllegalStateException("ActivityScope can only be annotated on QueryParam or PathParam: " + m);
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

    @SuppressWarnings("unchecked")
    public static List<String> getRequestActivityScope(ContainerRequestContext context) {
        if (context == null) {
            return Collections.emptyList();
        }

        List<String> result = (List<String>) context.getProperty(SCOPE_PROPERTY);
        if (result == null) {
            return Collections.emptyList();
        }
        return result;
    }

}
