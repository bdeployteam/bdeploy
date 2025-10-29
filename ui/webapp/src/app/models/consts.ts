/** HTTP header constant used to suppress global error handling */
export const NO_ERROR_HANDLING_HDR = 'X-No-Global-Error-Handling';
export const NO_UNAUTH_DELAY_HDR = 'X-No-Unauth-Delay';

/** Sort callback for node names, putting 'master' in the first place */
export const sortNodesMasterFirst = (a: string, b: string): number => {
  if (a === 'master') {
    return -1;
  } else if (b === 'master') {
    return 1;
  } else {
    return a.toLocaleLowerCase().localeCompare(b.toLocaleLowerCase());
  }
};

/**
 * Merges two arrays trying to keep ordering as intact as possible by inserting elements relative to its siblings if it is not contained in the other array.
 *
 * See https://stackoverflow.com/questions/53720910/merge-arrays-and-keep-ordering
 */
export function mergeOrdererd<T>(a: T[], b: T[], key: (ele: T) => T): T[] {
  const result: T[] = [];
  [a, b].forEach((array) => {
    array.forEach((item: T, idx: number) => {
      // check if the item has already been added, if not, try to add
      if (!result.some((x) => key(x) === key(item))) {
        // if item is not first item, find position of its left sibling in result array
        if (idx) {
          const resultIndex = result.indexOf(array[idx - 1]);
          // add item after left sibling position
          result.splice(resultIndex + 1, 0, item);
          return;
        }
        result.push(item);
      }
    });
  });
  return result;
}
