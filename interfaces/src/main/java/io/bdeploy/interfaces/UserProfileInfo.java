package io.bdeploy.interfaces;

import java.util.SortedSet;

/**
 * Aggregate of details about a successfully authenticated user at the point in time where authentication happened.
 */
public class UserProfileInfo {

    /** List of groups user belongs to */
    public SortedSet<UserGroupInfo> userGroups;
}
