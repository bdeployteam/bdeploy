import { isEqual, isObject, transform } from 'lodash';
import { Subscription } from 'rxjs';

/**
 * Disposes the resources held by the subscription
 */
export function unsubscribe(s: Subscription) {
  if (s) {
    s.unsubscribe();
  }
}

/**
 * Deep diff between two object, using lodash
 * https://gist.github.com/Yimiprod/7ee176597fef230d1451
 */
export function diffDeep(object: any, base: any) {
  return changes(object, base);
}

/**
 * Returns the index of the first occurrence of a value in an array.
 */
export function indexOf(collection: any, node: any) {
  return Array.prototype.indexOf.call(collection, node);
}

/**
 * Recursively determines the changes between two objects
 */
function changes(object: any, base: any) {
  return transform(object, function(result, value, key) {
    if (!isEqual(value, base[key])) {
      result[key] = isObject(value) && isObject(base[key]) ? changes(value, base[key]) : value;
    }
  });
}

/**
 * Finds a string starting with a given prefix in the given list of strings.
 * All matching values without the prefix are returned.
 */
export function findEntry(input: string[], prefix: string) {
  const result: string[] = [];
  for (const entry of input) {
    if (entry.startsWith(prefix)) {
      result.push(entry.substr(prefix.length));
    }
  }
  return result;
}

/**
 * Returns a new array containing elements that are present in both arrays.
 */
export function intersection(a: any[], b: any[]) {
  return a.filter(value => b.includes(value));
}
