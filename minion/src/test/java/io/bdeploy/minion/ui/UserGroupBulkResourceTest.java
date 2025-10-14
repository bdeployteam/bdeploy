package io.bdeploy.minion.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.bdeploy.interfaces.UserGroupInfo;
import io.bdeploy.minion.TestMinion;
import io.bdeploy.ui.api.AuthAdminResource;
import io.bdeploy.ui.api.AuthResource;
import io.bdeploy.ui.dto.BulkOperationResultDto;
import io.bdeploy.ui.dto.OperationResult;
import io.bdeploy.ui.dto.OperationResult.OperationResultType;

@ExtendWith(TestMinion.class)
class UserGroupBulkResourceTest {

    @Test
    void testAllUsersGroup(AuthResource auth) {
        AuthAdminResource admin = auth.getAdmin();
        var bulk = admin.getUserGroupBulkResource();

        Set<String> setOfAllUsersGroup = Set.of("all");
        Set<String> setOfAllUsersGroupWithDummy = Set.of("all", "dummy");

        // Ensure baseline
        assertEquals(1, admin.getAllUserGroups().size()); // Default all-users-group always exists

        // Test setting the all-users-group inactive
        BulkOperationResultDto setInactiveResult1 = bulk.setInactiveUserGroups(true, setOfAllUsersGroup);
        assertBulkOperationResult(setInactiveResult1, 0, 0, 1);
        assertEquals(1, admin.getAllUserGroups().size());

        // Test setting the all-users-group inactive along with a non-existant group
        BulkOperationResultDto setInactiveResult2 = bulk.setInactiveUserGroups(true, setOfAllUsersGroupWithDummy);
        assertBulkOperationResult(setInactiveResult2, 0, 0, 2);
        assertEquals(1, admin.getAllUserGroups().size());

        // Test setting the all-users-group active
        BulkOperationResultDto setInactiveResult3 = bulk.setInactiveUserGroups(false, setOfAllUsersGroup);
        assertBulkOperationResult(setInactiveResult3, 0, 0, 1);
        assertEquals(1, admin.getAllUserGroups().size());

        // Test setting the all-users-group active along with a non-existant group
        BulkOperationResultDto setInactiveResult4 = bulk.setInactiveUserGroups(false, setOfAllUsersGroupWithDummy);
        assertBulkOperationResult(setInactiveResult4, 0, 0, 2);
        assertEquals(1, admin.getAllUserGroups().size());

        // Test deleting the all-users-group
        BulkOperationResultDto setInactiveResult5 = bulk.deleteUserGroups(setOfAllUsersGroup);
        assertBulkOperationResult(setInactiveResult5, 0, 0, 1);
        assertEquals(1, admin.getAllUserGroups().size());

        // Test deleting the all-users-group along with a non-existant group
        BulkOperationResultDto setInactiveResult6 = bulk.deleteUserGroups(setOfAllUsersGroupWithDummy);
        assertBulkOperationResult(setInactiveResult6, 0, 0, 2);
        assertEquals(1, admin.getAllUserGroups().size());

        // Add a real group
        String id = createUserGroup(admin, "real");
        assertEquals(2, admin.getAllUserGroups().size());

        Set<String> setOfAllUsersGroupWithReal = Set.of("all", id);

        // Test setting the all-users-group inactive along with a real group
        BulkOperationResultDto setInactiveResult7 = bulk.setInactiveUserGroups(true, setOfAllUsersGroupWithReal);
        assertBulkOperationResult(setInactiveResult7, 1, 0, 1);
        assertEquals(2, admin.getAllUserGroups().size());

        // Test setting the all-users-group active along with a real group
        BulkOperationResultDto setInactiveResult8 = bulk.setInactiveUserGroups(false, setOfAllUsersGroupWithReal);
        assertBulkOperationResult(setInactiveResult8, 1, 0, 1);
        assertEquals(2, admin.getAllUserGroups().size());

        // Test deleting the all-users-group along with a real group
        BulkOperationResultDto setInactiveResult9 = bulk.deleteUserGroups(setOfAllUsersGroupWithReal);
        assertBulkOperationResult(setInactiveResult9, 1, 0, 1);
        assertEquals(1, admin.getAllUserGroups().size());
    }

    @Test
    void testModifyAndDeleteGroups(AuthResource auth) {
        AuthAdminResource admin = auth.getAdmin();
        var bulk = admin.getUserGroupBulkResource();

        BiFunction<Integer, Integer, Set<String>> nameGenerator = (a, b) -> IntStream.rangeClosed(a, b)
                .mapToObj(i -> "TestGroup" + i).map(o -> o).collect(Collectors.toSet());

        Set<String> group1Names = nameGenerator.apply(1, 3);
        Set<String> group2Names = nameGenerator.apply(4, 10);
        Set<String> allNames = new HashSet<>() {

            {
                addAll(group1Names);
                addAll(group2Names);
            }
        };

        int group1Count = group1Names.size();
        int group2Count = group2Names.size();
        int totalCount = group1Count + group2Count;

        // Ensure baseline
        assertEquals(1, admin.getAllUserGroups().size()); // Default all-users-group always exists

        // Create groups and get the IDs
        allNames.forEach(n -> createUserGroup(admin, n));
        SortedSet<UserGroupInfo> allUserGroups = admin.getAllUserGroups();
        assertEquals(1 + totalCount, allUserGroups.size());

        Map<String, String> nameToIdMapGroup1 = new HashMap<>();
        Map<String, String> nameToIdMapGroup2 = new HashMap<>();
        for (UserGroupInfo groupInfo : allUserGroups) {
            String groupName = groupInfo.name;
            if (group1Names.contains(groupName)) {
                nameToIdMapGroup1.put(groupName, groupInfo.id);
            } else if (group2Names.contains(groupName)) {
                nameToIdMapGroup2.put(groupName, groupInfo.id);
            } else if ("all".equals(groupName)) {
                // ignore default all-users-group
                continue;
            } else {
                throw new IllegalStateException("huh?");
            }
        }

        Set<String> group1Ids = new HashSet<>(nameToIdMapGroup1.values());
        Set<String> group2Ids = new HashSet<>(nameToIdMapGroup2.values());
        Set<String> allIds = new HashSet<>() {

            {
                addAll(group1Ids);
                addAll(group2Ids);
            }
        };
        Set<String> testIds = Set.of("blah1", "blah2");

        int testIdCount = testIds.size();

        // Test bulk setInactive
        BulkOperationResultDto setInactiveResult1 = bulk.setInactiveUserGroups(true, group1Ids);
        assertBulkOperationResult(setInactiveResult1, group1Count, 0, 0);

        BulkOperationResultDto setInactiveResult2 = bulk.setInactiveUserGroups(true, group2Ids);
        assertBulkOperationResult(setInactiveResult2, group2Count, 0, 0);

        BulkOperationResultDto setInactiveResult3 = bulk.setInactiveUserGroups(false, allIds);
        assertBulkOperationResult(setInactiveResult3, totalCount, 0, 0);

        BulkOperationResultDto setInactiveResult4 = bulk.setInactiveUserGroups(false, group2Ids);
        assertBulkOperationResult(setInactiveResult4, group2Count, 0, 0);

        BulkOperationResultDto setInactiveResult5 = bulk.setInactiveUserGroups(true, testIds);
        assertBulkOperationResult(setInactiveResult5, 0, 0, testIdCount);

        BulkOperationResultDto setInactiveResult6 = bulk.setInactiveUserGroups(false, new HashSet<String>() {

            {
                addAll(group2Ids);
                addAll(testIds);
            }
        });
        assertBulkOperationResult(setInactiveResult6, group2Count, 0, testIdCount);

        // Test bulk delete part of all groups
        BulkOperationResultDto deleteResult1 = bulk.deleteUserGroups(group1Ids);
        assertBulkOperationResult(deleteResult1, group1Count, 0, 0);
        assertEquals(1 + group2Count, admin.getAllUserGroups().size());

        // Test bulk delete all groups, some of which were already deleted in the previous step
        BulkOperationResultDto bulkDeletionResult2 = bulk.deleteUserGroups(allIds);
        assertBulkOperationResult(bulkDeletionResult2, group2Count, 0, group1Count);
        assertEquals(1, admin.getAllUserGroups().size());

        // Test bulk delete of entirely non-existant groups
        BulkOperationResultDto bulkDeletionResult3 = bulk.deleteUserGroups(testIds);
        assertBulkOperationResult(bulkDeletionResult3, 0, 0, testIdCount);
        assertEquals(1, admin.getAllUserGroups().size());
    }

    private static String createUserGroup(AuthAdminResource admin, String name) {
        UserGroupInfo userGroupInfo = new UserGroupInfo();
        userGroupInfo.name = name;
        userGroupInfo.description = name + " description";
        admin.createUserGroup(userGroupInfo);
        return admin.getAllUserGroups().stream().filter(g -> g.name.equals(userGroupInfo.name)).findAny().get().id;
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
