package io.bdeploy.ui.api.impl;

import java.io.IOException;
import java.lang.reflect.Method;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Priority;
import javax.inject.Inject;
import javax.ws.rs.ForbiddenException;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;

import org.glassfish.jersey.server.ExtendedUriInfo;
import org.glassfish.jersey.server.model.ResourceMethod;

import io.bdeploy.common.security.RequiredPermission;
import io.bdeploy.common.security.ScopedPermission;
import io.bdeploy.jersey.JerseySecurityContext;
import io.bdeploy.ui.api.AuthService;

/**
 * Ensures that the user has the required permissions to access a certain method.
 * <p>
 * The URI is visited from left to right and the required permissions and scopes are evaluated. All required permissions
 * must be fulfilled otherwise an {@linkplain ForbiddenException exception} is thrown. Scopes defined on resource locators are
 * inherited to all following resource methods that are called. Thus if a resource locator defines a required permission of READ
 * for scope 'A' all methods defined on that locator will automatically inherit READ for scope 'A'. However the method can
 * overwrite the scope and the permission and define a more restrictive value like WRITE or ADMIN.
 * </p>
 */
@Priority(Priorities.AUTHORIZATION)
public class PermissionRequestFilter implements ContainerRequestFilter {

    @Inject
    private AuthService authService;

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        UriInfo plainInfo = requestContext.getUriInfo();
        if (!(plainInfo instanceof ExtendedUriInfo)) {
            return;
        }
        // Authorization requires a user to be set.
        SecurityContext plainSecurityContext = requestContext.getSecurityContext();
        if (plainSecurityContext == null || plainSecurityContext.getUserPrincipal() == null) {
            return;
        }

        // Security context must contain an API access token
        Principal userPrincipal = plainSecurityContext.getUserPrincipal();
        String userName = userPrincipal.getName();
        if (!(plainSecurityContext instanceof JerseySecurityContext)) {
            throw new ForbiddenException("User '" + userName + "' is not authorized to access requested resource.");
        }
        JerseySecurityContext securityContext = (JerseySecurityContext) plainSecurityContext;

        // Check if one of the invoked locators require a special permission
        ExtendedUriInfo uriInfo = (ExtendedUriInfo) plainInfo;
        List<ResourceMethod> methods = new ArrayList<>(uriInfo.getMatchedResourceLocators());
        Collections.reverse(methods);

        // Check if the final method requires a special permission
        ResourceMethod invokedMethod = uriInfo.getMatchedResourceMethod();
        if (invokedMethod != null) {
            methods.add(invokedMethod);
        }

        String activeScope = null;

        // Check if the user has the permissions declared on each involved method
        for (ResourceMethod resourceMethod : methods) {
            Method method = resourceMethod.getInvocable().getDefinitionMethod();
            RequiredPermission requiredPermission = getRequiredPermission(method);
            if (requiredPermission == null) {
                continue;
            }

            // Try to find the parameter holding the actual scoped value
            String methodScope = getScopedValue(uriInfo, requiredPermission.scope());
            if (methodScope != null) {
                activeScope = methodScope;
            }

            // Check if the user has global permissions
            ScopedPermission scopedPermission = new ScopedPermission(activeScope, requiredPermission.permission());
            if (securityContext.isAuthorized(scopedPermission)) {
                continue;
            }

            // Check if the user has scoped permissions
            if (!authService.isAuthorized(userName, scopedPermission)) {
                throw new ForbiddenException("User '" + userName + "' is not authorized to access requested resource.");
            }
        }
    }

    /**
     * Returns the defined permission or {@code null} if not defined on the method or on class level.
     */
    private RequiredPermission getRequiredPermission(Method method) {
        RequiredPermission permission = method.getAnnotation(RequiredPermission.class);
        if (permission != null) {
            return permission;
        }
        return method.getDeclaringClass().getAnnotation(RequiredPermission.class);
    }

    /**
     * Tries to find the actual value for a parameter with the given name.
     *
     * @param uriInfo info about the invoked URI
     * @param scopeParam the name of the parameter to find
     * @return the actual value
     */
    private String getScopedValue(ExtendedUriInfo uriInfo, String scopeParam) {
        if (scopeParam == null || scopeParam.isEmpty()) {
            return null;
        }

        // Check if the value is defined in the query parameters
        MultivaluedMap<String, String> pathParameters = uriInfo.getPathParameters();
        String pathValue = pathParameters.getFirst(scopeParam);
        if (pathValue != null) {
            return pathValue;
        }

        // Check if the value is defined in the query parameters
        MultivaluedMap<String, String> queryParams = uriInfo.getQueryParameters();
        String queryValue = queryParams.getFirst(scopeParam);
        if (queryValue != null) {
            return queryValue;
        }

        // We cannot find a parameter with the given name. Thats an error and the annotation must be fixed
        throw new IllegalStateException("URI does not contain a parameter with the name '" + scopeParam + "'");
    }

}
