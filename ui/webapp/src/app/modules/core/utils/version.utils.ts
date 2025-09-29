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
 * @param a the first Version to compare
 * @param b the second Version to compare
 * @returns a positive number if Version a is newer than Version b, a negative number if Version a is older than Version b, and exactly 0 if they are the same Version
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
