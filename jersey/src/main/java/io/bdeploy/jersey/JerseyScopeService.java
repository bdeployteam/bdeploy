package io.bdeploy.jersey;

import java.util.List;

import org.jvnet.hk2.annotations.Service;

import io.bdeploy.jersey.ws.change.msg.ObjectScope;
import jakarta.inject.Inject;

@Service
public class JerseyScopeService {

    private static final String BD_SCOPES = "BD_SCOPES";
    private static final String BD_USER = "BD_USER";

    @Inject
    private JerseyRequestContext reqCtx;

    public void setScope(List<String> scope, String user) {
        this.reqCtx.setProperty(BD_SCOPES, scope);
        this.reqCtx.setProperty(BD_USER, user);
    }

    @SuppressWarnings("unchecked")
    public List<String> getScope() {
        return (List<String>) this.reqCtx.getProperty(BD_SCOPES);
    }

    public ObjectScope getObjectScope() {
        return new ObjectScope(getScope());
    }

    public String getUser() {
        return (String) this.reqCtx.getProperty(BD_USER);
    }

}
