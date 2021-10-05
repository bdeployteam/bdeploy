import { cloneDeep, isEqual } from 'lodash-es';

/** A dirty check for two objects. Empty strings are mapped to null before comparing to clear out anything a form may have done. */
export function isDirty(a: any, b: any) {
  return !isEqual(mapEmptyValueToNull(cloneDeep(a)), mapEmptyValueToNull(cloneDeep(b)));
}

function mapEmptyValueToNull(object) {
  Object.keys(object).forEach((key) => {
    if (object[key] === '') {
      object[key] = null;
    }
  });
  return object;
}
