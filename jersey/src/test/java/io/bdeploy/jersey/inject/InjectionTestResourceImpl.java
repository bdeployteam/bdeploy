package io.bdeploy.jersey.inject;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.ws.rs.core.SecurityContext;

public class InjectionTestResourceImpl implements InjectionTestResource {

    @Inject
    @Named(INJECTED_STRING)
    private String injected;

    @Inject
    @Named(INJECTED_STRING)
    private Provider<String> injectedProvider;

    @Inject
    private Provider<SecurityContext> security;

    @Override
    public String retrieveInjected() {
        return injected;
    }

    @Override
    public String retrieveInjectedProvider() {
        return injectedProvider.get();
    }

    @Override
    public String retrieveUserFromToken() {
        return security.get().getUserPrincipal().getName();
    }

}
