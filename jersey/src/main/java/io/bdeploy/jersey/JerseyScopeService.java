package io.bdeploy.jersey;

import org.jvnet.hk2.annotations.Service;

import io.bdeploy.jersey.ws.change.msg.ObjectScope;

@Service
public class JerseyScopeService {

    private final ThreadLocal<ObjectScope> scope = ThreadLocal.withInitial(() -> ObjectScope.EMPTY);

    public void setScope(ObjectScope scope) {
        this.scope.set(scope);
    }

    public void clear() {
        this.scope.set(ObjectScope.EMPTY);
    }

    public ObjectScope getObjectScope() {
        return scope.get();
    }

}
