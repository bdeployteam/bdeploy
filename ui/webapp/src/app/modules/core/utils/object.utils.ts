export interface SimpleEntry<V> {
  key: string;
  value: V;
}

/** Converts a map as sent by the backend to a easier usable array */
export function mapObjToArray<V>(obj: { [key: string]: V }): SimpleEntry<V>[] {
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

export function groupArrayBy<T>(array: T[], key: string): {} {
  return array.reduce((pv, cv) => {
    (pv[cv[key]] = pv[cv[key]] || []).push(cv);
    return pv;
  }, {});
}

export function randomString(length: number, alowNumbers?: boolean): string {
  const chars = alowNumbers ? '0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ' : 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ';
  let result = '';
  for (var i = length; i > 0; --i) result += chars[Math.floor(Math.random() * chars.length)];
  return result;
}
