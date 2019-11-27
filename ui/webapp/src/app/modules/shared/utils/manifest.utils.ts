import { ManifestKey, OperatingSystem } from '../../../models/gen.dtos';

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

/**
 * Sorts the given array of records by tags
 */
export function sortByTags(records: any[], accessor: (record: any) => string, asc: boolean): any[] {
  return records.sort((aRec, bRec) => {
    const tagA: string = accessor(aRec);
    const tagB: string = accessor(bRec);
    return (asc ? 1 : -1) * compareTags(tagA, tagB);
  });
}

/**
 * Compare tag strings by splitting the tag
 * into alnum parts (sorting each part either numerical or alphabetical)
 */
export function compareTags(tagA: string, tagB: string): number {
  // split tags into parts, e.g. "5.9.0-N20190830" -> ["5", "9", "0", "N20190830"]
  const tagTokenRegex = /[^0-9a-zA-Z]+/;
  const a: string[] = tagA.split(tagTokenRegex);
  const b: string[] = tagB.split(tagTokenRegex);
  // sort  by tokens
  for (let i = 0; i < Math.min(a.length, b.length); i++) {
    const a_isnum = /^\d+$/.test(a[i]);
    const b_isnum = /^\d+$/.test(b[i]);
    if (a_isnum && b_isnum) {
      if (+a[i] < +b[i]) {
        return -1;
      } else if (+a[i] > +b[i]) {
        return 1;
      }
    } else {
      if (a[i] < b[i]) {
        return -1;
      } else if (a[i] > b[i]) {
        return 1;
      }
    }
  }
  return a.length - b.length;
}
