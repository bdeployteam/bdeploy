package io.bdeploy.bhive.op.remote;

import io.bdeploy.bhive.BHive.Operation;
import io.bdeploy.common.security.RemoteService;

public abstract class RemoteOperation<T, X extends RemoteOperation<T, X>> extends Operation<T> {

    private RemoteService remote;

    @SuppressWarnings("unchecked")
    public X setRemote(RemoteService def) {
        this.remote = def;
        return (X) this;
    }

    protected RemoteService getRemote() {
        return remote;
    }
}
