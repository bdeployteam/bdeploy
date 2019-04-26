package io.bdeploy.jersey.security;

public class SecurityTestResourceImpl implements SecurityTestResource {

    @Override
    public String testUnsecured() {
        return "unsecured";
    }

    @Override
    public String testSecured() {
        return "secured";
    }

}
