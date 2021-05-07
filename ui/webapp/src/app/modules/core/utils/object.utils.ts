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
