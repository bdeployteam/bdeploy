import { cloneDeep, isEqual } from 'lodash-es';

/** A dirty check for two objects. Empty strings are mapped to null before comparing to clear out anything a form may have done. */
export function isDirty(a: object, b: object) {
  if (!a || !b) {
    return false;
  }
  return !isEqual(mapEmptyValueToNull(cloneDeep(a)), mapEmptyValueToNull(cloneDeep(b)));
}

function mapEmptyValueToNull(object: object) {
  Object.keys(object).forEach((key) => {
    const record = object as Record<string, unknown>;
    if (record[key] === '') {
      record[key] = null;
    }
  });
  return object;
}
