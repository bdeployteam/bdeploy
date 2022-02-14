import { Version } from '../../../models/gen.dtos';

/**
 * Returns a human readable string of the given version
 */
export function convert2String(version: Version): string {
  if (!version) {
    return 'Unknown';
  }
  return (
    version.major +
    '.' +
    version.minor +
    '.' +
    version.micro +
    (version.qualifier ? version.qualifier : '')
  );
}
