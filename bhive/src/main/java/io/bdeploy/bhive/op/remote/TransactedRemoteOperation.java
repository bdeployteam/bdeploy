package io.bdeploy.bhive.op.remote;

import io.bdeploy.bhive.BHive;
import io.bdeploy.common.security.RemoteService;

@SuppressWarnings("unchecked")
public abstract class TransactedRemoteOperation<T, X extends TransactedRemoteOperation<T, X>>
        extends BHive.TransactedOperation<T> {

    private RemoteService remote;

    public X setRemote(RemoteService def) {
        this.remote = def;
        return (X) this;
    }

    protected RemoteService getRemote() {
        return remote;
    }

}
