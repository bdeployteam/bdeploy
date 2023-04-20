import { Permission, ScopedPermission } from 'src/app/models/gen.dtos';

export function getGlobalPermission(
  permissions: ScopedPermission[]
): Permission {
  if (!permissions) {
    return null;
  }
  let p = permissions.find(
    (sc) => sc.scope === null && sc.permission === Permission.ADMIN
  );
  p = p
    ? p
    : permissions.find(
        (sc) => sc.scope === null && sc.permission === Permission.WRITE
      );
  p = p
    ? p
    : permissions.find(
        (sc) => sc.scope === null && sc.permission === Permission.READ
      );
  p = p
    ? p
    : permissions.find(
        (sc) => sc.scope === null && sc.permission === Permission.CLIENT
      );
  return p ? p.permission : null;
}
