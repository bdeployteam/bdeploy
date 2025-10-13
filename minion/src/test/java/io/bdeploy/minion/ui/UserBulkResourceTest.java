package io.bdeploy.minion.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.bdeploy.common.security.ScopedPermission;
import io.bdeploy.interfaces.UserGroupInfo;
import io.bdeploy.interfaces.UserInfo;
import io.bdeploy.minion.TestMinion;
import io.bdeploy.ui.api.AuthAdminResource;
import io.bdeploy.ui.api.AuthResource;
import io.bdeploy.ui.dto.BulkOperationResultDto;
import io.bdeploy.ui.dto.BulkOperationResultDto.OperationResult;
import io.bdeploy.ui.dto.BulkOperationResultDto.OperationResultType;

@ExtendWith(TestMinion.class)
class UserBulkResourceTest {

    @Test
    void testCreateUpdateDeleteUser(AuthResource auth) {
        AuthAdminResource admin = auth.getAdmin();
        var bulk = admin.getUserBulkResource();

        BiFunction<Integer, Integer, Set<String>> nameGenerator = (a, b) -> IntStream.rangeClosed(a, b)
                .mapToObj(i -> "TestUser" + i).map(o -> o).collect(Collectors.toSet());

        Set<String> group1 = nameGenerator.apply(1, 3);
        Set<String> group2 = nameGenerator.apply(4, 10);
        Set<String> allNames = new HashSet<>() {

            {
                addAll(group1);
                addAll(group2);
            }
        };
        Set<String> testGroup = nameGenerator.apply(100, 110);

        int group1Count = group1.size();
        int group2Count = group2.size();
        int totalCount = group1Count + group2Count;
        int testGroupCount = testGroup.size();

        // Ensure baseline
        assertEquals(1, admin.getAllUser().size()); // Default global administrator user already exists
        assertEquals(1, admin.getAllUserNames().size()); // Default global administrator user already exists

        // Create users
        allNames.forEach(n -> createUser(admin, n));
        assertEquals(1 + totalCount, admin.getAllUser().size());
        assertEquals(1 + totalCount, admin.getAllUserNames().size());

        // Test bulk setInactive
        BulkOperationResultDto setInactiveResult1 = bulk.setInactive(true, group1);
        assertBulkOperationResult(setInactiveResult1, group1Count, 0, 0);
        group1.forEach(n -> assertTrue(admin.getUser(n).inactive));
        group2.forEach(n -> assertFalse(admin.getUser(n).inactive));

        BulkOperationResultDto setInactiveResult2 = bulk.setInactive(true, group2);
        assertBulkOperationResult(setInactiveResult2, group2Count, 0, 0);
        allNames.forEach(n -> assertTrue(admin.getUser(n).inactive));

        BulkOperationResultDto setInactiveResult3 = bulk.setInactive(false, allNames);
        assertBulkOperationResult(setInactiveResult3, totalCount, 0, 0);
        allNames.forEach(n -> assertFalse(admin.getUser(n).inactive));

        BulkOperationResultDto setInactiveResult4 = bulk.setInactive(false, group2);
        assertBulkOperationResult(setInactiveResult4, group2Count, 0, 0);
        allNames.forEach(n -> assertFalse(admin.getUser(n).inactive));

        BulkOperationResultDto setInactiveResult5 = bulk.setInactive(true, testGroup);
        assertBulkOperationResult(setInactiveResult5, 0, 0, testGroupCount);

        BulkOperationResultDto setInactiveResult6 = bulk.setInactive(false, new HashSet<String>() {

            {
                addAll(group2);
                addAll(testGroup);
            }
        });
        assertBulkOperationResult(setInactiveResult6, group2Count, 0, testGroupCount);

        // Test bulk addToGroup
        UserGroupInfo userGroupInfo = new UserGroupInfo();
        userGroupInfo.name = "TestGroup";
        userGroupInfo.description = "Cool Test Group";
        userGroupInfo.permissions = Set.of(new ScopedPermission(ScopedPermission.Permission.READ));
        admin.createUserGroup(userGroupInfo);
        userGroupInfo.id = admin.getAllUserGroups().stream().filter(g -> g.name.equals(userGroupInfo.name)).findAny().get().id;

        BulkOperationResultDto addToGroupResult1 = bulk.addToGroup(userGroupInfo.id, group2);
        assertBulkOperationResult(addToGroupResult1, group2Count, 0, 0);

        BulkOperationResultDto addToGroupResult2 = bulk.addToGroup(userGroupInfo.id, testGroup);
        assertBulkOperationResult(addToGroupResult2, 0, 0, testGroupCount);

        BulkOperationResultDto addToGroupResult3 = bulk.addToGroup(userGroupInfo.id, new HashSet<String>() {

            {
                addAll(allNames);
                addAll(testGroup);
            }
        });
        assertBulkOperationResult(addToGroupResult3, totalCount, 0, testGroupCount);

        // Test bulk delete part of all users
        BulkOperationResultDto bulkDeletionResult1 = bulk.delete(group1);
        assertBulkOperationResult(bulkDeletionResult1, group1Count, 0, 0);
        assertEquals(1 + group2Count, admin.getAllUser().size());
        assertEquals(1 + group2Count, admin.getAllUserNames().size());

        // Test bulk delete all users, some of which were already deleted in the previous step
        BulkOperationResultDto bulkDeletionResult2 = bulk.delete(allNames);
        assertBulkOperationResult(bulkDeletionResult2, group2Count, 0, group1Count);
        assertEquals(1, admin.getAllUser().size());
        assertEquals(1, admin.getAllUserNames().size());

        // Test bulk delete of entirely non-existant users
        BulkOperationResultDto bulkDeletionResult3 = bulk.delete(testGroup);
        assertBulkOperationResult(bulkDeletionResult3, 0, 0, testGroupCount);
        assertEquals(1, admin.getAllUser().size());
        assertEquals(1, admin.getAllUserNames().size());
    }

    private static void createUser(AuthAdminResource admin, String name) {
        UserInfo newUser = new UserInfo(name);
        newUser.password = UUID.randomUUID().toString();
        admin.createLocalUser(newUser);
    }

    private static void assertBulkOperationResult(BulkOperationResultDto dto, int infoCount, int warningCount, int errorCount) {
        var mappedToType = dto.results.stream().collect(Collectors.groupingBy(r -> r.type()));
        List<OperationResult> infos = mappedToType.get(OperationResultType.INFO);
        List<OperationResult> warnings = mappedToType.get(OperationResultType.WARNING);
        List<OperationResult> errors = mappedToType.get(OperationResultType.ERROR);
        if (infoCount == 0) {
            assertNull(infos);
        } else {
            assertEquals(infoCount, infos.size());
        }
        if (warningCount == 0) {
            assertNull(warnings);
        } else {
            assertEquals(warningCount, warnings.size());
        }
        if (errorCount == 0) {
            assertNull(errors);
        } else {
            assertEquals(errorCount, errors.size());
        }
    }
}
