import { Permission, ScopedPermission, UserGroupInfo, UserInfo } from 'src/app/models/gen.dtos';

export function getGlobalPermission(permissions: ScopedPermission[]): Permission {
  return getPermissionLevelForScope(permissions, (sc) => sc.scope === null);
}

export function getLocalPermission(permissions: ScopedPermission[], scope: string): Permission {
  return getPermissionLevelForScope(permissions, (sc) => sc.scope === scope);
}

export function getGlobalOrLocalPermission(permissions: ScopedPermission[], scope: string): Permission {
  return getPermissionLevelForScope(permissions, (sc) => sc.scope === null || sc.scope === scope);
}

export function getInheritedPermissions(user: UserInfo, userGroups: UserGroupInfo[]): ScopedPermission[] {
  return userGroups
    .filter((userGroup) => user.groups.includes(userGroup.id))
    .flatMap((userGroup) => userGroup.permissions);
}

export function getInheritedPermissionHint(user: UserInfo, userGroups: UserGroupInfo[], scope: string): string {
  const userGroupNames = userGroups
    .filter((userGroup) => user.groups.includes(userGroup.id))
    .filter((userGroup) => getGlobalOrLocalPermission(userGroup.permissions, scope))
    .map((userGroup) => userGroup.name);
  if (userGroupNames.length === 0) {
    return '';
  }
  if (userGroupNames.length === 1) {
    return `${userGroupNames[0]} user group affects ${user.name}'s permission for ${scope}`;
  }
  return `${userGroupNames.join(', ')} user groups affect ${user.name}'s permission for ${scope}`;
}

function getPermissionLevelForScope(arr: ScopedPermission[], scopePredicate: (sc: ScopedPermission) => boolean) {
  if (!arr) {
    return null;
  }
  const permissions = arr.filter((sc) => scopePredicate(sc));
  let p = permissions.find((sc) => sc.permission === Permission.ADMIN);
  p = p || permissions.find((sc) => sc.permission === Permission.WRITE);
  p = p || permissions.find((sc) => sc.permission === Permission.READ);
  p = p || permissions.find((sc) => sc.permission === Permission.CLIENT);
  return p ? p.permission : null;
}
