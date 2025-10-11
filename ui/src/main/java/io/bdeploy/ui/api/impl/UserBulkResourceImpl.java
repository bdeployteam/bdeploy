package io.bdeploy.ui.api.impl;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.common.security.ScopedPermission;
import io.bdeploy.interfaces.UserGroupInfo;
import io.bdeploy.interfaces.UserInfo;
import io.bdeploy.ui.api.AuthGroupService;
import io.bdeploy.ui.api.AuthService;
import io.bdeploy.ui.api.UserBulkResource;
import io.bdeploy.ui.dto.BulkOperationResultDto;
import io.bdeploy.ui.dto.BulkOperationResultDto.OperationResult;
import io.bdeploy.ui.dto.BulkOperationResultDto.OperationResultType;
import io.bdeploy.ui.dto.ObjectChangeDetails;
import io.bdeploy.ui.dto.ObjectChangeType;
import io.bdeploy.ui.dto.UserBulkAssignPermissionDto;
import io.bdeploy.ui.dto.UserBulkRemovePermissionDto;
import jakarta.inject.Inject;

public class UserBulkResourceImpl implements UserBulkResource {

    private static final Logger log = LoggerFactory.getLogger(UserBulkResourceImpl.class);

    @Inject
    private AuthService auth;

    @Inject
    private AuthGroupService authGroup;

    @Inject
    private ChangeEventManager cem;

    @Override
    public BulkOperationResultDto delete(Set<String> userNames) {
        BulkOperationResultDto result = new BulkOperationResultDto();

        for (String name : userNames) {
            try {
                if (auth.deleteUser(name)) {
                    result.add(new OperationResult(name, OperationResultType.INFO, "Deleted"));
                    cem.remove(ObjectChangeType.USER, Collections.singletonMap(ObjectChangeDetails.USER_NAME, name));
                } else {
                    result.add(new OperationResult(name, OperationResultType.ERROR, "Cannot delete user with name " + name));
                }
            } catch (Exception e) {
                log.warn("Error while deleting user {}", name, e);
                result.add(new OperationResult(name, OperationResultType.ERROR, e.getMessage()));
            }
        }

        return result;
    }

    @Override
    public BulkOperationResultDto setInactive(boolean inactive, Set<String> userNames) {
        BulkOperationResultDto result = new BulkOperationResultDto();

        String status = inactive ? "Deactivated" : "Activated";

        for (String name : userNames) {
            UserInfo user = auth.getUser(name);
            if (user == null) {
                result.add(new OperationResult(name, OperationResultType.ERROR, "Cannot find user with name " + name));
                continue;
            }
            if (user.inactive == inactive) {
                result.add(new OperationResult(name, OperationResultType.INFO, "Skipped, already " + status));
                continue;
            }
            try {
                user.inactive = inactive;
                auth.updateUserInfo(user);
                result.add(new OperationResult(name, OperationResultType.INFO, status));
                cem.change(ObjectChangeType.USER, Collections.singletonMap(ObjectChangeDetails.USER_NAME, name));
            } catch (Exception e) {
                log.warn("Error while updating inactive flag for user {}", name, e);
                result.add(new OperationResult(name, OperationResultType.ERROR, e.getMessage()));
            }
        }

        return result;
    }

    @Override
    public BulkOperationResultDto assignPermission(UserBulkAssignPermissionDto dto) {
        BulkOperationResultDto result = new BulkOperationResultDto();

        ScopedPermission scopedPerm = dto.scopedPermission;
        for (String name : dto.userNames) {
            UserInfo user = auth.getUser(name);
            if (user == null) {
                result.add(new OperationResult(name, OperationResultType.ERROR, "Cannot find user with name " + name));
                continue;
            }
            try {
                boolean updated = user.permissions.removeIf(perm -> Objects.equals(perm.scope, scopedPerm.scope));
                user.permissions.add(scopedPerm);
                auth.updateUserInfo(user);
                result.add(new OperationResult(user.name, OperationResultType.INFO,
                        updated ? "Permission updated" : "Permission assigned"));
                cem.change(ObjectChangeType.USER, Collections.singletonMap(ObjectChangeDetails.USER_NAME, name));
            } catch (Exception e) {
                log.warn("Error while assigning permission to user {}", name, e);
                result.add(new OperationResult(name, OperationResultType.ERROR, e.getMessage()));
            }
        }

        return result;
    }

    @Override
    public BulkOperationResultDto removePermission(UserBulkRemovePermissionDto dto) {
        BulkOperationResultDto result = new BulkOperationResultDto();

        for (String name : dto.userNames) {
            UserInfo user = auth.getUser(name);
            if (user == null) {
                result.add(new OperationResult(name, OperationResultType.ERROR, "Cannot find user with name " + name));
                continue;
            }
            boolean removed = user.permissions.removeIf(perm -> Objects.equals(perm.scope, dto.scope));
            if (!removed) {
                result.add(new OperationResult(user.name, OperationResultType.INFO, "Skipped, no permission of given scope"));
                continue;
            }
            try {
                auth.updateUserInfo(user);
                result.add(new OperationResult(user.name, OperationResultType.INFO, "Permission removed"));
                cem.change(ObjectChangeType.USER, Collections.singletonMap(ObjectChangeDetails.USER_NAME, name));
            } catch (Exception e) {
                log.warn("Error while removing permission from user {}", name, e);
                result.add(new OperationResult(name, OperationResultType.ERROR, e.getMessage()));
            }
        }

        return result;
    }

    @Override
    public BulkOperationResultDto addToGroup(String groupId, Set<String> userNames) {
        BulkOperationResultDto result = new BulkOperationResultDto();

        UserGroupInfo group = authGroup.getUserGroup(groupId);

        if (group == null) {
            result.add(new OperationResult(groupId, OperationResultType.ERROR, "Cannot find user group with id " + groupId));
            return result;
        }

        for (String name : userNames) {
            UserInfo user = auth.getUser(name);
            if (user == null) {
                result.add(new OperationResult(name, OperationResultType.ERROR, "Cannot find user with name " + name));
                continue;
            }
            if (user.getGroups().contains(groupId)) {
                result.add(new OperationResult(name, OperationResultType.INFO, "Skipped, already in group " + group.name));
                continue;
            }
            try {
                auth.addUserToGroup(groupId, name);
                result.add(new OperationResult(groupId, OperationResultType.INFO, "Added to group " + group.name));
                cem.remove(ObjectChangeType.USER, Collections.singletonMap(ObjectChangeDetails.USER_GROUP_ID, name));
            } catch (Exception e) {
                log.warn("Error while adding user {} to group {}", name, group.name, e);
                result.add(new OperationResult(name, OperationResultType.ERROR, e.getMessage()));
            }
        }
        return result;
    }
}
