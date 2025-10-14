package io.bdeploy.ui.api.impl;

import static io.bdeploy.interfaces.UserGroupInfo.ALL_USERS_GROUP_ID;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.common.security.ScopedPermission;
import io.bdeploy.interfaces.UserGroupInfo;
import io.bdeploy.ui.api.AuthGroupService;
import io.bdeploy.ui.api.UserGroupBulkResource;
import io.bdeploy.ui.dto.BulkOperationResultDto;
import io.bdeploy.ui.dto.ObjectChangeDetails;
import io.bdeploy.ui.dto.ObjectChangeType;
import io.bdeploy.ui.dto.OperationResult;
import io.bdeploy.ui.dto.OperationResult.OperationResultType;
import io.bdeploy.ui.dto.UserGroupBulkAssignPermissionDto;
import io.bdeploy.ui.dto.UserGroupBulkRemovePermissionDto;
import jakarta.inject.Inject;

public class UserGroupBulkResourceImpl implements UserGroupBulkResource {

    private static final Logger log = LoggerFactory.getLogger(UserGroupBulkResourceImpl.class);

    @Inject
    private AuthGroupService authGroup;

    @Inject
    private ChangeEventManager cem;

    @Override
    public BulkOperationResultDto deleteUserGroups(Set<String> groupIds) {
        BulkOperationResultDto result = new BulkOperationResultDto();

        for (String groupId : groupIds) {
            if (ALL_USERS_GROUP_ID.equals(groupId)) {
                result.add(new OperationResult(groupId, OperationResultType.ERROR, "Cannot delete " + ALL_USERS_GROUP_ID));
                continue;
            }
            UserGroupInfo group = authGroup.getUserGroup(groupId);
            if (group == null) {
                result.add(new OperationResult(groupId, OperationResultType.ERROR, "Cannot find group with id " + groupId));
                continue;
            }
            try {
                authGroup.deleteUserGroup(groupId);
                result.add(new OperationResult(group.name, OperationResultType.INFO, "Deleted"));
                cem.remove(ObjectChangeType.USER_GROUP, Collections.singletonMap(ObjectChangeDetails.USER_GROUP_ID, groupId));
            } catch (Exception e) {
                log.warn("Error while deleting user group {}", group.name, e);
                result.add(new OperationResult(group.name, OperationResultType.ERROR, e.getMessage()));
            }
        }

        return result;
    }

    @Override
    public BulkOperationResultDto setInactiveUserGroups(boolean inactive, Set<String> groupIds) {
        BulkOperationResultDto result = new BulkOperationResultDto();

        String status = inactive ? "Deactivated" : "Activated";

        for (String groupId : groupIds) {
            if (ALL_USERS_GROUP_ID.equals(groupId) && inactive) {
                result.add(new OperationResult(groupId, OperationResultType.ERROR, "Cannot deactivate " + ALL_USERS_GROUP_ID));
                continue;
            }
            UserGroupInfo group = authGroup.getUserGroup(groupId);
            if (group == null) {
                result.add(new OperationResult(groupId, OperationResultType.ERROR, "Cannot find group with id " + groupId));
                continue;
            }
            if (group.inactive == inactive) {
                result.add(new OperationResult(group.name, OperationResultType.INFO, "Skipped, already " + status));
                continue;
            }
            try {
                group.inactive = inactive;
                authGroup.updateUserGroup(group);
                result.add(new OperationResult(group.name, OperationResultType.INFO, status));
                cem.change(ObjectChangeType.USER_GROUP, Collections.singletonMap(ObjectChangeDetails.USER_GROUP_ID, groupId));
            } catch (Exception e) {
                log.warn("Error while updating inactive flag for user group {}", group.name, e);
                result.add(new OperationResult(group.name, OperationResultType.ERROR, e.getMessage()));
            }
        }

        return result;
    }

    @Override
    public BulkOperationResultDto assignPermission(UserGroupBulkAssignPermissionDto dto) {
        BulkOperationResultDto result = new BulkOperationResultDto();

        ScopedPermission scopedPerm = dto.scopedPermission;
        for (String groupId : dto.groupIds) {
            UserGroupInfo group = authGroup.getUserGroup(groupId);
            if (group == null) {
                result.add(new OperationResult(groupId, OperationResultType.ERROR, "Cannot find group with id " + groupId));
                continue;
            }
            try {
                boolean updated = group.permissions.removeIf(perm -> Objects.equals(perm.scope, scopedPerm.scope));
                group.permissions.add(scopedPerm);
                authGroup.updateUserGroup(group);
                result.add(new OperationResult(group.name, OperationResultType.INFO,
                        updated ? "Permission updated" : "Permission assigned"));
                cem.change(ObjectChangeType.USER_GROUP, Collections.singletonMap(ObjectChangeDetails.USER_GROUP_ID, groupId));
            } catch (Exception e) {
                log.warn("Error while assigning permission to user group {}", group.name, e);
                result.add(new OperationResult(group.name, OperationResultType.ERROR, e.getMessage()));
            }
        }

        return result;
    }

    @Override
    public BulkOperationResultDto removePermission(UserGroupBulkRemovePermissionDto dto) {
        BulkOperationResultDto result = new BulkOperationResultDto();

        for (String groupId : dto.groupIds) {
            UserGroupInfo group = authGroup.getUserGroup(groupId);
            if (group == null) {
                result.add(new OperationResult(groupId, OperationResultType.ERROR, "Cannot find group with id " + groupId));
                continue;
            }
            boolean removed = group.permissions.removeIf(perm -> Objects.equals(perm.scope, dto.scope));
            if (!removed) {
                result.add(new OperationResult(group.name, OperationResultType.INFO, "Skipped, no permission of given scope"));
                continue;
            }
            try {
                authGroup.updateUserGroup(group);
                result.add(new OperationResult(group.name, OperationResultType.INFO, "Permission removed"));
                cem.change(ObjectChangeType.USER_GROUP, Collections.singletonMap(ObjectChangeDetails.USER_GROUP_ID, groupId));
            } catch (Exception e) {
                log.warn("Error while removing permission from user group {}", group.name, e);
                result.add(new OperationResult(group.name, OperationResultType.ERROR, e.getMessage()));
            }
        }

        return result;
    }

}
