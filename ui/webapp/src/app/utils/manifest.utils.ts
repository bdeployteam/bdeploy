import { ManifestKey, OperatingSystem } from '../models/gen.dtos';

/**
 * Returns the base name of the application manifest key.
 */
export function getAppKeyName(appKey: ManifestKey) {
  const fullName = appKey.name;
  const lastSlashIdx = fullName.lastIndexOf('/');
  return fullName.substring(0, lastSlashIdx);
}

/**
 * Returns the OS supported by this application
 */
export function getAppOs(appKey: ManifestKey): OperatingSystem {
  const fullName = appKey.name;
  const lastSlashIdx = fullName.lastIndexOf('/') + 1;
  const osName = fullName.substring(lastSlashIdx).toUpperCase();
  return OperatingSystem[osName];
}
