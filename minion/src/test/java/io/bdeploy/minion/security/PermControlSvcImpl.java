package io.bdeploy.minion.security;

import org.jvnet.hk2.annotations.Service;

import io.bdeploy.common.security.ScopedPermission.Permission;

@Service
public class PermControlSvcImpl {

    private Permission perm;

    public void setPerm(Permission perm) {
        this.perm = perm;
    }

    public Permission getPerm() {
        return perm;
    }

}
