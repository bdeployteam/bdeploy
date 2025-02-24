import { Version } from '../../../models/gen.dtos';

/**
 * Returns a human readable string of the given version
 */
export function convert2String(version: Version): string {
  if (!version) {
    return 'Unknown';
  }
  return version.major + '.' + version.minor + '.' + version.micro + (version.qualifier ? version.qualifier : '');
}

/**
 * Compares two versions. Ignores qualifiers.
 */
export function compareVersions(a: Version, b: Version): number {
  if (a.major !== b.major) {
      return a.major - b.major;
  }
  if (a.minor !== b.minor) {
      return a.minor - b.minor;
  }
  return a.micro - b.micro;
}