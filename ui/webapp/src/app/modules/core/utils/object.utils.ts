import { cloneDeep, forOwn, isArray, isEmpty, isNull, isObject, isUndefined, pull } from 'lodash-es';
import { StatusMessage } from 'src/app/models/config.model';

export interface SimpleEntry<V> {
  key: string;
  value: V;
}

/** Converts a map as sent by the backend to a easier usable array */
export function mapObjToArray<V>(obj: Record<string, V>): SimpleEntry<V>[] {
  const result = [];

  for (const key of Object.keys(obj)) {
    result.push({ key, value: obj[key] });
  }

  return result;
}

/** formats a size in bytes into a human readable string. */
export function formatSize(size: number): string {
  const i: number = size === 0 ? 0 : Math.min(4, Math.floor(Math.log(size) / Math.log(1024)));
  return (i === 0 ? size : (size / Math.pow(1024, i)).toFixed(2)) + ' ' + ['B', 'kB', 'MB', 'GB', 'TB'][i];
}

export function randomString(length: number, allowNumbers = false): string {
  const chars = allowNumbers
    ? '0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ'
    : 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ';
  let result = '';
  for (let i = length; i > 0; --i) result += chars[Math.floor(Math.random() * chars.length)];
  return result;
}

export function expandVar(variable: string, variables: Record<string, string>, status: StatusMessage[]): string {
  let varName = variable;
  const colIndex = varName.indexOf(':');
  if (colIndex !== -1) {
    varName = varName.substring(0, colIndex);
  }
  const val = variables[varName];

  if (colIndex !== -1) {
    const op = variable.substring(colIndex + 1);
    const opNum = Number(op);
    const valNum = Number(val);

    if (Number.isNaN(opNum) || Number.isNaN(valNum)) {
      status.push({
        icon: 'error',
        message: `Invalid variable substitution for ${variable}: '${op}' or '${val}' is not a number.`,
      });
      return variable;
    }
    return (valNum + opNum).toString();
  }

  return val;
}

export function performTemplateVariableSubst(
  value: string,
  variables: Record<string, string>,
  status: StatusMessage[]
): string {
  if (!!value && value.includes('{{T:')) {
    let found = true;
    while (found) {
      const rex = new RegExp(/{{T:([^}]*)}}/).exec(value);
      if (rex) {
        value = value.replace(rex[0], expandVar(rex[1], variables, status));
      } else {
        found = false;
      }
    }
  }
  return value;
}

export function removeNullValues(obj: object) {
  return (function prune(current) {
    forOwn(current, function (value, key) {
      if (isUndefined(value) || isNull(value) || (isObject(value) && isEmpty(prune(value)))) {
        delete (current as Record<string, unknown>)[key];
      }
    });
    // remove any leftover undefined values from the delete
    // operation on an array
    if (isArray(current)) pull(current, undefined);

    return current;
  })(cloneDeep(obj)); // Do not modify the original object, create a clone instead
}
