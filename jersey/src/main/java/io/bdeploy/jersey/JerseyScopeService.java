package io.bdeploy.jersey;

import java.util.List;

import org.jvnet.hk2.annotations.Service;

@Service
public class JerseyScopeService {

    private final ThreadLocal<List<String>> scopes = new ThreadLocal<>();
    private final ThreadLocal<String> users = new ThreadLocal<>();

    public void setScope(List<String> scope, String user) {
        this.scopes.set(scope);
        this.users.set(user);
    }

    public List<String> getScope() {
        return this.scopes.get();
    }

    public String getUser() {
        return this.users.get();
    }

}
