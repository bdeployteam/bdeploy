package io.bdeploy.bhive.op.remote;

import io.bdeploy.bhive.BHive.TransactedOperation;
import io.bdeploy.common.security.RemoteService;

public abstract class TransactedRemoteOperation<T, X extends TransactedRemoteOperation<T, X>> extends TransactedOperation<T> {

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
